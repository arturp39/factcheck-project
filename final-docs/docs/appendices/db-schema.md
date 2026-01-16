# Database Schema

## Overview

| Attribute | Value |
|-----------|-------|
| **Database** | PostgreSQL (two instances: backend, collector) |
| **Version** | 15 (docker-compose) |
| **ORM** | Spring Data JPA / Hibernate |

## Entity Relationship Diagram (text)

- **backend**: app_user (1) -> claim_log (N) -> claim_followup (N)
- **collector**: publishers (1) -> source_endpoints (N) -> ingestion_logs (N); ingestion_runs (1) -> ingestion_logs (N);
  articles (1) -> article_content (1); articles (1) -> article_sources (N); mbfc_sources optional FK on publishers.

## Backend Tables

### app_user

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGSERIAL | PK | User id |
| username | VARCHAR(120) | UNIQUE, NOT NULL | Login name |
| password_hash | VARCHAR(255) | NOT NULL | BCrypt hash |
| role | VARCHAR(20) | NOT NULL | USER or ADMIN |
| created_at | TIMESTAMPTZ | DEFAULT now | Creation time |

Index: idx_app_user_username on username.

### claim_log

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGSERIAL | PK | Claim identifier |
| claim_text | TEXT | NOT NULL | User-provided claim |
| owner_username | VARCHAR(120) | NOT NULL | Claim owner (username) |
| created_at | TIMESTAMPTZ | DEFAULT now | When claim was created |
| model_answer | TEXT | NULL | Raw LLM response |
| verdict | VARCHAR | NULL | Parsed verdict (true/false/mixed/unclear) |
| explanation | TEXT | NULL | Parsed explanation |
| bias_analysis | TEXT | NULL | Bias commentary |

Indexes:
- idx_claim_log_created_at on created_at
- idx_claim_log_owner_username_created_at on (owner_username, created_at)

### claim_followup

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGSERIAL | PK | Follow-up identifier |
| claim_id | BIGINT | FK -> claim_log(id) ON DELETE CASCADE | Parent claim |
| question | TEXT | NOT NULL | Asked question |
| answer | TEXT | NOT NULL | Model answer |
| created_at | TIMESTAMPTZ | DEFAULT now | Timestamp |

Index: (claim_id, created_at).

## Collector Tables (content schema)

### publishers

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGSERIAL | PK | Publisher id |
| name | VARCHAR(255) | UNIQUE | Publisher name |
| country_code | CHAR(2) | NULL | Country |
| bias_label | VARCHAR(100) | NULL | Optional bias label |
| reliability_score | DOUBLE PRECISION | DEFAULT 0.5 | Reliability score |
| website_url | TEXT | NULL | Site URL |
| mbfc_url | TEXT | NULL | MBFC page URL |
| mbfc_source_id | BIGINT | FK -> mbfc_sources | Optional bias mapping |
| created_at/updated_at | TIMESTAMPTZ | DEFAULT now | Timestamps |

### source_endpoints

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGSERIAL | PK | Endpoint id |
| publisher_id | BIGINT | FK -> publishers | Owner |
| kind | ENUM SourceKind | NOT NULL | RSS or API |
| display_name | VARCHAR(255) | NOT NULL | Human label |
| rss_url | TEXT | NULL | RSS feed |
| api_provider | TEXT | NULL | Provider name (e.g., newsapi) |
| api_query | TEXT | NULL | Provider-specific query/id |
| enabled | BOOLEAN | DEFAULT true | Active flag |
| fetch_interval_minutes | INT | DEFAULT 1440 | Suggested cadence (API defaults to 30 when omitted) |
| last_fetched_at/last_attempted_at/last_success_at | TIMESTAMP | NULL | Timing fields |
| failure_count | INT | DEFAULT 0 | Consecutive failures |
| robots_disallowed | BOOLEAN | DEFAULT false | Robots flag |
| blocked_until | TIMESTAMP | NULL | Cooldown |
| block_reason | TEXT | NULL | Last block reason |
| block_count | INT | DEFAULT 0 | Times blocked |
| created_at/updated_at | TIMESTAMPTZ | DEFAULT now | Timestamps |

### articles

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGSERIAL | PK | Article id |
| publisher_id | BIGINT | FK -> publishers | Source publisher |
| canonical_url | TEXT | NOT NULL | Normalized URL |
| canonical_url_hash | CHAR(64) | NOT NULL | SHA-256 for dedup |
| original_url | TEXT | NULL | Fetched URL |
| title | TEXT | NOT NULL | Article title |
| description | TEXT | NULL | Short description |
| published_date | TIMESTAMPTZ | NULL | Publication time |
| first_seen_at/last_seen_at | TIMESTAMPTZ | DEFAULT now | Discovery timestamps |
| content_fetched_at | TIMESTAMPTZ | NULL | When content fetched |
| http_status | INT | NULL | HTTP status |
| http_etag | TEXT | NULL | HTTP ETag |
| http_last_modified | TEXT | NULL | HTTP Last-Modified |
| content_hash | CHAR(64) | NULL | Hash of extracted text |
| chunk_count | INT | DEFAULT 0 | Number of chunks indexed |
| status | ENUM content.article_status | DEFAULT DISCOVERED | DISCOVERED/FETCHED/EXTRACTED/INDEXED/ERROR |
| fetch_error/extraction_error | TEXT | NULL | Error messages |
| weaviate_indexed | BOOLEAN | DEFAULT false | Indexed flag |
| created_at/updated_at | TIMESTAMPTZ | DEFAULT now | Timestamps |

### article_content

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| article_id | BIGINT | PK/FK -> articles | Article id |
| extracted_text | TEXT | NOT NULL | Cleaned full text |
| extracted_at | TIMESTAMPTZ | DEFAULT now | Extraction time |

### article_sources

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGSERIAL | PK | Row id |
| article_id | BIGINT | FK -> articles | Article |
| source_endpoint_id | BIGINT | FK -> source_endpoints | Which endpoint fetched it |
| source_item_id | TEXT | NOT NULL | Provider item id |
| fetched_at | TIMESTAMPTZ | DEFAULT now | Time fetched |

### ingestion_runs

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGSERIAL | PK | Run id |
| started_at/completed_at | TIMESTAMPTZ | | Timing |
| status | ENUM content.ingestion_run_status | NOT NULL | RUNNING/COMPLETED/PARTIAL/FAILED |
| correlation_id | UUID | NOT NULL | Trace id |
| version | BIGINT | DEFAULT 0 | Optimistic locking/version |

### ingestion_logs

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGSERIAL | PK | Log id |
| source_endpoint_id | BIGINT | FK -> source_endpoints | Endpoint processed |
| run_id | BIGINT | FK -> ingestion_runs | Parent run |
| started_at/completed_at | TIMESTAMPTZ | | Timing |
| articles_fetched/processed/failed | INT | DEFAULT 0 | Counters |
| status | ENUM content.ingestion_status | | STARTED/PROCESSING/SUCCESS/PARTIAL/FAILED/SKIPPED |
| error_details | TEXT | NULL | Error text |
| correlation_id | UUID | NOT NULL | Trace id |
| version | BIGINT | DEFAULT 0 | Optimistic locking/version |

### mbfc_sources

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| mbfc_source_id | BIGINT | PK | External id |
| source_name | TEXT | NOT NULL | Outlet |
| mbfc_url | TEXT | NOT NULL | MBFC page |
| bias | TEXT | NULL | Political bias |
| country | TEXT | NULL | Country |
| factual_reporting | TEXT | NULL | MBFC factual rating |
| media_type | TEXT | NULL | Type |
| source_url | TEXT | NULL | Outlet URL |
| source_url_domain | TEXT | NULL | Domain |
| credibility | TEXT | NULL | Credibility label |
| synced_at | TIMESTAMPTZ | DEFAULT now | Sync time |

## Migrations

| Version | Description | Date |
|---------|-------------|------|
| V1__init_claim_log | Backend claim_log table | 2026-01-03 |
| V2__claim_followup | Backend follow-up table | 2026-01-08 |
| V3__claim_log_owner | Add claim ownership | 2026-01-16 |
| V4__app_user | Backend user table | 2026-01-16 |
| Collector schema migrations (V1/V2/V3/V5) | Content schema + seeds + enum updates | 2026-01-08+ |

## Seeding

No seed scripts are included. Populate by running ingestion (/ingestion/run) and by submitting claims through the UI/API.

