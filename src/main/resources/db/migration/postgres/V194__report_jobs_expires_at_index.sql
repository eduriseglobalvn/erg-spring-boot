-- Retention purge of report_jobs (expires_at < cutoff, all tenants): V91 leads with tenant_id and cannot serve it.
-- Non-transactional by design: ONLY `CREATE INDEX CONCURRENTLY` (see V114).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_report_jobs_expires_at
    ON report_jobs (expires_at)
    WHERE expires_at IS NOT NULL;
