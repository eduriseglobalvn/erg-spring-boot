# Redis 8.8 operating gates

- Warn when `used_memory/maxmemory >= 70%`; investigate TTL mix, oversized bypasses, hit rate, and hot-key distribution.
- Critical at `>= 85%`; stop raising Redis memory unless PostgreSQL and the OS remain free of swap pressure.
- Any `redis-state` eviction, state write OOM, failed AOF write status, container OOM/restart, or PostgreSQL/Hikari pool collapse during cache fallback fails acceptance.
- A `redis-cache` outage is degraded service: reads fall back to PostgreSQL with the cache circuit open. It must not fail liveness.
- A required `redis-state` outage fails readiness and GraphQL rate limiting follows the configured fail-closed policy.
- Never mount `users.acl.example`. Generate an uncommitted ACL file with independent state/cache secrets, mount it read-only, then run `verify-redis.sh` before deployment.
