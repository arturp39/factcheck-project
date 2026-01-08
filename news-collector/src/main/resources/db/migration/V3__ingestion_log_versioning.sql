BEGIN;

ALTER TABLE content.ingestion_runs
  ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE content.ingestion_logs
  ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_type t
    JOIN pg_namespace n ON n.oid = t.typnamespace
    WHERE t.typname = 'ingestion_status' AND n.nspname = 'content'
  ) THEN
    CREATE TYPE content.ingestion_status AS ENUM (
      'STARTED',
      'PROCESSING',
      'SUCCESS',
      'PARTIAL',
      'FAILED',
      'SKIPPED'
    );
  END IF;
END $$;

ALTER TABLE content.ingestion_logs
  ALTER COLUMN status TYPE content.ingestion_status
  USING CASE
    WHEN status IS NULL THEN NULL
    ELSE status::content.ingestion_status
  END;

CREATE UNIQUE INDEX IF NOT EXISTS ux_ingestion_logs_run_source_endpoint
  ON content.ingestion_logs (run_id, source_endpoint_id)
  WHERE run_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ingestion_logs_run_pending
  ON content.ingestion_logs (run_id)
  WHERE completed_at IS NULL;

DROP INDEX IF EXISTS content.ux_mbfc_sources_mbfc_url;
DROP INDEX IF EXISTS ux_mbfc_sources_mbfc_url;

ALTER TABLE content.publishers
  DROP COLUMN IF EXISTS language_code;

COMMIT;