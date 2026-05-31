INSERT INTO users (
    id,
    created_at,
    updated_at,
    email,
    password_hash,
    full_name,
    status,
    provider,
    account_type,
    last_login_provider,
    tenant_id,
    job_title,
    is_profile_completed,
    login_count
)
SELECT
    'usr_super_admin',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'admin@erg.edu.vn',
    '$argon2id$v=19$m=16384,t=2,p=1$Y1xo5N7TR2o0n8flxcsehw$TBb8yQcuYHtKBtKQvk9ZbUnXLC6avXqQY6tqlVgmnqs',
    'ERG Super Admin',
    'ACTIVE',
    'local',
    'erg',
    'local',
    'erg',
    'System Super Admin',
    true,
    0
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE lower(email) = lower('admin@erg.edu.vn')
);

INSERT INTO user_roles (user_id, role_id)
SELECT 'usr_super_admin', 'role_super_admin'
WHERE EXISTS (SELECT 1 FROM users WHERE id = 'usr_super_admin')
  AND EXISTS (SELECT 1 FROM roles WHERE id = 'role_super_admin')
  AND NOT EXISTS (
      SELECT 1 FROM user_roles
      WHERE user_id = 'usr_super_admin' AND role_id = 'role_super_admin'
  );

INSERT INTO user_access_scopes (
    id,
    created_at,
    updated_at,
    user_id,
    education_unit_id,
    modules,
    role
)
SELECT
    'scope_super_admin',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'usr_super_admin',
    'ctr_system',
    '*',
    'system.super_admin'
WHERE EXISTS (SELECT 1 FROM users WHERE id = 'usr_super_admin')
  AND EXISTS (SELECT 1 FROM education_units WHERE id = 'ctr_system')
  AND NOT EXISTS (
      SELECT 1 FROM user_access_scopes WHERE id = 'scope_super_admin'
  );

INSERT INTO profiles (
    id,
    created_at,
    updated_at,
    user_id,
    full_name,
    phone,
    is_profile_completed,
    internal_note
)
SELECT
    'prof_super_admin',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'usr_super_admin',
    'ERG Super Admin',
    null,
    true,
    'Seeded by Flyway enterprise admin account migration'
WHERE EXISTS (SELECT 1 FROM users WHERE id = 'usr_super_admin')
  AND NOT EXISTS (
      SELECT 1 FROM profiles WHERE user_id = 'usr_super_admin'
  );
