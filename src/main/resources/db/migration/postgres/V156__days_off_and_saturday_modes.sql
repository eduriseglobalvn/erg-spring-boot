-- LD B11 (KE_HOACH_LICH_DAY.md 2.6): days off with kind / pay mode / teaching action, per-day Saturday modes, and
-- the link from a period cancelled by a holiday back to that holiday (so removing the holiday restores only those).
-- V153-V155 belong to the CBG branch (KE_HOACH_MASTER.md section 2).
ALTER TABLE tk_holidays ADD COLUMN kind varchar(20) NOT NULL DEFAULT 'STATE'
    CHECK (kind IN ('STATE', 'COMPANY', 'COMPENSATORY'));
-- DEFAULT only fills the old rows; the service requires an explicit pay_mode (2.6: no default).
ALTER TABLE tk_holidays ADD COLUMN pay_mode varchar(20) NOT NULL DEFAULT 'PAID'
    CHECK (pay_mode IN ('PAID', 'ANNUAL_LEAVE', 'UNPAID'));
ALTER TABLE tk_holidays ADD COLUMN teaching_action varchar(20) NOT NULL DEFAULT 'CANCEL'
    CHECK (teaching_action IN ('CANCEL', 'KEEP'));
ALTER TABLE tk_holidays ADD COLUMN created_by varchar(36) NULL;
-- State holidays and compensatory days are always paid by law.
ALTER TABLE tk_holidays ADD CONSTRAINT ck_tk_holidays_state_paid CHECK (kind = 'COMPANY' OR pay_mode = 'PAID');
CREATE INDEX IF NOT EXISTS ix_tk_holidays_tenant_date ON tk_holidays (tenant_id, holiday_date);

-- No row for a Saturday means OFFICE.
CREATE TABLE IF NOT EXISTS tk_saturday_modes (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    work_date date NOT NULL CHECK (extract(isodow FROM work_date) = 6),
    mode varchar(20) NOT NULL CHECK (mode IN ('OFFICE', 'WFH', 'PAID_OFF', 'UNPAID_OFF')),
    created_by varchar(36) NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_tk_saturday_modes_tenant ON tk_saturday_modes (tenant_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_tk_saturday_modes_date ON tk_saturday_modes (tenant_id, work_date);

ALTER TABLE tk_saturday_modes ENABLE ROW LEVEL SECURITY;
ALTER TABLE tk_saturday_modes FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tk_saturday_modes_tenant_isolation ON tk_saturday_modes;
CREATE POLICY tk_saturday_modes_tenant_isolation ON tk_saturday_modes
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

ALTER TABLE teaching_schedule_events ADD COLUMN cancelled_by_holiday_id varchar(36) NULL;
CREATE INDEX IF NOT EXISTS ix_tse_cancelled_by_holiday ON teaching_schedule_events (tenant_id, cancelled_by_holiday_id)
    WHERE cancelled_by_holiday_id IS NOT NULL;

-- KL: an unpaid day off (0 công), shown as "KL" on the month sheet.
ALTER TABLE tk_days DROP CONSTRAINT IF EXISTS tk_days_status_check;
ALTER TABLE tk_days ADD CONSTRAINT tk_days_status_check CHECK (status IN ('FULL', 'LATE_EARLY', 'HALF', 'LEAVE', 'SICK',
    'BUSINESS', 'MISSING_PUNCH', 'ABSENT_UNPAID', 'HOLIDAY', 'UNPAID_OFF', 'OFF'));
