-- Soft delete of accounts: status DELETED plus a 30-day restore window; the purge job anonymises after purge_after.
ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_at timestamp with time zone;
ALTER TABLE users ADD COLUMN IF NOT EXISTS purge_after timestamp with time zone;
CREATE INDEX IF NOT EXISTS idx_users_purge_after ON users (purge_after) WHERE purge_after IS NOT NULL;
