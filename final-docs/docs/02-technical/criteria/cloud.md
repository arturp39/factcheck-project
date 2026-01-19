# Criterion: Cloud

## Architecture Decision Record

### Status
**Status:** Accepted (Partial)  
**Date:** 2026-01-16

### Context
We rely on Vertex AI and cloud hosting; deployments must stay env-driven and reproducible.

### Decision
- Deploy application services to **Google Cloud Run** (backend, news-collector, nlp-service) with per-service containers built via Cloud Build.
- Use **Cloud SQL for PostgreSQL** for both backend and collector databases; connections via env-driven JDBC URLs.
- Host **Weaviate** on a GCP VM (Docker) to keep vector search close to Cloud Run while retaining self-hosted control.
- Schedule ingestion runs via **Cloud Scheduler** hitting `/ingestion/run` on the collector (no in-app scheduler; Cloud Run is request-driven).
- Use **Vertex AI** for embeddings (`gemini-embedding-001`) and generation (`gemini-2.5-flash`), with credentials provided via env (`VERTEX_*`).
- Keep all endpoints configurable (Cloud SQL, Weaviate, Vertex) via environment variables managed per environment.

### Alternatives Considered
| Alternative | Pros | Cons | Why Not Chosen |
|-------------|------|------|----------------|
| Run everything on VMs | Full control | More ops toil, slower iteration | Cloud Run gives autoscaling and simple deploys |
| Managed vector store | Less ops | Vendor lock-in; local parity harder | Self-hosted Weaviate keeps schema control |

### Consequences
**Positive:** Cloud Run autoscaling; Cloud SQL managed DBs; Vertex AI; env-driven config keeps environments portable.  
**Negative:** Weaviate VM needs manual ops; no IaC for recreating Cloud Run/SQL yet; secrets must be supplied per environment.

## Implementation Details
### Project Structure
```
cloudbuild.backend.yaml
cloudbuild.news-collector.yaml
cloudbuild.nlp-service.yaml
backend/src/main/resources/application.yml      # env placeholders for Cloud SQL/Vertex/Weaviate
infra/.env.example                              # documents cloud-related vars
```

### Key Implementation Decisions
| Decision | Rationale |
|----------|-----------|
| Cloud Run for stateless services | Autoscaling and minimal ops per container |
| Cloud SQL for Postgres | Managed backups and maintenance for relational data |
| Weaviate on VM with Docker | Keep vector store under control; align with schema bootstrapper |
| Vertex auth helper with ADC or file path | Works locally and in Cloud Run with service accounts |
| Env-based base URLs and API keys | Avoid hardcoded endpoints and rotate easily |
| Fake embeddings toggle | Enables cloud-less development and cost control |

### Requirements Checklist
| # | Requirement | Status | Evidence |
|---|-------------|--------|----------|
| 1 | Uses cloud AI service | Done | Vertex AI via `VertexAuthHelper` |
| 2 | Cloud deployment target | Done | Cloud Run for all three services |
| 3 | Managed database | Done | Cloud SQL for Postgres (backend + collector) |
| 4 | Env-driven cloud config | Done | `application.yml`, `.env.example` |
| 5 | Cloud build pipeline | Partial | Cloud Build YAMLs exist; activation/IaC pending |

## Known Limitations
| Limitation | Impact | Potential Solution |
|------------|--------|-------------------|
| No Terraform/K8s manifests | Manual recreation risk | Add IaC (Terraform) for Cloud Run, SQL, VM, networking |
| Secrets management not automated | Risk of misconfig/secret sprawl | Use Secret Manager + per-service bindings |
| Weaviate VM ops manual | Potential drift/downtime | Add VM automation/managed backups; consider managed vector option later |

## References
- `cloudbuild.*.yaml`
- `backend/src/main/resources/application.yml`
- `infra/.env.example`
