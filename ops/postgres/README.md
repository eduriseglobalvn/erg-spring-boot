# PostgreSQL performance evidence

These scripts are diagnostic only. Run them against a staging clone with the approved
workload before adding or removing indexes. Never run `EXPLAIN ANALYZE` on a write query
in production without an explicit maintenance window.

Required extensions/settings:

```sql
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
```

Capture the database version, `max_connections`, shared buffers, work memory, and the
application image fingerprint alongside every result. An index is eligible for a migration
only when the captured plan shows a high-frequency sequential scan, bad join, or expensive
sort and the before/after workload shows no write or lock regression.

