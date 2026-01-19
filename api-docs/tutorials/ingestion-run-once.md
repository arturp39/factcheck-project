# Tutorial: Run Ingestion Once

Goal: trigger a single ingestion run to fetch and index news content.

Prerequisites
- Stack running (`docker compose up --build`).
- Collector reachable (default `http://localhost:8081`).

Steps
1) Start ingestion for all enabled sources
   ```bash
   curl -s -X POST "http://localhost:8081/ingestion/run?correlationId=4f2c9c2e-5d43-4c31-8f0d-9b9b6a4c4e6e"
   ```
   Response: JSON `IngestionRunStartResponse` (runId, correlationId, tasksEnqueued, status).

2) Check latest runs
   ```bash
   curl -s "http://localhost:8081/admin/ingestion/logs?page=0&size=5"
   ```
   Inspect `status`, `articlesFetched/Processed/Failed`.

3) Inspect a specific run
   ```bash
   curl -s "http://localhost:8081/admin/ingestion/runs/{RUN_ID}"
   ```

4) (Optional) Abort a run
   ```bash
   curl -s -X POST "http://localhost:8081/admin/ingestion/runs/{RUN_ID}/abort"
   ```

What to expect
- Each trigger runs ingestion again (non-idempotent).
- Correlation IDs echo in responses and logs for tracing.
- Evidence availability in backend depends on successful ingestion + indexing into Weaviate.
