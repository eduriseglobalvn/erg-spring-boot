-- Retention purge of user_sessions (expires_at < cutoff, all tenants): V35 leads with user_id and cannot serve it.
-- Non-transactional by design: ONLY `CREATE INDEX CONCURRENTLY` (see V114).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_sessions_expires_at
    ON user_sessions (expires_at);
