-- BE-055: tenant-safe operational directory writes and current class membership.
ALTER TABLE academic_classes ADD COLUMN IF NOT EXISTS record_version bigint NOT NULL DEFAULT 0;
ALTER TABLE lms_students ADD COLUMN IF NOT EXISTS record_version bigint NOT NULL DEFAULT 0;
ALTER TABLE lms_subjects ADD COLUMN IF NOT EXISTS record_version bigint NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX IF NOT EXISTS uq_academic_classes_tenant_school_year_name
    ON academic_classes (tenant_id, school_id, lower(name), coalesce(academic_year, ''));
CREATE UNIQUE INDEX IF NOT EXISTS uq_lms_students_tenant_code
    ON lms_students (tenant_id, lower(student_code));
CREATE UNIQUE INDEX IF NOT EXISTS uq_lms_subjects_tenant_code
    ON lms_subjects (tenant_id, lower(code));

CREATE TABLE IF NOT EXISTS lms_student_enrollments (
    id varchar(36) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(255) NOT NULL,
    school_id varchar(255) NOT NULL,
    student_id varchar(36) NOT NULL,
    class_id varchar(36) NOT NULL,
    academic_year varchar(64),
    status varchar(32) NOT NULL,
    idempotency_key varchar(255),
    created_by varchar(36),
    record_version bigint NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_lms_enrollment_tenant_school
    ON lms_student_enrollments (tenant_id, school_id);
CREATE INDEX IF NOT EXISTS idx_lms_enrollment_tenant_student
    ON lms_student_enrollments (tenant_id, student_id);
CREATE INDEX IF NOT EXISTS idx_lms_enrollment_tenant_class
    ON lms_student_enrollments (tenant_id, class_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_lms_enrollment_tenant_idempotency
    ON lms_student_enrollments (tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_lms_enrollment_current_membership
    ON lms_student_enrollments (tenant_id, student_id, class_id, coalesce(academic_year, ''))
    WHERE status = 'ENROLLED';

INSERT INTO permissions (id, created_at, updated_at, name, group_name, label, description,
                        resource, action, portal, supported_scope, is_system, status)
VALUES
    ('perm_lms_student_manage', now(), now(), 'lms.student.manage', 'operations', 'Quản lý học viên',
     'Create, update, archive and import students within school scope', 'student', 'manage', 'lms', 'school', false, 'ACTIVE'),
    ('perm_lms_subject_manage', now(), now(), 'lms.subject.manage', 'operations', 'Quản lý môn học',
     'Create, update and archive LMS subjects within tenant/school scope', 'subject', 'manage', 'lms', 'tenant,school', false, 'ACTIVE'),
    ('perm_lms_enrollment_manage', now(), now(), 'lms.enrollment.manage', 'operations', 'Quản lý ghi danh',
     'Enroll and move students between classes within school scope', 'enrollment', 'manage', 'lms', 'school', false, 'ACTIVE')
ON CONFLICT (name) DO UPDATE SET
    label = EXCLUDED.label, description = EXCLUDED.description, resource = EXCLUDED.resource,
    action = EXCLUDED.action, portal = EXCLUDED.portal, supported_scope = EXCLUDED.supported_scope,
    status = EXCLUDED.status, updated_at = now();
