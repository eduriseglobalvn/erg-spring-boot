-- Owner rule (18/11/2026): every staff account proves a working mailbox at its first login, the seeded admin included.
-- V55 marked the admin's recovery email as verified without any address, so its first login skipped the email step
-- and went straight to the password change. A "verified" flag with no address is not a verification: such accounts
-- verify an email and replace their password at the next login. Any account holding the student role is left alone
-- (students never enter the first-login lifecycle, V143).
UPDATE users
SET recovery_email_verified = false,
    must_change_password = true,
    updated_at = CURRENT_TIMESTAMP
WHERE coalesce(recovery_email_verified, false)
  AND coalesce(btrim(recovery_email), '') = ''
  AND upper(coalesce(status, '')) <> 'DELETED'
  AND NOT EXISTS (SELECT 1 FROM user_roles legacy
                  WHERE legacy.user_id = users.id AND legacy.role_id = 'role_lms_student')
  AND NOT EXISTS (SELECT 1 FROM user_role_assignments assignment
                  WHERE assignment.user_id = users.id AND assignment.role_id = 'role_lms_student');
