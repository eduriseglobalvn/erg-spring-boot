---
title: "Abandoned attempts are closed by a background sweep, and attempt changes invalidate only the scopes they touch"
author: Vuong
date: 2026-11-04
branch: claude/fix-audit-findings
cost: { minutes: 1, tokens: 4676 }
id: l-9ce1e210
scope: repo
status: active
replaces: l-265b0750
src: agent
touches:
  - ops/docker/compose.benchmark.yml
  - load/k6/redis-8-8-cache.js
---

## Decided
- A periodic sweep closes overdue in-progress attempts. It runs one transaction per attempt under the cluster job lock and reuses the start path's auto-close.
- A start, submit, grade or auto-close bumps a generation per (tenant, cache, scope) instead of the whole tenant. The scopes are: homework workspace by the assigning teacher, progress and report by assignment, analytics by quiz and quiz version, dashboard by the viewed student's user id. An unknown scope falls back to tenant-wide.
- Measured on the local benchmark: 20 VUs reading while the 5,000-attempt burst ran. Hit rate during the burst went from 0% to 95% for homework and from 29% to 81% for the dashboard, and successful reads went from 50% to 92%.

## Tried and ruled out
- Per-class scopes: the dashboard and homework keys carry no class id.
- Putting the sweep query in the main attempt repository: that repository is at its class-size ceiling.

## Watch out
- Scope functions read fixed `@Cacheable` key positions, and a contract test pins them.
- The local generation counter must never evict, because that resurrects stale L2 keys. The Redis memo may evict.

In the code:
- `ErgTieredCache.redisKey` / `clearLocal(tenant, scope)`, `LmsReadCacheInvalidator.evictAttemptAfterCommit`, `LmsCacheDescriptorContributor.keyPart`, `LmsCacheScopeKeyContractTest`
- `AttemptExpirySweepJob`, `AttemptExpiryRepository.findExpiredCandidates`
- Benchmark: `ops/docker/compose.benchmark.yml`, `load/k6/redis-8-8-cache.js` + `erg-5k-submission-burst.js`, `erg_cache_operations_total`. In Git Bash, set `MSYS_NO_PATHCONV=1`.
