# Local Run (Getting Started)

Prerequisites
- Docker + Docker Compose (standard setup)
- If running services outside Docker: Java 17+ for backend/collector, Python 3.10+ for the NLP service

Repo layout (this monorepo)
- `infra/` for Docker Compose + env
- `backend/` Spring Boot API
- `news-collector/` Spring Boot ingestion service
- `nlp-service/` FastAPI embeddings/preprocess

Steps
1) Copy env template: `cp infra/.env.example infra/.env`.
2) Build & start: `cd infra` then `docker compose up --build`.
3) Wait for health checks: Postgres (backend + collector), Weaviate, NLP, backend, collector.
4) Test services:
   - Backend health: `curl -s https://factcheck-backend-804697237544.us-central1.run.app/actuator/health`
   - Collector health: `curl -s https://factcheck-news-collector-804697237544.us-central1.run.app/actuator/health`
   - NLP health: `curl -s https://factcheck-nlp-service-804697237544.us-central1.run.app/health`

Shutdown
- From `infra/`: `docker compose down` (add `-v` to drop volumes).

Common options
- Override ports via `infra/.env` (`BACKEND_PORT`, `COLLECTOR_PORT`, `NLP_PORT`, `WEAVIATE_PORT`, `BACKEND_POSTGRES_PORT`, `COLLECTOR_POSTGRES_PORT`).
- Use fake embeddings for offline dev: `NLP_USE_FAKE_EMBEDDINGS=true` (default in template).

