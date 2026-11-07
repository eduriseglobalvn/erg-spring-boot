-- Timekeeping (P5) configuration: punch sites, shifts, per-school policies (versioned by effective_from so a
-- change can start next period), holidays, remote-work rules and face-photo consent. Tenant-isolated like V128.
CREATE TABLE IF NOT EXISTS tk_sites (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    school_id varchar(255) NOT NULL,
    name varchar(255) NOT NULL,
    address varchar(500),
    lat numeric(9,6) NOT NULL CHECK (lat BETWEEN -90 AND 90),
    lng numeric(9,6) NOT NULL CHECK (lng BETWEEN -180 AND 180),
    radius_m integer NOT NULL DEFAULT 150 CHECK (radius_m BETWEEN 50 AND 500),
    active boolean NOT NULL DEFAULT true,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_tk_sites_school ON tk_sites(tenant_id, school_id);

-- weekdays: ISO day numbers, 1 = Monday .. 7 = Sunday.
CREATE TABLE IF NOT EXISTS tk_shifts (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    school_id varchar(255) NOT NULL,
    name varchar(255) NOT NULL,
    start_time time NOT NULL,
    end_time time NOT NULL,
    work_units numeric(3,1) NOT NULL DEFAULT 1 CHECK (work_units > 0),
    weekdays integer[] NOT NULL CHECK (cardinality(weekdays) > 0 AND weekdays <@ ARRAY[1,2,3,4,5,6,7]),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT ck_tk_shifts_time_order CHECK (end_time > start_time)
);
CREATE INDEX IF NOT EXISTS idx_tk_shifts_school ON tk_shifts(tenant_id, school_id);

CREATE TABLE IF NOT EXISTS tk_policies (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    school_id varchar(255) NOT NULL,
    effective_from date NOT NULL,
    late_tolerance_min integer NOT NULL DEFAULT 5 CHECK (late_tolerance_min >= 0),
    late_block_min integer NOT NULL DEFAULT 15 CHECK (late_block_min > 0),
    require_face boolean NOT NULL DEFAULT true,
    allow_outside boolean NOT NULL DEFAULT false,
    explanations_per_month integer NOT NULL DEFAULT 3 CHECK (explanations_per_month >= 0),
    explanation_window_days integer NOT NULL DEFAULT 3 CHECK (explanation_window_days >= 0),
    annual_leave_days integer NOT NULL DEFAULT 12 CHECK (annual_leave_days >= 0),
    seniority_step_years integer NOT NULL DEFAULT 5 CHECK (seniority_step_years > 0),
    lock_day_of_month integer NOT NULL DEFAULT 8 CHECK (lock_day_of_month BETWEEN 1 AND 28),
    approval_chains jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_tk_policies_school_effective UNIQUE (tenant_id, school_id, effective_from)
);

-- school_id NULL = applies to every school of the tenant.
CREATE TABLE IF NOT EXISTS tk_holidays (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    school_id varchar(255),
    holiday_date date NOT NULL,
    name varchar(255) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_tk_holidays_scope_date
    ON tk_holidays(tenant_id, coalesce(school_id, ''), holiday_date);

CREATE TABLE IF NOT EXISTS tk_remote_rules (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    school_id varchar(255) NOT NULL,
    kind varchar(20) NOT NULL CHECK (kind IN ('USERS', 'WEEKDAY', 'APPROVED_WFH')),
    user_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
    weekday integer CHECK (weekday BETWEEN 1 AND 7),
    shift_id varchar(36) REFERENCES tk_shifts(id) ON DELETE RESTRICT,
    active boolean NOT NULL DEFAULT true,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT ck_tk_remote_rules_weekday CHECK (kind <> 'WEEKDAY' OR weekday IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS idx_tk_remote_rules_school ON tk_remote_rules(tenant_id, school_id);
CREATE INDEX IF NOT EXISTS idx_tk_remote_rules_shift ON tk_remote_rules(shift_id);

CREATE TABLE IF NOT EXISTS tk_face_consents (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    user_id varchar(36) NOT NULL,
    consented_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_tk_face_consents_user UNIQUE (tenant_id, user_id)
);

ALTER TABLE tk_sites ENABLE ROW LEVEL SECURITY;
ALTER TABLE tk_sites FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tk_sites_tenant_isolation ON tk_sites;
CREATE POLICY tk_sites_tenant_isolation ON tk_sites
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

ALTER TABLE tk_shifts ENABLE ROW LEVEL SECURITY;
ALTER TABLE tk_shifts FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tk_shifts_tenant_isolation ON tk_shifts;
CREATE POLICY tk_shifts_tenant_isolation ON tk_shifts
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

ALTER TABLE tk_policies ENABLE ROW LEVEL SECURITY;
ALTER TABLE tk_policies FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tk_policies_tenant_isolation ON tk_policies;
CREATE POLICY tk_policies_tenant_isolation ON tk_policies
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

ALTER TABLE tk_holidays ENABLE ROW LEVEL SECURITY;
ALTER TABLE tk_holidays FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tk_holidays_tenant_isolation ON tk_holidays;
CREATE POLICY tk_holidays_tenant_isolation ON tk_holidays
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

ALTER TABLE tk_remote_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE tk_remote_rules FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tk_remote_rules_tenant_isolation ON tk_remote_rules;
CREATE POLICY tk_remote_rules_tenant_isolation ON tk_remote_rules
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

ALTER TABLE tk_face_consents ENABLE ROW LEVEL SECURITY;
ALTER TABLE tk_face_consents FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tk_face_consents_tenant_isolation ON tk_face_consents;
CREATE POLICY tk_face_consents_tenant_isolation ON tk_face_consents
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
