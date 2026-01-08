BEGIN;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_type t
    JOIN pg_namespace n ON n.oid = t.typnamespace
    JOIN pg_enum e ON e.enumtypid = t.oid
    WHERE t.typname = 'ingestion_run_status'
      AND n.nspname = 'content'
      AND e.enumlabel = 'PARTIAL'
  ) THEN
    ALTER TYPE content.ingestion_run_status ADD VALUE 'PARTIAL';
  END IF;
END $$;

COMMIT;