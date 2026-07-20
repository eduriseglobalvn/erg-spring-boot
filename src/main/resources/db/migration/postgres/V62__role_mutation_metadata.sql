ALTER TABLE roles
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(36);

CREATE INDEX IF NOT EXISTS idx_roles_updated_by
    ON roles(updated_by);
