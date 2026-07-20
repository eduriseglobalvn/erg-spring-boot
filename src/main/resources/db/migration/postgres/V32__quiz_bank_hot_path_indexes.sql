-- V32__quiz_bank_hot_path_indexes.sql
-- Hot-path indexes for LCMS question-bank / quiz-bank filters used by questionBankWorkspace.
-- These complement the existing taxonomy and FTS indexes with indexes that match the current
-- lower(..) LIKE search predicates and active non-deleted quiz-bank reads.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_lms_quizzes_lcms_bank_filters
    ON lms_quizzes(tenant_id, subject_id, level_id, category_id, lower(kind), lower(status), updated_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_lms_quizzes_title_trgm
    ON lms_quizzes USING gin (lower(coalesce(title, '')) gin_trgm_ops)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_lms_questions_bank_filters_active
    ON lms_questions(tenant_id, subject_id, level_id, topic_id, lower(status), updated_at DESC)
    WHERE archived_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_lms_questions_stem_trgm
    ON lms_questions USING gin (lower(coalesce(stem, '')) gin_trgm_ops)
    WHERE archived_at IS NULL;
