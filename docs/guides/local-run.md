# Local Run (Getting Started)

Prerequisites
- Docker + Docker Compose (standard setup)
- If running services outside Docker: Java 17+ for backend/collector, Python 3.10+ for the NLP service

Repositories (multi-repo setup expected side-by-side under `factcheck_project/`)
- https://github.com/arturp39/factcheck-platform (this repo; contains docker-compose)
- https://github.com/arturp39/factcheck-backend
- https://github.com/arturp39/factcheck-news-collector
- https://github.com/arturp39/factcheck-nlp-service
- https://github.com/arturp39/factcheck-db (init SQL)

Steps
1) Copy env template: `cp .env.example .env` (in repo root or `factcheck-platform/`).
2) Build & start: `docker compose up --build` (from repo root or `factcheck-platform/`).
3) Wait for health checks: Postgres, Weaviate, NLP, backend, collector.
4) Test services:
   - Backend health: `curl -s http://localhost:8080/actuator/health`
   - Collector health: `curl -s http://localhost:8081/actuator/health`
   - NLP health: `curl -s http://localhost:8000/health`

Shutdown
- `docker compose down` (add `-v` to drop volumes).

Common options
- Override ports via `.env` (`BACKEND_PORT`, `COLLECTOR_PORT`, `NLP_PORT`, `WEAVIATE_PORT`, `POSTGRES_PORT`).
- Use fake embeddings for offline dev: `NLP_USE_FAKE_EMBEDDINGS=true` (default in template).