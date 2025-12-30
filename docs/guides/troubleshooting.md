# Troubleshooting

Containers unhealthy
- Check logs: `docker compose logs backend collector nlp weaviate postgres`.
- Weaviate ready probe failing: ensure `WEAVIATE_PORT` not conflicting; verify host port free.
- Postgres failing health: confirm credentials in `.env` match mounted init scripts.

Vertex errors
- 503/401 from LLM or embeddings: verify `VERTEX_PROJECT_ID`, credentials path is mounted/readable, and service account has aiplatform scope.
- For offline dev set `NLP_USE_FAKE_EMBEDDINGS=true` to avoid Vertex calls.

Evidence search empty
- Ensure collector ran at least once: `curl http://localhost:8081/admin/ingestion/run`.
- Check Weaviate distance threshold `WEAVIATE_MAX_DISTANCE`; if too low, raise slightly (e.g., 0.6) and reindex.

DB migrations
- If schema drift occurs, drop volumes: `docker compose down -v` then `docker compose up --build`.

Correlation IDs
- Missing IDs auto-generated; to trace across services include `-H "X-Correlation-Id: test-123"` in requests and search logs for that value.