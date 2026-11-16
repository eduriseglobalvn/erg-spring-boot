-- V154__lesson_log_audit.sql
-- "Nhat ky so": one row per edited field of a lesson-log entry; created_at is the time of the edit.
CREATE TABLE lms_lesson_log_changes (
    id        VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    entry_id  VARCHAR(36) NOT NULL REFERENCES lms_lesson_log_entries (id),
    actor_id  VARCHAR(36) NOT NULL,
    field     VARCHAR(32) NOT NULL,
    old_value VARCHAR(500),
    new_value VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_lms_lesson_log_changes_entry ON lms_lesson_log_changes (tenant_id, entry_id, created_at);
CREATE INDEX idx_lms_lesson_log_changes_at ON lms_lesson_log_changes (tenant_id, created_at);

ALTER TABLE lms_lesson_log_changes ENABLE ROW LEVEL SECURITY;
ALTER TABLE lms_lesson_log_changes FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS lms_lesson_log_changes_tenant_isolation ON lms_lesson_log_changes;
CREATE POLICY lms_lesson_log_changes_tenant_isolation ON lms_lesson_log_changes
    USING (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''));
CREATE INDEX IF NOT EXISTS idx_lms_lesson_log_changes_tenant ON lms_lesson_log_changes (tenant_id);
