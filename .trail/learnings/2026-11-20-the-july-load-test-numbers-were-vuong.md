---
title: "The July load-test numbers were taken before sign-in results were cached, so they no longer show what the backend can carry"
author: Vuong
date: 2026-11-20
branch: fix/student-import-identity
cost: { minutes: 5, tokens: 122895 }
id: l-6ad30caa
scope: repo
status: active
src: agent
touches:
  - docs/architecture/module-cache-descriptors.md
  - pom.xml
  - src/main/java/com/erg/ergspringboot/platform/security/ErgPrincipalService.java
  - src/main/java/com/erg/ergspringboot/platform/security/ErgAuthenticationFilter.java
  - src/main/java/com/erg/ergspringboot/lms/internal/readmodel/LmsStudentReadModelUseCase.java
  - src/main/java/com/erg/ergspringboot/platform/security/TenantContextFilter.java
---

## Decided
The 265 req/s ceiling at 500 users (backend using 3.8 of 4 CPUs) in the July 29 run is not the current capacity. At that time every request re-read the signed-in user from the database (4 statements, one a five-way join over roles, permissions, overrides and scopes) and the student assignment list was not cached. Both were fixed later (Nov 3 and Nov 13) and are on main. Re-run the 500-user step before quoting any capacity figure or arguing for a rewrite in another language.

## Tried and ruled out
Blaming Postgres or the connection pool: the pool had no waiting borrowers during that run; the backend container itself was out of CPU.

## Watch out
A cached sign-in result lives 30 seconds. With 6,000 users online that is still about 200 full reloads per second (3 statements each). This is an estimate from the code, not measured; check it in the next load run before raising the lifetime, because the 30 s is a documented security bound.

In the code: load/results/20260729T190558Z/comparison.md, commit 2f3c5caf (PERF-01), platform/security/PrincipalCache.java (TTL), users/persistence/auth/AuthSessionRepository.java findPrincipalByAccessTokenHashAndTenantId, lms/internal/readmodel/LmsStudentReadModelUseCase.java listAssignments, docs/architecture/module-cache-descriptors.md.
