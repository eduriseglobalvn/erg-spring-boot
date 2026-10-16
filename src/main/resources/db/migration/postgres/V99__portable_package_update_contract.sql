-- Portable package/update-feed metadata. Existing package rows remain readable;
-- a package is advertised as portable-ready only after integrity metadata exists.
ALTER TABLE quiz_packages
    ADD COLUMN IF NOT EXISTS signature_algorithm varchar(32),
    ADD COLUMN IF NOT EXISTS min_runtime_version varchar(64),
    ADD COLUMN IF NOT EXISTS download_url varchar(2048),
    ADD COLUMN IF NOT EXISTS size_bytes bigint,
    ADD COLUMN IF NOT EXISTS revoked_at timestamp with time zone,
    ADD COLUMN IF NOT EXISTS revocation_reason varchar(256),
    ADD COLUMN IF NOT EXISTS release_channel varchar(32),
    ADD COLUMN IF NOT EXISTS lock_version integer NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_quiz_packages_tenant_channel
    ON quiz_packages(tenant_id, release_channel, quiz_id, revoked_at);

CREATE INDEX IF NOT EXISTS idx_quiz_packages_expiry
    ON quiz_packages(tenant_id, expires_at)
    WHERE expires_at IS NOT NULL AND revoked_at IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_quiz_packages_size_bytes_non_negative'
    ) THEN
        ALTER TABLE quiz_packages
            ADD CONSTRAINT ck_quiz_packages_size_bytes_non_negative
            CHECK (size_bytes IS NULL OR size_bytes >= 0);
    END IF;
END $$;
