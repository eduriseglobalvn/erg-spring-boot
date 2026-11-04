-- P0-1 forward fix: V110 rotates the seeded super admin to a temporary password that is documented in
-- the runbook, so the owner must replace it at first login. Only flags the row while it still carries
-- the V110 hash; an admin password rotated afterwards is left untouched.
UPDATE users
SET must_change_password = true,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'usr_super_admin'
  AND password_hash = '$argon2id$v=19$m=16384,t=2,p=1$QUckidyuyvIcpAKkPuaI7Q$fX5Bt5G/LRjhWX3eDrH6J4w8bUG0pY1ZhZja+mLpKWY';
