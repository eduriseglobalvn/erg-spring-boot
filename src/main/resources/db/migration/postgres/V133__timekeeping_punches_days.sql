-- Timekeeping (P5) punches, daily results and period locks, plus the per-tenant counters behind the
-- human-readable codes (CC-ddMMyy-nnnn for punches, DT-yyyy-nnnn for requests).
CREATE TABLE IF NOT EXISTS tk_code_counters (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    kind varchar(8) NOT NULL,
    scope varchar(16) NOT NULL,
    last_value bigint NOT NULL DEFAULT 0 CHECK (last_value >= 0),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_tk_code_counters_scope UNIQUE (tenant_id, kind, scope)
);

-- server_time is always the server clock. REJECTED is reached only by an admin review of a stored punch.
CREATE TABLE IF NOT EXISTS tk_punches (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    school_id varchar(255) NOT NULL,
    code varchar(20) NOT NULL,
    user_id varchar(36) NOT NULL,
    site_id varchar(36) REFERENCES tk_sites(id),
    shift_id varchar(36) REFERENCES tk_shifts(id),
    work_date date NOT NULL,
    direction varchar(3) NOT NULL CHECK (direction IN ('IN', 'OUT')),
    server_time timestamp with time zone NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('VALID', 'FLAGGED', 'PENDING_REVIEW', 'REJECTED')),
    method varchar(20),
    remote boolean NOT NULL DEFAULT false,
    mock_reported boolean NOT NULL DEFAULT false,
    lat numeric(9,6) CHECK (lat BETWEEN -90 AND 90),
    lng numeric(9,6) CHECK (lng BETWEEN -180 AND 180),
    accuracy_m integer CHECK (accuracy_m >= 0),
    distance_m integer CHECK (distance_m >= 0),
    reason varchar(500),
    photo_key varchar(512),
    photo_purged_at timestamp with time zone,
    idempotency_key varchar(64) NOT NULL,
    review_decision varchar(10) CHECK (review_decision IN ('ACCEPT', 'REJECT')),
    reviewed_by varchar(36),
    reviewed_at timestamp with time zone,
    review_note varchar(500),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_tk_punches_idempotency UNIQUE (tenant_id, user_id, idempotency_key),
    CONSTRAINT uq_tk_punches_code UNIQUE (tenant_id, code)
);
CREATE INDEX IF NOT EXISTS idx_tk_punches_user_date ON tk_punches(tenant_id, user_id, work_date);
CREATE INDEX IF NOT EXISTS idx_tk_punches_school_date ON tk_punches(tenant_id, school_id, work_date);
CREATE INDEX IF NOT EXISTS idx_tk_punches_site ON tk_punches(site_id);
CREATE INDEX IF NOT EXISTS idx_tk_punches_shift ON tk_punches(shift_id);
CREATE INDEX IF NOT EXISTS idx_tk_punches_photo_purge ON tk_punches(server_time)
    WHERE photo_key IS NOT NULL AND photo_purged_at IS NULL;

CREATE TABLE IF NOT EXISTS tk_days (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    school_id varchar(255) NOT NULL,
    user_id varchar(36) NOT NULL,
    work_date date NOT NULL,
    shift_id varchar(36) REFERENCES tk_shifts(id),
    status varchar(20) NOT NULL CHECK (status IN ('FULL', 'LATE_EARLY', 'HALF', 'LEAVE', 'SICK', 'BUSINESS',
        'MISSING_PUNCH', 'ABSENT_UNPAID', 'HOLIDAY', 'OFF')),
    work_units numeric(3,1) NOT NULL DEFAULT 0 CHECK (work_units >= 0),
    late_minutes integer NOT NULL DEFAULT 0 CHECK (late_minutes >= 0),
    early_minutes integer NOT NULL DEFAULT 0 CHECK (early_minutes >= 0),
    deduct_minutes integer NOT NULL DEFAULT 0 CHECK (deduct_minutes >= 0),
    periods integer NOT NULL DEFAULT 0 CHECK (periods >= 0),
    remote boolean NOT NULL DEFAULT false,
    in_punch_id varchar(36) REFERENCES tk_punches(id),
    out_punch_id varchar(36) REFERENCES tk_punches(id),
    missing varchar(3) CHECK (missing IN ('IN', 'OUT')),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_tk_days_user_date UNIQUE (tenant_id, user_id, work_date)
);
CREATE INDEX IF NOT EXISTS idx_tk_days_school_date ON tk_days(tenant_id, school_id, work_date);
CREATE INDEX IF NOT EXISTS idx_tk_days_shift ON tk_days(shift_id);
CREATE INDEX IF NOT EXISTS idx_tk_days_in_punch ON tk_days(in_punch_id);
CREATE INDEX IF NOT EXISTS idx_tk_days_out_punch ON tk_days(out_punch_id);

CREATE TABLE IF NOT EXISTS tk_period_locks (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    school_id varchar(255) NOT NULL,
    period varchar(7) NOT NULL CHECK (period ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    locked_at timestamp with time zone NOT NULL,
    locked_by varchar(36) NOT NULL,
    reopened_at timestamp with time zone,
    reopened_by varchar(36),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_tk_period_locks_period UNIQUE (tenant_id, school_id, period)
);

ALTER TABLE tk_code_counters ENABLE ROW LEVEL SECURITY;
ALTER TABLE tk_code_counters FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tk_code_counters_tenant_isolation ON tk_code_counters;
CREATE POLICY tk_code_counters_tenant_isolation ON tk_code_counters
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

ALTER TABLE tk_punches ENABLE ROW LEVEL SECURITY;
ALTER TABLE tk_punches FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tk_punches_tenant_isolation ON tk_punches;
CREATE POLICY tk_punches_tenant_isolation ON tk_punches
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

ALTER TABLE tk_days ENABLE ROW LEVEL SECURITY;
ALTER TABLE tk_days FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tk_days_tenant_isolation ON tk_days;
CREATE POLICY tk_days_tenant_isolation ON tk_days
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

ALTER TABLE tk_period_locks ENABLE ROW LEVEL SECURITY;
ALTER TABLE tk_period_locks FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tk_period_locks_tenant_isolation ON tk_period_locks;
CREATE POLICY tk_period_locks_tenant_isolation ON tk_period_locks
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
