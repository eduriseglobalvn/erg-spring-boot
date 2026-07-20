# Redis 8.8 implementation report

## Scope and version

- Redis state/cache image: `redis:8.8.0-alpine3.23`.
- Observed local image digest: `sha256:9d317178eceac8454a2284a9e6df2466b93c745529947f0cd42a0fa9609d7005`.
- PostgreSQL remains authoritative; Redis stores session/rate/coordination state and rebuildable typed cache DTOs only.
- API request/response payloads and business error contracts were not changed. No FE migration guide is required.

## Implemented

- Split persistent `redis-state` (`noeviction`, AOF every second) from non-persistent `redis-cache` (`allkeys-lfu`).
- Independent bounded Lettuce clients and 5,000-workflow starting pool budgets.
- Redis 8.8 `INCREX` fixed-window GraphQL limiter with capability detection and Redis 7.4 Lua fallback.
- Tenant-safe SHA-256 keys, typed Jackson DTO serialization, 1 MiB bypass, weighted Caffeine L1, Redis L2, circuit fallback and single-flight.
- Post-commit generation invalidation and cross-replica Pub/Sub L1 invalidation.
- Thirteen approved business cache families including calendar, with user/permission scope where response content depends on it.
- Split state/cache health, bounded-cardinality cache/Redis metrics, 15-second INFO sampling, Native reflection hints, ACL verification and operating thresholds.

## Verification evidence

Focused JVM results and final suite results are recorded from Surefire reports, not inferred from quiet Maven output. Raw cold/warm/outage/5,000 k6 JSON belongs under `load/results/` and must be captured on the target topology before a capacity pass is claimed.

## Capacity disposition

The implementation removes known cache-coherency and limiter bottlenecks but does not by itself prove 5,000 concurrent users. Acceptance requires p95 <= 500 ms, p99 <= 1 s, error/429/503 < 0.1%, >= 500 completed RPS for 30 minutes, no sustained Hikari waiters, no PostgreSQL pool collapse, no swap/OOM, and Redis memory below the approved gates. The 16 GiB single-host profile is a saturation-discovery baseline.

## Native/cache continuation evidence — 2026-07-17

Redis 8.8 cache behavior was exercised from the rebuilt native image (`sha256:a02df2c43ced337d3928100f8e247d758926572fa969f8fe0fa2b9a30fc71715`). A diagnostic k6 run with 2 VUs for 10 seconds completed all checks with zero HTTP errors; mixed workflow p95 was 1.04 seconds and therefore did not meet the interactive 500 ms gate. The result is useful for locating remaining slow workflows but is not a capacity result. The approved 5,000-VU warm/cold/failure matrix and soak remain pending.

## Rollback

Set `ERG_REDIS_CACHE_ENABLED=false` to bypass L2 while preserving PostgreSQL reads. The application can fall back from native to JVM from the same revision. Redis 8.8 can be rolled back to 7.4 because the rate limiter detects `INCREX` and uses Lua; destructive local rollback discards only Redis volumes, never PostgreSQL.

## Deferred Redis 8.8 features

Redis Search/JSON/TimeSeries/Array, Streams as a durable queue, modules, and Sentinel/Cluster remain out of scope until measured workload or multi-host availability requirements justify them.
