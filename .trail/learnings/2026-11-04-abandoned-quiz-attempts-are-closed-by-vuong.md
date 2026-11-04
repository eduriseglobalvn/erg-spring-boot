---
title: Abandoned quiz attempts are closed by a background sweep; cache eviction stays per tenant
author: Vuong
date: 2026-11-04
branch: claude/fix-audit-findings
cost: { minutes: 19, tokens: 49208 }
id: l-265b0750
scope: repo
status: active
src: agent
touches:
  - src/main/java/com/erg/ergspringboot/lms/internal/attempt/AttemptExpirySweepJob.java
  - src/main/java/com/erg/ergspringboot/lms/persistence/AttemptExpiryRepository.java
  - src/main/java/com/erg/ergspringboot/platform/cache/ErgTieredCache.java
---

## Decided
- Overdue in-progress attempts are closed by a periodic sweep, not only when the student starts again. The sweep reuses the same auto-close path as start (grade what the server holds, mark late, project progress after commit), one transaction per attempt, under the cluster job lock.
- Candidates come from the live assignment deadline (due date, or start + duration) in SQL; the frozen snapshot deadline and the grace are re-checked in Java before closing.
- Cache eviction stays per tenant. Every allEntries evict already bumps only the current tenant's generation and clears that tenant's L1 after commit; nothing clears all tenants.

## Tried and ruled out
- Adding the sweep query to the main attempt repository: rejected, it sits at its reviewed class-size ceiling; the query lives in a small separate repository.
- Per-class cache eviction: deferred. It needs (tenant, class) generations in the cache infrastructure; only worth it if a k6 benchmark shows a poor hit rate.

## Watch out
- An attempt whose snapshot deadline is later than the live assignment deadline is re-checked every run without closing; fine while rare.
- When writing Java with regex escapes through a shell heredoc here, backslashes get collapsed; use the editor tools for such files.

In the code:
- AttemptExpirySweepJob (erg.lms.attempt-expiry-sweep.enabled / batch-size / fixed-delay-ms, default 5 min), LmsAttemptUseCase.closeIfExpired, AttemptExpiryRepository.findExpiredCandidates (native, make_interval).
- Proof against Postgres: LmsAttemptFlowIntegrationTest.expirySweepFindsAndClosesAnAttemptPastItsDuration.
