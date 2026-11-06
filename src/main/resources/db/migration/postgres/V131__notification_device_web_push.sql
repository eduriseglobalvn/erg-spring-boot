-- P2 notifications: browsers can register a standard Web Push (VAPID) subscription next to FCM tokens.
-- For WEB_PUSH rows the push endpoint is stored like an FCM token (registration_token encrypted by
-- NotificationTokenCipher, registration_token_hash for dedupe); p256dh and auth are encrypted the same way.
-- RLS and policies on this table already exist (tenant column unchanged).
ALTER TABLE notification_device_registrations
    ADD COLUMN IF NOT EXISTS transport varchar(16) NOT NULL DEFAULT 'FCM';

ALTER TABLE notification_device_registrations
    ADD COLUMN IF NOT EXISTS p256dh text;

ALTER TABLE notification_device_registrations
    ADD COLUMN IF NOT EXISTS auth text;

ALTER TABLE notification_device_registrations DROP CONSTRAINT IF EXISTS ck_notification_devices_transport;
ALTER TABLE notification_device_registrations ADD CONSTRAINT ck_notification_devices_transport
    CHECK (transport IN ('FCM', 'WEB_PUSH'));
