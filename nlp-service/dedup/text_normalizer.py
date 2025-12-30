import re
import unicodedata


_WHITESPACE_RE = re.compile(r"\s+")


def normalize_text(text: str) -> str:
    """
    Normalization used for deduplication:
    - lowercasing
    - Unicode NFKC normalization
    - collapsing whitespace
    - stripping
    """
    if text is None:
        return ""

    text = unicodedata.normalize("NFKC", text)
    text = text.lower()
    text = _WHITESPACE_RE.sub(" ", text)
    return text.strip()
