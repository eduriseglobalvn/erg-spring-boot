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

