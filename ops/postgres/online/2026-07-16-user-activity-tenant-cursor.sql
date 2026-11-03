-- Online index candidate proven by the 1,000,000-row accepted fixture.
-- Shipped by Flyway V114 (CONCURRENTLY, non-transactional). Run this script ahead of a deploy only to
-- pre-build the index on a large table; V114 then skips it (IF NOT EXISTS).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_activities_tenant_created_id
    ON user_activities(tenant_id, created_at DESC, id DESC);
