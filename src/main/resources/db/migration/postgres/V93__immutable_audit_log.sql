-- BE-061: tenant-scoped, append-only audit journal with bounded retention.
CREATE TABLE IF NOT EXISTS audit_events (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(96) NOT NULL,
    actor_id varchar(96),
    scope_type varchar(64),
    scope_id varchar(96),
    action varchar(128) NOT NULL,
    resource_type varchar(96) NOT NULL,
    resource_id varchar(96),
    outcome varchar(24) NOT NULL,
    request_id varchar(128),
    correlation_id varchar(128),
    causation_id varchar(128),
    details jsonb NOT NULL DEFAULT '{}'::jsonb,
    idempotency_key varchar(192) NOT NULL,
    previous_hash varchar(64),
    event_hash varchar(64) NOT NULL,
    occurred_at timestamptz NOT NULL,
    retention_until timestamptz NOT NULL,
    redaction_version integer NOT NULL DEFAULT 1,
    legal_hold boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_audit_events_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED', 'PARTIAL')),
    CONSTRAINT ck_audit_events_scope_type CHECK (
        scope_type IS NULL OR scope_type IN ('TENANT', 'EDUCATION_UNIT', 'CLASS')
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_audit_events_tenant_idempotency
    ON audit_events (tenant_id, idempotency_key);
CREATE INDEX IF NOT EXISTS idx_audit_events_tenant_time
    ON audit_events (tenant_id, occurred_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_audit_events_retention
    ON audit_events (retention_until, legal_hold);
CREATE INDEX IF NOT EXISTS idx_audit_events_resource
    ON audit_events (tenant_id, resource_type, resource_id, occurred_at DESC);

ALTER TABLE audit_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_events FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS audit_events_tenant_isolation ON audit_events;
CREATE POLICY audit_events_tenant_isolation ON audit_events
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

CREATE OR REPLACE FUNCTION erg_audit_events_append_only() RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE'
       AND current_setting('app.audit_retention_job', true) = 'true' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'audit_events_append_only';
END;
$$;

DROP TRIGGER IF EXISTS audit_events_append_only ON audit_events;
CREATE TRIGGER audit_events_append_only
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION erg_audit_events_append_only();

INSERT INTO permission_groups (id, created_at, updated_at, name, label, display_order)
VALUES ('pg_audit', now(), now(), 'audit', 'Audit & Compliance', 110)
ON CONFLICT (name) DO UPDATE SET
    label = EXCLUDED.label,
    display_order = EXCLUDED.display_order,
    updated_at = now();

INSERT INTO permissions (
    id, created_at, updated_at, name, group_name, label, description,
    resource, action, portal, supported_scope, is_system, status
)
VALUES
    ('perm_audit_read', now(), now(), 'audit.read', 'audit', 'Read audit events', 'Read tenant-scoped audit events', 'audit', 'read', 'admin', 'tenant,school,class', false, 'ACTIVE'),
    ('perm_audit_export', now(), now(), 'audit.export', 'audit', 'Export audit events', 'Export bounded audit pages', 'audit', 'export', 'admin', 'tenant,school,class', false, 'ACTIVE'),
    ('perm_audit_manage', now(), now(), 'audit.manage', 'audit', 'Manage audit policy', 'Manage audit retention and legal holds', 'audit', 'manage', 'admin', 'tenant', false, 'ACTIVE')
ON CONFLICT (name) DO UPDATE SET
    group_name = EXCLUDED.group_name,
    label = EXCLUDED.label,
    description = EXCLUDED.description,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    portal = EXCLUDED.portal,
    supported_scope = EXCLUDED.supported_scope,
    is_system = EXCLUDED.is_system,
    status = EXCLUDED.status,
    updated_at = now();
