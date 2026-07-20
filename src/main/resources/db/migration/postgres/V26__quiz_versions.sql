-- V26__quiz_versions.sql
-- Phase 2.1 of the Quiz backend (docs/0706/quiz-backend-plan.md §1.3, §1.4).
-- Immutable published lineage: a publish freezes an authoring snapshot into quiz_versions, compiles a
-- normalized runtime question set (quiz_version_sections + quiz_version_questions) and emits a signed
-- quiz_packages row. These tables are INSERT-only; the sole UPDATE is flipping is_active on quiz_versions.
-- All ids varchar(36); enums stored as text so a new question kind is a data change, not a DDL change.

-- --- quiz_versions: frozen publish snapshot (§1.3). ------------------------------------------------
CREATE TABLE IF NOT EXISTS quiz_versions (
    id varchar(36) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    quiz_id varchar(36) NOT NULL,
    tenant_id varchar(255) NOT NULL,
    version_label varchar(64) NOT NULL,
    version_number integer NOT NULL,
    authoring_snapshot jsonb,
    runtime_quiz jsonb,
    answer_key jsonb,
    grading_mode varchar(32) NOT NULL DEFAULT 'client-first',
    schema_version integer NOT NULL DEFAULT 1,
    content_hash varchar(128),
    published_by varchar(36),
    published_at timestamp with time zone,
    is_active boolean NOT NULL DEFAULT false,
    idempotency_key varchar(255)
);

-- Version numbering / labels are unique per quiz; only one active version per quiz at a time.
CREATE UNIQUE INDEX IF NOT EXISTS uq_quiz_versions_quiz_number
    ON quiz_versions(quiz_id, version_number);
CREATE UNIQUE INDEX IF NOT EXISTS uq_quiz_versions_quiz_label
    ON quiz_versions(quiz_id, version_label);
CREATE UNIQUE INDEX IF NOT EXISTS uq_quiz_versions_active
    ON quiz_versions(quiz_id) WHERE is_active;
CREATE INDEX IF NOT EXISTS idx_quiz_versions_tenant_quiz
    ON quiz_versions(tenant_id, quiz_id, version_number DESC);

-- --- quiz_version_sections: runtime sections compiled from groups (§1.4). --------------------------
CREATE TABLE IF NOT EXISTS quiz_version_sections (
    id varchar(36) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    quiz_version_id varchar(36) NOT NULL,
    tenant_id varchar(255) NOT NULL,
    title varchar(500),
    order_index integer NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_quiz_version_sections_version
    ON quiz_version_sections(quiz_version_id, order_index);

-- --- quiz_version_questions: normalized published questions for report/grading (§1.4). -------------
CREATE TABLE IF NOT EXISTS quiz_version_questions (
    id varchar(36) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    quiz_version_id varchar(36) NOT NULL,
    section_id varchar(36),
    tenant_id varchar(255) NOT NULL,
    runtime_question_id varchar(64) NOT NULL,
    source_slide_id varchar(64),
    bank_question_id varchar(36),
    kind varchar(64) NOT NULL,
    authoring_type varchar(64),
    points numeric(8,2) NOT NULL DEFAULT 0,
    is_auto_gradable boolean NOT NULL DEFAULT true,
    grading_policy varchar(32) NOT NULL DEFAULT 'auto',
    order_index integer NOT NULL DEFAULT 0,
    body jsonb,
    answer_key jsonb
);

CREATE INDEX IF NOT EXISTS idx_qvq_version_order
    ON quiz_version_questions(quiz_version_id, order_index);
CREATE INDEX IF NOT EXISTS idx_qvq_kind
    ON quiz_version_questions(kind);
CREATE INDEX IF NOT EXISTS idx_qvq_bank_question
    ON quiz_version_questions(bank_question_id) WHERE bank_question_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_qvq_version_autogradable
    ON quiz_version_questions(quiz_version_id, is_auto_gradable);

-- --- quiz_packages: compiled + signed runtime package (§1.3). --------------------------------------
CREATE TABLE IF NOT EXISTS quiz_packages (
    id varchar(128) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    quiz_id varchar(36) NOT NULL,
    quiz_version_id varchar(36) NOT NULL,
    quiz_version varchar(64),
    tenant_id varchar(255) NOT NULL,
    content_hash varchar(128),
    signature varchar(1000),
    public_key_id varchar(128),
    grading_mode varchar(32) NOT NULL DEFAULT 'client-first',
    schema_version integer NOT NULL DEFAULT 1,
    package_format varchar(32) NOT NULL DEFAULT 'single',
    assets_manifest jsonb,
    package_json jsonb,
    generated_at timestamp with time zone,
    expires_at timestamp with time zone
);

CREATE INDEX IF NOT EXISTS idx_quiz_packages_quiz_version
    ON quiz_packages(quiz_id, quiz_version);
CREATE INDEX IF NOT EXISTS idx_quiz_packages_tenant_quiz
    ON quiz_packages(tenant_id, quiz_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_quiz_packages_version
    ON quiz_packages(quiz_version_id);
