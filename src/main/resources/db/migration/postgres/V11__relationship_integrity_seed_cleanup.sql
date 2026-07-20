INSERT INTO users (
    id,
    created_at,
    updated_at,
    email,
    password_hash,
    full_name,
    status,
    provider,
    account_type,
    last_login_provider,
    tenant_id,
    job_title,
    is_profile_completed,
    login_count
)
SELECT
    seed.id,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    seed.email,
    NULL,
    seed.full_name,
    'ACTIVE',
    'seed',
    seed.account_type,
    'seed',
    'erg',
    seed.job_title,
    true,
    0
FROM (VALUES
    ('usr_teacher_ba', 'teacher.binh.an@example.test', 'Binh An Teacher', 'erg', 'LMS teacher'),
    ('usr_teacher_cl', 'teacher.cat.lai@example.test', 'Cat Lai Teacher', 'erg', 'LMS teacher'),
    ('usr_stu_ba_0001', 'ba.2025.0001@example.test', 'Nguyen An', 'erg', 'LMS student'),
    ('usr_stu_cl_0001', 'cl.2025.0001@example.test', 'Tran Binh', 'erg', 'LMS student'),
    ('usr_relationship_placeholder', 'relationship-placeholder@example.test', 'Relationship Placeholder', 'system', 'Data integrity placeholder')
) AS seed(id, email, full_name, account_type, job_title)
WHERE NOT EXISTS (SELECT 1 FROM users WHERE users.id = seed.id);

INSERT INTO user_roles (user_id, role_id)
SELECT seed.user_id, seed.role_id
FROM (VALUES
    ('usr_teacher_ba', 'role_lms_teacher'),
    ('usr_teacher_cl', 'role_lms_teacher'),
    ('usr_stu_ba_0001', 'role_lms_student'),
    ('usr_stu_cl_0001', 'role_lms_student')
) AS seed(user_id, role_id)
WHERE EXISTS (SELECT 1 FROM users WHERE users.id = seed.user_id)
  AND EXISTS (SELECT 1 FROM roles WHERE roles.id = seed.role_id)
  AND NOT EXISTS (
      SELECT 1 FROM user_roles
      WHERE user_roles.user_id = seed.user_id
        AND user_roles.role_id = seed.role_id
  );

INSERT INTO user_access_scopes (
    id,
    created_at,
    updated_at,
    user_id,
    education_unit_id,
    modules,
    role
)
SELECT
    seed.id,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    seed.user_id,
    seed.education_unit_id,
    seed.modules,
    seed.role_name
FROM (VALUES
    ('scope_teacher_ba', 'usr_teacher_ba', 'sch_binh_an', 'lms,hoclieu', 'lms.instructor'),
    ('scope_teacher_cl', 'usr_teacher_cl', 'sch_cat_lai', 'lms,hoclieu', 'lms.instructor'),
    ('scope_stu_ba_0001', 'usr_stu_ba_0001', 'sch_binh_an', 'lms,elearning', 'lms.learner'),
    ('scope_stu_cl_0001', 'usr_stu_cl_0001', 'sch_cat_lai', 'lms,elearning', 'lms.learner')
) AS seed(id, user_id, education_unit_id, modules, role_name)
WHERE EXISTS (SELECT 1 FROM users WHERE users.id = seed.user_id)
  AND EXISTS (SELECT 1 FROM education_units WHERE education_units.id = seed.education_unit_id)
  AND NOT EXISTS (SELECT 1 FROM user_access_scopes WHERE user_access_scopes.id = seed.id);

INSERT INTO profiles (
    id,
    created_at,
    updated_at,
    user_id,
    full_name,
    is_profile_completed,
    internal_note
)
SELECT
    seed.id,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    seed.user_id,
    seed.full_name,
    true,
    'Seeded by relationship integrity cleanup'
FROM (VALUES
    ('prof_teacher_ba', 'usr_teacher_ba', 'Binh An Teacher'),
    ('prof_teacher_cl', 'usr_teacher_cl', 'Cat Lai Teacher'),
    ('prof_stu_ba_0001', 'usr_stu_ba_0001', 'Nguyen An'),
    ('prof_stu_cl_0001', 'usr_stu_cl_0001', 'Tran Binh')
) AS seed(id, user_id, full_name)
WHERE EXISTS (SELECT 1 FROM users WHERE users.id = seed.user_id)
  AND NOT EXISTS (SELECT 1 FROM profiles WHERE profiles.user_id = seed.user_id);

DELETE FROM profiles profile
USING (
    SELECT id,
           row_number() OVER (
               PARTITION BY user_id
               ORDER BY updated_at DESC NULLS LAST, created_at DESC NULLS LAST, id
           ) AS row_number
    FROM profiles
) duplicate
WHERE profile.id = duplicate.id
  AND duplicate.row_number > 1;

DELETE FROM user_roles user_role
WHERE NOT EXISTS (SELECT 1 FROM users WHERE users.id = user_role.user_id)
   OR NOT EXISTS (SELECT 1 FROM roles WHERE roles.id = user_role.role_id);

DELETE FROM role_permissions role_permission
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE roles.id = role_permission.role_id)
   OR NOT EXISTS (SELECT 1 FROM permissions WHERE permissions.id = role_permission.permission_id);

UPDATE permissions permission
SET group_name = NULL
WHERE permission.group_name IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM permission_groups WHERE permission_groups.name = permission.group_name);

UPDATE education_units unit
SET parent_id = NULL
WHERE unit.parent_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM education_units parent WHERE parent.id = unit.parent_id);

UPDATE education_units unit
SET owner_user_id = NULL
WHERE unit.owner_user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM users WHERE users.id = unit.owner_user_id);

UPDATE education_units unit
SET created_by = NULL
WHERE unit.created_by IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM users WHERE users.id = unit.created_by);

DELETE FROM user_sessions session
WHERE NOT EXISTS (SELECT 1 FROM users WHERE users.id = session.user_id);

UPDATE auth_login_attempts attempt
SET user_id = NULL
WHERE attempt.user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM users WHERE users.id = attempt.user_id);

DELETE FROM profiles profile
WHERE NOT EXISTS (SELECT 1 FROM users WHERE users.id = profile.user_id);

DELETE FROM social_accounts account
WHERE NOT EXISTS (SELECT 1 FROM users WHERE users.id = account.user_id);

DELETE FROM certificates certificate
WHERE NOT EXISTS (SELECT 1 FROM users WHERE users.id = certificate.user_id);

DELETE FROM user_activities activity
WHERE NOT EXISTS (SELECT 1 FROM users WHERE users.id = activity.user_id);

UPDATE user_activities activity
SET target_user_id = NULL
WHERE activity.target_user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM users WHERE users.id = activity.target_user_id);

DELETE FROM user_permissions permission_override
WHERE NOT EXISTS (SELECT 1 FROM users WHERE users.id = permission_override.user_id)
   OR NOT EXISTS (SELECT 1 FROM permissions WHERE permissions.name = permission_override.permission);

UPDATE user_permissions permission_override
SET created_by = NULL
WHERE permission_override.created_by IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM users WHERE users.id = permission_override.created_by);

DELETE FROM user_access_scopes scope
WHERE NOT EXISTS (SELECT 1 FROM users WHERE users.id = scope.user_id)
   OR NOT EXISTS (SELECT 1 FROM education_units WHERE education_units.id = scope.education_unit_id);

DELETE FROM work_shifts shift
WHERE NOT EXISTS (SELECT 1 FROM users WHERE users.id = shift.user_id);

UPDATE work_shifts shift
SET school_id = NULL
WHERE shift.school_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM education_units WHERE education_units.id = shift.school_id);

UPDATE work_shifts shift
SET confirmed_by = NULL
WHERE shift.confirmed_by IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM users WHERE users.id = shift.confirmed_by);

DELETE FROM academic_classes classroom
WHERE NOT EXISTS (SELECT 1 FROM education_units WHERE education_units.id = classroom.school_id);

UPDATE academic_classes classroom
SET homeroom_teacher_id = NULL
WHERE classroom.homeroom_teacher_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM users WHERE users.id = classroom.homeroom_teacher_id);

DELETE FROM lms_assignments assignment
WHERE NOT EXISTS (SELECT 1 FROM academic_classes classroom WHERE classroom.id = assignment.academic_class_id);

UPDATE lms_assignments assignment
SET quiz_id = NULL
WHERE assignment.quiz_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM lms_quizzes quiz WHERE quiz.id = assignment.quiz_id);

UPDATE lms_assignments assignment
SET subject_id = NULL
WHERE assignment.subject_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM lms_subjects subject WHERE subject.id = assignment.subject_id);

UPDATE lms_assignments assignment
SET assigned_by = NULL
WHERE assignment.assigned_by IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM users WHERE users.id = assignment.assigned_by);

UPDATE lms_levels level
SET subject_id = NULL
WHERE level.subject_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM lms_subjects subject WHERE subject.id = level.subject_id);

UPDATE lms_topics topic
SET level_id = NULL
WHERE topic.level_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM lms_levels level WHERE level.id = topic.level_id);

UPDATE lms_questions question
SET subject_id = NULL
WHERE question.subject_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM lms_subjects subject WHERE subject.id = question.subject_id);

UPDATE lms_questions question
SET level_id = NULL
WHERE question.level_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM lms_levels level WHERE level.id = question.level_id);

UPDATE lms_questions question
SET topic_id = NULL
WHERE question.topic_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM lms_topics topic WHERE topic.id = question.topic_id);

UPDATE lms_quizzes quiz
SET subject_id = NULL
WHERE quiz.subject_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM lms_subjects subject WHERE subject.id = quiz.subject_id);

UPDATE lms_quizzes quiz
SET level_id = NULL
WHERE quiz.level_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM lms_levels level WHERE level.id = quiz.level_id);

UPDATE lms_students student
SET school_id = NULL
WHERE student.school_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM education_units WHERE education_units.id = student.school_id);

UPDATE lms_students student
SET academic_class_id = NULL
WHERE student.academic_class_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM academic_classes classroom WHERE classroom.id = student.academic_class_id);

UPDATE lms_students student
SET auth_user_id = NULL
WHERE student.auth_user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM users WHERE users.id = student.auth_user_id);

UPDATE lms_attempts attempt
SET assignment_id = NULL
WHERE attempt.assignment_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM lms_assignments assignment WHERE assignment.id = attempt.assignment_id);

UPDATE lms_attempts attempt
SET quiz_id = NULL
WHERE attempt.quiz_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM lms_quizzes quiz WHERE quiz.id = attempt.quiz_id);

UPDATE lms_attempts attempt
SET student_id = NULL
WHERE attempt.student_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM lms_students student WHERE student.id = attempt.student_id);

UPDATE hoclieu_taxonomy taxonomy
SET parent_id = NULL
WHERE taxonomy.parent_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM hoclieu_taxonomy parent WHERE parent.id = taxonomy.parent_id);

UPDATE hoclieu_taxonomy taxonomy
SET subject_id = NULL
WHERE taxonomy.subject_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM hoclieu_taxonomy subject WHERE subject.id = taxonomy.subject_id);

UPDATE hoclieu_taxonomy taxonomy
SET category_id = NULL
WHERE taxonomy.category_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM hoclieu_taxonomy category WHERE category.id = taxonomy.category_id);

DELETE FROM hoclieu_resources resource
WHERE NOT EXISTS (SELECT 1 FROM hoclieu_taxonomy subject WHERE subject.id = resource.subject_id);

UPDATE hoclieu_resources resource
SET category_id = NULL
WHERE resource.category_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM hoclieu_taxonomy category WHERE category.id = resource.category_id);

UPDATE hoclieu_resources resource
SET section_id = NULL
WHERE resource.section_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM hoclieu_taxonomy section WHERE section.id = resource.section_id);

UPDATE hoclieu_resources resource
SET created_by = NULL
WHERE resource.created_by IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM users WHERE users.id = resource.created_by);

UPDATE hoclieu_resources resource
SET updated_by = NULL
WHERE resource.updated_by IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM users WHERE users.id = resource.updated_by);

DELETE FROM hoclieu_assets asset
WHERE NOT EXISTS (SELECT 1 FROM hoclieu_resources resource WHERE resource.id = asset.resource_id);

DELETE FROM hoclieu_resource_items item
WHERE NOT EXISTS (SELECT 1 FROM hoclieu_resources resource WHERE resource.id = item.resource_id);

UPDATE hoclieu_resource_items item
SET asset_id = NULL
WHERE item.asset_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM hoclieu_assets asset WHERE asset.id = item.asset_id);

DELETE FROM hoclieu_asset_slide_cache slide
WHERE NOT EXISTS (SELECT 1 FROM hoclieu_assets asset WHERE asset.id = slide.asset_id);

DELETE FROM hoclieu_progress_state progress
WHERE NOT EXISTS (SELECT 1 FROM users WHERE users.id = progress.user_id)
   OR NOT EXISTS (SELECT 1 FROM education_units WHERE education_units.id = progress.school_id)
   OR NOT EXISTS (SELECT 1 FROM hoclieu_resources resource WHERE resource.id = progress.resource_id)
   OR NOT EXISTS (SELECT 1 FROM hoclieu_assets asset WHERE asset.id = progress.asset_id);

DELETE FROM hoclieu_slide_annotations annotation
WHERE NOT EXISTS (SELECT 1 FROM users WHERE users.id = annotation.user_id)
   OR NOT EXISTS (SELECT 1 FROM education_units WHERE education_units.id = annotation.school_id)
   OR NOT EXISTS (SELECT 1 FROM hoclieu_assets asset WHERE asset.id = annotation.asset_id);

DELETE FROM crm_school_profiles profile
WHERE NOT EXISTS (SELECT 1 FROM education_units school WHERE school.id = profile.school_id);

UPDATE crm_school_profiles profile
SET owner_user_id = NULL
WHERE profile.owner_user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM users WHERE users.id = profile.owner_user_id);

UPDATE crm_school_profiles profile
SET lead_owner_user_id = NULL
WHERE profile.lead_owner_user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM users WHERE users.id = profile.lead_owner_user_id);

UPDATE crm_school_profiles profile
SET manager_user_id = NULL
WHERE profile.manager_user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM users WHERE users.id = profile.manager_user_id);

UPDATE crm_school_profiles profile
SET introduced_by_user_id = NULL
WHERE profile.introduced_by_user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM users WHERE users.id = profile.introduced_by_user_id);

DELETE FROM crm_school_contacts contact
WHERE NOT EXISTS (SELECT 1 FROM education_units school WHERE school.id = contact.school_id);

DELETE FROM crm_school_deals deal
WHERE NOT EXISTS (SELECT 1 FROM education_units school WHERE school.id = deal.school_id);

UPDATE crm_school_deals deal
SET owner_user_id = 'usr_relationship_placeholder'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE users.id = deal.owner_user_id);

DELETE FROM crm_school_activities activity
WHERE NOT EXISTS (SELECT 1 FROM education_units school WHERE school.id = activity.school_id);

UPDATE crm_school_activities activity
SET deal_id = NULL
WHERE activity.deal_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM crm_school_deals deal WHERE deal.id = activity.deal_id);

UPDATE crm_school_activities activity
SET actor_user_id = NULL
WHERE activity.actor_user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM users WHERE users.id = activity.actor_user_id);

DELETE FROM crm_referrals referral
WHERE NOT EXISTS (SELECT 1 FROM education_units school WHERE school.id = referral.school_id);

UPDATE crm_referrals referral
SET referrer_user_id = NULL
WHERE referral.referrer_user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM users WHERE users.id = referral.referrer_user_id);

UPDATE notification_messages message
SET created_by = NULL
WHERE message.created_by IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM users WHERE users.id = message.created_by);

DELETE FROM notification_recipients recipient
WHERE NOT EXISTS (SELECT 1 FROM notification_messages message WHERE message.id = recipient.notification_id)
   OR NOT EXISTS (SELECT 1 FROM users WHERE users.id = recipient.user_id);

DELETE FROM notification_device_registrations device
WHERE NOT EXISTS (SELECT 1 FROM users WHERE users.id = device.user_id);

DELETE FROM notification_preferences preference
WHERE NOT EXISTS (SELECT 1 FROM users WHERE users.id = preference.user_id);

DELETE FROM notification_outbox outbox
WHERE NOT EXISTS (SELECT 1 FROM notification_messages message WHERE message.id = outbox.notification_id);

UPDATE notification_delivery_attempts attempt
SET outbox_id = NULL
WHERE attempt.outbox_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM notification_outbox outbox WHERE outbox.id = attempt.outbox_id);

DELETE FROM notification_delivery_attempts attempt
WHERE NOT EXISTS (SELECT 1 FROM notification_messages message WHERE message.id = attempt.notification_id);

UPDATE notification_delivery_attempts attempt
SET device_registration_id = NULL
WHERE attempt.device_registration_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM notification_device_registrations device
      WHERE device.id = attempt.device_registration_id
  );

DELETE FROM notification_topic_memberships topic
WHERE NOT EXISTS (
    SELECT 1
    FROM notification_device_registrations device
    WHERE device.id = topic.device_registration_id
);
