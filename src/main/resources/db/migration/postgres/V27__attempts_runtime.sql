-- V27__attempts_runtime.sql
-- Phase 3.1 of the Quiz backend (docs/0706/quiz-backend-plan.md §1.7).
-- Additive: introduces the normalized runtime tables (idempotent attempt_events, per-question
-- attempt_answers, live student_progress projection) alongside the existing lms_attempts row, and
-- pins each attempt to a quiz_version. The embedded answers/events jsonb on lms_attempts stays for now
-- because several read projections still consume it; it is retired once those move. All ids varchar(36).

-- --- lms_attempts: pin to the published version that produced the package (§1.7). -----------------
ALTER TABLE lms_attempts ADD COLUMN IF NOT EXISTS quiz_version_id varchar(36);
ALTER TABLE lms_attempts ADD COLUMN IF NOT EXISTS class_id varchar(36);
ALTER TABLE lms_attempts ADD COLUMN IF NOT EXISTS school_id varchar(36);
ALTER TABLE lms_attempts ADD COLUMN IF NOT EXISTS mode varchar(32);
ALTER TABLE lms_attempts ADD COLUMN IF NOT EXISTS grading_mode varchar(32);
ALTER TABLE lms_attempts ADD COLUMN IF NOT EXISTS submitted_count integer;
ALTER TABLE lms_attempts ADD COLUMN IF NOT EXISTS total_questions integer;
ALTER TABLE lms_attempts ADD COLUMN IF NOT EXISTS last_active_at timestamp with time zone;
ALTER TABLE lms_attempts ADD COLUMN IF NOT EXISTS client_runtime varchar(32);

CREATE INDEX IF NOT EXISTS idx_lms_attempts_tenant_version
    ON lms_attempts(tenant_id, quiz_id, quiz_version_id);

-- --- attempt_events: append-only, idempotent by event id (§1.7). ----------------------------------
CREATE TABLE IF NOT EXISTS attempt_events (
    id varchar(36) PRIMARY KEY,
    attempt_id varchar(36) NOT NULL,
    tenant_id varchar(255) NOT NULL,
    quiz_id varchar(36),
    package_id varchar(128),
    type varchar(48) NOT NULL,
    question_id varchar(64),
    answer jsonb,
    result jsonb,
    meta jsonb,
    created_at timestamp with time zone,
    received_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_attempt_events_attempt_created
    ON attempt_events(attempt_id, created_at);

-- --- attempt_answers: normalized per-question grading result (report source, §1.7). ----------------
CREATE TABLE IF NOT EXISTS attempt_answers (
    attempt_id varchar(36) NOT NULL,
    quiz_version_question_id varchar(36) NOT NULL,
    tenant_id varchar(255) NOT NULL,
    question_kind varchar(64),
    raw_answer jsonb,
    correct boolean,
    partially_right boolean NOT NULL DEFAULT false,
    awarded_points numeric(8,2) NOT NULL DEFAULT 0,
    max_points numeric(8,2) NOT NULL DEFAULT 0,
    graded_by varchar(16),
    graded_at timestamp with time zone,
    needs_manual_grade boolean NOT NULL DEFAULT false,
    PRIMARY KEY (attempt_id, quiz_version_question_id)
);

CREATE INDEX IF NOT EXISTS idx_attempt_answers_question
    ON attempt_answers(quiz_version_question_id);
CREATE INDEX IF NOT EXISTS idx_attempt_answers_attempt
    ON attempt_answers(attempt_id);

-- --- student_progress: live projection for the teacher dashboard (§1.7). --------------------------
CREATE TABLE IF NOT EXISTS student_progress (
    id varchar(36) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(255) NOT NULL,
    student_user_id varchar(36) NOT NULL,
    student_name varchar(500),
    quiz_id varchar(36) NOT NULL,
    quiz_title varchar(500),
    assignment_id varchar(36),
    class_id varchar(36),
    mode varchar(32),
    status varchar(32),
    current_question_index integer,
    current_question_title varchar(500),
    answered_count integer NOT NULL DEFAULT 0,
    total_questions integer NOT NULL DEFAULT 0,
    percent_complete numeric(6,2),
    score numeric(8,2),
    passed boolean,
    started_at timestamp with time zone,
    submitted_at timestamp with time zone,
    last_active_at timestamp with time zone
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_student_progress_attempt_scope
    ON student_progress(tenant_id, student_user_id, quiz_id, assignment_id);
CREATE INDEX IF NOT EXISTS idx_student_progress_quiz_status
    ON student_progress(quiz_id, status);
CREATE INDEX IF NOT EXISTS idx_student_progress_class_quiz
    ON student_progress(class_id, quiz_id);
