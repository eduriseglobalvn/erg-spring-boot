# Redis 8.8 Upgrade and Distributed Cache Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade ERG from one Redis 7.4 container to isolated Redis 8.8 state/cache services, make GraphQL rate limiting atomic, and add a tenant-safe L1 Caffeine + L2 Redis cache that remains correct under Redis failure and 5,000-concurrent-user load.

**Architecture:** `redis-state` owns session, limiter, cache-generation, and invalidation coordination with AOF and `noeviction`; `redis-cache` owns disposable TTL-bound DTO cache entries with `allkeys-lfu`. Spring uses a primary state connection plus a separately configured cache connection, a typed cache descriptor registry, process-local Caffeine L1, Redis L2, generation-based broad invalidation, and PostgreSQL fallback protected against stampedes.

**Tech Stack:** Spring Boot 4.0.6, Spring Data Redis 4.0.5, Lettuce 6.8.2, Caffeine, Jackson, Micrometer, Resilience4j, Testcontainers 2.0.5, Redis 8.8.0 Alpine 3.23, Docker Compose, PostgreSQL 17, Java 25, GraalVM Native Image, Maven, PowerShell, Bash, and k6.

**Approved design:** `docs/superpowers/specs/2026-07-16-redis-8-8-upgrade-design.md`

---

## File structure and ownership

New production files are grouped by responsibility:

- `platform/redis/*`: Redis properties, two connection factories, capability detection, state/cache health, and command adapters.
- `platform/cache/*`: cache descriptors, deterministic keys, codecs, L1/L2 behavior, generation state, invalidation messages, metrics, and runtime hints.
- `platform/graphql/ratelimit/*`: version-independent fixed-window counter implementations.
- `ops/docker/redis/*`: state/cache Redis configuration and ACL examples.
- `scripts/migrate-redis-8.8-local.*`: guarded destructive local migration only.
- `load/k6/redis-8-8-cache.js`: cold, warm, and failure workload; it complements the existing system-wide 5,000-user scripts.

Existing business services retain their current read APIs. Cache infrastructure is introduced behind Spring's `CacheManager`; business DTO and HTTP/GraphQL payload types do not change.

## Task 1: Pin and split Redis 8.8 Docker services

**Files:**

- Modify: `scripts/verify-docker-config.ps1`
- Modify: `ops/docker/compose.yml`
- Modify: `ops/docker/compose.dev.yml`
- Modify: `ops/docker/compose.native.yml`
- Modify: `ops/docker/.env.dev.example`
- Modify: `ops/docker/.env.production.example`
- Create: `ops/docker/redis/state.conf`
- Create: `ops/docker/redis/cache.conf`
- Create: `ops/docker/redis/users.acl.example`
- Test: `scripts/verify-docker-config.ps1`

- [ ] **Step 1: Make the Docker contract test require two exact Redis 8.8 services.**

Replace the Redis 7.4 assertion and add assertions equivalent to:

```powershell
Require-Match $composeBase 'redis-state:' "Base Compose must define redis-state"
Require-Match $composeBase 'redis-cache:' "Base Compose must define redis-cache"
Require-Match $composeBase 'redis:8\.8\.0-alpine3\.23' "Redis services must pin 8.8.0-alpine3.23"
Require-Match $composeBase 'state\.conf' "redis-state must load state.conf"
Require-Match $composeBase 'cache\.conf' "redis-cache must load cache.conf"
Require-Match $composeDev '127\.0\.0\.1:\$\{REDIS_STATE_HOST_PORT:-6379\}:6379' "Development state Redis must bind loopback"
Require-Match $composeDev '127\.0\.0\.1:\$\{REDIS_CACHE_HOST_PORT:-6380\}:6379' "Development cache Redis must bind loopback"
```

- [ ] **Step 2: Run the contract and confirm it fails on the old single Redis definition.**

Run:

```powershell
pwsh -File scripts/verify-docker-config.ps1
```

Expected: non-zero exit with missing `redis-state`, `redis-cache`, and Redis 8.8 assertions.

- [ ] **Step 3: Add state and cache Redis configuration.**

`state.conf` must contain the approved 16 GiB defaults:

```conf
bind 0.0.0.0
protected-mode yes
port 6379
appendonly yes
appendfsync everysec
auto-aof-rewrite-percentage 100
auto-aof-rewrite-min-size 64mb
maxmemory 768mb
maxmemory-policy noeviction
slowlog-log-slower-than 10000
slowlog-max-len 256
latency-monitor-threshold 100
```

`cache.conf` must contain:

```conf
bind 0.0.0.0
protected-mode yes
port 6379
appendonly no
save ""
maxmemory 2gb
maxmemory-policy allkeys-lfu
slowlog-log-slower-than 10000
slowlog-max-len 256
latency-monitor-threshold 100
```

Do not put passwords in either committed `.conf` file. Keep `users.acl.example` limited to non-secret examples and documentation; runtime credentials come from an uncommitted ACL/secrets mount or local-only environment.

- [ ] **Step 4: Replace the single service with isolated services and volumes.**

Use exact service names, independent healthchecks, `REDISCLI_AUTH`, internal networking, and resource limits:

```yaml
redis-state:
  image: redis:8.8.0-alpine3.23
  command: ["redis-server", "/usr/local/etc/redis/state.conf", "--requirepass", "${REDIS_STATE_PASSWORD:-erg-local-redis-state}"]
  environment:
    REDISCLI_AUTH: ${REDIS_STATE_PASSWORD:-erg-local-redis-state}
  mem_limit: ${REDIS_STATE_CONTAINER_MEMORY:-1280m}
  volumes:
    - redis-state-data:/data
    - ./redis/state.conf:/usr/local/etc/redis/state.conf:ro

redis-cache:
  image: redis:8.8.0-alpine3.23
  command: ["redis-server", "/usr/local/etc/redis/cache.conf", "--requirepass", "${REDIS_CACHE_PASSWORD:-erg-local-redis-cache}"]
  environment:
    REDISCLI_AUTH: ${REDIS_CACHE_PASSWORD:-erg-local-redis-cache}
  mem_limit: ${REDIS_CACHE_CONTAINER_MEMORY:-2560m}
  volumes:
    - redis-cache-data:/data
    - ./redis/cache.conf:/usr/local/etc/redis/cache.conf:ro
```

The current deployment is local-only, so Task 1 preserves password authentication with explicit development defaults while keeping Redis loopback/internal-only. Task 9 replaces this with generated, uncommitted ACL mounts before any production deployment; production examples must never contain a literal password.

- [ ] **Step 5: Add the development bindings and environment contracts.**

Use `6379` for state and `6380` for cache, both bound to `127.0.0.1`. Add `REDIS_STATE_*`, `REDIS_CACHE_*`, `ERG_REDIS_CACHE_ENABLED`, and both URLs to example env files. Retain `SPRING_DATA_REDIS_URL` as the state URL for Spring Session compatibility.

- [ ] **Step 6: Validate rendered Compose configuration.**

Run:

```powershell
pwsh -File scripts/verify-docker-config.ps1
docker compose --env-file ops/docker/.env.dev.example -f ops/docker/compose.yml -f ops/docker/compose.dev.yml config --quiet
```

Expected: both commands exit zero; rendered config contains two Redis 8.8.0 services, two volumes, and no public `0.0.0.0` Redis port.

- [ ] **Step 7: Commit Docker isolation.**

```powershell
git add scripts/verify-docker-config.ps1 ops/docker/compose.yml ops/docker/compose.dev.yml ops/docker/compose.native.yml ops/docker/.env.dev.example ops/docker/.env.production.example ops/docker/redis
git commit -m "infra: split state and cache Redis 8.8 services"
```

## Task 2: Add a guarded destructive local Redis migration

**Files:**

- Create: `scripts/migrate-redis-8.8-local.ps1`
- Create: `scripts/migrate-redis-8.8-local.sh`
- Create: `ops/docker/redis/README.md`
- Modify: `scripts/docker.ps1`
- Modify: `scripts/docker.sh`
- Test: manual guarded dry-run and real local migration

- [ ] **Step 1: Implement dry-run verification before any volume deletion.**

The PowerShell script must resolve the workspace, require Compose project `erg-dev`, and accept `-Execute` before mutation:

```powershell
param([switch]$Execute)
$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$composeDir = Join-Path $root "ops/docker"
if (-not $composeDir.StartsWith($root, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Resolved Compose directory escaped workspace: $composeDir"
}
$oldContainer = docker ps -a --filter "name=^/erg-dev-redis-1$" --format "{{.Names}}"
$oldVolumes = docker volume ls --filter "name=^erg-dev_redis-data$" --format "{{.Name}}"
if (-not $Execute) {
    Write-Host "Dry run: old container=$oldContainer old volume=$oldVolumes"
    exit 0
}
```

The shell version must enforce the same exact project/container/volume names and workspace boundary. Neither script may use wildcard volume deletion or `docker system prune`.

- [ ] **Step 2: Add exact stop/remove/start and verification operations.**

After `-Execute`, stop only the old Redis service/container, remove only `erg-dev_redis-data` when present, pull the two exact images, start the new services, and verify:

```text
redis-cli INFO server -> redis_version:8.8.0
redis-cli COMMAND INFO INCREX -> command metadata present
redis-state CONFIG GET appendonly -> yes
redis-state CONFIG GET maxmemory-policy -> noeviction
redis-cache CONFIG GET appendonly -> no
redis-cache CONFIG GET maxmemory-policy -> allkeys-lfu
```

- [ ] **Step 3: Document local-only destructive semantics and rollback.**

The README must say PostgreSQL volumes are never touched, local session/cache data is discarded, and future production migration requires a different state-preserving runbook. Rollback recreates Redis 7.4 with an empty Redis volume and selects the Lua limiter.

- [ ] **Step 4: Run dry-run tests.**

```powershell
pwsh -File scripts/migrate-redis-8.8-local.ps1
```

Expected: prints only exact candidate resources and performs no Docker mutation.

- [ ] **Step 5: Run the approved destructive migration locally.**

```powershell
pwsh -File scripts/migrate-redis-8.8-local.ps1 -Execute
docker exec erg-dev-redis-state-1 redis-cli INFO server
docker exec erg-dev-redis-cache-1 redis-cli INFO memory
```

Expected: both services are healthy on Redis 8.8.0; PostgreSQL remains healthy and its volume ID is unchanged.

- [ ] **Step 6: Commit the guarded migration tooling.**

```powershell
git add scripts/migrate-redis-8.8-local.ps1 scripts/migrate-redis-8.8-local.sh scripts/docker.ps1 scripts/docker.sh ops/docker/redis/README.md
git commit -m "ops: add guarded local Redis 8.8 migration"
```

## Task 3: Configure independent state and cache Redis clients

**Files:**

- Create: `src/main/java/com/erg/ergspringboot/platform/redis/ErgRedisProperties.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/redis/RedisInfrastructureConfig.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/redis/RedisClientFactory.java`
- Modify: `src/main/resources/application.yml`
- Modify: `ops/docker/.env.dev`
- Modify: `ops/docker/.env.dev.example`
- Modify: `ops/docker/.env.production.example`
- Test: `src/test/java/com/erg/ergspringboot/platform/redis/RedisInfrastructureConfigTest.java`

- [ ] **Step 1: Write failing configuration-context tests.**

Use `ApplicationContextRunner` to assert:

```java
assertThat(context).hasSingleBean(ErgRedisProperties.class);
assertThat(context).hasBean("redisConnectionFactory");
assertThat(context).hasBean("stateStringRedisTemplate");
assertThat(context).hasBean("cacheRedisConnectionFactory");
assertThat(context).hasBean("cacheStringRedisTemplate");
assertThat(context.getBean("redisConnectionFactory", RedisConnectionFactory.class))
        .isNotSameAs(context.getBean("cacheRedisConnectionFactory", RedisConnectionFactory.class));
```

Add a negative test: `erg.redis.cache.enabled=true` without `erg.redis.cache.url` fails startup with a clear property-validation message.

- [ ] **Step 2: Run the focused test and confirm missing classes/beans.**

```powershell
./mvnw.cmd -Dtest=RedisInfrastructureConfigTest test
```

Expected: FAIL because the new Redis configuration does not exist.

- [ ] **Step 3: Add validated properties.**

Create immutable/nested properties for `state` and `cache` with URL, pool size, minimum idle, connect timeout, command timeout, SSL, and enabled flags. The stable external configuration is:

```yaml
erg:
  redis:
    state:
      enabled: ${ERG_REDIS_STATE_ENABLED:false}
      url: ${ERG_REDIS_STATE_URL:${SPRING_DATA_REDIS_URL:}}
      connect-timeout: ${ERG_REDIS_STATE_CONNECT_TIMEOUT:1s}
      command-timeout: ${ERG_REDIS_STATE_COMMAND_TIMEOUT:1s}
      pool-size: ${ERG_REDIS_STATE_POOL_SIZE:16}
    cache:
      enabled: ${ERG_REDIS_CACHE_ENABLED:false}
      url: ${ERG_REDIS_CACHE_URL:}
      connect-timeout: ${ERG_REDIS_CACHE_CONNECT_TIMEOUT:250ms}
      command-timeout: ${ERG_REDIS_CACHE_COMMAND_TIMEOUT:500ms}
      pool-size: ${ERG_REDIS_CACHE_POOL_SIZE:24}
```

- [ ] **Step 4: Build two Lettuce factories without duplicating parsing logic.**

`RedisClientFactory` parses `RedisStandaloneConfiguration` from each URL, builds bounded `LettucePoolingClientConfiguration`, enables connection validation, and applies only the configured TLS/timeouts. `redisConnectionFactory` is `@Primary` and retains the conventional bean name required by Spring Session and health auto-configuration. Cache infrastructure injects only `@Qualifier("cacheRedisConnectionFactory")`.

- [ ] **Step 5: Run configuration tests and dependency checks.**

```powershell
./mvnw.cmd -Dtest=RedisInfrastructureConfigTest test
./mvnw.cmd dependency:tree -Dincludes=org.apache.commons:commons-pool2,io.lettuce:lettuce-core
```

Expected: tests PASS; dependency tree contains the pool implementation used by the factory. Add `commons-pool2` explicitly only if absent.

- [ ] **Step 6: Commit dual-client infrastructure.**

```powershell
git add src/main/java/com/erg/ergspringboot/platform/redis src/main/resources/application.yml ops/docker/.env.dev ops/docker/.env.dev.example ops/docker/.env.production.example pom.xml
git commit -m "feat: configure separate Redis state and cache clients"
```

## Task 4: Replace GraphQL rate limiting with atomic Redis 8.8 behavior

**Files:**

- Create: `src/main/java/com/erg/ergspringboot/platform/graphql/ratelimit/GraphQlRateLimitCounter.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/graphql/ratelimit/RateLimitCounterResult.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/graphql/ratelimit/RedisCapabilityDetector.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/graphql/ratelimit/Redis88IncrexRateLimitCounter.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/graphql/ratelimit/LuaFixedWindowRateLimitCounter.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/graphql/ratelimit/LocalEmergencyRateLimitCounter.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/graphql/ratelimit/GraphQlRateLimitCounterRouter.java`
- Modify: `src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlRateLimiter.java`
- Modify: `src/main/java/com/erg/ergspringboot/platform/graphql/ErgGraphQlProperties.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/erg/ergspringboot/platform/graphql/GraphQlRateLimiterTest.java`
- Test: `src/test/java/com/erg/ergspringboot/platform/graphql/ratelimit/Redis88IncrexRateLimitCounterIntegrationTest.java`
- Test: `src/test/java/com/erg/ergspringboot/platform/graphql/ratelimit/LuaFixedWindowRateLimitCounterIntegrationTest.java`

- [ ] **Step 1: Write algorithm contract tests before implementations.**

Use a mutable test clock and assert the common interface:

```java
record RateLimitCounterResult(long value, boolean incrementApplied, Instant expiresAt) {}

interface GraphQlRateLimitCounter {
    RateLimitCounterResult increment(String key, long upperBound, Instant windowEnd);
}
```

Tests cover requests `1..limit`, rejection at `limit+1`, a new window, exact `Retry-After`, fail-closed state error, bounded local fallback, and no process-local bypass when distributed limiting is enabled.

- [ ] **Step 2: Write Redis 8.8 and Lua parity integration tests.**

Start `redis:8.8.0-alpine3.23` with Testcontainers. Assert that `INCREX key BYINT 1 UBOUND 3 PXAT <end> ENX` returns applied increments for the first three calls and zero actual increment afterward. Assert `PTTL` is within tolerance of the exact wall-clock boundary. Run the same cases through the Lua implementation and compare results.

- [ ] **Step 3: Confirm focused tests fail.**

```powershell
./mvnw.cmd -Dtest=GraphQlRateLimiterTest,Redis88IncrexRateLimitCounterIntegrationTest,LuaFixedWindowRateLimitCounterIntegrationTest test
```

Expected: FAIL because the counter abstraction and implementations do not exist.

- [ ] **Step 4: Implement capability detection and raw `INCREX`.**

Execute `COMMAND INFO INCREX` once after connection and cache the capability. Execute the command through the state connection's low-level `RedisConnection.execute` API and strictly parse the two-element numeric reply. Unexpected reply shape throws a typed infrastructure exception and increments an error metric; it never defaults to allow.

- [ ] **Step 5: Implement the atomic Lua fallback.**

The script must use Redis server time/absolute expiry inputs supplied by the caller, increment only below the bound, and set `PEXPIREAT` only when the key has no TTL. Cache the script SHA through Spring's script executor rather than sending source on every request.

- [ ] **Step 6: Route counters and simplify `GraphQlRateLimiter`.**

Move Redis command details out of `GraphQlRateLimiter`. Correct JavaDoc from sliding/token bucket to fixed window. Build the Redis key from the existing SHA-256 digest plus `windowStart`. Preserve the HTTP 429 and `Retry-After` contract.

- [ ] **Step 7: Run rate-limit tests against Redis 8.8.**

```powershell
./mvnw.cmd -Dtest=GraphQlRateLimiterTest,*RateLimitCounter*Test test
```

Expected: PASS; no counter remains without TTL; `INCREX` and Lua results match.

- [ ] **Step 8: Commit atomic rate limiting.**

```powershell
git add src/main/java/com/erg/ergspringboot/platform/graphql src/test/java/com/erg/ergspringboot/platform/graphql src/main/resources/application.yml
git commit -m "feat: use Redis 8.8 atomic GraphQL rate limiting"
```

## Task 5: Build typed, tenant-safe cache descriptors and codecs

**Files:**

- Create: `src/main/java/com/erg/ergspringboot/platform/cache/ErgCacheProperties.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/cache/CacheDescriptor.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/cache/CacheDescriptorRegistry.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/cache/CacheKeyFactory.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/cache/CacheValueCodec.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/cache/JacksonCacheValueCodec.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/cache/OversizedCacheValueException.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/cache/CacheRuntimeHints.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/erg/ergspringboot/platform/cache/CacheDescriptorRegistryTest.java`
- Test: `src/test/java/com/erg/ergspringboot/platform/cache/CacheKeyFactoryTest.java`
- Test: `src/test/java/com/erg/ergspringboot/platform/cache/JacksonCacheValueCodecTest.java`

- [ ] **Step 1: Write descriptor-validation and deterministic-key tests.**

Assert duplicate names, missing tenant policy, non-positive TTL, and missing Java type fail startup. Assert equivalent scope IDs in different order produce identical hashes while different tenants produce different keys:

```java
assertThat(keys.businessKey("tenant-a", descriptor, List.of("c2", "c1"), 7))
        .isEqualTo(keys.businessKey("tenant-a", descriptor, List.of("c1", "c2"), 7));
assertThat(keys.businessKey("tenant-a", descriptor, "42", 7))
        .isNotEqualTo(keys.businessKey("tenant-b", descriptor, "42", 7));
```

- [ ] **Step 2: Write codec tests including the 1 MiB boundary.**

Round-trip every initial descriptor DTO. Assert encoded values at exactly the configured limit are accepted, larger values are marked as bypassed, malformed JSON is treated as a cache miss plus corruption metric, and secrets/JPA entities have no descriptor.

- [ ] **Step 3: Confirm tests fail.**

```powershell
./mvnw.cmd -Dtest=CacheDescriptorRegistryTest,CacheKeyFactoryTest,JacksonCacheValueCodecTest test
```

Expected: FAIL because cache foundation classes do not exist.

- [ ] **Step 4: Implement immutable descriptors and explicit registration.**

Use a record with explicit policy rather than a map of unchecked `Class<?>` values:

```java
public record CacheDescriptor<T>(
        String name,
        JavaType valueType,
        Duration l1Ttl,
        Duration l2Ttl,
        long l1MaximumWeightBytes,
        int maximumSerializedBytes,
        boolean tenantRequired,
        boolean cacheNulls
) {}
```

Register only approved DTO/read-model types. Do not register entities, Hibernate proxies, `PageImpl`, streams, authentication tokens, or permission allow decisions.

- [ ] **Step 5: Implement canonical SHA-256 keys and bounded JSON.**

Canonicalization sorts unordered IDs, preserves ordered pagination/sort inputs, normalizes null explicitly, and hashes the canonical payload. Redis keys contain namespace version, tenant, cache name, generation, and hash; they never contain raw query text, tokens, or PII.

- [ ] **Step 6: Register Native Image hints from the descriptor registry.**

`CacheRuntimeHints` registers constructors, record components, and serialization-visible members only for descriptor value types. Add a test that every L2-enabled descriptor exposes a hint registration path.

- [ ] **Step 7: Run cache foundation tests.**

```powershell
./mvnw.cmd -Dtest=CacheDescriptorRegistryTest,CacheKeyFactoryTest,JacksonCacheValueCodecTest test
```

Expected: PASS with deterministic tenant isolation and size enforcement.

- [ ] **Step 8: Commit typed cache foundation.**

```powershell
git add src/main/java/com/erg/ergspringboot/platform/cache src/test/java/com/erg/ergspringboot/platform/cache src/main/resources/application.yml
git commit -m "feat: add typed tenant-safe cache descriptors"
```

## Task 6: Implement the true L1 Caffeine plus L2 Redis cache

**Files:**

- Create: `src/main/java/com/erg/ergspringboot/platform/cache/ErgTieredCacheManager.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/cache/ErgTieredCache.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/cache/RedisCacheStore.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/cache/CacheLoadCoordinator.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/cache/CacheFailurePolicy.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/cache/CacheMetrics.java`
- Modify: `src/main/java/com/erg/ergspringboot/config/CacheConfig.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/erg/ergspringboot/platform/cache/ErgTieredCacheTest.java`
- Test: `src/test/java/com/erg/ergspringboot/platform/cache/RedisCacheFailureIntegrationTest.java`
- Test: `src/test/java/com/erg/ergspringboot/platform/cache/CacheStampedeIntegrationTest.java`

- [ ] **Step 1: Write L1/L2 behavior tests.**

Test this exact sequence: first call invokes loader and populates L2/L1; second call hits L1; after L1 expiry it hits L2 without loader; after both TTLs it invokes loader again. Assert `put`, `putIfAbsent`, `evict`, `clear`, `get(key, type)`, and `get(key, Callable)` obey Spring `Cache` semantics.

- [ ] **Step 2: Write outage, corruption, timeout, and stampede tests.**

Stop the Testcontainers Redis cache and assert successful PostgreSQL/test-loader fallback. Launch 100 concurrent `get(key, loader)` calls and assert one loader execution per process/key. Inject malformed bytes and assert eviction plus reload, not a user-visible JSON exception.

- [ ] **Step 3: Confirm tests fail.**

```powershell
./mvnw.cmd -Dtest=ErgTieredCacheTest,RedisCacheFailureIntegrationTest,CacheStampedeIntegrationTest test
```

Expected: FAIL because the tiered manager does not exist.

- [ ] **Step 4: Implement L1, Redis L2, and single-flight loading.**

`ErgTieredCacheManager` creates one `ErgTieredCache` per descriptor. L1 Caffeine uses `maximumWeight` with encoded/estimated byte weight rather than entry count. `CacheLoadCoordinator` uses a bounded concurrent map of in-flight `CompletableFuture`s and removes entries in `whenComplete`, including exceptional completion.

- [ ] **Step 5: Implement failure policy and short circuit breaker.**

Redis get/put/evict timeouts increment metrics and return cache miss/best-effort completion. After the configured failure threshold, bypass L2 for the open duration. Half-open recovery permits bounded probes. Loader exceptions propagate unchanged and are never cached.

- [ ] **Step 6: Replace `SimpleCacheManager` without enabling L2 globally.**

`CacheConfig` constructs the tiered manager from descriptors. Every current cache name remains registered. Caches not included in `ERG_REDIS_CACHE_L2_NAMES` remain L1-only, preserving safe rollout. Default local configuration initially enables no business L2 cache.

- [ ] **Step 7: Run behavior and existing cache registration tests.**

```powershell
./mvnw.cmd -Dtest=ErgTieredCacheTest,RedisCacheFailureIntegrationTest,CacheStampedeIntegrationTest,SchoolCacheRegistrationTest,LmsControllerHttpCacheTest test
```

Expected: PASS; existing cache names remain present; Redis outage does not multiply loader calls uncontrollably.

- [ ] **Step 8: Commit tiered caching.**

```powershell
git add src/main/java/com/erg/ergspringboot/platform/cache src/main/java/com/erg/ergspringboot/config/CacheConfig.java src/test/java/com/erg/ergspringboot/platform/cache src/main/resources/application.yml
git commit -m "feat: add resilient Caffeine and Redis tiered cache"
```

## Task 7: Add distributed generation and post-commit invalidation

**Files:**

- Create: `src/main/java/com/erg/ergspringboot/platform/cache/CacheGenerationService.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/cache/RedisCacheGenerationService.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/cache/CacheInvalidationMessage.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/cache/CacheInvalidationPublisher.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/cache/CacheInvalidationSubscriber.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/cache/CacheInvalidationService.java`
- Modify: `src/main/java/com/erg/ergspringboot/platform/redis/RedisInfrastructureConfig.java`
- Modify: `src/main/java/com/erg/ergspringboot/platform/cache/ErgTieredCache.java`
- Test: `src/test/java/com/erg/ergspringboot/platform/cache/CacheGenerationIntegrationTest.java`
- Test: `src/test/java/com/erg/ergspringboot/platform/cache/CrossReplicaInvalidationIntegrationTest.java`
- Test: `src/test/java/com/erg/ergspringboot/platform/cache/PostCommitInvalidationTest.java`

- [ ] **Step 1: Write two-replica invalidation tests.**

Create two cache managers with independent L1 caches and shared Redis containers. Populate both, publish exact invalidation, and assert both L1s miss afterward. Repeat with `clearTenantCache(tenantId, cacheName)` and assert only that tenant's generation changes.

- [ ] **Step 2: Write transaction ordering tests.**

Assert rollback publishes nothing and leaves generation unchanged. Assert commit registers one `afterCommit` action even when the same cache/key is requested repeatedly in one transaction.

- [ ] **Step 3: Confirm tests fail.**

```powershell
./mvnw.cmd -Dtest=CacheGenerationIntegrationTest,CrossReplicaInvalidationIntegrationTest,PostCommitInvalidationTest test
```

Expected: FAIL because invalidation infrastructure does not exist.

- [ ] **Step 4: Implement generation state in `redis-state`.**

Generation keys use `erg:state:v1:cache-generation:{tenant}:{cache}` and atomic `INCR`. Each application keeps a short local generation snapshot updated by invalidation messages. On startup/reconnect, it reloads required generations. If state Redis is unavailable, L2 reads requiring an unknown generation are bypassed rather than guessing generation zero.

- [ ] **Step 5: Implement explicit Pub/Sub messages.**

Messages contain schema version, event ID, source instance ID, tenant, cache name, generation, optional canonical key hash, and timestamp. Subscribers validate fields and ignore unknown versions. Pub/Sub is best effort; L1 TTL bounds missed-message staleness.

- [ ] **Step 6: Implement post-commit registration.**

Use `TransactionSynchronizationManager.registerSynchronization` when a transaction is active; otherwise invalidate immediately. Deduplicate requested invalidations in a transaction-scoped resource. Do not publish before commit.

- [ ] **Step 7: Run distributed invalidation tests.**

```powershell
./mvnw.cmd -Dtest=CacheGenerationIntegrationTest,CrossReplicaInvalidationIntegrationTest,PostCommitInvalidationTest test
```

Expected: PASS; rollback retains old values, commit invalidates both replica L1s, unrelated tenants retain their cache.

- [ ] **Step 8: Commit invalidation infrastructure.**

```powershell
git add src/main/java/com/erg/ergspringboot/platform/cache src/main/java/com/erg/ergspringboot/platform/redis src/test/java/com/erg/ergspringboot/platform/cache
git commit -m "feat: add tenant generation cache invalidation"
```

## Task 8: Roll out L2 cache families and remove broad invalidations

**Files:**

- Modify: `src/main/java/com/erg/ergspringboot/config/CacheConfig.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/elearning/readmodel/StudentDashboardReadService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/lms/readmodel/LmsReadModelService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/lcms/readmodel/LcmsWorkspaceReadService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/lcms/readmodel/LcmsQuizWorkspaceReadService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/quiz/runtime/QuizRuntimePackageService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/quiz/report/QuizReportService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/school/SchoolOperationsReadService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/organization/EducationUnitService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/calendar/CalendarReadService.java`
- Modify mutation services containing broad evictions in `src/main/java/com/erg/ergspringboot/service/lms/`, `service/quiz/`, `service/lcms/`, `service/school/`, `service/organization/`, and `service/calendar/`
- Test: `src/test/java/com/erg/ergspringboot/platform/cache/BusinessCacheIsolationIntegrationTest.java`
- Test: `src/test/java/com/erg/ergspringboot/platform/cache/BusinessCacheInvalidationIntegrationTest.java`
- Test: existing read-model/controller contract tests

- [ ] **Step 1: Inventory every cache key and invalidation owner in a test fixture.**

Create a parameterized inventory asserting each approved L2 cache has descriptor type, tenant-bearing key expression, read owner, mutation invalidation owner, L1/L2 TTL, and maximum bytes. The initial approved names are:

```text
studentDashboard
lmsClassWorkspace
lmsHomeworkWorkspace
lmsAssignmentProgress
lmsStudentGroups
lcmsQuestionBankWorkspace
lcmsQuizWorkspace
lcmsSchoolDirectory
quizRuntimePackage
quizQuestionAnalytics
quizAssignmentReport
organizationEducationUnits
calendarWorkspace
```

- [ ] **Step 2: Write tenant, user-scope, mutation, and self-invocation regression tests.**

For each family, create two tenants with identical entity IDs and disjoint results. Include managed-class/current-class/user scope where response content depends on it. Assert mutations invalidate only the affected entity or tenant/cache generation. Ensure cached methods are invoked through a Spring proxy, not `this.cachedMethod()` self-invocation.

- [ ] **Step 3: Run the inventory/isolation tests and record failures.**

```powershell
./mvnw.cmd -Dtest=BusinessCacheIsolationIntegrationTest,BusinessCacheInvalidationIntegrationTest test
```

Expected: FAIL on missing descriptors, incomplete keys, self-invocation, and broad invalidation paths.

- [ ] **Step 4: Register and enable one family at a time.**

Order rollout to minimize blast radius:

1. organization/school directory;
2. quiz runtime package;
3. question-bank/LCMS workspaces;
4. LMS workspaces/student dashboard;
5. quiz analytics/reports;
6. calendar workspace.

After each family, run its focused tests and record serialized p50/p95/p99 size. Values over 1 MiB bypass L2; do not raise the limit to make a test pass.

- [ ] **Step 5: Replace touched `allEntries=true` mutations.**

Use exact post-commit invalidation when entity/result keys are enumerable. Use tenant/cache generation increments for filtered lists and workspaces. Never call a global `Cache.clear()` for a single-tenant mutation. Leave auth/permission broad-eviction cleanup outside this business-cache rollout unless required for correctness, because authorization allow caching remains excluded.

- [ ] **Step 6: Verify all business cache contracts.**

```powershell
./mvnw.cmd -Dtest=BusinessCacheIsolationIntegrationTest,BusinessCacheInvalidationIntegrationTest,*ReadModel*Test,*Workspace*Test,*Report*Test test
```

Expected: PASS; zero cross-tenant/user leak; each mutation causes bounded invalidation; L2 values stay within size policy.

- [ ] **Step 7: Commit each cache family independently.**

Use separate commits such as:

```powershell
git commit -m "perf: distribute organization and school caches"
git commit -m "perf: distribute quiz runtime cache"
git commit -m "perf: distribute LCMS workspace caches"
git commit -m "perf: distribute LMS dashboard caches"
git commit -m "perf: distribute report and calendar caches"
```

Do not combine unrelated dirty-worktree files in these commits.

## Task 9: Add Redis observability, ACL validation, and Native Image gates

**Files:**

- Create: `src/main/java/com/erg/ergspringboot/platform/redis/RedisStateHealthIndicator.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/redis/RedisCacheHealthIndicator.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/redis/RedisMetricsBinder.java`
- Modify: `src/main/java/com/erg/ergspringboot/platform/web/PlatformHealthController.java`
- Modify: `src/main/java/com/erg/ergspringboot/platform/cache/CacheMetrics.java`
- Modify: `src/main/resources/application.yml`
- Modify: `ops/docker/redis/users.acl.example`
- Create: `ops/docker/redis/verify-redis.sh`
- Create: `src/test/java/com/erg/ergspringboot/platform/redis/RedisHealthAndMetricsTest.java`
- Create: `src/test/java/com/erg/ergspringboot/platform/cache/CacheNativeImageContractTest.java`
- Modify: `scripts/verify-docker-config.ps1`

- [ ] **Step 1: Write health semantics and metric-cardinality tests.**

Assert state failure makes readiness unavailable only when configured state-dependent behavior is enabled. Cache failure reports degraded cache health but does not fail application liveness. Metrics use bounded tags: cache name, tier, operation, and outcome; tenant, user, key, operation text, and exception message are forbidden tags.

- [ ] **Step 2: Write ACL contract checks.**

The verification script authenticates as each application user, asserts required string/PubSub/script commands succeed, and asserts `CONFIG`, `MODULE`, `DEBUG`, `SHUTDOWN`, replication, and database-wide destructive commands are denied. Review Redis 8 ACL categories instead of granting broad `+@all`.

- [ ] **Step 3: Run tests and confirm missing behavior.**

```powershell
./mvnw.cmd -Dtest=RedisHealthAndMetricsTest,CacheNativeImageContractTest test
```

Expected: FAIL because split health, metrics, and hint contracts are absent.

- [ ] **Step 4: Bind operational and cache metrics.**

Expose L1/L2 hit/miss, load, serialization bytes/time, size bypass, failure/circuit/fallback, single-flight waiters, invalidations, limiter path, Redis memory ratio, evictions, AOF status, rejected connections, and slowlog count. Fetch expensive Redis INFO fields on a scheduled bounded interval, not on every scrape.

- [ ] **Step 5: Add alert/runbook thresholds.**

Document warning at 70% and critical at 85% of Redis `maxmemory`. Treat any state eviction, state write OOM, failed AOF status, container OOM kill, or PostgreSQL pool collapse during cache failure as an acceptance failure.

- [ ] **Step 6: Run JVM tests and Native Image compile/smoke.**

```powershell
./mvnw.cmd -Dtest=RedisHealthAndMetricsTest,CacheNativeImageContractTest test
./mvnw.cmd -Pnative -DskipTests native:compile
```

Start the native executable against the two local Redis services and call authenticated health, one REST cached read, one GraphQL cached read, and the GraphQL rate-limit boundary.

Expected: compilation and all smoke calls succeed without reflection/serialization errors.

- [ ] **Step 7: Commit observability and native support.**

```powershell
git add src/main/java/com/erg/ergspringboot/platform/redis src/main/java/com/erg/ergspringboot/platform/cache src/main/java/com/erg/ergspringboot/platform/web/PlatformHealthController.java src/test/java/com/erg/ergspringboot/platform ops/docker/redis scripts/verify-docker-config.ps1 src/main/resources/application.yml
git commit -m "ops: observe and secure Redis 8.8 cache infrastructure"
```

## Task 10: Prove cold, warm, failure, and 5,000-user behavior

**Files:**

- Create: `load/k6/redis-8-8-cache.js`
- Create: `load/fixtures/redis-cache-manifest.yaml`
- Create: `load/results/.gitkeep`
- Modify: `load/README.md`
- Create: `ops/docker/redis/capture-evidence.ps1`
- Create: `ops/docker/redis/capture-evidence.sh`
- Create: `docs/2026-07-16-redis-8-8-implementation-report.md`
- Conditionally create: `docs/frontend/2026-07-16-redis-8-8-api-migration.md` only if an approved payload/error contract change occurs

- [ ] **Step 1: Define reproducible cache workload and thresholds.**

Use the existing deterministic system fixture and include login/session, student dashboard, assignment list/detail, quiz runtime, question bank, teacher workspace, analytics/report, calendar, GraphQL combined query, and submission burst. The script exposes explicit scenarios for cold cache, warm cache, cache outage, state outage, and 5,000 concurrent workflows with realistic think time.

The script records p50/p95/p99, request rate, accepted errors/429s, L1/L2 hit rate, loader calls, Hikari pending, PostgreSQL statements/time, Redis memory/eviction/latency, application RSS, and container OOM/restart count.

- [ ] **Step 2: Add evidence capture with no secrets.**

Capture source revision, dirty-tree fingerprint, image IDs/digests, Redis `INFO` sections, selected non-secret `CONFIG GET` values, PostgreSQL fixture checksum, environment name, start/end timestamps, and raw k6 JSON. Redact URLs, usernames where sensitive, passwords, tokens, cookies, and Redis ACL material.

- [ ] **Step 3: Run the L1-only baseline.**

```powershell
$env:ERG_REDIS_CACHE_ENABLED='false'
k6 run --out json=load/results/redis-l1-only.json load/k6/redis-8-8-cache.js
```

Expected: baseline completes and records API/DB/host metrics; it is not labeled as 5,000-user acceptance unless the full scenario ran.

- [ ] **Step 4: Run Redis 8.8 cold and warm cache profiles.**

Flush only `redis-cache`, never state/PostgreSQL, then run cold followed by warm:

```powershell
docker exec erg-dev-redis-cache-1 sh -c 'redis-cli -a "$REDISCLI_AUTH" FLUSHDB'
k6 run --env CACHE_MODE=cold --out json=load/results/redis-8-8-cold.json load/k6/redis-8-8-cache.js
k6 run --env CACHE_MODE=warm --out json=load/results/redis-8-8-warm.json load/k6/redis-8-8-cache.js
```

Expected: every L2-enabled family shows a statistically meaningful p95 or database-load improvement; unrelated writes show no material regression.

- [ ] **Step 5: Run controlled failure profiles.**

Stop `redis-cache` during a steady read phase and assert PostgreSQL fallback remains bounded with no pool collapse. Separately stop `redis-state` and assert configured fail-closed GraphQL behavior, bounded emergency path, and no unlimited per-replica allowance. Restore each service and verify circuit recovery.

- [ ] **Step 6: Run the approved 5,000-concurrent workflow scenario.**

Use a separate load generator if the application host cannot generate load without contaminating results. Run warm-up, ramp, steady period, submission burst, report contention, and soak phases from the existing scalability design. Passing Redis gates does not override system-wide CPU, RAM, PostgreSQL, Hikari, error-rate, or latency gates.

- [ ] **Step 7: Tune only from measured evidence and rerun.**

Adjust state/cache pool sizes, TTLs, L1 weight, and the approved 16 GiB/32 GiB memory profiles only when raw evidence identifies the bottleneck. Do not increase Redis beyond a budget that leaves the OS or PostgreSQL swapping. Preserve before/after artifacts for every accepted adjustment.

- [ ] **Step 8: Write the implementation report and FE disposition.**

The report lists files changed, exact Redis versions/digests, tests, Native result, workload provenance, before/after measurements, resource use, remaining limits, rollback, and any deferred Redis 8.8 feature. State explicitly that API payloads did not change. Create the frontend migration file only if a separately approved contract change actually occurred.

- [ ] **Step 9: Run the final verification suite.**

```powershell
pwsh -File scripts/verify-docker-config.ps1
./mvnw.cmd test
./mvnw.cmd -Pnative -DskipTests native:compile
docker compose --env-file ops/docker/.env.dev -f ops/docker/compose.yml -f ops/docker/compose.dev.yml ps
```

Expected: configuration check, complete JVM suite, Native compile/smoke, both Redis healthchecks, and PostgreSQL health all pass. Review Surefire reports for zero failures/errors, not only Maven exit text.

- [ ] **Step 10: Commit load assets and verified report.**

```powershell
git add load ops/docker/redis/capture-evidence.ps1 ops/docker/redis/capture-evidence.sh docs/2026-07-16-redis-8-8-implementation-report.md
git commit -m "test: validate Redis 8.8 cache scalability"
```

## Plan self-review

### Spec coverage

- Exact Redis 8.8 pin, state/cache split, 16 GiB and 32 GiB budgets: Tasks 1-2.
- Destructive local-only migration and clean rollback: Task 2.
- Independent Spring clients and Native-compatible configuration: Tasks 3 and 9.
- Atomic `INCREX`, exact fixed-window expiry, Lua rollback, and local emergency behavior: Task 4.
- Typed DTO-only serialization, deterministic tenant keys, 1 MiB limit, and RuntimeHints: Task 5.
- True L1/L2 population, weighted L1, single-flight, circuit breaking, and PostgreSQL fallback: Task 6.
- Exact/generation/post-commit/cross-replica invalidation: Task 7.
- All approved business cache families and broad invalidation removal: Task 8.
- ACL, bounded metrics, health semantics, Redis memory/AOF alerts, and Native smoke: Task 9.
- Cold/warm/failure/5,000-user proof, evidence, report, and conditional FE guide: Task 10.
- PostgreSQL remains authoritative and Redis Streams/Search/JSON/TimeSeries/Array/Sentinel remain out of scope: preserved throughout Tasks 1-10.

### Placeholder and consistency scan

- The plan contains no `TBD`, `TODO`, “implement later,” or unbounded “write tests” step.
- State Redis uses port 6379, AOF every second, 768 MiB maxmemory, 1.25 GiB container limit, and `noeviction` in the 16 GiB profile.
- Cache Redis uses port 6380 on the development host, no persistence, 2 GiB maxmemory, 2.5 GiB container limit, and `allkeys-lfu`.
- `INCREX` uses `PXAT windowEndEpochMillis` and `ENX`; the fallback must match that behavior.
- `redisConnectionFactory` always means the primary state connection; `cacheRedisConnectionFactory` always means L2.
- Business cache entries are typed DTOs, tenant-aware, TTL-bound, and capped at 1 MiB.
- Cache generation and invalidation coordination live in `redis-state`; L2 data lives only in `redis-cache`.
- No task authorizes an API payload change. A frontend migration document is conditional on separate approval.
