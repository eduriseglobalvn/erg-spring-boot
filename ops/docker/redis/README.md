# Redis 8.8 local topology

ERG uses two Redis 8.8.0 services:

- `redis-state`: session, GraphQL rate-limit, and cache coordination; AOF `everysec`, 768 MiB key budget, `noeviction`.
- `redis-cache`: rebuildable business DTO cache; no persistence, 2 GiB key budget, `allkeys-lfu`.

The container limits are intentionally higher than Redis `maxmemory` so allocator, client, AOF, fork, and command buffers do not trigger a Docker OOM kill before Redis reaches its configured key budget.

## Local migration

Dry run on Windows:

```powershell
.\scripts\migrate-redis-8.8-local.ps1
```

Execute the approved destructive local cutover:

```powershell
.\scripts\migrate-redis-8.8-local.ps1 -Execute
```

Linux uses `./scripts/migrate-redis-8.8-local.sh` and `--execute`.

The scripts can remove only `erg-dev-redis-1` and `erg-dev_redis-data`. They never remove or recreate `erg-dev_postgres-data`. Local Redis sessions, counters, and caches are discarded.

## Rollback

Rollback is clean-state only: stop the Redis 8.8 services, select the retained Redis 7.4 Compose definition from version control, recreate an empty Redis volume, and configure the application to use the Lua fixed-window limiter. PostgreSQL is unaffected.

A future production deployment needs a separate state-preserving migration runbook. Do not use this destructive local script after production has user sessions.

