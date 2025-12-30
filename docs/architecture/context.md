# System Context

Actors
- End user / frontend: submits claims, reads verdicts.
- Admin/ops: triggers ingestion, manages sources.
- External providers: Vertex AI (LLM + embeddings), RSS/API news feeds.

Services
- Backend (Spring Boot, port 8080): public API/UI for claim verification, follow-up, bias.
- Collector (Spring Boot, port 8081): ingestion pipeline + internal search/content APIs.
- NLP (FastAPI, port 8000): preprocessing + embeddings.
- Data stores: Postgres for metadata/logs; Weaviate for vector search.

Flows
- Verify claim: user -> Backend -> NLP embed -> Weaviate search -> Vertex LLM -> Postgres log.
- Ingestion: Admin/scheduler -> Collector -> fetch sources -> NLP embed chunks -> Weaviate index + Postgres content metadata.

Infrastructure
- Docker Compose (`factcheck-platform/docker-compose.yml`) with shared network `factcheck_net`, volumes `postgres_data`, `weaviate_data`.