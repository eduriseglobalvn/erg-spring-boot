-- Online index candidate proven by the 1,000,000-row accepted fixture.
-- Must be executed by the deployment job with autocommit enabled, never by Flyway startup.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_activities_tenant_created_id
    ON user_activities(tenant_id, created_at DESC, id DESC);
