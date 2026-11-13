-- Student discussions: teacher pin, chosen answer, subject, follow. Inbox: student <-> teacher thread tied to one question.

ALTER TABLE student_discussion_threads
    ADD COLUMN IF NOT EXISTS subject varchar(64),
    ADD COLUMN IF NOT EXISTS pinned_at timestamp with time zone,
    ADD COLUMN IF NOT EXISTS pinned_by varchar(36),
    ADD COLUMN IF NOT EXISTS accepted_comment_id varchar(36);

CREATE TABLE IF NOT EXISTS student_discussion_follows (
    tenant_id  varchar(255) NOT NULL,
    post_id    varchar(36)  NOT NULL REFERENCES student_discussion_threads(id) ON DELETE CASCADE,
    user_id    varchar(36)  NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, post_id, user_id)
);

CREATE TABLE IF NOT EXISTS student_question_threads (
    id                  varchar(36) PRIMARY KEY,
    tenant_id           varchar(255) NOT NULL,
    student_id          varchar(36)  NOT NULL,
    student_user_id     varchar(36)  NOT NULL,
    academic_class_id   varchar(36)  NOT NULL,
    teacher_id          varchar(36)  NOT NULL,
    assignment_id       varchar(36)  NOT NULL,
    question_index      integer      NOT NULL,
    question_prompt     text,
    student_answer_text text,
    status              varchar(12)  NOT NULL DEFAULT 'OPEN',
    idempotency_key     varchar(192) NOT NULL,
    last_message_at     timestamp with time zone NOT NULL,
    created_at          timestamp with time zone NOT NULL,
    CONSTRAINT chk_student_question_thread_status CHECK (status IN ('OPEN', 'ANSWERED')),
    CONSTRAINT chk_student_question_thread_index CHECK (question_index >= 1)
);

CREATE TABLE IF NOT EXISTS student_question_messages (
    id             varchar(36) PRIMARY KEY,
    tenant_id      varchar(255) NOT NULL,
    thread_id      varchar(36)  NOT NULL REFERENCES student_question_threads(id) ON DELETE CASCADE,
    sender_id      varchar(36)  NOT NULL,
    sender_role    varchar(8)   NOT NULL,
    body           text         NOT NULL,
    attachment_ids jsonb        NOT NULL DEFAULT '[]'::jsonb,
    created_at     timestamp with time zone NOT NULL,
    CONSTRAINT chk_student_question_message_role CHECK (sender_role IN ('STUDENT', 'TEACHER'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_student_question_thread_idempotency
    ON student_question_threads(tenant_id, student_user_id, idempotency_key);
CREATE INDEX IF NOT EXISTS idx_student_question_thread_student
    ON student_question_threads(tenant_id, student_user_id, last_message_at DESC);
CREATE INDEX IF NOT EXISTS idx_student_question_thread_teacher
    ON student_question_threads(tenant_id, teacher_id, status, last_message_at DESC);
CREATE INDEX IF NOT EXISTS idx_student_question_messages_thread
    ON student_question_messages(tenant_id, thread_id, created_at);

ALTER TABLE student_discussion_follows ENABLE ROW LEVEL SECURITY;
ALTER TABLE student_question_threads ENABLE ROW LEVEL SECURITY;
ALTER TABLE student_question_messages ENABLE ROW LEVEL SECURITY;

CREATE POLICY student_discussion_follows_tenant_isolation ON student_discussion_follows
    USING (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''));
CREATE POLICY student_question_threads_tenant_isolation ON student_question_threads
    USING (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''));
CREATE POLICY student_question_messages_tenant_isolation ON student_question_messages
    USING (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (current_setting('app.maintenance_mode', true) = 'true' OR tenant_id = nullif(current_setting('app.tenant_id', true), ''));

-- Tenant-leading indexes (the tenant migration policy test looks for an explicit one).
CREATE INDEX IF NOT EXISTS idx_student_discussion_follows_tenant ON student_discussion_follows (tenant_id);
