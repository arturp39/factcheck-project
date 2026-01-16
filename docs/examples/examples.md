# Examples

Backend
- Verify claim:
  ```bash
  curl -s -X POST https://factcheck-backend-804697237544.us-central1.run.app/api/claims/verify \
    -H "Content-Type: application/json" \
    -d '{"claim":"Water boils at 100C at sea level."}'
  ```
- Paginate claims:
  ```bash
  curl -s "https://factcheck-backend-804697237544.us-central1.run.app/api/claims?page=0&size=5"
  ```
- Follow-up:
  ```bash
  curl -s -X POST https://factcheck-backend-804697237544.us-central1.run.app/api/claims/123/followup \
    -H "Content-Type: application/json" \
    -d '{"question":"Why is the verdict false?"}'
  ```

Collector
- Trigger ingestion for all sources:
  ```bash
  curl -s -X POST "https://factcheck-news-collector-804697237544.us-central1.run.app/ingestion/run?correlationId=demo-1"
  ```
- List sources:
  ```bash
  curl -s https://factcheck-news-collector-804697237544.us-central1.run.app/admin/sources
  ```
- Search article chunks (requires embedding vector):
  ```bash
  curl -s -X POST https://factcheck-news-collector-804697237544.us-central1.run.app/internal/articles/search \
    -H "Content-Type: application/json" \
    -d '{"embedding":[0.1,0.2,0.3], "limit":3, "minScore":0.7}'
  ```

NLP
- Health:
  ```bash
  curl -s https://factcheck-nlp-service-804697237544.us-central1.run.app/health
  ```
- Embed text:
  ```bash
  curl -s -X POST https://factcheck-nlp-service-804697237544.us-central1.run.app/embed \
    -H "Content-Type: application/json" \
    -d '{"texts":["Example sentence one.","Example sentence two."]}'
  ```

