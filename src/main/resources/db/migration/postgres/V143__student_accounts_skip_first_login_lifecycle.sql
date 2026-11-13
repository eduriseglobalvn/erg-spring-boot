-- STU-01: a student account (its only active role is the learner role, role_lms_student) is never held in the
-- first-login lifecycle: no onboarding, no forced password change, no recovery-email verification.
-- Roles are read from both the legacy user_roles table and the active user_role_assignments rows.
-- An INVITED student is activated too: login refuses an INVITED account that no longer has to change its password.
UPDATE users
SET is_profile_completed = true,
    recovery_email_verified = true,
    must_change_password = false,
    status = CASE WHEN upper(users.status) = 'INVITED' THEN 'ACTIVE' ELSE users.status END,
    updated_at = CURRENT_TIMESTAMP
WHERE (users.must_change_password
        OR NOT coalesce(users.is_profile_completed, false)
        OR NOT coalesce(users.recovery_email_verified, false))
  AND (EXISTS (SELECT 1 FROM user_roles legacy
               WHERE legacy.user_id = users.id AND legacy.role_id = 'role_lms_student')
       OR EXISTS (SELECT 1 FROM user_role_assignments assignment
                  WHERE assignment.user_id = users.id AND assignment.role_id = 'role_lms_student'
                    AND assignment.revoked_at IS NULL
                    AND (assignment.expires_at IS NULL OR assignment.expires_at > CURRENT_TIMESTAMP)))
  AND NOT EXISTS (SELECT 1 FROM user_roles legacy
                  WHERE legacy.user_id = users.id AND legacy.role_id <> 'role_lms_student')
  AND NOT EXISTS (SELECT 1 FROM user_role_assignments assignment
                  WHERE assignment.user_id = users.id AND assignment.role_id <> 'role_lms_student'
                    AND assignment.revoked_at IS NULL
                    AND (assignment.expires_at IS NULL OR assignment.expires_at > CURRENT_TIMESTAMP));
