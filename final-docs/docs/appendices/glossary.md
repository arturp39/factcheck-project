# Glossary

| Term | Definition |
|------|------------|
| Claim | A user-provided statement to verify |
| Verdict | Parsed model classification: true/false/mixed/unclear |
| Evidence chunk | Short passage retrieved from Weaviate and shown to the user |
| Follow-up | User question asked after the initial verdict, tied to a claim |
| Bias analysis | Summary of potential bias based on MBFC metadata of sources |
| Ingestion run | Batch process that fetches and indexes articles from all endpoints |
| Article chunk | Semantic chunk of an article stored in Weaviate with vector embedding |
| Correlation ID | UUID carried across services for tracing requests |

## Acronyms

| Acronym | Full Form | Description |
|---------|-----------|-------------|
| API | Application Programming Interface | HTTP endpoints used by services/clients |
| UI | User Interface | Thymeleaf pages served by backend |
| RAG | Retrieval-Augmented Generation | Combines search (Weaviate) with LLM (Gemini) |
| JWT | JSON Web Token | Signed token used for auth |
| MBFC | Media Bias/Fact Check | External bias/factuality dataset via RapidAPI |
| NLP | Natural Language Processing | Preprocess and embeddings service |

## Domain-Specific Terms

### Fact-Checking

| Term | Definition |
|------|------------|
| Evidence snippet | Title + snippet returned from vector search |
| Bias metadata | MBFC bias/factual_reporting/credibility fields attached to sources |

### Ingestion

| Term | Definition |
|------|------------|
| Source endpoint | RSS/API entry configured for a publisher |
| Block reason | Reason an endpoint was paused (robots/CAPTCHA/extraction failure) |
