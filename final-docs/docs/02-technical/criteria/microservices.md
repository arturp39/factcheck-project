# Criterion: Microservices

## Architecture Decision Record

### Status
**Status:** Accepted (Done)  
**Date:** 2026-01-16

### Context
We need clear separation of responsibilities: claim UI/API, ingestion/indexing, and NLP/embeddings. Isolation reduces blast radius and lets each stack use the best-fit language/runtime.

### Decision
- Split into three services:
  - **Backend:** claim verification UI/API, RAG orchestration, storage of claims/follow-ups/bias.
  - **News Collector:** source catalog, ingestion runs, content extraction, chunking, embedding requests, Weaviate indexing.
  - **NLP Service:** preprocessing and embeddings (real Vertex or fake mode), with correlation-aware logging.
- Share only over HTTP/REST with correlation ID propagation.

### Alternatives Considered
| Alternative | Pros | Cons | Why Not Chosen |
|-------------|------|------|----------------|
| Monolith | Simpler deploy | Harder to tune NLP stack; larger blast radius | Needed stack flexibility and isolation |
| Many smaller functions | Fine-grained scaling | More operational overhead | MVP scope favors three cohesive services |

### Consequences
**Positive:** Tech fit per domain (Java for business/ingestion, Python for NLP); failures contained; independent deployments.  
**Negative:** More services to operate and observe; network latency between components.

## Implementation Details
### Project Structure
```
backend/                  # Claim UI/API service
news-collector/           # Ingestion/indexing service
nlp-service/              # NLP/embeddings service
```

### Key Implementation Decisions
| Decision | Rationale |
|----------|-----------|
| Correlation ID filter/middleware in every service | End-to-end tracing |
| Separate Postgres DBs | Clear data ownership per service |
| Collector writes to Weaviate; backend queries it | Clear ownership of indexing vs retrieval |

### Requirements Checklist
| # | Requirement | Status | Evidence |
|---|-------------|--------|----------|
| 1 | >= 2 services with clear domains | Done | Backend, collector, NLP |
| 2 | REST communication | Done | Controllers/clients across services |
| 3 | Isolation of data stores | Done | Separate Postgres instances |

## Known Limitations
| Limitation | Impact | Potential Solution |
|------------|--------|-------------------|
| No service registry/mesh | Manual host config | Use service discovery or env config per env |
| No async messaging | Coupled to HTTP latency | Add queue for ingestion tasks later |

## References
- `backend/config/CorrelationIdFilter.java`
- `news-collector/config/CorrelationIdFilter.java`
- `nlp-service/main.py`
