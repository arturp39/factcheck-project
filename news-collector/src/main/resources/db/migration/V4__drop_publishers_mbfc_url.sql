BEGIN;

ALTER TABLE content.publishers
  DROP COLUMN IF EXISTS mbfc_url,
  DROP COLUMN IF EXISTS bias_label,
  DROP COLUMN IF EXISTS reliability_score;

COMMIT;