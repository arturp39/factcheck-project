# Tutorial: Run Ingestion Once

Goal: trigger a single ingestion run to fetch and index news content.

Prerequisites
- Stack running (`docker compose up --build`).
- Collector reachable (default `https://factcheck-news-collector-804697237544.us-central1.run.app`).

Steps
1) Start ingestion for all enabled sources  
   ```bash
   curl -s -X POST "https://factcheck-news-collector-804697237544.us-central1.run.app/ingestion/run?correlationId=demo-1"
   ```
   Response: JSON `IngestionRunStartResponse` (runId, correlationId, tasksEnqueued, status).

2) Check latest runs  
   ```bash
   curl -s "https://factcheck-news-collector-804697237544.us-central1.run.app/admin/ingestion/logs?page=0&size=5"
   ```
   Inspect `status`, `articlesFetched/Processed/Failed`.

3) Inspect a specific run  
   ```bash
   curl -s "https://factcheck-news-collector-804697237544.us-central1.run.app/admin/ingestion/runs/{RUN_ID}"
   ```
 
4) (Optional) Abort a run  
   ```bash
   curl -s -X POST "https://factcheck-news-collector-804697237544.us-central1.run.app/admin/ingestion/runs/{RUN_ID}/abort"
   ```

What to expect
- Each trigger runs ingestion again (non-idempotent).
- Correlation IDs echo in responses and logs for tracing.
- Evidence availability in backend depends on successful ingestion + indexing into Weaviate.

