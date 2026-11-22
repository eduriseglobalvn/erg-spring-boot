-- Retention of attempt_events (AttemptEventRetentionJob) deletes by received_at in small batches; the only index is (attempt_id, created_at),
-- so without this the purge would scan the whole append-only table. Non-transactional by design: ONLY `CREATE INDEX CONCURRENTLY` (see V114).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_attempt_events_received_at
    ON attempt_events (received_at);
