-- BUG-D-004: teachers read the hand-graded (essay) answers of an attempt before grading them.
INSERT INTO permission_bindings (
    id, permission_id, surface_type, surface_identifier, description, enforced, created_at, updated_at
)
SELECT
    'pb_' || substr(md5(permission.name || ':lms-manual-answers-rest'), 1, 32),
    permission.id,
    'REST',
    'GET /api/v1/lms/attempts/{attemptId}/manual-answers',
    'Tenant/assignment-scoped LMS runtime contract',
    true,
    now(),
    now()
FROM permissions permission
WHERE permission.name = 'lms.attempt.grade'
ON CONFLICT (permission_id, surface_type, surface_identifier) DO NOTHING;
