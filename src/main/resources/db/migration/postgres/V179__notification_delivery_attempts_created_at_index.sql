-- Retention deletes scan notification_delivery_attempts by created_at.
-- Non-transactional by design: ONLY `CREATE INDEX CONCURRENTLY` (see V114).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notification_delivery_attempts_created_at
    ON notification_delivery_attempts (created_at);
