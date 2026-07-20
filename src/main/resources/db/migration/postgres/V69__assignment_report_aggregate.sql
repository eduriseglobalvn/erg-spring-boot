-- Incremental assignment-report aggregate foundation.
-- Sixty-four shards avoid one hot aggregate row when thousands of students submit
-- the same assignment concurrently. Interactive reads sum at most 64 compact rows.
CREATE TABLE IF NOT EXISTS assignment_progress_aggregates (
    tenant_id varchar(255) NOT NULL,
    assignment_id varchar(36) NOT NULL,
    shard_id smallint NOT NULL,
    total_recipients bigint NOT NULL DEFAULT 0,
    submitted_count bigint NOT NULL DEFAULT 0,
    in_progress_count bigint NOT NULL DEFAULT 0,
    needs_review_count bigint NOT NULL DEFAULT 0,
    scored_count bigint NOT NULL DEFAULT 0,
    score_sum numeric(20,4) NOT NULL DEFAULT 0,
    source_version bigint NOT NULL DEFAULT 1,
    source_updated_at timestamptz NOT NULL DEFAULT now(),
    reconciled_at timestamptz,
    PRIMARY KEY (tenant_id, assignment_id, shard_id),
    CONSTRAINT ck_assignment_progress_aggregate_shard CHECK (shard_id BETWEEN 0 AND 63),
    CONSTRAINT ck_assignment_progress_aggregate_counts CHECK (
        total_recipients >= 0 AND submitted_count >= 0 AND in_progress_count >= 0
        AND needs_review_count >= 0 AND scored_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_assignment_progress_aggregates_lookup
    ON assignment_progress_aggregates(tenant_id, assignment_id);

CREATE TABLE IF NOT EXISTS report_reconciliation_queue (
    tenant_id varchar(255) NOT NULL,
    aggregate_type varchar(64) NOT NULL,
    aggregate_id varchar(96) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'pending',
    attempts integer NOT NULL DEFAULT 0,
    available_at timestamptz NOT NULL DEFAULT now(),
    lease_until timestamptz,
    source_updated_at timestamptz NOT NULL DEFAULT now(),
    last_error text,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, aggregate_type, aggregate_id),
    CONSTRAINT ck_report_reconciliation_status CHECK (status IN ('pending', 'processing', 'failed'))
);

CREATE INDEX IF NOT EXISTS idx_report_reconciliation_pickup
    ON report_reconciliation_queue(status, available_at, lease_until);

CREATE OR REPLACE FUNCTION erg_apply_assignment_progress_delta(
    p_tenant_id text,
    p_assignment_id text,
    p_student_user_id text,
    p_total_delta bigint,
    p_submitted_delta bigint,
    p_in_progress_delta bigint,
    p_needs_review_delta bigint,
    p_scored_delta bigint,
    p_score_delta numeric
) RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    v_shard smallint;
BEGIN
    IF p_tenant_id IS NULL OR p_assignment_id IS NULL OR p_student_user_id IS NULL THEN
        RETURN;
    END IF;
    v_shard := (hashtextextended(p_student_user_id, 0) & 63)::smallint;

    IF p_total_delta < 0 THEN
        UPDATE assignment_progress_aggregates SET
            total_recipients = greatest(0, total_recipients + p_total_delta),
            submitted_count = greatest(0, submitted_count + p_submitted_delta),
            in_progress_count = greatest(0, in_progress_count + p_in_progress_delta),
            needs_review_count = greatest(0, needs_review_count + p_needs_review_delta),
            scored_count = greatest(0, scored_count + p_scored_delta),
            score_sum = score_sum + coalesce(p_score_delta, 0),
            source_version = source_version + 1,
            source_updated_at = now()
        WHERE tenant_id = p_tenant_id AND assignment_id = p_assignment_id AND shard_id = v_shard;
        RETURN;
    END IF;

    INSERT INTO assignment_progress_aggregates (
        tenant_id, assignment_id, shard_id, total_recipients, submitted_count,
        in_progress_count, needs_review_count, scored_count, score_sum,
        source_version, source_updated_at)
    VALUES (
        p_tenant_id, p_assignment_id, v_shard, p_total_delta, p_submitted_delta,
        p_in_progress_delta, p_needs_review_delta, p_scored_delta,
        coalesce(p_score_delta, 0), 1, now())
    ON CONFLICT (tenant_id, assignment_id, shard_id) DO UPDATE SET
        total_recipients = assignment_progress_aggregates.total_recipients + EXCLUDED.total_recipients,
        submitted_count = assignment_progress_aggregates.submitted_count + EXCLUDED.submitted_count,
        in_progress_count = assignment_progress_aggregates.in_progress_count + EXCLUDED.in_progress_count,
        needs_review_count = assignment_progress_aggregates.needs_review_count + EXCLUDED.needs_review_count,
        scored_count = assignment_progress_aggregates.scored_count + EXCLUDED.scored_count,
        score_sum = assignment_progress_aggregates.score_sum + EXCLUDED.score_sum,
        source_version = assignment_progress_aggregates.source_version + 1,
        source_updated_at = now();
END;
$$;

CREATE OR REPLACE FUNCTION erg_student_progress_assignment_aggregate_trigger()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        PERFORM erg_apply_assignment_progress_delta(
            OLD.tenant_id, OLD.assignment_id, OLD.student_user_id, -1,
            -CASE WHEN lower(coalesce(OLD.status, '')) = 'submitted' THEN 1 ELSE 0 END,
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
            CASE WHEN lower(coalesce(NEW.status, '')) = 'submitted' THEN 1 ELSE 0 END,
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

DROP TRIGGER IF EXISTS trg_student_progress_assignment_aggregate ON student_progress;
CREATE TRIGGER trg_student_progress_assignment_aggregate
AFTER INSERT OR UPDATE OR DELETE ON student_progress
FOR EACH ROW EXECUTE FUNCTION erg_student_progress_assignment_aggregate_trigger();

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
           count(*) FILTER (WHERE lower(coalesce(status, '')) = 'submitted'),
           count(*) FILTER (WHERE lower(coalesce(status, '')) = 'in_progress'),
           count(*) FILTER (WHERE lower(coalesce(status, '')) IN ('needs_review', 'review_required')),
           count(score), coalesce(sum(score), 0), 1, max(updated_at), now()
    FROM student_progress
    WHERE tenant_id = p_tenant_id AND assignment_id = p_assignment_id
    GROUP BY tenant_id, assignment_id, (hashtextextended(student_user_id, 0) & 63)::smallint;
END;
$$;

INSERT INTO assignment_progress_aggregates (
    tenant_id, assignment_id, shard_id, total_recipients, submitted_count,
    in_progress_count, needs_review_count, scored_count, score_sum,
    source_version, source_updated_at, reconciled_at)
SELECT tenant_id, assignment_id,
       (hashtextextended(student_user_id, 0) & 63)::smallint,
       count(*),
       count(*) FILTER (WHERE lower(coalesce(status, '')) = 'submitted'),
       count(*) FILTER (WHERE lower(coalesce(status, '')) = 'in_progress'),
       count(*) FILTER (WHERE lower(coalesce(status, '')) IN ('needs_review', 'review_required')),
       count(score), coalesce(sum(score), 0), 1, max(updated_at), now()
FROM student_progress
WHERE assignment_id IS NOT NULL
GROUP BY tenant_id, assignment_id, (hashtextextended(student_user_id, 0) & 63)::smallint
ON CONFLICT (tenant_id, assignment_id, shard_id) DO NOTHING;

ALTER TABLE assignment_progress_aggregates ENABLE ROW LEVEL SECURITY;
ALTER TABLE assignment_progress_aggregates FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS assignment_progress_aggregates_tenant_isolation ON assignment_progress_aggregates;
CREATE POLICY assignment_progress_aggregates_tenant_isolation ON assignment_progress_aggregates
    USING (current_setting('app.maintenance_mode', true) = 'true'
        OR tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (current_setting('app.maintenance_mode', true) = 'true'
        OR tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE report_reconciliation_queue ENABLE ROW LEVEL SECURITY;
ALTER TABLE report_reconciliation_queue FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS report_reconciliation_queue_tenant_isolation ON report_reconciliation_queue;
CREATE POLICY report_reconciliation_queue_tenant_isolation ON report_reconciliation_queue
    USING (current_setting('app.maintenance_mode', true) = 'true'
        OR tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (current_setting('app.maintenance_mode', true) = 'true'
        OR tenant_id = nullif(current_setting('app.tenant_id', true), ''));
