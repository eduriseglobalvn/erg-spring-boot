-- BE-060: tenant-scoped SIS/HRIS/SCIM connector orchestration.
-- Only bounded provider metadata and secret references are persisted; upstream
-- payloads and credentials remain inside provider adapters/secret managers.
CREATE TABLE IF NOT EXISTS enterprise_connectors (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(96) NOT NULL,
    provider_kind varchar(16) NOT NULL,
    adapter_key varchar(64) NOT NULL,
    provider_key varchar(96) NOT NULL,
    display_name varchar(255) NOT NULL,
    endpoint varchar(255),
    secret_ref varchar(255) NOT NULL,
    mapping_version varchar(64) NOT NULL,
    mapping_hash varchar(64) NOT NULL,
    status varchar(32) NOT NULL,
    created_by varchar(96) NOT NULL,
    updated_by varchar(96) NOT NULL,
    record_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_enterprise_connectors_kind CHECK (provider_kind IN ('SIS', 'HRIS', 'SCIM')),
    CONSTRAINT ck_enterprise_connectors_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_enterprise_connectors_secret_ref CHECK (secret_ref LIKE 'secret://%'),
    CONSTRAINT ck_enterprise_connectors_mapping_hash CHECK (mapping_hash ~ '^[0-9a-fA-F]{64}$')
);

CREATE TABLE IF NOT EXISTS enterprise_connector_runs (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(96) NOT NULL,
    connector_id varchar(36) NOT NULL,
    mode varchar(32) NOT NULL,
    status varchar(32) NOT NULL,
    processed integer NOT NULL DEFAULT 0,
    created_count integer NOT NULL DEFAULT 0,
    updated_count integer NOT NULL DEFAULT 0,
    failed_count integer NOT NULL DEFAULT 0,
    retry_count integer NOT NULL DEFAULT 0,
    sample_external_ids varchar(2048),
    error_code varchar(96),
    requested_by varchar(96) NOT NULL,
    record_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_enterprise_connector_runs_mode CHECK (mode IN ('DRY_RUN', 'SYNC', 'RETRY', 'DEPROVISION')),
    CONSTRAINT ck_enterprise_connector_runs_status CHECK (status IN ('PREVIEW', 'SUCCEEDED', 'FAILED', 'RETRYABLE_FAILED', 'DEPROVISIONED')),
    CONSTRAINT ck_enterprise_connector_runs_counts CHECK (processed >= 0 AND created_count >= 0 AND updated_count >= 0
        AND failed_count >= 0 AND retry_count >= 0 AND retry_count <= 3)
);

CREATE TABLE IF NOT EXISTS enterprise_connector_command_receipts (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(96) NOT NULL,
    operation varchar(64) NOT NULL,
    idempotency_key varchar(255) NOT NULL,
    payload_hash varchar(64) NOT NULL,
    aggregate_type varchar(64) NOT NULL,
    aggregate_id varchar(96) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_enterprise_connector_receipt_hash CHECK (payload_hash ~ '^[0-9a-fA-F]{64}$')
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_enterprise_connectors_provider
    ON enterprise_connectors (tenant_id, provider_key);
CREATE INDEX IF NOT EXISTS idx_enterprise_connectors_tenant_status
    ON enterprise_connectors (tenant_id, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_enterprise_connector_runs_tenant_connector
    ON enterprise_connector_runs (tenant_id, connector_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_enterprise_connector_runs_tenant_status
    ON enterprise_connector_runs (tenant_id, status, updated_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS ux_enterprise_connector_receipts_key
    ON enterprise_connector_command_receipts (tenant_id, operation, idempotency_key);

DO $$
DECLARE
    table_name text;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'enterprise_connectors',
        'enterprise_connector_runs',
        'enterprise_connector_command_receipts'
    ] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', table_name);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', table_name);
        EXECUTE format('DROP POLICY IF EXISTS %I ON %I', table_name || '_tenant_isolation', table_name);
        EXECUTE format($policy$
            CREATE POLICY %I ON %I
            USING (
                current_setting('app.maintenance_mode', true) = 'true'
                OR (nullif(current_setting('app.tenant_id', true), '') IS NOT NULL
                    AND tenant_id = current_setting('app.tenant_id', true))
            )
            WITH CHECK (
                current_setting('app.maintenance_mode', true) = 'true'
                OR (nullif(current_setting('app.tenant_id', true), '') IS NOT NULL
                    AND tenant_id = current_setting('app.tenant_id', true))
            )
        $policy$, table_name || '_tenant_isolation', table_name);
    END LOOP;
END $$;

INSERT INTO permission_groups (id, created_at, updated_at, name, label, display_order)
VALUES ('pg_enterprise_connectors', now(), now(), 'enterprise_connectors', 'Enterprise Connectors', 117)
ON CONFLICT (name) DO UPDATE SET
    label = EXCLUDED.label,
    display_order = EXCLUDED.display_order,
    updated_at = now();

INSERT INTO permissions (
    id, created_at, updated_at, name, group_name, label, description,
    resource, action, portal, supported_scope, is_system, status
)
VALUES
    ('perm_enterprise_connectors_read', now(), now(), 'enterprise.connectors.read', 'enterprise_connectors',
     'Read enterprise connectors', 'Read tenant-scoped connector registrations and run metadata',
     'enterprise_connectors', 'read', 'admin', 'tenant', false, 'ACTIVE'),
    ('perm_enterprise_connectors_manage', now(), now(), 'enterprise.connectors.manage', 'enterprise_connectors',
     'Manage enterprise connectors', 'Register and configure SIS, HRIS and SCIM connectors',
     'enterprise_connectors', 'manage', 'admin', 'tenant', false, 'ACTIVE'),
    ('perm_enterprise_connectors_sync', now(), now(), 'enterprise.connectors.sync', 'enterprise_connectors',
     'Run enterprise connectors', 'Run dry-run, sync, retry and deprovision operations',
     'enterprise_connectors', 'sync', 'admin', 'tenant', false, 'ACTIVE')
ON CONFLICT (name) DO UPDATE SET
    group_name = EXCLUDED.group_name,
    label = EXCLUDED.label,
    description = EXCLUDED.description,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    portal = EXCLUDED.portal,
    supported_scope = EXCLUDED.supported_scope,
    status = EXCLUDED.status,
    updated_at = now();

WITH runtime_permissions(name) AS (VALUES
    ('enterprise.connectors.read'),
    ('enterprise.connectors.manage'),
    ('enterprise.connectors.sync')
)
INSERT INTO permission_bindings (
    id, permission_id, surface_type, surface_identifier, description, enforced, created_at, updated_at
)
SELECT
    'pb_' || substr(md5(permission.name || ':policy'), 1, 32),
    permission.id,
    'SERVICE',
    'ErgPolicyService#isAllowed:' || permission.name,
    'Runtime permission evaluated by the shared REST/GraphQL policy engine',
    true,
    now(),
    now()
FROM permissions permission
JOIN runtime_permissions runtime ON runtime.name = permission.name
ON CONFLICT (permission_id, surface_type, surface_identifier) DO UPDATE
SET enforced = true, description = EXCLUDED.description, updated_at = now();
