-- C2/N4/SQ-08: provider calls run outside database transactions (claim -> call -> record).
--   * RUNNING marks a claimed run whose provider call is in flight.
--   * A retry creates a new run linked to the failed run instead of overwriting it; at most
--     one retry child per run, so concurrent retries cannot fan out past the retry limit.
ALTER TABLE enterprise_connector_runs ADD COLUMN IF NOT EXISTS parent_run_id varchar(36);

ALTER TABLE enterprise_connector_runs DROP CONSTRAINT IF EXISTS ck_enterprise_connector_runs_status;
ALTER TABLE enterprise_connector_runs ADD CONSTRAINT ck_enterprise_connector_runs_status
    CHECK (status IN ('RUNNING', 'PREVIEW', 'SUCCEEDED', 'FAILED', 'RETRYABLE_FAILED', 'DEPROVISIONED'));

CREATE UNIQUE INDEX IF NOT EXISTS ux_enterprise_connector_runs_parent
    ON enterprise_connector_runs (tenant_id, parent_run_id)
    WHERE parent_run_id IS NOT NULL;
