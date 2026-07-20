CREATE INDEX IF NOT EXISTS idx_lms_levels_subject_id ON lms_levels(subject_id);
CREATE INDEX IF NOT EXISTS idx_lms_topics_level_id ON lms_topics(level_id);
CREATE INDEX IF NOT EXISTS idx_lms_questions_subject_id ON lms_questions(subject_id);
CREATE INDEX IF NOT EXISTS idx_lms_questions_level_id ON lms_questions(level_id);
CREATE INDEX IF NOT EXISTS idx_lms_questions_topic_id ON lms_questions(topic_id);
CREATE INDEX IF NOT EXISTS idx_lms_quizzes_level_id ON lms_quizzes(level_id);

ALTER TABLE academic_classes
    ADD CONSTRAINT fk_academic_classes_school
    FOREIGN KEY (school_id) REFERENCES education_units(id)
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE academic_classes
    ADD CONSTRAINT fk_academic_classes_teacher
    FOREIGN KEY (homeroom_teacher_id) REFERENCES users(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE lms_assignments
    ADD CONSTRAINT fk_lms_assignments_class
    FOREIGN KEY (academic_class_id) REFERENCES academic_classes(id)
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE lms_assignments
    ADD CONSTRAINT fk_lms_assignments_quiz
    FOREIGN KEY (quiz_id) REFERENCES lms_quizzes(id)
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE lms_assignments
    ADD CONSTRAINT fk_lms_assignments_subject
    FOREIGN KEY (subject_id) REFERENCES lms_subjects(id)
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE lms_assignments
    ADD CONSTRAINT fk_lms_assignments_assigned_by
    FOREIGN KEY (assigned_by) REFERENCES users(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE lms_levels
    ADD CONSTRAINT fk_lms_levels_subject
    FOREIGN KEY (subject_id) REFERENCES lms_subjects(id)
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE lms_topics
    ADD CONSTRAINT fk_lms_topics_level
    FOREIGN KEY (level_id) REFERENCES lms_levels(id)
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE lms_questions
    ADD CONSTRAINT fk_lms_questions_subject
    FOREIGN KEY (subject_id) REFERENCES lms_subjects(id)
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE lms_questions
    ADD CONSTRAINT fk_lms_questions_level
    FOREIGN KEY (level_id) REFERENCES lms_levels(id)
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE lms_questions
    ADD CONSTRAINT fk_lms_questions_topic
    FOREIGN KEY (topic_id) REFERENCES lms_topics(id)
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE lms_quizzes
    ADD CONSTRAINT fk_lms_quizzes_subject
    FOREIGN KEY (subject_id) REFERENCES lms_subjects(id)
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE lms_quizzes
    ADD CONSTRAINT fk_lms_quizzes_level
    FOREIGN KEY (level_id) REFERENCES lms_levels(id)
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE lms_students
    ADD CONSTRAINT fk_lms_students_school
    FOREIGN KEY (school_id) REFERENCES education_units(id)
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE lms_students
    ADD CONSTRAINT fk_lms_students_class
    FOREIGN KEY (academic_class_id) REFERENCES academic_classes(id)
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE lms_students
    ADD CONSTRAINT fk_lms_students_auth_user
    FOREIGN KEY (auth_user_id) REFERENCES users(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE lms_attempts
    ADD CONSTRAINT fk_lms_attempts_assignment
    FOREIGN KEY (assignment_id) REFERENCES lms_assignments(id)
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE lms_attempts
    ADD CONSTRAINT fk_lms_attempts_quiz
    FOREIGN KEY (quiz_id) REFERENCES lms_quizzes(id)
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE lms_attempts
    ADD CONSTRAINT fk_lms_attempts_student
    FOREIGN KEY (student_id) REFERENCES lms_students(id)
    ON DELETE RESTRICT
    NOT VALID;
