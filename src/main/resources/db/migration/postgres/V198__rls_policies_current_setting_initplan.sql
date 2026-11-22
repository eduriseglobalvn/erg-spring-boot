-- Wrap every current_setting(...) call in the RLS policies in a scalar subquery so the planner evaluates it
-- once per statement (InitPlan) instead of per row, and `tenant_id = (select ...)` becomes an index condition.
-- The logic is unchanged: (select current_setting('x', true)) returns the same NULL / '' / value as the bare call.
-- ALTER POLICY keeps each policy's command and roles; only USING / WITH CHECK are replaced. Idempotent: calls
-- already preceded by "SELECT " (how PostgreSQL deparses a wrapped call) are not matched again.
-- Runs in Flyway's transaction (all-or-nothing); the migration session sets lock_timeout = 10s (FlywayConfig).
DO $$
DECLARE
    bare constant text := 'current_setting\((''[^'']+''::text), (true|false)\)';
    wrapped constant text := '(SELECT current_setting(\1, \2))';
    guarded constant text := '(?<!SELECT )' || bare;
    any_bare constant text := '(?<!SELECT )current_setting\(';
    pol record;
    new_qual text;
    new_check text;
    stmt text;
    remaining text;
BEGIN
    FOR pol IN
        SELECT schemaname, tablename, policyname, qual, with_check
        FROM pg_policies
        WHERE schemaname = current_schema()
          AND (qual ~ guarded OR with_check ~ guarded)
        ORDER BY tablename, policyname
    LOOP
        new_qual := regexp_replace(pol.qual, guarded, wrapped, 'g');
        new_check := regexp_replace(pol.with_check, guarded, wrapped, 'g');
        stmt := format('ALTER POLICY %I ON %I.%I', pol.policyname, pol.schemaname, pol.tablename);
        IF new_qual IS NOT NULL THEN
            stmt := stmt || ' USING (' || new_qual || ')';
        END IF;
        IF new_check IS NOT NULL THEN
            stmt := stmt || ' WITH CHECK (' || new_check || ')';
        END IF;
        EXECUTE stmt;
    END LOOP;

    SELECT string_agg(tablename || '.' || policyname, ', ') INTO remaining
    FROM pg_policies
    WHERE schemaname = current_schema()
      AND (qual ~ any_bare OR with_check ~ any_bare);
    IF remaining IS NOT NULL THEN
        RAISE EXCEPTION 'RLS policies still call current_setting() outside a scalar subquery: %', remaining;
    END IF;
END $$;
