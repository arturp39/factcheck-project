# 2. Technical Implementation

This section covers the technical architecture, design decisions, and implementation details.

## Contents

- [Tech Stack](tech-stack.md)
- [Criteria Documentation](criteria/) - ADR for each evaluation criterion
- [Deployment](deployment.md)

## Solution Architecture

### High-Level Architecture

```mermaid
flowchart LR
  U["End user"] <--> BE
  A["Admin / Operator"] <--> COL

  subgraph CR["GCP Cloud Run"]
    BE["factcheck-backend<br>(Spring Boot + Thymeleaf)<br>/api/claims/verify<br>/api/claims/:id/followup<br>/api/claims/:id/bias"]
    COL["factcheck-news-collector<br>(Spring Boot)<br>/ingestion/run<br>/ingestion/task"]
    NLP["factcheck-nlp-service<br>(FastAPI)<br>/preprocess<br>/embed<br>/embed-sentences"]
  end

  subgraph SQL["Cloud SQL (PostgreSQL)"]
    DB_BE["backend DB<br>users, claims, followups"]
    DB_COL["collector DB<br>sources, articles, runs/logs, mbfc_sources"]
  end

  subgraph GCE["GCE VM (Docker)"]
    W["Weaviate Vector DB<br>Class: ArticleChunk<br>/v1/graphql<br>/v1/objects"]
  end

  subgraph EXT["External services / APIs"]
    VAI["Vertex AI<br>Embeddings: gemini-embedding-001<br>LLM: gemini-2.5-flash"]
    NEWS["NewsAPI / RSS"]
    MBFC["MBFC via RapidAPI"]
  end

  subgraph SCHED["Scheduling"]
    SCH["Cloud Scheduler (daily)"]
    TASKS["Cloud Tasks queue"]
  end

  BE -- "embed claim" --> NLP
  NLP -- "embeddings" --> BE
  BE -- "nearVector search" --> W
  W -- "evidence" --> BE
  BE -- "LLM request" --> VAI
  VAI -- "verdict + explanation" --> BE
  BE <--> DB_BE

  SCH --> COL
  COL -- "enqueue tasks" --> TASKS
  TASKS --> COL
  COL --> NEWS
  NEWS --> COL
  COL -- "sync bias metadata" --> MBFC
  MBFC --> COL
  COL -- "preprocess + embed" --> NLP
  NLP -- "sentence embeddings" --> COL
  COL -- "index chunks" --> W
  COL <--> DB_COL
```

### System Components

| Component | Description | Technology |
|-----------|-------------|------------|
| **Frontend** | Server-rendered UI for login and claims | Thymeleaf templates |
| **Backend** | Claim workflow, auth, persistence | Java 21, Spring Boot 3, JPA |
| **News Collector** | Ingestion, chunking, indexing | Java 21, Spring Boot 3 |
| **NLP Service** | Preprocess + embeddings | Python, FastAPI, Vertex AI |
| **Vector DB** | Evidence storage/search | Weaviate |
| **Databases** | Claims + catalog state | PostgreSQL (separate DBs) |

### Data Flow

`
Claim -> Backend /verify (JWT required)
      -> NLP embed -> Weaviate vector search
      -> Filter low-credibility sources (MBFC) -> Vertex Gemini prompt
      -> Stored with owner_username; correlation ID returned in responses/logs

Ingestion -> fetch sources -> preprocess/chunk
         -> embed -> index in Weaviate
Cloud Scheduler triggers /ingestion/run daily at 00:00; Cloud Tasks fan out /ingestion/task in production.
`

### Vector Store Schema (ArticleChunk)

```mermaid
flowchart LR
  A["Article<br>title, url, publishedDate"] --> C1["Chunk 1<br>text"]
  A --> C2["Chunk 2<br>text"]
  A --> C3["Chunk N<br>text"]

  subgraph W["Weaviate: ArticleChunk class"]
    V["ArticleChunk<br>text<br>articleTitle<br>sourceName<br>articleUrl<br>publishedDate<br>chunkIndex<br>mbfcBias<br>mbfcFactualReporting<br>mbfcCredibility<br>vector[3072]"]
  end

  C1 --> V
  C2 --> V
  C3 --> V
```

## Security Overview

| Aspect | Implementation |
|--------|----------------|
| **Authentication** | JWT login/register (UI cookie + API Bearer) |
| **Authorization** | USER for UI + `/api/claims/**`; ADMIN for other API/admin routes |
| **Input Validation** | Claim length limits and DTO checks |
| **Secrets Management** | Env vars for JWT, DB, and external services |
| **Service-to-service auth** | Optional Cloud Run IAM: backend/collector send ID tokens to NLP when enabled |
