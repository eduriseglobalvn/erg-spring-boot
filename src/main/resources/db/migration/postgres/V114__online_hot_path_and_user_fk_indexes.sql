-- N7 + N5: bring the measured "online" indexes from ops/postgres/online/*.sql under Flyway and add
-- leading user indexes for the user FK cascades (notification_recipients, student_progress).
--
-- Non-transactional by design: this file contains ONLY `CREATE INDEX CONCURRENTLY` statements.
-- Flyway's PostgreSQL parser detects CONCURRENTLY and runs the statements outside a transaction;
-- adding any transactional statement here would make Flyway refuse the mixed migration.
-- IF NOT EXISTS makes it a no-op where the ops/postgres/online scripts were already applied.
-- If a build is interrupted it can leave an INVALID index; see docs/runbooks/postgres-migration-preflight.md.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_assignment_recipients_tenant_user_assignment
    ON assignment_recipients(tenant_id, student_user_id, assignment_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_activities_tenant_created_id
    ON user_activities(tenant_id, created_at DESC, id DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_lms_attempts_tenant_version_status_id
    ON lms_attempts(tenant_id, quiz_version_id, status, id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notification_recipients_user_id
    ON notification_recipients(user_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_student_progress_student_user_id
    ON student_progress(student_user_id);
