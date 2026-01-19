# 4. Retrospective

This section reflects on the project development process, lessons learned, and future improvements.

## What Went Well

### Technical Successes

- End-to-end RAG pipeline: evidence retrieval, grounded prompting, and auditable verdict storage.
- Semantic chunking with sentence embeddings and boundary detection for better long-article retrieval.
- Production ingestion pipeline: robots-aware HTML extraction plus Cloud Scheduler + Cloud Tasks orchestration.

### Process Successes

- Docker Compose enabled quick full-stack runs.
- Clear separation of services reduced regression risk when iterating on NLP vs backend.
- Prompt files in version control simplified tuning.

### Personal Achievements

- Built first end-to-end Weaviate + Vertex pipeline.
- Implemented correlation-aware logging in both Java and Python stacks.
- Learned to balance cost (fake embeddings) with functionality.

## What Didn't Go As Planned

| Planned | Actual Outcome | Cause | Impact |
|---------|---------------|-------|--------|
| Automatic scheduled ingestion | Scheduled via Cloud Scheduler + Cloud Tasks (daily 00:00) | Time constraints | Low |
| Rich frontend UX | Kept minimal Thymeleaf views | Scope control | Low |
| Full auth/roles | Basic JWT only; no SSO/reset | Time constraints | Low/medium |

### Challenges Encountered

1. **Vertex quota and latency**
   - Problem: Embedding/generation calls can be slow or fail without credentials.
   - Impact: Verification latency spikes.
   - Resolution: Added fake embeddings mode and retries in NLP service.

2. **Robots and site blocking**
   - Problem: Some sources block scraping or return CAPTCHAs.
   - Impact: Ingestion logs partial/failed, fewer articles.
   - Resolution: Robots checks, block reasons, and block counters in EndpointIngestionJob.

## Technical Debt & Known Issues

| ID | Issue | Severity | Description | Potential Fix |
|----|-------|----------|-------------|---------------|
| TD-001 | No SSO/password reset | Medium | Manual user management and no recovery flow | Add reset + SSO integration |
| TD-002 | Limited retries/backoff | Medium | External API hiccups bubble up | Add resilience4j + queues |
| TD-003 | Limited test coverage | Medium | Unit tests exist but coverage is incomplete | Add integration tests and CI gate |

### Code Quality Issues

- No shared API client between services; contracts are implicit.
- Error messages for LLM failures are generic.
- Frontend templates lack accessibility testing.

## Future Improvements (Backlog)

### High Priority

1. **Harden authentication**
   - Value: Password reset, MFA/SSO readiness, and safer admin controls.
   - Effort: Medium (reset flow + IdP integration).

2. **Automated retries/backoff for ingestion**
   - Value: Keeps corpus fresh when sources fail temporarily.
   - Effort: Medium (retry policies + queue).

### Medium Priority

3. **Structured LLM outputs**
   - Value: More robust verdict parsing and analytics.
   - Effort: Medium (JSON prompts + validators).

4. **Observability stack**
   - Value: Faster debugging and capacity planning.
   - Effort: Medium (Prometheus/Grafana or Cloud Logging dashboards).

### Nice to Have

5. UI polish and accessibility review
6. Export claims as PDF/CSV
7. Active learning loop to flag weak evidence

## Lessons Learned

### Technical Lessons

| Lesson | Context | Application |
|--------|---------|-------------|
| Keep prompts in files | Prompt tuning in code is risky | Version prompts and diff them like code |
| Correlation IDs are cheap value | Multi-service debugging | Standardize headers early |
| Semantic chunking helps | Long-form articles retrieval | Use sentence embeddings to place boundaries |

### Process Lessons

| Lesson | Context | Application |
|--------|---------|-------------|
| Start with end-to-end slice | Many dependencies | Build vertical slice before optimizing |
| Budget for external API errors | Vertex/NewsAPI quotas | Add fake modes and retries early |

### What Would Be Done Differently

| Area | Current Approach | What Would Change | Why |
|------|-----------------|-------------------|-----|
| Planning | Feature-first | Add resiliency tasks earlier | Avoid retrofitting retries |
| Technology | No queue | Add lightweight queue (SQS/Kafka) | Better ingestion throughput |
| Process | Minimal tests | Add contract tests between services | Prevent integration regressions |
| Scope | Basic JWT only | Add SSO + reset flow | Better enterprise readiness |

## Personal Growth

### Skills Developed

| Skill | Before Project | After Project |
|-------|---------------|---------------|
| RAG design | Beginner | Intermediate |
| Spring Boot + JPA | Intermediate | Advanced |
| FastAPI + Vertex AI | Beginner | Intermediate |

### Key Takeaways

1. Observability pays dividends once multiple services are involved.
2. Evidence quality hinges on chunking strategy as much as on the model.
3. Simple UIs are fine when the pipeline behind them is solid.

---

*Retrospective completed: 2026-01-16*
