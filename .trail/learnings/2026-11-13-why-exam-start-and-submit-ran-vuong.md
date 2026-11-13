---
title: Why exam start and submit ran 13 and 37 statements
author: Vuong
date: 2026-11-13
branch: perf/exam-hot-path
id: l-79f577e0
scope: repo
status: active
src: agent
touches:
  - src/main/java/com/erg/ergspringboot/lms/internal/quiz/runtime/QuizAttemptGradingService.java
  - src/main/java/com/erg/ergspringboot/lcms/persistence/quiz/runtime/AttemptAnswerRow.java
  - src/main/java/com/erg/ergspringboot/lms/internal/attempt/AttemptFactory.java
  - src/main/java/com/erg/ergspringboot/lms/internal/attempt/AttemptCompletionEffects.java
---

## Cause
`LmsExamHotPathQueryBudgetIntegrationTest` counts Hibernate statements per exam call (20-question version). Before: home 3, package 7, start 13, draft 4, submit 37. Where they came from:
- Submit: `AttemptAnswerRow` and `AttemptEvent` have assigned ids and no `@Version`, so Spring Data `save`/`saveAll` merged them: one SELECT per new answer row (20) before the batched insert. `AttemptEventRecorder.record` also ran `existsById` for a server-generated id.
- Submit: `QuizAttemptGradingService` loaded the version's questions twice (`runtimeQuestionIds`, then `gradeSubmission`) and the whole `QuizVersion` (all JSON blobs) only to read `passPercent`.
- Start: `AttemptFactory` put `submitGraceSeconds` into the jsonb policy snapshot as a Long. Hibernate's JSON snapshot reads it back as an Integer, so the attempt was dirty on every flush: two extra UPDATEs of `lms_attempts` and two `record_version` bumps per start.
- Start and submit: `AttemptCompletionEffects.evictReads` looked the learner up by login id using the attempt's Student.id. It found nobody, so every start and submit cleared `studentDashboard` for the whole tenant.
- Start: `packageEntityForVersion` loaded the full version row only to check its quiz id.

## Decided
- `AttemptAnswerRow` and `AttemptEvent` implement `Persistable` with a transient flag set by `@PostLoad`/`@PostPersist`, so new rows are persisted without a SELECT.
- Published versions are immutable, so the grading service caches each version's questions and pass mark (Caffeine, 256 entries, key = caller tenant + version id).
- Start passes the caller's login id to the eviction; other callers resolve Student.id to the login id. `packageEntityForVersion` reads the package by version id and checks its quiz id.
- After: start 9, submit 13 (11 once the version is cached), others unchanged.

## Watch out
- Any Long, OffsetDateTime or other non-JSON-native value put into a jsonb `Map<String, Object>` makes the entity dirty on every flush. Check the UPDATE count in the SQL log when adding such a field.
- The k6 log line `POST /api/v1/lms/attempts triggered 15` was start, not submit.
- `LmsLearningEventConsumer` fails on submit in tests: notifications use Student.id as `created_by`, which is a foreign key to `users`.
