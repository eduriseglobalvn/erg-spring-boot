DELETE FROM role_permissions rp
USING roles r, permissions p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND r.name IN (
      'lms_teacher_standard',
      'lms_homeroom_teacher',
      'lms_subject_teacher',
      'lms_teacher_assistant',
      'lms_teacher_readonly',
      'lms_teacher_lead'
  )
  AND p.name IN (
      'lms.exercise.assign',
      'lms.grade.write',
      'lms.grade.finalize',
      'lms.grade.export',
      'lms.attendance.write',
      'lms.attendance.export',
      'lms.class_log.write',
      'lms.report.export'
  );
