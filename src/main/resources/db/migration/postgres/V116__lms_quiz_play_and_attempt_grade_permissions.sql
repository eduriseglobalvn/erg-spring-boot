-- (a)/N3/SQ-06: learners load assigned quiz packages with a dedicated permission instead of the LCMS
-- authoring permission lms.quiz.read (which also unlocks drafts and the question bank answers).
-- B3: teachers grade answers that need manual review (essay) with an explicit attempt grading permission.
INSERT INTO permissions (
    id, created_at, updated_at, name, group_name, label, description,
    resource, action, portal, supported_scope, is_system, status
)
VALUES
    ('perm_lms_quiz_play', now(), now(), 'lms.quiz.play', 'lms',
     'Play assigned quizzes', 'Load the runtime package of quizzes assigned to the authenticated learner (no answer key)',
     'quiz', 'play', 'lms', 'tenant,school,class,self', false, 'ACTIVE'),
    ('perm_lms_attempt_grade', now(), now(), 'lms.attempt.grade', 'lms',
     'Grade attempts', 'Grade answers awaiting manual review for attempts in managed classes',
     'attempt', 'grade', 'lms', 'tenant,school,class', false, 'ACTIVE')
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

WITH runtime_permissions(name, surface) AS (VALUES
    ('lms.quiz.play', 'GET /api/v1/lms/quizzes/{quizId}/package'),
    ('lms.attempt.grade', 'POST /api/v1/lms/attempts/{attemptId}/answers/{questionId}/grade')
)
INSERT INTO permission_bindings (
    id, permission_id, surface_type, surface_identifier, description, enforced, created_at, updated_at
)
SELECT
    'pb_' || substr(md5(permission.name || ':lms-runtime-rest'), 1, 32),
    permission.id,
    'REST',
    runtime.surface,
    'Tenant/assignment-scoped LMS runtime contract',
    true,
    now(),
    now()
FROM permissions permission
JOIN runtime_permissions runtime ON runtime.name = permission.name
ON CONFLICT (permission_id, surface_type, surface_identifier) DO UPDATE
SET enforced = true, description = EXCLUDED.description, updated_at = now();

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.name = 'lms.quiz.play'
WHERE role.id = 'role_lms_student'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions existing
      WHERE existing.role_id = role.id AND existing.permission_id = permission.id
  );

-- Teachers who can assign homework can grade it.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT assigner.role_id, permission.id
FROM role_permissions assigner
JOIN permissions assign_permission ON assign_permission.id = assigner.permission_id
                                  AND assign_permission.name = 'lms.assignment.create'
JOIN permissions permission ON permission.name = 'lms.attempt.grade'
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions existing
    WHERE existing.role_id = assigner.role_id AND existing.permission_id = permission.id
);
