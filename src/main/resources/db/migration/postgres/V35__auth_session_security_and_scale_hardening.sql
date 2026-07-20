ALTER TABLE user_sessions
    ADD COLUMN IF NOT EXISTS device_fingerprint varchar(255);

CREATE INDEX IF NOT EXISTS idx_user_sessions_user_tenant_device_active
    ON user_sessions(user_id, tenant_id, device_fingerprint, device_id, revoked_at);

CREATE INDEX IF NOT EXISTS idx_user_sessions_active_expiry
    ON user_sessions(user_id, tenant_id, revoked_at, expires_at);

CREATE INDEX IF NOT EXISTS idx_auth_login_attempts_email_result_created
    ON auth_login_attempts(attempted_email, result, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_auth_login_attempts_ip_result_created
    ON auth_login_attempts(ip_address, result, created_at DESC);
