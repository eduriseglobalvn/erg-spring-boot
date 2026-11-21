-- TkPunchRepository.findUsersInWithoutOut filters (tenant_id, shift_id, work_date).
-- Non-transactional by design: ONLY `CREATE INDEX CONCURRENTLY` (see V114).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_tk_punches_tenant_shift_date
    ON tk_punches (tenant_id, shift_id, work_date);
