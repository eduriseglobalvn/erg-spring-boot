-- V150__school_travel_times.sql
-- Minutes a teacher needs to get between two schools; the pair is stored once with school_a_id < school_b_id.
-- No row means the default from erg.calendar.travel.default-minutes (50).
CREATE TABLE school_travel_times (
    tenant_id   VARCHAR(36) NOT NULL,
    school_a_id VARCHAR(36) NOT NULL,
    school_b_id VARCHAR(36) NOT NULL,
    minutes     SMALLINT NOT NULL CHECK (minutes BETWEEN 0 AND 240),
    updated_by  VARCHAR(36),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, school_a_id, school_b_id),
    CHECK (school_a_id < school_b_id)
);

-- Tenant isolation like the other tenant tables (runtime role is not the owner, so this applies).
ALTER TABLE school_travel_times ENABLE ROW LEVEL SECURITY;
CREATE POLICY school_travel_times_tenant_isolation ON school_travel_times
    USING (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''));
CREATE INDEX IF NOT EXISTS idx_school_travel_times_tenant ON school_travel_times (tenant_id);
