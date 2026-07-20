ALTER TABLE user_activities
    ADD COLUMN IF NOT EXISTS request_id varchar(100),
    ADD COLUMN IF NOT EXISTS metadata jsonb NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX IF NOT EXISTS idx_user_activities_request_id
    ON user_activities (request_id)
    WHERE request_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_user_activities_target_action_time
    ON user_activities (tenant_id, target_user_id, action, created_at DESC);
