-- BE-058: tenant-scoped learning paths, competencies and outcome certificates.
-- Profile credentials in users.certificates are intentionally left untouched.
CREATE TABLE IF NOT EXISTS learning_paths (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(96) NOT NULL,
    title varchar(255) NOT NULL,
    description text,
    status varchar(32) NOT NULL,
    education_unit_id varchar(96),
    class_id varchar(96),
    created_by varchar(96) NOT NULL,
    updated_by varchar(96) NOT NULL,
    record_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_learning_paths_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE TABLE IF NOT EXISTS learning_path_nodes (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(96) NOT NULL,
    path_id varchar(96) NOT NULL,
    content_ref_id varchar(96) NOT NULL,
    content_kind varchar(48) NOT NULL,
    sort_order integer NOT NULL,
    required boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_learning_path_nodes_sort_order CHECK (sort_order >= 0)
);

CREATE TABLE IF NOT EXISTS competencies (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(96) NOT NULL,
    code varchar(96) NOT NULL,
    name varchar(255) NOT NULL,
    description text,
    status varchar(32) NOT NULL,
    education_unit_id varchar(96),
    created_by varchar(96) NOT NULL,
    updated_by varchar(96) NOT NULL,
    record_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_competencies_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE TABLE IF NOT EXISTS competency_attainments (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(96) NOT NULL,
    learner_id varchar(96) NOT NULL,
    competency_id varchar(96) NOT NULL,
    learning_path_id varchar(96),
    class_id varchar(96),
    status varchar(32) NOT NULL,
    evidence_ref varchar(255) NOT NULL,
    achieved_at timestamptz NOT NULL,
    recorded_by varchar(96) NOT NULL,
    record_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_competency_attainments_status CHECK (status IN ('ATTAINED', 'REVOKED'))
);

CREATE TABLE IF NOT EXISTS outcome_certificates (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(96) NOT NULL,
    learner_id varchar(96) NOT NULL,
    learning_path_id varchar(96) NOT NULL,
    certificate_number varchar(128) NOT NULL,
    status varchar(32) NOT NULL,
    evidence_ref varchar(255) NOT NULL,
    issued_at timestamptz NOT NULL,
    expires_at timestamptz,
    revoked_at timestamptz,
    issued_by varchar(96) NOT NULL,
    record_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_outcome_certificates_status CHECK (status IN ('ISSUED', 'REVOKED', 'EXPIRED'))
);

CREATE TABLE IF NOT EXISTS learning_outcome_command_receipts (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(96) NOT NULL,
    operation varchar(64) NOT NULL,
    idempotency_key varchar(255) NOT NULL,
    payload_hash varchar(64) NOT NULL,
    aggregate_type varchar(64) NOT NULL,
    aggregate_id varchar(96) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_learning_outcome_receipts_key UNIQUE (tenant_id, idempotency_key, operation)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_learning_path_nodes_content
    ON learning_path_nodes (tenant_id, path_id, content_ref_id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_competencies_tenant_code
    ON competencies (tenant_id, code);
CREATE UNIQUE INDEX IF NOT EXISTS ux_competency_attainments_identity
    ON competency_attainments (tenant_id, learner_id, competency_id, coalesce(learning_path_id, ''));
CREATE UNIQUE INDEX IF NOT EXISTS ux_outcome_certificates_number
    ON outcome_certificates (tenant_id, certificate_number);
CREATE UNIQUE INDEX IF NOT EXISTS ux_outcome_certificates_identity
    ON outcome_certificates (tenant_id, learner_id, learning_path_id);

CREATE INDEX IF NOT EXISTS idx_learning_paths_tenant_status
    ON learning_paths (tenant_id, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_learning_paths_tenant_scope
    ON learning_paths (tenant_id, education_unit_id, class_id);
CREATE INDEX IF NOT EXISTS idx_learning_path_nodes_tenant_path
    ON learning_path_nodes (tenant_id, path_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_competencies_tenant_status
    ON competencies (tenant_id, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_competencies_tenant_scope
    ON competencies (tenant_id, education_unit_id);
CREATE INDEX IF NOT EXISTS idx_competency_attainments_tenant_learner
    ON competency_attainments (tenant_id, learner_id, achieved_at DESC);
CREATE INDEX IF NOT EXISTS idx_competency_attainments_tenant_competency
    ON competency_attainments (tenant_id, competency_id, status);
CREATE INDEX IF NOT EXISTS idx_outcome_certificates_tenant_learner
    ON outcome_certificates (tenant_id, learner_id, issued_at DESC);
CREATE INDEX IF NOT EXISTS idx_outcome_certificates_tenant_path
    ON outcome_certificates (tenant_id, learning_path_id, status);

DO $$
DECLARE
    table_name text;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'learning_paths', 'learning_path_nodes', 'competencies',
        'competency_attainments', 'outcome_certificates',
        'learning_outcome_command_receipts'
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
VALUES ('pg_learning_outcomes', now(), now(), 'learning_outcomes', 'Learning Outcomes', 115)
ON CONFLICT (name) DO UPDATE SET label = EXCLUDED.label, display_order = EXCLUDED.display_order, updated_at = now();

INSERT INTO permissions (
    id, created_at, updated_at, name, group_name, label, description,
    resource, action, portal, supported_scope, is_system, status
)
VALUES
    ('perm_lo_read', now(), now(), 'learning.outcomes.read', 'learning_outcomes',
     'Read learning outcomes', 'Read tenant-scoped learning paths, competencies and outcome records',
     'learning_outcomes', 'read', 'lms', 'tenant,school,class,self', false, 'ACTIVE'),
    ('perm_lo_manage', now(), now(), 'learning.outcomes.manage', 'learning_outcomes',
     'Manage learning outcomes', 'Create paths, competencies and attainment records',
     'learning_outcomes', 'manage', 'lms', 'tenant,school,class,self', false, 'ACTIVE'),
    ('perm_lo_issue_cert', now(), now(), 'learning.outcomes.issue_certificate', 'learning_outcomes',
     'Issue outcome certificates', 'Issue a certificate only for a published learning path',
     'learning_outcomes', 'issue_certificate', 'lms', 'tenant,school,class,self', false, 'ACTIVE')
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
    ('learning.outcomes.read'),
    ('learning.outcomes.manage'),
    ('learning.outcomes.issue_certificate')
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
