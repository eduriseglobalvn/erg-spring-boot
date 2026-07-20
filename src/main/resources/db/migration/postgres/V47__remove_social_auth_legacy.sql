DROP TABLE IF EXISTS social_accounts;

ALTER TABLE users
    DROP COLUMN IF EXISTS provider_id,
    DROP COLUMN IF EXISTS google_sub,
    DROP COLUMN IF EXISTS google_email,
    DROP COLUMN IF EXISTS google_email_verified;
