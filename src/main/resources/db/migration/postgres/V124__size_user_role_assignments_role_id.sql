-- V108 shipped role_id as varchar(64); size it like roles.id. Forward fix, V108 stays as applied.
ALTER TABLE user_role_assignments
    ALTER COLUMN role_id TYPE varchar(36);
