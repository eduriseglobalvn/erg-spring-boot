-- C11: audit_events stays append-only, but legal holds become usable and retention can
-- no longer delete held or unexpired rows, even with app.audit_retention_job set.
--   UPDATE: allowed only when every column except legal_hold/updated_at is unchanged.
--   DELETE: allowed only for the retention job, and only when the row is not on legal hold
--           and its retention_until has passed.
-- Follow-up (needs DBA/role work, not in this migration): move retention deletes into a
-- SECURITY DEFINER function owned by a dedicated role and REVOKE DELETE from erg_app so the
-- GUC is no longer the only gate.
CREATE OR REPLACE FUNCTION erg_audit_events_append_only() RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF (to_jsonb(NEW) - 'legal_hold' - 'updated_at') = (to_jsonb(OLD) - 'legal_hold' - 'updated_at') THEN
            RETURN NEW;
        END IF;
        RAISE EXCEPTION 'audit_events_append_only';
    END IF;
    IF TG_OP = 'DELETE'
       AND current_setting('app.audit_retention_job', true) = 'true'
       AND OLD.legal_hold = false
       AND OLD.retention_until < now() THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'audit_events_append_only';
END;
$$;
