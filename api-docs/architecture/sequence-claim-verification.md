# Sequence: Claim Verification

1) Client sends `POST /api/claims/verify` with claim text and `Authorization: Bearer <token>` (optional `X-Correlation-Id`).
2) Backend validates length, saves `claim_log` row.
3) Backend calls NLP `/embed` with claim -> receives vector.
4) Backend queries Weaviate `ArticleChunk` via nearVector (limit = `app.search.top-k`, distance filter).
5) Backend builds prompt with claim + evidence, calls Vertex LLM (`generateContent`).
6) Backend parses verdict/explanation, updates `claim_log` with verdict/explanation/raw model answer.
7) Response returns verdict, explanation, evidence, claimId, correlationId.
8) Follow-up (`POST /api/claims/{id}/followup`) reuses stored verdict/explanation, re-queries evidence, calls Vertex for Q&A.
9) Bias analysis (`POST /api/claims/{id}/bias`) reuses verdict + evidence, stores bias text.

