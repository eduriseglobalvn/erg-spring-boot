CREATE INDEX IF NOT EXISTS idx_lms_assignments_read_class_status_due
    ON lms_assignments(tenant_id, academic_class_id, status, due_at, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_lms_assignments_read_teacher_status_created
    ON lms_assignments(tenant_id, assigned_by, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_lms_attempts_read_assignment_status_updated
    ON lms_attempts(tenant_id, assignment_id, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_lms_attempts_read_student_updated
    ON lms_attempts(tenant_id, student_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_academic_classes_read_school_status_name
    ON academic_classes(tenant_id, school_id, status, name);

CREATE INDEX IF NOT EXISTS idx_lms_students_read_class_status_name_code
    ON lms_students(tenant_id, academic_class_id, status, full_name, student_code);

CREATE INDEX IF NOT EXISTS idx_hoclieu_resources_read_library_scope
    ON hoclieu_resources(
        tenant_id,
        subject_id,
        grade_id,
        category_id,
        section_id,
        topic_id,
        status,
        visibility,
        published_at DESC,
        updated_at DESC
    );

CREATE INDEX IF NOT EXISTS idx_hoclieu_resources_read_visibility_updated
    ON hoclieu_resources(tenant_id, status, visibility, updated_at DESC, title);
