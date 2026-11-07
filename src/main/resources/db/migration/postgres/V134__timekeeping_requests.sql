-- Timekeeping (P5) requests (leave, explanation, WFH, business trip), their approval steps, schedule
-- evidence (no file attachments, D14) and an action log.
CREATE TABLE IF NOT EXISTS tk_requests (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    school_id varchar(255) NOT NULL,
    code varchar(20) NOT NULL,
    user_id varchar(36) NOT NULL,
    type varchar(20) NOT NULL CHECK (type IN ('LEAVE', 'EXPLANATION', 'WFH', 'BUSINESS')),
    leave_kind varchar(20) CHECK (leave_kind IN ('ANNUAL', 'SICK', 'PAID_PERSONAL', 'UNPAID')),
    from_date date,
    to_date date,
    part varchar(4) NOT NULL DEFAULT 'FULL' CHECK (part IN ('FULL', 'AM', 'PM')),
    days numeric(4,1) CHECK (days >= 0),
    work_date date,
    proposed_out time,
    explanation_reason varchar(20) CHECK (explanation_reason IN ('FORGOT', 'DEVICE', 'OUTSIDE', 'BUSINESS_TRIP')),
    note varchar(1000),
    status varchar(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    current_step integer,
    submitted_at timestamp with time zone,
    last_reminded_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_tk_requests_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_tk_requests_date_order CHECK (from_date IS NULL OR to_date IS NULL OR to_date >= from_date)
);
CREATE INDEX IF NOT EXISTS idx_tk_requests_user_status ON tk_requests(tenant_id, user_id, status);
CREATE INDEX IF NOT EXISTS idx_tk_requests_school_status ON tk_requests(tenant_id, school_id, status);

-- resolver: MANAGER or SCHOOL_ROLE:<roles.name>. activated_at/due_at drive the overdue badge and reminders.
CREATE TABLE IF NOT EXISTS tk_request_steps (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    request_id varchar(36) NOT NULL REFERENCES tk_requests(id) ON DELETE CASCADE,
    step_no integer NOT NULL CHECK (step_no >= 1),
    resolver varchar(64) NOT NULL,
    approver_id varchar(36),
    status varchar(20) NOT NULL DEFAULT 'WAITING' CHECK (status IN ('WAITING', 'APPROVED', 'REJECTED', 'SKIPPED')),
    acted_by varchar(36),
    acted_at timestamp with time zone,
    on_behalf boolean NOT NULL DEFAULT false,
    comment varchar(1000),
    viewed_at timestamp with time zone,
    activated_at timestamp with time zone,
    due_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_tk_request_steps_no UNIQUE (request_id, step_no)
);
CREATE INDEX IF NOT EXISTS idx_tk_request_steps_approver ON tk_request_steps(tenant_id, approver_id, status);

CREATE TABLE IF NOT EXISTS tk_request_evidence (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    request_id varchar(36) NOT NULL REFERENCES tk_requests(id) ON DELETE CASCADE,
    kind varchar(20) NOT NULL DEFAULT 'SCHEDULE' CHECK (kind = 'SCHEDULE'),
    ref varchar(64) NOT NULL,
    label varchar(255),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_tk_request_evidence_request ON tk_request_evidence(request_id);

CREATE TABLE IF NOT EXISTS tk_audit_log (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    actor_id varchar(36) NOT NULL,
    action varchar(64) NOT NULL,
    target_type varchar(32) NOT NULL,
    target_id varchar(36) NOT NULL,
    detail jsonb NOT NULL DEFAULT '{}'::jsonb,
    at timestamp with time zone NOT NULL DEFAULT now(),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_tk_audit_log_target ON tk_audit_log(tenant_id, target_type, target_id);

ALTER TABLE tk_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE tk_requests FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tk_requests_tenant_isolation ON tk_requests;
CREATE POLICY tk_requests_tenant_isolation ON tk_requests
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

ALTER TABLE tk_request_steps ENABLE ROW LEVEL SECURITY;
ALTER TABLE tk_request_steps FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tk_request_steps_tenant_isolation ON tk_request_steps;
CREATE POLICY tk_request_steps_tenant_isolation ON tk_request_steps
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

ALTER TABLE tk_request_evidence ENABLE ROW LEVEL SECURITY;
ALTER TABLE tk_request_evidence FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tk_request_evidence_tenant_isolation ON tk_request_evidence;
CREATE POLICY tk_request_evidence_tenant_isolation ON tk_request_evidence
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

ALTER TABLE tk_audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE tk_audit_log FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tk_audit_log_tenant_isolation ON tk_audit_log;
CREATE POLICY tk_audit_log_tenant_isolation ON tk_audit_log
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
