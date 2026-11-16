-- V151__teaching_schedule_rooms_seats_and_indexes.sql
ALTER TABLE teaching_schedule_rooms ADD COLUMN seat_count SMALLINT NULL;

-- Conflict engine and access rule look events up by class/school and by time.
CREATE INDEX IF NOT EXISTS ix_tse_class_status_end ON teaching_schedule_events (academic_class_id, status, end_at);
CREATE INDEX IF NOT EXISTS ix_tse_school_start ON teaching_schedule_events (school_id, start_at);
-- Link table column is teacher_user_id (the plan wrote teacher_id).
CREATE INDEX IF NOT EXISTS ix_tset_teacher ON teaching_schedule_event_teachers (teacher_user_id, event_id);
