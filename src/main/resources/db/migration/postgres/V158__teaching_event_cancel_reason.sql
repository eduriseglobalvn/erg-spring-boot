-- V158__teaching_event_cancel_reason.sql
-- Why a teaching_schedule_events row is CANCELLED, so the teacher's sheet ("Cong cua toi") lists only lessons that
-- really fell through and not rows an edit, a bulk replace or a deleted draft merely superseded.
--   HOLIDAY         a day off cancelled the period (also cancelled_by_holiday_id)
--   ADMIN_CANCEL    an admin deleted a published period or a teacher's published schedule
--   BATCH_CANCELLED a published batch was cancelled (periods not yet started)
--   REPLACED        a bulk create with DELETE_CONFLICTING superseded the period
--   DRAFT_REMOVED   a draft period / draft batch / teacher draft schedule deleted before publishing
-- Rows cancelled before this migration keep NULL except holiday ones: the old status alone cannot tell the cases
-- apart, and NULL is treated as "not shown to the teacher" (no leak).
ALTER TABLE teaching_schedule_events ADD COLUMN cancel_reason VARCHAR(20);
ALTER TABLE teaching_schedule_events ADD CONSTRAINT chk_tse_cancel_reason
    CHECK (cancel_reason IS NULL OR cancel_reason IN ('HOLIDAY', 'ADMIN_CANCEL', 'BATCH_CANCELLED', 'REPLACED', 'DRAFT_REMOVED'));
UPDATE teaching_schedule_events SET cancel_reason = 'HOLIDAY' WHERE status = 'CANCELLED' AND cancelled_by_holiday_id IS NOT NULL;
