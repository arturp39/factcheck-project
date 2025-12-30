import json
import logging
import os
from dataclasses import dataclass
from datetime import datetime, timezone
from functools import lru_cache
from pathlib import Path
from typing import Any, Dict

from dotenv import load_dotenv

BASE_DIR = Path(__file__).resolve().parent
ENV_PATH = BASE_DIR / ".env"
load_dotenv(dotenv_path=ENV_PATH)

@dataclass
class Settings:
    """
    Centralized configuration for the NLP microservice.
    """

    # Service identity
    service_name: str = os.getenv("NLP_SERVICE_NAME", "nlp-service")
    service_version: str = os.getenv("NLP_SERVICE_VERSION", "1.0.0")

    # Network
    port: int = int(os.getenv("NLP_PORT", "8000"))

    # Embedding configuration
    embedding_dim: int = int(os.getenv("NLP_EMBEDDING_DIM", "768"))

    # Safety limits for requests
    max_text_length: int = int(os.getenv("NLP_MAX_TEXT_LENGTH", "50000"))
    max_texts_per_request: int = int(os.getenv("NLP_MAX_TEXTS_PER_REQUEST", "100"))
    max_total_chars_per_request: int = int(
        os.getenv("NLP_MAX_TOTAL_CHARS", "500000")
    )

    # Logging level
    log_level: str = os.getenv("NLP_LOG_LEVEL", "INFO")

    # False -> use real Vertex AI embeddings
    # True  -> use deterministic fake embeddings
    use_fake_embeddings: bool = os.getenv(
        "NLP_USE_FAKE_EMBEDDINGS", "false"
    ).lower() == "true"

    # Vertex AI configuration
    vertex_project_id: str = os.getenv("VERTEX_PROJECT_ID")
    vertex_location: str = os.getenv("VERTEX_LOCATION", "us-central1")
    vertex_model: str = os.getenv("NLP_VERTEX_MODEL", "text-embedding-004")
    vertex_task_type: str = os.getenv("NLP_VERTEX_TASK_TYPE", "RETRIEVAL_DOCUMENT")
    vertex_timeout_seconds: int = int(os.getenv("NLP_VERTEX_TIMEOUT_SECONDS", "30"))
    vertex_internal_batch_size: int = int(os.getenv("NLP_VERTEX_BATCH_SIZE", "5"))
    vertex_max_calls_per_minute: int = int(
        os.getenv("NLP_VERTEX_MAX_CALLS_PER_MINUTE", "5")
    )

class CorrelationIdFilter(logging.Filter):
    """Injects `correlation_id` into log records (default '-') so formatter
    can always use it.
    """

    def filter(self, record: logging.LogRecord) -> bool:
        if not hasattr(record, "correlation_id"):
            record.correlation_id = "-"
        return True


class JSONFormatter(logging.Formatter):
    """Structured JSON logs for centralized logging."""

    def format(self, record: logging.LogRecord) -> str:
        cfg = get_settings()

        log: Dict[str, Any] = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "level": record.levelname,
            "service": cfg.service_name,
            "version": cfg.service_version,
            "logger": record.name,
            "message": record.getMessage(),
        }

        if hasattr(record, "correlation_id"):
            log["correlationId"] = record.correlation_id
        if hasattr(record, "context") and isinstance(record.context, dict):
            log["context"] = record.context

        return json.dumps(log, ensure_ascii=False)


def setup_logging() -> None:
    cfg = get_settings()

    root = logging.getLogger()
    if root.handlers:
        return

    handler = logging.StreamHandler()
    handler.setFormatter(JSONFormatter())

    level = getattr(logging, cfg.log_level.upper(), logging.INFO)
    root.setLevel(level)
    root.addHandler(handler)
    root.addFilter(CorrelationIdFilter())


@lru_cache()
def get_settings() -> Settings:
    """
    Returns a cached Settings instance and performs basic sanity checks.
    """
    cfg = Settings()

    # if real embeddings requested but project id missing -> error.
    if not cfg.use_fake_embeddings and not cfg.vertex_project_id:
        raise RuntimeError(
            "VERTEX_PROJECT_ID must be set when NLP_USE_FAKE_EMBEDDINGS=false "
            "(required for Vertex AI embeddings). "
            f"Loaded from .env at: {ENV_PATH}"
        )

    return cfg


def get_logger(name: str) -> logging.Logger:
    setup_logging()
    return logging.getLogger(name)


settings = get_settings()
setup_logging()
