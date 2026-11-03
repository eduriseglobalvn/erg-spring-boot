-- (b1) Late submissions accepted inside the grace window, and auto-closed expired attempts, are marked.
ALTER TABLE lms_attempts ADD COLUMN IF NOT EXISTS late boolean NOT NULL DEFAULT false;

-- (b2) At most one in-progress attempt per learner, assignment and quiz. Older duplicate in-progress
-- rows (created by racing starts before this index existed) are closed as late submissions without
-- score so the newest attempt stays resumable and the unique index can be built.
WITH ranked AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY tenant_id, assignment_id, quiz_id, student_id
               ORDER BY coalesce(last_active_at, updated_at, started_at) DESC NULLS LAST, id DESC
           ) AS position
    FROM lms_attempts
    WHERE lower(status) = 'in_progress'
)
UPDATE lms_attempts attempt
SET status = 'submitted',
    late = true,
    submitted_at = coalesce(attempt.submitted_at, attempt.updated_at, now()),
    updated_at = now()
FROM ranked
WHERE ranked.id = attempt.id
  AND ranked.position > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_lms_attempts_single_in_progress
    ON lms_attempts (tenant_id, assignment_id, quiz_id, student_id)
    WHERE lower(status) = 'in_progress';
