# API Reference

## Overview

Base URLs (production):
- Backend: https://factcheck-backend-804697237544.us-central1.run.app
- News collector: https://factcheck-news-collector-804697237544.us-central1.run.app
- NLP service: https://factcheck-nlp-service-804697237544.us-central1.run.app

Authentication:
- UI uses a JWT cookie (`factcheck_token` by default) set after /auth/login or /auth/register.
- API clients use Authorization: Bearer <token> from /api/auth/login or /api/auth/register.
- Correlation header: X-Correlation-Id (optional; auto-generated if missing).

**Important:** The fact-checker is optimized for **recent news**. If the corpus has no fresh coverage for a claim (e.g., static facts like landmark locations), responses may be "unclear" because there is no evidence to ground them.
Evidence items are filtered when MBFC labels indicate questionable bias or low credibility.

## Authentication (Backend)

### POST /api/auth/login

`http
POST /api/auth/login
Content-Type: application/json

{ "username": "user", "password": "secret123" }
`

`json
{ "token": "...", "tokenType": "Bearer" }
`

### POST /api/auth/register

`http
POST /api/auth/register
Content-Type: application/json

{ "username": "newuser", "password": "secret123" }
`

`json
{ "token": "...", "tokenType": "Bearer" }
`

### UI Auth Routes

- GET /login, POST /auth/login
- GET /register, POST /auth/register
- GET /auth/logout

## Backend Endpoints (require JWT)

Note: `/api/claims/**` is accessible to USER and ADMIN. Other `/api/**` endpoints (including Swagger) require ADMIN.

### POST /api/claims/verify
Submit a claim and receive a verdict with evidence.

`http
POST /api/claims/verify
Authorization: Bearer <token>
Content-Type: application/json

{ "claim": "The city council approved the transit plan yesterday." }
`

`json
{
  "correlationId": "...",
  "claimId": 42,
  "claim": "The city council approved the transit plan yesterday.",
  "verdict": "true",
  "explanation": "...",
  "evidence": [
    {
      "title": "...",
      "source": "AP",
      "publishedAt": "2026-01-14T12:00:00",
      "snippet": "..."
    }
  ]
}
`

| Status Code | Description |
|-------------|-------------|
| 200 | Success |
| 400 | Validation error (empty/too long claim) |
| 401 | Unauthorized (missing/invalid token) |
| 500 | Internal error (Vertex/Weaviate issues) |

---

### GET /api/claims
List claims with pagination. Returns only the caller's claims unless the user is ADMIN.

Parameters: page (default 0), size (1-200, default 20).

---

### GET /api/claims/{id}
Get a single claim with stored verdict/explanation/bias/model answer. Access is limited to the owner unless ADMIN.

---

### GET /api/claims/{id}/evidence
Return evidence used for the claim (recomputed via Weaviate search).

---

### GET /api/claims/{id}/history
Return claim context plus follow-up history.

---

### POST /api/claims/{id}/followup
Ask a follow-up question about the claim.

`json
{ "question": "What is the primary source cited?" }
`

---

### POST /api/claims/{id}/bias
Generate bias analysis based on claim evidence.

---

## News Collector Endpoints (internal)
These endpoints are intended for internal use and are not protected by app-level auth; restrict access via ingress/IAM.

### POST /ingestion/run
Starts an ingestion run. Returns 202 Accepted with run ID and task count. 409 if a run is already active.

### POST /ingestion/task
Handles a specific ingestion task (used by scheduler/worker). Returns 200 on success, 204 if the payload is invalid and ignored.

### GET /admin/ingestion/logs
Paginated list of ingestion logs.

### GET /admin/ingestion/runs/{id}
Run details with status.

### POST /admin/ingestion/runs/abort-active
Abort the latest running ingestion.

### Source Catalog APIs
- GET /admin/newsapi/sources, POST /admin/newsapi/sources/sync
- GET/POST/PATCH/DELETE /admin/mbfc/sources, POST /admin/mbfc/sync, POST /admin/mbfc/map-publishers
- GET/POST/PATCH /admin/publishers
- GET/POST/PATCH /admin/sources
- POST /admin/sources/{id}/enable, POST /admin/sources/{id}/disable

### Article Access APIs
- GET /internal/articles (list or search titles)
- POST /internal/articles/search (vector search via Weaviate with correlation ID)
- GET /internal/articles/{id} (metadata)
- GET /internal/articles/{id}/content (full text)

## NLP Service
The NLP service has no app-level auth and is intended for internal access. In production it can be protected with Cloud Run IAM; backend and collector include a Google-signed ID token when enabled.
- POST /preprocess - sentence splitting
- POST /embed - embeddings for texts
- POST /embed-sentences - embeddings per sentence (used for semantic chunking)
- GET /health, GET /metrics

## Error Responses

| Error Code | Message | Description |
|------------|---------|-------------|
| 400 | Validation failed | Missing/empty fields or size limits exceeded |
| 401 | Unauthorized | Missing/invalid credentials |
| 403 | Forbidden | Access denied (e.g., not owner) |
| 409 | Run already in progress | Ingestion run exists |
| 429 | Too Many Requests | MBFC quota exceeded (collector) |
| 500 | Internal Server Error | Upstream failure (Vertex/Weaviate/DB) |
| 503 | Embedding generation failed | Vertex unavailable (NLP service) |

## Swagger/OpenAPI

OpenAPI is available at /v3/api-docs and Swagger UI at /swagger-ui.html (ADMIN only).
