INSERT INTO permission_groups (id, created_at, updated_at, name, label, display_order)
SELECT 'pg_system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'System Administration', 10
WHERE NOT EXISTS (SELECT 1 FROM permission_groups WHERE id = 'pg_system');

INSERT INTO permission_groups (id, created_at, updated_at, name, label, display_order)
SELECT 'pg_rbac', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'rbac', 'Roles and Permissions', 20
WHERE NOT EXISTS (SELECT 1 FROM permission_groups WHERE id = 'pg_rbac');

INSERT INTO permission_groups (id, created_at, updated_at, name, label, display_order)
SELECT 'pg_users', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'users', 'User Management', 30
WHERE NOT EXISTS (SELECT 1 FROM permission_groups WHERE id = 'pg_users');

INSERT INTO permission_groups (id, created_at, updated_at, name, label, display_order)
SELECT 'pg_lms', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'lms', 'Learning Management', 40
WHERE NOT EXISTS (SELECT 1 FROM permission_groups WHERE id = 'pg_lms');

INSERT INTO permission_groups (id, created_at, updated_at, name, label, display_order)
SELECT 'pg_hoclieu', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'hoclieu', 'Learning Resources', 50
WHERE NOT EXISTS (SELECT 1 FROM permission_groups WHERE id = 'pg_hoclieu');

INSERT INTO permissions (id, created_at, updated_at, name, group_name, label, description)
SELECT 'perm_all', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '*', 'system', 'All permissions', 'Full platform access'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = '*');

INSERT INTO permissions (id, created_at, updated_at, name, group_name, label, description)
SELECT 'perm_profile_self', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'profile.self', 'users', 'Own profile', 'Read and update own profile'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'profile.self');

INSERT INTO permissions (id, created_at, updated_at, name, group_name, label, description)
SELECT 'perm_users_manage', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'users.manage', 'users', 'Manage users', 'Create, update, ban, unban, and manage user accounts'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'users.manage');

INSERT INTO permissions (id, created_at, updated_at, name, group_name, label, description)
SELECT 'perm_rbac_manage', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'rbac.*', 'rbac', 'Manage RBAC', 'Manage roles, permissions, and overrides'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'rbac.*');

INSERT INTO permissions (id, created_at, updated_at, name, group_name, label, description)
SELECT 'perm_lms_read', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'lms.read', 'lms', 'Read LMS', 'Read LMS data'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'lms.read');

INSERT INTO permissions (id, created_at, updated_at, name, group_name, label, description)
SELECT 'perm_lms_manage', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'lms.manage', 'lms', 'Manage LMS', 'Manage LMS data'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'lms.manage');

INSERT INTO permissions (id, created_at, updated_at, name, group_name, label, description)
SELECT 'perm_lms_class_read', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'lms.class.read', 'lms', 'Read classes', 'Read managed LMS classes'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'lms.class.read');

INSERT INTO permissions (id, created_at, updated_at, name, group_name, label, description)
SELECT 'perm_lms_assign_self', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'lms.assignment.read_self', 'lms', 'Read own assignments', 'Students can read their own assignments'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'lms.assignment.read_self');

INSERT INTO permissions (id, created_at, updated_at, name, group_name, label, description)
SELECT 'perm_lms_submit', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'lms.assignment.submit', 'lms', 'Submit assignment', 'Students can submit attempts'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'lms.assignment.submit');

INSERT INTO permissions (id, created_at, updated_at, name, group_name, label, description)
SELECT 'perm_lms_unit_read', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'lms.unit.read', 'lms', 'Read units', 'Read managed education units'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'lms.unit.read');

INSERT INTO permissions (id, created_at, updated_at, name, group_name, label, description)
SELECT 'perm_hoclieu_manage', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'hoclieu.*', 'hoclieu', 'Manage learning resources', 'Manage HocLieu resources and studio data'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'hoclieu.*');

INSERT INTO roles (id, created_at, updated_at, name, description, is_default)
SELECT 'role_super_admin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system.super_admin', 'Full platform administrator', false
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE id = 'role_super_admin');

INSERT INTO roles (id, created_at, updated_at, name, description, is_default)
SELECT 'role_lms_admin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'lms.admin', 'LMS administrator', false
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE id = 'role_lms_admin');

INSERT INTO roles (id, created_at, updated_at, name, description, is_default)
SELECT 'role_lms_teacher', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'lms.instructor', 'LMS teacher', false
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE id = 'role_lms_teacher');

INSERT INTO roles (id, created_at, updated_at, name, description, is_default)
SELECT 'role_lms_student', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'lms.learner', 'LMS learner', true
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE id = 'role_lms_student');

INSERT INTO role_permissions (role_id, permission_id)
SELECT 'role_super_admin', 'perm_all'
WHERE NOT EXISTS (SELECT 1 FROM role_permissions WHERE role_id = 'role_super_admin' AND permission_id = 'perm_all');

INSERT INTO role_permissions (role_id, permission_id)
SELECT 'role_lms_admin', 'perm_lms_read'
WHERE NOT EXISTS (SELECT 1 FROM role_permissions WHERE role_id = 'role_lms_admin' AND permission_id = 'perm_lms_read');

INSERT INTO role_permissions (role_id, permission_id)
SELECT 'role_lms_admin', 'perm_lms_manage'
WHERE NOT EXISTS (SELECT 1 FROM role_permissions WHERE role_id = 'role_lms_admin' AND permission_id = 'perm_lms_manage');

INSERT INTO role_permissions (role_id, permission_id)
SELECT 'role_lms_teacher', 'perm_lms_class_read'
WHERE NOT EXISTS (SELECT 1 FROM role_permissions WHERE role_id = 'role_lms_teacher' AND permission_id = 'perm_lms_class_read');

INSERT INTO role_permissions (role_id, permission_id)
SELECT 'role_lms_teacher', 'perm_lms_unit_read'
WHERE NOT EXISTS (SELECT 1 FROM role_permissions WHERE role_id = 'role_lms_teacher' AND permission_id = 'perm_lms_unit_read');

INSERT INTO role_permissions (role_id, permission_id)
SELECT 'role_lms_student', 'perm_lms_assign_self'
WHERE NOT EXISTS (SELECT 1 FROM role_permissions WHERE role_id = 'role_lms_student' AND permission_id = 'perm_lms_assign_self');

INSERT INTO role_permissions (role_id, permission_id)
SELECT 'role_lms_student', 'perm_lms_submit'
WHERE NOT EXISTS (SELECT 1 FROM role_permissions WHERE role_id = 'role_lms_student' AND permission_id = 'perm_lms_submit');

INSERT INTO education_units (id, created_at, updated_at, name, slug, type, parent_id, logo_url, description, status)
SELECT 'ctr_system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'ERG Platform', 'erg-platform', 'system', null, '/assets/units/erg-platform.svg', 'Root platform organization', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM education_units WHERE id = 'ctr_system');

INSERT INTO education_units (id, created_at, updated_at, name, slug, type, parent_id, logo_url, description, status)
SELECT 'ctr_hoclieu', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'HocLieu Operations', 'hoclieu-operations', 'center', 'ctr_system', '/assets/units/hoclieu-operations.svg', 'Learning content and school operations center', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM education_units WHERE id = 'ctr_hoclieu');

INSERT INTO education_units (id, created_at, updated_at, name, slug, type, parent_id, logo_url, description, status)
SELECT 'sch_binh_an', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'THCS Binh An', 'thcs-binh-an', 'school', 'ctr_hoclieu', '/assets/units/thcs-binh-an.svg', 'THCS Binh An', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM education_units WHERE id = 'sch_binh_an');

INSERT INTO education_units (id, created_at, updated_at, name, slug, type, parent_id, logo_url, description, status)
SELECT 'sch_cat_lai', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'THCS Cat Lai', 'thcs-cat-lai', 'school', 'ctr_hoclieu', '/assets/units/thcs-cat-lai.svg', 'THCS Cat Lai', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM education_units WHERE id = 'sch_cat_lai');

INSERT INTO academic_classes (id, created_at, updated_at, tenant_id, school_id, name, grade, academic_year, status, homeroom_teacher_id)
SELECT 'cls_ba_2025_6a1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'erg', 'sch_binh_an', 'Lop 6A1', 'grade-6', '2025-2026', 'active', 'usr_teacher_ba'
WHERE NOT EXISTS (SELECT 1 FROM academic_classes WHERE id = 'cls_ba_2025_6a1');

INSERT INTO academic_classes (id, created_at, updated_at, tenant_id, school_id, name, grade, academic_year, status, homeroom_teacher_id)
SELECT 'cls_cl_2025_6a1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'erg', 'sch_cat_lai', 'Lop 6A1', 'grade-6', '2025-2026', 'active', 'usr_teacher_cl'
WHERE NOT EXISTS (SELECT 1 FROM academic_classes WHERE id = 'cls_cl_2025_6a1');

INSERT INTO lms_subjects (id, created_at, updated_at, tenant_id, scope, name, code, status)
SELECT 'sub_math', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'erg', null, 'Mathematics', 'math', 'active'
WHERE NOT EXISTS (SELECT 1 FROM lms_subjects WHERE id = 'sub_math');

INSERT INTO lms_levels (id, created_at, updated_at, tenant_id, subject_id, name, code, sort_order, status)
SELECT 'lvl_math_g6', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'erg', 'sub_math', 'Grade 6 Mathematics', 'grade-6', 1, 'active'
WHERE NOT EXISTS (SELECT 1 FROM lms_levels WHERE id = 'lvl_math_g6');

INSERT INTO lms_topics (id, created_at, updated_at, tenant_id, level_id, name, code, sort_order, status)
SELECT 'top_math_g6_num', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'erg', 'lvl_math_g6', 'Natural Numbers', 'natural-numbers', 1, 'active'
WHERE NOT EXISTS (SELECT 1 FROM lms_topics WHERE id = 'top_math_g6_num');

INSERT INTO lms_questions (id, created_at, updated_at, tenant_id, scope, subject_id, level_id, topic_id, type, stem, choices, answer, metadata, status)
SELECT 'que_math_g6_001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'erg', null, 'sub_math', 'lvl_math_g6', 'top_math_g6_num', 'single_choice', 'Which number is a natural number?', null, null, null, 'published'
WHERE NOT EXISTS (SELECT 1 FROM lms_questions WHERE id = 'que_math_g6_001');

INSERT INTO lms_questions (id, created_at, updated_at, tenant_id, scope, subject_id, level_id, topic_id, type, stem, choices, answer, metadata, status)
SELECT 'que_math_g6_002', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'erg', null, 'sub_math', 'lvl_math_g6', 'top_math_g6_num', 'single_choice', 'What is 12 + 8?', null, null, null, 'published'
WHERE NOT EXISTS (SELECT 1 FROM lms_questions WHERE id = 'que_math_g6_002');

INSERT INTO lms_quizzes (id, created_at, updated_at, tenant_id, scope, title, kind, subject_id, level_id, topic_ids, question_ids, slides, settings, result, theme, theme_id, status, version, package_hash, published_at)
SELECT 'qz_math_g6_base', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'erg', null, 'Grade 6 Mathematics Baseline', 'exam', 'sub_math', 'lvl_math_g6', null, null, null, null, null, null, null, 'published', 1, 'pkg_math_g6_v1', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM lms_quizzes WHERE id = 'qz_math_g6_base');

INSERT INTO lms_assignments (id, created_at, updated_at, tenant_id, academic_class_id, quiz_id, subject_id, student_ids, due_at, teacher_note, status, assigned_by, recipient_mode)
SELECT 'asg_ba_math_g6_001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'erg', 'cls_ba_2025_6a1', 'qz_math_g6_base', 'sub_math', null, CURRENT_TIMESTAMP, null, 'active', 'usr_teacher_ba', 'all'
WHERE NOT EXISTS (SELECT 1 FROM lms_assignments WHERE id = 'asg_ba_math_g6_001');

INSERT INTO lms_assignments (id, created_at, updated_at, tenant_id, academic_class_id, quiz_id, subject_id, student_ids, due_at, teacher_note, status, assigned_by, recipient_mode)
SELECT 'asg_cl_math_g6_001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'erg', 'cls_cl_2025_6a1', 'qz_math_g6_base', 'sub_math', null, CURRENT_TIMESTAMP, null, 'active', 'usr_teacher_cl', 'all'
WHERE NOT EXISTS (SELECT 1 FROM lms_assignments WHERE id = 'asg_cl_math_g6_001');

INSERT INTO lms_students (id, created_at, updated_at, tenant_id, school_id, academic_class_id, student_code, full_name, username, auth_user_id, email, status, metrics)
SELECT 'stu_ba_0001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'erg', 'sch_binh_an', 'cls_ba_2025_6a1', 'BA-2025-0001', 'Nguyen An', 'ba.2025.0001', 'usr_stu_ba_0001', 'ba.2025.0001@example.test', 'active', null
WHERE NOT EXISTS (SELECT 1 FROM lms_students WHERE id = 'stu_ba_0001');

INSERT INTO lms_students (id, created_at, updated_at, tenant_id, school_id, academic_class_id, student_code, full_name, username, auth_user_id, email, status, metrics)
SELECT 'stu_cl_0001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'erg', 'sch_cat_lai', 'cls_cl_2025_6a1', 'CL-2025-0001', 'Tran Binh', 'cl.2025.0001', 'usr_stu_cl_0001', 'cl.2025.0001@example.test', 'active', null
WHERE NOT EXISTS (SELECT 1 FROM lms_students WHERE id = 'stu_cl_0001');
