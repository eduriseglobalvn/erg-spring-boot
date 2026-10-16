# Flyway upgrade and rollback runbook

## Release gate

1. Take and verify a PostgreSQL backup before deployment (`pg_dump --format=custom --no-owner`).
2. Restore that backup into an isolated database and run the current application migrations there.
3. Run `FlywayUpgradePathIntegrationTest`; it migrates a real PostgreSQL 17 database to the previous release boundary and then upgrades in place to the current version.
4. Verify Flyway history parity, application readiness, error rate, lock waits and event publication lag before increasing canary traffic.

## Rollback

Application rollback must use compatibility facades and backward-compatible schema for at least one deployment window. Never execute destructive down migrations in production. If a migration is defective, disable the new handler, deploy a forward repair migration and keep old/new event readers compatible. Restore from backup only for disaster recovery after stopping writers and recording the recovery point; validate row counts, Flyway history and idempotency keys before reopening traffic.

Record the commit SHA, database engine/version, source Flyway version, target version, backup checksum, restore duration and validation queries with the release evidence.
