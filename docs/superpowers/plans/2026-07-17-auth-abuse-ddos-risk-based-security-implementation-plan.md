# Authentication Abuse, DDoS, and Risk-Based Security Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Close the proven authentication abuse and proxy trust gaps while adding distributed login protection and a deployable edge-security baseline.

**Architecture:** Redis 8.8 stores bounded distributed abuse counters and temporary challenge state. Spring owns trusted IP resolution, identity-aware login decisions, audit, and session response. Nginx and the external edge shed traffic before Java; no core LMS payload changes.

**Tech Stack:** Spring Boot 4, Spring Security, PostgreSQL, Redis 8.8, Nginx, JUnit, Testcontainers, k6.

---

### Task 1: Trusted client IP resolver

**Files:**
- Create `src/main/java/com/erg/ergspringboot/platform/security/TrustedClientIpResolver.java`
- Modify auth/client-context callers that parse forwarded headers
- Test `src/test/java/com/erg/ergspringboot/platform/security/TrustedClientIpResolverTest.java`

- [ ] Add failing tests for direct peer, trusted proxy chain, spoofed XFF from untrusted peer, malformed addresses, IPv6, and empty headers.
- [ ] Implement CIDR-aware right-to-left resolution and bind auth logging/rate-limit context to it.
- [ ] Remove direct `X-Forwarded-For` parsing from controllers.
- [ ] Run focused tests and `git diff --check`.

### Task 2: Remove account-lockout DoS

**Files:**
- Modify `LoginFailureEnforcementService.java`
- Create Redis-backed temporary login throttle/challenge service under `service/auth`
- Test `LoginFailureEnforcementServiceTest.java` and new abuse tests

- [ ] Add a failing regression proving five attacker failures do not set a victim `User` to `BLOCKED`.
- [ ] Preserve failure audit and session revocation only for explicit confirmed compromise/admin block.
- [ ] Implement temporary throttle/challenge state with generic authentication responses.
- [ ] Verify legitimate recovery/MFA path remains available and tenant isolation holds.

### Task 3: Distributed login abuse limiter

**Files:**
- Create Redis counter/service classes under `platform/auth/risk`
- Modify login entry point and `application.yml`
- Add Redis/native-image hints if required
- Tests under `src/test/java/com/erg/ergspringboot/platform/security`

- [ ] Add failing concurrency tests for IP, subnet, account hash, tenant-account, device, distinct-account, and distinct-source dimensions.
- [ ] Implement atomic Redis Lua/INCREX counters with TTL, bounded keys, and fail-closed privileged policy.
- [ ] Configure environment-driven thresholds for local/dev/production.
- [ ] Verify no raw email/password/token is stored in Redis or logs.

### Task 4: Risk-based login and session signals

**Files:**
- Create risk model/evaluator and security event writer under `service/auth` or `platform/security`
- Modify auth session creation and refresh flow
- Add migration only for required indexed risk/session state
- Tests for allow/step-up/deny and session revocation

- [ ] Add failing tests for new device, suspicious source, privileged role, recent failure burst, and missing reputation data.
- [ ] Implement bounded `ALLOW`, `STEP_UP`, `DENY` decisions without trusting client-supplied geolocation.
- [ ] Add short-lived challenge state; do not issue normal refresh token before step-up.
- [ ] Implement refresh-token rotation/reuse detection if not already complete and emit `REFRESH_TOKEN_REUSE`.
- [ ] Preserve low-risk login response compatibility; document additive challenge codes.

### Task 5: MFA foundation

**Files:**
- Reuse existing account/security-event/session models where possible
- Add MFA enrollment/challenge endpoints and services
- Add WebAuthn/passkey integration boundary and TOTP fallback
- Add FE guide `docs/frontend/2026-07-17-auth-risk-mfa-session-guide.md`

- [ ] Add contract tests for privileged-role MFA requirement and step-up expiry.
- [ ] Implement provider-neutral factor interface; do not store raw secrets or recovery codes.
- [ ] Add challenge/recovery error payload documentation.
- [ ] Verify admin/teacher compatibility and session revocation on password/MFA reset.

### Task 6: Nginx edge controls and production runbook

**Files:**
- Modify `ops/docker/nginx/native.conf` and `dev.conf`
- Modify compose/env examples and `ops/docker/README.md`
- Create `ops/docker/nginx/security-runbook.md`
- Tests `NginxSecurityConfigContractTest`

- [ ] Add route-specific `limit_req_zone`, `limit_conn_zone`, body limits, and JSON-compatible 429 behavior.
- [ ] Keep GraphQL identity-aware limiting in BE; avoid double limits that reject normal 5,000-user traffic.
- [ ] Document Cloudflare/tunnel, origin firewall, trusted proxy CIDRs, closed ports, and multi-replica deployment.
- [ ] Run `nginx -t` using the project-supported validation command and compose config validation.

### Task 7: Abuse/load verification and report

**Files:**
- Create/modify `load/k6/auth-abuse.js`
- Create `docs/2026-07-17-auth-abuse-ddos-risk-based-security-report.md`

- [ ] Run login burst, distributed credential stuffing, valid-password-new-device, refresh-reuse, Redis-down, and Nginx 429 scenarios.
- [ ] Record status codes, challenge rates, lockout behavior, Redis counters, DB writes, and origin saturation.
- [ ] Run focused tests, full Maven tests, native build contracts, and `git diff --check`.
- [ ] Report external DDoS protection as deployment prerequisite, not as a claim proven by local tests.
