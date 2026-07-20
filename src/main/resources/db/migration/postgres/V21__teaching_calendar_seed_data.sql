-- V21__teaching_calendar_seed_data.sql
-- Seed data cho teaching calendar demo
-- ============================================================================

-- ============================================================================
-- 1. Schools
-- ============================================================================
INSERT INTO education_units (id, created_at, updated_at, name, slug, type, parent_id, status)
SELECT 'sch_binh_an', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'THCS Bình An', 'thcs-binh-an', 'school', 'ctr_hoclieu', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM education_units WHERE id = 'sch_binh_an');

INSERT INTO education_units (id, created_at, updated_at, name, slug, type, parent_id, status)
SELECT 'sch_cat_lai', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'THCS Cát Lái', 'thcs-cat-lai', 'school', 'ctr_hoclieu', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM education_units WHERE id = 'sch_cat_lai');

INSERT INTO education_units (id, created_at, updated_at, name, slug, type, parent_id, status)
SELECT 'sch_truong_cong_dinh', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'THCS Trương Công Định', 'thcs-truong-cong-dinh', 'school', 'ctr_hoclieu', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM education_units WHERE id = 'sch_truong_cong_dinh');

INSERT INTO education_units (id, created_at, updated_at, name, slug, type, parent_id, status)
SELECT 'sch_nguyen_thi_minh_khai', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'TH Nguyễn Thị Minh Khai', 'th-nguyen-thi-minh-khai', 'school', 'ctr_hoclieu', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM education_units WHERE id = 'sch_nguyen_thi_minh_khai');

-- ============================================================================
-- 2. Teachers
-- ============================================================================
INSERT INTO users (id, created_at, updated_at, email, full_name, status, provider, account_type, tenant_id, is_profile_completed, login_count)
SELECT 'usr_teacher_truc', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'truc@erg.edu.vn', 'Ngô Xuân Trúc', 'ACTIVE', 'local', 'lms', 'erg', true, 0
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = 'usr_teacher_truc');

INSERT INTO users (id, created_at, updated_at, email, full_name, status, provider, account_type, tenant_id, is_profile_completed, login_count)
SELECT 'usr_teacher_vuong', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'vuong@erg.edu.vn', 'Trần Quốc Vương', 'ACTIVE', 'local', 'lms', 'erg', true, 0
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = 'usr_teacher_vuong');

INSERT INTO users (id, created_at, updated_at, email, full_name, status, provider, account_type, tenant_id, is_profile_completed, login_count)
SELECT 'usr_teacher_tuan', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'tuan@erg.edu.vn', 'Trần Tướng Tuấn', 'ACTIVE', 'local', 'lms', 'erg', true, 0
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = 'usr_teacher_tuan');

INSERT INTO users (id, created_at, updated_at, email, full_name, status, provider, account_type, tenant_id, is_profile_completed, login_count)
SELECT 'usr_teacher_vang', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'vang@erg.edu.vn', 'K Vảng', 'ACTIVE', 'local', 'lms', 'erg', true, 0
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = 'usr_teacher_vang');

INSERT INTO users (id, created_at, updated_at, email, full_name, status, provider, account_type, tenant_id, is_profile_completed, login_count)
SELECT 'usr_teacher_tram', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'tram@erg.edu.vn', 'Dương Ngọc Trâm', 'ACTIVE', 'local', 'lms', 'erg', true, 0
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = 'usr_teacher_tram');

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, 'role_lms_teacher'
FROM users u
WHERE u.id IN ('usr_teacher_truc','usr_teacher_vuong','usr_teacher_tuan','usr_teacher_vang','usr_teacher_tram')
  AND NOT EXISTS (SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = 'role_lms_teacher');

INSERT INTO profiles (id, created_at, updated_at, user_id, full_name, is_profile_completed)
SELECT 'prof_'||u.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, u.id, u.full_name, true
FROM users u
WHERE u.id IN ('usr_teacher_truc','usr_teacher_vuong','usr_teacher_tuan','usr_teacher_vang','usr_teacher_tram')
  AND NOT EXISTS (SELECT 1 FROM profiles p WHERE p.user_id = u.id);

-- ============================================================================
-- 3. Classes
-- ============================================================================
DO $$
DECLARE
    sid TEXT;
    g INT;
    n INT;
BEGIN
    FOREACH sid IN ARRAY ARRAY['sch_binh_an','sch_cat_lai','sch_truong_cong_dinh'] LOOP
        FOR g IN 6..9 LOOP
            FOR n IN 1..9 LOOP
                INSERT INTO academic_classes (id, created_at, updated_at, tenant_id, school_id, name, grade, academic_year, status)
                SELECT sid||'_g'||g||'a'||n, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'erg', sid, g||'A'||n, 'grade-'||g, '2025-2026', 'active'
                WHERE NOT EXISTS (SELECT 1 FROM academic_classes WHERE id = sid||'_g'||g||'a'||n);
            END LOOP;
        END LOOP;
    END LOOP;

    FOR g IN 1..5 LOOP
        FOR n IN 1..5 LOOP
            INSERT INTO academic_classes (id, created_at, updated_at, tenant_id, school_id, name, grade, academic_year, status)
            SELECT 'sch_nguyen_thi_minh_khai_g'||g||'a'||n, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'erg', 'sch_nguyen_thi_minh_khai', g||'A'||n, 'grade-'||g, '2025-2026', 'active'
            WHERE NOT EXISTS (SELECT 1 FROM academic_classes WHERE id = 'sch_nguyen_thi_minh_khai_g'||g||'a'||n);
        END LOOP;
    END LOOP;
END $$;

-- ============================================================================
-- 4. Subjects
-- ============================================================================
INSERT INTO lms_subjects (id, created_at, updated_at, tenant_id, name, code, status)
SELECT 'sub_toan', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'erg', 'Toán', 'toan', 'active'
WHERE NOT EXISTS (SELECT 1 FROM lms_subjects WHERE id = 'sub_toan');

INSERT INTO lms_subjects (id, created_at, updated_at, tenant_id, name, code, status)
SELECT 'sub_ngu_van', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'erg', 'Ngữ Văn', 'ngu-van', 'active'
WHERE NOT EXISTS (SELECT 1 FROM lms_subjects WHERE id = 'sub_ngu_van');

INSERT INTO lms_subjects (id, created_at, updated_at, tenant_id, name, code, status)
SELECT 'sub_tieng_anh', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'erg', 'Tiếng Anh', 'tieng-anh', 'active'
WHERE NOT EXISTS (SELECT 1 FROM lms_subjects WHERE id = 'sub_tieng_anh');

INSERT INTO lms_subjects (id, created_at, updated_at, tenant_id, name, code, status)
SELECT 'sub_vat_ly', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'erg', 'Vật Lý', 'vat-ly', 'active'
WHERE NOT EXISTS (SELECT 1 FROM lms_subjects WHERE id = 'sub_vat_ly');

INSERT INTO lms_subjects (id, created_at, updated_at, tenant_id, name, code, status)
SELECT 'sub_hoa_hoc', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'erg', 'Hóa Học', 'hoa-hoc', 'active'
WHERE NOT EXISTS (SELECT 1 FROM lms_subjects WHERE id = 'sub_hoa_hoc');

INSERT INTO lms_subjects (id, created_at, updated_at, tenant_id, name, code, status)
SELECT 'sub_sinh_hoc', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'erg', 'Sinh Học', 'sinh-hoc', 'active'
WHERE NOT EXISTS (SELECT 1 FROM lms_subjects WHERE id = 'sub_sinh_hoc');

INSERT INTO lms_subjects (id, created_at, updated_at, tenant_id, name, code, status)
SELECT 'sub_lich_su', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'erg', 'Lịch Sử', 'lich-su', 'active'
WHERE NOT EXISTS (SELECT 1 FROM lms_subjects WHERE id = 'sub_lich_su');

INSERT INTO lms_subjects (id, created_at, updated_at, tenant_id, name, code, status)
SELECT 'sub_dia_ly', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'erg', 'Địa Lý', 'dia-ly', 'active'
WHERE NOT EXISTS (SELECT 1 FROM lms_subjects WHERE id = 'sub_dia_ly');

INSERT INTO lms_levels (id, created_at, updated_at, tenant_id, subject_id, name, code, sort_order, status)
SELECT 'lvl_toan_cb', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'erg', 'sub_toan', 'Toán Cơ Bản', 'co-ban', 1, 'active'
WHERE NOT EXISTS (SELECT 1 FROM lms_levels WHERE id = 'lvl_toan_cb');

INSERT INTO lms_levels (id, created_at, updated_at, tenant_id, subject_id, name, code, sort_order, status)
SELECT 'lvl_toan_nc', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'erg', 'sub_toan', 'Toán Nâng Cao', 'nang-cao', 2, 'active'
WHERE NOT EXISTS (SELECT 1 FROM lms_levels WHERE id = 'lvl_toan_nc');

INSERT INTO lms_levels (id, created_at, updated_at, tenant_id, subject_id, name, code, sort_order, status)
SELECT 'lvl_van_cb', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'erg', 'sub_ngu_van', 'Văn Cơ Bản', 'co-ban', 1, 'active'
WHERE NOT EXISTS (SELECT 1 FROM lms_levels WHERE id = 'lvl_van_cb');

-- ============================================================================
-- 5. Rooms
-- ============================================================================
INSERT INTO teaching_schedule_rooms (id, tenant_id, school_id, name, status, created_by, created_at, updated_at)
SELECT 'room_ba_101', 'erg', 'sch_binh_an', 'Phòng 101', 'ACTIVE', 'usr_super_admin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM teaching_schedule_rooms WHERE id = 'room_ba_101');

INSERT INTO teaching_schedule_rooms (id, tenant_id, school_id, name, status, created_by, created_at, updated_at)
SELECT 'room_ba_102', 'erg', 'sch_binh_an', 'Phòng 102', 'ACTIVE', 'usr_super_admin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM teaching_schedule_rooms WHERE id = 'room_ba_102');

INSERT INTO teaching_schedule_rooms (id, tenant_id, school_id, name, status, created_by, created_at, updated_at)
SELECT 'room_ba_201', 'erg', 'sch_binh_an', 'Phòng 201', 'ACTIVE', 'usr_super_admin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM teaching_schedule_rooms WHERE id = 'room_ba_201');

INSERT INTO teaching_schedule_rooms (id, tenant_id, school_id, name, status, created_by, created_at, updated_at)
SELECT 'room_cl_101', 'erg', 'sch_cat_lai', 'Phòng 101', 'ACTIVE', 'usr_super_admin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM teaching_schedule_rooms WHERE id = 'room_cl_101');

INSERT INTO teaching_schedule_rooms (id, tenant_id, school_id, name, status, created_by, created_at, updated_at)
SELECT 'room_cl_102', 'erg', 'sch_cat_lai', 'Phòng 102', 'ACTIVE', 'usr_super_admin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM teaching_schedule_rooms WHERE id = 'room_cl_102');

INSERT INTO teaching_schedule_rooms (id, tenant_id, school_id, name, status, created_by, created_at, updated_at)
SELECT 'room_tcd_101', 'erg', 'sch_truong_cong_dinh', 'Phòng 101', 'ACTIVE', 'usr_super_admin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM teaching_schedule_rooms WHERE id = 'room_tcd_101');

INSERT INTO teaching_schedule_rooms (id, tenant_id, school_id, name, status, created_by, created_at, updated_at)
SELECT 'room_tcd_102', 'erg', 'sch_truong_cong_dinh', 'Phòng 102', 'ACTIVE', 'usr_super_admin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM teaching_schedule_rooms WHERE id = 'room_tcd_102');

-- ============================================================================
-- 6. Teaching Schedule Batches
-- ============================================================================
INSERT INTO teaching_schedule_batches (id, tenant_id, school_id, academic_year, apply_from, repeat_weeks, status, source, note, created_by, created_at, updated_at)
SELECT 'batch_ba_demo_001', 'erg', 'sch_binh_an', '2025-2026', '2026-06-15', 4, 'PUBLISHED', 'LCMS_BULK_GRID', 'Lịch HK1 - THCS Bình An', 'usr_super_admin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM teaching_schedule_batches WHERE id = 'batch_ba_demo_001');

INSERT INTO teaching_schedule_batches (id, tenant_id, school_id, academic_year, apply_from, repeat_weeks, status, source, note, created_by, created_at, updated_at)
SELECT 'batch_cl_demo_001', 'erg', 'sch_cat_lai', '2025-2026', '2026-06-15', 4, 'PUBLISHED', 'LCMS_BULK_GRID', 'Lịch HK1 - THCS Cát Lái', 'usr_super_admin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM teaching_schedule_batches WHERE id = 'batch_cl_demo_001');

INSERT INTO teaching_schedule_batches (id, tenant_id, school_id, academic_year, apply_from, repeat_weeks, status, source, note, created_by, created_at, updated_at)
SELECT 'batch_tcd_demo_001', 'erg', 'sch_truong_cong_dinh', '2025-2026', '2026-06-15', 4, 'PUBLISHED', 'LCMS_BULK_GRID', 'Lịch HK1 - THCS Trương Công Định', 'usr_super_admin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM teaching_schedule_batches WHERE id = 'batch_tcd_demo_001');

-- ============================================================================
-- 7. Teaching Schedule Events & Teacher Assignments
-- Dùng procedure để dễ debug và tránh lỗi
-- ============================================================================
DO $$
DECLARE
    rec RECORD;
    class_rec RECORD;
    v_event_date DATE;
    v_eid TEXT;
    v_cnt INT := 0;
    v_wd INT;
    v_wo INT;
BEGIN
    FOR rec IN SELECT * FROM (VALUES
        ('batch_ba_demo_001', 'sch_binh_an', 'room_ba_101', 'room_ba_102'),
        ('batch_cl_demo_001', 'sch_cat_lai', 'room_cl_101', 'room_cl_102'),
        ('batch_tcd_demo_001', 'sch_truong_cong_dinh', 'room_tcd_101', 'room_tcd_102')
    ) AS t(bid, sid, rm1, rm2) LOOP

        FOR class_rec IN SELECT id, name FROM academic_classes WHERE school_id = rec.sid ORDER BY name LIMIT 2 LOOP

            -- Toán: Thứ 2 (1) & Thứ 4 (3), 07:00-08:30
            FOREACH v_wd IN ARRAY ARRAY[1, 3] LOOP
                FOR v_wo IN 0..3 LOOP
                    v_event_date := '2026-06-15'::DATE + ((v_wd + 7 - EXTRACT(ISODOW FROM '2026-06-15'::DATE)::INT) % 7) + (v_wo * 7);
                    v_cnt := v_cnt + 1; v_eid := rec.bid || '_e' || v_cnt;

                    IF NOT EXISTS (SELECT 1 FROM teaching_schedule_events WHERE id = v_eid) THEN
                        INSERT INTO teaching_schedule_events (id, tenant_id, batch_id, school_id, academic_class_id, subject_id, level_id, room_id, room_name, title, start_at, end_at, weekday, session, period_start, period_count, status, created_by, created_at, updated_at)
                        VALUES (v_eid, 'erg', rec.bid, rec.sid, class_rec.id, 'sub_toan', 'lvl_toan_cb', rec.rm1, 'Phòng 101', 'Toán - ' || class_rec.name, (v_event_date||' 07:00:00+07')::TIMESTAMPTZ, (v_event_date||' 08:30:00+07')::TIMESTAMPTZ, v_wd, 'MORNING', 1, 2, 'PUBLISHED', 'usr_super_admin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

                        INSERT INTO teaching_schedule_event_teachers (id, tenant_id, event_id, teacher_user_id, role, display_name_snapshot, created_at, updated_at)
                        VALUES (v_eid||'_m1', 'erg', v_eid, 'usr_teacher_truc', 'MAIN', 'Ngô Xuân Trúc', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
                        INSERT INTO teaching_schedule_event_teachers (id, tenant_id, event_id, teacher_user_id, role, display_name_snapshot, created_at, updated_at)
                        VALUES (v_eid||'_m2', 'erg', v_eid, 'usr_teacher_vuong', 'MAIN', 'Trần Quốc Vương', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
                        INSERT INTO teaching_schedule_event_teachers (id, tenant_id, event_id, teacher_user_id, role, display_name_snapshot, created_at, updated_at)
                        VALUES (v_eid||'_a1', 'erg', v_eid, 'usr_teacher_vang', 'ASSISTANT', 'K Vảng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
                    END IF;
                END LOOP;
            END LOOP;

            -- Tiếng Anh: Thứ 3 (2) & Thứ 5 (4), 09:00-10:30
            FOREACH v_wd IN ARRAY ARRAY[2, 4] LOOP
                FOR v_wo IN 0..3 LOOP
                    v_event_date := '2026-06-15'::DATE + ((v_wd + 7 - EXTRACT(ISODOW FROM '2026-06-15'::DATE)::INT) % 7) + (v_wo * 7);
                    v_cnt := v_cnt + 1; v_eid := rec.bid || '_e' || v_cnt;

                    IF NOT EXISTS (SELECT 1 FROM teaching_schedule_events WHERE id = v_eid) THEN
                        INSERT INTO teaching_schedule_events (id, tenant_id, batch_id, school_id, academic_class_id, subject_id, room_id, room_name, title, start_at, end_at, weekday, session, period_start, period_count, status, created_by, created_at, updated_at)
                        VALUES (v_eid, 'erg', rec.bid, rec.sid, class_rec.id, 'sub_tieng_anh', rec.rm2, 'Phòng 102', 'Tiếng Anh - ' || class_rec.name, (v_event_date||' 09:00:00+07')::TIMESTAMPTZ, (v_event_date||' 10:30:00+07')::TIMESTAMPTZ, v_wd, 'MORNING', 3, 2, 'PUBLISHED', 'usr_super_admin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

                        INSERT INTO teaching_schedule_event_teachers (id, tenant_id, event_id, teacher_user_id, role, display_name_snapshot, created_at, updated_at)
                        VALUES (v_eid||'_m1', 'erg', v_eid, 'usr_teacher_tuan', 'MAIN', 'Trần Tướng Tuấn', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
                        INSERT INTO teaching_schedule_event_teachers (id, tenant_id, event_id, teacher_user_id, role, display_name_snapshot, created_at, updated_at)
                        VALUES (v_eid||'_a1', 'erg', v_eid, 'usr_teacher_tram', 'ASSISTANT', 'Dương Ngọc Trâm', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
                    END IF;
                END LOOP;
            END LOOP;

            -- Ngữ Văn: Thứ 6 (5), 13:00-14:30
            v_wd := 5;
            FOR v_wo IN 0..3 LOOP
                v_event_date := '2026-06-15'::DATE + ((5 + 7 - EXTRACT(ISODOW FROM '2026-06-15'::DATE)::INT) % 7) + (v_wo * 7);
                v_cnt := v_cnt + 1; v_eid := rec.bid || '_e' || v_cnt;

                IF NOT EXISTS (SELECT 1 FROM teaching_schedule_events WHERE id = v_eid) THEN
                    INSERT INTO teaching_schedule_events (id, tenant_id, batch_id, school_id, academic_class_id, subject_id, level_id, room_name, title, start_at, end_at, weekday, session, period_start, period_count, status, created_by, created_at, updated_at)
                    VALUES (v_eid, 'erg', rec.bid, rec.sid, class_rec.id, 'sub_ngu_van', 'lvl_van_cb', 'Phòng Văn', 'Ngữ Văn - ' || class_rec.name, (v_event_date||' 13:00:00+07')::TIMESTAMPTZ, (v_event_date||' 14:30:00+07')::TIMESTAMPTZ, v_wd, 'AFTERNOON', 1, 2, 'PUBLISHED', 'usr_super_admin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

                    INSERT INTO teaching_schedule_event_teachers (id, tenant_id, event_id, teacher_user_id, role, display_name_snapshot, created_at, updated_at)
                    VALUES (v_eid||'_m1', 'erg', v_eid, 'usr_teacher_vang', 'MAIN', 'K Vảng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
                    INSERT INTO teaching_schedule_event_teachers (id, tenant_id, event_id, teacher_user_id, role, display_name_snapshot, created_at, updated_at)
                    VALUES (v_eid||'_m2', 'erg', v_eid, 'usr_teacher_tram', 'MAIN', 'Dương Ngọc Trâm', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
                END IF;
            END LOOP;
        END LOOP;
    END LOOP;
END $$;
