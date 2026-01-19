# Troubleshooting

Containers unhealthy
- Check logs: `docker compose logs backend collector nlp weaviate postgres-backend postgres-collector`.
- Weaviate ready probe failing: ensure `WEAVIATE_PORT` not conflicting; verify host port free.
- Postgres failing health: confirm credentials in `infra/.env` match the container env vars.

Vertex errors
- 503/401 from LLM or embeddings: verify `VERTEX_PROJECT_ID`, credentials path is mounted/readable, and service account has aiplatform scope.
- For offline dev set `NLP_USE_FAKE_EMBEDDINGS=true` to avoid Vertex calls.

Evidence search empty
- Ensure collector ran at least once: `curl -X POST http://localhost:8081/ingestion/run`.
- Check Weaviate distance threshold `WEAVIATE_MAX_DISTANCE`; if too low, raise slightly (e.g., 0.6) and reindex.

DB migrations
- If schema drift occurs, drop volumes: `docker compose down -v` then `docker compose up --build`.

Correlation IDs
- Missing IDs auto-generated; to trace across services include `-H "X-Correlation-Id: 4f2c9c2e-5d43-4c31-8f0d-9b9b6a4c4e6e"` in requests and search logs for that value.

