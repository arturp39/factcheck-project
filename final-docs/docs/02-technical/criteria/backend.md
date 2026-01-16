# Criterion: Backend

## Architecture Decision Record

### Status
**Status:** Accepted (Done)  
**Date:** 2026-01-16

### Context
We need a reliable API and server-rendered UI to orchestrate claim verification, follow-ups, bias analysis, and persistence with validation and error handling.

### Decision
- Use Spring Boot 3 (Java 21) with MVC + REST controllers for both UI (`ClaimController`) and JSON APIs (`ClaimApiController`).
- Apply validation and length limits for claims/follow-ups; return structured errors via `GlobalExceptionHandler`.
- Persist claims and follow-ups in Postgres via JPA; run Flyway migrations on startup.

### Alternatives Considered
| Alternative | Pros | Cons | Why Not Chosen |
|-------------|------|------|----------------|
| Python/Node backend | Rapid prototyping | Less synergy with Java ingestion stack | Shared JVM skills and Spring ecosystem |
| SPA frontend + API | Richer UX | More surface area/time | MVP needs speed and simplicity |

### Consequences
**Positive:** Stable, type-safe API with clear validation and templated UI; easy containerization.  
**Negative:** Higher JVM footprint than lightweight runtimes.

## Implementation Details
### Project Structure
```
backend/controller       # UI + API controllers
backend/service          # Claim workflow, Vertex, Weaviate, persistence
backend/repository       # JPA repositories
backend/resources/db     # Flyway migrations
backend/resources/prompts# Prompt templates
backend/resources/templates/static # Thymeleaf + CSS
```

### Key Implementation Decisions
| Decision | Rationale |
|----------|-----------|
| Separate workflow/service layers | Keeps controllers thin and testable |
| JWT auth with UI cookies + API bearer tokens | Protects endpoints while keeping UI simple |
| Filter low-credibility sources in evidence | Avoids biasing verdicts with unreliable outlets |
| Parse LLM verdicts but store raw answers | Auditability + structured outputs |
| Limit claim length (400 chars) | Prevents runaway prompts and poor UX |

### Requirements Checklist
| # | Requirement | Status | Evidence |
|---|-------------|--------|----------|
| 1 | Stable REST endpoints | Done | `ClaimApiController` |
| 2 | Server-rendered UI for claims | Done | `templates/index.html`, `result.html` |
| 3 | JWT auth + roles | Done | `SecurityConfig`, `JwtAuthenticationFilter`, `AuthController` |
| 4 | Validation and error handling | Done | DTO checks + `GlobalExceptionHandler` |
| 5 | Persistence with migrations | Done | JPA entities + Flyway scripts |

## Known Limitations
| Limitation | Impact | Potential Solution |
|------------|--------|-------------------|
| Minimal automated tests | Risk of regressions | Add controller/service tests |
| No password reset/SSO | Limited enterprise readiness | Add reset flow or SSO integration |

## References
- `backend/controller/*`
- `backend/security/*`
- `backend/service/ClaimWorkflowService.java`
- `backend/resources/db/migration/*`
