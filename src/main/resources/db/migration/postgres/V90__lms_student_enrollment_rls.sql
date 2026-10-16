-- BE-062: apply the tenant RLS contract to the operational enrollment table added by V88.
-- This is a forward-only repair; V88 remains immutable for already-applied databases.
ALTER TABLE lms_student_enrollments ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS lms_student_enrollments_tenant_isolation ON lms_student_enrollments;
CREATE POLICY lms_student_enrollments_tenant_isolation ON lms_student_enrollments
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
