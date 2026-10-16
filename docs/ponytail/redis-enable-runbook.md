# Enabling distributed Redis (rate-limit + cache)

## Why

Without Redis, permission-cache invalidation and rate-limiting are **per-instance**
(Caffeine local). Behind a load balancer with 2+ instances this is wrong: rate
limits count per pod, and a cache invalidation on one pod does not reach the
others. Turning Redis on makes both **distributed / cross-instance**.

Redis is **off by default** on purpose so dev/local boots without it. Enabling it
is an operational decision, done via the `redis` Spring profile + one or two env
vars. Nothing is hardcoded.

## What flips (traced in code)

| Flag (default `false`) | Turns on |
|---|---|
| `erg.redis.state.enabled` | Shared `StringRedisTemplate`, pub/sub `RedisMessageListenerContainer` subscriber, `RedisCacheGenerationService` (cross-instance cache generations), and the `GraphQlRateLimitCounterRouter` bean. `AuthLoginRateLimiter` picks up the router automatically (`ObjectProvider`). |
| `erg.graphql.rate-limit.redis-enabled` | `GraphQlRateLimiter.allow()` actually routes to the router. State-enabled alone is not enough for GraphQL rate-limit — this flag gates the branch. |
| `erg.cache.l2-enabled` + `erg.redis.cache.enabled` | L2 `RedisCacheStore` (separate cache endpoint) + `ErgTieredCache` promotes listed names to L2. |

The `redis` profile in `application.yml` sets all four to `true`.

> The custom `RedisInfrastructureConfig` builds its own Lettuce factory from
> `erg.redis.*` — it does **not** use Spring Boot's `DataRedisAutoConfiguration`,
> so the `spring.autoconfigure.exclude` list is left untouched (dev still boots
> without Redis). No change needed there.

## How to enable

1. Add the `redis` profile alongside your environment profile:
   ```
   SPRING_PROFILES_ACTIVE=prod,redis
   ```
2. Provide the endpoint URL(s) via env (never in yaml):
   - **State** (rate-limit + cache generation/pub-sub): `SPRING_DATA_REDIS_URL`
     (feeds `erg.redis.state.url` via fallback). e.g. `redis://:pass@host:6379/0`
   - **L2 cache** (separate endpoint, no fallback): `ERG_REDIS_CACHE_URL`
   - To actually cache anything in L2, list cache names:
     `ERG_REDIS_CACHE_L2_NAMES=authorizationRoles,authorizationPermissions,...`
     (empty = L2 wired but promotes nothing).

Ordering: set env vars first, then start with the profile active.

## Fail-fast on missing URL

`ErgRedisProperties` has `@AssertTrue` validators:
- `erg.redis.state.url must be configured when erg.redis.state.enabled=true`
- `erg.redis.cache.url must be configured when erg.redis.cache.enabled=true`

So enabling the `redis` profile **without** `SPRING_DATA_REDIS_URL` (and
`ERG_REDIS_CACHE_URL`) fails the context at startup with a clear message — no
silent wrong behavior.

## Verify it's live

- Enable health details and check the indicators — `redisStateHealth` and
  `redisCacheHealth` beans only exist when the respective flags are on:
  `MANAGEMENT_HEALTH_REDIS_ENABLED=true`, `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`,
  then `GET /actuator/health` shows the redis contributors UP.
- Metrics: `redisStateMetrics` / `redisCacheMetrics` binders publish under the
  `state` / `cache` tags to Prometheus (`/actuator/prometheus`).
- Behavior check: hit a rate-limited endpoint from two instances — the 429 should
  trip on the **combined** count, not per-pod.

## Warnings

- Rate-limiting becomes **distributed**: the configured requests-per-minute now
  applies across the whole fleet, not per instance. Effective per-user throughput
  drops vs. running N independent local limiters — retune limits if needed.
- `erg.graphql.rate-limit.fail-closed-on-redis-error` (default `true`): on a Redis
  error, GraphQL requests are **rejected** (429). Set `false` to fall back to the
  local counter instead.
- Cache invalidation goes **cross-instance** via pub/sub — an eviction on one pod
  now clears the others. Ensure the state Redis is reachable from every instance.
- Use a real, reachable Redis. There is no localhost default in the `redis`
  profile; the URL must come from env.
