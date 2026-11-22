-- Retention purge of auth_login_attempts (created_at < cutoff, all tenants): the tenant-leading indexes cannot serve it.
-- Non-transactional by design: ONLY `CREATE INDEX CONCURRENTLY` (see V114).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_auth_login_attempts_created
    ON auth_login_attempts (created_at);
