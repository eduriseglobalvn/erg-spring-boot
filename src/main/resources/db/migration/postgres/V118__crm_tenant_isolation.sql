-- C4 / D5: CRM school tables carry their own tenant_id so reads and writes can be
-- tenant-filtered without relying on an indirect join through education_units,
-- and the shared RLS policy (V75/V90 shape) protects them as a second layer.
-- Expand: add nullable column, backfill from the owning education unit (FK-validated
-- in V16, so every row has one), then enforce NOT NULL.
DO $$
DECLARE
    crm_table text;
BEGIN
    FOREACH crm_table IN ARRAY ARRAY[
        'crm_school_profiles',
        'crm_school_contacts',
        'crm_school_deals',
        'crm_school_activities',
        'crm_referrals'
    ] LOOP
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS tenant_id varchar(36)', crm_table);
        EXECUTE format(
            'UPDATE %I AS crm SET tenant_id = unit.tenant_id FROM education_units unit '
                || 'WHERE unit.id = crm.school_id AND crm.tenant_id IS NULL',
            crm_table);
        EXECUTE format('ALTER TABLE %I ALTER COLUMN tenant_id SET NOT NULL', crm_table);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (tenant_id, school_id)',
            'idx_' || crm_table || '_tenant_school', crm_table);

        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', crm_table);
        EXECUTE format('DROP POLICY IF EXISTS %I ON %I', crm_table || '_tenant_isolation', crm_table);
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
        $policy$, crm_table || '_tenant_isolation', crm_table);
    END LOOP;
END $$;

CREATE INDEX IF NOT EXISTS idx_crm_school_deals_tenant_owner_stage
    ON crm_school_deals (tenant_id, owner_user_id, stage);
