-- Persist provider routing and provider-neutral error details without rewriting existing delivery history.
ALTER TABLE notification_outbox
    ADD COLUMN IF NOT EXISTS provider varchar(32) NOT NULL DEFAULT 'firebase';

ALTER TABLE notification_delivery_attempts
    ADD COLUMN IF NOT EXISTS provider_error_code varchar(120);

ALTER TABLE notification_delivery_attempts
    ADD COLUMN IF NOT EXISTS provider_error_message varchar(500);

UPDATE notification_outbox
SET provider = 'firebase'
WHERE provider IS NULL OR btrim(provider) = '';

CREATE INDEX IF NOT EXISTS idx_notification_outbox_provider_pickup
    ON notification_outbox (tenant_id, provider, status, next_attempt_at);
