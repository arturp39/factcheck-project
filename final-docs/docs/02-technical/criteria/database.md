# Criterion: Database

## Architecture Decision Record

### Status
**Status:** Accepted (Done)  
**Date:** 2026-01-16

### Context
The system must persist claims/follow-ups/bias and manage ingestion/catalog data with deduplication, status tracking, and MBFC enrichment, all with schema control and migrations.

### Decision
- Use PostgreSQL with separate schemas/databases for backend and collector.
- Manage schema via Flyway migrations in each service.
- Normalize ingestion/catalog tables (publishers, endpoints, articles, logs) and backend claim tables with indexes and foreign keys.

### Alternatives Considered
| Alternative | Pros | Cons | Why Not Chosen |
|-------------|------|------|----------------|
| Single shared DB | Simpler infra | Blurred ownership, migration coupling | Separation keeps domains clean |
| NoSQL for articles | Flexible | Weaviate already handles unstructured vectors; need relational integrity | Relational fits catalog/logging needs |

### Consequences
**Positive:** Clear relational model, referential integrity, migration history.  
**Negative:** Two DB instances to operate/back up.

## Implementation Details
### Project Structure
```
backend/resources/db/migration          # claim_log, claim_followup
news-collector/resources/db/migration   # content schema (articles, endpoints, runs, logs, MBFC)
```

### Key Implementation Decisions
| Decision | Rationale |
|----------|-----------|
| Hash-based dedup for articles | Avoid duplicate indexing across endpoints |
| Claim ownership via owner_username | Enforces per-user claim access |
| Status fields for runs/logs | Trace ingestion outcomes and failures |
| Indexes on created_at and FK columns | Faster queries for history and logs |

### Requirements Checklist
| # | Requirement | Status | Evidence |
|---|-------------|--------|----------|
| 1 | Normalized schema | Done | `articles`, `publishers`, `claim_log`, `app_user`, etc. |
| 2 | Indexes/FKs enforced | Done | FK relations + created_at indexes |
| 3 | Migrations under version control | Done | Flyway scripts in both services |

## Known Limitations
| Limitation | Impact | Potential Solution |
|------------|--------|-------------------|
| No automated backups described | Risk of data loss | Add backup job in infra |
| User table stores only local credentials | Limited enterprise auth | Integrate with SSO or external IdP |

## References
- `backend/src/main/resources/db/migration/*`
- `news-collector/src/main/resources/db/migration/*`
- `final-docs/docs/appendices/db-schema.md`
