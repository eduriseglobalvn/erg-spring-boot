ALTER TABLE user_role_assignments
    ALTER COLUMN user_id TYPE varchar(36),
    ALTER COLUMN role_id TYPE varchar(36),
    ALTER COLUMN assigned_by TYPE varchar(36),
    ALTER COLUMN revoked_by TYPE varchar(36);
