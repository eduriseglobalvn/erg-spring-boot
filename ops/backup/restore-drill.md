# PostgreSQL backup/restore drill

This is an operational runbook, not a substitute for a completed drill. Run quarterly and
after schema or hosting changes.

1. Create an encrypted off-host base backup and retain WAL archives.
2. Record the source image, PostgreSQL version, Flyway schema version and backup checksum.
3. Restore into an empty PostgreSQL 17 host with no application writers.
4. Apply WAL through the chosen recovery target and verify the checksum.
5. Start the application against the restored database with Redis empty.
6. Run Flyway validation, authenticated tenant-isolation smoke tests, notification/outbox
   idempotency checks and one representative report query.
7. Record RPO, restore elapsed time, first-ready time, and any missing external effects.

The production acceptance gate is only met when the measured RTO/RPO is attached to the
release evidence; this document alone is not evidence of a successful restore.


## Restore the encrypted off-host copy (monthly)

`backup.sh` uploads `pg_full_<date>.sql.gz.age` (or plain `pg_full_<date>.sql.gz` when `BACKUP_AGE_RECIPIENT`
is unset; skip the `age -d` step for those) to `$BACKUP_RCLONE_REMOTE` under `daily/` (newest 7),
`weekly/` (Sunday runs, newest 4) and `monthly/` (runs on the 1st, newest 6). Run this on a workstation
that holds the age secret key and a read-only rclone remote for the backup bucket, never on the server.

```bash
REMOTE=erg-backup:erg-db-backups            # same remote:bucket as BACKUP_RCLONE_REMOTE
KEY=~/secure/erg-backup-age.key             # age secret key, kept offline
rclone lsf "$REMOTE/daily" | sort | tail -n 3
F=$(rclone lsf "$REMOTE/daily" | sort | tail -n 1)
rclone copyto "$REMOTE/daily/$F" "./$F"
time {
  docker run -d --name erg-restore-drill -e POSTGRES_PASSWORD=drill-only postgres:17-alpine
  until docker exec erg-restore-drill psql -U postgres -Atc 'select 1' >/dev/null 2>&1; do sleep 1; done
  age -d -i "$KEY" "./$F" | gunzip | docker exec -i erg-restore-drill psql -q -U postgres -d postgres > restore.log 2>&1
}
grep -c ERROR restore.log                   # expect 0 (pg_dumpall recreates roles and the erg database)
for t in users lms_attempts lms_assignments; do
  echo "$t: $(docker exec erg-restore-drill psql -U postgres -d erg -Atc "select count(*) from $t")"
done
docker rm -f erg-restore-drill && rm -f "./$F" restore.log
```

Compare the counts with production (`/status` on the ops bot or a read-only query) allowing for the
hours since the dump, and record the file name, its date, the `time` result and the counts below.

Results:
- 2026-11-13, local drill (Docker, rclone `local` remote, throwaway age key, 125 MB synthetic database:
  20 000 users, 400 000 attempts, 3 000 assignments): 11 MB encrypted file, decrypt + restore 7-8 s
  including container start, 0 psql errors, all three counts matched.
