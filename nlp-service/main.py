import time
from datetime import datetime
from typing import Optional

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse, Response
from starlette.middleware.base import BaseHTTPMiddleware
import uuid

from config import get_logger, settings
from schemas import (
    PreprocessRequest,
    PreprocessResponse,
    EmbedRequest,
    EmbedResponse,
    SentenceEmbedRequest,
    SentenceEmbedResponse,
    HealthResponse,
)
from services.preprocess import split_into_sentences, ensure_nltk_punkt
from services.embeddings import generate_embeddings
from metrics import REQUEST_COUNT, REQUEST_LATENCY

logger = get_logger("factcheck-nlp")

app = FastAPI(
    title="FactCheck NLP Service",
    version=settings.service_version,
    description=(
        "Preprocessing + embedding microservice for FactCheck project. "
        "Uses Vertex AI embeddings (text-embedding-004) by default, "
        "with correlation ID aware logging and Prometheus metrics."
    ),
)

_start_time = time.time()

# Middleware: correlationId support
class CorrelationIdMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        correlation_id = request.headers.get("X-Correlation-Id")
        if not correlation_id:
            correlation_id = str(uuid.uuid4())

        request.state.correlation_id = correlation_id

        response = await call_next(request)
        response.headers["X-Correlation-Id"] = correlation_id
        return response


app.add_middleware(CorrelationIdMiddleware)

@app.on_event("startup")
async def startup_event():
    logger.info("Starting NLP service", extra={"correlation_id": "-"})
    ensure_nltk_punkt()


# Global error handler
@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    cid: Optional[str] = getattr(request.state, "correlation_id", None)
    logger.error(
        "Unhandled exception in NLP service: %s",
        str(exc),
        extra={
            "correlation_id": cid or "-",
            "context": {"path": str(request.url.path)},
        },
    )
    return JSONResponse(
        status_code=500,
        content={
            "timestamp": datetime.utcnow().isoformat() + "Z",
            "status": 500,
            "error": "Internal Server Error",
            "message": "An unexpected error occurred in NLP service.",
            "path": str(request.url.path),
            "correlationId": cid,
        },
    )


# Health endpoint
@app.get("/health", response_model=HealthResponse)
async def health():
    vertex_status = "connected" if not settings.use_fake_embeddings else "fake-mode"

    return HealthResponse(
        status="healthy",
        service=settings.service_name,
        version=settings.service_version,
        vertexAI={
            "status": vertex_status,
            "model": settings.vertex_model,
        },
        uptime=int(time.time() - _start_time),
    )


# Metrics endpoint (Prometheus)
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest


@app.get("/metrics")
async def metrics():
    data = generate_latest()
    return Response(content=data, media_type=CONTENT_TYPE_LATEST)


# Preprocess endpoint
@app.post("/preprocess", response_model=PreprocessResponse)
async def preprocess(req: PreprocessRequest, request: Request) -> PreprocessResponse:
    cid = req.correlationId or getattr(request.state, "correlation_id", "none")

    REQUEST_COUNT.labels(endpoint="/preprocess").inc()
    with REQUEST_LATENCY.labels(endpoint="/preprocess").time():
        start = time.time()
        logger.info(
            "Preprocess request received",
            extra={
                "correlation_id": cid,
                "context": {"text_length": len(req.text)},
            },
        )

        try:
            sentences = split_into_sentences(req.text)
        except ValueError as ve:
            logger.warning(
                "Validation error in preprocess: %s",
                str(ve),
                extra={"correlation_id": cid},
            )
            raise HTTPException(status_code=400, detail=str(ve))

        processing_ms = int((time.time() - start) * 1000)

        logger.info(
            "Preprocess completed",
            extra={
                "correlation_id": cid,
                "context": {
                    "sentence_count": len(sentences),
                    "processingTimeMs": processing_ms,
                },
            },
        )

        return PreprocessResponse(
            sentences=sentences,
            sentenceCount=len(sentences),
            processingTimeMs=processing_ms,
            correlationId=req.correlationId,
        )


# Embed endpoint
@app.post("/embed", response_model=EmbedResponse)
async def embed(req: EmbedRequest, request: Request) -> EmbedResponse:
    cid = req.correlationId or getattr(request.state, "correlation_id", "none")

    REQUEST_COUNT.labels(endpoint="/embed").inc()
    with REQUEST_LATENCY.labels(endpoint="/embed").time():
        start = time.time()
        logger.info(
            "Embed request received",
            extra={
                "correlation_id": cid,
                "context": {"texts_count": len(req.texts)},
            },
        )

        try:
            vectors = generate_embeddings(req.texts, cid)
        except ValueError as ve:
            logger.warning(
                "Validation error in embed: %s",
                str(ve),
                extra={"correlation_id": cid},
            )
            raise HTTPException(status_code=400, detail=str(ve))
        except Exception as e:
            # Vertex AI error or unexpected issue
            logger.error(
                "Embedding generation failed: %s",
                str(e),
                extra={"correlation_id": cid},
            )
            # 503 – Vertex AI or integration unavailable
            raise HTTPException(status_code=503, detail="Embedding generation failed")

        processing_ms = int((time.time() - start) * 1000)

        logger.info(
            "Embed completed",
            extra={
                "correlation_id": cid,
                "context": {
                    "embedding_count": len(vectors),
                    "dimension": len(vectors[0]) if vectors else 0,
                    "processingTimeMs": processing_ms,
                    "use_fake": settings.use_fake_embeddings,
                },
            },
        )

        return EmbedResponse(
            embeddings=vectors,
            dimension=len(vectors[0]) if vectors else 0,
            model=settings.vertex_model
            if not settings.use_fake_embeddings
            else "deterministic-fake",
            processingTimeMs=processing_ms,
            correlationId=req.correlationId,
        )

@app.post("/embed-sentences", response_model=SentenceEmbedResponse)
async def embed_sentences(
    req: SentenceEmbedRequest, request: Request
) -> SentenceEmbedResponse:
    """
    Embed sentences individually for semantic similarity analysis.
    Used for intelligent chunk boundary detection.
    """
    cid = req.correlationId or getattr(request.state, "correlation_id", "none")

    REQUEST_COUNT.labels(endpoint="/embed-sentences").inc()
    with REQUEST_LATENCY.labels(endpoint="/embed-sentences").time():
        start = time.time()
        logger.info(
            "Sentence embed request received",
            extra={
                "correlation_id": cid,
                "context": {"sentence_count": len(req.sentences)},
            },
        )

        try:
            vectors = generate_embeddings(req.sentences, cid)
        except ValueError as ve:
            logger.warning(
                "Validation error in embed-sentences: %s",
                str(ve),
                extra={"correlation_id": cid},
            )
            raise HTTPException(status_code=400, detail=str(ve))
        except Exception as e:
            logger.error(
                "Sentence embedding generation failed: %s",
                str(e),
                extra={"correlation_id": cid},
            )
            raise HTTPException(status_code=503, detail="Sentence embedding failed")

        processing_ms = int((time.time() - start) * 1000)

        logger.info(
            "Sentence embed completed",
            extra={
                "correlation_id": cid,
                "context": {
                    "embedding_count": len(vectors),
                    "processingTimeMs": processing_ms,
                },
            },
        )

        return SentenceEmbedResponse(
            embeddings=vectors,
            dimension=len(vectors[0]) if vectors else 0,
            model=settings.vertex_model
            if not settings.use_fake_embeddings
            else "deterministic-fake",
            processingTimeMs=processing_ms,
            correlationId=req.correlationId,
        )


# Root endpoint
@app.get("/")
async def root():
    return {
        "service": settings.service_name,
        "status": "OK",
        "version": settings.service_version,
        "embedding_dim": settings.embedding_dim,
        "use_fake_embeddings": settings.use_fake_embeddings,
    }


# Local run entrypoint
if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=settings.port,
        reload=False,
    )
