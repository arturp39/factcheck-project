# API Specs Reference

- `backend.openapi.yaml`: Claim verification/public API (port 8080).
- `collector.openapi.yaml`: Admin + internal ingestion/search API (port 8081).
- `nlp.openapi.yaml`: FastAPI embeddings/preprocess service (port 8000).

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
