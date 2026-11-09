-- The student portal route (/student) and StudentDashboardReadService both require
-- elearning.assignment.read_self, but no migration ever defined or bound it, so a learner account
-- (role_lms_student) could log in yet always got 403 "Access restricted" on the student workspace.
INSERT INTO permissions (
    id, created_at, updated_at, name, group_name, label, description,
    resource, action, portal, supported_scope, is_system, status
)
VALUES
    ('perm_elearning_assignment_read_self', now(), now(), 'elearning.assignment.read_self', 'elearning',
     'Read own assignments (student portal)', 'Open the student portal and read own assignments',
     'assignment', 'read', 'elearning', 'tenant,school', false, 'ACTIVE')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.name = 'elearning.assignment.read_self'
WHERE role.id = 'role_lms_student'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions existing
      WHERE existing.role_id = role.id AND existing.permission_id = permission.id
  );
