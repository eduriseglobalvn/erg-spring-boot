INSERT INTO permission_groups (id, created_at, updated_at, name, label, display_order)
SELECT 'pg_crm', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'crm', 'CRM and School Sales', 35
WHERE NOT EXISTS (SELECT 1 FROM permission_groups WHERE id = 'pg_crm');

INSERT INTO permission_groups (id, created_at, updated_at, name, label, display_order)
SELECT 'pg_org', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'organization', 'Organization Management', 32
WHERE NOT EXISTS (SELECT 1 FROM permission_groups WHERE id = 'pg_org');

INSERT INTO permissions (id, created_at, updated_at, name, group_name, label, description)
SELECT 'p_crm_school_create', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'crm.school.create', 'crm', 'Create prospect schools', 'Create school master records from CRM prospecting'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'crm.school.create');

INSERT INTO permissions (id, created_at, updated_at, name, group_name, label, description)
SELECT 'p_crm_school_read', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'crm.school.read', 'crm', 'Read CRM schools', 'Read schools and their CRM profile'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'crm.school.read');

INSERT INTO permissions (id, created_at, updated_at, name, group_name, label, description)
SELECT 'p_crm_deal_manage', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'crm.deal.manage', 'crm', 'Manage school deals', 'Manage CRM deal pipeline, signed revenue, and lost reasons'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'crm.deal.manage');

INSERT INTO permissions (id, created_at, updated_at, name, group_name, label, description)
SELECT 'p_crm_contact_manage', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'crm.contact.manage', 'crm', 'Manage school contacts', 'Manage principal, vice principal, teacher, and other school contacts'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'crm.contact.manage');

INSERT INTO permissions (id, created_at, updated_at, name, group_name, label, description)
SELECT 'p_crm_activity_manage', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'crm.activity.manage', 'crm', 'Manage CRM activities', 'Manage calls, meetings, demos, notes, and follow-up activities'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'crm.activity.manage');

INSERT INTO permissions (id, created_at, updated_at, name, group_name, label, description)
SELECT 'p_crm_analytics_read', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'crm.analytics.read', 'crm', 'Read CRM analytics', 'Read conversion, signed revenue, lost rate, and SEO performance metrics'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'crm.analytics.read');

INSERT INTO permissions (id, created_at, updated_at, name, group_name, label, description)
SELECT 'p_org_school_activate', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'organization.school.activate', 'organization', 'Activate schools', 'Activate signed schools for operational LMS usage'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'organization.school.activate');

INSERT INTO roles (id, created_at, updated_at, name, description, is_default)
SELECT 'role_seo_staff', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'seo.staff', 'SEO staff who prospects and manages own school pipeline', false
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE id = 'role_seo_staff');

INSERT INTO roles (id, created_at, updated_at, name, description, is_default)
SELECT 'role_seo_lead', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'seo.lead', 'SEO lead who supports a team pipeline', false
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE id = 'role_seo_lead');

INSERT INTO roles (id, created_at, updated_at, name, description, is_default)
SELECT 'role_seo_manager', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'seo.manager', 'SEO manager with CRM analytics and pipeline oversight', false
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE id = 'role_seo_manager');

INSERT INTO roles (id, created_at, updated_at, name, description, is_default)
SELECT 'role_center_manager', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'center.manager', 'Center manager with descendant school operation access', false
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE id = 'role_center_manager');

INSERT INTO roles (id, created_at, updated_at, name, description, is_default)
SELECT 'role_school_manager', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'school.manager', 'School manager with school-level operation access', false
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE id = 'role_school_manager');

INSERT INTO roles (id, created_at, updated_at, name, description, is_default)
SELECT 'role_executive_director', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'executive.director', 'Executive director with organization and analytics visibility', false
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE id = 'role_executive_director');

INSERT INTO role_permissions (role_id, permission_id)
SELECT role_id, permission_id
FROM (VALUES
    ('role_seo_staff', 'p_crm_school_create'),
    ('role_seo_staff', 'p_crm_school_read'),
    ('role_seo_staff', 'p_crm_deal_manage'),
    ('role_seo_staff', 'p_crm_contact_manage'),
    ('role_seo_staff', 'p_crm_activity_manage'),
    ('role_seo_lead', 'p_crm_school_read'),
    ('role_seo_lead', 'p_crm_deal_manage'),
    ('role_seo_lead', 'p_crm_contact_manage'),
    ('role_seo_lead', 'p_crm_activity_manage'),
    ('role_seo_lead', 'p_crm_analytics_read'),
    ('role_seo_manager', 'p_crm_school_read'),
    ('role_seo_manager', 'p_crm_deal_manage'),
    ('role_seo_manager', 'p_crm_contact_manage'),
    ('role_seo_manager', 'p_crm_activity_manage'),
    ('role_seo_manager', 'p_crm_analytics_read'),
    ('role_center_manager', 'perm_lms_manage'),
    ('role_center_manager', 'p_org_school_activate'),
    ('role_school_manager', 'perm_lms_manage'),
    ('role_executive_director', 'p_crm_analytics_read'),
    ('role_executive_director', 'perm_lms_read')
) AS rp(role_id, permission_id)
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions
    WHERE role_permissions.role_id = rp.role_id AND role_permissions.permission_id = rp.permission_id
);
