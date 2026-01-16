# Stakeholders & Users

## Target Audience

| Persona | Description | Key Needs |
|---------|-------------|-----------|
| Fact-check analyst | Works through a queue of viral claims | Fast evidence surfacing, auditable verdicts |
| Newsroom editor | Decides whether to publish corrections | Confidence in sources, bias signals, history |
| Automation operator | Runs ingestion/ops | Stable jobs, clear logs, restart instructions |

## User Personas

### Persona 1: Ana the Analyst

| Attribute | Details |
|-----------|---------|
| **Role** | Fact-check analyst |
| **Age** | 25-40 |
| **Tech Savviness** | Medium |
| **Goals** | Verify a claim quickly and share a defendable verdict |
| **Frustrations** | Tab-juggling across sources, no clear audit trail |
| **Scenario** | Pastes a viral claim, reads the verdict + evidence, exports/share results |

### Persona 2: Eli the Editor

| Attribute | Details |
|-----------|---------|
| **Role** | Newsroom editor |
| **Age** | 30-50 |
| **Tech Savviness** | Medium |
| **Goals** | Decide whether to post a correction or follow-up story |
| **Frustrations** | Unclear provenance, lack of bias signals |
| **Scenario** | Checks recent claims, scans evidence snippets and bias analysis before approving messaging |

## Stakeholder Map

### High Influence / High Interest

- Product sponsor: Wants timely, defensible fact-checks to protect brand trust.

### High Influence / Low Interest

- Infrastructure/IT: Provides DB/Vertex/Weaviate access; cares about cost and security.

### Low Influence / High Interest

- Analysts and editors: Daily users; interested in speed and clarity.

### Low Influence / Low Interest

- General readers: Indirect beneficiaries of better corrections; no direct access planned.
