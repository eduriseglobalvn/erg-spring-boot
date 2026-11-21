---
title: "A write that must not fail a student's submit has to stay out of Hibernate and catch its own errors"
author: Vuong
date: 2026-11-21
branch: fix/student-import-identity
cost: { minutes: 27, tokens: 215692 }
id: l-20624193
scope: repo
status: active
src: agent
touches:
  - src/main/java/com/erg/ergspringboot/platform/security/PrincipalCache.java
  - src/main/java/com/erg/ergspringboot/lcms/quiz/authoring/TeacherQuizOwnership.java
  - src/test/java/com/erg/ergspringboot/lcms/quiz/authoring/TeacherQuizOwnershipTest.java
---

## Decided
The student_progress projection is written with plain JDBC inside a JDBC savepoint, and the method catches its own failures. It runs in the attempt's transaction (one connection), but a failure there no longer rolls back the graded submit.

## Tried and ruled out
- Wrapping the Hibernate write in a Spring PROPAGATION_NESTED template: the entity write only fails at commit-time flush (outside the savepoint), and any exception crossing a participating @Transactional proxy marks the Hibernate transaction rollback-only, which rollbackToSavepoint does not clear. The submit would still be rolled back.
- Running it after commit in a new transaction (the old way): it takes a second pooled connection while the first is still held, which starves the pool when thousands submit together.

## Watch out
- Never let an exception leave a @Transactional method you intend to "swallow" in the caller; catch inside it.
- assignment_id is nullable and the unique index treats nulls as distinct, so the upsert is update-then-insert, not ON CONFLICT.

In the code: QuizAttemptGradingService.projectProgress / upsertProgressOnSavepoint, AttemptProgressProjector.
