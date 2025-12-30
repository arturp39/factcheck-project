FROM python:3.11-slim AS builder

WORKDIR /app

# System deps for building wheels; cleaned away with the stage
RUN apt-get update \
    && apt-get install -y --no-install-recommends build-essential \
    && python -m venv /opt/venv \
    && /opt/venv/bin/pip install --no-cache-dir --upgrade pip \
    && rm -rf /var/lib/apt/lists/*

# Install Python deps into a separate venv to copy later
COPY requirements.txt .
RUN /opt/venv/bin/pip install --no-cache-dir -r requirements.txt


FROM python:3.11-slim

WORKDIR /app

# Non-root runtime user
RUN useradd -m appuser
USER appuser

# Ensure installed binaries are available
ENV PATH="/opt/venv/bin:${PATH}"
ENV PYTHONPATH="/opt/venv/lib/python3.11/site-packages"
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

# Copy dependencies and app source
COPY --from=builder /opt/venv /opt/venv
COPY . .

# Defaults
ENV NLP_PORT=8000
ENV NLP_USE_FAKE_EMBEDDINGS=true

EXPOSE 8000

CMD ["sh", "-c", "uvicorn main:app --host 0.0.0.0 --port ${NLP_PORT}"]
