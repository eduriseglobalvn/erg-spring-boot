-- V54 revoked lms.class_log.write and lms.attendance.write from the teacher role groups while the server did not
-- implement them. The class log (V153) and attendance by period now exist, so teachers must hold them again.
-- lms.instructor (role_lms_teacher) is the role the real teacher accounts carry; it only had class/unit read.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('lms.class_log.read', 'lms.class_log.write', 'lms.attendance.read', 'lms.attendance.write')
WHERE r.name = 'lms.instructor'
  AND NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = r.id AND x.permission_id = p.id);

-- role groups of V52 (the read-only group stays read-only)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('lms.class_log.write', 'lms.attendance.write')
WHERE r.name IN ('lms_teacher_standard', 'lms_homeroom_teacher', 'lms_subject_teacher', 'lms_teacher_assistant', 'lms_teacher_lead')
  AND NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = r.id AND x.permission_id = p.id);
