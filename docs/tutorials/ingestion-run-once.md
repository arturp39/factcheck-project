# Tutorial: Run Ingestion Once

Goal: trigger a single ingestion run to fetch and index news content.

Prerequisites
- Stack running (`docker compose up --build`).
- Collector reachable (default `http://localhost:8081`).

Steps
1) Start ingestion for all enabled sources  
   ```bash
   curl -s -X POST "http://localhost:8081/admin/ingestion/run?correlationId=demo-1"
   ```
   Response: plain text with correlationId.

2) Check latest runs  
   ```bash
   curl -s "http://localhost:8081/admin/ingestion/logs?page=0&size=5"
   ```
   Inspect `status`, `articlesFetched/Processed/Failed`.

3) Inspect a specific run  
   ```bash
   curl -s "http://localhost:8081/admin/ingestion/runs/{RUN_ID}"
   ```

4) (Optional) Trigger a single source  
   ```bash
   curl -s -X POST "http://localhost:8081/admin/ingestion/run/{SOURCE_ID}?correlationId=demo-2"
   ```

What to expect
- Each trigger runs ingestion again (non-idempotent).
- Correlation IDs echo in responses and logs for tracing.
- Evidence availability in backend depends on successful ingestion + indexing into Weaviate.