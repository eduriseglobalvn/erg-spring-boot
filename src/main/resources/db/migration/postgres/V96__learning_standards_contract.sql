-- BE-059: tenant-scoped SCORM/xAPI/LTI integration boundary.
-- Protocol payloads are reduced to bounded metadata before persistence. Package
-- bytes remain in the module-owned artifact storage adapter.
CREATE TABLE IF NOT EXISTS learning_standard_packages (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(96) NOT NULL,
    title varchar(255) NOT NULL,
    standard varchar(32) NOT NULL,
    package_version varchar(96) NOT NULL,
    artifact_key varchar(255) NOT NULL,
    artifact_sha256 varchar(64) NOT NULL,
    byte_size bigint NOT NULL,
    manifest_sha256 varchar(64) NOT NULL,
    manifest_identifier varchar(255),
    launch_path varchar(255) NOT NULL,
    status varchar(32) NOT NULL,
    quarantine_reason varchar(255),
    created_by varchar(96) NOT NULL,
    updated_by varchar(96) NOT NULL,
    record_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_learning_standard_packages_standard CHECK (standard IN ('SCORM_12', 'SCORM_2004')),
    CONSTRAINT ck_learning_standard_packages_status CHECK (status IN ('UPLOADING', 'VALIDATING', 'READY', 'QUARANTINED')),
    CONSTRAINT ck_learning_standard_packages_size CHECK (byte_size > 0 AND byte_size <= 536870912)
);

CREATE TABLE IF NOT EXISTS learning_standard_lti_registrations (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(96) NOT NULL,
    issuer varchar(255) NOT NULL,
    client_id varchar(255) NOT NULL,
    deployment_id varchar(255) NOT NULL,
    public_key_pem text NOT NULL,
    allowed_target_host varchar(255) NOT NULL,
    status varchar(32) NOT NULL,
    created_by varchar(96) NOT NULL,
    updated_by varchar(96) NOT NULL,
    record_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_learning_standard_lti_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE IF NOT EXISTS learning_standard_xapi_statements (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(96) NOT NULL,
    statement_id varchar(96) NOT NULL,
    actor_hash varchar(64) NOT NULL,
    verb varchar(512) NOT NULL,
    object_id varchar(512) NOT NULL,
    payload_sha256 varchar(64) NOT NULL,
    status varchar(32) NOT NULL,
    occurred_at timestamptz NOT NULL,
    received_at timestamptz NOT NULL,
    accepted_by varchar(96) NOT NULL,
    record_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_learning_standard_xapi_status CHECK (status IN ('ACCEPTED', 'REJECTED'))
);

CREATE TABLE IF NOT EXISTS learning_standard_command_receipts (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(96) NOT NULL,
    operation varchar(64) NOT NULL,
    idempotency_key varchar(255) NOT NULL,
    payload_hash varchar(64) NOT NULL,
    aggregate_type varchar(64) NOT NULL,
    aggregate_id varchar(96) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_learning_standard_receipts_key UNIQUE (tenant_id, idempotency_key, operation)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_learning_standard_packages_artifact
    ON learning_standard_packages (tenant_id, artifact_key);
CREATE INDEX IF NOT EXISTS idx_learning_standard_packages_tenant_status
    ON learning_standard_packages (tenant_id, status, updated_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS ux_learning_standard_lti_registration_identity
    ON learning_standard_lti_registrations (tenant_id, issuer, client_id);
CREATE INDEX IF NOT EXISTS idx_learning_standard_lti_tenant_status
    ON learning_standard_lti_registrations (tenant_id, status, updated_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS ux_learning_standard_xapi_statement_identity
    ON learning_standard_xapi_statements (tenant_id, statement_id);
CREATE INDEX IF NOT EXISTS idx_learning_standard_xapi_tenant_received
    ON learning_standard_xapi_statements (tenant_id, received_at DESC);

DO $$
DECLARE
    table_name text;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'learning_standard_packages',
        'learning_standard_lti_registrations',
        'learning_standard_xapi_statements',
        'learning_standard_command_receipts'
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
VALUES ('pg_learning_standards', now(), now(), 'learning_standards', 'Learning Standards', 116)
ON CONFLICT (name) DO UPDATE SET
    label = EXCLUDED.label,
    display_order = EXCLUDED.display_order,
    updated_at = now();

INSERT INTO permissions (
    id, created_at, updated_at, name, group_name, label, description,
    resource, action, portal, supported_scope, is_system, status
)
VALUES
    ('perm_learning_standards_read', now(), now(), 'learning.standards.read', 'learning_standards',
     'Read learning standards', 'Read tenant-scoped SCORM, xAPI and LTI metadata',
     'learning_standards', 'read', 'lms', 'tenant,school,class,self', false, 'ACTIVE'),
    ('perm_learning_standards_manage', now(), now(), 'learning.standards.manage', 'learning_standards',
     'Manage learning standards', 'Register and validate packages and LTI integrations',
     'learning_standards', 'manage', 'lms', 'tenant,school,class', false, 'ACTIVE'),
    ('perm_learning_standards_launch', now(), now(), 'learning.standards.launch', 'learning_standards',
     'Launch learning standards', 'Launch validated SCORM packages and LTI resources',
     'learning_standards', 'launch', 'lms', 'tenant,school,class,self', false, 'ACTIVE')
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
    ('learning.standards.read'),
    ('learning.standards.manage'),
    ('learning.standards.launch')
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
