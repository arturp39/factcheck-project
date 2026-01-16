-- Auth users for backend

CREATE TABLE IF NOT EXISTS public.app_user (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(120) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_app_user_username
    ON public.app_user (username);
