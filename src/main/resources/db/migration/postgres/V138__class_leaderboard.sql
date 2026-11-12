-- Class leaderboard ("Xếp hạng"): teachers switch it on or off for the students of each class (on by default).
ALTER TABLE academic_classes
    ADD COLUMN IF NOT EXISTS leaderboard_enabled boolean NOT NULL DEFAULT true;

INSERT INTO permissions (
    id, created_at, updated_at, name, group_name, label, description,
    resource, action, portal, supported_scope, is_system, status
)
VALUES
    ('perm_el_leaderboard_manage', now(), now(), 'elearning.leaderboard.manage', 'elearning',
     'Quản lý xếp hạng lớp', 'View the class leaderboard and switch it on or off for the students of classes in scope',
     'leaderboard', 'manage', 'lms', 'tenant,school,class', false, 'ACTIVE')
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
WHERE permission.name = 'elearning.leaderboard.manage'
ON CONFLICT (permission_id, surface_type, surface_identifier) DO UPDATE
SET enforced = true,
    description = EXCLUDED.description,
    updated_at = now();

-- Which classes may be opened is decided per request (tenant admin: every class; teacher: classes taught).
INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.name = 'elearning.leaderboard.manage'
WHERE role.id IN (
    'role_system_admin', 'role_super_admin', 'role_lms_admin',
    'role_lms_teacher', 'role_lms_teacher_standard', 'role_lms_homeroom_teacher',
    'role_lms_subject_teacher', 'role_lms_teacher_lead'
)
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions existing
      WHERE existing.role_id = role.id AND existing.permission_id = permission.id
  );
