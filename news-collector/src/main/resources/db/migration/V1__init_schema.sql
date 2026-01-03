-- Schema for news ingestion (content)

CREATE SCHEMA IF NOT EXISTS content;

-- Types

DO $$
    BEGIN
        IF NOT EXISTS (
            SELECT 1 FROM pg_type t
                              JOIN pg_namespace n ON n.oid = t.typnamespace
            WHERE t.typname = 'source_kind'
              AND n.nspname = 'content'
        ) THEN
            CREATE TYPE content.source_kind AS ENUM ('RSS', 'API');
        END IF;
    END$$;

-- Publishers (high-level news source)

CREATE TABLE IF NOT EXISTS content.publishers (
                                                  id                BIGSERIAL PRIMARY KEY,
                                                  name              VARCHAR(255) NOT NULL,         -- "BBC News", "Reuters"
                                                  country_code      CHAR(2),                       -- "GB", "US"
                                                  language_code     VARCHAR(10),                   -- "en", "en-GB"
                                                  bias_label        VARCHAR(100),                  -- from mediabiasfactcheck
                                                  reliability_score DOUBLE PRECISION NOT NULL DEFAULT 0.5,
                                                  website_url       TEXT,
                                                  mbfc_url          TEXT,                          -- link to mediabiasfactcheck page
                                                  created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                                  updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_publishers_name
    ON content.publishers (LOWER(name));

-- Source endpoints (RSS feed or API query)

CREATE TABLE IF NOT EXISTS content.source_endpoints (
                                                        id                     BIGSERIAL PRIMARY KEY,
                                                        publisher_id           BIGINT NOT NULL REFERENCES content.publishers(id),
                                                        kind                   content.source_kind NOT NULL,   -- 'RSS' or 'API'
                                                        display_name           VARCHAR(255) NOT NULL,          -- "BBC World RSS", "NewsAPI: BBC"
                                                        rss_url                TEXT,                           -- when kind = 'RSS'
                                                        api_provider           VARCHAR(100),                   -- "newsapi", "newsdata", etc.
                                                        api_query              TEXT,                           -- e.g. 'sources=bbc-news' or 'q=politics'
                                                        enabled                BOOLEAN NOT NULL DEFAULT TRUE,
                                                        fetch_interval_minutes INT NOT NULL DEFAULT 30,
                                                        last_fetched_at        TIMESTAMPTZ,
                                                        last_success_at        TIMESTAMPTZ,
                                                        failure_count          INT NOT NULL DEFAULT 0,
                                                        created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                                        updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
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

-- Articles (deduped per publisher + canonical URL)

CREATE TABLE IF NOT EXISTS content.articles (
                                                id                  BIGSERIAL PRIMARY KEY,
                                                publisher_id        BIGINT NOT NULL REFERENCES content.publishers(id),
                                                canonical_url       TEXT NOT NULL,              -- final URL considered “identity”
                                                canonical_url_hash  CHAR(40) NOT NULL,          -- e.g. SHA1 of canonical_url
                                                title               TEXT NOT NULL,
                                                description         TEXT,
                                                published_date      TIMESTAMPTZ,
                                                first_seen_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                                last_seen_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                                chunk_count         INT NOT NULL DEFAULT 0,
                                                status              VARCHAR(50) NOT NULL,       -- 'NEW','PROCESSED','ERROR'
                                                error_message       TEXT,
                                                weaviate_indexed    BOOLEAN NOT NULL DEFAULT FALSE,
                                                created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                                updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_articles_canonical_url_hash
    ON content.articles (publisher_id, canonical_url_hash);

CREATE INDEX IF NOT EXISTS idx_articles_published_date
    ON content.articles (published_date);

-- Article-source link (which endpoint produced which article)

CREATE TABLE IF NOT EXISTS content.article_sources (
                                                       id                  BIGSERIAL PRIMARY KEY,
                                                       article_id          BIGINT NOT NULL REFERENCES content.articles(id) ON DELETE CASCADE,
                                                       source_endpoint_id  BIGINT NOT NULL REFERENCES content.source_endpoints(id),
                                                       source_item_id      TEXT NOT NULL,             -- RSS guid, NewsAPI article url/id, etc.
                                                       fetched_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_article_sources_unique
    ON content.article_sources (source_endpoint_id, source_item_id);

CREATE INDEX IF NOT EXISTS idx_article_sources_article
    ON content.article_sources (article_id);

-- Ingestion logs

CREATE TABLE IF NOT EXISTS content.ingestion_logs (
                                                      id                  BIGSERIAL PRIMARY KEY,
                                                      source_endpoint_id  BIGINT NOT NULL REFERENCES content.source_endpoints(id),
                                                      started_at          TIMESTAMPTZ NOT NULL,
                                                      completed_at        TIMESTAMPTZ,
                                                      articles_fetched    INT NOT NULL DEFAULT 0,
                                                      articles_processed  INT NOT NULL DEFAULT 0,
                                                      articles_failed     INT NOT NULL DEFAULT 0,
                                                      status              VARCHAR(50),
                                                      error_details       TEXT,
                                                      correlation_id      VARCHAR(36) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ingestion_logs_source_endpoint
    ON content.ingestion_logs (source_endpoint_id);

CREATE INDEX IF NOT EXISTS idx_ingestion_logs_started_at
    ON content.ingestion_logs (started_at);