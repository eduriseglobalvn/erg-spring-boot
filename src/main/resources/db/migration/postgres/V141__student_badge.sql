-- Weekly badges (WEEK_GOLD / WEEK_SILVER / WEEK_BRONZE / STREAK_7 / BREAKTHROUGH_3), granted when the week closes.
CREATE TABLE IF NOT EXISTS student_badge (
    tenant_id  varchar(255) NOT NULL,
    student_id varchar(24)  NOT NULL,
    badge_key  varchar(24)  NOT NULL,
    week_start date         NOT NULL,
    awarded_at timestamp with time zone NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, student_id, badge_key, week_start),
    CONSTRAINT chk_student_badge_key CHECK (badge_key IN ('WEEK_GOLD', 'WEEK_SILVER', 'WEEK_BRONZE', 'STREAK_7', 'BREAKTHROUGH_3'))
);

-- Tenant isolation, same policy as the other tenant tables.
ALTER TABLE student_badge ENABLE ROW LEVEL SECURITY;
CREATE POLICY student_badge_tenant_isolation ON student_badge
    USING (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''));

-- Tenant-leading indexes (the tenant migration policy test looks for an explicit one).
CREATE INDEX IF NOT EXISTS idx_student_badge_tenant ON student_badge (tenant_id);
