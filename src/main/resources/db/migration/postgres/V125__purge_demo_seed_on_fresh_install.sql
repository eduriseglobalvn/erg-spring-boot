-- P3-7: a brand-new production database must not carry the demo rows that V4, V11, V21, V33, V68
-- and V102 insert in every environment. Those migrations are applied and append-only, so this
-- migration removes their rows afterwards, but ONLY when an operator opts in:
--
--   ERG_FLYWAY_DEMO_PURGE=true  ->  FlywayConfig runs "SET erg.demo_purge = 'true'" as initSql.
--
-- Unset (the default, and every other Flyway runner) this file is a no-op, so existing databases are
-- untouched. The flag is read at run time, never substituted into the script, so the checksum is
-- the same either way. Safety rules (docs/runbooks/postgres-migration-preflight.md section 3):
--   * rows are matched by their exact seed ids only, never by pattern;
--   * the purge aborts unless the database is a fresh install: no user other than the seed
--     accounts, and no seed account has a password or a login;
--   * it aborts if any row outside the demo set references a demo row through a foreign key;
--   * every removed row is first copied to _quarantine_v125_<table>, counted with RAISE NOTICE,
--     and the total is capped.
-- Kept on purpose: usr_super_admin, usr_relationship_placeholder (V11 FK sink for orphaned CRM deals),
-- roles/permissions, the ctr_* organisation units and the IC3/Tin hoc/Python/MOS curriculum.
DO $$
DECLARE
    purge_tables CONSTANT text[][] := ARRAY[
        -- table, key column; children before parents
        ['curriculum_teaching_plan_settings', 'id'],
        ['teaching_schedule_event_teachers', 'id'],
        ['teaching_schedule_events', 'id'],
        ['teaching_schedule_batches', 'id'],
        ['teaching_schedule_rooms', 'id'],
        ['quiz_packages', 'id'],
        ['quiz_versions', 'id'],
        ['lms_assignments', 'id'],
        ['lms_students', 'id'],
        ['lms_quizzes', 'id'],
        ['lms_questions', 'id'],
        ['lms_topics', 'id'],
        ['lms_levels', 'id'],
        ['lms_subjects', 'id'],
        ['curriculum_topics', 'id'],
        ['curriculum_levels', 'id'],
        ['curriculum_subjects', 'id'],
        ['academic_classes', 'id'],
        ['user_access_scopes', 'user_id'],
        ['profiles', 'user_id'],
        ['user_roles', 'user_id'],
        ['education_units', 'id'],
        ['users', 'id']
    ];
    demo_users CONSTANT text[] := ARRAY[
        'usr_teacher_ba', 'usr_teacher_cl', 'usr_stu_ba_0001', 'usr_stu_cl_0001',            -- V11
        'usr_teacher_truc', 'usr_teacher_vuong', 'usr_teacher_tuan', 'usr_teacher_vang',     -- V21
        'usr_teacher_tram',
        'seed.question.bank', 'seed.quiz.demo'                                               -- V33
    ];
    demo_schools CONSTANT text[] := ARRAY[
        'sch_binh_an', 'sch_cat_lai', 'sch_truong_cong_dinh', 'sch_nguyen_thi_minh_khai'];
    demo_subjects CONSTANT text[] := ARRAY[
        'sub_math', 'sub_toan', 'sub_ngu_van', 'sub_tieng_anh', 'sub_vat_ly', 'sub_hoa_hoc',
        'sub_sinh_hoc', 'sub_lich_su', 'sub_dia_ly'];
    demo_levels CONSTANT text[] := ARRAY['lvl_math_g6', 'lvl_toan_cb', 'lvl_toan_nc', 'lvl_van_cb'];
    demo_batches CONSTANT text[] := ARRAY['batch_ba_demo_001', 'batch_cl_demo_001', 'batch_tcd_demo_001'];
    max_rows CONSTANT int := 1000;
    fk record;
    tbl text;
    keycol text;
    parent_key text;
    n bigint;
    total bigint := 0;
BEGIN
    IF coalesce(current_setting('erg.demo_purge', true), '') <> 'true' THEN
        RAISE NOTICE 'V125: demo purge not requested (ERG_FLYWAY_DEMO_PURGE != true), nothing removed';
        RETURN;
    END IF;

    -- Every tenant table is FORCE RLS; the migration acts on all tenants (transaction-local).
    PERFORM set_config('app.maintenance_mode', 'true', true);

    SELECT count(*) INTO n FROM users
    WHERE id <> ALL (demo_users || ARRAY['usr_super_admin', 'usr_relationship_placeholder']);
    IF n > 0 THEN
        RAISE EXCEPTION 'V125: demo purge is only for a fresh install, but % non-seed users exist. Unset ERG_FLYWAY_DEMO_PURGE and redeploy.', n;
    END IF;
    SELECT count(*) INTO n FROM users
    WHERE id = ANY (demo_users) AND (password_hash IS NOT NULL OR coalesce(login_count, 0) > 0);
    IF n > 0 THEN
        RAISE EXCEPTION 'V125: % seed accounts have a password or a login; refusing to purge. Unset ERG_FLYWAY_DEMO_PURGE and redeploy.', n;
    END IF;

    -- Exact demo ids per table (V4, V11, V21, V33, V68, V102).
    CREATE TEMP TABLE v125_demo (tbl text NOT NULL, id text NOT NULL, PRIMARY KEY (tbl, id)) ON COMMIT DROP;
    INSERT INTO v125_demo
    SELECT 'users', unnest(demo_users)
    UNION ALL SELECT 'user_roles', unnest(demo_users)
    UNION ALL SELECT 'profiles', unnest(demo_users)
    UNION ALL SELECT 'user_access_scopes', unnest(demo_users)
    UNION ALL SELECT 'education_units', unnest(demo_schools)
    UNION ALL SELECT 'lms_subjects', unnest(demo_subjects)
    UNION ALL SELECT 'curriculum_subjects', unnest(demo_subjects)        -- V38 bridge copies
    UNION ALL SELECT 'lms_levels', unnest(demo_levels)
    UNION ALL SELECT 'curriculum_levels', unnest(demo_levels)
    UNION ALL SELECT 'lms_topics', 'top_math_g6_num'
    UNION ALL SELECT 'curriculum_topics', 'top_math_g6_num'
    UNION ALL SELECT 'lms_questions', unnest(ARRAY['que_math_g6_001', 'que_math_g6_002'])
    UNION ALL SELECT 'lms_quizzes', 'qz_math_g6_base'
    UNION ALL SELECT 'lms_assignments', unnest(ARRAY['asg_ba_math_g6_001', 'asg_cl_math_g6_001'])
    UNION ALL SELECT 'lms_students', unnest(ARRAY['stu_ba_0001', 'stu_cl_0001'])
    UNION ALL SELECT 'quiz_versions', 'qv_math_g6_v1'
    UNION ALL SELECT 'quiz_packages', 'pkg_math_g6'
    UNION ALL SELECT 'curriculum_teaching_plan_settings', 'ctp_demo_binh_an_ic3_2026'
    UNION ALL SELECT 'academic_classes', unnest(ARRAY['cls_ba_2025_6a1', 'cls_cl_2025_6a1'])
    -- V21 classes: <school>_g<grade>a<n>
    UNION ALL SELECT 'academic_classes', s || '_g' || g || 'a' || c
              FROM unnest(ARRAY['sch_binh_an', 'sch_cat_lai', 'sch_truong_cong_dinh']) s,
                   generate_series(6, 9) g, generate_series(1, 9) c
    UNION ALL SELECT 'academic_classes', 'sch_nguyen_thi_minh_khai_g' || g || 'a' || c
              FROM generate_series(1, 5) g, generate_series(1, 5) c
    UNION ALL SELECT 'teaching_schedule_rooms', unnest(ARRAY[
              'room_ba_101', 'room_ba_102', 'room_ba_201', 'room_cl_101', 'room_cl_102',
              'room_tcd_101', 'room_tcd_102'])
    UNION ALL SELECT 'teaching_schedule_batches', unnest(demo_batches)
    -- V21 events: <batch>_e<n> (one counter across all batches) and their teachers <event>_m1/_m2/_a1
    UNION ALL SELECT 'teaching_schedule_events', e.id
              FROM teaching_schedule_events e
              WHERE e.batch_id = ANY (demo_batches)
                AND e.id IN (SELECT b || '_e' || i FROM unnest(demo_batches) b, generate_series(1, 120) i)
    UNION ALL SELECT 'teaching_schedule_event_teachers', t.id
              FROM teaching_schedule_event_teachers t
              JOIN teaching_schedule_events e ON e.id = t.event_id
              WHERE e.batch_id = ANY (demo_batches)
                AND e.id IN (SELECT b || '_e' || i FROM unnest(demo_batches) b, generate_series(1, 120) i)
                AND t.id IN (e.id || '_m1', e.id || '_m2', e.id || '_a1');

    -- Abort if anything outside the demo set points at a demo row (FK cascades would delete it).
    FOR fk IN
        SELECT c.conrelid::regclass::text AS child, ca.attname AS child_col,
               c.confrelid::regclass::text AS parent, pa.attname AS parent_col
        FROM pg_constraint c
        JOIN pg_attribute ca ON ca.attrelid = c.conrelid AND ca.attnum = c.conkey[1]
        JOIN pg_attribute pa ON pa.attrelid = c.confrelid AND pa.attnum = c.confkey[1]
        WHERE c.contype = 'f' AND array_length(c.conkey, 1) = 1
          AND c.confrelid::regclass::text IN (SELECT DISTINCT d.tbl FROM v125_demo d)
    LOOP
        keycol := NULL;
        parent_key := NULL;
        FOR i IN 1 .. array_length(purge_tables, 1) LOOP
            IF purge_tables[i][1] = fk.child THEN keycol := purge_tables[i][2]; END IF;
            IF purge_tables[i][1] = fk.parent THEN parent_key := purge_tables[i][2]; END IF;
        END LOOP;
        EXECUTE format(
            'SELECT count(*) FROM %I x WHERE x.%I IN (SELECT p.%I FROM %I p WHERE p.%I IN '
                || '(SELECT id FROM v125_demo WHERE tbl = %L))',
            fk.child, fk.child_col, fk.parent_col, fk.parent, parent_key, fk.parent)
            || CASE WHEN keycol IS NULL THEN ''
                    ELSE format(' AND x.%I NOT IN (SELECT id FROM v125_demo WHERE tbl = %L)', keycol, fk.child) END
            INTO n;
        IF n > 0 THEN
            RAISE EXCEPTION 'V125: % non-demo rows in %.% reference demo rows of %; refusing to purge',
                n, fk.child, fk.child_col, fk.parent;
        END IF;
    END LOOP;

    -- Quarantine, delete, count.
    FOR i IN 1 .. array_length(purge_tables, 1) LOOP
        tbl := purge_tables[i][1];
        keycol := purge_tables[i][2];
        EXECUTE format(
            'CREATE TABLE %I AS SELECT t.*, now() AS quarantined_at FROM %I t '
                || 'WHERE t.%I IN (SELECT id FROM v125_demo WHERE tbl = %L)',
            '_quarantine_v125_' || tbl, tbl, keycol, tbl);
        EXECUTE format('DELETE FROM %I t WHERE t.%I IN (SELECT id FROM v125_demo WHERE tbl = %L)',
            tbl, keycol, tbl);
        GET DIAGNOSTICS n = ROW_COUNT;
        total := total + n;
        RAISE NOTICE 'V125: % rows removed from %', n, tbl;
    END LOOP;

    IF total > max_rows THEN
        RAISE EXCEPTION 'V125: % rows exceed the approved threshold of %', total, max_rows;
    END IF;
    RAISE NOTICE 'V125: demo purge removed % rows in total', total;
END $$;
