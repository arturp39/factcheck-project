# Architecture Overview

- API backend: orchestrates claim verification, evidence search, LLM prompts; stores claim logs in Postgres.
- Collector: ingests publisher endpoints via `/ingestion/run` (can be scheduled externally), extracts/chunks content, embeds, and indexes vectors into Weaviate; exposes admin and internal APIs.
- NLP service: stateless preprocessing + embedding front-end; can run in fake mode for local/offline work.
- Data:
  - Postgres (backend): claim logs.
  - Postgres (collector): publishers/source_endpoints, articles, ingestion runs/logs.
  - Weaviate: `ArticleChunk` vectors for semantic search.
- External: Vertex AI for embeddings (NLP) and LLM generation (backend).
- Cross-cutting: correlation IDs propagated via headers; structured error envelopes; health checks at `/actuator/health` (backend/collector) and `/health` (NLP).

