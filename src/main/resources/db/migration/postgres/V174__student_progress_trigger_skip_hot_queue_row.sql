-- Hot-row lock: every progress UPDATE upserted the one reconciliation-queue row of its assignment, so
-- thousands of students submitting the same assignment serialised on it. The delta function already keeps
-- the aggregate exact; queue a reconcile only when a row is deleted or moved to another assignment.
-- CREATE OR REPLACE FUNCTION is metadata-only and touches no table rows.
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
        IF OLD.assignment_id IS NOT NULL AND (TG_OP = 'DELETE' OR OLD.assignment_id IS DISTINCT FROM NEW.assignment_id) THEN
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
