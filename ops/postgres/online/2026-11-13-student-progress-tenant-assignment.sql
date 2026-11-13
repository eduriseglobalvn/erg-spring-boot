-- PERF-08 online index for summarizeAssignment / findAssignmentReport (student_progress by tenant + assignment).
-- Shipped by Flyway V144 (plain CREATE INDEX, takes a write lock while it builds). Run this script ahead of
-- the deploy on a large table so the build does not block writes; V144 then skips it (IF NOT EXISTS).
-- Must run outside a transaction (psql autocommit). An interrupted build leaves an INVALID index:
-- drop it and re-run (see docs/runbooks/postgres-migration-preflight.md).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_student_progress_tenant_assignment
    ON student_progress(tenant_id, assignment_id);
