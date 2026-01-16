# API Specs Reference

- `backend.openapi.yaml`: Claim verification/public API (Cloud Run backend URL).
- `collector.openapi.yaml`: Admin + internal ingestion/search API (Cloud Run collector URL).
- `nlp.openapi.yaml`: FastAPI embeddings/preprocess service (Cloud Run NLP URL).

Cloud Run URLs:
- Backend: https://factcheck-backend-804697237544.us-central1.run.app
- Collector: https://factcheck-news-collector-804697237544.us-central1.run.app
- NLP: https://factcheck-nlp-service-804697237544.us-central1.run.app

Validation:
- Quick check: `npx @redocly/cli lint docs/openapi/backend.openapi.yaml` (repeat for other files).
- Visual: drop files into https://editor.swagger.io or Redocly preview.

Conventions:
- Correlation ID header `X-Correlation-Id` is optional on all services; service echoes/generated.
- Health: `/actuator/health` on backend and collector (Actuator), `/health` on NLP.
- Errors: backend/collector return `ErrorResponse` (`status`, `message`, `path`, `correlationId`); NLP uses FastAPI `{"detail": "..."}`
  for 4xx/503 and `ErrorResponse` only on unhandled 500s.
- Examples include success and validation edges (empty claim, bad pagination).

Versioning:
- Current spec version: `1.0.0`.
- On contract changes, set a new `info.version` and (if needed) add an `docs/openapi/archive/` folder for prior breaking versions.

