-- N1: apply the tenant RLS contract (same policy shape as V90) to the curriculum tables added by
-- V101/V106. Forward-only repair; V101/V106 stay immutable. FORCE matches the newer modules
-- (V92-V96) so the table owner is constrained too; the V102/V107 seed rows were written before this.
DO $$
DECLARE
    table_name text;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'curriculum_teaching_plan_settings',
        'curriculum_programs',
        'curriculum_program_rows'
    ]
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', table_name);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', table_name);
        EXECUTE format('DROP POLICY IF EXISTS %I ON %I', table_name || '_tenant_isolation', table_name);
        EXECUTE format($policy$
            CREATE POLICY %I ON %I
                USING (
                    current_setting('app.maintenance_mode', true) = 'true'
                    OR (nullif(current_setting('app.tenant_id', true), '') IS NOT NULL
                        AND tenant_id = current_setting('app.tenant_id', true))
                )
                WITH CHECK (
                    current_setting('app.maintenance_mode', true) = 'true'
                    OR (nullif(current_setting('app.tenant_id', true), '') IS NOT NULL
                        AND tenant_id = current_setting('app.tenant_id', true))
                )
        $policy$, table_name || '_tenant_isolation', table_name);
    END LOOP;
END $$;
