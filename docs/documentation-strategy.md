# Documentation Strategy & Gap Assessment

Standards & tools
- Specs in OpenAPI 3.0 (`docs/openapi/*.yaml`); lint with Redocly CLI.
- Error envelopes standardized as `ErrorResponse` in backend/collector; NLP uses FastAPI `detail` for 4xx/503.
- Correlation IDs via `X-Correlation-Id`.
- Examples included for success and validation failures.
- Versioning: bump `info.version`; archive breaking changes under `docs/openapi/archive/` (create when needed).
- Naming conventions: OpenAPI files use kebab-case (e.g., `nlp.openapi.yaml`); schema/component names use PascalCase; examples are in `docs/examples/`.

Structure
- `/openapi` for machine-readable contracts.
- `/guides` for setup/config/troubleshooting.
- `/tutorials` for step-by-step workflows.
- `/architecture` for context/flows.
- `/data` for storage schemas.
- `/examples` for copy/paste calls.

Documentation gaps
- Authentication/authorization docs are not present, not implemented in the project.
- Rate limiting is not documented and not enforced.
- Release notes/versioning, changelog not present.

