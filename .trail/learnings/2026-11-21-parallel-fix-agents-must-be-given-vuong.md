---
title: "Parallel fix agents must be given consecutive migration numbers, and a lazy column on a hot entity is asked in SQL"
author: Vuong
date: 2026-11-21
branch: fix/student-import-identity
cost: { minutes: 30, tokens: 139966 }
id: l-2452169e
scope: repo
status: active
src: agent
touches:
  - ops/docker/nginx/native.conf
  - src/test/java/com/erg/ergspringboot/platform/security/NginxSecurityConfigContractTest.java
  - src/main/java/com/erg/ergspringboot/calendar/CalendarCacheDescriptorContributor.java
  - src/main/java/com/erg/ergspringboot/config/CacheConfig.java
  - src/main/java/com/erg/ergspringboot/elearning/internal/engagement/StudentTierReader.java
  - src/main/java/com/erg/ergspringboot/lms/persistence/AssignmentRecipientScope.java
  - src/test/java/com/erg/ergspringboot/lcms/internal/readmodel/LcmsWorkspaceReadServiceTest.java
  - src/test/java/com/erg/ergspringboot/calendar/CalendarPublishedScenarioTest.java
  - src/test/java/com/erg/ergspringboot/timekeeping/internal/admin/AdminITBase.java
  - src/test/java/com/erg/ergspringboot/lms/LmsAssignmentRecipientParityTest.java
---

## Decided
- Flyway versions here must be gap-free (FlywayMigrationAppendOnlyContractTest). When several agents add migrations in parallel, reserve ranges for them, then renumber to consecutive versions before the first verify.
- Assignment.student_ids is now a lazy attribute. Code must not call getStudentIds() on a read path: ask the database (AssignmentRecipientRepository.legacySnapshotNames / countLegacyStudentIds).

## Tried and ruled out
- Reading the lazy snapshot in LmsAssignmentAccessPolicy.isRecipient: it added a 10th statement to POST attempts (budget 9) and can fail on a detached entity.
- Stopping the student_ids write: three SQL readers outside lms still use it (StudentEngagementService, StudentQuestionThreadService, JdbcStudentDashboardAnnouncementReadPort).

## Watch out
- After renaming a migration, delete the old copy from target/classes/db/migration/postgres, or Flyway tests fail with "more than one migration with version N".
- A new Spring Data repository must live in a module persistence package and be counted in JpaRegistrationCatalog.
- Integration tests that change tk_shifts with JDBC must clear the timekeepingSchoolSetup cache (AdminITBase does).
- A Java text block drops trailing spaces: write select\s before concatenating a column list.

In the code: src/main/resources/db/migration/postgres/V187..V196, lms/LmsAssignmentAccessPolicy.java, lms/persistence/AssignmentRecipientScope.java, config/persistence/JpaRegistrationCatalog.java
