# ERG load validation

Run the steady and submission scenarios only against a deterministic fixture loaded from
`load/fixtures/manifest.yaml`. Supply `BASE_URL` and a test-only `ACCESS_TOKEN`.

The 5,000-VU scenario is an acceptance workload, not a claim that the 16 GB pilot host can
pass it. Record p50/p95/p99, completed RPS, rejected requests, Hikari pending borrowers,
PostgreSQL CPU/I/O/WAL/locks, Redis memory, RSS, GC and swap for every run.

## Disposable JVM/Native benchmark platform

The isolated `erg-benchmark` Compose project includes PostgreSQL, separate Redis state/cache,
Prometheus, Grafana, cAdvisor, PostgreSQL/Redis exporters and k6. It never shares project labels,
networks or volumes with `erg-dev`.

```powershell
# Validate orchestration without changing Docker state.
powershell -NoProfile -File load/scripts/benchmark-lib.Tests.ps1
powershell -NoProfile -File load/scripts/benchmark.ps1 -DryRun

# Build comparable images from the current working-tree snapshot.
docker compose --project-name erg-benchmark --env-file ops/docker/.env.benchmark.example `
  -f ops/docker/compose.benchmark.yml --profile jvm build backend-jvm
docker compose --project-name erg-benchmark --env-file ops/docker/.env.benchmark.example `
  -f ops/docker/compose.benchmark.yml --profile native build backend-native

# Correctness smoke (default) or the complete measured load profile.
powershell -NoProfile -File load/scripts/benchmark.ps1 -Runtime All
powershell -NoProfile -File load/scripts/benchmark.ps1 -Runtime All -FullLoad
```

Smoke runs validate authentication, routing and response correctness; cold-start latency is not an
acceptance measurement. A full run warms each REST and GraphQL business path at 100 VUs for five
minutes before measured receipts, so JVM JIT compilation does not pollute the first p95/p99 sample.
The versioned p50/p95/p99/error thresholds remain enabled for `-FullLoad`.

Before load begins, the harness proves the Prometheus backend/exporter targets, cAdvisor container
series, Hikari metrics, and Grafana datasource/dashboard are queryable. cAdvisor v0.57.0 is required
for Docker Engine 29 with the containerd image store; older 0.52.x images can report healthy while
silently omitting current containers. Raw summaries, validated monitoring receipts, image metadata,
`comparison.json`, and `comparison.md` are retained under `load/results/<run-id>/`. All Compose
resources are removed in `finally` unless `-KeepInfrastructure` is explicitly selected for diagnostics.
The ladder is fail-fast per runtime: a failed step is retained and higher steps for that runtime are
not launched. JVM and Native outcomes remain independent.

Additional isolated gates are available for `webhook-burst.js`, `outbox-dispatch.js`
and `modulith-events.js`. The latter two are operational smoke/command probes;
they do not manufacture worker backlog or prove throughput. Use a seeded staging
environment and attach the resulting metrics before marking a performance gate green.

Redis 8.8 profiles use `load/k6/redis-8-8-cache.js`:

```powershell
$env:CACHE_MODE='cold' # warm, cache-outage, state-outage, or 5k
$env:BASE_URL='http://127.0.0.1:1606'
$env:ACCESS_TOKEN='<test-only-token>'
k6 run --out json=load/results/redis-8-8-cold.json load/k6/redis-8-8-cache.js
```

Flush only `redis-cache` before a cold run. Never flush `redis-state` or PostgreSQL. Stop one
Redis role at a time for degradation profiles and restore it before the next profile. The `5k`
profile requires a separate Vietnam-LAN/region load generator, 10-minute warmup, 30-minute
steady phase, and collection of host/container/PostgreSQL/Hikari metrics. A local smoke run is
not capacity acceptance.
