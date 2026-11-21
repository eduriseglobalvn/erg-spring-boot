-- Retention purge of user_sessions (revoked_at < cutoff, all tenants); partial because most rows are never revoked.
-- Non-transactional by design: ONLY `CREATE INDEX CONCURRENTLY` (see V114).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_sessions_revoked_at
    ON user_sessions (revoked_at) WHERE revoked_at IS NOT NULL;
