# Spring Modulith event overhead benchmark record

## Scope

This record defines the repeatable comparison for a command that publishes a
business event:

1. synchronous facade with event publication disabled;
2. in-process `@ApplicationModuleListener` without durable publication; and
3. JDBC Event Publication Registry enabled.

The workload is `load/k6/modulith-events.js`. Run it against the same seeded
database, image, instance resources and connection-pool settings. Capture the
commit SHA, image digest, fixture checksum and raw k6 JSON for every run.

## Required measurements

- request p50/p95/p99 and error rate;
- event rows per transaction and publication lag;
- transaction commit latency, PostgreSQL WAL/IO and lock wait;
- listener throughput, retry count and incomplete/failed publications;
- CPU, heap/RSS and queue/connection-pool wait.

## Release gate

The durable registry may not increase command p95 by more than 10% on the
accepted workload. If the gate fails, reduce payload/listener work or move a
non-critical consumer to a projection batch; do not increase thread pools or
unbounded queues to hide the regression.

## Current evidence

The deterministic fixture now supplies 20,000 independent DRAFT batches and
events. The k6 scenario pins 128 preallocated VUs and assigns a disjoint batch
range to each VU, so every accepted iteration executes a real publish instead
of repeatedly measuring the already-published error path.

Local preflight on 2026-07-21, fixture SHA-256
`239D2A9E12036E614DC883151B86565896B7E14A131E880E994A15ACD54089A5`:

| Runtime | Requests | Error rate | p50 | p90 | p95 |
|---|---:|---:|---:|---:|---:|
| JVM | 6,000 | 0.00% | 50.01 ms | 131.77 ms | 319.88 ms |
| Native image `sha256:213f94f379607088c1ca088c89ede629c2c5a46f3b599158b02178c28b928327` | 6,001 | 0.00% | 26.01 ms | 38.73 ms | 43.68 ms |

Both runs used the JDBC Event Publication Registry and the same PostgreSQL/
Redis fixture. The registry had zero incomplete publications after the native
run. This closes native event-path compatibility and the local workload
preflight, but it does **not** calculate registry overhead because the required
no-registry and non-durable baselines were not run. The <=10% release gate
therefore remains explicitly open for CI/release infrastructure.
