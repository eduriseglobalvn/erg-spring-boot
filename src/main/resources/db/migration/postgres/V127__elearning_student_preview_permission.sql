-- Plan 4.1: staff (teachers, academic operations, system admin) may open the student portal in
-- read-only preview mode. The permission only projects the STUDENT workspace for the session; it
-- grants no learner capability (no lms.quiz.play, no attempt creation) and /api/v1/elearning/**
-- still requires elearning.manage.
INSERT INTO permissions (
    id, created_at, updated_at, name, group_name, label, description,
    resource, action, portal, supported_scope, is_system, status
)
VALUES
    ('perm_elearning_student_preview', now(), now(), 'elearning.student.preview', 'elearning',
     'Preview student portal', 'Open the student portal in read-only staff preview (no learner identity, no graded attempts)',
     'student', 'preview', 'elearning', 'tenant,school', false, 'ACTIVE')
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
JOIN permissions permission ON permission.name = 'elearning.student.preview'
WHERE role.id IN (
    'role_lms_teacher_standard', 'role_lms_homeroom_teacher', 'role_lms_subject_teacher',
    'role_lms_teacher_lead', 'role_pdt_operator', 'role_system_admin'
)
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions existing
      WHERE existing.role_id = role.id AND existing.permission_id = permission.id
  );
