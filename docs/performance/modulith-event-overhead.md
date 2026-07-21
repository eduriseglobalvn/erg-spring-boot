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

The repository contains the k6 scenario, JDBC registry configuration,
recovery tests and full Maven verification. A production-equivalent numeric
benchmark result is not present in this checkout, so the performance gate is
explicitly open and must be closed by CI/release infrastructure before a
production rollout.
