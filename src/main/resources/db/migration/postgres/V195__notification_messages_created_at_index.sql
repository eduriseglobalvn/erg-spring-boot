-- Retention purge of notification_messages (created_at < cutoff, all tenants): V10 leads with tenant_id and cannot serve it.
-- Non-transactional by design: ONLY `CREATE INDEX CONCURRENTLY` (see V114).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notification_messages_created_at
    ON notification_messages (created_at);
