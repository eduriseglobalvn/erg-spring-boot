-- Online index candidate proven by the 180,000-recipient accepted fixture.
-- Must be executed by the deployment job with autocommit enabled, never by Flyway startup.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_assignment_recipients_tenant_user_assignment
    ON assignment_recipients(tenant_id, student_user_id, assignment_id);
