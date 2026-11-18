-- BUG-A-003: a student code is unique inside one school, not across the tenant.
-- Two schools both have a class 6A1 whose student no. 1 gets the code "6A1-1".
DROP INDEX IF EXISTS uq_lms_students_tenant_code;
CREATE UNIQUE INDEX IF NOT EXISTS uq_lms_students_tenant_school_code
    ON lms_students (tenant_id, coalesce(school_id, ''), lower(student_code));
