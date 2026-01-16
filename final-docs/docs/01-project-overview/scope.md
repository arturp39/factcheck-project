# Project Scope

## In Scope

| Feature | Description | Priority |
|---------|-------------|----------|
| Claim verification | Submit a text claim and receive verdict + explanation | Must |
| Evidence retrieval | Vector search over indexed news chunks via Weaviate | Must |
| Follow-ups & bias | Ask follow-up questions and run bias analysis on verdict | Should |
| Corpus ingestion | Collect, clean, chunk, embed, and index news articles | Must |
| Admin/ops endpoints | Trigger ingestion runs, view logs, sync sources (NewsAPI/MBFC) | Should |

## Out of Scope

| Feature | Reason | When Possible |
|---------|--------|---------------|
| Advanced auth (SSO, password reset, MFA) | Basic username/password only | Future enhancement |
| Non-text claims (audio/video) | Pipeline built for text-only | Future R&D |
| Multi-language support | NLP and prompts tuned for English | Future phase |

## Assumptions

| # | Assumption | Impact if Wrong | Probability |
|---|------------|-----------------|-------------|
| 1 | Claims and articles are English | Model quality may drop on other languages | Medium |
| 2 | Access to Vertex AI + Weaviate endpoints is available | Retrieval/LLM flow fails without them | Low |
| 3 | NewsAPI and MBFC quotas are sufficient | Ingestion freshness/bias mapping degrades if limited | Medium |

## Constraints

Limitations that affect the project:

| Constraint Type | Description | Mitigation |
|-----------------|-------------|------------|
| **Time** | MVP functionality over full polished UX | Keep UI simple (Thymeleaf) |
| **Budget** | Vertex/NewsAPI/MBFC costs | Support fake embeddings mode; throttle Vertex calls |
| **Technology** | RAG pipeline relies on Weaviate schema | Schema bootstrapper + validation |
| **Resources** | Single developer | Heavy automation via ingestion jobs and scripts |
| **External** | External APIs can throttle or block | Correlation IDs + request caps; robots checks; manual reruns |

## Dependencies

| Dependency | Type | Owner | Status |
|------------|------|-------|--------|
| Vertex AI (Gemini + embeddings) | External | Google Cloud | Configured via env, needs credentials |
| Weaviate vector DB | External | Self-hosted | GCE VM (Docker) in prod; docker-compose in dev |
| PostgreSQL (backend + collector) | Technical | Cloud SQL | Cloud SQL in prod; docker-compose in dev |
| NewsAPI | External | NewsAPI.org | Key required for live ingestion |
| MBFC via RapidAPI | External | RapidAPI | Key required for bias sync |
