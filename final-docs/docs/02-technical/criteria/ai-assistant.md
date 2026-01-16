# Criterion: AI Assistant

## Architecture Decision Record

### Status
**Status:** Accepted (Partial)  
**Date:** 2026-01-16

### Context
We need a fact-checking assistant that generates verdicts, explanations, follow-ups, and bias analysis grounded in retrieved evidence while remaining auditable.

### Decision
- Use retrieval-augmented generation: embed claim via NLP service -> Weaviate search -> prompt Vertex Gemini (`gemini-2.5-flash`) with evidence + MBFC metadata.
- Version prompts in `backend/src/main/resources/prompts` for fact-check, follow-up, and bias flows.
- Parse `verdict:` from model output while storing the raw answer for traceability.
- Expose follow-up and bias endpoints separately to control cost.

### Alternatives Considered
| Alternative | Pros | Cons | Why Not Chosen |
|-------------|------|------|----------------|
| Pure LLM (no retrieval) | Simpler | Unverifiable/hallucinated answers | Evidence grounding is required |
| Heavy JSON-only prompts | Easier parsing | Fragile to model formatting changes | Kept natural prose with a clear verdict cue |
| Inlined embedding in backend | Fewer services | Harder to run fake mode; heavier JVM deps | Python NLP service better fit |

### Consequences
**Positive:** Evidence-grounded answers, prompt versioning, and audit trail (raw model answer saved).  
**Negative:** Parsing heuristic can misread unexpected outputs; latency depends on Vertex; no safety filters beyond prompt design; answers are limited to **recent news** indexed by the collector, so timeless facts may return "unclear."

## Implementation Details
### Project Structure
```
backend/service/ClaimWorkflowService.java   # Orchestrates RAG + storage
backend/service/VertexAiService.java        # Prompt construction + Vertex calls
backend/resources/prompts/*.txt             # Prompt templates
```

### Key Implementation Decisions
| Decision | Rationale |
|----------|-----------|
| Evidence top-k limit + chunked content | Control prompt size and cost |
| Store raw model answer | Audit + future re-parsing |
| Separate bias analysis call | Optional cost; reuse evidence and MBFC metadata |
| Verdict parsing heuristic with fallback | Avoid hard failures on format drift |

### Requirements Checklist
| # | Requirement | Status | Evidence |
|---|-------------|--------|----------|
| 1 | Evidence-grounded responses | Done | `ClaimService.searchEvidence` before Vertex call |
| 2 | Prompt versioning | Done | `prompts/*.txt` |
| 3 | Output parsing with audit | Partial | `parseAnswer` + stored raw text |
| 4 | Follow-up/bias support | Done | `followup` and `bias` endpoints |

## Known Limitations
| Limitation | Impact | Potential Solution |
|------------|--------|-------------------|
| No safety/policy filters | Risk of undesired content | Enable Vertex safety settings; add post-filters |
| Verdict parsing heuristic | Possible misclassification | Move to JSON schema prompts and validators |
| Latency tied to Vertex calls | Slower responses | Add caching or streaming UI |
| No automated eval harness | Hard to measure quality drift | Add gold set and periodic evaluation |

## References
- `backend/service/VertexAiService.java`
- `backend/service/ClaimService.java`
- `backend/resources/prompts/*`
