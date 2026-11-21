-- Per-email lockout scans and history match the email case-insensitively and read the newest rows first. The index is on
-- lower(attempted_email): the repository finders must compare lower(...) (explicit @Query) for it to be used.
-- Non-transactional by design: ONLY `CREATE INDEX CONCURRENTLY` (see V114).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_auth_login_attempts_email_lower_created
    ON auth_login_attempts (lower(attempted_email), created_at DESC);
