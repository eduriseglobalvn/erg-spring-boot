-- Assignment attempt summaries filter (tenant_id, assignment_id) and order by updated_at DESC (status optional, so
-- the V9 status-first index cannot serve the unfiltered list). Non-transactional by design: ONLY `CREATE INDEX CONCURRENTLY` (see V114).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_lms_attempts_tenant_assignment_updated
    ON lms_attempts (tenant_id, assignment_id, updated_at DESC, id DESC);
