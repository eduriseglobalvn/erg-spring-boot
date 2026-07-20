-- V22__teaching_calendar_enterprise_indexes.sql
-- Hot-path indexes for GraphQL teaching calendar workspace, conflict preview, and batch listing.

CREATE INDEX IF NOT EXISTS idx_tse_tenant_school_status_timerange
    ON teaching_schedule_events(tenant_id, school_id, status, start_at, end_at);

CREATE INDEX IF NOT EXISTS idx_tse_tenant_batch
    ON teaching_schedule_events(tenant_id, batch_id);

CREATE INDEX IF NOT EXISTS idx_tse_tenant_class_status_timerange
    ON teaching_schedule_events(tenant_id, academic_class_id, status, start_at, end_at);

CREATE INDEX IF NOT EXISTS idx_tse_tenant_subject_status_timerange
    ON teaching_schedule_events(tenant_id, subject_id, status, start_at, end_at);

CREATE INDEX IF NOT EXISTS idx_tse_tenant_level_status_timerange
    ON teaching_schedule_events(tenant_id, level_id, status, start_at, end_at)
    WHERE level_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_tse_tenant_school_room_timerange_active
    ON teaching_schedule_events(tenant_id, school_id, lower(room_name), start_at, end_at)
    WHERE room_name IS NOT NULL AND status <> 'CANCELLED';

CREATE INDEX IF NOT EXISTS idx_tset_tenant_teacher_event
    ON teaching_schedule_event_teachers(tenant_id, teacher_user_id, event_id);

CREATE INDEX IF NOT EXISTS idx_tset_tenant_event_teacher
    ON teaching_schedule_event_teachers(tenant_id, event_id, teacher_user_id);

CREATE INDEX IF NOT EXISTS idx_tsb_tenant_school_status_created
    ON teaching_schedule_batches(tenant_id, school_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_tsb_tenant_created
    ON teaching_schedule_batches(tenant_id, created_at DESC);
