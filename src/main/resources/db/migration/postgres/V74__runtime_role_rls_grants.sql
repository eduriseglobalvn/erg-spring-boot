-- The HTTP/runtime connection must not own tables or hold superuser/BYPASSRLS privileges.
-- Docker initializes this fixed role before Flyway; external deployments must provision it too.
do $$
begin
    if exists (select 1 from pg_roles where rolname = 'erg_app') then
        grant usage on schema public to erg_app;
        grant select, insert, update, delete on all tables in schema public to erg_app;
        grant usage, select, update on all sequences in schema public to erg_app;

        alter default privileges in schema public
            grant select, insert, update, delete on tables to erg_app;
        alter default privileges in schema public
            grant usage, select, update on sequences to erg_app;
    end if;
end $$;
