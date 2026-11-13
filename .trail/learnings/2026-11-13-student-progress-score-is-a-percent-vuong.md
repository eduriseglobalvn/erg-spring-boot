---
title: student_progress.score is a percent; submitted means handed in
author: Vuong
date: 2026-11-13
branch: fix/student-progress-score-source
id: l-879e5249
scope: repo
status: active
src: agent
touches:
  - src/main/java/com/erg/ergspringboot/lms/internal/attempt/AttemptProgressProjector.java
  - src/main/java/com/erg/ergspringboot/lcms/persistence/quiz/runtime/StudentProgressRepository.java
  - src/main/java/com/erg/ergspringboot/reporting/quiz/QuizReportService.java
---

## Decided
- student_progress.score is the latest hand-in's percent (0-100). AttemptProgressProjector used to write Attempt.score (raw points), so a 9/10 showed as 9 on the teacher progress page and averagePercent mixed points and percents. V145 backfills it from lms_attempts.
- The projection is written on every hand-in, manual grade and auto-close, and LatestHandedInAttempt orders by updated_at, so the last write already is the latest hand-in; no extra lookup is needed.
- submittedCount of the assignment report counts every LmsStatePolicy.completedAttemptStates() state; needs_review is a subset of it, so notStarted = total - submitted - inProgress. Before, a graded hand-in was counted as not started. The literal list lives in the JPQL and in the V69/V145 trigger; AttemptStateSqlParityTest checks the JPQL against the policy.

## Watch out
- pgjdbc binds OffsetDateTime untyped, so ':p is null' on a timestamp fails with 'could not determine data type of parameter'. Use cast(:p as OffsetDateTime) is null. The filtered assignment report failed this way for every call before.
- student_progress has student_user_id (login id, or the Student id when there is none), not student_id; join lms_students to reach lms_attempts.student_id.
- Projection tests cannot be @Transactional: projectProgress is REQUIRES_NEW and FK-checks the seed rows, which would deadlock. StudentProgressLatestScoreIntegrationTest commits its seed and deletes it after.
