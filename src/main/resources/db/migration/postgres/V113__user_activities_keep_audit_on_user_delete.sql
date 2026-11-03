-- N5: deleting a user must not erase the audit trail. user_activities.user_id was NOT NULL with
-- ON DELETE CASCADE (V1/V12); relax it to nullable + ON DELETE SET NULL so an activity row outlives
-- its actor. The application now soft-deletes users, so this only guards manual/legacy deletes.
ALTER TABLE user_activities ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE user_activities DROP CONSTRAINT IF EXISTS fk_user_activities_user;
ALTER TABLE user_activities
    ADD CONSTRAINT fk_user_activities_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE SET NULL
    NOT VALID;
ALTER TABLE user_activities VALIDATE CONSTRAINT fk_user_activities_user;
