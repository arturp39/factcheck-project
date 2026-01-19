# Weaviate Schema (Vector Store)

Class: `ArticleChunk`
- `text` (string): chunk content.
- `articleTitle` (string): originating article title.
- `sourceName` (string): human-readable source.
- `mbfcBias` (string): MBFC bias label (if mapped).
- `mbfcFactualReporting` (string): MBFC factual reporting label (if mapped).
- `mbfcCredibility` (string): MBFC credibility label (if mapped).
- `articleId` (int): upstream DB article ID (collector).
- `articleUrl` (string): URL to original article.
- `publishedDate` (date): ISO timestamp of publication.
- `chunkIndex` (int): order of the chunk.
- Vector: provided externally (from NLP embeddings); no built-in vectorizer.

Queries
- Backend uses nearVector with distance filter (`weaviate.max-distance`, default 0.5) and limit `app.search.top-k` (default 5).
- Backend fields requested: `text`, `articleTitle`, `sourceName`, `articleId`, `articleUrl`, `publishedDate`, `mbfcBias`, `mbfcFactualReporting`, `mbfcCredibility`, `_additional { distance }`.
- Collector search uses nearVector with `limit`/`minScore`; fields requested: `text`, `articleId`, `articleUrl`, `articleTitle`, `sourceName`, `publishedDate`, `chunkIndex`, `_additional { distance }`.

Inserts
- Collector writes chunks via Weaviate HTTP `/v1/batch/objects` with provided vector.
- Backend can insert manual chunks via `/v1/objects` (used for admin/manual tooling).
- Default vectorizer set to `none`; backend sends `X-API-KEY` when configured (collector currently does not send it).

