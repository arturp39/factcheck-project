CREATE TABLE IF NOT EXISTS claim_log (
                                         id           BIGSERIAL PRIMARY KEY,
                                         claim_text   TEXT NOT NULL,
                                         created_at   TIMESTAMPTZ,
                                         model_answer TEXT
);