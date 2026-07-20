-- One active desktop browser slot and one active mobile browser/PWA slot.
ALTER TABLE user_sessions ADD COLUMN IF NOT EXISTS platform varchar(32);

UPDATE user_sessions
SET platform = CASE
    WHEN lower(coalesce(device_id, '') || ' ' || coalesce(device_name, '') || ' ' || coalesce(user_agent, ''))
            ~ '(mobile|android|iphone|ipad|tablet|pwa)'
        THEN 'WEB_MOBILE'
    ELSE 'WEB_DESKTOP'
END
WHERE platform IS NULL OR platform = '';

ALTER TABLE user_sessions ALTER COLUMN platform SET DEFAULT 'WEB_DESKTOP';

CREATE INDEX IF NOT EXISTS idx_user_sessions_platform_slot
    ON user_sessions (user_id, tenant_id, platform, revoked_at);
