-- V149 created teaching_period_templates without tenant isolation; add it like the other tenant tables.
ALTER TABLE teaching_period_templates ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS teaching_period_templates_tenant_isolation ON teaching_period_templates;
CREATE POLICY teaching_period_templates_tenant_isolation ON teaching_period_templates
    USING (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''));
CREATE INDEX IF NOT EXISTS idx_teaching_period_templates_tenant ON teaching_period_templates (tenant_id);
