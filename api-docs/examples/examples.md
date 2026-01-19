# Examples

Backend
- Login (get JWT token):
  ```bash
  curl -s -X POST http://localhost:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"change_me"}'
  ```
- If needed, register a user:
  ```bash
  curl -s -X POST http://localhost:8080/api/auth/register \
    -H "Content-Type: application/json" \
    -d '{"username":"user1","password":"change_me"}'
  ```
- Verify claim (requires `Authorization: Bearer <token>`):
  ```bash
  curl -s -X POST http://localhost:8080/api/claims/verify \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d '{"claim":"Water boils at 100C at sea level."}'
  ```
- Paginate claims:
  ```bash
  curl -s "http://localhost:8080/api/claims?page=0&size=5" \
    -H "Authorization: Bearer $TOKEN"
  ```
- Follow-up:
  ```bash
  curl -s -X POST http://localhost:8080/api/claims/123/followup \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d '{"question":"Why is the verdict false?"}'
  ```

Collector
- Trigger ingestion for all sources:
  ```bash
  curl -s -X POST "http://localhost:8081/ingestion/run?correlationId=4f2c9c2e-5d43-4c31-8f0d-9b9b6a4c4e6e"
  ```
- List sources:
  ```bash
  curl -s http://localhost:8081/admin/sources
  ```
- Search article chunks (requires embedding vector):
  ```bash
  curl -s -X POST http://localhost:8081/internal/articles/search \
    -H "Content-Type: application/json" \
    -d '{"embedding":[0.01,0.02,0.03,0.04,0.05,0.06,0.07,0.08], "limit":3, "minScore":0.7}'
  ```
  Note: embedding length must match `SEARCH_EMBEDDING_DIMENSION` (default 768).

NLP
- Health:
  ```bash
  curl -s http://localhost:8000/health
  ```
- Embed text:
  ```bash
  curl -s -X POST http://localhost:8000/embed \
    -H "Content-Type: application/json" \
    -d '{"texts":["Example sentence one.","Example sentence two."]}'
  ```
- Embed sentences:
  ```bash
  curl -s -X POST http://localhost:8000/embed-sentences \
    -H "Content-Type: application/json" \
    -d '{"sentences":["Sentence one.","Sentence two."]}'
  ```
