-- Add owner tracking for claims

ALTER TABLE public.claim_log
    ADD COLUMN IF NOT EXISTS owner_username VARCHAR(120);

UPDATE public.claim_log
SET owner_username = 'legacy'
WHERE owner_username IS NULL;

ALTER TABLE public.claim_log
    ALTER COLUMN owner_username SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_claim_log_owner_username_created_at
    ON public.claim_log (owner_username, created_at);
