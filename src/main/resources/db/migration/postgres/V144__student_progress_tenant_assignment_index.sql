-- PERF-08: summarizeAssignment / findAssignmentReport filter student_progress by (tenant_id, assignment_id);
-- no existing index leads with that pair, so the teacher assignment-progress screen scanned the tenant.
-- On a large production table pre-build it online first with
-- ops/postgres/online/2026-11-13-student-progress-tenant-assignment.sql; IF NOT EXISTS then skips it here.
CREATE INDEX IF NOT EXISTS idx_student_progress_tenant_assignment
    ON student_progress(tenant_id, assignment_id);
