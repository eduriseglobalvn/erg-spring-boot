-- Additive permission catalog extension for authenticated student engagement flows.
INSERT INTO permissions (
    id, created_at, updated_at, name, group_name, label, description,
    resource, action, portal, supported_scope, is_system, status
)
VALUES
    ('perm_el_dashboard_read', now(), now(), 'elearning.dashboard.read', 'elearning',
     'Xem bảng tin học viên', 'Read the authenticated student dashboard and discussion feed',
     'dashboard', 'read', 'elearning', 'tenant,school,class,self', false, 'ACTIVE'),
    ('perm_el_discuss_self', now(), now(), 'elearning.discussion.write_self', 'elearning',
     'Thảo luận với tư cách học viên', 'Create, reply to and react to discussions as the authenticated student',
     'discussion', 'write_self', 'elearning', 'tenant,school,class,self', false, 'ACTIVE'),
    ('perm_el_announce_self', now(), now(), 'elearning.announcement.read_self', 'elearning',
     'Đọc thông báo học viên', 'Read and acknowledge announcements visible to the authenticated student',
     'announcement', 'read_self', 'elearning', 'tenant,school,class,self', false, 'ACTIVE')
ON CONFLICT (name) DO UPDATE
SET group_name = EXCLUDED.group_name,
    label = EXCLUDED.label,
    description = EXCLUDED.description,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    portal = EXCLUDED.portal,
    supported_scope = EXCLUDED.supported_scope,
    is_system = EXCLUDED.is_system,
    status = EXCLUDED.status,
    updated_at = now();

WITH runtime_permissions(name) AS (VALUES
    ('elearning.dashboard.read'),
    ('elearning.discussion.write_self'),
    ('elearning.announcement.read_self')
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
SET enforced = true,
    description = EXCLUDED.description,
    updated_at = now();

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.name IN (
    'elearning.dashboard.read',
    'elearning.discussion.write_self',
    'elearning.announcement.read_self'
)
WHERE role.id = 'role_lms_student'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions existing
      WHERE existing.role_id = role.id AND existing.permission_id = permission.id
  );
