CREATE TABLE IF NOT EXISTS lms_announcements (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    target_type varchar(24) NOT NULL DEFAULT 'ALL',
    academic_class_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
    student_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
    title varchar(500) NOT NULL,
    content text,
    pinned boolean NOT NULL DEFAULT false,
    author_id varchar(36) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'DRAFT',
    published_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);

INSERT INTO permissions (
    id, created_at, updated_at, name, group_name, label, description,
    resource, action, portal, supported_scope, is_system, status
)
VALUES
    ('perm_elearning_dashboard_read', now(), now(), 'elearning.dashboard.read', 'elearning', 'Xem bảng học viên', 'Read the authenticated student dashboard', 'dashboard', 'read', 'elearning', 'self', false, 'ACTIVE'),
    ('perm_elearning_discussion_self', now(), now(), 'elearning.discussion.write_self', 'elearning', 'Thảo luận học tập', 'Create and interact with discussions in the enrolled class', 'discussion', 'write_self', 'elearning', 'self,class', false, 'ACTIVE'),
    ('perm_elearning_announcement_self', now(), now(), 'elearning.announcement.read_self', 'elearning', 'Đọc thông báo', 'Read and acknowledge announcements in the student scope', 'announcement', 'read_self', 'elearning', 'self,class', false, 'ACTIVE')
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

CREATE TABLE IF NOT EXISTS student_discussion_threads (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    academic_class_id varchar(36) NOT NULL,
    title varchar(250) NOT NULL,
    content text NOT NULL,
    assignment_id varchar(36),
    attachment_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
    author_id varchar(36) NOT NULL,
    idempotency_key varchar(192) NOT NULL,
    latest_activity_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);

CREATE TABLE IF NOT EXISTS student_discussion_comments (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    post_id varchar(36) NOT NULL REFERENCES student_discussion_threads(id) ON DELETE CASCADE,
    parent_id varchar(36) REFERENCES student_discussion_comments(id) ON DELETE CASCADE,
    author_id varchar(36) NOT NULL,
    content text NOT NULL,
    attachment_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
    idempotency_key varchar(192) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);

CREATE TABLE IF NOT EXISTS student_discussion_reactions (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    target_type varchar(16) NOT NULL,
    target_id varchar(36) NOT NULL,
    user_id varchar(36) NOT NULL,
    reaction varchar(16) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_student_discussion_reaction_target CHECK (target_type IN ('POST', 'COMMENT')),
    CONSTRAINT chk_student_discussion_reaction_value CHECK (reaction IN ('like', 'love', 'care', 'haha', 'wow', 'sad', 'angry'))
);

CREATE TABLE IF NOT EXISTS student_announcement_reads (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    announcement_id varchar(36) NOT NULL REFERENCES lms_announcements(id) ON DELETE CASCADE,
    student_id varchar(36) NOT NULL,
    user_id varchar(36) NOT NULL,
    read_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_lms_announcements_tenant_publish
    ON lms_announcements(tenant_id, status, pinned, published_at DESC);
CREATE INDEX IF NOT EXISTS idx_lms_announcements_classes_gin ON lms_announcements USING gin(academic_class_ids);
CREATE INDEX IF NOT EXISTS idx_lms_announcements_students_gin ON lms_announcements USING gin(student_ids);
CREATE INDEX IF NOT EXISTS idx_student_discussion_feed
    ON student_discussion_threads(tenant_id, academic_class_id, latest_activity_at DESC);
CREATE INDEX IF NOT EXISTS idx_student_discussion_comments_post
    ON student_discussion_comments(tenant_id, post_id, created_at);
CREATE INDEX IF NOT EXISTS idx_student_discussion_reactions_target
    ON student_discussion_reactions(tenant_id, target_type, target_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_student_discussion_thread_idempotency
    ON student_discussion_threads(tenant_id, author_id, idempotency_key);
CREATE UNIQUE INDEX IF NOT EXISTS uq_student_discussion_comment_idempotency
    ON student_discussion_comments(tenant_id, author_id, idempotency_key);
CREATE UNIQUE INDEX IF NOT EXISTS uq_student_discussion_reaction_actor
    ON student_discussion_reactions(tenant_id, target_type, target_id, user_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_student_announcement_read_actor
    ON student_announcement_reads(tenant_id, announcement_id, user_id);

ALTER TABLE lms_announcements ENABLE ROW LEVEL SECURITY;
ALTER TABLE student_discussion_threads ENABLE ROW LEVEL SECURITY;
ALTER TABLE student_discussion_comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE student_discussion_reactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE student_announcement_reads ENABLE ROW LEVEL SECURITY;

CREATE POLICY lms_announcements_tenant_isolation ON lms_announcements
    USING (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''));
CREATE POLICY student_discussion_threads_tenant_isolation ON student_discussion_threads
    USING (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''));
CREATE POLICY student_discussion_comments_tenant_isolation ON student_discussion_comments
    USING (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''));
CREATE POLICY student_discussion_reactions_tenant_isolation ON student_discussion_reactions
    USING (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''));
CREATE POLICY student_announcement_reads_tenant_isolation ON student_announcement_reads
    USING (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''));
