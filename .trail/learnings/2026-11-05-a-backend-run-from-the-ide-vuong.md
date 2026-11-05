---
title: A backend run from the IDE breaks when Maven rebuilds the same output folder
author: Vuong
date: 2026-11-05
branch: claude/fix-audit-findings
cost: { minutes: 18, tokens: 55620 }
id: l-2b6c6471
scope: repo
status: active
src: agent
touches:
  - ops/docker/compose.monitor.yml
  - ops/docker/compose.native.yml
  - ops/docker/deploy.sh
  - ".github/workflows/uptime.yml"
---

## Decided
When freshly issued tokens get 401 on every request (login and refresh still return 200), first check whether the running backend was started from the repo's own compiled-classes folder and whether commits were built into that folder after it started. Restart it cleanly before debugging auth code.

## Tried and ruled out
- The DB session rows were fine: correct tenant, refresh kept extending the access expiry, and the tenant-lookup SQL function resolves them. The DB timezone is UTC and the expiry columns are timestamptz, so a clock or timezone mismatch was ruled out.
- The principal is not cached anywhere, so a stale cache is not the cause.

## Watch out
- The IDE debug run loads classes lazily from the same folder that `mvnw test/verify` writes to. Building while it runs leaves the JVM with old and new auth classes mixed together. `mvnw clean` removes that folder completely.
- If `mvnw verify` reports "Unresolved compilation problems" with a package that "does not match the expected package", the cause is old classes the IDE compiler left in the test-classes output. Run `clean verify` instead of chasing them.

In the code:
- On 2026-11-05 the backend (pid 7588) ran with `-cp F:\ERG\erg-spring\target\classes` under `-Xrunjdwp`.
- Auth path: `ErgAuthenticationFilter.principalFrom` -> `ErgPrincipalService.resolveTenantId` -> SQL function `public.erg_resolve_session_tenant` (V100).
