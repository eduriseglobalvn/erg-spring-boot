-- The outbox_id FK is ON DELETE SET NULL: every outbox delete scanned the whole attempts table without this index.
-- Non-transactional by design: ONLY `CREATE INDEX CONCURRENTLY` (see V114).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notification_delivery_attempts_outbox_id
    ON notification_delivery_attempts (outbox_id);
