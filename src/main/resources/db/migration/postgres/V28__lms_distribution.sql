-- V28__lms_distribution.sql
-- Phase 4.1 of the Quiz backend (docs/0706/quiz-backend-plan.md §1.8, §4.7, §4.8).
-- Additive: introduces the multi-quiz / flat-recipient distribution model alongside the legacy
-- single-class lms_assignments row. The legacy academic_class_id/quiz_id columns stay because ~10
-- read projections still join Student ON academic_class_id; they are retired once those projections
-- move to assignment_recipients. New assignments write BOTH the legacy columns (for the primary class)
-- and the normalized tables so old and new read paths agree during the transition. All ids varchar(36).

-- --- lms_assignments: distribution knobs added to the existing row (§1.8). ------------------------
ALTER TABLE lms_assignments ADD COLUMN IF NOT EXISTS attempt_limit integer;
ALTER TABLE lms_assignments ADD COLUMN IF NOT EXISTS max_duration_minutes integer;
ALTER TABLE lms_assignments ADD COLUMN IF NOT EXISTS start_at timestamp with time zone;
ALTER TABLE lms_assignments ADD COLUMN IF NOT EXISTS title varchar(500);
ALTER TABLE lms_assignments ADD COLUMN IF NOT EXISTS idempotency_key varchar(255);
ALTER TABLE lms_assignments ADD COLUMN IF NOT EXISTS recipient_count integer;

CREATE INDEX IF NOT EXISTS idx_lms_assignments_tenant_idempotency
    ON lms_assignments(tenant_id, idempotency_key) WHERE idempotency_key IS NOT NULL;

-- --- assignment_resources: multi-quiz per assignment, each pinning a published version (§1.8). ----
CREATE TABLE IF NOT EXISTS assignment_resources (
    assignment_id varchar(36) NOT NULL,
    quiz_id varchar(36) NOT NULL,
    tenant_id varchar(255) NOT NULL,
    quiz_version_id varchar(36),
    quiz_version_label varchar(64),
    order_index integer NOT NULL DEFAULT 0,
    created_at timestamp with time zone NOT NULL,
    PRIMARY KEY (assignment_id, quiz_id)
);

CREATE INDEX IF NOT EXISTS idx_assignment_resources_tenant_quiz
    ON assignment_resources(tenant_id, quiz_id);

-- --- assignment_recipients: flattened per-student rows regardless of recipient_mode (§1.8). -------
CREATE TABLE IF NOT EXISTS assignment_recipients (
    id varchar(36) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    assignment_id varchar(36) NOT NULL,
    tenant_id varchar(255) NOT NULL,
    student_id varchar(36) NOT NULL,
    student_user_id varchar(36),
    class_id varchar(36),
    source_mode varchar(32) NOT NULL DEFAULT 'class'
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_assignment_recipients_assignment_student
    ON assignment_recipients(assignment_id, student_id);
CREATE INDEX IF NOT EXISTS idx_assignment_recipients_tenant_student
    ON assignment_recipients(tenant_id, student_id);
CREATE INDEX IF NOT EXISTS idx_assignment_recipients_tenant_class
    ON assignment_recipients(tenant_id, class_id);

-- --- student_groups: named ad-hoc cohorts (§1.8). ------------------------------------------------
CREATE TABLE IF NOT EXISTS student_groups (
    id varchar(36) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(255) NOT NULL,
    name varchar(255),
    class_id varchar(36),
    owner_user_id varchar(36),
    student_count integer NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_student_groups_tenant
    ON student_groups(tenant_id, class_id);

CREATE TABLE IF NOT EXISTS student_group_members (
    group_id varchar(36) NOT NULL,
    student_id varchar(36) NOT NULL,
    tenant_id varchar(255) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    PRIMARY KEY (group_id, student_id)
);

CREATE INDEX IF NOT EXISTS idx_student_group_members_tenant_student
    ON student_group_members(tenant_id, student_id);
