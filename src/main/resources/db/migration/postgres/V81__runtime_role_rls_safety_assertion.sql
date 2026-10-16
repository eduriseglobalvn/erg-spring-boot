-- Fail-closed assertion for the RLS trust model instead of blanket FORCE ROW LEVEL SECURITY.
--
-- The runtime role (erg_app) is a NON-owner, so ENABLE RLS already constrains every request path
-- (see V75). FORCE would only additionally constrain the OWNER — which is Flyway/maintenance — and
-- that silently breaks expand/backfill migrations that legitimately run without a tenant GUC. The
-- real risk the audit flagged is "what if the runtime role is misconfigured as owner / BYPASSRLS?".
-- This migration asserts exactly that, so a bad grant fails the deploy instead of silently
-- bypassing tenant isolation at runtime.
--
-- Conditional on erg_app existing (matches the V74 guard; some external envs provision it later).
DO $$
DECLARE
    owned_count int;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'erg_app') THEN
        RETURN;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'erg_app' AND rolsuper) THEN
        RAISE EXCEPTION 'RLS trust violation: runtime role erg_app must not be SUPERUSER';
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'erg_app' AND rolbypassrls) THEN
        RAISE EXCEPTION 'RLS trust violation: runtime role erg_app must not have BYPASSRLS';
    END IF;

    SELECT count(*) INTO owned_count
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
      AND c.relkind = 'r'
      AND pg_get_userbyid(c.relowner) = 'erg_app';

    IF owned_count > 0 THEN
        RAISE EXCEPTION 'RLS trust violation: runtime role erg_app must not own tables (owns %); '
            'a table owner bypasses non-FORCE RLS policies', owned_count;
    END IF;
END $$;
