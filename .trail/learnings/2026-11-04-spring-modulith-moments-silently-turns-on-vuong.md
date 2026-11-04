---
title: "Spring Modulith Moments silently turns on every scheduled job, so it is disabled"
author: Vuong
date: 2026-11-04
branch: claude/fix-audit-findings
cost: { minutes: 47, tokens: 92276 }
id: l-0595afff
scope: repo
status: active
src: agent
touches:
  - src/main/resources/application.yml
---

## Decided
- `spring.modulith.moments.enabled: false`. Moments comes in through the Modulith core starter, nothing here listens to its time events, and its auto-configuration carries `@EnableScheduling`. While it was on, every `@Scheduled` worker ran in every environment and the `erg.scheduling.enabled` switch did nothing.
- A guard test asserts the test context has no scheduled-annotation processor.

## Tried and ruled out
- Blaming the new attempt-expiry sweep's cross-tenant query: on a shared Postgres it still passed. The real failure was a query-count test seeing 10 statements instead of 9, because a background worker ran during the measurement. Hibernate statistics count statements from every thread.
- Reproducing with SQL debug logging: the slower timing hid the race. Alone, or 6 runs in a row, it never failed.

## Watch out
- CI uses one shared Postgres service; local runs get a fresh Testcontainer. To reproduce CI, start `postgres:17`, point `ERG_TEST_POSTGRES_URL` at it, and pass `-DargLine=-Duser.timezone=UTC`. A Windows JVM sends `Asia/Saigon`, which Postgres 17 rejects.

In the code:
- `src/main/resources/application.yml` `spring.modulith.moments.enabled`
- `config/SchedulingConfig` (`erg.scheduling.enabled`, off in the test profile)
- `LmsTeacherHomeworkWorkspaceQueryCountIntegrationTest.backgroundWorkersAreOffSoTheyCannotSkewStatementCounts`
