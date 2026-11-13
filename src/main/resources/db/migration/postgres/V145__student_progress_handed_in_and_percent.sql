-- DATA-01 (G3-02): student_progress is the teacher's assignment progress source.
-- 1. "Submitted" in the sharded aggregate now means handed in: LmsStatePolicy.completedAttemptStates()
--    (submitted, needs_review, review_required, graded, completed). Before, a graded hand-in fell into
--    "not started". needs_review stays counted on its own too (a subset of handed in).
-- 2. student_progress.score held raw points; reports read it as 0-100. Backfill it from the percent of the
--    latest handed-in attempt (newest updated_at, then id: LatestHandedInAttempt), then rebuild the aggregate.
-- Every table here is FORCE RLS; the migration acts on all tenants (transaction-local).
SELECT set_config('app.maintenance_mode', 'true', true);

CREATE OR REPLACE FUNCTION erg_student_progress_assignment_aggregate_trigger()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        PERFORM erg_apply_assignment_progress_delta(
            OLD.tenant_id, OLD.assignment_id, OLD.student_user_id, -1,
            -CASE WHEN lower(coalesce(OLD.status, '')) IN ('submitted', 'needs_review', 'review_required', 'graded', 'completed') THEN 1 ELSE 0 END,
            -CASE WHEN lower(coalesce(OLD.status, '')) = 'in_progress' THEN 1 ELSE 0 END,
            -CASE WHEN lower(coalesce(OLD.status, '')) IN ('needs_review', 'review_required') THEN 1 ELSE 0 END,
            -CASE WHEN OLD.score IS NOT NULL THEN 1 ELSE 0 END,
            -coalesce(OLD.score, 0));
        IF OLD.assignment_id IS NOT NULL THEN
            INSERT INTO report_reconciliation_queue (
                tenant_id, aggregate_type, aggregate_id, status, available_at, source_updated_at)
            VALUES (OLD.tenant_id, 'assignment_progress', OLD.assignment_id, 'pending', now(), now())
            ON CONFLICT (tenant_id, aggregate_type, aggregate_id) DO UPDATE SET
                status = 'pending', available_at = now(), lease_until = NULL,
                source_updated_at = EXCLUDED.source_updated_at, last_error = NULL;
        END IF;
    END IF;
    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        PERFORM erg_apply_assignment_progress_delta(
            NEW.tenant_id, NEW.assignment_id, NEW.student_user_id, 1,
            CASE WHEN lower(coalesce(NEW.status, '')) IN ('submitted', 'needs_review', 'review_required', 'graded', 'completed') THEN 1 ELSE 0 END,
            CASE WHEN lower(coalesce(NEW.status, '')) = 'in_progress' THEN 1 ELSE 0 END,
            CASE WHEN lower(coalesce(NEW.status, '')) IN ('needs_review', 'review_required') THEN 1 ELSE 0 END,
            CASE WHEN NEW.score IS NOT NULL THEN 1 ELSE 0 END,
            coalesce(NEW.score, 0));
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION erg_reconcile_assignment_progress(p_tenant_id text, p_assignment_id text)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    DELETE FROM assignment_progress_aggregates
    WHERE tenant_id = p_tenant_id AND assignment_id = p_assignment_id;

    INSERT INTO assignment_progress_aggregates (
        tenant_id, assignment_id, shard_id, total_recipients, submitted_count,
        in_progress_count, needs_review_count, scored_count, score_sum,
        source_version, source_updated_at, reconciled_at)
    SELECT tenant_id, assignment_id,
           (hashtextextended(student_user_id, 0) & 63)::smallint,
           count(*),
           count(*) FILTER (WHERE lower(coalesce(status, '')) IN ('submitted', 'needs_review', 'review_required', 'graded', 'completed')),
           count(*) FILTER (WHERE lower(coalesce(status, '')) = 'in_progress'),
           count(*) FILTER (WHERE lower(coalesce(status, '')) IN ('needs_review', 'review_required')),
           count(score), coalesce(sum(score), 0), 1, max(updated_at), now()
    FROM student_progress
    WHERE tenant_id = p_tenant_id AND assignment_id = p_assignment_id
    GROUP BY tenant_id, assignment_id, (hashtextextended(student_user_id, 0) & 63)::smallint;
END;
$$;

-- student_user_id is the learner's login id (or the Student id when it has none); attempts carry the Student id.
WITH latest AS (
    SELECT DISTINCT ON (progress.id) progress.id, attempt.percent
    FROM student_progress progress
    JOIN lms_students student
      ON student.tenant_id = progress.tenant_id
     AND (student.auth_user_id = progress.student_user_id OR student.id = progress.student_user_id)
    JOIN lms_attempts attempt
      ON attempt.tenant_id = progress.tenant_id
     AND attempt.assignment_id = progress.assignment_id
     AND attempt.quiz_id = progress.quiz_id
     AND attempt.student_id = student.id
    WHERE lower(attempt.status) IN ('submitted', 'needs_review', 'review_required', 'graded', 'completed')
      AND attempt.percent IS NOT NULL
    ORDER BY progress.id, attempt.updated_at DESC, attempt.id DESC
)
UPDATE student_progress progress
SET score = latest.percent
FROM latest
WHERE progress.id = latest.id
  AND progress.score IS DISTINCT FROM latest.percent::numeric(8,2);

DELETE FROM assignment_progress_aggregates;
INSERT INTO assignment_progress_aggregates (
    tenant_id, assignment_id, shard_id, total_recipients, submitted_count,
    in_progress_count, needs_review_count, scored_count, score_sum,
    source_version, source_updated_at, reconciled_at)
SELECT tenant_id, assignment_id,
       (hashtextextended(student_user_id, 0) & 63)::smallint,
       count(*),
       count(*) FILTER (WHERE lower(coalesce(status, '')) IN ('submitted', 'needs_review', 'review_required', 'graded', 'completed')),
       count(*) FILTER (WHERE lower(coalesce(status, '')) = 'in_progress'),
       count(*) FILTER (WHERE lower(coalesce(status, '')) IN ('needs_review', 'review_required')),
       count(score), coalesce(sum(score), 0), 1, max(updated_at), now()
FROM student_progress
WHERE assignment_id IS NOT NULL
GROUP BY tenant_id, assignment_id, (hashtextextended(student_user_id, 0) & 63)::smallint;
