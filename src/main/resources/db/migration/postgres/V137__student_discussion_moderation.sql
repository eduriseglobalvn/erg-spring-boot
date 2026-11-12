-- Student discussions ("Thảo luận"): teacher/admin moderation and two-level comments.

-- Hide keeps the row (moderators can show it again); students never see hidden rows or their replies.
ALTER TABLE student_discussion_threads
    ADD COLUMN IF NOT EXISTS hidden_at timestamp with time zone,
    ADD COLUMN IF NOT EXISTS hidden_by varchar(36);
ALTER TABLE student_discussion_comments
    ADD COLUMN IF NOT EXISTS hidden_at timestamp with time zone,
    ADD COLUMN IF NOT EXISTS hidden_by varchar(36);

-- Comments have two levels (a comment and its replies). Rows deeper than that were never returned by
-- the feed; attach each one to its root comment so they show up as replies again.
WITH RECURSIVE tree(id, root_id) AS (
    SELECT id, id FROM student_discussion_comments WHERE parent_id IS NULL
    UNION ALL
    SELECT child.id, tree.root_id
    FROM student_discussion_comments child
    JOIN tree ON child.parent_id = tree.id
)
UPDATE student_discussion_comments comment
SET parent_id = tree.root_id,
    updated_at = now()
FROM tree
WHERE comment.id = tree.id
  AND comment.parent_id IS NOT NULL
  AND comment.parent_id <> tree.root_id;

INSERT INTO permissions (
    id, created_at, updated_at, name, group_name, label, description,
    resource, action, portal, supported_scope, is_system, status
)
VALUES
    ('perm_el_discuss_moderate', now(), now(), 'elearning.discussion.moderate', 'elearning',
     'Kiểm duyệt thảo luận học viên', 'View, hide, show again and delete student discussion posts and comments of classes in scope',
     'discussion', 'moderate', 'lms', 'tenant,school,class', false, 'ACTIVE')
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
WHERE permission.name = 'elearning.discussion.moderate'
ON CONFLICT (permission_id, surface_type, surface_identifier) DO UPDATE
SET enforced = true,
    description = EXCLUDED.description,
    updated_at = now();

-- Which classes a moderator may open is decided per request (tenant admin: every class; teacher: classes taught).
INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.name = 'elearning.discussion.moderate'
WHERE role.id IN (
    'role_system_admin', 'role_super_admin', 'role_lms_admin',
    'role_lms_teacher', 'role_lms_teacher_standard', 'role_lms_homeroom_teacher',
    'role_lms_subject_teacher', 'role_lms_teacher_lead'
)
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions existing
      WHERE existing.role_id = role.id AND existing.permission_id = permission.id
  );
