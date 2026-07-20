# ERG load validation

Run the steady and submission scenarios only against a deterministic fixture loaded from
`load/fixtures/manifest.yaml`. Supply `BASE_URL` and a test-only `ACCESS_TOKEN`.

The 5,000-VU scenario is an acceptance workload, not a claim that the 16 GB pilot host can
pass it. Record p50/p95/p99, completed RPS, rejected requests, Hikari pending borrowers,
PostgreSQL CPU/I/O/WAL/locks, Redis memory, RSS, GC and swap for every run.

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
