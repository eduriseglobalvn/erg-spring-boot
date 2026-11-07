-- P5 timekeeping permissions. self = punch, own timesheet and requests; approve = act on approval steps;
-- manage = configuration, today/timesheet views, period lock. Roles are listed by id, nothing is matched by pattern.
INSERT INTO permission_groups (id, created_at, updated_at, name, label, display_order)
VALUES ('pg_timekeeping', now(), now(), 'timekeeping', 'Timekeeping', 118)
ON CONFLICT (name) DO UPDATE SET
    label = EXCLUDED.label,
    display_order = EXCLUDED.display_order,
    updated_at = now();

-- SCHOOL_ROLE:VICE_PRINCIPAL approval steps resolve to holders of this role.
INSERT INTO roles (id, created_at, updated_at, name, description, is_default)
SELECT 'role_school_vice_principal', now(), now(), 'school.vice_principal', 'Vice principal: approves staff leave within the school', false
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE id = 'role_school_vice_principal' OR lower(name) = 'school.vice_principal');

INSERT INTO permissions (
    id, created_at, updated_at, name, group_name, label, description,
    resource, action, portal, supported_scope, is_system, status
)
VALUES
    ('perm_timekeeping_self', now(), now(), 'timekeeping.self', 'timekeeping',
     'Own timekeeping', 'Punch in/out, view own timesheet and submit leave, explanation, WFH and business-trip requests',
     'timekeeping', 'self', 'lms', 'tenant,school', false, 'ACTIVE'),
    ('perm_timekeeping_approve', now(), now(), 'timekeeping.approve', 'timekeeping',
     'Approve timekeeping requests', 'Approve or reject timekeeping requests assigned to the approver',
     'timekeeping', 'approve', 'admin', 'tenant,school', false, 'ACTIVE'),
    ('perm_timekeeping_manage', now(), now(), 'timekeeping.manage', 'timekeeping',
     'Manage timekeeping', 'Configure sites, shifts and policies, review punches, view timesheets and lock periods',
     'timekeeping', 'manage', 'admin', 'tenant,school', false, 'ACTIVE')
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

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.name = 'timekeeping.self'
WHERE role.id IN (
    -- teachers
    'role_lms_teacher_standard', 'role_lms_homeroom_teacher', 'role_lms_subject_teacher',
    'role_lms_teacher_lead', 'role_lms_teacher',
    -- school staff
    'role_lms_teacher_assistant', 'role_pdt_operator', 'role_it_operator', 'role_school_manager',
    'role_center_manager', 'role_school_vice_principal'
)
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions existing
      WHERE existing.role_id = role.id AND existing.permission_id = permission.id
  );

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.name = 'timekeeping.approve'
WHERE role.id IN (
    'role_lms_teacher_lead', 'role_school_vice_principal',
    'role_school_manager', 'role_pdt_operator', 'role_system_admin', 'role_super_admin', 'role_lms_admin'
)
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions existing
      WHERE existing.role_id = role.id AND existing.permission_id = permission.id
  );

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.name = 'timekeeping.manage'
WHERE role.id IN (
    'role_school_manager', 'role_pdt_operator', 'role_system_admin', 'role_super_admin', 'role_lms_admin'
)
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions existing
      WHERE existing.role_id = role.id AND existing.permission_id = permission.id
  );
