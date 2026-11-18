-- Owner decision (11/2026): a staff account is active as soon as it is created; "INVITED" is no longer used.
-- Activates the staff accounts still INVITED. Nothing else changes: must_change_password stays, so they
-- still verify their email and replace the temporary password at first login. Any account holding the
-- student role is left alone (students are handled by V143 and never enter the first-login lifecycle).
UPDATE users
SET status = 'ACTIVE',
    updated_at = CURRENT_TIMESTAMP
WHERE upper(status) = 'INVITED'
  AND NOT EXISTS (SELECT 1 FROM user_roles legacy
                  WHERE legacy.user_id = users.id AND legacy.role_id = 'role_lms_student')
  AND NOT EXISTS (SELECT 1 FROM user_role_assignments assignment
                  WHERE assignment.user_id = users.id AND assignment.role_id = 'role_lms_student');
