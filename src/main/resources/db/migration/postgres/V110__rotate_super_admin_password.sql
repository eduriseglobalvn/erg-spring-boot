-- Rotate the seeded super admin password (the V7 hash is public in source control).
-- Argon2id hash generated with Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(), same as PasswordService.
UPDATE users
SET password_hash = '$argon2id$v=19$m=16384,t=2,p=1$QUckidyuyvIcpAKkPuaI7Q$fX5Bt5G/LRjhWX3eDrH6J4w8bUG0pY1ZhZja+mLpKWY',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'usr_super_admin';

-- Sessions issued under the old password must not survive the rotation.
UPDATE user_sessions
SET revoked_at = CURRENT_TIMESTAMP,
    revoked_reason = 'PASSWORD_ROTATED'
WHERE user_id = 'usr_super_admin'
  AND revoked_at IS NULL;
