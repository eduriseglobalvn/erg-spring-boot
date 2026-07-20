-- V33__question_type_and_quiz_question_relationships.sql
-- Normalize question types into reference data and add a real quiz-question relationship table.
-- lms_quizzes.question_ids remains as a denormalized compatibility/cache column for existing APIs.

CREATE TABLE IF NOT EXISTS question_types (
    id varchar(36) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    label varchar(255) NOT NULL,
    authoring_type varchar(64) NOT NULL,
    runtime_kind varchar(64) NOT NULL,
    grading_policy varchar(32) NOT NULL DEFAULT 'auto',
    is_auto_gradable boolean NOT NULL DEFAULT true,
    sort_order integer NOT NULL DEFAULT 0,
    status varchar(32) NOT NULL DEFAULT 'ACTIVE'
);

INSERT INTO question_types (
    id,
    label,
    authoring_type,
    runtime_kind,
    grading_policy,
    is_auto_gradable,
    sort_order
)
VALUES
    ('single_choice', 'Single choice', 'multiple-choice', 'single_choice', 'auto', true, 10),
    ('multiple_choice', 'Multiple choice', 'multiple-choice', 'single_choice', 'auto', true, 11),
    ('multiple_response', 'Multiple response', 'multiple-response', 'multiple_response', 'auto', true, 20),
    ('true_false', 'True / false', 'true-false', 'true_false', 'auto', true, 30),
    ('short_answer', 'Short answer', 'short-answer', 'short_answer', 'auto', true, 40),
    ('numeric', 'Numeric', 'numeric', 'numeric', 'auto', true, 50),
    ('sequence', 'Sequence', 'sequence', 'sequence', 'auto', true, 60),
    ('matching', 'Matching', 'matching', 'matching', 'auto', true, 70),
    ('fill_blank', 'Fill in the blanks', 'fill-in-the-blanks', 'fill_blank', 'auto', true, 80),
    ('select_from_lists', 'Select from lists', 'select-from-lists', 'select_from_lists', 'auto', true, 90),
    ('drag_words', 'Drag the words', 'drag-the-words', 'drag_words', 'auto', true, 100),
    ('hotspot', 'Hotspot', 'hotspot', 'hotspot', 'auto', true, 110),
    ('drag_drop', 'Drag and drop', 'drag-and-drop', 'drag_drop', 'auto', true, 120),
    ('likert_scale', 'Likert scale', 'likert-scale', 'likert_scale', 'survey', false, 130),
    ('essay', 'Essay', 'essay', 'essay', 'manual', false, 140)
ON CONFLICT (id) DO UPDATE SET
    label = EXCLUDED.label,
    authoring_type = EXCLUDED.authoring_type,
    runtime_kind = EXCLUDED.runtime_kind,
    grading_policy = EXCLUDED.grading_policy,
    is_auto_gradable = EXCLUDED.is_auto_gradable,
    sort_order = EXCLUDED.sort_order,
    updated_at = now();

INSERT INTO users (
    id,
    created_at,
    updated_at,
    email,
    full_name,
    status,
    provider,
    account_type,
    tenant_id,
    job_title
)
VALUES
    ('seed.question.bank', now(), now(), 'seed.question.bank@erg.local', 'Question Bank Seed', 'ACTIVE', 'seed', 'seed', 'erg', 'System seed'),
    ('seed.quiz.demo', now(), now(), 'seed.quiz.demo@erg.local', 'Quiz Demo Seed', 'ACTIVE', 'seed', 'seed', 'erg', 'System seed')
ON CONFLICT (id) DO UPDATE SET
    updated_at = now(),
    full_name = EXCLUDED.full_name,
    status = EXCLUDED.status;

UPDATE lms_questions
SET type = lower(replace(type, '-', '_'))
WHERE type IS NOT NULL;

UPDATE lms_questions SET type = 'single_choice' WHERE type IN ('singlechoice', 'single_choice_question');
UPDATE lms_questions SET type = 'multiple_choice' WHERE type IN ('multiplechoice', 'multiple_choice_question');
UPDATE lms_questions SET type = 'multiple_response' WHERE type IN ('multiple_response_question', 'multiple_responses');
UPDATE lms_questions SET type = 'true_false' WHERE type IN ('truefalse', 'boolean');
UPDATE lms_questions SET type = 'short_answer' WHERE type IN ('shortanswer');
UPDATE lms_questions SET type = 'fill_blank' WHERE type IN ('fill_in_the_blanks', 'fill_blanks', 'fill_in_blank');
UPDATE lms_questions SET type = 'select_from_lists' WHERE type IN ('select_from_list');
UPDATE lms_questions SET type = 'drag_words' WHERE type IN ('drag_the_words');
UPDATE lms_questions SET type = 'drag_drop' WHERE type IN ('drag_and_drop');
UPDATE lms_questions SET type = 'likert_scale' WHERE type IN ('likert');
UPDATE lms_questions SET type = 'multiple_choice' WHERE type IS NULL OR type NOT IN (SELECT id FROM question_types);

ALTER TABLE lms_questions
    ALTER COLUMN type SET NOT NULL;

ALTER TABLE lms_questions
    ADD COLUMN IF NOT EXISTS deleted_at timestamp with time zone;

ALTER TABLE lms_questions
    ADD COLUMN IF NOT EXISTS settings jsonb;

UPDATE lms_questions
SET deleted_at = archived_at
WHERE deleted_at IS NULL
  AND archived_at IS NOT NULL;

UPDATE lms_questions question
SET owner_user_id = NULL
WHERE owner_user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM users usr WHERE usr.id = question.owner_user_id);

UPDATE lms_quizzes quiz
SET owner_user_id = NULL
WHERE owner_user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM users usr WHERE usr.id = quiz.owner_user_id);

UPDATE lms_quizzes quiz
SET category_id = NULL
WHERE category_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM lms_topics topic WHERE topic.id = quiz.category_id);

CREATE INDEX IF NOT EXISTS idx_question_types_runtime_kind
    ON question_types(runtime_kind);
CREATE INDEX IF NOT EXISTS idx_lms_questions_deleted_at
    ON lms_questions(deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_lms_quizzes_deleted_at
    ON lms_quizzes(deleted_at) WHERE deleted_at IS NOT NULL;

ALTER TABLE lms_questions
    ADD CONSTRAINT fk_lms_questions_type
    FOREIGN KEY (type) REFERENCES question_types(id)
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE lms_questions
    ADD CONSTRAINT fk_lms_questions_owner
    FOREIGN KEY (owner_user_id) REFERENCES users(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE lms_quizzes
    ADD CONSTRAINT fk_lms_quizzes_owner
    FOREIGN KEY (owner_user_id) REFERENCES users(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE lms_quizzes
    ADD CONSTRAINT fk_lms_quizzes_category
    FOREIGN KEY (category_id) REFERENCES lms_topics(id)
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE quiz_version_questions
    ADD CONSTRAINT fk_quiz_version_questions_type
    FOREIGN KEY (kind) REFERENCES question_types(id)
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE quiz_versions
    ADD CONSTRAINT fk_quiz_versions_quiz
    FOREIGN KEY (quiz_id) REFERENCES lms_quizzes(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE quiz_version_sections
    ADD CONSTRAINT fk_quiz_version_sections_version
    FOREIGN KEY (quiz_version_id) REFERENCES quiz_versions(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE quiz_version_questions
    ADD CONSTRAINT fk_quiz_version_questions_version
    FOREIGN KEY (quiz_version_id) REFERENCES quiz_versions(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE quiz_version_questions
    ADD CONSTRAINT fk_quiz_version_questions_section
    FOREIGN KEY (section_id) REFERENCES quiz_version_sections(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE quiz_version_questions
    ADD CONSTRAINT fk_quiz_version_questions_bank_question
    FOREIGN KEY (bank_question_id) REFERENCES lms_questions(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE quiz_packages
    ADD CONSTRAINT fk_quiz_packages_quiz
    FOREIGN KEY (quiz_id) REFERENCES lms_quizzes(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE quiz_packages
    ADD CONSTRAINT fk_quiz_packages_version
    FOREIGN KEY (quiz_version_id) REFERENCES quiz_versions(id)
    ON DELETE CASCADE
    NOT VALID;

CREATE TABLE IF NOT EXISTS lms_quiz_questions (
    id varchar(36) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    tenant_id varchar(255) NOT NULL,
    quiz_id varchar(36) NOT NULL,
    question_id varchar(36) NOT NULL,
    order_index integer NOT NULL DEFAULT 0,
    source varchar(32) NOT NULL DEFAULT 'manual',
    points numeric(8,2),
    settings jsonb,
    metadata jsonb
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_lms_quiz_questions_quiz_question
    ON lms_quiz_questions(tenant_id, quiz_id, question_id);
CREATE INDEX IF NOT EXISTS idx_lms_quiz_questions_quiz_order
    ON lms_quiz_questions(tenant_id, quiz_id, order_index);
CREATE INDEX IF NOT EXISTS idx_lms_quiz_questions_question
    ON lms_quiz_questions(tenant_id, question_id);

INSERT INTO lms_quiz_questions (
    id,
    created_at,
    updated_at,
    tenant_id,
    quiz_id,
    question_id,
    order_index,
    source
)
SELECT
    left(md5(quiz.tenant_id || ':' || quiz.id || ':' || refs.question_id), 36),
    now(),
    now(),
    quiz.tenant_id,
    quiz.id,
    refs.question_id,
    refs.order_index - 1,
    coalesce(quiz.settings ->> 'sourceMode', 'manual')
FROM lms_quizzes quiz
JOIN LATERAL jsonb_array_elements_text(coalesce(quiz.question_ids, '[]'::jsonb)) WITH ORDINALITY AS refs(question_id, order_index)
    ON true
JOIN lms_questions question
    ON question.id = refs.question_id
    AND question.tenant_id = quiz.tenant_id
WHERE quiz.tenant_id IS NOT NULL
ON CONFLICT (tenant_id, quiz_id, question_id) DO UPDATE SET
    order_index = EXCLUDED.order_index,
    source = EXCLUDED.source,
    updated_at = now();

ALTER TABLE lms_quiz_questions
    ADD CONSTRAINT fk_lms_quiz_questions_quiz
    FOREIGN KEY (quiz_id) REFERENCES lms_quizzes(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE lms_quiz_questions
    ADD CONSTRAINT fk_lms_quiz_questions_question
    FOREIGN KEY (question_id) REFERENCES lms_questions(id)
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE lms_questions VALIDATE CONSTRAINT fk_lms_questions_type;
ALTER TABLE lms_questions VALIDATE CONSTRAINT fk_lms_questions_owner;
ALTER TABLE lms_quizzes VALIDATE CONSTRAINT fk_lms_quizzes_owner;
ALTER TABLE lms_quizzes VALIDATE CONSTRAINT fk_lms_quizzes_category;
ALTER TABLE quiz_versions VALIDATE CONSTRAINT fk_quiz_versions_quiz;
ALTER TABLE quiz_version_sections VALIDATE CONSTRAINT fk_quiz_version_sections_version;
ALTER TABLE quiz_version_questions VALIDATE CONSTRAINT fk_quiz_version_questions_version;
ALTER TABLE quiz_version_questions VALIDATE CONSTRAINT fk_quiz_version_questions_section;
ALTER TABLE quiz_version_questions VALIDATE CONSTRAINT fk_quiz_version_questions_type;
ALTER TABLE quiz_version_questions VALIDATE CONSTRAINT fk_quiz_version_questions_bank_question;
ALTER TABLE quiz_packages VALIDATE CONSTRAINT fk_quiz_packages_quiz;
ALTER TABLE quiz_packages VALIDATE CONSTRAINT fk_quiz_packages_version;
ALTER TABLE lms_quiz_questions VALIDATE CONSTRAINT fk_lms_quiz_questions_quiz;
ALTER TABLE lms_quiz_questions VALIDATE CONSTRAINT fk_lms_quiz_questions_question;
