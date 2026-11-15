-- V153__lesson_log_entries.sql
-- Electronic class log (so dau bai), KE_HOACH_CONG_BAO_GIANG.md 4.1. One row per taught period of a published event.
-- Ids are VARCHAR like the rest of the schema (the plan wrote UUID); `version` is the JPA @Version counter.
CREATE TABLE lms_lesson_log_entries (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(255) NOT NULL,
    event_id        VARCHAR(36) NOT NULL REFERENCES teaching_schedule_events (id),
    lesson_date     DATE NOT NULL,
    period_no       SMALLINT NOT NULL CHECK (period_no BETWEEN 1 AND 20),
    class_id        VARCHAR(100) NOT NULL,
    subject_id      VARCHAR(100) NOT NULL,
    teacher_user_id VARCHAR(36) NOT NULL,
    ppct_period     INTEGER NOT NULL CHECK (ppct_period >= 1),
    actual_period   INTEGER NOT NULL CHECK (actual_period >= 1),
    comment         VARCHAR(500),
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'CONFIRMED')),
    confirmed_at    TIMESTAMPTZ,
    confirmed_by    VARCHAR(36),
    late            BOOLEAN NOT NULL DEFAULT FALSE,
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_lms_lesson_log_period UNIQUE (tenant_id, event_id, lesson_date, period_no)
);
CREATE INDEX idx_lms_lesson_log_teacher_date ON lms_lesson_log_entries (tenant_id, teacher_user_id, lesson_date);
CREATE INDEX idx_lms_lesson_log_class_date ON lms_lesson_log_entries (tenant_id, class_id, subject_id, lesson_date);

ALTER TABLE lms_lesson_log_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE lms_lesson_log_entries FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS lms_lesson_log_entries_tenant_isolation ON lms_lesson_log_entries;
CREATE POLICY lms_lesson_log_entries_tenant_isolation ON lms_lesson_log_entries
    USING (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''));
CREATE INDEX IF NOT EXISTS idx_lms_lesson_log_entries_tenant ON lms_lesson_log_entries (tenant_id);
