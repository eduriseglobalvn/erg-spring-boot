-- Teacher account lifecycle: first-login password reset + recovery email verification.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS must_change_password boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS recovery_email varchar(255),
    ADD COLUMN IF NOT EXISTS recovery_email_verified boolean NOT NULL DEFAULT false;

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_recovery_email_normalized
    ON users (lower(recovery_email))
    WHERE recovery_email IS NOT NULL;

-- One-time verification codes for recovery-email and password-reset challenges.
-- Replaces the legacy auth_pins table dropped in V48; codes are stored hashed only.
CREATE TABLE IF NOT EXISTS account_verification_codes (
    id varchar(36) PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    user_id varchar(24) NOT NULL,
    tenant_id varchar(255),
    purpose varchar(32) NOT NULL,
    code_hash varchar(255) NOT NULL,
    target_email varchar(255),
    attempts integer NOT NULL DEFAULT 0,
    max_attempts integer NOT NULL DEFAULT 5,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    last_sent_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_account_verification_codes_user_purpose
    ON account_verification_codes (user_id, purpose, expires_at DESC);

-- Only one active (unconsumed) challenge per user + purpose at a time.
CREATE UNIQUE INDEX IF NOT EXISTS uq_account_verification_codes_active
    ON account_verification_codes (user_id, purpose)
    WHERE consumed_at IS NULL;
