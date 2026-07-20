CREATE INDEX IF NOT EXISTS idx_profiles_user_id ON profiles(user_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_profiles_user_id ON profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_social_accounts_user_id ON social_accounts(user_id);
CREATE INDEX IF NOT EXISTS idx_certificates_user_id ON certificates(user_id);
CREATE INDEX IF NOT EXISTS idx_user_activities_user_id ON user_activities(user_id);
CREATE INDEX IF NOT EXISTS idx_user_activities_target_user_id ON user_activities(target_user_id);
CREATE INDEX IF NOT EXISTS idx_user_permissions_user_id ON user_permissions(user_id);
CREATE INDEX IF NOT EXISTS idx_user_permissions_permission ON user_permissions(permission);
CREATE INDEX IF NOT EXISTS idx_user_permissions_created_by ON user_permissions(created_by);
CREATE INDEX IF NOT EXISTS idx_work_shifts_user_id ON work_shifts(user_id);
CREATE INDEX IF NOT EXISTS idx_work_shifts_school_id ON work_shifts(school_id);
CREATE INDEX IF NOT EXISTS idx_work_shifts_confirmed_by ON work_shifts(confirmed_by);
CREATE INDEX IF NOT EXISTS idx_education_units_owner_user_id ON education_units(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_education_units_created_by ON education_units(created_by);

ALTER TABLE permissions
    ADD CONSTRAINT fk_permissions_group_name
    FOREIGN KEY (group_name) REFERENCES permission_groups(name)
    ON UPDATE CASCADE
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE user_roles
    ADD CONSTRAINT fk_user_roles_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE user_roles
    ADD CONSTRAINT fk_user_roles_role
    FOREIGN KEY (role_id) REFERENCES roles(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE role_permissions
    ADD CONSTRAINT fk_role_permissions_role
    FOREIGN KEY (role_id) REFERENCES roles(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE role_permissions
    ADD CONSTRAINT fk_role_permissions_permission
    FOREIGN KEY (permission_id) REFERENCES permissions(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE education_units
    ADD CONSTRAINT fk_education_units_parent
    FOREIGN KEY (parent_id) REFERENCES education_units(id)
    ON UPDATE CASCADE
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE education_units
    ADD CONSTRAINT fk_education_units_owner_user
    FOREIGN KEY (owner_user_id) REFERENCES users(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE education_units
    ADD CONSTRAINT fk_education_units_created_by
    FOREIGN KEY (created_by) REFERENCES users(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE user_sessions
    ADD CONSTRAINT fk_user_sessions_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE auth_login_attempts
    ADD CONSTRAINT fk_auth_login_attempts_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE profiles
    ADD CONSTRAINT fk_profiles_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE social_accounts
    ADD CONSTRAINT fk_social_accounts_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE certificates
    ADD CONSTRAINT fk_certificates_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE user_activities
    ADD CONSTRAINT fk_user_activities_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE user_activities
    ADD CONSTRAINT fk_user_activities_target_user
    FOREIGN KEY (target_user_id) REFERENCES users(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE user_permissions
    ADD CONSTRAINT fk_user_permissions_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE user_permissions
    ADD CONSTRAINT fk_user_permissions_permission
    FOREIGN KEY (permission) REFERENCES permissions(name)
    ON UPDATE CASCADE
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE user_permissions
    ADD CONSTRAINT fk_user_permissions_created_by
    FOREIGN KEY (created_by) REFERENCES users(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE user_access_scopes
    ADD CONSTRAINT fk_user_access_scopes_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE user_access_scopes
    ADD CONSTRAINT fk_user_access_scopes_unit
    FOREIGN KEY (education_unit_id) REFERENCES education_units(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE work_shifts
    ADD CONSTRAINT fk_work_shifts_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE work_shifts
    ADD CONSTRAINT fk_work_shifts_school
    FOREIGN KEY (school_id) REFERENCES education_units(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE work_shifts
    ADD CONSTRAINT fk_work_shifts_confirmed_by
    FOREIGN KEY (confirmed_by) REFERENCES users(id)
    ON DELETE SET NULL
    NOT VALID;
