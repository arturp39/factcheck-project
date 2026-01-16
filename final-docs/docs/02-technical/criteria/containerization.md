# Criterion: Containerization

## Architecture Decision Record

### Status
**Status:** Accepted (Done)  
**Date:** 2026-01-16

### Context
We need reproducible environments for the backend, collector, NLP service, Postgres, and Weaviate to run locally and for demos.

### Decision
- Package each service with its own Dockerfile (Java services via Maven wrapper; Python via uvicorn).
- Use Docker Compose (`infra/docker-compose.yml`) to orchestrate services plus Postgres and Weaviate with shared `.env`.
- Document environment variables in `.env.example`.

### Alternatives Considered
| Alternative | Pros | Cons | Why Not Chosen |
|-------------|------|------|----------------|
| Manual host installs | Fewer containers | Fragile, hard to reproduce | Containers give parity and speed |
| K8s-only manifests | Production-ready | Overkill for MVP/local dev | Compose is simpler for now |

### Consequences
**Positive:** One command brings up the full stack; isolates dependencies; easy teardown.  
**Negative:** No autoscaling or health-aware restarts beyond Docker defaults.

## Implementation Details
### Project Structure
```
backend/Dockerfile
news-collector/Dockerfile
nlp-service/Dockerfile
infra/docker-compose.yml
infra/.env.example
```

### Key Implementation Decisions
| Decision | Rationale |
|----------|-----------|
| Separate DB containers | Ownership boundaries for backend vs collector data |
| Compose networking | Simple service discovery by name |
| Fake embeddings toggle | Allows offline/demo runs without Vertex costs |

### Requirements Checklist
| # | Requirement | Status | Evidence |
|---|-------------|--------|----------|
| 1 | Container images per service | Done | Dockerfiles in each repo |
| 2 | Orchestrated local stack | Done | `docker-compose.yml` |
| 3 | Env-based configuration | Done | `.env.example` |

## Known Limitations
| Limitation | Impact | Potential Solution |
|------------|--------|-------------------|
| No healthchecks in compose | Harder auto-restart on failure | Add Docker healthchecks and restart policies |
| No multi-arch images documented | Limits ARM users | Build/push multi-arch via CI |

## References
- `infra/docker-compose.yml`
- Service Dockerfiles
- `infra/.env.example`
