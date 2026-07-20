-- Final canonical-content cutover cleanup.
-- Runtime now reads through GraphQL and canonical curriculum/content tables only.

INSERT INTO permission_groups (id, created_at, updated_at, name, label, display_order)
SELECT 'pg_content', now(), now(), 'content', 'Content and Curriculum', 50
WHERE NOT EXISTS (
    SELECT 1
    FROM permission_groups
    WHERE id = 'pg_content'
       OR name = 'content'
);

INSERT INTO permissions (id, created_at, updated_at, name, group_name, label, description)
SELECT 'perm_content_read', now(), now(), 'content.resource.read', 'content', 'Read content resources', 'Read curriculum-linked learning resources'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'content.resource.read');

INSERT INTO permissions (id, created_at, updated_at, name, group_name, label, description)
SELECT 'perm_content_manage', now(), now(), 'content.manage', 'content', 'Manage content', 'Manage curriculum-linked content resources and assets'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'content.manage');

INSERT INTO permissions (id, created_at, updated_at, name, group_name, label, description)
SELECT 'perm_curr_manage', now(), now(), 'curriculum.manage', 'content', 'Manage curriculum', 'Manage subjects, levels, and topics'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'curriculum.manage');

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT role_permission.role_id, canonical_permission.id
FROM role_permissions role_permission
JOIN permissions canonical_permission
  ON canonical_permission.name = 'content.manage'
WHERE role_permission.permission_id = 'perm_hoclieu_manage'
ON CONFLICT DO NOTHING;

UPDATE user_permissions
SET permission = 'content.manage'
WHERE permission = 'hoclieu.*';

DELETE FROM role_permissions
WHERE permission_id = 'perm_hoclieu_manage';

DELETE FROM permissions
WHERE id = 'perm_hoclieu_manage'
   OR name = 'hoclieu.*'
   OR group_name = 'hoclieu';

DELETE FROM permission_groups
WHERE id = 'pg_hoclieu'
   OR name = 'hoclieu';

INSERT INTO education_units (
    id,
    created_at,
    updated_at,
    name,
    slug,
    type,
    parent_id,
    logo_url,
    description,
    status
)
SELECT
    'ctr_content_ops',
    now(),
    now(),
    'Content Operations',
    'content-operations',
    'center',
    'ctr_system',
    '/assets/units/content-operations.svg',
    'Curriculum and learning content operations center',
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
    FROM education_units
    WHERE id = 'ctr_content_ops'
       OR slug = 'content-operations'
);

UPDATE education_units
SET parent_id = 'ctr_content_ops',
    updated_at = now()
WHERE parent_id = 'ctr_hoclieu'
  AND EXISTS (SELECT 1 FROM education_units WHERE id = 'ctr_content_ops');

UPDATE user_access_scopes
SET education_unit_id = 'ctr_content_ops',
    updated_at = now()
WHERE education_unit_id = 'ctr_hoclieu'
  AND EXISTS (SELECT 1 FROM education_units WHERE id = 'ctr_content_ops');

DELETE FROM education_units
WHERE id = 'ctr_hoclieu'
   OR slug = 'hoclieu-operations';

WITH normalized_scopes AS (
    SELECT
        scope.id,
        array_to_string(ARRAY(
            SELECT DISTINCT CASE WHEN module = 'hoclieu' THEN 'content' ELSE module END
            FROM unnest(string_to_array(replace(coalesce(scope.modules, ''), ' ', ''), ',')) AS module
            WHERE module <> ''
        ), ',') AS modules
    FROM user_access_scopes scope
    WHERE scope.modules ILIKE '%hoclieu%'
)
UPDATE user_access_scopes scope
SET modules = normalized.modules,
    updated_at = now()
FROM normalized_scopes normalized
WHERE scope.id = normalized.id;

DROP TABLE IF EXISTS legacy_id_mappings CASCADE;
