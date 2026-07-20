-- V24__question_bank_formalize.sql
-- Phase 0.3 of the Quiz backend (docs/0706/quiz-backend-plan.md §1.5).
-- Additive-only: enriches the existing lms_questions table with question-bank authoring columns and
-- introduces the bank taxonomy tables plus quiz_bank_items. The running LcmsQuestionBankWorkspace
-- query is left untouched here; the new columns are consumed by later phases. Every added column is
-- nullable/defaulted so existing rows validate under hibernate.hbm2ddl.auto=validate.

-- --- lms_questions: question-bank authoring columns (§1.5). ------------------------------------
ALTER TABLE lms_questions ADD COLUMN IF NOT EXISTS category_id varchar(255);
ALTER TABLE lms_questions ADD COLUMN IF NOT EXISTS objective varchar(2000);
ALTER TABLE lms_questions ADD COLUMN IF NOT EXISTS difficulty varchar(64);
ALTER TABLE lms_questions ADD COLUMN IF NOT EXISTS tags jsonb;
ALTER TABLE lms_questions ADD COLUMN IF NOT EXISTS content jsonb;
ALTER TABLE lms_questions ADD COLUMN IF NOT EXISTS mastery_rate double precision;
ALTER TABLE lms_questions ADD COLUMN IF NOT EXISTS usage_count integer;
ALTER TABLE lms_questions ADD COLUMN IF NOT EXISTS schools_using integer;
ALTER TABLE lms_questions ADD COLUMN IF NOT EXISTS owner_user_id varchar(36);
ALTER TABLE lms_questions ADD COLUMN IF NOT EXISTS schema_version integer;

UPDATE lms_questions SET usage_count = 0 WHERE usage_count IS NULL;
UPDATE lms_questions SET schema_version = 1 WHERE schema_version IS NULL;

-- Bank filter/search indexes (§1.5): taxonomy filter, type/status facet, tag containment, stem FTS.
CREATE INDEX IF NOT EXISTS idx_lms_questions_tenant_taxonomy
    ON lms_questions(tenant_id, subject_id, level_id, category_id);
CREATE INDEX IF NOT EXISTS idx_lms_questions_tenant_type_status
    ON lms_questions(tenant_id, type, status);
CREATE INDEX IF NOT EXISTS idx_lms_questions_tags
    ON lms_questions USING gin (tags jsonb_path_ops);
CREATE INDEX IF NOT EXISTS idx_lms_questions_stem_fts
    ON lms_questions USING gin (to_tsvector('simple', coalesce(stem, '')));

-- --- question bank taxonomy (§1.5). -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS question_bank_subjects (
    id varchar(36) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(255) NOT NULL,
    label varchar(255),
    description varchar(2000),
    company_scope_label varchar(255)
);

CREATE TABLE IF NOT EXISTS question_bank_levels (
    id varchar(36) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(255) NOT NULL,
    subject_id varchar(36) NOT NULL,
    label varchar(255),
    description varchar(2000),
    order_index integer
);

CREATE TABLE IF NOT EXISTS question_bank_categories (
    id varchar(36) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(255) NOT NULL,
    subject_id varchar(36) NOT NULL,
    level_id varchar(36) NOT NULL,
    label varchar(255)
);

CREATE INDEX IF NOT EXISTS idx_question_bank_subjects_tenant
    ON question_bank_subjects(tenant_id, label);
CREATE INDEX IF NOT EXISTS idx_question_bank_levels_tenant_subject
    ON question_bank_levels(tenant_id, subject_id, order_index);
CREATE INDEX IF NOT EXISTS idx_question_bank_categories_tenant_scope
    ON question_bank_categories(tenant_id, subject_id, level_id);

-- --- quiz_bank_items: static hand-built / auto-random quiz packages (§1.5). --------------------
CREATE TABLE IF NOT EXISTS quiz_bank_items (
    id varchar(36) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(255) NOT NULL,
    title varchar(500),
    kind varchar(64),
    status varchar(64),
    subject_id varchar(36),
    level_id varchar(36),
    question_ids jsonb,
    source_mode varchar(64),
    duration_label varchar(128),
    owner_user_id varchar(36)
);

CREATE INDEX IF NOT EXISTS idx_quiz_bank_items_tenant_subject_level
    ON quiz_bank_items(tenant_id, subject_id, level_id);
CREATE INDEX IF NOT EXISTS idx_quiz_bank_items_questions
    ON quiz_bank_items USING gin (question_ids jsonb_path_ops);
