-- Harden the remaining quiz/runtime/distribution relationships introduced after the core V13/V16 FK pass.
-- The cleanup statements keep existing demo/dev rows migratable; future writes are then guarded by FKs.

UPDATE lms_quizzes quiz
SET current_version_id = NULL
WHERE current_version_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM quiz_versions version WHERE version.id = quiz.current_version_id);

UPDATE lms_quizzes quiz
SET latest_draft_id = NULL
WHERE latest_draft_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM quiz_drafts draft WHERE draft.id = quiz.latest_draft_id);

UPDATE lms_questions question
SET category_id = NULL
WHERE category_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM lms_topics topic WHERE topic.id = question.category_id);

DELETE FROM quiz_drafts draft
WHERE NOT EXISTS (SELECT 1 FROM lms_quizzes quiz WHERE quiz.id = draft.quiz_id);

UPDATE quiz_drafts draft
SET base_version_id = NULL
WHERE base_version_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM quiz_versions version WHERE version.id = draft.base_version_id);

UPDATE quiz_drafts draft
SET updated_by = NULL
WHERE updated_by IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM users usr WHERE usr.id = draft.updated_by);

UPDATE quiz_versions version
SET published_by = NULL
WHERE published_by IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM users usr WHERE usr.id = version.published_by);

UPDATE lms_attempts attempt
SET quiz_version_id = NULL
WHERE quiz_version_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM quiz_versions version WHERE version.id = attempt.quiz_version_id);

UPDATE lms_attempts attempt
SET class_id = NULL
WHERE class_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM academic_classes class WHERE class.id = attempt.class_id);

UPDATE lms_attempts attempt
SET school_id = NULL
WHERE school_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM education_units school WHERE school.id = attempt.school_id);

DELETE FROM attempt_events event
WHERE NOT EXISTS (SELECT 1 FROM lms_attempts attempt WHERE attempt.id = event.attempt_id);

UPDATE attempt_events event
SET quiz_id = NULL
WHERE quiz_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM lms_quizzes quiz WHERE quiz.id = event.quiz_id);

UPDATE attempt_events event
SET package_id = NULL
WHERE package_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM quiz_packages package WHERE package.id = event.package_id);

DELETE FROM attempt_answers answer
WHERE NOT EXISTS (SELECT 1 FROM lms_attempts attempt WHERE attempt.id = answer.attempt_id)
   OR NOT EXISTS (SELECT 1 FROM quiz_version_questions question WHERE question.id = answer.quiz_version_question_id);

DELETE FROM student_progress progress
WHERE NOT EXISTS (SELECT 1 FROM users usr WHERE usr.id = progress.student_user_id)
   OR NOT EXISTS (SELECT 1 FROM lms_quizzes quiz WHERE quiz.id = progress.quiz_id)
   OR (progress.assignment_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM lms_assignments assignment WHERE assignment.id = progress.assignment_id))
   OR (progress.class_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM academic_classes class WHERE class.id = progress.class_id));

DELETE FROM assignment_resources resource
WHERE NOT EXISTS (SELECT 1 FROM lms_assignments assignment WHERE assignment.id = resource.assignment_id)
   OR NOT EXISTS (SELECT 1 FROM lms_quizzes quiz WHERE quiz.id = resource.quiz_id);

UPDATE assignment_resources resource
SET quiz_version_id = NULL
WHERE quiz_version_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM quiz_versions version WHERE version.id = resource.quiz_version_id);

DELETE FROM assignment_recipients recipient
WHERE NOT EXISTS (SELECT 1 FROM lms_assignments assignment WHERE assignment.id = recipient.assignment_id)
   OR NOT EXISTS (SELECT 1 FROM lms_students student WHERE student.id = recipient.student_id);

UPDATE assignment_recipients recipient
SET student_user_id = NULL
WHERE student_user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM users usr WHERE usr.id = recipient.student_user_id);

UPDATE assignment_recipients recipient
SET class_id = NULL
WHERE class_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM academic_classes class WHERE class.id = recipient.class_id);

DELETE FROM student_groups grp
WHERE class_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM academic_classes class WHERE class.id = grp.class_id);

UPDATE student_groups grp
SET owner_user_id = NULL
WHERE owner_user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM users usr WHERE usr.id = grp.owner_user_id);

DELETE FROM student_group_members member
WHERE NOT EXISTS (SELECT 1 FROM student_groups grp WHERE grp.id = member.group_id)
   OR NOT EXISTS (SELECT 1 FROM lms_students student WHERE student.id = member.student_id);

DELETE FROM question_bank_categories category
WHERE NOT EXISTS (SELECT 1 FROM question_bank_subjects subject WHERE subject.id = category.subject_id)
   OR NOT EXISTS (SELECT 1 FROM question_bank_levels level WHERE level.id = category.level_id);

DELETE FROM question_bank_levels level
WHERE NOT EXISTS (SELECT 1 FROM question_bank_subjects subject WHERE subject.id = level.subject_id);

UPDATE quiz_bank_items item
SET owner_user_id = NULL
WHERE owner_user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM users usr WHERE usr.id = item.owner_user_id);

UPDATE quiz_bank_items item
SET subject_id = NULL
WHERE subject_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM question_bank_subjects subject WHERE subject.id = item.subject_id);

UPDATE quiz_bank_items item
SET level_id = NULL
WHERE level_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM question_bank_levels level WHERE level.id = item.level_id);

ALTER TABLE lms_quizzes
    ADD CONSTRAINT fk_lms_quizzes_current_version
    FOREIGN KEY (current_version_id) REFERENCES quiz_versions(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE lms_quizzes
    ADD CONSTRAINT fk_lms_quizzes_latest_draft
    FOREIGN KEY (latest_draft_id) REFERENCES quiz_drafts(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE lms_questions
    ADD CONSTRAINT fk_lms_questions_category
    FOREIGN KEY (category_id) REFERENCES lms_topics(id)
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE quiz_drafts
    ADD CONSTRAINT fk_quiz_drafts_quiz
    FOREIGN KEY (quiz_id) REFERENCES lms_quizzes(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE quiz_drafts
    ADD CONSTRAINT fk_quiz_drafts_base_version
    FOREIGN KEY (base_version_id) REFERENCES quiz_versions(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE quiz_drafts
    ADD CONSTRAINT fk_quiz_drafts_updated_by
    FOREIGN KEY (updated_by) REFERENCES users(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE quiz_versions
    ADD CONSTRAINT fk_quiz_versions_published_by
    FOREIGN KEY (published_by) REFERENCES users(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE lms_attempts
    ADD CONSTRAINT fk_lms_attempts_quiz_version
    FOREIGN KEY (quiz_version_id) REFERENCES quiz_versions(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE lms_attempts
    ADD CONSTRAINT fk_lms_attempts_class_context
    FOREIGN KEY (class_id) REFERENCES academic_classes(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE lms_attempts
    ADD CONSTRAINT fk_lms_attempts_school_context
    FOREIGN KEY (school_id) REFERENCES education_units(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE attempt_events
    ADD CONSTRAINT fk_attempt_events_attempt
    FOREIGN KEY (attempt_id) REFERENCES lms_attempts(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE attempt_events
    ADD CONSTRAINT fk_attempt_events_quiz
    FOREIGN KEY (quiz_id) REFERENCES lms_quizzes(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE attempt_events
    ADD CONSTRAINT fk_attempt_events_package
    FOREIGN KEY (package_id) REFERENCES quiz_packages(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE attempt_answers
    ADD CONSTRAINT fk_attempt_answers_attempt
    FOREIGN KEY (attempt_id) REFERENCES lms_attempts(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE attempt_answers
    ADD CONSTRAINT fk_attempt_answers_version_question
    FOREIGN KEY (quiz_version_question_id) REFERENCES quiz_version_questions(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE student_progress
    ADD CONSTRAINT fk_student_progress_student_user
    FOREIGN KEY (student_user_id) REFERENCES users(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE student_progress
    ADD CONSTRAINT fk_student_progress_quiz
    FOREIGN KEY (quiz_id) REFERENCES lms_quizzes(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE student_progress
    ADD CONSTRAINT fk_student_progress_assignment
    FOREIGN KEY (assignment_id) REFERENCES lms_assignments(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE student_progress
    ADD CONSTRAINT fk_student_progress_class
    FOREIGN KEY (class_id) REFERENCES academic_classes(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE assignment_resources
    ADD CONSTRAINT fk_assignment_resources_assignment
    FOREIGN KEY (assignment_id) REFERENCES lms_assignments(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE assignment_resources
    ADD CONSTRAINT fk_assignment_resources_quiz
    FOREIGN KEY (quiz_id) REFERENCES lms_quizzes(id)
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE assignment_resources
    ADD CONSTRAINT fk_assignment_resources_quiz_version
    FOREIGN KEY (quiz_version_id) REFERENCES quiz_versions(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE assignment_recipients
    ADD CONSTRAINT fk_assignment_recipients_assignment
    FOREIGN KEY (assignment_id) REFERENCES lms_assignments(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE assignment_recipients
    ADD CONSTRAINT fk_assignment_recipients_student
    FOREIGN KEY (student_id) REFERENCES lms_students(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE assignment_recipients
    ADD CONSTRAINT fk_assignment_recipients_student_user
    FOREIGN KEY (student_user_id) REFERENCES users(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE assignment_recipients
    ADD CONSTRAINT fk_assignment_recipients_class
    FOREIGN KEY (class_id) REFERENCES academic_classes(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE student_groups
    ADD CONSTRAINT fk_student_groups_class
    FOREIGN KEY (class_id) REFERENCES academic_classes(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE student_groups
    ADD CONSTRAINT fk_student_groups_owner
    FOREIGN KEY (owner_user_id) REFERENCES users(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE student_group_members
    ADD CONSTRAINT fk_student_group_members_group
    FOREIGN KEY (group_id) REFERENCES student_groups(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE student_group_members
    ADD CONSTRAINT fk_student_group_members_student
    FOREIGN KEY (student_id) REFERENCES lms_students(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE question_bank_levels
    ADD CONSTRAINT fk_question_bank_levels_subject
    FOREIGN KEY (subject_id) REFERENCES question_bank_subjects(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE question_bank_categories
    ADD CONSTRAINT fk_question_bank_categories_subject
    FOREIGN KEY (subject_id) REFERENCES question_bank_subjects(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE question_bank_categories
    ADD CONSTRAINT fk_question_bank_categories_level
    FOREIGN KEY (level_id) REFERENCES question_bank_levels(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE quiz_bank_items
    ADD CONSTRAINT fk_quiz_bank_items_subject
    FOREIGN KEY (subject_id) REFERENCES question_bank_subjects(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE quiz_bank_items
    ADD CONSTRAINT fk_quiz_bank_items_level
    FOREIGN KEY (level_id) REFERENCES question_bank_levels(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE quiz_bank_items
    ADD CONSTRAINT fk_quiz_bank_items_owner
    FOREIGN KEY (owner_user_id) REFERENCES users(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE lms_quizzes VALIDATE CONSTRAINT fk_lms_quizzes_current_version;
ALTER TABLE lms_quizzes VALIDATE CONSTRAINT fk_lms_quizzes_latest_draft;
ALTER TABLE lms_questions VALIDATE CONSTRAINT fk_lms_questions_category;
ALTER TABLE quiz_drafts VALIDATE CONSTRAINT fk_quiz_drafts_quiz;
ALTER TABLE quiz_drafts VALIDATE CONSTRAINT fk_quiz_drafts_base_version;
ALTER TABLE quiz_drafts VALIDATE CONSTRAINT fk_quiz_drafts_updated_by;
ALTER TABLE quiz_versions VALIDATE CONSTRAINT fk_quiz_versions_published_by;
ALTER TABLE lms_attempts VALIDATE CONSTRAINT fk_lms_attempts_quiz_version;
ALTER TABLE lms_attempts VALIDATE CONSTRAINT fk_lms_attempts_class_context;
ALTER TABLE lms_attempts VALIDATE CONSTRAINT fk_lms_attempts_school_context;
ALTER TABLE attempt_events VALIDATE CONSTRAINT fk_attempt_events_attempt;
ALTER TABLE attempt_events VALIDATE CONSTRAINT fk_attempt_events_quiz;
ALTER TABLE attempt_events VALIDATE CONSTRAINT fk_attempt_events_package;
ALTER TABLE attempt_answers VALIDATE CONSTRAINT fk_attempt_answers_attempt;
ALTER TABLE attempt_answers VALIDATE CONSTRAINT fk_attempt_answers_version_question;
ALTER TABLE student_progress VALIDATE CONSTRAINT fk_student_progress_student_user;
ALTER TABLE student_progress VALIDATE CONSTRAINT fk_student_progress_quiz;
ALTER TABLE student_progress VALIDATE CONSTRAINT fk_student_progress_assignment;
ALTER TABLE student_progress VALIDATE CONSTRAINT fk_student_progress_class;
ALTER TABLE assignment_resources VALIDATE CONSTRAINT fk_assignment_resources_assignment;
ALTER TABLE assignment_resources VALIDATE CONSTRAINT fk_assignment_resources_quiz;
ALTER TABLE assignment_resources VALIDATE CONSTRAINT fk_assignment_resources_quiz_version;
ALTER TABLE assignment_recipients VALIDATE CONSTRAINT fk_assignment_recipients_assignment;
ALTER TABLE assignment_recipients VALIDATE CONSTRAINT fk_assignment_recipients_student;
ALTER TABLE assignment_recipients VALIDATE CONSTRAINT fk_assignment_recipients_student_user;
ALTER TABLE assignment_recipients VALIDATE CONSTRAINT fk_assignment_recipients_class;
ALTER TABLE student_groups VALIDATE CONSTRAINT fk_student_groups_class;
ALTER TABLE student_groups VALIDATE CONSTRAINT fk_student_groups_owner;
ALTER TABLE student_group_members VALIDATE CONSTRAINT fk_student_group_members_group;
ALTER TABLE student_group_members VALIDATE CONSTRAINT fk_student_group_members_student;
ALTER TABLE question_bank_levels VALIDATE CONSTRAINT fk_question_bank_levels_subject;
ALTER TABLE question_bank_categories VALIDATE CONSTRAINT fk_question_bank_categories_subject;
ALTER TABLE question_bank_categories VALIDATE CONSTRAINT fk_question_bank_categories_level;
ALTER TABLE quiz_bank_items VALIDATE CONSTRAINT fk_quiz_bank_items_subject;
ALTER TABLE quiz_bank_items VALIDATE CONSTRAINT fk_quiz_bank_items_level;
ALTER TABLE quiz_bank_items VALIDATE CONSTRAINT fk_quiz_bank_items_owner;
