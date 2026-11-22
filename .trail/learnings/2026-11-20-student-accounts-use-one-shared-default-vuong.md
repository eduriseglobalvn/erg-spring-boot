---
title: "Student accounts use one shared default password, and a teacher reset always returns to it"
author: Vuong
date: 2026-11-20
branch: fix/student-import-identity
cost: { minutes: 1, tokens: 0 }
id: l-913db0c8
scope: repo
status: active
src: agent
touches:
  - src/main/java/com/erg/ergspringboot/platform/security/ErgAuthProperties.java
  - src/main/java/com/erg/ergspringboot/lms/internal/core/StudentCredentialResetService.java
  - src/main/java/com/erg/ergspringboot/lms/internal/core/StudentAccountProvisioner.java
---

## Decided
The owner set the rule on 21/11/2026: a new student account gets the default password, and when a teacher resets a student's password it always goes back to that default, even if the student had changed it. This replaces the earlier "password = username" behaviour and the random reset password. The owner knows a shared password is weak and chose it for classroom practicality; do not "harden" it without asking.

## Tried and ruled out
Random reset passwords: teachers could not hand them out in class. Password equal to username: dropped by the owner.

## Watch out
An import row that carries a username but no password now gets an account with the default password (before, it needed a password). mustChangePassword stays false on purpose. A password that equals the username (older frontends send that) is treated as "no password given".
Caches added in the same pass evict L1 immediately inside a transaction and again after commit; integration tests that seed rows with entityManager.persist bypass service eviction and must clear the cache themselves.

In the code: erg.auth.student-default-password (ErgAuthProperties.studentDefaultPassword, default erg2026, env ERG_AUTH_STUDENT_DEFAULT_PASSWORD), StudentCredentialResetService, StudentAccountProvisioner.
