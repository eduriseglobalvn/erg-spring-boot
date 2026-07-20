ALTER TABLE user_sessions
    ADD COLUMN IF NOT EXISTS previous_refresh_token_hash varchar(255),
    ADD COLUMN IF NOT EXISTS refresh_token_rotated_at timestamptz;

CREATE INDEX IF NOT EXISTS idx_user_sessions_previous_refresh_token_hash
    ON user_sessions(previous_refresh_token_hash)
    WHERE previous_refresh_token_hash IS NOT NULL;
