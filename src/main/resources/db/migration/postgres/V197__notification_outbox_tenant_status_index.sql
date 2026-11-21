-- Admin outbox stats group a tenant's rows by status; the V10 pickup index leads with status and has no tenant column.
-- Non-transactional by design: ONLY `CREATE INDEX CONCURRENTLY` (see V114).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notification_outbox_tenant_status
    ON notification_outbox (tenant_id, status);
