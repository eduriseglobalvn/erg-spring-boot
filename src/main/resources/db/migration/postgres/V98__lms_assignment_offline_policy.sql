-- Explicit learner offline opt-in. Existing assignments remain denied by default.
ALTER TABLE lms_assignments
    ADD COLUMN IF NOT EXISTS offline_allowed boolean NOT NULL DEFAULT false;
