-- Validates the V175 constraints. VALIDATE CONSTRAINT scans under SHARE UPDATE EXCLUSIVE, so reads and writes continue.
-- If orphan rows exist the constraint stays NOT VALID (still enforced for new rows) and a warning is logged instead of
-- failing the deploy: find them with
--   SELECT count(*) FROM lms_attendance_records r WHERE NOT EXISTS (SELECT 1 FROM lms_students s WHERE s.id = r.student_id);
-- (same shape for the other four), clean them up, then run the ALTER TABLE ... VALIDATE CONSTRAINT by hand.
DO $$
DECLARE
    c record;
BEGIN
    FOR c IN SELECT * FROM (VALUES
        ('lms_student_enrollments', 'fk_lms_student_enrollments_student'),
        ('lms_student_enrollments', 'fk_lms_student_enrollments_class'),
        ('lms_attendance_records', 'fk_lms_attendance_records_student'),
        ('lms_attendance_records', 'fk_lms_attendance_records_class'),
        ('lms_gradebook_scores', 'fk_lms_gradebook_scores_student')) AS v(tbl, con)
    LOOP
        BEGIN
            EXECUTE format('ALTER TABLE %I VALIDATE CONSTRAINT %I', c.tbl, c.con);
        EXCEPTION WHEN foreign_key_violation THEN
            RAISE WARNING 'constraint % on % left NOT VALID: orphan rows exist', c.con, c.tbl;
        END;
    END LOOP;
END;
$$;
