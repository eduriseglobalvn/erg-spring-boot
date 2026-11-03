# PostgreSQL runtime role and RLS

The application connection must not use the Flyway owner or a PostgreSQL superuser. RLS is
meaningless when the application can bypass row security.

The Docker deployment provisions two credentials:

- `POSTGRES_USER` / `POSTGRES_PASSWORD`: migration owner, used only through `SPRING_FLYWAY_USER`
  and `SPRING_FLYWAY_PASSWORD`.
- `POSTGRES_RUNTIME_USER` / `POSTGRES_RUNTIME_PASSWORD`: non-owner `NOSUPERUSER` role used by
  `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`.

`ops/docker/postgres/init/02-runtime-role.sh` creates the runtime role on a new PostgreSQL volume.
Migration `V74__runtime_role_rls_grants.sql` grants table/sequence access and default privileges.
Migration `V75__tenant_rls_matrix.sql` enables a strict tenant/maintenance policy on every remaining
table carrying `tenant_id`. Legacy tables are not `FORCE`d during expand/backfill because the Flyway
owner must retain migration access; the non-owner runtime role is still constrained by PostgreSQL RLS.
`TenantRlsMatrixIntegrationTest` fails when a new tenant table is added without RLS.
For an existing database, provision the role and grants before switching the backend datasource;
do not delete the production volume just to rerun the init script.

The PostgreSQL integration test uses a non-superuser role and proves that quiz-domain rows cannot
be read or inserted across tenants. This is a DB-level guard in addition to REST/GraphQL boundary
checks. Legacy tables are not considered RLS-complete until their own policy and integration proof
are added.

## Startup guard (prod)

In the `prod` profile `RuntimeDatabaseRoleGuard` (`config/persistence`) queries `pg_roles`/`pg_class`
for the datasource user and refuses to start when it is SUPERUSER, has BYPASSRLS, or owns (directly or
through role membership) any `public` table. Fix the credentials rather than disabling the guard; the
`ERG_PERSISTENCE_RUNTIME_ROLE_GUARD_ENABLED=false` bypass is an incident-only escape hatch. Newer tenant
tables (V92-V96, V111) also use `FORCE ROW LEVEL SECURITY`. See `postgres-migration-preflight.md`.
