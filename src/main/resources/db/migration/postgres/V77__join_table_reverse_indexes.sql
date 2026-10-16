-- Composite PKs on user_roles(user_id, role_id) and role_permissions(role_id, permission_id)
-- only index the leading column. Reverse lookups ("which users have role X", "which roles grant
-- permission Y" — e.g. RoleRepository.findAllWithPermissionsAndUsersByPermissionIds) seq-scan
-- without an index on the trailing column.
CREATE INDEX IF NOT EXISTS idx_user_roles_role_id ON user_roles(role_id);
CREATE INDEX IF NOT EXISTS idx_role_permissions_permission_id ON role_permissions(permission_id);
