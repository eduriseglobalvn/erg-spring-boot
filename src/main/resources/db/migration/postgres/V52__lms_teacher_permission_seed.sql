-- LMS teacher permission catalog + role-group templates.
-- Mirrors the shared contract in docs/contracts/teacher-account-lifecycle-rbac.md.
-- Idempotent: safe to re-run. Deny/scope handled via existing user_permissions + user_access_scopes.

-- Permission groups -------------------------------------------------------
INSERT INTO permission_groups (id, created_at, updated_at, name, label, display_order)
SELECT 'pg_lms', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'lms', 'LMS Teaching Operations', 20
WHERE NOT EXISTS (SELECT 1 FROM permission_groups WHERE name = 'lms');

INSERT INTO permission_groups (id, created_at, updated_at, name, label, display_order)
SELECT 'pg_account', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'account', 'Account Self Service', 10
WHERE NOT EXISTS (SELECT 1 FROM permission_groups WHERE name = 'account');

-- Permissions -------------------------------------------------------------
CREATE TEMP TABLE lms_teacher_permission_seed (
    id varchar(36) PRIMARY KEY,
    name varchar(255) NOT NULL UNIQUE,
    group_name varchar(255) NOT NULL,
    label varchar(255) NOT NULL,
    description varchar(500) NOT NULL
) ON COMMIT DROP;

INSERT INTO lms_teacher_permission_seed (id, name, group_name, label, description) VALUES
    ('p_lms_portal_access', 'lms.portal.access', 'lms', 'Access LMS portal', 'Enter the LMS teacher portal'),
    ('p_lms_dashboard_read', 'lms.dashboard.read', 'lms', 'Read LMS dashboard', 'View the LMS teacher dashboard'),
    ('p_lms_assignment_read', 'lms.assignment.read', 'lms', 'Read assignments', 'View homework assignments'),
    ('p_lms_assignment_create', 'lms.assignment.create', 'lms', 'Create assignments', 'Create homework assignments'),
    ('p_lms_assignment_update', 'lms.assignment.update', 'lms', 'Update assignments', 'Edit homework assignments'),
    ('p_lms_assignment_delete', 'lms.assignment.delete', 'lms', 'Delete assignments', 'Remove homework assignments'),
    ('p_lms_assignment_assign', 'lms.assignment.assign', 'lms', 'Assign homework', 'Assign homework to classes or students'),
    ('p_lms_assignment_progress_read', 'lms.assignment.progress.read', 'lms', 'Read assignment progress', 'View homework submission progress'),
    ('p_lms_exercise_read', 'lms.exercise.read', 'lms', 'Read exercise bank', 'View the exercise bank'),
    ('p_lms_exercise_create', 'lms.exercise.create', 'lms', 'Create exercises', 'Create exercise-bank items'),
    ('p_lms_exercise_update', 'lms.exercise.update', 'lms', 'Update exercises', 'Edit exercise-bank items'),
    ('p_lms_exercise_delete', 'lms.exercise.delete', 'lms', 'Delete exercises', 'Remove exercise-bank items'),
    ('p_lms_exercise_assign', 'lms.exercise.assign', 'lms', 'Assign exercises', 'Assign exercise-bank items to homework'),
    ('p_lms_student_group_read', 'lms.student_group.read', 'lms', 'Read student groups', 'View student groups'),
    ('p_lms_student_group_create', 'lms.student_group.create', 'lms', 'Create student groups', 'Create student groups'),
    ('p_lms_student_group_update', 'lms.student_group.update', 'lms', 'Update student groups', 'Edit student groups'),
    ('p_lms_student_group_delete', 'lms.student_group.delete', 'lms', 'Delete student groups', 'Remove student groups'),
    ('p_lms_class_read', 'lms.class.read', 'lms', 'Read classes', 'View assigned classes'),
    ('p_lms_class_manage', 'lms.class.manage', 'lms', 'Manage classes', 'Manage class rosters and settings'),
    ('p_lms_student_read', 'lms.student.read', 'lms', 'Read students', 'View student profiles within scope'),
    ('p_lms_grade_read', 'lms.grade.read', 'lms', 'Read grades', 'View the grade book'),
    ('p_lms_grade_write', 'lms.grade.write', 'lms', 'Write grades', 'Enter and edit grades'),
    ('p_lms_grade_finalize', 'lms.grade.finalize', 'lms', 'Finalize grades', 'Finalize and lock grade books'),
    ('p_lms_grade_export', 'lms.grade.export', 'lms', 'Export grades', 'Export grade books'),
    ('p_lms_attendance_read', 'lms.attendance.read', 'lms', 'Read attendance', 'View attendance records'),
    ('p_lms_attendance_write', 'lms.attendance.write', 'lms', 'Write attendance', 'Record and edit attendance'),
    ('p_lms_attendance_export', 'lms.attendance.export', 'lms', 'Export attendance', 'Export attendance records'),
    ('p_lms_schedule_read', 'lms.schedule.read', 'lms', 'Read schedule', 'View the teaching calendar'),
    ('p_lms_schedule_create', 'lms.schedule.create', 'lms', 'Create schedule', 'Create calendar events'),
    ('p_lms_schedule_update', 'lms.schedule.update', 'lms', 'Update schedule', 'Edit calendar events'),
    ('p_lms_schedule_delete', 'lms.schedule.delete', 'lms', 'Delete schedule', 'Remove calendar events'),
    ('p_lms_schedule_publish', 'lms.schedule.publish', 'lms', 'Publish schedule', 'Publish the teaching calendar'),
    ('p_lms_schedule_import', 'lms.schedule.import', 'lms', 'Import schedule', 'Import teaching calendar data'),
    ('p_lms_class_log_read', 'lms.class_log.read', 'lms', 'Read class log', 'View the class log'),
    ('p_lms_class_log_write', 'lms.class_log.write', 'lms', 'Write class log', 'Record class-log entries'),
    ('p_lms_resource_read', 'lms.resource.read', 'lms', 'Read resources', 'View learning resources'),
    ('p_lms_resource_create', 'lms.resource.create', 'lms', 'Create resources', 'Create learning resources'),
    ('p_lms_resource_update', 'lms.resource.update', 'lms', 'Update resources', 'Edit learning resources'),
    ('p_lms_resource_delete', 'lms.resource.delete', 'lms', 'Delete resources', 'Remove learning resources'),
    ('p_lms_resource_share', 'lms.resource.share', 'lms', 'Share resources', 'Share learning resources'),
    ('p_lms_report_read', 'lms.report.read', 'lms', 'Read reports', 'View LMS reports'),
    ('p_lms_report_export', 'lms.report.export', 'lms', 'Export reports', 'Export LMS reports'),
    ('p_lms_notification_read', 'lms.notification.read', 'lms', 'Read notifications', 'View LMS notifications'),
    ('p_lms_notification_prefs_update', 'lms.notification.preferences.update', 'lms', 'Update notification preferences', 'Manage notification preferences'),
    ('p_acct_self_read', 'account.self.read', 'account', 'Read own account', 'View own account and profile'),
    ('p_acct_self_update', 'account.self.update', 'account', 'Update own account', 'Edit own profile and phone'),
    ('p_acct_recovery_email_update', 'account.self.recovery_email.update', 'account', 'Update recovery email', 'Set and verify own recovery email'),
    ('p_acct_password_change', 'account.self.password.change', 'account', 'Change own password', 'Change own password'),
    ('p_acct_sessions_read', 'account.self.sessions.read', 'account', 'Read own sessions', 'View own active sessions');

INSERT INTO permissions (id, created_at, updated_at, name, group_name, label, description)
SELECT v.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, v.name, v.group_name, v.label, v.description
FROM lms_teacher_permission_seed v
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permissions.name = v.name);

-- Role-group templates ----------------------------------------------------
CREATE TEMP TABLE lms_teacher_role_seed (
    id varchar(36) PRIMARY KEY,
    name varchar(255) NOT NULL UNIQUE,
    description varchar(500) NOT NULL
) ON COMMIT DROP;

INSERT INTO lms_teacher_role_seed (id, name, description) VALUES
    ('role_lms_teacher_standard', 'lms_teacher_standard', 'Teacher operating assigned classes: homework, grades, attendance, schedule, class log, resources, reports'),
    ('role_lms_homeroom_teacher', 'lms_homeroom_teacher', 'Standard plus student/group management and class reports within homeroom scope'),
    ('role_lms_subject_teacher', 'lms_subject_teacher', 'Assigned subject/classes only; no student profile management beyond scope'),
    ('role_lms_teacher_assistant', 'lms_teacher_assistant', 'Read classes; assist homework/attendance with limited rights; no finalize/sensitive export'),
    ('role_lms_teacher_readonly', 'lms_teacher_readonly', 'Read-only dashboard, classes, homework, grades, attendance, schedule, reports'),
    ('role_lms_teacher_lead', 'lms_teacher_lead', 'Coordinates teaching within school/center scope; manages schedule/resources/reports; no IAM admin');

INSERT INTO roles (id, created_at, updated_at, name, description, is_default)
SELECT v.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, v.name, v.description, false
FROM lms_teacher_role_seed v
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE roles.name = v.name);

-- Role-group -> permission grants (matched by permission id) ---------------
INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM (VALUES
    -- Self-service (all teacher role groups)
    ('role_lms_teacher_standard', 'p_acct_self_read'),
    ('role_lms_teacher_standard', 'p_acct_self_update'),
    ('role_lms_teacher_standard', 'p_acct_recovery_email_update'),
    ('role_lms_teacher_standard', 'p_acct_password_change'),
    ('role_lms_teacher_standard', 'p_acct_sessions_read'),
    -- Standard teaching operations
    ('role_lms_teacher_standard', 'p_lms_dashboard_read'),
    ('role_lms_teacher_standard', 'p_lms_assignment_read'),
    ('role_lms_teacher_standard', 'p_lms_assignment_create'),
    ('role_lms_teacher_standard', 'p_lms_assignment_update'),
    ('role_lms_teacher_standard', 'p_lms_assignment_assign'),
    ('role_lms_teacher_standard', 'p_lms_assignment_progress_read'),
    ('role_lms_teacher_standard', 'p_lms_exercise_read'),
    ('role_lms_teacher_standard', 'p_lms_exercise_create'),
    ('role_lms_teacher_standard', 'p_lms_exercise_update'),
    ('role_lms_teacher_standard', 'p_lms_exercise_assign'),
    ('role_lms_teacher_standard', 'p_lms_class_read'),
    ('role_lms_teacher_standard', 'p_lms_student_read'),
    ('role_lms_teacher_standard', 'p_lms_grade_read'),
    ('role_lms_teacher_standard', 'p_lms_grade_write'),
    ('role_lms_teacher_standard', 'p_lms_attendance_read'),
    ('role_lms_teacher_standard', 'p_lms_attendance_write'),
    ('role_lms_teacher_standard', 'p_lms_schedule_read'),
    ('role_lms_teacher_standard', 'p_lms_class_log_read'),
    ('role_lms_teacher_standard', 'p_lms_class_log_write'),
    ('role_lms_teacher_standard', 'p_lms_resource_read'),
    ('role_lms_teacher_standard', 'p_lms_resource_share'),
    ('role_lms_teacher_standard', 'p_lms_report_read'),
    ('role_lms_teacher_standard', 'p_lms_notification_read'),
    ('role_lms_teacher_standard', 'p_lms_notification_prefs_update'),
    -- Homeroom = standard + student/group management
    ('role_lms_homeroom_teacher', 'p_acct_self_read'),
    ('role_lms_homeroom_teacher', 'p_acct_self_update'),
    ('role_lms_homeroom_teacher', 'p_acct_recovery_email_update'),
    ('role_lms_homeroom_teacher', 'p_acct_password_change'),
    ('role_lms_homeroom_teacher', 'p_acct_sessions_read'),
    ('role_lms_homeroom_teacher', 'p_lms_dashboard_read'),
    ('role_lms_homeroom_teacher', 'p_lms_assignment_read'),
    ('role_lms_homeroom_teacher', 'p_lms_assignment_create'),
    ('role_lms_homeroom_teacher', 'p_lms_assignment_update'),
    ('role_lms_homeroom_teacher', 'p_lms_assignment_assign'),
    ('role_lms_homeroom_teacher', 'p_lms_assignment_progress_read'),
    ('role_lms_homeroom_teacher', 'p_lms_exercise_read'),
    ('role_lms_homeroom_teacher', 'p_lms_exercise_create'),
    ('role_lms_homeroom_teacher', 'p_lms_exercise_update'),
    ('role_lms_homeroom_teacher', 'p_lms_exercise_assign'),
    ('role_lms_homeroom_teacher', 'p_lms_class_read'),
    ('role_lms_homeroom_teacher', 'p_lms_class_manage'),
    ('role_lms_homeroom_teacher', 'p_lms_student_read'),
    ('role_lms_homeroom_teacher', 'p_lms_student_group_read'),
    ('role_lms_homeroom_teacher', 'p_lms_student_group_create'),
    ('role_lms_homeroom_teacher', 'p_lms_student_group_update'),
    ('role_lms_homeroom_teacher', 'p_lms_student_group_delete'),
    ('role_lms_homeroom_teacher', 'p_lms_grade_read'),
    ('role_lms_homeroom_teacher', 'p_lms_grade_write'),
    ('role_lms_homeroom_teacher', 'p_lms_attendance_read'),
    ('role_lms_homeroom_teacher', 'p_lms_attendance_write'),
    ('role_lms_homeroom_teacher', 'p_lms_schedule_read'),
    ('role_lms_homeroom_teacher', 'p_lms_class_log_read'),
    ('role_lms_homeroom_teacher', 'p_lms_class_log_write'),
    ('role_lms_homeroom_teacher', 'p_lms_resource_read'),
    ('role_lms_homeroom_teacher', 'p_lms_resource_share'),
    ('role_lms_homeroom_teacher', 'p_lms_report_read'),
    ('role_lms_homeroom_teacher', 'p_lms_report_export'),
    ('role_lms_homeroom_teacher', 'p_lms_notification_read'),
    ('role_lms_homeroom_teacher', 'p_lms_notification_prefs_update'),
    -- Subject teacher = standard scope, no student-profile management
    ('role_lms_subject_teacher', 'p_acct_self_read'),
    ('role_lms_subject_teacher', 'p_acct_self_update'),
    ('role_lms_subject_teacher', 'p_acct_recovery_email_update'),
    ('role_lms_subject_teacher', 'p_acct_password_change'),
    ('role_lms_subject_teacher', 'p_acct_sessions_read'),
    ('role_lms_subject_teacher', 'p_lms_dashboard_read'),
    ('role_lms_subject_teacher', 'p_lms_assignment_read'),
    ('role_lms_subject_teacher', 'p_lms_assignment_create'),
    ('role_lms_subject_teacher', 'p_lms_assignment_update'),
    ('role_lms_subject_teacher', 'p_lms_assignment_assign'),
    ('role_lms_subject_teacher', 'p_lms_assignment_progress_read'),
    ('role_lms_subject_teacher', 'p_lms_exercise_read'),
    ('role_lms_subject_teacher', 'p_lms_exercise_create'),
    ('role_lms_subject_teacher', 'p_lms_exercise_update'),
    ('role_lms_subject_teacher', 'p_lms_class_read'),
    ('role_lms_subject_teacher', 'p_lms_grade_read'),
    ('role_lms_subject_teacher', 'p_lms_grade_write'),
    ('role_lms_subject_teacher', 'p_lms_attendance_read'),
    ('role_lms_subject_teacher', 'p_lms_attendance_write'),
    ('role_lms_subject_teacher', 'p_lms_schedule_read'),
    ('role_lms_subject_teacher', 'p_lms_class_log_read'),
    ('role_lms_subject_teacher', 'p_lms_class_log_write'),
    ('role_lms_subject_teacher', 'p_lms_resource_read'),
    ('role_lms_subject_teacher', 'p_lms_report_read'),
    ('role_lms_subject_teacher', 'p_lms_notification_read'),
    ('role_lms_subject_teacher', 'p_lms_notification_prefs_update'),
    -- Assistant = limited support, no finalize/export
    ('role_lms_teacher_assistant', 'p_acct_self_read'),
    ('role_lms_teacher_assistant', 'p_acct_self_update'),
    ('role_lms_teacher_assistant', 'p_acct_recovery_email_update'),
    ('role_lms_teacher_assistant', 'p_acct_password_change'),
    ('role_lms_teacher_assistant', 'p_acct_sessions_read'),
    ('role_lms_teacher_assistant', 'p_lms_dashboard_read'),
    ('role_lms_teacher_assistant', 'p_lms_assignment_read'),
    ('role_lms_teacher_assistant', 'p_lms_assignment_progress_read'),
    ('role_lms_teacher_assistant', 'p_lms_exercise_read'),
    ('role_lms_teacher_assistant', 'p_lms_class_read'),
    ('role_lms_teacher_assistant', 'p_lms_grade_read'),
    ('role_lms_teacher_assistant', 'p_lms_attendance_read'),
    ('role_lms_teacher_assistant', 'p_lms_attendance_write'),
    ('role_lms_teacher_assistant', 'p_lms_schedule_read'),
    ('role_lms_teacher_assistant', 'p_lms_class_log_read'),
    ('role_lms_teacher_assistant', 'p_lms_resource_read'),
    ('role_lms_teacher_assistant', 'p_lms_report_read'),
    ('role_lms_teacher_assistant', 'p_lms_notification_read'),
    -- Read-only
    ('role_lms_teacher_readonly', 'p_acct_self_read'),
    ('role_lms_teacher_readonly', 'p_acct_password_change'),
    ('role_lms_teacher_readonly', 'p_acct_sessions_read'),
    ('role_lms_teacher_readonly', 'p_lms_dashboard_read'),
    ('role_lms_teacher_readonly', 'p_lms_assignment_read'),
    ('role_lms_teacher_readonly', 'p_lms_assignment_progress_read'),
    ('role_lms_teacher_readonly', 'p_lms_class_read'),
    ('role_lms_teacher_readonly', 'p_lms_grade_read'),
    ('role_lms_teacher_readonly', 'p_lms_attendance_read'),
    ('role_lms_teacher_readonly', 'p_lms_schedule_read'),
    ('role_lms_teacher_readonly', 'p_lms_class_log_read'),
    ('role_lms_teacher_readonly', 'p_lms_resource_read'),
    ('role_lms_teacher_readonly', 'p_lms_report_read'),
    ('role_lms_teacher_readonly', 'p_lms_notification_read'),
    -- Lead = coordination within school/center scope
    ('role_lms_teacher_lead', 'p_acct_self_read'),
    ('role_lms_teacher_lead', 'p_acct_self_update'),
    ('role_lms_teacher_lead', 'p_acct_recovery_email_update'),
    ('role_lms_teacher_lead', 'p_acct_password_change'),
    ('role_lms_teacher_lead', 'p_acct_sessions_read'),
    ('role_lms_teacher_lead', 'p_lms_dashboard_read'),
    ('role_lms_teacher_lead', 'p_lms_assignment_read'),
    ('role_lms_teacher_lead', 'p_lms_assignment_create'),
    ('role_lms_teacher_lead', 'p_lms_assignment_update'),
    ('role_lms_teacher_lead', 'p_lms_assignment_assign'),
    ('role_lms_teacher_lead', 'p_lms_assignment_progress_read'),
    ('role_lms_teacher_lead', 'p_lms_exercise_read'),
    ('role_lms_teacher_lead', 'p_lms_exercise_create'),
    ('role_lms_teacher_lead', 'p_lms_exercise_update'),
    ('role_lms_teacher_lead', 'p_lms_exercise_assign'),
    ('role_lms_teacher_lead', 'p_lms_class_read'),
    ('role_lms_teacher_lead', 'p_lms_student_read'),
    ('role_lms_teacher_lead', 'p_lms_student_group_read'),
    ('role_lms_teacher_lead', 'p_lms_student_group_create'),
    ('role_lms_teacher_lead', 'p_lms_student_group_update'),
    ('role_lms_teacher_lead', 'p_lms_grade_read'),
    ('role_lms_teacher_lead', 'p_lms_grade_write'),
    ('role_lms_teacher_lead', 'p_lms_grade_finalize'),
    ('role_lms_teacher_lead', 'p_lms_grade_export'),
    ('role_lms_teacher_lead', 'p_lms_attendance_read'),
    ('role_lms_teacher_lead', 'p_lms_attendance_write'),
    ('role_lms_teacher_lead', 'p_lms_attendance_export'),
    ('role_lms_teacher_lead', 'p_lms_schedule_read'),
    ('role_lms_teacher_lead', 'p_lms_schedule_create'),
    ('role_lms_teacher_lead', 'p_lms_schedule_update'),
    ('role_lms_teacher_lead', 'p_lms_schedule_publish'),
    ('role_lms_teacher_lead', 'p_lms_schedule_import'),
    ('role_lms_teacher_lead', 'p_lms_class_log_read'),
    ('role_lms_teacher_lead', 'p_lms_class_log_write'),
    ('role_lms_teacher_lead', 'p_lms_resource_read'),
    ('role_lms_teacher_lead', 'p_lms_resource_create'),
    ('role_lms_teacher_lead', 'p_lms_resource_update'),
    ('role_lms_teacher_lead', 'p_lms_resource_share'),
    ('role_lms_teacher_lead', 'p_lms_report_read'),
    ('role_lms_teacher_lead', 'p_lms_report_export'),
    ('role_lms_teacher_lead', 'p_lms_notification_read'),
    ('role_lms_teacher_lead', 'p_lms_notification_prefs_update')
) AS desired(role_seed_id, permission_seed_id)
JOIN lms_teacher_role_seed role_seed ON role_seed.id = desired.role_seed_id
JOIN roles role ON role.name = role_seed.name
JOIN lms_teacher_permission_seed permission_seed ON permission_seed.id = desired.permission_seed_id
JOIN permissions permission ON permission.name = permission_seed.name
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions
    WHERE role_permissions.role_id = role.id AND role_permissions.permission_id = permission.id
);

-- Reconcile role templates with the frontend contract (minimum required grants only).
INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM (VALUES
    ('role_lms_teacher_standard', 'p_lms_student_group_read'),
    ('role_lms_teacher_standard', 'p_lms_student_group_create'),
    ('role_lms_teacher_standard', 'p_lms_student_group_update'),
    ('role_lms_teacher_standard', 'p_lms_schedule_create'),
    ('role_lms_teacher_standard', 'p_lms_schedule_update'),
    ('role_lms_teacher_standard', 'p_lms_resource_create'),
    ('role_lms_teacher_standard', 'p_lms_resource_update'),
    ('role_lms_homeroom_teacher', 'p_lms_schedule_create'),
    ('role_lms_homeroom_teacher', 'p_lms_schedule_update'),
    ('role_lms_homeroom_teacher', 'p_lms_resource_create'),
    ('role_lms_homeroom_teacher', 'p_lms_resource_update'),
    ('role_lms_subject_teacher', 'p_lms_student_read'),
    ('role_lms_subject_teacher', 'p_lms_student_group_read'),
    ('role_lms_subject_teacher', 'p_lms_student_group_create'),
    ('role_lms_subject_teacher', 'p_lms_student_group_update'),
    ('role_lms_subject_teacher', 'p_lms_exercise_assign'),
    ('role_lms_subject_teacher', 'p_lms_schedule_create'),
    ('role_lms_subject_teacher', 'p_lms_schedule_update'),
    ('role_lms_subject_teacher', 'p_lms_resource_create'),
    ('role_lms_subject_teacher', 'p_lms_resource_update'),
    ('role_lms_subject_teacher', 'p_lms_resource_share'),
    ('role_lms_teacher_assistant', 'p_lms_student_read'),
    ('role_lms_teacher_assistant', 'p_lms_student_group_read'),
    ('role_lms_teacher_assistant', 'p_lms_assignment_update'),
    ('role_lms_teacher_assistant', 'p_lms_class_log_write'),
    ('role_lms_teacher_readonly', 'p_lms_student_read'),
    ('role_lms_teacher_readonly', 'p_lms_exercise_read')
) AS desired(role_seed_id, permission_seed_id)
JOIN lms_teacher_role_seed role_seed ON role_seed.id = desired.role_seed_id
JOIN roles role ON role.name = role_seed.name
JOIN lms_teacher_permission_seed permission_seed ON permission_seed.id = desired.permission_seed_id
JOIN permissions permission ON permission.name = permission_seed.name
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions existing
    WHERE existing.role_id = role.id AND existing.permission_id = permission.id
);

-- Every teacher role group can enter the LMS portal; route/action permissions still apply afterwards.
INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
CROSS JOIN permissions permission
WHERE role.name IN (
    'lms_teacher_standard',
    'lms_homeroom_teacher',
    'lms_subject_teacher',
    'lms_teacher_assistant',
    'lms_teacher_readonly',
    'lms_teacher_lead'
)
AND permission.name = 'lms.portal.access'
AND NOT EXISTS (
    SELECT 1 FROM role_permissions existing
    WHERE existing.role_id = role.id AND existing.permission_id = permission.id
);
