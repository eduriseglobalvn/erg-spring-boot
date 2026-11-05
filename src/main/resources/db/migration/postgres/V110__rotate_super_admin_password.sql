-- Rotate the seeded super admin password (the V7 hash is public in source control).
-- Argon2id hash generated with Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(), same as PasswordService.
-- Only while the row still carries the public V7 hash: an owner-rotated password must not be replaced
-- by this temporary one, and its sessions stay valid.
WITH rotated AS (
    UPDATE users
    SET password_hash = '$argon2id$v=19$m=16384,t=2,p=1$QUckidyuyvIcpAKkPuaI7Q$fX5Bt5G/LRjhWX3eDrH6J4w8bUG0pY1ZhZja+mLpKWY',
        updated_at = CURRENT_TIMESTAMP
    WHERE id = 'usr_super_admin'
      AND password_hash = '$argon2id$v=19$m=16384,t=2,p=1$Y1xo5N7TR2o0n8flxcsehw$TBb8yQcuYHtKBtKQvk9ZbUnXLC6avXqQY6tqlVgmnqs'
    RETURNING id
)
-- Sessions issued under the old password must not survive the rotation.
UPDATE user_sessions
SET revoked_at = CURRENT_TIMESTAMP,
    revoked_reason = 'PASSWORD_ROTATED'
WHERE user_id IN (SELECT id FROM rotated)
  AND revoked_at IS NULL;
