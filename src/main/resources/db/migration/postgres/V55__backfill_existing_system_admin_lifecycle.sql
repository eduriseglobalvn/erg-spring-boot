-- V51 introduced teacher first-login lifecycle fields with conservative defaults.
-- Existing completed system administrators did not have a recovery-email workflow,
-- so keep them active while preserving onboarding for newly invited admin accounts.
UPDATE users
SET recovery_email_verified = true,
    updated_at = CURRENT_TIMESTAMP
WHERE users.is_profile_completed = true
  AND users.must_change_password = false
  AND users.recovery_email_verified = false
  AND EXISTS (
      SELECT 1
      FROM user_roles user_role
      JOIN roles role ON role.id = user_role.role_id
      WHERE user_role.user_id = users.id
        AND role.name = 'system.super_admin'
  );
