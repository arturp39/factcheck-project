# Problem Statement & Goals

## Context

Online discussions spread claims faster than manual fact-checkers can respond. Newsrooms and analysts need an internal tool that can ingest thousands of articles, surface relevant evidence, and provide an auditable verdict so they can respond to misinformation with speed and confidence.

## Problem Statement

**Who:** Fact-check analysts, newsroom editors, and trust & safety teams.

**What:** Manually verifying a claim requires searching across many sources, copy-pasting snippets, and writing explanations from scratch.

**Why:** Slow, manual work delays responses to misinformation and makes it hard to prove why a verdict was reached.

### Pain Points

| # | Pain Point | Severity | Current Workaround |
|---|------------|----------|-------------------|
| 1 | Collecting evidence across outlets is time-consuming | High | Manual Google/News searches and spreadsheets |
| 2 | Verdicts are hard to reproduce and audit | High | Analysts paste links without context or provenance |
| 3 | Source bias is rarely tracked | Medium | Ad-hoc gut feeling about outlets |

## Business Goals

| Goal | Description | Success Indicator |
|------|-------------|-------------------|
| Faster verification | Provide a verdict in under a minute with auto-collected evidence | Avg. response time < 60s |
| Traceable outputs | Every verdict links to the exact chunks and model response | 100% claims stored with evidence and model answer |
| Bias-aware results | Flag source bias/factuality to improve trust | Bias metadata present on >80% indexed articles |

## Objectives & Metrics

| Objective | Metric | Current Value | Target Value | Timeline |
|-----------|--------|---------------|--------------|----------|
| Reduce analyst manual steps | # of manual search steps per claim | ~6 | <=2 | Before release |
| Keep corpus fresh | New articles ingested per day | ~0 (cold start) | 1k+ | Weekly after go-live |
| Improve answer quality | User-rated "useful" verdicts | Baseline TBD | >=80% positive | 1 month post-launch |

## Success Criteria

### Must Have

- [ ] Submit a claim and get a verdict with 3+ evidence snippets
- [ ] Persist conversation history (verdict, evidence, follow-ups); return correlation IDs for tracing
- [ ] Return bias analysis when MBFC mapping exists

### Nice to Have

- [x] Scheduled ingestion via Cloud Scheduler
- [ ] Simple admin UI to inspect ingestion runs

## Non-Goals

What this project explicitly does NOT aim to achieve:

- Multimedia (image/video) fact-checking
- Multilingual claims (English-only pipeline)
- Full public-facing account system; only internal operators are assumed
