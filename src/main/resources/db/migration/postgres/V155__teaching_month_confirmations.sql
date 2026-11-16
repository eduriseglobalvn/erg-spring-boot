-- V155__teaching_month_confirmations.sql
-- Monthly teaching-period sheet: per-teacher confirmation, HR close, and the snapshot taken at close
-- (KE_HOACH_CONG_BAO_GIANG.md 4.1). Surrogate ids + unique natural keys instead of the plan's composite primary keys,
-- so the entities extend BaseEntity like every other table.
CREATE TABLE tk_teaching_months (
    id        VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    month     VARCHAR(7) NOT NULL,
    status    VARCHAR(16) NOT NULL DEFAULT 'COLLECTING' CHECK (status IN ('COLLECTING', 'READY', 'CLOSED')),
    closed_at TIMESTAMPTZ,
    closed_by VARCHAR(36),
    version   BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_tk_teaching_months UNIQUE (tenant_id, month)
);

CREATE TABLE tk_teaching_confirmations (
    id                         VARCHAR(36) PRIMARY KEY,
    tenant_id                  VARCHAR(255) NOT NULL,
    month                      VARCHAR(7) NOT NULL,
    teacher_user_id            VARCHAR(36) NOT NULL,
    status                     VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'CONFIRMED', 'ISSUE_REPORTED')),
    confirmed_total            INTEGER,
    confirmed_assistant_total  INTEGER,
    confirmed_at               TIMESTAMPTZ,
    issue_note                 VARCHAR(1000),
    version                    BIGINT NOT NULL DEFAULT 0,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_tk_teaching_confirmations UNIQUE (tenant_id, month, teacher_user_id)
);

-- Snapshot written when HR closes the month; role keeps the main-teacher and assistant sheets apart.
CREATE TABLE tk_teaching_month_lines (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(255) NOT NULL,
    month           VARCHAR(7) NOT NULL,
    teacher_user_id VARCHAR(36) NOT NULL,
    role            VARCHAR(16) NOT NULL CHECK (role IN ('MAIN', 'ASSISTANT')),
    school_id       VARCHAR(255) NOT NULL,
    subject_id      VARCHAR(100) NOT NULL,
    class_names     TEXT NOT NULL,
    week_totals     INTEGER[] NOT NULL,
    total           INTEGER NOT NULL CHECK (total >= 0),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_tk_teaching_month_lines UNIQUE (tenant_id, month, teacher_user_id, role, school_id, subject_id)
);

ALTER TABLE tk_teaching_months ENABLE ROW LEVEL SECURITY;
ALTER TABLE tk_teaching_months FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tk_teaching_months_tenant_isolation ON tk_teaching_months;
CREATE POLICY tk_teaching_months_tenant_isolation ON tk_teaching_months
    USING (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''));
CREATE INDEX IF NOT EXISTS idx_tk_teaching_months_tenant ON tk_teaching_months (tenant_id);

ALTER TABLE tk_teaching_confirmations ENABLE ROW LEVEL SECURITY;
ALTER TABLE tk_teaching_confirmations FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tk_teaching_confirmations_tenant_isolation ON tk_teaching_confirmations;
CREATE POLICY tk_teaching_confirmations_tenant_isolation ON tk_teaching_confirmations
    USING (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''));
CREATE INDEX IF NOT EXISTS idx_tk_teaching_confirmations_tenant ON tk_teaching_confirmations (tenant_id);

ALTER TABLE tk_teaching_month_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE tk_teaching_month_lines FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tk_teaching_month_lines_tenant_isolation ON tk_teaching_month_lines;
CREATE POLICY tk_teaching_month_lines_tenant_isolation ON tk_teaching_month_lines
    USING (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''));
CREATE INDEX IF NOT EXISTS idx_tk_teaching_month_lines_tenant ON tk_teaching_month_lines (tenant_id);
