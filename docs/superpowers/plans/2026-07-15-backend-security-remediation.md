# Backend Security Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close all eight validated backend authorization, tenant-isolation, and authentication-control findings with test-first, repository-native changes.

**Architecture:** Centralize protected-role and permission-alternative policy, require tenant-qualified object lookup at service/repository boundaries, and isolate failed-login enforcement in a `REQUIRES_NEW` transaction. Preserve existing REST DTOs and successful login behavior while making actor and tenant context explicit.

**Tech Stack:** Java 21, Spring Boot 4, Spring Security, Spring Data JPA, PostgreSQL/Flyway, JUnit 5, Mockito, Maven.

---

### Task 1: Protected-role policy and role-name safety

**Files:**
- Create: `src/main/java/com/erg/ergspringboot/platform/security/ProtectedRolePolicy.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/authorization/AuthorizationService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/users/UserService.java`
- Modify: `src/main/java/com/erg/ergspringboot/controller/users/UserController.java`
- Create: `src/test/java/com/erg/ergspringboot/platform/security/ProtectedRolePolicyTest.java`
- Modify: `src/test/java/com/erg/ergspringboot/service/users/UserServiceTest.java`

- [ ] Write tests rejecting `system.*` role creation/rename and lower-privileged assignment.
- [ ] Run focused tests and verify expected RED failures.
- [ ] Implement immutable protected-role registry and actor ceiling checks.
- [ ] Pass actor principal into user creation and role mutation boundaries.
- [ ] Run focused tests to GREEN and refactor without broadening scope.

### Task 2: REST/GraphQL DENY precedence parity

**Files:**
- Modify: `src/main/java/com/erg/ergspringboot/platform/security/ErgPolicyService.java`
- Modify: `src/main/java/com/erg/ergspringboot/platform/security/PermissionAuthorizationManager.java`
- Modify: `src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlAccessSupport.java`
- Create: `src/test/java/com/erg/ergspringboot/platform/security/PermissionAuthorizationManagerTest.java`
- Modify: `src/test/java/com/erg/ergspringboot/platform/graphql/GraphQlAccessSupportPermissionTest.java`

- [ ] Write a REST test where a specific DENY plus broad grant must deny.
- [ ] Verify RED against current per-alternative evaluation.
- [ ] Add one shared complete-alternative evaluator with DENY-first semantics.
- [ ] Route REST and GraphQL through the shared evaluator.
- [ ] Verify parity tests and legitimate broad-grant cases are GREEN.

### Task 3: Tenant-scoped employee list and authorization reads

**Files:**
- Modify: `src/main/java/com/erg/ergspringboot/repository/auth/user/UserRepository.java`
- Modify: `src/main/java/com/erg/ergspringboot/repository/authorization/UserPermissionOverrideRepository.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/users/UserService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/authorization/AuthorizationService.java`
- Modify: `src/main/java/com/erg/ergspringboot/controller/users/UserController.java`
- Modify: `src/main/java/com/erg/ergspringboot/controller/authorization/AuthorizationController.java`
- Modify: `src/test/java/com/erg/ergspringboot/service/users/UserServiceTest.java`
- Create: `src/test/java/com/erg/ergspringboot/service/authorization/AuthorizationServiceSecurityTest.java`

- [ ] Write failing tests proving foreign tenant users are excluded/rejected for list, effective permissions, overrides, feature config, and preview.
- [ ] Verify RED through real service/repository method contracts.
- [ ] Add tenant-qualified repository methods and mandatory tenant parameters.
- [ ] Pass principal tenant from controllers and remove global reads from admin services.
- [ ] Verify same-tenant controls remain GREEN.

### Task 4: Tenant-scoped authorization mutations

**Files:**
- Modify: `src/main/java/com/erg/ergspringboot/service/authorization/AuthorizationService.java`
- Modify: `src/main/java/com/erg/ergspringboot/controller/authorization/AuthorizationController.java`
- Modify: `src/test/java/com/erg/ergspringboot/service/authorization/AuthorizationServiceSecurityTest.java`

- [ ] Write failing tests for foreign bulk assignment, role removal, override creation, and override deletion.
- [ ] Verify RED and all-or-none bulk behavior.
- [ ] Resolve every target through tenant-qualified lookup before mutation.
- [ ] Reject incomplete or mixed-tenant batches atomically.
- [ ] Verify negative and legitimate same-tenant mutation cases.

### Task 5: Durable failed-login enforcement

**Files:**
- Create: `src/main/java/com/erg/ergspringboot/service/auth/LoginFailureEnforcementService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/auth/AuthService.java`
- Create: `src/test/java/com/erg/ergspringboot/service/auth/AuthServiceTransactionIntegrationTest.java`
- Modify: `src/test/java/com/erg/ergspringboot/service/auth/AuthServiceTest.java`

- [ ] Add a Spring proxy integration test proving attempt/lockout/session state currently rolls back.
- [ ] Run the test and verify RED for the expected missing durable state.
- [ ] Move failure recording and threshold enforcement into `REQUIRES_NEW`.
- [ ] Preserve existing `AuthFailure` types and successful-login behavior.
- [ ] Verify durable failure state, lockout, revocation, and success-path controls.

### Task 6: Database guardrails

**Files:**
- Create: `src/main/resources/db/migration/postgres/V56__authorization_security_boundaries.sql`
- Create: `src/test/java/com/erg/ergspringboot/platform/security/AuthorizationSecurityMigrationContractTest.java`

- [ ] Write migration contract tests for reserved/unique role names and tenant-supporting indexes.
- [ ] Verify RED before adding migration.
- [ ] Add normalized unique role-name and reserved-system-role guardrails compatible with seeded roles.
- [ ] Add supporting indexes needed by tenant-qualified authorization queries.
- [ ] Verify migration contracts and PostgreSQL migration tests.

### Task 7: Ordered verification and bypass review

**Files:**
- Review all files changed by Tasks 1–6.

- [ ] Inspect final diff and separate pre-existing changes from remediation changes.
- [ ] Run focused RBAC, tenant, auth, controller, GraphQL, and migration tests.
- [ ] Re-run each original malicious condition and same-tenant/legitimate control.
- [ ] Trace alternate protected-role casing, mixed-tenant bulk IDs, foreign override IDs, wildcard permissions, and transaction exceptions.
- [ ] Run the complete Maven test suite.
- [ ] Record any unavailable integration check as residual uncertainty; do not claim fixed if an ordered gate fails.
