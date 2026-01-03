# Configuration

Env files
- Copy `factcheck-platform/.env.example` to `.env` and adjust.
- Docker Compose reads `.env`; backend/collector Spring apps also read the same variables inside containers.

Key settings
- Database: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT`.
- Backend: `BACKEND_PORT`, `APP_CLAIM_MAX_LENGTH`, `APP_SEARCH_TOP_K`, `WEAVIATE_MAX_DISTANCE`.
- Collector: `COLLECTOR_PORT`, `INGESTION_MAX_PARALLEL_SOURCES`, `INGESTION_INTERVAL_MS`, `SEARCH_EMBEDDING_DIMENSION`, crawler UA.
- NLP: `NLP_PORT`, `NLP_USE_FAKE_EMBEDDINGS`, `NLP_VERTEX_*`, `NLP_MAX_*` limits.
- Vertex AI: `VERTEX_PROJECT_ID`, `VERTEX_LOCATION`, `VERTEX_MODEL_NAME`, `VERTEX_CREDENTIALS_PATH` (backend); `GCP_*`/`NLP_VERTEX_*` (NLP).
- Weaviate: `WEAVIATE_BASE_URL`, `WEAVIATE_API_KEY`, `WEAVIATE_PORT`, `WEAVIATE_DEFAULT_VECTORIZER=none`, `WEAVIATE_QUERY_DEFAULTS_LIMIT`.

Profiles
- Backend/collector default to `prod` profile; DB schema is applied by Flyway migrations in `classpath:db/migration`.

Correlation IDs
- Optional request header `X-Correlation-Id`. Services generate and echo when absent; logged consistently.

Rate/size limits
- Claim length: default 400 chars.
- Pagination: size 1-200.
- Collector search: embedding dimension must match `SEARCH_EMBEDDING_DIMENSION`.
- NLP limits: configurable max texts per request, max text length, and total chars.