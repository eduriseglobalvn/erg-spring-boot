-- NotificationOutboxRetentionJob purges terminal rows ('sent'/'dead') older than the retention window
-- via `where status in ('sent','dead') and created_at < :cutoff limit :batchSize`. The existing
-- idx_notification_outbox_pickup leads with (status, next_attempt_at, ...), so the created_at range
-- test can't be served by an index-only scan for the retention predicate — each batch would seq-scan.
-- This partial index covers exactly the terminal rows the job scans, keeping it small on the busiest
-- table (only sent/dead rows are indexed) and letting the bounded delete resolve via an index range.
-- Non-CONCURRENTLY + IF NOT EXISTS: Flyway wraps each migration in a transaction (CONCURRENTLY is
-- illegal there) and IF NOT EXISTS keeps re-runs safe, matching the V77/V79 index style.
CREATE INDEX IF NOT EXISTS idx_notification_outbox_retention
    ON notification_outbox (status, created_at)
    WHERE status IN ('sent', 'dead');
