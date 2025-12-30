CREATE TABLE IF NOT EXISTS claim_log (
    id BIGSERIAL PRIMARY KEY,
    claim_text TEXT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    model_answer TEXT,
    verdict VARCHAR(255),
    explanation TEXT,
    bias_analysis TEXT
);

CREATE INDEX IF NOT EXISTS idx_claim_log_created_at
    ON claim_log(created_at);