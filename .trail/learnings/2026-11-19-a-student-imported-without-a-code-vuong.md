---
title: "A student imported without a code gets one issued by the backend, so the import result is matched to rows by position"
author: Vuong
date: 2026-11-19
branch: fix/student-import-identity
cost: { minutes: 5, tokens: 71376 }
id: l-8da08756
scope: repo
status: active
src: agent
touches:
  - src/main/java/com/erg/ergspringboot/lms/internal/core/StudentIdentity.java
---

## Decided
The frontend no longer invents student codes (it used `${schoolId}-${Date.now()}-${index}` and the roster parser used `${class}-${STT}`). A blank code goes to the backend, which issues HS-XXXXXX and recognises a returning child by name + birthday (or name + class when either side has no birthday). The results from addStudents come back in row order, and logins are paired with rows by index, not by code.

## Tried and ruled out
Codes from the row number: they change when the sheet is sorted, and a re-import then created every child a second time (Vân Đồn, Nov 2026: 1478 students, 823 random codes, class counts wrong).

## Watch out
Two different children with the same name and birthday in one school need a real code in the sheet, otherwise the second is taken as the first.

In the code: hooks/use-school-management.ts addStudents; components/setup/school-students-step.tsx importStudents; backend StudentIdentity.java (erg-spring).
