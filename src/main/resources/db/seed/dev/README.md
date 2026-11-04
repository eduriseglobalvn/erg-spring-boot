# Dev-only Flyway seed location

`FlywayConfig` adds `classpath:db/seed/dev` only when the `dev` profile is active and `prod` is not.
Put demo/sample data here, never in `db/migration/postgres`.

- Use repeatable scripts only (`R__dev_<topic>.sql`), written idempotently (`WHERE NOT EXISTS`/`ON CONFLICT DO NOTHING`).
  A versioned `V*` file here would take a version number the shared migrations need later.
- Demo rows inserted by already-applied migrations (V11, V21, V68, ...) cannot be moved: those files are
  checksummed history. V112 deactivates the unused demo accounts they created.
