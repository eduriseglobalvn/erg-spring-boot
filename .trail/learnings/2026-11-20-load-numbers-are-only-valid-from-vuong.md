---
title: Load numbers are only valid from a benchmark stack set up like production and with students picked at random
author: Vuong
date: 2026-11-20
branch: fix/student-import-identity
cost: { minutes: 1, tokens: 3503 }
id: l-2facfd09
scope: repo
status: active
replaces: l-6ad30caa
src: agent
touches:
  - ops/docker/compose.benchmark.yml
  - ops/docker/compose.native.yml
  - load/fixtures/seed-postgres-scale.sql
---

## Decided
On the real server (i5-7500, 4 cores, 16 GB), image 5aa98bcc, the student assignment read path held 7,500 students online (750 req/s, p95 24 ms, p99 250 ms, no errors) and failed at 10,000 (1,000 req/s, p95 9 s) with the load generator on the same machine. Two things had to be fixed before the numbers meant anything:
- The benchmark stack switches off Spring Modulith module observability like production does. With it on, every call across a module boundary reads the Spring Environment under the JVM's System-properties lock; at 500 req/s about 25 threads were permanently blocked there, while holding database connections, so it looked like pool exhaustion.
- The load script picks a random student per request. Taking students in fixed turns makes every 30-second cache entry expire in the same pass, which produces a 12-15 s storm of cache misses every 36 s that real students do not cause.

## Tried and ruled out
- A bigger connection pool or a faster Postgres: the pool only fills because threads wait on the lock while holding a connection, and Postgres has nothing to do meanwhile.
- Turning virtual threads off: same tail latency.
- Quoting the July figure (265 req/s) or a ladder run with observability on: both measure a stack that production does not run.

## Watch out
- The seed fixture drifts from the schema (subject tables dropped in V163, roles merged in V160) and needs statement_timeout lifted on a 4-core host; seeding takes about 19 minutes there.
- A profile needs no JDK tools in the image: start the JVM with -XX:StartFlightRecording and read the file with `jfr view contention-by-site`.
- Production keeps the student assignment list only in memory (64 MiB, 30 s); the benchmark also keeps it in Redis for 120 s, so production misses to Postgres about four times as often as the benchmark. Not measured.
- Only this one cached read was measured. Exam taking (draft saves, submit) and a login storm were not.

In the code: ops/docker/compose.benchmark.yml (SPRING_AUTOCONFIGURE_EXCLUDE), ops/docker/compose.native.yml, load/k6/erg-ccu-ladder.js, load/fixtures/seed-postgres-scale.sql, load/results/server-20261120T140154Z/, config/LmsCacheDescriptorContributor.java, ERG_REDIS_CACHE_L2_NAMES.
