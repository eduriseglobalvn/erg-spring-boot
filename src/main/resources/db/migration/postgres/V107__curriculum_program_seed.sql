-- Demo PPCT rows for the existing IC3 GS6 Level 1 seed.
INSERT INTO curriculum_programs (
    id, tenant_id, subject_id, level_id, academic_year, name, status,
    record_version, created_by, updated_by, created_at, updated_at
)
VALUES ('cp_demo_ic3_gs6_l1_2026', 'erg', 'sub_ic3', 'lvl_ic3_gs6_l1', '2026-2027',
        'PPCT IC3 GS6 Level 1', 'published', 0, 'seed', 'seed', now(), now())
ON CONFLICT (tenant_id, subject_id, level_id, academic_year) DO UPDATE SET
    name = EXCLUDED.name, status = EXCLUDED.status, updated_at = now();

INSERT INTO curriculum_program_rows (
    id, tenant_id, program_id, topic, period, title, sessions, sort_order, status, record_version,
    created_at, updated_at
)
VALUES
 ('cpr_demo_ic3_01','erg','cp_demo_ic3_gs6_l1_2026','Căn bản về công nghệ',1,'Bài 1: Xác định yêu cầu hệ thống và yêu cầu phần mềm',1,0,'active',0,now(),now()),
 ('cpr_demo_ic3_02','erg','cp_demo_ic3_gs6_l1_2026','Căn bản về công nghệ',2,'Bài 1: Xác định yêu cầu hệ thống và yêu cầu phần mềm',1,1,'active',0,now(),now()),
 ('cpr_demo_ic3_03','erg','cp_demo_ic3_gs6_l1_2026','Căn bản về công nghệ',3,'Bài 2: Khắc phục sự cố máy tính',1,2,'active',0,now(),now()),
 ('cpr_demo_ic3_04','erg','cp_demo_ic3_gs6_l1_2026','Căn bản về công nghệ',4,'Bài 2: Khắc phục sự cố máy tính',1,3,'active',0,now(),now()),
 ('cpr_demo_ic3_05','erg','cp_demo_ic3_gs6_l1_2026','Căn bản về công nghệ',5,'Bài 3: Cấp phép phần mềm',1,4,'active',0,now(),now()),
 ('cpr_demo_ic3_06','erg','cp_demo_ic3_gs6_l1_2026','Căn bản về công nghệ',6,'Bài 4: Cài đặt mặc định phần mềm',1,5,'active',0,now(),now()),
 ('cpr_demo_ic3_07','erg','cp_demo_ic3_gs6_l1_2026','Công dân số',7,'Ôn tập 1',1,6,'active',0,now(),now()),
 ('cpr_demo_ic3_08','erg','cp_demo_ic3_gs6_l1_2026','Công dân số',8,'Bài 5: Báo cáo hành vi gây hại (1)',1,7,'active',0,now(),now()),
 ('cpr_demo_ic3_09','erg','cp_demo_ic3_gs6_l1_2026','Công dân số',9,'Bài 6: Báo cáo hành vi gây hại (2)',1,8,'active',0,now(),now()),
 ('cpr_demo_ic3_10','erg','cp_demo_ic3_gs6_l1_2026','Công dân số',10,'Bài 7: Một số hành vi bất hợp pháp trên môi trường kĩ thuật số',1,9,'active',0,now(),now()),
 ('cpr_demo_ic3_11','erg','cp_demo_ic3_gs6_l1_2026','Công dân số',11,'Bài 7: Một số hành vi bất hợp pháp trên môi trường kĩ thuật số',1,10,'active',0,now(),now()),
 ('cpr_demo_ic3_12','erg','cp_demo_ic3_gs6_l1_2026','Công dân số',12,'Bài 8: Cập nhật kiến thức kĩ thuật số (1)',1,11,'active',0,now(),now())
ON CONFLICT (id) DO UPDATE SET
    topic = EXCLUDED.topic, period = EXCLUDED.period, title = EXCLUDED.title,
    sessions = EXCLUDED.sessions, sort_order = EXCLUDED.sort_order, updated_at = now();
