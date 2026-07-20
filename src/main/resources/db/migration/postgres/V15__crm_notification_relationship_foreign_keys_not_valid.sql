ALTER TABLE crm_school_profiles ALTER COLUMN owner_user_id TYPE varchar(36);
ALTER TABLE crm_school_profiles ALTER COLUMN lead_owner_user_id TYPE varchar(36);
ALTER TABLE crm_school_profiles ALTER COLUMN manager_user_id TYPE varchar(36);
ALTER TABLE crm_school_profiles ALTER COLUMN introduced_by_user_id TYPE varchar(36);
ALTER TABLE crm_school_deals ALTER COLUMN owner_user_id TYPE varchar(36);
ALTER TABLE crm_school_activities ALTER COLUMN actor_user_id TYPE varchar(36);
ALTER TABLE crm_referrals ALTER COLUMN referrer_user_id TYPE varchar(36);

CREATE INDEX IF NOT EXISTS idx_crm_school_profiles_lead_owner ON crm_school_profiles(lead_owner_user_id);
CREATE INDEX IF NOT EXISTS idx_crm_school_profiles_manager ON crm_school_profiles(manager_user_id);
CREATE INDEX IF NOT EXISTS idx_notification_messages_created_by ON notification_messages(created_by);
CREATE INDEX IF NOT EXISTS idx_notification_delivery_device ON notification_delivery_attempts(device_registration_id);
CREATE INDEX IF NOT EXISTS idx_notification_preferences_user ON notification_preferences(tenant_id, user_id, portal);

ALTER TABLE crm_school_profiles
    ADD CONSTRAINT fk_crm_school_profiles_school
    FOREIGN KEY (school_id) REFERENCES education_units(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE crm_school_profiles
    ADD CONSTRAINT fk_crm_school_profiles_owner
    FOREIGN KEY (owner_user_id) REFERENCES users(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE crm_school_profiles
    ADD CONSTRAINT fk_crm_school_profiles_lead_owner
    FOREIGN KEY (lead_owner_user_id) REFERENCES users(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE crm_school_profiles
    ADD CONSTRAINT fk_crm_school_profiles_manager
    FOREIGN KEY (manager_user_id) REFERENCES users(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE crm_school_profiles
    ADD CONSTRAINT fk_crm_school_profiles_introduced_by
    FOREIGN KEY (introduced_by_user_id) REFERENCES users(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE crm_school_contacts
    ADD CONSTRAINT fk_crm_school_contacts_school
    FOREIGN KEY (school_id) REFERENCES education_units(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE crm_school_deals
    ADD CONSTRAINT fk_crm_school_deals_school
    FOREIGN KEY (school_id) REFERENCES education_units(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE crm_school_deals
    ADD CONSTRAINT fk_crm_school_deals_owner
    FOREIGN KEY (owner_user_id) REFERENCES users(id)
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE crm_school_activities
    ADD CONSTRAINT fk_crm_school_activities_school
    FOREIGN KEY (school_id) REFERENCES education_units(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE crm_school_activities
    ADD CONSTRAINT fk_crm_school_activities_deal
    FOREIGN KEY (deal_id) REFERENCES crm_school_deals(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE crm_school_activities
    ADD CONSTRAINT fk_crm_school_activities_actor
    FOREIGN KEY (actor_user_id) REFERENCES users(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE crm_referrals
    ADD CONSTRAINT fk_crm_referrals_school
    FOREIGN KEY (school_id) REFERENCES education_units(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE crm_referrals
    ADD CONSTRAINT fk_crm_referrals_referrer
    FOREIGN KEY (referrer_user_id) REFERENCES users(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE notification_messages
    ADD CONSTRAINT fk_notification_messages_created_by
    FOREIGN KEY (created_by) REFERENCES users(id)
    ON DELETE SET NULL
    NOT VALID;

ALTER TABLE notification_recipients
    ADD CONSTRAINT fk_notification_recipients_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE notification_device_registrations
    ADD CONSTRAINT fk_notification_devices_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE notification_preferences
    ADD CONSTRAINT fk_notification_preferences_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE notification_delivery_attempts
    ADD CONSTRAINT fk_notification_delivery_notification
    FOREIGN KEY (notification_id) REFERENCES notification_messages(id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE notification_delivery_attempts
    ADD CONSTRAINT fk_notification_delivery_device
    FOREIGN KEY (device_registration_id) REFERENCES notification_device_registrations(id)
    ON DELETE SET NULL
    NOT VALID;
