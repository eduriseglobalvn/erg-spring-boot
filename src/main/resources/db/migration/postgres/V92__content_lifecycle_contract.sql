-- Shared draft/review/approve/publish/archive/rollback lifecycle for authoring resources.
DO $$
DECLARE
    table_name text;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'curriculum_subjects', 'curriculum_levels', 'curriculum_topics',
        'content_items', 'lms_questions', 'lms_quizzes'
    ] LOOP
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS lifecycle_state varchar(32) NOT NULL DEFAULT ''DRAFT''', table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS record_version bigint NOT NULL DEFAULT 0', table_name);
        EXECUTE format($sql$
            UPDATE %I
               SET lifecycle_state = CASE lower(coalesce(status, 'draft'))
                   WHEN 'published' THEN 'PUBLISHED'
                   WHEN 'active' THEN 'PUBLISHED'
                   WHEN 'approved' THEN 'APPROVED'
                   WHEN 'in_review' THEN 'IN_REVIEW'
                   WHEN 'review' THEN 'IN_REVIEW'
                   WHEN 'archived' THEN 'ARCHIVED'
                   WHEN 'deleted' THEN 'ARCHIVED'
                   WHEN 'removed' THEN 'ARCHIVED'
                   ELSE 'DRAFT'
               END
             WHERE lifecycle_state = 'DRAFT'
               AND lower(coalesce(status, 'draft')) <> 'draft'
        $sql$, table_name);
        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT IF EXISTS %I', table_name, 'ck_' || table_name || '_lifecycle_state');
        EXECUTE format(
            'ALTER TABLE %I ADD CONSTRAINT %I CHECK (lifecycle_state IN (''DRAFT'', ''IN_REVIEW'', ''APPROVED'', ''PUBLISHED'', ''ARCHIVED''))',
            table_name, 'ck_' || table_name || '_lifecycle_state');
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (tenant_id, lifecycle_state, updated_at DESC)',
            'idx_' || table_name || '_tenant_lifecycle', table_name);
    END LOOP;
END $$;

CREATE TABLE IF NOT EXISTS content_lifecycle_transitions (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(96) NOT NULL,
    resource_kind varchar(48) NOT NULL,
    resource_id varchar(96) NOT NULL,
    from_state varchar(32) NOT NULL,
    to_state varchar(32) NOT NULL,
    actor_id varchar(96) NOT NULL,
    reason text NOT NULL,
    expected_version bigint,
    resulting_version bigint,
    operation_id varchar(128),
    idempotency_key varchar(255) NOT NULL,
    source_version_id varchar(96),
    correlation_id varchar(128),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_content_lifecycle_kind CHECK (resource_kind IN (
        'CURRICULUM_SUBJECT', 'CURRICULUM_LEVEL', 'CURRICULUM_TOPIC',
        'CONTENT_ITEM', 'QUESTION', 'QUIZ')),
    CONSTRAINT ck_content_lifecycle_states CHECK (
        from_state IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'PUBLISHED', 'ARCHIVED')
        AND to_state IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'PUBLISHED', 'ARCHIVED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_content_lifecycle_idempotency
    ON content_lifecycle_transitions (tenant_id, resource_kind, resource_id, idempotency_key);
CREATE INDEX IF NOT EXISTS idx_content_lifecycle_resource
    ON content_lifecycle_transitions (tenant_id, resource_kind, resource_id, created_at DESC);

ALTER TABLE content_lifecycle_transitions ENABLE ROW LEVEL SECURITY;
ALTER TABLE content_lifecycle_transitions FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS content_lifecycle_tenant_isolation ON content_lifecycle_transitions;
CREATE POLICY content_lifecycle_tenant_isolation ON content_lifecycle_transitions
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

INSERT INTO permissions (
    id, created_at, updated_at, name, group_name, label, description,
    resource, action, portal, supported_scope, is_system, status
)
VALUES
    ('perm_content_lifecycle_submit', now(), now(), 'content.lifecycle.submit', 'content', 'Submit content for review', 'Move owned content into review', 'content-lifecycle', 'submit', 'admin', 'tenant,school', false, 'ACTIVE'),
    ('perm_content_lifecycle_approve', now(), now(), 'content.lifecycle.approve', 'content', 'Approve content', 'Approve content awaiting review', 'content-lifecycle', 'approve', 'admin', 'tenant,school', false, 'ACTIVE'),
    ('perm_content_lifecycle_publish', now(), now(), 'content.lifecycle.publish', 'content', 'Publish content', 'Publish an approved immutable snapshot', 'content-lifecycle', 'publish', 'admin', 'tenant,school', false, 'ACTIVE'),
    ('perm_content_lifecycle_archive', now(), now(), 'content.lifecycle.archive', 'content', 'Archive content', 'Archive a published resource', 'content-lifecycle', 'archive', 'admin', 'tenant,school', false, 'ACTIVE'),
    ('perm_content_lifecycle_rollback', now(), now(), 'content.lifecycle.rollback', 'content', 'Rollback content', 'Restore an archived resource or fork a quiz draft', 'content-lifecycle', 'rollback', 'admin', 'tenant,school', false, 'ACTIVE')
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
