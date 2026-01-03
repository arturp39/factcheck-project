-- Claim log for fact-checking UI

CREATE SCHEMA IF NOT EXISTS public;

CREATE TABLE IF NOT EXISTS public.claim_log (
                                                id              BIGSERIAL PRIMARY KEY,
                                                claim_text      TEXT NOT NULL,
                                                created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                                model_answer    TEXT,
                                                verdict         VARCHAR(255),
                                                explanation     TEXT,
                                                bias_analysis   TEXT
);

CREATE INDEX IF NOT EXISTS idx_claim_log_created_at
    ON public.claim_log (created_at);