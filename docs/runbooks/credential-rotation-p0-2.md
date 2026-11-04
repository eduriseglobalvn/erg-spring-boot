# Credential rotation after the tracked DB dumps (P0-2)

Source: `docs/second-brain/review/13-verify-database-secrets.md` finding D8, plan item P0-2 in
`docs/second-brain/review/README.md`. This runbook needs an operator with access to the hosted
database, Redis and the Git remote. Nothing here can be done from application code.

## What leaked

`backups/` was committed and pushed (commit `cac9194`, present on `origin/Backup`). The two `.sql`
dumps and the reset manifest expose:

- the super-admin argon2id hash (same as `V7__enterprise_admin_account_seed.sql`);
- 13 users and 41 internal `@erg.edu.vn` e-mail addresses;
- 29 `user_sessions` rows with refresh/access token hashes, IP and user agent;
- 29 `auth_login_attempts` rows with e-mail, IP and user agent;
- 770 `lms_students` rows with names, birth dates, parent names and phone numbers;
- the hosted Postgres JDBC host and admin username (manifest), and the managed Redis host
  (`docs/1006/bao-cao-bao-mat-spring-boot.md` §C-2 records that these were never rotated).

## Already handled in code

- `V110__rotate_super_admin_password.sql` replaces the super-admin hash and revokes that account's
  open sessions. It runs on the next migration of each database.
- The temporary password set by V110 is guessable: change it immediately after the first login.

## Operator steps

Steps 1 and the P0-10 V88 duplicate check run as one command (counts only, safe to re-run):

```bash
psql "$TARGET_URL" -v ON_ERROR_STOP=1 -f ops/postgres/predeploy-p0.sql
```

1. **Revoke every session that existed when the dump was taken** (the dump is dated 2026-07-16 14:37
   UTC+7). Run on each hosted database:

   ```sql
   UPDATE user_sessions
      SET revoked_at = CURRENT_TIMESTAMP,
          revoked_reason = 'DUMP_EXPOSURE_P0_2'
    WHERE revoked_at IS NULL
      AND created_at <= TIMESTAMPTZ '2026-07-16 14:37:13+07';
   ```

   Record the affected row count in the incident note.
2. **Rotate the managed Postgres admin password** and, where the provider allows, rename the
   admin user. Move the application to the restricted runtime role (`docs/runbooks/postgres-runtime-role.md`).
3. **Rotate the managed Redis password.**
4. Update the deployment secrets (`SPRING_DATASOURCE_*`, `SPRING_FLYWAY_*`, Redis password) and
   restart. Confirm `RuntimeDatabaseRoleGuard` lets the `prod` profile start.
5. **Purge `backups/` from Git history.** This rewrites history and needs explicit approval from the
   repository owner, because every clone must be re-cloned afterwards:

   ```bash
   git filter-repo --path backups --invert-paths
   ```

   Then force-push the rewritten branches, including `Backup`, and ask the Git host to purge cached
   views and forks. Until this is done, treat everything listed above as public.
6. If the `lms_students` data is real, assess notification duties under Decree 13/2023/ND-CP with
   the data-protection owner.

## Done when

- `git log --all -- backups` is empty on the remote.
- No session created before the dump timestamp is active.
- The old Postgres and Redis credentials fail to authenticate.
