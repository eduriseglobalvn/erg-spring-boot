---
title: "Student remember-me sessions slide 30 days, capped at 90"
author: Vuong
date: 2026-11-13
branch: feat/student-remember-me
id: l-281ec967
scope: repo
status: active
src: agent
touches:
  - src/main/java/com/erg/ergspringboot/auth/internal/login/SessionIssuer.java
  - src/main/java/com/erg/ergspringboot/auth/internal/refresh/AuthRefreshUseCase.java
---

## Decided
Owner decision 2026-11-13: a student who ticks "Ghi nhớ đăng nhập" gets a sliding session. Login sets expiresAt = now + 30 days; every refresh sets expiresAt = min(now + 30 days, createdAt + 90 days) (RememberedStudentSession.slide). Only a student-only account (the login's projected portals are exactly ["elearning"]) with LoginRequest.rememberMe = true gets it; teachers and admins keep 12h / 8h even when they send rememberMe = true, and a session that was not remembered keeps its absolute expiry on refresh.

There is no remember_me column. A session counts as remembered when expiresAt - createdAt is longer than one day, which only this path can produce (every other session is at most 12h). SessionIssuer sets createdAt = issue time explicitly so the 90-day anchor is exact instead of BaseEntity's @PrePersist clock.

## Tried and ruled out
A Flyway column (V146): main ends at V142, two unmerged branches both claim V143 and others hold V144/V145, and FlywayMigrationAppendOnlyContractTest requires gap-free numbers, so any number picked on this branch either collides or fails the contract until the others merge.

## Watch out
If any session type is ever issued for longer than a day, RememberedStudentSession.isRemembered must get a real flag (column) first, or that session will start sliding. The OpenAPI LoginRequest already had rememberMe, so no contract change was needed.
