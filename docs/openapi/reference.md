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
- Errors follow `ErrorResponse` envelope with `status`, `message`, `path`, `correlationId`.
- Examples include success and validation edges (empty claim, bad pagination).

Versioning:
- Current spec version: `1.0.0`.
- On contract changes, set a new `info.version` and keep prior breaking versions in `openapi/archive/`.