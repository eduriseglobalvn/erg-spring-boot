-- Authoritative permission metadata and usage bindings for the admin catalog.
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS resource varchar(255);
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS action varchar(128);
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS portal varchar(128);
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS supported_scope varchar(128);
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS is_system boolean NOT NULL DEFAULT false;
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS status varchar(32) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS deprecated_at timestamp with time zone;

UPDATE permissions
SET portal = COALESCE(portal, split_part(name, '.', 1)),
    action = COALESCE(action, regexp_replace(name, '^.*\.', '')),
    resource = COALESCE(resource, regexp_replace(name, '^[^.]+\.|\.[^.]+$', ''))
WHERE name IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_permissions_catalog_lookup
    ON permissions (portal, group_name, status, name);

CREATE TABLE IF NOT EXISTS permission_bindings (
    id varchar(36) PRIMARY KEY,
    permission_id varchar(36) NOT NULL,
    surface_type varchar(32) NOT NULL,
    surface_identifier varchar(512) NOT NULL,
    description varchar(1000),
    enforced boolean NOT NULL DEFAULT true,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT fk_permission_bindings_permission
        FOREIGN KEY (permission_id) REFERENCES permissions(id),
    CONSTRAINT ck_permission_bindings_surface_type
        CHECK (surface_type IN ('REST', 'GRAPHQL', 'ROUTE', 'SERVICE'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_permission_bindings_surface
    ON permission_bindings (permission_id, surface_type, surface_identifier);

CREATE INDEX IF NOT EXISTS idx_permission_bindings_permission
    ON permission_bindings (permission_id, enforced);

-- Categories are classification metadata. Assignable permission groups remain roles.
INSERT INTO permission_groups (id, created_at, updated_at, name, label, display_order)
VALUES
    ('pg_ai_content', now(), now(), 'ai-content', 'AI Content', 70),
    ('pg_api_keys', now(), now(), 'api-keys', 'API Keys', 80),
    ('pg_cms', now(), now(), 'cms', 'CMS', 50),
    ('pg_courses', now(), now(), 'courses', 'Courses', 45),
    ('pg_crawler', now(), now(), 'crawler', 'Crawler', 90),
    ('pg_documents', now(), now(), 'documents', 'Documents', 55),
    ('pg_elearning', now(), now(), 'elearning', 'Elearning', 40),
    ('pg_media', now(), now(), 'media', 'Media', 60),
    ('pg_notifications', now(), now(), 'notifications', 'Notifications', 65),
    ('pg_operations', now(), now(), 'operations', 'Operations', 95),
    ('pg_platform', now(), now(), 'platform', 'Platform', 100),
    ('pg_public_disclosure', now(), now(), 'public-disclosure', 'Public Disclosure', 75),
    ('pg_seo', now(), now(), 'seo', 'SEO', 85)
ON CONFLICT (name) DO UPDATE
SET label = EXCLUDED.label,
    display_order = EXCLUDED.display_order,
    updated_at = now();

INSERT INTO permissions (
    id, created_at, updated_at, name, group_name, label, description,
    resource, action, portal, supported_scope, is_system, status
)
VALUES
    ('perm_rbac_role_read', now(), now(), 'rbac.role.read', 'rbac', 'Xem nhóm quyền', 'Read role definitions', 'role', 'read', 'rbac', 'system', true, 'ACTIVE'),
    ('perm_rbac_role_create', now(), now(), 'rbac.role.create', 'rbac', 'Tạo nhóm quyền', 'Create role definitions', 'role', 'create', 'rbac', 'system', true, 'ACTIVE'),
    ('perm_rbac_role_update', now(), now(), 'rbac.role.update', 'rbac', 'Sửa nhóm quyền', 'Update role definitions', 'role', 'update', 'rbac', 'system', true, 'ACTIVE'),
    ('perm_rbac_role_delete', now(), now(), 'rbac.role.delete', 'rbac', 'Xóa nhóm quyền', 'Delete role definitions', 'role', 'delete', 'rbac', 'system', true, 'ACTIVE'),
    ('perm_rbac_role_assign', now(), now(), 'rbac.role.assign', 'rbac', 'Gán nhóm quyền', 'Assign roles to users', 'role', 'assign', 'rbac', 'tenant,school', true, 'ACTIVE'),
    ('perm_rbac_permission_read', now(), now(), 'rbac.permission.read', 'rbac', 'Xem quyền hệ thống', 'Read the permission catalog', 'permission', 'read', 'rbac', 'system', true, 'ACTIVE'),
    ('perm_lms_quiz_read', now(), now(), 'lms.quiz.read', 'lms', 'Xem bài kiểm tra', 'Read quizzes', 'quiz', 'read', 'lms', 'school,class', false, 'ACTIVE'),
    ('perm_lms_quiz_create', now(), now(), 'lms.quiz.create', 'lms', 'Tạo bài kiểm tra', 'Create quizzes', 'quiz', 'create', 'lms', 'school,class', false, 'ACTIVE'),
    ('perm_lms_quiz_publish', now(), now(), 'lms.quiz.publish', 'lms', 'Xuất bản bài kiểm tra', 'Publish quizzes', 'quiz', 'publish', 'lms', 'school,class', false, 'ACTIVE'),
    ('perm_api_keys_manage', now(), now(), 'api-keys.manage', 'api-keys', 'Quản lý API key', 'Manage API keys', 'api-key', 'manage', 'admin', 'system', true, 'ACTIVE'),
    ('perm_ai_content_use', now(), now(), 'ai-content.use', 'ai-content', 'Sử dụng AI nội dung', 'Use AI content services', 'content', 'use', 'lcms', 'tenant,school', false, 'ACTIVE'),
    ('perm_operations_manage', now(), now(), 'operations.manage', 'operations', 'Quản lý vận hành', 'Manage platform operations', 'operations', 'manage', 'admin', 'system', true, 'ACTIVE'),
    ('perm_platform_read', now(), now(), 'platform.read', 'platform', 'Xem nền tảng', 'Read platform configuration', 'platform', 'read', 'admin', 'system', true, 'ACTIVE'),
    ('perm_crawler_manage', now(), now(), 'crawler.manage', 'crawler', 'Quản lý crawler', 'Manage crawler jobs', 'crawler', 'manage', 'admin', 'system', true, 'ACTIVE'),
    ('perm_notifications_manage', now(), now(), 'notifications.manage', 'notifications', 'Quản lý thông báo', 'Manage administrative notifications', 'notification', 'manage', 'admin', 'tenant', true, 'ACTIVE'),
    ('perm_elearning_manage', now(), now(), 'elearning.manage', 'elearning', 'Quản lý Elearning', 'Manage elearning operations', 'elearning', 'manage', 'elearning', 'tenant,school', false, 'ACTIVE'),
    ('perm_seo_manage', now(), now(), 'seo.manage', 'seo', 'Quản lý SEO', 'Manage SEO operations', 'seo', 'manage', 'crm', 'tenant', false, 'ACTIVE'),
    ('perm_cms_posts_create', now(), now(), 'cms.posts.create', 'cms', 'Tạo bài viết', 'Create CMS posts', 'posts', 'create', 'lcms', 'tenant', false, 'ACTIVE'),
    ('perm_cms_posts_update', now(), now(), 'cms.posts.update', 'cms', 'Sửa bài viết', 'Update CMS posts', 'posts', 'update', 'lcms', 'tenant', false, 'ACTIVE'),
    ('perm_cms_posts_delete', now(), now(), 'cms.posts.delete', 'cms', 'Xóa bài viết', 'Delete CMS posts', 'posts', 'delete', 'lcms', 'tenant', false, 'ACTIVE'),
    ('perm_cms_categories_create', now(), now(), 'cms.categories.create', 'cms', 'Tạo danh mục', 'Create CMS categories', 'categories', 'create', 'lcms', 'tenant', false, 'ACTIVE'),
    ('perm_cms_categories_update', now(), now(), 'cms.categories.update', 'cms', 'Sửa danh mục', 'Update CMS categories', 'categories', 'update', 'lcms', 'tenant', false, 'ACTIVE'),
    ('perm_cms_categories_delete', now(), now(), 'cms.categories.delete', 'cms', 'Xóa danh mục', 'Delete CMS categories', 'categories', 'delete', 'lcms', 'tenant', false, 'ACTIVE'),
    ('perm_media_upload', now(), now(), 'media.upload', 'media', 'Tải media lên', 'Upload media', 'media', 'upload', 'lcms', 'tenant', false, 'ACTIVE'),
    ('perm_media_delete', now(), now(), 'media.delete', 'media', 'Xóa media', 'Delete media', 'media', 'delete', 'lcms', 'tenant', false, 'ACTIVE'),
    ('perm_courses_create', now(), now(), 'courses.create', 'courses', 'Tạo khóa học', 'Create courses', 'courses', 'create', 'elearning', 'tenant,school', false, 'ACTIVE'),
    ('perm_courses_update', now(), now(), 'courses.update', 'courses', 'Sửa khóa học', 'Update courses', 'courses', 'update', 'elearning', 'tenant,school', false, 'ACTIVE'),
    ('perm_courses_delete', now(), now(), 'courses.delete', 'courses', 'Xóa khóa học', 'Delete courses', 'courses', 'delete', 'elearning', 'tenant,school', false, 'ACTIVE'),
    ('perm_documents_manage', now(), now(), 'documents.manage', 'documents', 'Quản lý tài liệu', 'Manage documents', 'documents', 'manage', 'lcms', 'tenant,school', false, 'ACTIVE'),
    ('perm_public_disclosure_manage', now(), now(), 'public-disclosure.manage', 'public-disclosure', 'Quản lý công khai', 'Manage public disclosure', 'public-disclosure', 'manage', 'admin', 'tenant', true, 'ACTIVE'),
    ('perm_lms_scope_read', now(), now(), 'lms.scope.read', 'lms', 'Xem phạm vi LMS', 'Read LMS scopes', 'scope', 'read', 'lms', 'tenant,school', false, 'ACTIVE'),
    ('perm_lms_progress_read', now(), now(), 'lms.progress.read', 'lms', 'Xem tiến độ LMS', 'Read learning progress', 'progress', 'read', 'lms', 'school,class', false, 'ACTIVE')
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

-- Every runtime-enforced key is registered. More specific REST/GraphQL bindings can
-- coexist with this policy-engine binding and are displayed together by the catalog.
WITH runtime_permissions(name) AS (VALUES
    ('account.self.password.change'),
    ('account.self.read'),
    ('account.self.recovery_email.update'),
    ('account.self.sessions.read'),
    ('account.self.update'),
    ('ai-content.use'),
    ('api-keys.manage'),
    ('cms.categories.create'),
    ('cms.categories.delete'),
    ('cms.categories.update'),
    ('cms.posts.create'),
    ('cms.posts.delete'),
    ('cms.posts.update'),
    ('content.manage'),
    ('content.resource.read'),
    ('courses.create'),
    ('courses.delete'),
    ('courses.update'),
    ('crawler.manage'),
    ('crm.analytics.read'),
    ('crm.deal.manage'),
    ('crm.school.read'),
    ('curriculum.manage'),
    ('documents.manage'),
    ('elearning.manage'),
    ('lms.admin'),
    ('lms.assignment.assign'),
    ('lms.assignment.create'),
    ('lms.assignment.delete'),
    ('lms.assignment.progress.read'),
    ('lms.assignment.read'),
    ('lms.assignment.read_self'),
    ('lms.assignment.submit'),
    ('lms.assignment.update'),
    ('lms.class.manage'),
    ('lms.class.read'),
    ('lms.exercise.create'),
    ('lms.exercise.delete'),
    ('lms.exercise.read'),
    ('lms.exercise.update'),
    ('lms.grade.read'),
    ('lms.manage'),
    ('lms.notification.preferences.update'),
    ('lms.notification.read'),
    ('lms.progress.read'),
    ('lms.quiz.create'),
    ('lms.quiz.publish'),
    ('lms.quiz.read'),
    ('lms.report.read'),
    ('lms.resource.create'),
    ('lms.resource.delete'),
    ('lms.resource.read'),
    ('lms.resource.share'),
    ('lms.resource.update'),
    ('lms.schedule.create'),
    ('lms.schedule.delete'),
    ('lms.schedule.import'),
    ('lms.schedule.manage'),
    ('lms.schedule.publish'),
    ('lms.schedule.read'),
    ('lms.schedule.read_self'),
    ('lms.schedule.update'),
    ('lms.school.archive'),
    ('lms.school.create'),
    ('lms.school.read'),
    ('lms.school.update'),
    ('lms.scope.read'),
    ('lms.student_group.create'),
    ('lms.student_group.delete'),
    ('lms.student_group.read'),
    ('lms.student_group.update'),
    ('media.delete'),
    ('media.upload'),
    ('notifications.manage'),
    ('operations.manage'),
    ('platform.read'),
    ('profile.self'),
    ('public-disclosure.manage'),
    ('rbac.permission.read'),
    ('rbac.role.assign'),
    ('rbac.role.create'),
    ('rbac.role.delete'),
    ('rbac.role.read'),
    ('rbac.role.update'),
    ('seo.manage'),
    ('users.manage')
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
