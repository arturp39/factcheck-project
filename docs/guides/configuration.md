# Configuration

Env files
- Copy `infra/.env.example` to `infra/.env` and adjust.
- Docker Compose reads `infra/.env`; backend/collector/NLP containers read the same variables.

Key settings
- Database (collector): `COLLECTOR_POSTGRES_DB`, `COLLECTOR_POSTGRES_USER`, `COLLECTOR_POSTGRES_PASSWORD`, `COLLECTOR_POSTGRES_PORT`.
- Database (backend): `BACKEND_POSTGRES_DB`, `BACKEND_POSTGRES_USER`, `BACKEND_POSTGRES_PASSWORD`, `BACKEND_POSTGRES_PORT`.
- Backend: `BACKEND_PORT`, `APP_CLAIM_MAX_LENGTH`, `APP_SEARCH_TOP_K`, `APP_REST_CONNECT_TIMEOUT_MS`, `APP_REST_READ_TIMEOUT_MS`.
- Collector: `COLLECTOR_PORT`, `INGESTION_MAX_PARALLEL_SOURCES`, `INGESTION_INTERVAL_MS`, `SEARCH_EMBEDDING_DIMENSION`, `CRAWLER_USER_AGENT`.
- NLP: `NLP_PORT`, `NLP_USE_FAKE_EMBEDDINGS`, `NLP_VERTEX_*` limits.
- Vertex AI (backend): `VERTEX_PROJECT_ID`, `VERTEX_LOCATION`, `VERTEX_MODEL_NAME`, `VERTEX_CREDENTIALS_PATH`.
- Weaviate: `WEAVIATE_PORT`, `WEAVIATE_API_KEY`, `WEAVIATE_MAX_DISTANCE`, `WEAVIATE_DEFAULT_VECTORIZER=none`, `WEAVIATE_QUERY_DEFAULTS_LIMIT`.
  - For non-Docker runs set `WEAVIATE_BASE_URL` (backend + collector).
- NewsAPI: `NEWSAPI_API_KEY`, `NEWSAPI_BASE_URL`, `NEWSAPI_MAX_SOURCES_PER_REQUEST`, `NEWSAPI_MAX_PAGES_PER_BATCH`, `NEWSAPI_MAX_REQUESTS_PER_INGESTION`, `NEWSAPI_SORT_BY`.
- MBFC (RapidAPI): `RAPIDAPI_KEY` (optionally `MBFC_RAPIDAPI_BASE_URL`, `MBFC_RAPIDAPI_HOST`).

Profiles
- Collector uses `SPRING_PROFILES_ACTIVE` (default `prod` in `infra/.env`).
- Backend uses the default Spring profile unless set; both services run Flyway migrations from `classpath:db/migration`.

Correlation IDs
- Optional request header `X-Correlation-Id`. Services generate and echo when absent; logged consistently.

Rate/size limits
- Claim length: default 400 chars.
- Pagination: size 1-200.
- Collector search: embedding dimension must match `SEARCH_EMBEDDING_DIMENSION`.
- NLP limits: configurable max texts per request, max text length, and total chars.
