-- Security activity feed: findByTenantIdOrderByCreatedAtDescIdDesc.
-- Non-transactional by design: ONLY `CREATE INDEX CONCURRENTLY` (see V114).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_auth_login_attempts_tenant_created
    ON auth_login_attempts (tenant_id, created_at DESC, id DESC);
