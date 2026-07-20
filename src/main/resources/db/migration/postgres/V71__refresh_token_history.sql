CREATE TABLE IF NOT EXISTS refresh_token_history (
    id varchar(36) PRIMARY KEY,
    token_hash varchar(255) NOT NULL UNIQUE,
    session_id varchar(36) NOT NULL,
    user_id varchar(36) NOT NULL,
    tenant_id varchar(36) NOT NULL,
    consumed_at timestamptz NOT NULL,
    expires_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_refresh_token_history_hash
    ON refresh_token_history(token_hash);
CREATE INDEX IF NOT EXISTS idx_refresh_token_history_session
    ON refresh_token_history(session_id);
CREATE INDEX IF NOT EXISTS idx_refresh_token_history_retention
    ON refresh_token_history(consumed_at);
