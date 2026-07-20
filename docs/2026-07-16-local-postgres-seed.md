# Local PostgreSQL environment

The local Docker environment is now the default development target for the ignored root `.env` and for `ops/docker/.env.dev`:

- PostgreSQL: `jdbc:postgresql://localhost:5432/erg`, user `erg`
- Redis: `redis://:erg-local-redis@localhost:6379`
- Mongo auto-configuration: disabled locally; no Mongo container is required
- Flyway: enabled, migrations applied through `V67`

## Current database state

The local database was replaced on 2026-07-16 with a data-only export from the temporary Aiven PostgreSQL database. The local schema was rebuilt from the current source migrations through V67 before importing the Aiven data. This avoids accepting the historical V29 Flyway checksum drift from the Aiven schema.

All 77 Aiven business-table row counts were compared after restore and matched local. Important restored counts include:

| Entity | Count |
|---|---:|
| Users | 13 |
| Education units | 12 |
| Academic classes | 179 |
| LMS students | 770 |
| LMS questions | 35 |
| LMS quizzes | 11 |
| LMS assignments | 2 |
| LMS attempts | 0 |

The previous synthetic fixture was removed during the restore. Backups are stored in the ignored `backups/` directory:

- `aiven-source-20260716-143713.dump`: exact Aiven V64 custom-format archive.
- `aiven-source-20260716-143713.sql`: readable/restorable SQL representation of the exact Aiven V64 archive.
- `local-aiven-mirror-v67-20260716-150316.sql`: recommended SQL backup containing the restored Aiven data on the current V67 schema.
- `local-before-aiven-20260716-143628.dump`: local synthetic database before it was replaced.

Restore the recommended V67 SQL backup into an empty local database with:

```powershell
Get-Content -Raw backups/local-aiven-mirror-v67-20260716-150316.sql |
  docker exec -i erg-dev-postgres-1 psql -v ON_ERROR_STOP=1 -U erg -d erg
```

## Optional synthetic performance fixture

The benchmark fixture remains available for later performance testing. Running it adds synthetic rows to the current Aiven mirror and therefore must not be used when a clean production-data mirror is required:

```powershell
./scripts/seed-local-postgres.ps1
```

The optional fixture targets:

| Entity | Count |
|---|---:|
| Seed users | 5,000 |
| Seed schools/tenants | 10 |
| Seed classes | 250 |
| Seed LMS students | 4,500 |
| Seed assignments | 10,000 |
| Seed assignment recipients | 180,000 |
| Seed attempts | 180,000 |
| Seed user activity history | 1,000,000 |

The SQL source is [seed-postgres-scale.sql](../load/fixtures/seed-postgres-scale.sql), and the runner is [seed-local-postgres.ps1](../scripts/seed-local-postgres.ps1). The fixture uses only local benchmark credentials/data and does not call SMTP, Firebase, MongoDB, Aiven, R2, or AI providers.

This is a data baseline, not a capacity guarantee. The remaining acceptance gate is the authenticated REST/GraphQL load run with p95/p99, Hikari, PostgreSQL, Redis, CPU, memory and error-rate measurements.
