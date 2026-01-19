# Sequence: Ingestion & Indexing

1) Admin/external scheduler triggers `POST /ingestion/run` on collector (optional UUID `correlationId`).
2) Collector creates `content.ingestion_runs` and per-endpoint `content.ingestion_logs`.
3) Collector enqueues tasks for eligible `content.source_endpoints`.
4) Task handler claims the log row (lease) and selects a `SourceFetcher` (RSS/API).
5) Discovery upserts `content.articles` + `content.article_sources` for each raw item.
6) Enrichment fetches/extracts content (or uses provided text) and writes `content.article_content`.
7) Indexing calls NLP `/preprocess`, optionally `/embed-sentences` for semantic chunking, then `/embed`, and writes chunks to Weaviate `ArticleChunk`.
8) Collector updates article status/chunk counts and finalizes ingestion logs/runs.

