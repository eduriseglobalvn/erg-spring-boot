-- StudentRepository.findByTenantIdAndSchoolIdAndFullNameNormalized (coalesce(school_id, '') shape, same as V185), on the V187 column.
-- Non-transactional by design: ONLY `CREATE INDEX CONCURRENTLY` (see V114).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_lms_students_tenant_school_name_normalized
    ON lms_students (tenant_id, coalesce(school_id, ''), full_name_normalized);
