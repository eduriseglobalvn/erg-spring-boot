-- P2 notifications: admins and teachers may compose and send notification campaigns.
-- Who they may address is decided per request by AudienceScopeGuard (admin: whole tenant,
-- teacher: students of the classes they teach); this permission only opens the API.
INSERT INTO permissions (
    id, created_at, updated_at, name, group_name, label, description,
    resource, action, portal, supported_scope, is_system, status
)
VALUES
    ('perm_notification_campaign_send', now(), now(), 'notification.campaign.send', 'notifications',
     'Send notification campaigns', 'Compose, schedule and send notifications to an audience within the sender scope',
     'notification_campaign', 'send', 'lms', 'tenant,school,class', false, 'ACTIVE')
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
JOIN permissions permission ON permission.name = 'notification.campaign.send'
WHERE role.id IN (
    'role_system_admin', 'role_lms_admin', 'role_pdt_operator',
    'role_lms_teacher', 'role_lms_teacher_standard', 'role_lms_homeroom_teacher',
    'role_lms_subject_teacher', 'role_lms_teacher_lead'
)
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions existing
      WHERE existing.role_id = role.id AND existing.permission_id = permission.id
  );

-- A scheduled campaign whose dispatch fails (creator lost scope, audience too large, error) ends FAILED.
ALTER TABLE notification_campaigns DROP CONSTRAINT IF EXISTS ck_notification_campaigns_status;
ALTER TABLE notification_campaigns ADD CONSTRAINT ck_notification_campaigns_status
    CHECK (status IN ('DRAFT', 'SCHEDULED', 'SENDING', 'SENT', 'CANCELLED', 'FAILED'));

-- Tenant isolation for the campaign table (V129 created it without a policy; same rule as every tenant table).
ALTER TABLE notification_campaigns ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_campaigns FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS notification_campaigns_tenant_isolation ON notification_campaigns;
CREATE POLICY notification_campaigns_tenant_isolation ON notification_campaigns
    USING (
        current_setting('app.maintenance_mode', true) = 'true'
        OR (nullif(current_setting('app.tenant_id', true), '') IS NOT NULL
            AND tenant_id = current_setting('app.tenant_id', true))
    )
    WITH CHECK (
        current_setting('app.maintenance_mode', true) = 'true'
        OR (nullif(current_setting('app.tenant_id', true), '') IS NOT NULL
            AND tenant_id = current_setting('app.tenant_id', true))
    );
