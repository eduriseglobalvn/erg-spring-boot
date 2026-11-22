-- StudentRepository.findByTenantIdAndSchoolIdAndFullNameIgnoreCase (explicit lower() / coalesce(school_id, '') query,
-- same shape as V164). Non-transactional by design: ONLY `CREATE INDEX CONCURRENTLY` (see V114).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_lms_students_tenant_school_name_lower
    ON lms_students (tenant_id, coalesce(school_id, ''), lower(full_name));
