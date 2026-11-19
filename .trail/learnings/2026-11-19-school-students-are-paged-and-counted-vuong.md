---
title: "School students are paged and counted in SQL, never loaded whole into the school workspace"
author: Vuong
date: 2026-11-19
branch: fix/school-workspace-students
id: l-8fb45085
scope: repo
status: active
src: agent
touches:
  - src/main/java/com/erg/ergspringboot/common/response/lms/LmsOperationalResponses.java
  - src/main/java/com/erg/ergspringboot/common/response/school/SchoolOperationsResponses.java
  - src/main/java/com/erg/ergspringboot/controller/lcms/graphql/LcmsSchoolOperationsGraphQlController.java
  - src/main/java/com/erg/ergspringboot/lms/internal/core/LmsOperationalCommandService.java
  - src/main/java/com/erg/ergspringboot/lms/persistence/StudentRepository.java
  - src/main/java/com/erg/ergspringboot/school/SchoolQueryOperations.java
  - src/main/java/com/erg/ergspringboot/school/internal/read/SchoolOperationsReadService.java
  - src/main/resources/graphql/modules/00-shell.graphqls
  - src/main/resources/graphql/modules/10-school-quiz.graphqls
  - src/main/resources/graphql/schema.graphqls
---

## Decided
The school workspace (schoolOperations) no longer carries the student list. Students come from
`schoolStudents(input)`: filtered (search, grade, className, status) and paged in SQL, size capped
at 200, with totals and stats (total, atRisk, studying, profileComplete, assigned) counted by SQL
aggregates. A 1.5k-student school (THCS Vân Đồn) did not load because the workspace capped the roster.

## Tried and ruled out
Raising the roster cap to 10,000 (first version of PR #35). The owner rejected it: a school of
100,000 would break again, and every detail open shipped the full roster to the browser.

## Watch out
- Student import (REST, with accounts) returns `replayedStudentIds`: rows already on the roster,
  returned untouched. The frontend uses it to skip showing passwords for them, instead of diffing
  against a full roster it no longer has.
- Frontend and backend must deploy together: the frontend reads `summary.students` and
  `schoolStudents`, the old frontend reads `students` from the workspace.

In the code: `StudentRepository` school student queries + `SchoolStudentStatsProjection`,
`SchoolOperationsReadService`, `LmsOperationalCommandService.importStudents`,
`10-school-quiz.graphqls`; frontend `use-school-students.ts`, `getSchoolStudents`.
