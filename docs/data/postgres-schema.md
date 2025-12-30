# Postgres Schema (Operational)

Database: `factcheck`

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

- `content.sources`
  - `id` BIGSERIAL PK
  - `name` VARCHAR(255) NOT NULL
  - `type` VARCHAR(50) NOT NULL (`RSS|API|HTML`)
  - `url` TEXT NOT NULL UNIQUE
  - `category` VARCHAR(100) NOT NULL DEFAULT 'general'
  - `enabled` BOOLEAN DEFAULT TRUE
  - `reliability_score` DOUBLE PRECISION DEFAULT 0.5
  - `last_fetched_at`, `last_success_at` TIMESTAMPTZ
  - `failure_count` INT DEFAULT 0
  - `created_at`, `updated_at` TIMESTAMPTZ DEFAULT now()

- `content.articles`
  - `id` BIGSERIAL PK
  - `source_id` FK -> `content.sources(id)`
  - `external_url` TEXT NOT NULL UNIQUE
  - `title` TEXT NOT NULL
  - `description` TEXT
  - `published_date` TIMESTAMPTZ
  - `fetched_at` TIMESTAMPTZ DEFAULT now()
  - `chunk_count` INT DEFAULT 0
  - `status` VARCHAR(50) NOT NULL (`PENDING|PROCESSING|PROCESSED|FAILED`)
  - `error_message` TEXT
  - `weaviate_indexed` BOOLEAN DEFAULT FALSE
  - `created_at`, `updated_at` TIMESTAMPTZ DEFAULT now()

- `content.ingestion_logs`
  - `id` BIGSERIAL PK
  - `source_id` FK nullable -> `content.sources(id)`
  - `started_at` TIMESTAMPTZ NOT NULL
  - `completed_at` TIMESTAMPTZ
  - `articles_fetched` INT DEFAULT 0
  - `articles_processed` INT DEFAULT 0
  - `articles_failed` INT DEFAULT 0
  - `status` VARCHAR(50) (`RUNNING|SUCCESS|PARTIAL|FAILED`)
  - `error_details` TEXT
  - `correlation_id` VARCHAR(36) NOT NULL