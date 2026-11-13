---
title: Per-IP login limit counts only wrong passwords so a school behind one address can log in
author: Vuong
date: 2026-11-13
branch: fix/rate-limit-school-nat
id: l-14f7916a
scope: repo
status: active
src: agent
touches:
  - src/main/java/com/erg/ergspringboot/auth/internal/login/AuthLoginRateLimiter.java
  - src/main/java/com/erg/ergspringboot/auth/internal/login/AuthLoginUseCase.java
  - src/main/java/com/erg/ergspringboot/platform/graphql/ratelimit/GraphQlRateLimitCounter.java
  - src/main/java/com/erg/ergspringboot/platform/graphql/ratelimit/GraphQlRateLimitCounterRouter.java
  - src/main/java/com/erg/ergspringboot/platform/graphql/ratelimit/LocalEmergencyRateLimitCounter.java
  - src/main/java/com/erg/ergspringboot/platform/graphql/ratelimit/LuaFixedWindowRateLimitCounter.java
  - src/main/java/com/erg/ergspringboot/platform/graphql/ratelimit/Redis88IncrexRateLimitCounter.java
  - src/test/java/com/erg/ergspringboot/auth/internal/login/AuthLoginRateLimiterTest.java
  - src/test/java/com/erg/ergspringboot/platform/graphql/ratelimit/FixedWindowRateLimitCounterIntegrationTest.java
---

## Decided
The per-IP login limit counts only wrong-password attempts. Every student of a school reaches the
server from one NAT address, so counting every login blocked the 31st student of the minute even
with the right password (measured on the i5-7500: 31 of 209 distinct students got in). The per-IP
limit exists to stop password spraying across many accounts, and that shows up as failures.
The account (10/min) and device (20/min) limits still count every attempt.

## Watch out
- The check before the password peeks at the counter (`current`) and must not increment it.
- A Redis outage still fails the request closed at the peek; recording a failure never turns a
  failed login into a server error.

In the code: `AuthLoginRateLimiter.allowRequest` / `recordFailedLogin`, called from
`AuthLoginUseCase.login` on `AuthFailure.InvalidCredentials`; `GraphQlRateLimitCounter.current`.
