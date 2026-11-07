CREATE TABLE IF NOT EXISTS notification_campaigns (
    id varchar(36) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(64) NOT NULL,
    created_by varchar(36) NOT NULL,
    sender_role varchar(16) NOT NULL,
    title varchar(120) NOT NULL,
    body varchar(2000) NOT NULL,
    priority varchar(16) NOT NULL,
    audience jsonb NOT NULL,
    resolved_count integer,
    estimated_count integer,
    require_read_ack boolean NOT NULL DEFAULT false,
    push_enabled boolean NOT NULL DEFAULT true,
    status varchar(16) NOT NULL,
    scheduled_at timestamp with time zone,
    sent_at timestamp with time zone,
    CONSTRAINT ck_notification_campaigns_sender_role CHECK (sender_role IN ('ADMIN', 'TEACHER')),
    CONSTRAINT ck_notification_campaigns_priority CHECK (priority IN ('INFO', 'IMPORTANT', 'URGENT')),
    CONSTRAINT ck_notification_campaigns_status CHECK (status IN ('DRAFT', 'SCHEDULED', 'SENDING', 'SENT', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_notification_campaigns_tenant_status_scheduled
    ON notification_campaigns(tenant_id, status, scheduled_at);

CREATE INDEX IF NOT EXISTS idx_notification_campaigns_tenant_creator_created
    ON notification_campaigns(tenant_id, created_by, created_at DESC);

-- notification_messages.priority already exists (V10); campaign messages reuse it.
ALTER TABLE notification_messages
    ADD COLUMN IF NOT EXISTS campaign_id varchar(36) REFERENCES notification_campaigns(id),
    ADD COLUMN IF NOT EXISTS acked_at timestamp with time zone;

CREATE INDEX IF NOT EXISTS idx_notification_messages_campaign
    ON notification_messages(campaign_id);
