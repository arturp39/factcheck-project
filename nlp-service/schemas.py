from typing import List, Optional

from pydantic import BaseModel, Field, field_validator

from config import settings


class PreprocessRequest(BaseModel):
    text: str = Field(..., min_length=1)
    correlationId: Optional[str] = None

    @field_validator("text")
    @classmethod
    def validate_text(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("text cannot be empty or whitespace only")

        if len(v) > settings.max_text_length:
            raise ValueError(
                f"text is too long (>{settings.max_text_length} characters). "
                f"Consider splitting or truncating before sending."
            )
        return v


class PreprocessResponse(BaseModel):
    sentences: List[str]
    sentenceCount: int
    processingTimeMs: int
    correlationId: Optional[str] = None


class EmbedRequest(BaseModel):
    texts: List[str] = Field(..., min_length=1)
    correlationId: Optional[str] = None

    @field_validator("texts")
    @classmethod
    def validate_texts(cls, v: List[str]) -> List[str]:
        if not v:
            raise ValueError("texts must contain at least one item")
        if len(v) > settings.max_texts_per_request:
            raise ValueError(
                f"Too many texts in one request "
                f"(>{settings.max_texts_per_request})."
            )
        total_len = 0
        for t in v:
            t_stripped = t.strip()
            if not t_stripped:
                raise ValueError("texts cannot contain empty or whitespace-only items")
            if len(t_stripped) > settings.max_text_length:
                raise ValueError(
                    f"One of the texts is too long "
                    f"(>{settings.max_text_length} characters)."
                )
            total_len += len(t_stripped)

        if total_len > settings.max_total_chars_per_request:
            raise ValueError(
                f"Total size of texts exceeds limit "
                f"({settings.max_total_chars_per_request} characters)."
            )
        return v


class EmbedResponse(BaseModel):
    embeddings: List[List[float]]
    dimension: int
    model: str
    processingTimeMs: int
    correlationId: Optional[str] = None


class HealthResponse(BaseModel):
    status: str
    service: str
    version: str
    vertexAI: dict
    uptime: int

class SentenceEmbedRequest(BaseModel):
    sentences: List[str] = Field(
        ...,
        description="List of sentences to embed individually for semantic analysis",
        min_length=1,
        max_length=200
    )
    correlationId: Optional[str] = Field(
        None,
        description="Optional correlation ID for request tracing"
    )

    @field_validator("sentences")
    @classmethod
    def validate_sentences(cls, v: List[str]) -> List[str]:
        if not v:
            raise ValueError("sentences list cannot be empty")

        if len(v) > settings.max_texts_per_request:
            raise ValueError(
                f"Too many sentences in one request (>{settings.max_texts_per_request})."
            )

        total_chars = sum(len(s.strip()) for s in v)
        if total_chars > settings.max_total_chars_per_request:
            raise ValueError(
                f"Total size of sentences exceeds limit "
                f"({settings.max_total_chars_per_request} characters)."
            )

        for s in v:
            if not s.strip():
                raise ValueError("sentences cannot contain empty or whitespace-only items")
            if len(s) > settings.max_text_length:
                raise ValueError(
                    f"One of the sentences is too long (>{settings.max_text_length} characters)."
                )

        return v


class SentenceEmbedResponse(BaseModel):
    embeddings: List[List[float]] = Field(
        ..., description="List of embedding vectors, one per sentence"
    )
    dimension: int = Field(..., description="Dimensionality of each embedding vector")
    model: str = Field(..., description="Model used for embeddings")
    processingTimeMs: int = Field(..., description="Processing time in milliseconds")
    correlationId: Optional[str] = Field(
        None, description="Correlation ID from request"
    )