# Sequence: Ingestion & Indexing

1) Admin triggers `POST /admin/ingestion/run` (or scheduler) on collector.
2) Collector fetches enabled sources from Postgres.
3) For each source, fetch articles (RSS/API/HTML) and normalize content.
4) Split into chunks, call NLP `/embed` for embeddings.
5) Store article metadata/chunk counts in Postgres `content.articles`.
6) Index chunks into Weaviate `ArticleChunk` with supplied vectors and metadata.
7) Record run stats in `content.ingestion_logs` (fetched/processed/failed, status, correlationId).
8) Backend evidence search later queries these Weaviate vectors.