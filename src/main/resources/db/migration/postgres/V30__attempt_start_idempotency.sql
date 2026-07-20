-- V30__attempt_start_idempotency.sql
-- Runtime hardening: retries/double-clicks on POST /lms/attempts must not create duplicate attempts.

ALTER TABLE lms_attempts ADD COLUMN IF NOT EXISTS start_idempotency_key varchar(255);

CREATE UNIQUE INDEX IF NOT EXISTS uq_lms_attempts_start_idempotency
    ON lms_attempts(tenant_id, assignment_id, quiz_id, student_id, start_idempotency_key)
    WHERE start_idempotency_key IS NOT NULL;
