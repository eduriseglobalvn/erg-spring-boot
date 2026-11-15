-- V157__period_templates_evening_and_effective_from.sql
-- Board "Gio tiet cua truong": a school keeps several dated period tables ("Ap dung tu 01/11/2026") and may have an
-- evening session (periods 11-12). Templates stay immutable once a batch uses them, so a new season = a new template.
ALTER TABLE teaching_period_templates DROP CONSTRAINT IF EXISTS teaching_period_templates_periods_per_day_check;
ALTER TABLE teaching_period_templates ADD CONSTRAINT chk_tpt_periods_per_day CHECK (periods_per_day BETWEEN 1 AND 12);

ALTER TABLE teaching_period_template_slots DROP CONSTRAINT IF EXISTS teaching_period_template_slots_period_no_check;
ALTER TABLE teaching_period_template_slots DROP CONSTRAINT IF EXISTS teaching_period_template_slots_session_check;
ALTER TABLE teaching_period_template_slots ADD CONSTRAINT chk_tpts_period_no CHECK (period_no BETWEEN 1 AND 12);
ALTER TABLE teaching_period_template_slots
    ADD CONSTRAINT chk_tpts_session CHECK (session IN ('MORNING', 'AFTERNOON', 'EVENING'));

-- Assignment of a table to a school from a date. The sentinel 1970-01-01 marks the single undated row older code wrote.
ALTER TABLE school_period_templates ADD COLUMN tenant_id VARCHAR(36);
UPDATE school_period_templates s SET tenant_id = t.tenant_id FROM teaching_period_templates t WHERE t.id = s.template_id;
ALTER TABLE school_period_templates ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE school_period_templates ADD COLUMN effective_from DATE NOT NULL DEFAULT DATE '1970-01-01';
ALTER TABLE school_period_templates ADD COLUMN updated_by VARCHAR(36);
ALTER TABLE school_period_templates ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE school_period_templates DROP CONSTRAINT school_period_templates_pkey;
ALTER TABLE school_period_templates ADD PRIMARY KEY (school_id, effective_from);
CREATE INDEX IF NOT EXISTS idx_school_period_templates_tenant ON school_period_templates (tenant_id);
CREATE INDEX IF NOT EXISTS idx_school_period_templates_template ON school_period_templates (template_id);

ALTER TABLE school_period_templates ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS school_period_templates_tenant_isolation ON school_period_templates;
CREATE POLICY school_period_templates_tenant_isolation ON school_period_templates
    USING (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''));

-- period_template_id of a batch is looked up when a template is edited ("TEMPLATE_IN_USE").
CREATE INDEX IF NOT EXISTS idx_ts_batches_period_template ON teaching_schedule_batches (period_template_id)
    WHERE period_template_id IS NOT NULL;
