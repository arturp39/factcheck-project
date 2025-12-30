import logging
import re
from typing import List

import nltk

from config import get_logger

logger = get_logger(__name__)

# Precompiled regex for fallback splitter
_FALLBACK_SPLIT_RE = re.compile(r"\.(\s+|$)")


def ensure_nltk_punkt() -> None:
    """Ensure NLTK punkt tokenizer is available."""
    try:
        nltk.data.find("tokenizers/punkt")
    except LookupError:
        logger.info("NLTK punkt not found, downloading...", extra={"correlation_id": "-"})
        nltk.download("punkt", quiet=True)


def _clean_text(text: str) -> str:
    # Remove control chars and collapse whitespace
    text = re.sub(r"[\x00-\x1F\x7F-\x9F]", " ", text)
    text = re.sub(r"\s+", " ", text)
    return text.strip()


def _fallback_split(text: str) -> List[str]:
    cleaned = _clean_text(text)
    if not cleaned:
        return []
    parts = _FALLBACK_SPLIT_RE.split(cleaned)
    sentences: List[str] = []
    current = ""
    for part in parts:
        if not part:
            continue
        current += part
        if part.endswith("."):
            s = current.strip()
            if s:
                sentences.append(s)
            current = ""
    if current.strip():
        sentences.append(current.strip())
    return sentences

def split_into_sentences(text: str) -> List[str]:
    """Clean text and split into normalized sentences (NLTK + fallback)."""
    text = _clean_text(text)
    if not text:
        return []

    ensure_nltk_punkt()

    try:
        raw = nltk.sent_tokenize(text)
        sentences = [s.strip() for s in raw if len(s.strip()) >= 3]
        return sentences
    except Exception as e:
        logger.warning(
            "NLTK sentence splitting failed, using fallback: %s",
            str(e),
            extra={"correlation_id": "-"},
        )
        return _fallback_split(text)