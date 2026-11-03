-- P0-6 / N6: approving a CRM handover is a separate duty from creating one.
-- crm.deal.manage (held by seo.staff) no longer suffices; the service layer
-- requires crm.handover.approve and rejects self-approval.
INSERT INTO permissions (
    id, created_at, updated_at, name, group_name, label, description,
    resource, action, portal, supported_scope, is_system, status
)
VALUES ('p_crm_handover_approve', now(), now(), 'crm.handover.approve', 'crm', 'Approve CRM handovers',
        'Approve a ready CRM handover requested by another user', 'handover', 'approve', 'crm', 'tenant',
        false, 'ACTIVE')
ON CONFLICT (name) DO UPDATE SET
    group_name = EXCLUDED.group_name,
    label = EXCLUDED.label,
    description = EXCLUDED.description,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    portal = EXCLUDED.portal,
    supported_scope = EXCLUDED.supported_scope,
    status = EXCLUDED.status,
    updated_at = now();

-- Managers and leads oversee team pipelines; seo.staff (the usual requester) is not granted.
INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.name = 'crm.handover.approve'
WHERE role.name IN ('seo.manager', 'seo.lead')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions existing
      WHERE existing.role_id = role.id AND existing.permission_id = permission.id
  );

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
WHERE permission.name = 'crm.handover.approve'
ON CONFLICT (permission_id, surface_type, surface_identifier) DO UPDATE
SET enforced = true, description = EXCLUDED.description, updated_at = now();
