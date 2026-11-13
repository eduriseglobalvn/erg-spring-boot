---
title: Why POST /lms/attempts returned 409 and 500 on concurrent starts
author: Vuong
date: 2026-11-13
branch: fix/attempt-start-409
id: l-c37409b1
scope: repo
status: active
src: agent
touches:
  - "src/main/java/com/erg/ergspringboot/lms/internal/attempt/LmsAttemptUseCase.java#start"
  - "src/main/java/com/erg/ergspringboot/lms/internal/core/LmsAttemptService.java#lockStart"
---

## Cause
Without a lock, two parallel calls to `LmsAttemptUseCase.start` for the same learner, assignment and quiz race. `LmsAttemptStartConflictIntegrationTest` reproduces each case on Postgres:
- Different idempotency keys, no open attempt (two tabs or devices): the loser hits `uq_lms_attempts_single_in_progress`, so 409 "an attempt is already in progress for this quiz".
- Different keys, one open attempt: both resumes save the same `record_version`, so the loser gets 409 `STALE_WRITE`.
- Same key (double click, Enter key repeat): the second insert into `lms_sync_operations` hits the unique key, Postgres aborts the transaction, and the follow-up select fails, so a 500 (`UncategorizedSQLException`), never a 409.
The intended 409s on this endpoint are "attempt limit reached" (`ensureLimit`) and `IDEMPOTENCY_CONFLICT` when one key is reused with another payload.

## Decided
- `start` takes a transaction-scoped advisory lock per (tenant, assignment, quiz, student) via `LmsAttemptService.lockStart` before any lookup. The second caller waits, then resumes or replays what the first committed. The catch on the unique index stays as a backstop for other writers.

## Tried and ruled out
- Catching the unique violation and re-reading the open attempt: Postgres aborts the transaction on the violation, so the re-read cannot run without a savepoint.
- Blaming the FE alone: one player session reuses one start key, so a double click can only cause the 500 case, not a 409.

## Watch out
- `LmsAttemptUseCase` is near the 400-line `ClassSizeRatchetTest` limit; that is why the lock lives in `LmsAttemptService`.
- Draft saves and the expiry sweep do not take the lock, so a resume racing them can still return `STALE_WRITE`.
