-- Extend tenant isolation to every remaining table that carries tenant_id.
--
-- RLS is enabled but deliberately not FORCEd for legacy tables during the strangler rollout:
-- the dedicated runtime role (erg_app) is not the table owner and is therefore constrained,
-- while Flyway/maintenance ownership can still perform expand/backfill migrations. New modules
-- should use FORCE RLS when all maintenance paths explicitly apply TenantContext.maintenance().
-- The policy itself is strict for requests and explicitly permits only maintenance mode.
DO $$
DECLARE
    tenant_table text;
BEGIN
    FOR tenant_table IN
        SELECT DISTINCT c.relname
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        JOIN pg_attribute a ON a.attrelid = c.oid
        WHERE n.nspname = 'public'
          AND c.relkind = 'r'
          AND a.attname = 'tenant_id'
          AND NOT a.attisdropped
          AND NOT c.relrowsecurity
        ORDER BY c.relname
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tenant_table);
        EXECUTE format('DROP POLICY IF EXISTS %I ON %I', tenant_table || '_tenant_isolation', tenant_table);
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
        $policy$, tenant_table || '_tenant_isolation', tenant_table);
    END LOOP;
END $$;
