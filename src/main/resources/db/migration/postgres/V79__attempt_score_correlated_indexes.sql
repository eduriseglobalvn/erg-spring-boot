-- AttemptRepository.findLatestAttemptScoresForRecipients / findBestAttemptScoresForRecipients run a
-- correlated NOT EXISTS that self-joins lms_attempts on (tenant_id, assignment_id, student_id) and
-- then compares updated_at (latest) or percent+updated_at (best). Existing indexes only cover
-- (tenant_id, assignment_id) / (tenant_id, student_id), so each correlated probe seq-scans the row
-- group. These covering indexes let the NOT EXISTS resolve the "newer/better" test with an index-only
-- range scan per (tenant_id, assignment_id, student_id).
-- Non-CONCURRENTLY: Flyway runs each migration in a transaction and CREATE INDEX CONCURRENTLY is
-- illegal inside one. IF NOT EXISTS keeps re-runs safe, matching the V77 index style.
CREATE INDEX IF NOT EXISTS idx_lms_attempts_latest_score
    ON lms_attempts (tenant_id, assignment_id, student_id, updated_at DESC, id);
CREATE INDEX IF NOT EXISTS idx_lms_attempts_best_score
    ON lms_attempts (tenant_id, assignment_id, student_id, percent DESC, updated_at DESC, id);
