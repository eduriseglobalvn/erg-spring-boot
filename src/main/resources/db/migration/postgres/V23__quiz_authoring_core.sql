-- V23__quiz_authoring_core.sql
-- Phase 0.2 of the Quiz backend (docs/0706/quiz-backend-plan.md §1.2).
-- Additive-only: adds authoring routing/version-pointer columns to the existing lms_quizzes
-- table and introduces the mutable quiz_drafts working table. The monolithic authoring columns
-- (slides/settings/result/theme/question_ids/version/package_hash) stay in place because runtime
-- services still read them; they are retired in a later phase once every consumer is repointed.

-- --- lms_quizzes: identity/routing columns for the draft/published lifecycle (all nullable so
--     existing rows validate under hibernate.hbm2ddl.auto=validate). -----------------------------
ALTER TABLE lms_quizzes ADD COLUMN IF NOT EXISTS portal varchar(255);
ALTER TABLE lms_quizzes ADD COLUMN IF NOT EXISTS subtitle varchar(255);
ALTER TABLE lms_quizzes ADD COLUMN IF NOT EXISTS category_id varchar(255);
ALTER TABLE lms_quizzes ADD COLUMN IF NOT EXISTS current_version_id varchar(36);
ALTER TABLE lms_quizzes ADD COLUMN IF NOT EXISTS latest_draft_id varchar(36);
ALTER TABLE lms_quizzes ADD COLUMN IF NOT EXISTS owner_user_id varchar(36);
ALTER TABLE lms_quizzes ADD COLUMN IF NOT EXISTS content_scope jsonb;
ALTER TABLE lms_quizzes ADD COLUMN IF NOT EXISTS schema_version integer;
ALTER TABLE lms_quizzes ADD COLUMN IF NOT EXISTS idempotency_key varchar(255);
ALTER TABLE lms_quizzes ADD COLUMN IF NOT EXISTS deleted_at timestamp with time zone;

-- Backfill routing defaults for rows created before the authoring lifecycle existed.
UPDATE lms_quizzes SET portal = 'lcms' WHERE portal IS NULL;
UPDATE lms_quizzes SET schema_version = 1 WHERE schema_version IS NULL;

-- Filterable/search indexes for the LCMS quiz workspace (§1.2).
CREATE INDEX IF NOT EXISTS idx_lms_quizzes_tenant_portal_status
    ON lms_quizzes(tenant_id, portal, status);
CREATE INDEX IF NOT EXISTS idx_lms_quizzes_tenant_taxonomy
    ON lms_quizzes(tenant_id, subject_id, level_id, category_id);
CREATE INDEX IF NOT EXISTS idx_lms_quizzes_tenant_updated
    ON lms_quizzes(tenant_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_lms_quizzes_title_fts
    ON lms_quizzes USING gin (to_tsvector('simple', coalesce(title, '')));

-- --- quiz_drafts: mutable working document, one row per active draft (§1.2). ------------------
CREATE TABLE IF NOT EXISTS quiz_drafts (
    id varchar(36) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    quiz_id varchar(36) NOT NULL,
    tenant_id varchar(255) NOT NULL,
    settings jsonb,
    player_template jsonb,
    tree jsonb,
    schema_version integer NOT NULL DEFAULT 1,
    doc_version bigint NOT NULL DEFAULT 0,
    base_version_id varchar(36),
    updated_by varchar(36)
);

CREATE INDEX IF NOT EXISTS idx_quiz_drafts_tenant_quiz
    ON quiz_drafts(tenant_id, quiz_id);
-- Bank-usage lookups scan draft trees for referenced bank question ids (§1.2 derived index).
CREATE INDEX IF NOT EXISTS idx_quiz_drafts_tree_paths
    ON quiz_drafts USING gin (tree jsonb_path_ops);
