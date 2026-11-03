-- Measured 2026-07-17 on the deterministic 180k-attempt/900k-answer workload.
-- Shipped by Flyway V114 (without the ANALYZE). Run this script ahead of a deploy only to pre-build
-- the index; use psql autocommit, concurrent index creation is forbidden inside a transaction.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_lms_attempts_tenant_version_status_id
    ON lms_attempts(tenant_id, quiz_version_id, status, id);

ANALYZE lms_attempts;
