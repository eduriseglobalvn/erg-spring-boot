---
title: "Class average counts only each student's latest hand-in"
author: Vuong
date: 2026-11-13
branch: fix/class-average-latest-attempt
id: l-746577d2
scope: repo
status: active
src: agent
touches:
  - "src/main/java/com/erg/ergspringboot/lms/persistence/AttemptRepository.java#summarizeClassWorkspaceScores"
  - src/test/java/com/erg/ergspringboot/lms/LmsAttemptFlowIntegrationTest.java
---

Decided: the class workspace score summary (AttemptRepository.summarizeClassWorkspaceScores) uses LatestHandedInAttempt.OF_ATTEMPT, same as the student dashboard, so a student who hands in 40, 60, 90 counts once at 90 in the class average (before: 63). ClassScoreSummary.completedAttemptCount kept its GraphQL name but now means handed-in student x assignment pairs, not raw attempts; the FE maps it to completedAssignments, which matches the new meaning. Watch out: lmsClassWorkspace has no scope in LmsReadCacheInvalidator.evictAttemptAfterCommit, so every submit clears it tenant-wide (already covered by LmsReadCacheInvalidatorScopeTest); the FE (lms-classroom-api.ts mapper) copies the class-wide averagePercent and completedAttemptCount into every student row, which is a separate bug.
