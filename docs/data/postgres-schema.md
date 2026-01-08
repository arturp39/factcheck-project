# Postgres Schema (Operational)

Database: `factcheck`

Schemas
- `content` (news collector)

Enums (content schema)
- `content.source_kind`: `RSS|API`
- `content.article_status`: `DISCOVERED|FETCHED|EXTRACTED|INDEXED|ERROR`
- `content.ingestion_run_status`: `RUNNING|COMPLETED|PARTIAL|FAILED`
- `content.ingestion_status`: `STARTED|PROCESSING|SUCCESS|PARTIAL|FAILED|SKIPPED`

Tables
- `claim_log`
  - `id` BIGSERIAL PK
  - `claim_text` TEXT NOT NULL
  - `created_at` TIMESTAMPTZ DEFAULT now()
  - `model_answer` TEXT (raw LLM output)
  - `verdict` VARCHAR(255)
  - `explanation` TEXT
  - `bias_analysis` TEXT
  - Index: `idx_claim_log_created_at`

- `content.publishers`
  - `id` BIGSERIAL PK
  - `name` VARCHAR(255) NOT NULL (unique by lowercased name)
  - `country_code` CHAR(2)
  - `website_url` TEXT
  - `mbfc_source_id` FK -> `content.mbfc_sources(mbfc_source_id)`
  - `created_at`, `updated_at` TIMESTAMPTZ DEFAULT now()

- `content.source_endpoints`
  - `id` BIGSERIAL PK
  - `publisher_id` FK -> `content.publishers(id)`
  - `kind` content.source_kind (`RSS|API`)
  - `display_name` VARCHAR(255) NOT NULL
  - `rss_url` TEXT
  - `api_provider` VARCHAR(100)
  - `api_query` TEXT
  - `enabled` BOOLEAN NOT NULL DEFAULT TRUE
  - `fetch_interval_minutes` INT NOT NULL DEFAULT 1440
  - `last_fetched_at`, `last_success_at`, `last_attempted_at` TIMESTAMPTZ
  - `failure_count` INT NOT NULL DEFAULT 0
  - `robots_disallowed` BOOLEAN NOT NULL DEFAULT FALSE
  - `blocked_until` TIMESTAMPTZ
  - `block_reason` TEXT
  - `block_count` INT NOT NULL DEFAULT 0
  - `created_at`, `updated_at` TIMESTAMPTZ DEFAULT now()
  - Constraint: RSS requires `rss_url`, API requires `api_provider` + `api_query`

- `content.articles`
  - `id` BIGSERIAL PK
  - `publisher_id` FK -> `content.publishers(id)`
  - `original_url` TEXT
  - `canonical_url` TEXT NOT NULL
  - `canonical_url_hash` CHAR(64) NOT NULL (sha256 hex)
  - `title` TEXT NOT NULL
  - `description` TEXT
  - `published_date` TIMESTAMPTZ
  - `first_seen_at`, `last_seen_at` TIMESTAMPTZ DEFAULT now()
  - `content_fetched_at` TIMESTAMPTZ
  - `http_status` INT
  - `http_etag` TEXT
  - `http_last_modified` TEXT
  - `content_hash` CHAR(64)
  - `chunk_count` INT NOT NULL DEFAULT 0
  - `status` content.article_status NOT NULL DEFAULT `DISCOVERED`
  - `fetch_error` TEXT
  - `extraction_error` TEXT
  - `weaviate_indexed` BOOLEAN NOT NULL DEFAULT FALSE
  - `created_at`, `updated_at` TIMESTAMPTZ DEFAULT now()
  - Unique: `(publisher_id, canonical_url_hash)`

- `content.article_content`
  - `article_id` PK/FK -> `content.articles(id)` (ON DELETE CASCADE)
  - `extracted_text` TEXT NOT NULL
  - `extracted_at` TIMESTAMPTZ DEFAULT now()

- `content.article_sources`
  - `id` BIGSERIAL PK
  - `article_id` FK -> `content.articles(id)` (ON DELETE CASCADE)
  - `source_endpoint_id` FK -> `content.source_endpoints(id)`
  - `source_item_id` TEXT NOT NULL
  - `fetched_at` TIMESTAMPTZ DEFAULT now()
  - Unique: `(source_endpoint_id, source_item_id)`

- `content.ingestion_runs`
  - `id` BIGSERIAL PK
  - `version` BIGINT NOT NULL DEFAULT 0
  - `started_at` TIMESTAMPTZ DEFAULT now()
  - `completed_at` TIMESTAMPTZ
  - `status` content.ingestion_run_status NOT NULL
  - `correlation_id` UUID NOT NULL
  - Unique: single RUNNING row (partial unique index)

- `content.ingestion_logs`
  - `id` BIGSERIAL PK
  - `version` BIGINT NOT NULL DEFAULT 0
  - `run_id` FK nullable -> `content.ingestion_runs(id)` (ON DELETE SET NULL)
  - `source_endpoint_id` FK -> `content.source_endpoints(id)`
  - `started_at` TIMESTAMPTZ NOT NULL
  - `completed_at` TIMESTAMPTZ
  - `articles_fetched` INT NOT NULL DEFAULT 0
  - `articles_processed` INT NOT NULL DEFAULT 0
  - `articles_failed` INT NOT NULL DEFAULT 0
  - `status` content.ingestion_status (`STARTED|PROCESSING|SUCCESS|PARTIAL|FAILED|SKIPPED`)
  - `error_details` TEXT
  - `correlation_id` UUID NOT NULL
  - Unique: `(run_id, source_endpoint_id)` when `run_id` is not null

- `content.mbfc_sources`
  - `mbfc_source_id` BIGINT PK
  - `source_name` TEXT NOT NULL
  - `mbfc_url` TEXT NOT NULL
  - `bias` TEXT
  - `country` TEXT
  - `factual_reporting` TEXT
  - `media_type` TEXT
  - `source_url` TEXT
  - `source_url_domain` TEXT
  - `credibility` TEXT
  - `synced_at` TIMESTAMPTZ DEFAULT now()
