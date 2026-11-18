-- ENV-001: a brand-new production database must hold only admin@erg.edu.vn and the system catalogs
-- (roles, permissions, question types, help FAQs, default period template, ctr_system). V4/V11/V21/
-- V33/V68/V102 seed demo accounts, schools, classes, a calendar, curriculum and quizzes everywhere,
-- and V125 only removes part of them and only on an opt-in flag nobody set.
--
-- This migration removes ALL of that demo data, but it can never touch a database that is in use.
-- It does nothing unless every one of these holds:
--   1. FlywayConfig asked for it: "SET erg.fresh_install_purge = 'true'" (default on for the prod
--      profile, env ERG_FLYWAY_FRESH_INSTALL_PURGE; off for dev/tests, which rely on the seed rows).
--      The flag is a session setting, so the checksum is the same either way.
--   2. Every user id is one of the 13 migration seed ids: nobody has ever created an account.
--   3. Nobody has ever logged in (no login_count, no last_login_at, no user_sessions row).
--   4. No table to be cleared holds more rows than the migrations themselves seed, and the tables
--      that only real use fills (attempts, employees, role assignments, CRM, school years) are empty.
-- A dev/test/production database that has been used fails 2, 3 and 4 at once; then this is a logged
-- no-op (never an error, so it cannot block a deploy). Nothing is quarantined: every removed row is a
-- seed row that the earlier migrations reproduce on any scratch database.
DO $$
DECLARE
    -- table, seed row count on a fresh install; children before parents
    purge CONSTANT text[][] := ARRAY[
        ['curriculum_teaching_plan_settings', '1'],
        ['teaching_schedule_event_teachers', '288'],
        ['teaching_schedule_events', '120'],
        ['teaching_schedule_batches', '3'],
        ['teaching_schedule_rooms', '7'],
        ['lms_assignments', '2'],
        ['lms_students', '2'],
        ['quiz_packages', '1'],
        ['quiz_versions', '1'],
        ['lms_quizzes', '1'],
        ['lms_questions', '2'],
        ['topic_content_items', '3'],
        ['content_item_assets', '2'],
        ['content_items', '3'],
        ['content_assets', '2'],
        ['curriculum_program_rows', '12'],
        ['curriculum_programs', '1'],
        ['curriculum_topics', '9'],
        ['curriculum_levels', '13'],
        ['curriculum_subjects', '13'],
        ['academic_classes', '135']
    ];
    must_be_empty CONSTANT text[] := ARRAY[
        'user_sessions', 'lms_attempts', 'student_progress', 'employee_profiles', 'user_role_assignments',
        'crm_school_profiles', 'school_academic_years', 'lms_lesson_log_entries', 'user_identity_links'];
    seed_users CONSTANT text[] := ARRAY[
        'usr_super_admin', 'usr_relationship_placeholder',
        'usr_teacher_ba', 'usr_teacher_cl', 'usr_stu_ba_0001', 'usr_stu_cl_0001',
        'usr_teacher_truc', 'usr_teacher_vuong', 'usr_teacher_tuan', 'usr_teacher_vang', 'usr_teacher_tram',
        'seed.question.bank', 'seed.quiz.demo'];
    seed_units CONSTANT text[] := ARRAY[
        'ctr_system', 'ctr_content_ops',
        'sch_binh_an', 'sch_cat_lai', 'sch_truong_cong_dinh', 'sch_nguyen_thi_minh_khai'];
    n bigint;
    total bigint := 0;
BEGIN
    IF coalesce(current_setting('erg.fresh_install_purge', true), '') <> 'true' THEN
        RAISE NOTICE 'V169: fresh-install cleanup not requested, nothing removed';
        RETURN;
    END IF;

    -- Every tenant table is FORCE RLS; the migration must see all tenants (transaction-local).
    PERFORM set_config('app.maintenance_mode', 'true', true);

    SELECT count(*) INTO n FROM users
    WHERE id <> ALL (seed_users) OR coalesce(login_count, 0) > 0 OR last_login_at IS NOT NULL;
    IF n > 0 THEN
        RAISE NOTICE 'V169: database is in use (% real or logged-in users), nothing removed', n;
        RETURN;
    END IF;
    SELECT count(*) INTO n FROM education_units WHERE id <> ALL (seed_units);
    IF n > 0 THEN
        RAISE NOTICE 'V169: database is in use (% non-seed education units), nothing removed', n;
        RETURN;
    END IF;
    FOR i IN 1 .. array_length(must_be_empty, 1) LOOP
        EXECUTE format('SELECT count(*) FROM %I', must_be_empty[i]) INTO n;
        IF n > 0 THEN
            RAISE NOTICE 'V169: database is in use (% rows in %), nothing removed', n, must_be_empty[i];
            RETURN;
        END IF;
    END LOOP;
    FOR i IN 1 .. array_length(purge, 1) LOOP
        EXECUTE format('SELECT count(*) FROM %I', purge[i][1]) INTO n;
        IF n > purge[i][2]::bigint THEN
            RAISE NOTICE 'V169: database is in use (% rows in %, the seed has %), nothing removed',
                n, purge[i][1], purge[i][2];
            RETURN;
        END IF;
    END LOOP;

    FOR i IN 1 .. array_length(purge, 1) LOOP
        EXECUTE format('DELETE FROM %I', purge[i][1]);
        GET DIAGNOSTICS n = ROW_COUNT;
        total := total + n;
    END LOOP;
    -- user_roles, profiles and user_access_scopes of the removed users and units go by ON DELETE CASCADE.
    DELETE FROM users WHERE id <> 'usr_super_admin';
    GET DIAGNOSTICS n = ROW_COUNT;
    total := total + n;
    DELETE FROM education_units WHERE type = 'school';
    GET DIAGNOSTICS n = ROW_COUNT;
    total := total + n;
    DELETE FROM education_units WHERE id <> 'ctr_system';
    GET DIAGNOSTICS n = ROW_COUNT;
    total := total + n;
    RAISE NOTICE 'V169: fresh install cleaned, % demo rows removed', total;
END $$;
