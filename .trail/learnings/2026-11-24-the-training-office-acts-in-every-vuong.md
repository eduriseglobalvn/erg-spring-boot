---
title: "The training office acts in every school and class of its organisation, in the teacher portal too, whether or not it teaches there"
author: Vuong
date: 2026-11-24
branch: fix/teacher-portal-own-schools
cost: { minutes: 27, tokens: 180038 }
id: l-0603ef2e
scope: repo
status: active
replaces: l-142fc729
src: agent
touches:
  - src/main/java/com/erg/ergspringboot/controller/sessions/SessionController.java
  - src/test/java/com/erg/ergspringboot/auth/SessionServiceTest.java
  - src/main/java/com/erg/ergspringboot/platform/security/ErgPrincipalService.java
  - src/test/java/com/erg/ergspringboot/platform/security/ErgPrincipalServiceTest.java
---

## Decided
Owner decision, 24/11/2026, replacing the 16/11 rule that the training office (Phòng đào tạo, role pdt_operator) behaves like a plain teacher in the teacher portal. A training-office account manages many schools and supports their teachers, so in the teacher portal it sees every school and class of its organisation and can do everything a teacher of that class can, with or without a published lesson there. Example from the owner: a teacher forgot to assign homework, the training office assigns it for them from the teacher portal.

## Tried and ruled out
Narrowing the teacher-portal school list to schools where the training office has a published lesson (a teacher view of the session bootstrap): built and reverted the same day, the owner wants the opposite.

## Watch out
Before this, the school picker listed every school (unit scope "*") but saving work failed with "You can only access LMS data in managed centers/classes", because the class scope still held only taught classes. Unit scope and class scope must stay in step: whoever has unit "*" now also gets class "*". Every query stays tenant-filtered; "*" never crosses tenants for this role. Director and Operations still have no unit scope.

In the code (backend repo erg-spring):
- ErgPrincipalService.fromSession: managedClassIds = "*" when managedEducationUnitIds contains "*"
- ErgPrincipalService.managedEducationUnitIds returns "*" for pdt_operator
- LmsAssignmentAccessPolicy.ensureClassScope is the check that refused the save
