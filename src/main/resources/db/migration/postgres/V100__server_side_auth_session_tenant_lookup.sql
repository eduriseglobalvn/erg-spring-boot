-- Resolve the tenant for an opaque bearer session before tenant RLS is active.
-- The function accepts only the already-hashed token and returns a tenant when
-- exactly one active session matches. It never accepts a client tenant/header.
CREATE OR REPLACE FUNCTION public.erg_resolve_session_tenant(p_access_token_hash varchar)
RETURNS varchar
LANGUAGE sql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
    SELECT CASE
        WHEN count(DISTINCT session.tenant_id) = 1 THEN max(session.tenant_id)
        ELSE NULL
    END
    FROM public.user_sessions session
    WHERE session.access_token_hash = p_access_token_hash
      AND session.tenant_id IS NOT NULL
      AND session.tenant_id <> ''
      AND session.revoked_at IS NULL
      AND session.expires_at > CURRENT_TIMESTAMP
      AND (session.access_token_expires_at IS NULL OR session.access_token_expires_at > CURRENT_TIMESTAMP)
$$;

REVOKE ALL ON FUNCTION public.erg_resolve_session_tenant(varchar) FROM PUBLIC;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'erg_app') THEN
        GRANT EXECUTE ON FUNCTION public.erg_resolve_session_tenant(varchar) TO erg_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'erg') THEN
        GRANT EXECUTE ON FUNCTION public.erg_resolve_session_tenant(varchar) TO erg;
    END IF;
END $$;
