-- Follow-up Q&A history per claim

CREATE TABLE IF NOT EXISTS public.claim_followup (
    id           BIGSERIAL PRIMARY KEY,
    claim_id     BIGINT NOT NULL REFERENCES public.claim_log(id) ON DELETE CASCADE,
    question     TEXT NOT NULL,
    answer       TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_claim_followup_claim_id_created_at
    ON public.claim_followup (claim_id, created_at);
