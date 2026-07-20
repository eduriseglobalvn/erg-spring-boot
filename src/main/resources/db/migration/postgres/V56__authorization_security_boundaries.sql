-- Authorization security guardrails.
-- Reserved system role names remain service-owned because existing system roles
-- must continue to be readable; create/update APIs reject the namespace in code.

CREATE UNIQUE INDEX IF NOT EXISTS ux_roles_name_lower
    ON roles (lower(name))
    WHERE name IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_users_tenant_id_id
    ON users (tenant_id, id);

CREATE INDEX IF NOT EXISTS idx_users_tenant_created_at
    ON users (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_permission_overrides_user_id
    ON user_permissions (user_id);
