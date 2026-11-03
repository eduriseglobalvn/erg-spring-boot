# PostgreSQL migration preflight and destructive-migration safety

Use this runbook before deploying a build to any database that already holds data, and when writing
any migration that deletes, drops or constrains existing rows. Migrations are append-only: never edit
an applied migration, always roll forward (see `flyway-upgrade-rollback.md`).

## 1. Before every deploy to an existing database

1. Back up the target (`pg_dump --format=custom --no-owner`) and record the checksum.
2. Read the applied version: `select max(version::int) from flyway_schema_history where success and version ~ '^[0-9]+$';`
3. If the applied version is **below 88**, run the V88 preflight (section 2) and do not deploy until it
   reports zero duplicate keys.
4. Confirm the runtime role: the application must connect as the restricted runtime role (for example
   `erg_app`), Flyway as the owner via `SPRING_FLYWAY_USER`. In the `prod` profile
   `RuntimeDatabaseRoleGuard` stops startup when the runtime role is SUPERUSER, has BYPASSRLS or owns
   (or inherits ownership of) a `public` table. See `postgres-runtime-role.md`.
   `ERG_PERSISTENCE_RUNTIME_ROLE_GUARD_ENABLED=false` exists only as a documented emergency bypass and
   must be recorded as an incident.
5. `prod` never baselines a schema that has no `flyway_schema_history` (that would replay V1..Vn,
   including cleanup migrations, over real data). Only for an approved one-time onboarding of a legacy
   database, set `ERG_FLYWAY_ALLOW_BASELINE_ON_MIGRATE=true` for that single run and remove it afterwards.

## 2. V88 duplicate preflight (databases below V88)

`V88__lms_operational_write_contract.sql` creates these unique indexes without deduplicating:

| Index | Key |
|---|---|
| `uq_lms_students_tenant_code` | `(tenant_id, lower(student_code))` |
| `uq_lms_subjects_tenant_code` | `(tenant_id, lower(code))` |
| `uq_academic_classes_tenant_school_year_name` | `(tenant_id, school_id, lower(name), coalesce(academic_year, ''))` |

A single duplicate makes V88 fail, Flyway rolls it back and the application does not start. V88 is
immutable, so duplicates are fixed in the data **before** migrating:

```powershell
psql "$env:TARGET_URL" -v ON_ERROR_STOP=1 -f ops/postgres/preflight-v88-duplicates.sql
```

The script is read-only and prints counts only. Every `*_duplicate_keys` value must be `0`.

### Deduplicating (non-destructive)

Do this in a maintenance window, after the backup, as the owner role, in one transaction. Prefer
**renaming** duplicate codes over deleting rows: students and subjects are referenced by enrollments,
attempts, gradebook and attendance rows, so deleting a "duplicate" loses learner history.

```sql
BEGIN;
-- 1. Quarantine the affected rows so the change is auditable and reversible.
CREATE TABLE IF NOT EXISTS _quarantine_v88_lms_students AS
SELECT s.*, now() AS quarantined_at
FROM lms_students s
WHERE (s.tenant_id, lower(s.student_code)) IN (
    SELECT tenant_id, lower(student_code) FROM lms_students
    GROUP BY 1, 2 HAVING count(*) > 1);

-- 2. Keep the oldest row's code; suffix the others so each key becomes unique.
WITH ranked AS (
    SELECT id, row_number() OVER (
               PARTITION BY tenant_id, lower(student_code) ORDER BY created_at, id) AS rn
    FROM lms_students)
UPDATE lms_students s
SET student_code = s.student_code || '-DUP' || ranked.rn,
    updated_at = now()
FROM ranked
WHERE ranked.id = s.id AND ranked.rn > 1;

-- 3. Re-run the preflight queries inside the transaction; COMMIT only when they return 0.
COMMIT;
```

Repeat the same pattern for `lms_subjects` (`code`) and `academic_classes` (`name`). Hand the
quarantine tables to the data owner, who decides whether suffixed records should be merged in the
application. Keep the quarantine tables until the release is accepted, then drop them deliberately.

## 3. Pattern for future destructive migrations

Any new migration that runs `DELETE`, `DROP TABLE`, `DROP COLUMN`, `TRUNCATE` or adds a constraint
that existing rows might violate must:

1. **Quarantine first**: copy the rows it will remove into `_quarantine_v<version>_<table>`
   (`CREATE TABLE ... AS SELECT ...`) in the same migration, before the delete/drop.
2. **Count and report**: `GET DIAGNOSTICS n = ROW_COUNT;` then `RAISE NOTICE 'V<version>: % rows removed from <table>', n;`
   so the count lands in the deployment log.
3. **Guard a threshold**: stop when the blast radius is unexpectedly large, for example
   `IF n > 1000 THEN RAISE EXCEPTION 'V<version>: % rows exceed the approved threshold', n; END IF;`
   The migration then fails and rolls back instead of silently deleting data.
4. **Preflight unique constraints**: ship a read-only `ops/postgres/preflight-v<version>-*.sql` that
   counts violating keys, and add the check to this runbook.
5. Never rely on `IF EXISTS` alone for a `DROP` of a table that may still hold user data; prove it is
   empty or migrated in the same migration.

## 4. Non-transactional index migrations

`V114__online_hot_path_and_user_fk_indexes.sql` contains only `CREATE INDEX CONCURRENTLY IF NOT EXISTS`.
Flyway detects `CONCURRENTLY` on PostgreSQL and runs the file outside a transaction, so writes are not
blocked. Do not add transactional statements to such a file (Flyway rejects mixed migrations).

If a concurrent build is interrupted it can leave an `INVALID` index, which `IF NOT EXISTS` would then
skip. Check and repair before retrying:

```sql
SELECT c.relname FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid WHERE NOT i.indisvalid;
DROP INDEX CONCURRENTLY IF EXISTS <invalid_index_name>;
```

Then run `flyway repair` (only if the history row is marked failed) and migrate again.

## 5. Reset scripts

`scripts/reset-and-seed-postgres.ps1` and `scripts/reset-postgres-db.ps1` drop the whole `public`
schema. They refuse any host other than `localhost`, `127.0.0.1`, `::1`, `*.localhost` or `*.test`
unless `-AllowRemoteHost <exact host>` names the target. Never pass it for a production database.
