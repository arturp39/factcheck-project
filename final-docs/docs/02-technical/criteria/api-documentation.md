# Criterion: API Documentation

## Architecture Decision Record

### Status
**Status:** Accepted (Done)  
**Date:** 2026-01-16

### Context
Multiple consumers (UI, operators, potential integrations) need clear descriptions of backend and collector APIs, including Markdown and OpenAPI references.

### Decision
- Document REST endpoints in Markdown stored in the docs folder (`final-docs/docs/appendices/api-reference.md` + top-level `docs/`) with request/response examples and status codes.
- Maintain OpenAPI YAML specs in `docs/openapi/*.yaml` plus a short `docs/openapi/reference.md`.
- Include authentication details (JWT cookie for UI, Bearer token for API) and correlation headers.
- Enable Springdoc OpenAPI for interactive docs while keeping Markdown as the primary narrative reference.

### Alternatives Considered
| Alternative | Pros | Cons | Why Not Chosen |
|-------------|------|------|----------------|
| Auto-generated OpenAPI via Springdoc | Interactive docs, client generation | Additional dependency/config | Implemented alongside Markdown |
| Postman collection only | Easy sharing | Not versioned with code | Prefer docs in repo |

### Consequences
**Positive:** Versioned docs live with code; Markdown is easy to read and update alongside controllers.  
**Negative:** Markdown and YAML specs are manual and can drift without CI checks.

## Implementation Details
### Project Structure
```
final-docs/docs/appendices/api-reference.md   # Primary endpoint documentation (backend + collector + NLP)
docs/                                        # Additional API docs (if provided by user)
docs/openapi/*.yaml                          # OpenAPI specs per service
docs/openapi/reference.md                    # OpenAPI reference index
backend/controller/*                         # Claim and admin APIs
news-collector/controller/*                   # Ingestion/admin/internal APIs
nlp-service/main.py                           # NLP endpoints
```

### Key Implementation Decisions
| Decision | Rationale |
|----------|-----------|
| Correlation header documented | Aids support and tracing |
| Clear pagination/validation rules | Reduces misuse of endpoints |

### Requirements Checklist
| # | Requirement | Status | Evidence |
|---|-------------|--------|----------|
| 1 | Document main APIs | Done | `final-docs/docs/appendices/api-reference.md`, `docs/` |
| 2 | Include request/response examples | Done | Markdown examples in api-reference |
| 3 | Auth requirements documented | Done | JWT/cookie details in api-reference |
| 4 | Correlation header documented | Done | Mentioned in api-reference |
| 5 | Machine-readable spec (OpenAPI) | Done | Springdoc UI available under `/swagger-ui.html` |

## Known Limitations
| Limitation | Impact | Potential Solution |
|------------|--------|-------------------|
| Docs can drift from code | Risk of mismatch | Add CI check or regenerate from controllers |

## References
- `final-docs/docs/appendices/api-reference.md`
- `backend/controller/*`
- `news-collector/controller/*`
