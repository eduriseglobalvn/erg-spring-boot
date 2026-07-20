CREATE TABLE IF NOT EXISTS notification_messages (
    id varchar(36) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(64) NOT NULL,
    event_type varchar(80) NOT NULL,
    source_type varchar(80),
    source_id varchar(80),
    title varchar(180) NOT NULL,
    body_preview varchar(500) NOT NULL,
    action_url varchar(500),
    priority varchar(24) NOT NULL DEFAULT 'normal',
    channel varchar(24) NOT NULL DEFAULT 'push',
    visibility varchar(24) NOT NULL DEFAULT 'private',
    collapse_key varchar(120),
    dedupe_key varchar(180),
    payload_data jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_by varchar(36),
    scheduled_at timestamp with time zone,
    expires_at timestamp with time zone
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_messages_tenant_dedupe
    ON notification_messages(tenant_id, dedupe_key);

CREATE INDEX IF NOT EXISTS idx_notification_messages_tenant_created
    ON notification_messages(tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notification_messages_source
    ON notification_messages(tenant_id, source_type, source_id);

CREATE TABLE IF NOT EXISTS notification_recipients (
    id varchar(36) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(64) NOT NULL,
    notification_id varchar(36) NOT NULL REFERENCES notification_messages(id) ON DELETE CASCADE,
    user_id varchar(36) NOT NULL,
    portal varchar(32) NOT NULL,
    role_key varchar(64),
    status varchar(24) NOT NULL DEFAULT 'unread',
    read_at timestamp with time zone,
    archived_at timestamp with time zone,
    delivery_policy varchar(24) NOT NULL DEFAULT 'push_and_inbox'
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_recipients_message_user
    ON notification_recipients(notification_id, user_id, portal);

CREATE INDEX IF NOT EXISTS idx_notification_recipients_inbox
    ON notification_recipients(tenant_id, user_id, portal, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notification_recipients_unread
    ON notification_recipients(tenant_id, user_id, portal, status);

CREATE TABLE IF NOT EXISTS notification_device_registrations (
    id varchar(36) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(64) NOT NULL,
    user_id varchar(36) NOT NULL,
    portal varchar(32) NOT NULL,
    platform varchar(40) NOT NULL,
    firebase_installation_id varchar(180),
    registration_token text,
    registration_token_hash varchar(128),
    endpoint_hash varchar(128),
    service_worker_scope varchar(255),
    browser_name varchar(80),
    browser_version varchar(80),
    os_name varchar(80),
    user_agent_hash varchar(128),
    locale varchar(16),
    timezone varchar(64),
    permission_state varchar(24) NOT NULL DEFAULT 'default',
    enabled boolean NOT NULL DEFAULT true,
    last_seen_at timestamp with time zone,
    token_updated_at timestamp with time zone,
    revoked_at timestamp with time zone
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_devices_fid
    ON notification_device_registrations(tenant_id, firebase_installation_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_devices_token_hash
    ON notification_device_registrations(tenant_id, registration_token_hash);

CREATE INDEX IF NOT EXISTS idx_notification_devices_user_active
    ON notification_device_registrations(tenant_id, user_id, portal, enabled, last_seen_at DESC);

CREATE INDEX IF NOT EXISTS idx_notification_devices_stale
    ON notification_device_registrations(enabled, last_seen_at, token_updated_at);

CREATE TABLE IF NOT EXISTS notification_outbox (
    id varchar(36) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(64) NOT NULL,
    notification_id varchar(36) NOT NULL REFERENCES notification_messages(id) ON DELETE CASCADE,
    target_kind varchar(32) NOT NULL,
    target_ref varchar(255) NOT NULL,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    status varchar(24) NOT NULL DEFAULT 'pending',
    attempt_count integer NOT NULL DEFAULT 0,
    max_attempts integer NOT NULL DEFAULT 8,
    next_attempt_at timestamp with time zone NOT NULL,
    locked_by varchar(80),
    locked_at timestamp with time zone,
    last_error_code varchar(120),
    last_error_message varchar(500)
);

CREATE INDEX IF NOT EXISTS idx_notification_outbox_pickup
    ON notification_outbox(status, next_attempt_at, created_at);

CREATE INDEX IF NOT EXISTS idx_notification_outbox_notification
    ON notification_outbox(notification_id);

CREATE TABLE IF NOT EXISTS notification_delivery_attempts (
    id varchar(36) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(64) NOT NULL,
    outbox_id varchar(36) REFERENCES notification_outbox(id) ON DELETE SET NULL,
    notification_id varchar(36) NOT NULL,
    device_registration_id varchar(36),
    target_kind varchar(32) NOT NULL,
    target_ref_hash varchar(128),
    provider varchar(32) NOT NULL DEFAULT 'firebase',
    provider_message_id varchar(255),
    http_status integer,
    firebase_error_code varchar(120),
    status varchar(32) NOT NULL,
    duration_ms bigint,
    response_sample varchar(500)
);

CREATE INDEX IF NOT EXISTS idx_notification_delivery_notification
    ON notification_delivery_attempts(notification_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notification_delivery_status
    ON notification_delivery_attempts(tenant_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS notification_topic_memberships (
    id varchar(36) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(64) NOT NULL,
    device_registration_id varchar(36) NOT NULL REFERENCES notification_device_registrations(id) ON DELETE CASCADE,
    topic_name varchar(255) NOT NULL,
    membership_status varchar(24) NOT NULL DEFAULT 'active',
    subscribed_at timestamp with time zone,
    unsubscribed_at timestamp with time zone,
    last_sync_at timestamp with time zone
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_topic_membership
    ON notification_topic_memberships(device_registration_id, topic_name);

CREATE INDEX IF NOT EXISTS idx_notification_topic_name
    ON notification_topic_memberships(tenant_id, topic_name, membership_status);

CREATE TABLE IF NOT EXISTS notification_preferences (
    id varchar(36) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tenant_id varchar(64) NOT NULL,
    user_id varchar(36) NOT NULL,
    portal varchar(32) NOT NULL,
    event_type varchar(80) NOT NULL,
    push_enabled boolean NOT NULL DEFAULT true,
    inbox_enabled boolean NOT NULL DEFAULT true,
    email_enabled boolean NOT NULL DEFAULT false,
    quiet_hours jsonb NOT NULL DEFAULT '{}'::jsonb
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_preferences_user_event
    ON notification_preferences(tenant_id, user_id, portal, event_type);
