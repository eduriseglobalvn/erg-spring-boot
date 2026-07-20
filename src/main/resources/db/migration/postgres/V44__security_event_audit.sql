CREATE TABLE IF NOT EXISTS security_events (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(36) NOT NULL,
    user_id varchar(36),
    event_type varchar(100) NOT NULL,
    severity varchar(20) NOT NULL,
    portal varchar(50),
    device_id varchar(255),
    ip_address varchar(100),
    reason varchar(500),
    occurred_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_security_events_tenant_user_time
    ON security_events (tenant_id, user_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_security_events_type_time
    ON security_events (event_type, occurred_at DESC);
