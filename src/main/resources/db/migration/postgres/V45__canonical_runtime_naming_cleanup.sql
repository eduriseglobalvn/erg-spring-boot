-- Normalize runtime rows that may have been created by older bootstrap services.
-- Historical migrations can still mention legacy source names so data can be moved safely;
-- runtime data after this migration must use canonical curriculum/content naming.

UPDATE education_units
SET name = 'Content Studio',
    slug = 'content-studio',
    logo_url = '/assets/units/content-studio.svg',
    description = 'Curriculum and learning content operations unit',
    updated_at = now()
WHERE id = 'ctr_content'
   OR lower(slug) = 'hoc-lieu-studio'
   OR lower(name) = 'hoc lieu studio';

UPDATE education_units
SET name = 'Content Operations',
    slug = 'content-operations',
    logo_url = '/assets/units/content-operations.svg',
    description = 'Curriculum and learning content operations center',
    updated_at = now()
WHERE id = 'ctr_content_ops';

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

DELETE FROM role_permissions
WHERE permission_id = 'perm_hoclieu_manage';

DELETE FROM permissions
WHERE id = 'perm_hoclieu_manage'
   OR name = 'hoclieu.*'
   OR group_name = 'hoclieu';

DELETE FROM permission_groups
WHERE id = 'pg_hoclieu'
   OR name = 'hoclieu';

DELETE FROM education_units
WHERE id = 'ctr_hoclieu'
   OR slug = 'hoclieu-operations'
   OR lower(name) = 'hoclieu operations';
