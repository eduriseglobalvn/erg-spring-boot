-- Plan 4.4 #5: which school subjects (lms_subjects, the ones the school setup screen lists) each class studies in an academic year (previously only in the browser
-- localStorage). One row per (class, subject); a save replaces the whole (school, year) set and bumps
-- `version` so concurrent editors get a 409 instead of silently overwriting each other.
CREATE TABLE IF NOT EXISTS curriculum_class_subject_allocations (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    school_id varchar(255) NOT NULL,
    academic_year varchar(16) NOT NULL,
    academic_class_id varchar(36) NOT NULL REFERENCES academic_classes(id) ON DELETE CASCADE,
    subject_id varchar(36) NOT NULL REFERENCES lms_subjects(id) ON DELETE CASCADE,
    version bigint NOT NULL DEFAULT 1,
    created_by varchar(36),
    updated_by varchar(36),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_curriculum_class_subject_allocation
        UNIQUE (tenant_id, school_id, academic_year, academic_class_id, subject_id)
);

CREATE INDEX IF NOT EXISTS idx_curriculum_class_subject_allocations_scope
    ON curriculum_class_subject_allocations(tenant_id, school_id, academic_year);

ALTER TABLE curriculum_class_subject_allocations ENABLE ROW LEVEL SECURITY;
ALTER TABLE curriculum_class_subject_allocations FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS curriculum_class_subject_allocations_tenant_isolation ON curriculum_class_subject_allocations;
CREATE POLICY curriculum_class_subject_allocations_tenant_isolation ON curriculum_class_subject_allocations
    USING (
        current_setting('app.maintenance_mode', true) = 'true'
        OR (nullif(current_setting('app.tenant_id', true), '') IS NOT NULL
            AND tenant_id = current_setting('app.tenant_id', true))
    )
    WITH CHECK (
        current_setting('app.maintenance_mode', true) = 'true'
        OR (nullif(current_setting('app.tenant_id', true), '') IS NOT NULL
            AND tenant_id = current_setting('app.tenant_id', true))
    );
