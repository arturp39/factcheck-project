BEGIN;

CREATE SCHEMA IF NOT EXISTS content;

-- Types
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_type t
    JOIN pg_namespace n ON n.oid = t.typnamespace
    WHERE t.typname = 'source_kind' AND n.nspname = 'content'
  ) THEN
    CREATE TYPE content.source_kind AS ENUM ('RSS', 'API');
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_type t
    JOIN pg_namespace n ON n.oid = t.typnamespace
    WHERE t.typname = 'article_status' AND n.nspname = 'content'
  ) THEN
    CREATE TYPE content.article_status AS ENUM ('DISCOVERED', 'FETCHED', 'EXTRACTED', 'INDEXED', 'ERROR');
  END IF;
END $$;

-- updated_at trigger helper
CREATE OR REPLACE FUNCTION content.set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Publishers
CREATE TABLE IF NOT EXISTS content.publishers (
  id                BIGSERIAL PRIMARY KEY,
  name              VARCHAR(255) NOT NULL,
  country_code      CHAR(2),
  language_code     VARCHAR(10),
  bias_label        VARCHAR(100),
  reliability_score DOUBLE PRECISION NOT NULL DEFAULT 0.5,
  website_url       TEXT,
  mbfc_url          TEXT,
  mbfc_source_id    BIGINT,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_publishers_name
  ON content.publishers (LOWER(name));

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_publishers_updated_at') THEN
    CREATE TRIGGER trg_publishers_updated_at
    BEFORE UPDATE ON content.publishers
    FOR EACH ROW
    EXECUTE FUNCTION content.set_updated_at();
  END IF;
END $$;

-- Source endpoints
CREATE TABLE IF NOT EXISTS content.source_endpoints (
  id                     BIGSERIAL PRIMARY KEY,
  publisher_id           BIGINT NOT NULL REFERENCES content.publishers(id),
  kind                   content.source_kind NOT NULL,
  display_name           VARCHAR(255) NOT NULL,
  rss_url                TEXT,
  api_provider           VARCHAR(100),
  api_query              TEXT,
  enabled                BOOLEAN NOT NULL DEFAULT TRUE,
  fetch_interval_minutes INT NOT NULL DEFAULT 1440,
  last_fetched_at        TIMESTAMPTZ,
  last_success_at        TIMESTAMPTZ,
  failure_count          INT NOT NULL DEFAULT 0,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CONSTRAINT chk_source_endpoints_kind_fields
    CHECK (
      (kind = 'RSS' AND rss_url IS NOT NULL AND api_provider IS NULL AND api_query IS NULL)
   OR (kind = 'API' AND rss_url IS NULL AND api_provider IS NOT NULL AND api_query IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_source_endpoints_publisher
  ON content.source_endpoints (publisher_id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_source_endpoints_unique
  ON content.source_endpoints (
    publisher_id,
    kind,
    COALESCE(rss_url, ''),
    COALESCE(api_provider, ''),
    COALESCE(api_query, '')
  );

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_source_endpoints_updated_at') THEN
    CREATE TRIGGER trg_source_endpoints_updated_at
    BEFORE UPDATE ON content.source_endpoints
    FOR EACH ROW
    EXECUTE FUNCTION content.set_updated_at();
  END IF;
END $$;

-- Articles
-- canonical_url_hash: store as SHA-256 hex (64 chars) computed in the app for portability.
CREATE TABLE IF NOT EXISTS content.articles (
  id                  BIGSERIAL PRIMARY KEY,

  publisher_id        BIGINT NOT NULL REFERENCES content.publishers(id),

  -- URL identity
  original_url        TEXT,
  canonical_url       TEXT NOT NULL,
  canonical_url_hash  CHAR(64) NOT NULL,

  -- Discovery metadata
  title               TEXT NOT NULL,
  description         TEXT,
  published_date      TIMESTAMPTZ,
  first_seen_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_seen_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  -- Fetch metadata (HTML download)
  content_fetched_at  TIMESTAMPTZ,
  http_status         INT,
  http_etag           TEXT,
  http_last_modified  TEXT,

  -- Extraction metadata
  content_hash        CHAR(64),
  chunk_count         INT NOT NULL DEFAULT 0,

  -- Processing / indexing
  status              content.article_status NOT NULL DEFAULT 'DISCOVERED',
  fetch_error         TEXT,
  extraction_error    TEXT,
  weaviate_indexed    BOOLEAN NOT NULL DEFAULT FALSE,

  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CONSTRAINT chk_articles_canonical_url_nonempty CHECK (btrim(canonical_url) <> ''),
  CONSTRAINT chk_articles_canonical_url_hash_nonempty CHECK (btrim(canonical_url_hash) <> '')
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_articles_canonical_url_hash
  ON content.articles (publisher_id, canonical_url_hash);

CREATE INDEX IF NOT EXISTS idx_articles_published_date
  ON content.articles (published_date);

CREATE INDEX IF NOT EXISTS idx_articles_status
  ON content.articles (status);

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_articles_updated_at') THEN
    CREATE TRIGGER trg_articles_updated_at
    BEFORE UPDATE ON content.articles
    FOR EACH ROW
    EXECUTE FUNCTION content.set_updated_at();
  END IF;
END $$;

-- Extracted content (what Jsoup extractor returns)
CREATE TABLE IF NOT EXISTS content.article_content (
  article_id     BIGINT PRIMARY KEY REFERENCES content.articles(id) ON DELETE CASCADE,
  extracted_text TEXT NOT NULL,
  extracted_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Article-source link (which endpoint produced which article)
CREATE TABLE IF NOT EXISTS content.article_sources (
  id                  BIGSERIAL PRIMARY KEY,
  article_id          BIGINT NOT NULL REFERENCES content.articles(id) ON DELETE CASCADE,
  source_endpoint_id  BIGINT NOT NULL REFERENCES content.source_endpoints(id),
  source_item_id      TEXT NOT NULL,
  fetched_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_article_sources_unique
  ON content.article_sources (source_endpoint_id, source_item_id);

CREATE INDEX IF NOT EXISTS idx_article_sources_article
  ON content.article_sources (article_id);

-- Ingestion run (one daily job execution; useful even if schedule is in code)
CREATE TABLE IF NOT EXISTS content.ingestion_runs (
  id             BIGSERIAL PRIMARY KEY,
  started_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  completed_at   TIMESTAMPTZ,
  status         VARCHAR(50) NOT NULL,
  correlation_id UUID NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ingestion_runs_started_at
  ON content.ingestion_runs (started_at);

-- Per-endpoint ingestion logs (attempts)
CREATE TABLE IF NOT EXISTS content.ingestion_logs (
  id                  BIGSERIAL PRIMARY KEY,
  run_id              BIGINT REFERENCES content.ingestion_runs(id) ON DELETE SET NULL,
  source_endpoint_id  BIGINT NOT NULL REFERENCES content.source_endpoints(id),
  started_at          TIMESTAMPTZ NOT NULL,
  completed_at        TIMESTAMPTZ,
  articles_fetched    INT NOT NULL DEFAULT 0,
  articles_processed  INT NOT NULL DEFAULT 0,
  articles_failed     INT NOT NULL DEFAULT 0,
  status              VARCHAR(50),
  error_details       TEXT,
  correlation_id      UUID NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ingestion_logs_run
  ON content.ingestion_logs (run_id);

CREATE INDEX IF NOT EXISTS idx_ingestion_logs_source_endpoint
  ON content.ingestion_logs (source_endpoint_id);

CREATE INDEX IF NOT EXISTS idx_ingestion_logs_started_at
  ON content.ingestion_logs (started_at);

-- MBFC dataset (monthly sync) + FK
CREATE TABLE IF NOT EXISTS content.mbfc_sources (
  mbfc_source_id      BIGINT PRIMARY KEY,
  source_name         TEXT NOT NULL,
  mbfc_url            TEXT NOT NULL,
  bias                TEXT,
  country             TEXT,
  factual_reporting   TEXT,
  media_type          TEXT,
  source_url          TEXT,
  source_url_domain   TEXT,
  credibility         TEXT,
  synced_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_mbfc_sources_mbfc_url
  ON content.mbfc_sources (mbfc_url);

CREATE INDEX IF NOT EXISTS idx_mbfc_sources_domain
  ON content.mbfc_sources (source_url_domain);

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'fk_publishers_mbfc_source'
      AND conrelid = 'content.publishers'::regclass
  ) THEN
    ALTER TABLE content.publishers
      ADD CONSTRAINT fk_publishers_mbfc_source
      FOREIGN KEY (mbfc_source_id) REFERENCES content.mbfc_sources(mbfc_source_id);
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_publishers_mbfc_source_id
  ON content.publishers (mbfc_source_id);

COMMIT;