-- Quiz attempt list filters (tenant_id, quiz_id) and orders by updated_at DESC, id DESC.
-- Non-transactional by design: ONLY `CREATE INDEX CONCURRENTLY` (see V114).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_lms_attempts_tenant_quiz_updated
    ON lms_attempts (tenant_id, quiz_id, updated_at DESC, id DESC);
