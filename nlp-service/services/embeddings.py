import hashlib
import math
import time
import threading
import collections
from typing import List, Dict, Optional

from google.api_core.exceptions import GoogleAPIError
from google.cloud import aiplatform
from tenacity import retry, stop_after_attempt, wait_exponential
try:
    from vertexai.preview.language_models import TextEmbeddingModel as VertexTextEmbeddingModel
except ImportError:
    from vertexai.language_models import TextEmbeddingModel as VertexTextEmbeddingModel

from config import settings, get_logger
from dedup.text_normalizer import normalize_text

logger = get_logger(__name__)

# Vertex model lazy init
_vertex_initialized = False
_vertex_model: Optional[VertexTextEmbeddingModel] = None

# Simple in-process rate limiter for Vertex calls
_rate_lock = threading.Lock()
# store timestamps (seconds) of recent Vertex calls
_vertex_call_times = collections.deque(maxlen=64)


def _throttle_vertex_if_needed(correlation_id: Optional[str] = None) -> None:
    """
    Ensure we do not exceed settings.vertex_max_calls_per_minute
    Vertex requests per minute in this process.
    """
    max_calls = settings.vertex_max_calls_per_minute
    if max_calls <= 0:
        return

    with _rate_lock:
        now = time.time()
        # drop entries older than 60s
        while _vertex_call_times and now - _vertex_call_times[0] > 60.0:
            _vertex_call_times.popleft()

        if len(_vertex_call_times) >= max_calls:
            # time until we fall below the limit
            oldest = _vertex_call_times[0]
            wait_for = 60.0 - (now - oldest) + 0.05
            if wait_for > 0:
                logger.info(
                    "Throttling Vertex calls for %.2f seconds to respect quota",
                    wait_for,
                    extra={"correlation_id": correlation_id or "-"},
                )
                time.sleep(wait_for)
                # after sleeping, clean out again
                now = time.time()
                while _vertex_call_times and now - _vertex_call_times[0] > 60.0:
                    _vertex_call_times.popleft()

        _vertex_call_times.append(time.time())


def _init_vertex_if_needed(correlation_id: Optional[str] = None) -> None:
    global _vertex_initialized, _vertex_model
    if _vertex_initialized:
        return

    if not settings.vertex_project_id:
        raise RuntimeError("VERTEX_PROJECT_ID is not configured for Vertex AI embeddings")

    aiplatform.init(
        project=settings.vertex_project_id,
        location=settings.vertex_location,
    )
    _vertex_model = VertexTextEmbeddingModel.from_pretrained(settings.vertex_model)
    _vertex_initialized = True

    logger.info(
        "Initialized Vertex AI TextEmbeddingModel",
        extra={
            "correlation_id": correlation_id or "-",
            "context": {
                "model": settings.vertex_model,
                "location": settings.vertex_location,
            },
        },
    )


def deterministic_embedding(text: str, dim: Optional[int] = None) -> List[float]:
    """Deterministic pseudo-embedding based on SHA-256 of the text.
    Dev/budget mode only;
    """
    if dim is None:
        dim = settings.embedding_dim

    if not text:
        return [0.0] * dim

    digest = hashlib.sha256(text.encode("utf-8")).hexdigest()
    bytes_vals = [
        int(digest[i : i + 2], 16) / 255.0 for i in range(0, len(digest), 2)
    ]

    needed_repeats = math.ceil(dim / len(bytes_vals))
    vals = (bytes_vals * needed_repeats)[:dim]
    return vals


@retry(
    stop=stop_after_attempt(3),
    wait=wait_exponential(multiplier=1, min=1, max=10),
    reraise=True,
)
def _vertex_embed_batch(
    texts: List[str],
    correlation_id: Optional[str],
) -> List[List[float]]:
    """Call Vertex AI for a single batch of texts."""
    _init_vertex_if_needed(correlation_id)

    assert _vertex_model is not None

    _throttle_vertex_if_needed(correlation_id)

    start = time.time()
    try:
        task_type = (settings.vertex_task_type or "").strip()
        if task_type:
            task_enum = getattr(_vertex_model, "TaskType", None)
            task_value = getattr(task_enum, task_type, task_type) if task_enum else task_type
            try:
                response = _vertex_model.get_embeddings(texts, task_type=task_value)
            except TypeError:
                response = _vertex_model.get_embeddings(texts)
        else:
            response = _vertex_model.get_embeddings(texts)
        vectors = [emb.values for emb in response]
        duration = int((time.time() - start) * 1000)

        logger.info(
            "Vertex AI batch embedding done",
            extra={
                "correlation_id": correlation_id or "-",
                "context": {
                    "batch_size": len(texts),
                    "durationMs": duration,
                    "model": settings.vertex_model,
                },
            },
        )
        return vectors
    except GoogleAPIError as e:
        logger.error(
            "Vertex AI error: %s",
            str(e),
            extra={"correlation_id": correlation_id or "-"},
        )
        raise
    except Exception as e:
        logger.error(
            "Unexpected error calling Vertex AI: %s",
            str(e),
            extra={"correlation_id": correlation_id or "-"},
        )
        raise


def generate_embeddings(
    texts: List[str],
    correlation_id: Optional[str],
) -> List[List[float]]:
    if not texts:
        return []

    # Dedup by normalized text
    norm_to_indices: Dict[str, List[int]] = {}
    unique_texts: List[str] = []

    for idx, t in enumerate(texts):
        key = normalize_text(t)
        if key not in norm_to_indices:
            norm_to_indices[key] = [idx]
            unique_texts.append(t)
        else:
            norm_to_indices[key].append(idx)

    logger.info(
        "Embedding pipeline input",
        extra={
            "correlation_id": correlation_id or "-",
            "context": {
                "original_count": len(texts),
                "unique_count": len(unique_texts),
                "use_fake": settings.use_fake_embeddings,
            },
        },
    )

    # Compute embeddings for unique texts
    if settings.use_fake_embeddings:
        unique_vectors = [
            deterministic_embedding(t, settings.embedding_dim) for t in unique_texts
        ]
    else:
        unique_vectors: List[List[float]] = []
        batch_size = settings.vertex_internal_batch_size or 5
        for i in range(0, len(unique_texts), batch_size):
            batch = unique_texts[i : i + batch_size]
            batch_vecs = _vertex_embed_batch(batch, correlation_id)
            unique_vectors.extend(batch_vecs)

    # Map normalized text -> vector
    mapping: Dict[str, List[float]] = {}
    for text, vec in zip(unique_texts, unique_vectors):
        if len(vec) != settings.embedding_dim:
            # Allow dynamic dimension, but log mismatch
            logger.warning(
                "Embedding dimension mismatch: got %d expected %d",
                len(vec),
                settings.embedding_dim,
                extra={"correlation_id": correlation_id or "-"},
            )
        mapping[normalize_text(text)] = vec

    # Restore original ordering
    result: List[List[float]] = []
    for t in texts:
        key = normalize_text(t)
        result.append(mapping[key])

    return result
