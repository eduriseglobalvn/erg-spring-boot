-- AuthSessionRepository.findSecurityFeedByTenant orders by coalesce(revoked_at, created_at) DESC, id DESC.
-- Non-transactional by design: ONLY `CREATE INDEX CONCURRENTLY` (see V114).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_sessions_tenant_feed
    ON user_sessions (tenant_id, (coalesce(revoked_at, created_at)) DESC, id DESC);
