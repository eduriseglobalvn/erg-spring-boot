-- Online index candidate proven by the 180,000-recipient accepted fixture.
-- Shipped by Flyway V114 (CONCURRENTLY, non-transactional). Run this script ahead of a deploy only to
-- pre-build the index on a large table; V114 then skips it (IF NOT EXISTS).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_assignment_recipients_tenant_user_assignment
    ON assignment_recipients(tenant_id, student_user_id, assignment_id);
