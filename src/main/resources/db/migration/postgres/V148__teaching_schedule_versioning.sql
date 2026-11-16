-- V148__teaching_schedule_versioning.sql
-- Optimistic locking, batch labels, per-tenant load thresholds (KE_HOACH_LICH_DAY.md 4.1).
-- Ids in this codebase are VARCHAR(36), so period_template_id follows suit (the plan wrote UUID).
ALTER TABLE teaching_schedule_batches ADD COLUMN record_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE teaching_schedule_batches ADD COLUMN label VARCHAR(80);
ALTER TABLE teaching_schedule_batches ADD COLUMN period_template_id VARCHAR(36);
ALTER TABLE teaching_schedule_events ADD COLUMN record_version BIGINT NOT NULL DEFAULT 0;

-- Existing batches get "Dot <n>" per school in creation order (labels must exist before the unique index).
UPDATE teaching_schedule_batches b
SET label = 'Đợt ' || numbered.rn
FROM (
    SELECT id, row_number() OVER (PARTITION BY tenant_id, school_id ORDER BY created_at, id) AS rn
    FROM teaching_schedule_batches
) numbered
WHERE b.id = numbered.id;

-- Batch name is unique within a school, case-insensitive, ignoring cancelled batches.
CREATE UNIQUE INDEX IF NOT EXISTS ux_ts_batch_label
    ON teaching_schedule_batches (tenant_id, school_id, lower(label))
    WHERE status <> 'CANCELLED';

-- Weekly load thresholds, one row per tenant (default 18 warn / 24 max, editable in settings).
CREATE TABLE IF NOT EXISTS teaching_schedule_settings (
    tenant_id  VARCHAR(64) PRIMARY KEY,
    load_warn  INT NOT NULL DEFAULT 18 CHECK (load_warn BETWEEN 1 AND 60),
    load_max   INT NOT NULL DEFAULT 24 CHECK (load_max BETWEEN 1 AND 60),
    updated_by VARCHAR(64),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (load_warn <= load_max)
);

-- Tenant isolation like the other tenant tables (runtime role is not the owner, so this applies).
ALTER TABLE teaching_schedule_settings ENABLE ROW LEVEL SECURITY;
CREATE POLICY teaching_schedule_settings_tenant_isolation ON teaching_schedule_settings
    USING (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''));
CREATE INDEX IF NOT EXISTS idx_teaching_schedule_settings_tenant ON teaching_schedule_settings (tenant_id);
