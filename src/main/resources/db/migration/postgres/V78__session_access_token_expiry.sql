-- Access tokens previously outlived their advertised 15-minute TTL: authentication only checked the
-- session expiry (8-12h), so a leaked token stayed valid for the whole session. Track a dedicated
-- access-token expiry so ErgPrincipalService can reject stale tokens and force a refresh.
-- Nullable: sessions issued before this migration keep NULL and fall back to session expiry until
-- their next refresh, so live users are not logged out on deploy.
ALTER TABLE user_sessions ADD COLUMN IF NOT EXISTS access_token_expires_at timestamp with time zone;
