-- Relationship integrity for the enrollment / attendance / gradebook tables (they were created without FKs).
-- NOT VALID: enforced for new and updated rows at once, existing rows are checked by V178 (the pattern of V12/V13/V34).
-- Each ADD CONSTRAINT only takes a short SHARE ROW EXCLUSIVE lock and does not scan the table.
-- Write paths already check the student/class first (LmsAcademicRecordUseCase.assertStudentInClass); nothing deletes
-- students or classes at runtime, so the default NO ACTION delete rule changes no flow.
-- lms_gradebook_scores.class_id is covered by fk_lms_gradebook_score_column; assignment_progress_aggregates.assignment_id
-- is intentionally not constrained: student_progress has no FK to assignments, so a constraint there could turn latent orphans into write failures.
ALTER TABLE lms_student_enrollments
    ADD CONSTRAINT fk_lms_student_enrollments_student
    FOREIGN KEY (student_id) REFERENCES lms_students(id)
    NOT VALID;

ALTER TABLE lms_student_enrollments
    ADD CONSTRAINT fk_lms_student_enrollments_class
    FOREIGN KEY (class_id) REFERENCES academic_classes(id)
    NOT VALID;

ALTER TABLE lms_attendance_records
    ADD CONSTRAINT fk_lms_attendance_records_student
    FOREIGN KEY (student_id) REFERENCES lms_students(id)
    NOT VALID;

ALTER TABLE lms_attendance_records
    ADD CONSTRAINT fk_lms_attendance_records_class
    FOREIGN KEY (class_id) REFERENCES academic_classes(id)
    NOT VALID;

ALTER TABLE lms_gradebook_scores
    ADD CONSTRAINT fk_lms_gradebook_scores_student
    FOREIGN KEY (student_id) REFERENCES lms_students(id)
    NOT VALID;
