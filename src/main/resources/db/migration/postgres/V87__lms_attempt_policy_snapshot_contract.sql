-- BE-056: freeze server policy/content lineage at attempt start and make submit retries safe.

ALTER TABLE lms_attempts
    ADD COLUMN IF NOT EXISTS policy_snapshot jsonb;

ALTER TABLE lms_attempts
    ADD COLUMN IF NOT EXISTS submit_idempotency_key varchar(255);

ALTER TABLE lms_attempts
    ADD COLUMN IF NOT EXISTS record_version bigint NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX IF NOT EXISTS uq_lms_attempts_submit_idempotency
    ON lms_attempts(tenant_id, id, submit_idempotency_key)
    WHERE submit_idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_lms_attempts_policy_snapshot
    ON lms_attempts(tenant_id, assignment_id, student_id)
    WHERE policy_snapshot IS NOT NULL;
