-- Serialize logical command retries at the database boundary. Preserve existing business rows;
-- only later duplicate keys are cleared before the unique indexes are installed.
WITH ranked AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY tenant_id, idempotency_key
               ORDER BY created_at, id
           ) AS duplicate_number
    FROM lms_assignments
    WHERE idempotency_key IS NOT NULL
)
UPDATE lms_assignments assignment
SET idempotency_key = NULL
FROM ranked
WHERE assignment.id = ranked.id
  AND ranked.duplicate_number > 1;

DROP INDEX IF EXISTS idx_lms_assignments_tenant_idempotency;
CREATE UNIQUE INDEX uq_lms_assignments_tenant_idempotency
    ON lms_assignments(tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

WITH ranked AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY tenant_id, quiz_id, idempotency_key
               ORDER BY created_at, id
           ) AS duplicate_number
    FROM quiz_versions
    WHERE idempotency_key IS NOT NULL
)
UPDATE quiz_versions version
SET idempotency_key = NULL
FROM ranked
WHERE version.id = ranked.id
  AND ranked.duplicate_number > 1;

CREATE UNIQUE INDEX uq_quiz_versions_publish_idempotency
    ON quiz_versions(tenant_id, quiz_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

WITH ranked AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY tenant_id, idempotency_key
               ORDER BY created_at, id
           ) AS duplicate_number
    FROM lms_quizzes
    WHERE idempotency_key IS NOT NULL
)
UPDATE lms_quizzes quiz
SET idempotency_key = NULL
FROM ranked
WHERE quiz.id = ranked.id
  AND ranked.duplicate_number > 1;

CREATE UNIQUE INDEX uq_lms_quizzes_tenant_idempotency
    ON lms_quizzes(tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
