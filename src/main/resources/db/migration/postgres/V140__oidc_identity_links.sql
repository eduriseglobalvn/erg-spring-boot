-- OIDC BFF identity links: an IdP account (issuer, subject) is pinned to one internal user so a
-- later change of preferred_username/email at the IdP cannot re-point it to another account.
CREATE TABLE IF NOT EXISTS user_identity_links (
    id varchar(64) PRIMARY KEY,
    tenant_id varchar(96) NOT NULL,
    user_id varchar(64) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    issuer varchar(512) NOT NULL,
    subject varchar(255) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_user_identity_links_subject
    ON user_identity_links (tenant_id, issuer, subject);
CREATE UNIQUE INDEX IF NOT EXISTS ux_user_identity_links_user
    ON user_identity_links (tenant_id, issuer, user_id);
CREATE INDEX IF NOT EXISTS idx_user_identity_links_user_id
    ON user_identity_links (user_id);

ALTER TABLE user_identity_links ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_identity_links FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS user_identity_links_tenant_isolation ON user_identity_links;
CREATE POLICY user_identity_links_tenant_isolation ON user_identity_links
    USING (
        current_setting('app.maintenance_mode', true) = 'true'
        OR (nullif(current_setting('app.tenant_id', true), '') IS NOT NULL
            AND tenant_id = current_setting('app.tenant_id', true))
    )
    WITH CHECK (
        current_setting('app.maintenance_mode', true) = 'true'
        OR (nullif(current_setting('app.tenant_id', true), '') IS NOT NULL
            AND tenant_id = current_setting('app.tenant_id', true))
    );
