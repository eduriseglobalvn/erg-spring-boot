ALTER TABLE report_jobs
    ADD COLUMN IF NOT EXISTS format varchar(16) NOT NULL DEFAULT 'json',
    ADD COLUMN IF NOT EXISTS expires_at timestamptz;

ALTER TABLE report_jobs
    DROP CONSTRAINT IF EXISTS ck_report_jobs_format;
ALTER TABLE report_jobs
    ADD CONSTRAINT ck_report_jobs_format CHECK (format IN ('json', 'csv'));

CREATE INDEX IF NOT EXISTS idx_report_jobs_expiry
    ON report_jobs(tenant_id, expires_at)
    WHERE expires_at IS NOT NULL;

COMMENT ON COLUMN report_jobs.expires_at IS
    'Bounded download retention; expired results must not be served to clients.';
