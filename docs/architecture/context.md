# System Context

Actors
- End user / frontend: submits claims, reads verdicts.
- Admin/ops: triggers ingestion, manages sources.
- External providers: Vertex AI (LLM + embeddings), RSS/API news feeds.

Services
- Backend (Spring Boot, port 8080): public API/UI for claim verification, follow-up, bias.
- Collector (Spring Boot, port 8081): ingestion pipeline + internal search/content APIs.
- NLP (FastAPI, port 8000): preprocessing + embeddings.
- Data stores: Postgres (backend for claim logs, collector for ingestion metadata); Weaviate for vector search.

Flows
- Verify claim: user -> Backend -> NLP embed -> Weaviate search -> Vertex LLM -> Postgres (backend) log.
- Ingestion: Admin/scheduler -> Collector -> fetch source_endpoints -> NLP embed chunks -> Weaviate index + Postgres (collector) content metadata.

Infrastructure
- Docker Compose (`infra/docker-compose.yml`) with shared network `factcheck_net`, volumes `postgres_backend_data`, `postgres_collector_data`, `weaviate_data`.
