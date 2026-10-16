# BE-034 Authenticated Actor and LMS Authorization Clean-Break Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the header/portal/flattened-permission security model with one immutable server-derived actor and make every protected LMS REST and GraphQL operation exact-permission, resource-scope, and ownership checked.

**Architecture:** The security context contains an `AuthenticatedActor` whose effective permissions stay bound to the scope that granted them. A server-side token-hash lookup discovers the session tenant before the tenant-scoped session query, so no client tenant header is needed. LMS owns a typed `LmsAccessPolicy` and authorization query port; REST and GraphQL only validate transport input and call the same LMS application operation.

**Tech Stack:** Java 25, Spring Boot 4, Spring Security, Spring Modulith, JPA/PostgreSQL, Flyway, REST, GraphQL, JUnit 5, AssertJ, Mockito, Testcontainers, Maven.

---

## Scope, baseline, and invariants

Task ID is `BE-034`. The workbook row is active at 20%, priority P0, platform `ERG-SPRING`, with dependencies `BE-004`, `BE-029`, and `BE-033`. The approved design is [2026-08-03-erg-auth-workspace-lms-authorization-clean-break-design.md](../specs/2026-08-03-erg-auth-workspace-lms-authorization-clean-break-design.md).

Baseline captured on 2026-08-03:

- Branch: `codex/spring-modulith-modernization`.
- The backend worktree is heavily dirty. Changes in the security, auth, LMS, GraphQL, migration, and test paths belong to the existing user work and must be preserved. The worker must run `git diff --name-only -- <path>` immediately before touching each file and patch only the required lines.
- Focused baseline command:

  ```powershell
  .\mvnw.cmd "-Dtest=AccessScopeSetTest,AccessScopeAssemblerTest,ErgPrincipalServiceTest,GraphQlRequestContextFactoryTest,LmsAssignmentCommandServiceTest,LmsRuntimeServiceAttemptRulesTest" test
  ```

  Expected baseline: exit 0, 31 tests, 0 failures, 0 errors. This passed on 2026-08-03 in 1m27s.

- Existing source confirms `ErgPrincipal`, `AccessScopeSet`, `AccessScopeAssembler`, and `UserAccessContext` duplicate scope facts; `ErgPrincipalService` unions direct role permissions and scoped-role permissions; `UserAccessContext` reads `X-Current-*`; `GraphQlRequestContextFactory` reads `X-Tenant-ID` and `X-Portal`; `LmsAttemptUseCase.assigned(...)` ends with `orElse(true)`; and `LmsAssignmentCommandService` validates tenant but not the teacher's resource scope.

The implementation must preserve these invariants:

1. Tenant, account, session, grants, and denies come only from verified server-side identity. `X-Tenant-ID`, `X-Portal`, `X-Current-*`, and equivalent aliases never influence authorization.
2. `platform.super_admin` is the only system bypass. Runtime authorization never recognizes `*`, `lms.*`, `lms.manage`, `content.manage`, or aliases as a substitute for an exact business permission.
3. A grant is `(exact permission, bound scope)`. Permission and scope sets are never independently unioned.
4. Workspace capabilities (`lms.workspace.admin.access`, `lms.workspace.teacher.access`, `lms.workspace.student.access`, `crm.workspace.access`) select product context and bootstrap navigation only; they do not authorize an operation.
5. Missing student mapping, missing resource, foreign class, foreign assignment, foreign group, foreign quiz, and foreign attempt fail closed. A known resource outside scope is exposed as `RESOURCE_NOT_FOUND`; a missing exact grant is `PERMISSION_DENIED`.
6. REST and GraphQL call one application operation and return the same stable error code and request ID.
7. Flyway is append-only. Existing migrations, especially `V4`, `V52`, and `V85`, are not edited. New migrations start at `V86`.
8. This plan covers BE-034 only. Session bootstrap (`BE-052`), BFF/PKCE/cookie/CSRF/SSO (`BE-053`), role-assignment administration (`BE-054`), and frontend migration (`ERG-088`–`ERG-097`) remain follow-on tasks.

## File map

Create these focused production types:

- `src/main/java/com/erg/ergspringboot/platform/security/AccountId.java` — normalized account identifier.
- `src/main/java/com/erg/ergspringboot/platform/security/SessionId.java` — normalized session identifier.
- `src/main/java/com/erg/ergspringboot/platform/security/TenantId.java` — verified tenant identifier with `ERG` server constant.
- `src/main/java/com/erg/ergspringboot/platform/security/PermissionCode.java` — exact lower-case permission value.
- `src/main/java/com/erg/ergspringboot/platform/security/RoleName.java` — immutable role/audit value.
- `src/main/java/com/erg/ergspringboot/platform/security/AccessScopeType.java` — `PLATFORM`, `TENANT`, `EDUCATION_UNIT`, `CLASS`, `COURSE`, `SELF` scope kinds.
- `src/main/java/com/erg/ergspringboot/platform/security/ScopeMode.java` — `PLATFORM`, `TENANT`, `CONSTRAINED` modes.
- `src/main/java/com/erg/ergspringboot/platform/security/EffectiveGrant.java` — one permission and its bound `AccessScopeSet`.
- `src/main/java/com/erg/ergspringboot/platform/security/EffectiveGrantSet.java` — immutable deny-aware grant collection.
- `src/main/java/com/erg/ergspringboot/platform/security/AuthenticatedActor.java` — the only authenticated application actor.
- `src/main/java/com/erg/ergspringboot/platform/security/AuthenticatedActorResolver.java` — security-context lookup and required-actor failure.
- `src/main/java/com/erg/ergspringboot/platform/security/AuthenticatedActorFactory.java` — builds the actor from a loaded session and active role assignments.
- `src/main/java/com/erg/ergspringboot/platform/security/AuthSessionTenantResolver.java` — server-only token-hash-to-tenant port.
- `src/main/java/com/erg/ergspringboot/platform/security/JdbcAuthSessionTenantResolver.java` — bounded PostgreSQL function call for the port.
- `src/main/java/com/erg/ergspringboot/lms/authorization/LmsAction.java` — exact LMS action-to-permission mapping.
- `src/main/java/com/erg/ergspringboot/lms/authorization/LmsResourceRef.java` — sealed LMS resource references.
- `src/main/java/com/erg/ergspringboot/lms/authorization/LmsAccessPolicy.java` — public LMS application-boundary policy.
- `src/main/java/com/erg/ergspringboot/lms/internal/authorization/LmsAuthorizationQueryPort.java` — minimal LMS authorization projections.
- `src/main/java/com/erg/ergspringboot/lms/internal/authorization/JpaLmsAuthorizationQueryAdapter.java` — JPA adapter for those projections.
- `src/main/java/com/erg/ergspringboot/lms/internal/authorization/DefaultLmsAccessPolicy.java` — teacher/student/admin decision implementation.

Modify these security/auth boundaries:

- `src/main/java/com/erg/ergspringboot/platform/security/AccessScopeSet.java`
- `src/main/java/com/erg/ergspringboot/platform/security/ErgAuthenticationFilter.java`
- `src/main/java/com/erg/ergspringboot/platform/security/ErgPrincipalService.java`
- `src/main/java/com/erg/ergspringboot/platform/security/OidcPrincipalBridgeFilter.java`
- `src/main/java/com/erg/ergspringboot/platform/security/SecurityConfig.java`
- `src/main/java/com/erg/ergspringboot/platform/security/SecurityErrorResponseWriter.java`
- `src/main/java/com/erg/ergspringboot/platform/security/TenantContext.java`
- `src/main/java/com/erg/ergspringboot/platform/security/TenantContextFilter.java`
- `src/main/java/com/erg/ergspringboot/platform/security/TenantGucApplier.java`
- `src/main/java/com/erg/ergspringboot/platform/security/SecurityUtils.java`
- `src/main/java/com/erg/ergspringboot/platform/security/MethodAuthorizationEvaluator.java`
- `src/main/java/com/erg/ergspringboot/platform/security/PermissionAuthorizationManager.java`
- `src/main/java/com/erg/ergspringboot/platform/security/ErgPolicyService.java`
- `src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlRequestContext.java`
- `src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlRequestContextFactory.java`
- `src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlAccessSupport.java`
- `src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlGuardFilter.java`
- `src/main/java/com/erg/ergspringboot/platform/web/ApiAccessLogFilter.java`
- `src/main/java/com/erg/ergspringboot/platform/web/CorsConfig.java`
- `src/main/java/com/erg/ergspringboot/controller/sessions/SessionController.java`
- `src/main/java/com/erg/ergspringboot/controller/platform/MigrationPlatformController.java`
- `src/main/java/com/erg/ergspringboot/repository/auth/AuthSessionRepository.java`
- `src/main/java/com/erg/ergspringboot/repository/authorization/UserRoleAssignmentRepository.java`

Modify LMS application and transport boundaries:

- `src/main/java/com/erg/ergspringboot/lms/LmsOperations.java`
- `src/main/java/com/erg/ergspringboot/lms/LmsQueryOperations.java`
- `src/main/java/com/erg/ergspringboot/lms/LmsAcademicRecordOperations.java`
- `src/main/java/com/erg/ergspringboot/lms/internal/attempt/LmsAttemptUseCase.java`
- `src/main/java/com/erg/ergspringboot/lms/internal/readmodel/LmsStudentReadModelUseCase.java`
- `src/main/java/com/erg/ergspringboot/lms/internal/readmodel/LmsClassWorkspaceReadUseCase.java`
- `src/main/java/com/erg/ergspringboot/lms/internal/readmodel/LmsTeacherHomeworkReadUseCase.java`
- `src/main/java/com/erg/ergspringboot/lms/internal/readmodel/LmsAssignmentProgressQueryUseCase.java`
- `src/main/java/com/erg/ergspringboot/lms/internal/readmodel/LmsStudentGroupReadUseCase.java`
- `src/main/java/com/erg/ergspringboot/lms/internal/classroom/LmsClassroomQueryUseCase.java`
- `src/main/java/com/erg/ergspringboot/lms/internal/resource/LmsResourceQueryUseCase.java`
- `src/main/java/com/erg/ergspringboot/lms/internal/academicrecord/LmsAcademicRecordUseCase.java`
- `src/main/java/com/erg/ergspringboot/service/lms/LmsAssignmentCommandService.java`
- `src/main/java/com/erg/ergspringboot/service/lms/LmsRuntimeService.java`
- `src/main/java/com/erg/ergspringboot/controller/lms/LmsController.java`
- `src/main/java/com/erg/ergspringboot/controller/lms/LmsAssignmentController.java`
- `src/main/java/com/erg/ergspringboot/controller/lms/graphql/LmsClassWorkspaceGraphQlController.java`
- `src/main/java/com/erg/ergspringboot/controller/lms/graphql/LmsHomeworkWorkspaceGraphQlController.java`
- `src/main/java/com/erg/ergspringboot/controller/lms/graphql/LmsLearningResourceGraphQlController.java`
- `src/main/java/com/erg/ergspringboot/lms/internal/academicrecord/LmsAcademicRecordGraphQlController.java`

The remaining production consumers of `ErgPrincipal` and `UserAccessContext` are migrated in the same actor-cutover task. The exact inventory command is part of Task 3 and must return zero legacy references before the task can be marked complete.

Create append-only migrations:

- `src/main/resources/db/migration/postgres/V86__server_side_auth_session_tenant_lookup.sql`
- `src/main/resources/db/migration/postgres/V87__clean_break_scoped_lms_permissions.sql`

Create/modify tests in these focused areas:

- `src/test/java/com/erg/ergspringboot/platform/security/AuthenticatedActorTest.java`
- `src/test/java/com/erg/ergspringboot/platform/security/EffectiveGrantSetTest.java`
- `src/test/java/com/erg/ergspringboot/platform/security/AuthSessionTenantResolverTest.java`
- `src/test/java/com/erg/ergspringboot/platform/security/ErgAuthenticationFilterTest.java`
- `src/test/java/com/erg/ergspringboot/platform/security/OidcPrincipalBridgeFilterTest.java`
- `src/test/java/com/erg/ergspringboot/platform/security/SecurityHardeningTest.java`
- `src/test/java/com/erg/ergspringboot/platform/security/GraphQlRequestContextFactoryTest.java`
- `src/test/java/com/erg/ergspringboot/platform/security/RuntimePermissionCatalogContractTest.java`
- `src/test/java/com/erg/ergspringboot/platform/security/LmsAccessPolicyTest.java`
- `src/test/java/com/erg/ergspringboot/platform/security/LegacyAuthorizationInputContractTest.java`
- `src/test/java/com/erg/ergspringboot/lms/internal/readmodel/LmsStudentReadModelUseCaseTest.java`
- `src/test/java/com/erg/ergspringboot/lms/internal/authorization/DefaultLmsAccessPolicyTest.java`
- `src/test/java/com/erg/ergspringboot/service/lms/LmsAssignmentCommandServiceTest.java`
- `src/test/java/com/erg/ergspringboot/service/lms/LmsRuntimeServiceAttemptRulesTest.java`
- `src/test/java/com/erg/ergspringboot/controller/lms/LmsRuntimeControllerContractTest.java`
- `src/test/java/com/erg/ergspringboot/controller/lms/graphql/LmsHomeworkWorkspaceGraphQlControllerTest.java`
- `src/test/java/com/erg/ergspringboot/controller/lms/graphql/LmsClassWorkspaceGraphQlControllerTest.java`
- `src/test/java/com/erg/ergspringboot/lms/internal/academicrecord/LmsAcademicRecordGraphQlControllerTest.java`
- `src/test/java/com/erg/ergspringboot/config/persistence/FlywayMigrationAppendOnlyContractTest.java`
- `src/test/java/com/erg/ergspringboot/config/persistence/FlywayUpgradePathIntegrationTest.java`
- `src/test/java/com/erg/ergspringboot/platform/security/TenantRlsIsolationIntegrationTest.java`
- `src/test/java/com/erg/ergspringboot/platform/security/TenantRlsMatrixIntegrationTest.java`

Delete after all consumers are migrated and no references remain:

- `src/main/java/com/erg/ergspringboot/platform/security/ErgPrincipal.java`
- `src/main/java/com/erg/ergspringboot/platform/security/AccessScopeAssembler.java`
- `src/main/java/com/erg/ergspringboot/platform/security/UserAccessContext.java`
- `src/main/java/com/erg/ergspringboot/platform/security/ErgPolicyService.java`
- `src/main/java/com/erg/ergspringboot/platform/security/PermissionAuthorizationManager.java`
- LMS business-authorization annotations under `src/main/java/com/erg/ergspringboot/lms/authorization/` after controllers no longer use them.

## Implementation sequence

### Task 1: Write RED tests for the immutable actor and bound grants

**Files:** Create `AuthenticatedActorTest.java`, `EffectiveGrantSetTest.java`; replace the old compatibility assertions in `AccessScopeSetTest.java` and `ErgPrincipalConstructionContractTest.java`.

- [ ] **Step 1: Write the actor construction tests.** Assert that blank account, session, or tenant identifiers throw; an empty grant set or deny set throws; all collections are immutable; profile attributes are not accepted by the actor.

  ```java
  @Test
  void actorRequiresVerifiedIdentityAndNonNullAuthorizationSets() {
      assertThatThrownBy(() -> AuthenticatedActor.of(null, new SessionId("s"), TenantId.ERG,
              Set.of(), EffectiveGrantSet.empty(), Set.of()))
          .isInstanceOf(IllegalArgumentException.class);
  }
  ```

- [ ] **Step 2: Write exact grant-binding tests.** Cover a teacher grant for `CLASS/class-a` and a different student grant for `SELF/account-b`. Assert class-a permission cannot consume class-b or SELF; an explicit deny wins; `*`, `lms.*`, and `lms.manage` are rejected as grant codes.

- [ ] **Step 3: Write scope-mode tests.** `platform()` covers any resource only when the permission is `platform.super_admin`; `tenant()` covers tenant-owned resources but not a class-specific resource without a class grant; `constrained(CLASS, class-a)` covers only class-a; `self(account-a)` covers only account-a. No current selection is stored.

- [ ] **Step 4: Run the RED test set.**

  ```powershell
  .\mvnw.cmd "-Dtest=AuthenticatedActorTest,EffectiveGrantSetTest,AccessScopeSetTest" test
  ```

  Expected result before implementation: compilation or assertion failures identifying the missing actor/grant APIs.

- [ ] **Step 5: Implement the value objects.** Use records for identifiers and grants. Use `Map<AccessScopeType, SortedSet<String>>` internally, defensive copies at construction, and a canonical SHA-256 fingerprint. Expose only `covers(LmsResourceScope)` and `fingerprint`; do not expose mutable maps or current-scope accessors.

  The stable API used by later tasks is:

  ```java
  public record EffectiveGrant(PermissionCode permission, AccessScopeSet scope) {}

  public final class EffectiveGrantSet {
      public boolean hasExact(PermissionCode permission, LmsResourceScope resourceScope) { /* exact grant + its own scope */ }
  }

  public record AuthenticatedActor(
          AccountId accountId,
          SessionId sessionId,
          TenantId tenantId,
          Set<RoleName> roles,
          EffectiveGrantSet grants,
          Set<PermissionCode> deniedPermissions
  ) {
      public static AuthenticatedActor of(AccountId accountId, SessionId sessionId, TenantId tenantId,
              Set<RoleName> roles, EffectiveGrantSet grants, Set<PermissionCode> deniedPermissions) { /* validated immutable construction */ }
  }
  ```

- [ ] **Step 6: Run the focused tests again.** Expected result: all actor, grant-binding, deny-precedence, wildcard-rejection, and fingerprint tests pass.

### Task 2: Resolve the tenant from the bearer token on the server

**Files:** Create `AuthSessionTenantResolver.java`, `JdbcAuthSessionTenantResolver.java`, and `AuthSessionTenantResolverTest.java`; create `V86__server_side_auth_session_tenant_lookup.sql`; modify `AuthSessionRepository.java`, `ErgAuthenticationFilter.java`, `ErgPrincipalService.java`, `TenantContext.java`, and `TenantGucApplier.java`.

- [ ] **Step 1: Write the resolver contract test.** The resolver accepts only a SHA-256 token hash, returns one tenant only when exactly one session row matches, returns empty for no row or a collision across tenants, and never accepts a request header.

- [ ] **Step 2: Write the migration test before SQL.** Assert V86 creates a `SECURITY DEFINER` function with a fixed `search_path`, `REVOKE ALL FROM PUBLIC`, conditional execute grant to `erg_app`, and a query constrained to `access_token_hash` plus active expiry/revocation state. Assert no existing migration file changes.

- [ ] **Step 3: Implement V86.** Add `erg_resolve_session_tenant(token_hash varchar)` returning `tenant_id` only for one active session. Use `SET search_path = pg_catalog, public`, qualify `public.user_sessions`, and return `NULL` when the same hash appears under multiple tenants. Create an index on `access_token_hash` if the existing composite index is insufficient. Do not enable maintenance mode for an HTTP request.

- [ ] **Step 4: Implement the JDBC adapter.** Call the function with a prepared statement, hash the bearer token before the call, reject blank tokens, and return `TenantId` only. Never log the token, hash, account, or session.

- [ ] **Step 5: Change authentication flow.** `ErgAuthenticationFilter` hashes the bearer token, resolves the tenant server-side, calls `TenantContext.authenticationLookup(tenantId)` for the bounded session lookup, loads the complete session through the existing tenant-scoped repository, and clears that lookup context in `finally`. Missing resolver result produces `AUTHENTICATION_REQUIRED`; it does not fall back to `erg` and does not inspect `X-Tenant-ID`.

- [ ] **Step 6: Run the resolver and RLS tests.**

  ```powershell
  .\mvnw.cmd "-Dtest=AuthSessionTenantResolverTest,ErgAuthenticationFilterTest,TenantRlsIsolationIntegrationTest,TenantRlsMatrixIntegrationTest" test
  ```

  Expected result: exit 0 with no client-controlled tenant path remaining.

### Task 3: Build the actor from session-scoped role assignments and cut over Spring Security

**Files:** Create `AuthenticatedActorFactory.java` and `AuthenticatedActorResolver.java`; modify `ErgPrincipalService.java`, `OidcPrincipalBridgeFilter.java`, `TenantContextFilter.java`, `SecurityUtils.java`, `MethodAuthorizationEvaluator.java`, `AuthorizationOperations.java`, `AuthorizationCommandOperations.java`, `ProtectedRolePolicy.java`, and every production consumer found by the inventory commands below.

- [ ] **Step 1: Capture the exact dirty-file inventory.** Run:

  ```powershell
  rg -l --glob '*.java' 'ErgPrincipal|UserAccessContext|AccessScopeAssembler' src/main src/test
  git diff --name-only -- src/main/java/com/erg/ergspringboot/platform/security src/main/java/com/erg/ergspringboot/auth src/main/java/com/erg/ergspringboot/lms src/main/java/com/erg/ergspringboot/controller src/test/java/com/erg/ergspringboot/platform/security src/test/java/com/erg/ergspringboot/lms
  ```

  Save the output in the task evidence. Do not overwrite any file that is dirty for another task.

- [ ] **Step 2: Implement `AuthenticatedActorFactory`.** Load active `UserRoleAssignment` rows with `role.permissions`, map `SYSTEM` to platform mode, `TENANT` to tenant mode, `CENTER`/`SCHOOL` to `EDUCATION_UNIT`, `CLASS` to `CLASS`, and `SELF` to `SELF`. Create one `EffectiveGrant` per permission per assignment; never merge scopes across assignments. Add the exact workspace capability grants as ordinary grants.

- [ ] **Step 3: Apply deny semantics.** Expired/revoked assignments contribute nothing. A `DENY` user override contributes an actor-wide exact deny. An unscoped grant override cannot authorize a scoped LMS operation; it is either materialized by V87 into an assignment or ignored with an audit event. Do not create a new wildcard or alias path.

- [ ] **Step 4: Use the same factory for bearer and OIDC authentication.** OIDC resolves the server-configured ERG tenant and account identifier from the verified issuer; it never reads `X-Tenant-ID`. Both filters install `UsernamePasswordAuthenticationToken` with `AuthenticatedActor` as principal.

- [ ] **Step 5: Migrate non-LMS consumers.** Replace `principal.userId()` with `actor.accountId().value()`, `principal.tenantId()` with `actor.tenantId().value()`, and permission checks with exact `actor.grants().hasExact(...)` against a module-owned resource scope. Replace `UserAccessContext` arguments with `AuthenticatedActor` or a module-owned projection. Background workers keep `TenantContext.maintenance()` only in their existing bounded worker paths.

- [ ] **Step 6: Add architecture ratchets.** Change `ErgPrincipalConstructionContractTest` into a contract asserting that no production file mentions `ErgPrincipal`, `UserAccessContext`, `AccessScopeAssembler`, `permissions().contains("*")`, or `portalFromPath` for authorization. Assert all actor construction goes through `AuthenticatedActorFactory`.

- [ ] **Step 7: Run compile and security tests.**

  ```powershell
  .\mvnw.cmd "-Dtest=AuthenticatedActorTest,EffectiveGrantSetTest,ErgPrincipalServiceTest,OidcPrincipalBridgeFilterTest,MethodAuthorizationEvaluatorTest,PermissionAuthorizationManagerTest,SecurityHardeningTest" test
  ```

  Expected result after migration: green tests with the old principal tests rewritten to actor semantics. The old `PermissionAuthorizationManagerTest` is deleted if the class is deleted.

### Task 4: Remove client-controlled tenant, portal, current-scope, and authenticated GraphQL tenant inputs

**Files:** Modify `GraphQlRequestContext.java`, `GraphQlRequestContextFactory.java`, `GraphQlAccessSupport.java`, `GraphQlGuardFilter.java`, `ApiAccessLogFilter.java`, `CorsConfig.java`, `SessionController.java`, `MigrationPlatformController.java`, all authenticated GraphQL controllers with `tenantId` input fields, `src/main/resources/graphql/schema.graphqls`, and the generated OpenAPI source through controller signatures.

- [ ] **Step 1: Write the transport contract test.** Scan `src/main/java`, GraphQL schema input definitions, and OpenAPI export for `X-Tenant-ID`, `X-Portal`, `X-Current-`, `X-Class-ID`, `X-Center-ID`, `X-EducationUnit-ID`, and authenticated `tenantId` arguments. The test must fail while any protected input remains.

- [ ] **Step 2: Simplify GraphQL request context.** Keep `AuthenticatedActor actor`, request ID, operation name, and bearer/session presence. Remove requested tenant, requested portal, current education unit, current class, flattened portals, and duplicated permission lists. `GraphQlRequestContextFactory` reads only the verified security context and correlation headers.

- [ ] **Step 3: Remove transport headers.** Delete these names from `ErgAuthenticationFilter`, `UserAccessContext` callers, `GraphQlGuardFilter`, `ApiAccessLogFilter`, `CorsConfig`, migration diagnostics, and session endpoints. Logs use the actor tenant and account only after authentication; unauthenticated logs contain no client tenant claim.

- [ ] **Step 4: Remove GraphQL input tenant IDs.** Delete `tenantId` from authenticated input records such as `AttendanceWorkspaceInput`, `GradebookWorkspaceInput`, `UpsertAttendanceInput`, `UpsertGradebookScoreInput`, teaching calendar inputs, student engagement inputs, LMS workspace inputs, and LCMS school-operation inputs. Keep tenant IDs in response DTOs only if the published response still requires them; populate them from the actor.

- [ ] **Step 5: Update REST signatures.** Remove authenticated `tenantId` request parameters/path variables from calendar, LMS, session, document, LCMS media, and user operations. The service obtains tenant from the actor and loads resources by `(actor tenant, resource id)`.

- [ ] **Step 6: Regenerate and inspect contracts.** Run the backend export/check commands from Task 12 and verify that no authenticated tenant input appears. Do not hand-edit generated frontend types or vendored FE contracts.

### Task 5: Make platform authorization exact and reduce `SecurityConfig` to infrastructure concerns

**Files:** Modify `AuthorizationOperations.java`, `MethodAuthorizationEvaluator.java`, `SecurityConfig.java`, `SecurityErrorResponseWriter.java`, `ErgPolicyService.java`; delete `PermissionAuthorizationManager.java` after its consumers are removed; update method-policy tests.

- [ ] **Step 1: Write exact-policy tests.** Assert exact `PermissionCode` equality only; `*`, namespace wildcards, aliases, and role names never satisfy an operation. Assert explicit deny wins. Assert an actor with only a class-scoped grant cannot pass a route check that has no resource projection.

- [ ] **Step 2: Implement the small platform facade.** Keep route authorization limited to public/auth/infrastructure/authenticated fallback/deny-all. For non-LMS module meta-annotations, evaluate exact permission plus tenant/platform scope; resource-specific LMS decisions are delegated to `LmsAccessPolicy`.

- [ ] **Step 3: Simplify `SecurityConfig`.** Preserve public health/docs/webhook/auth endpoints, OAuth2/BFF setup, CSRF/session configuration, authenticated `/api/v1/**` fallback, and final `.anyRequest().denyAll()`. Remove LMS, LCMS, CRM, reporting, calendar, and notification business permission strings from the filter chain.

- [ ] **Step 4: Normalize errors.** Map missing actor to `AUTHENTICATION_REQUIRED`, missing workspace capability to `WORKSPACE_ACCESS_DENIED`, missing exact grant to `PERMISSION_DENIED`, and preserve request correlation. GraphQL extensions use the same code values as REST.

- [ ] **Step 5: Run platform tests.**

  ```powershell
  .\mvnw.cmd "-Dtest=SecurityHardeningTest,MethodAuthorizationEvaluatorTest,GraphQlAccessSupportPermissionTest,GraphQlRequestContextFactoryTest,RuntimePermissionCatalogContractTest" test
  ```

### Task 6: Add the clean-break permission catalog and backfill scoped assignments

**Files:** Create `V87__clean_break_scoped_lms_permissions.sql`; add migration contract assertions under `src/test/java/com/erg/ergspringboot/config/persistence/`; update `RuntimePermissionCatalogContractTest`.

- [ ] **Step 1: Write migration RED tests.** Require the four workspace capabilities, all exact LMS actions used by REST/GraphQL, `platform.super_admin`, and `SELF` scope support. Require no new role permission for `lms.manage`, `lms.portal.access`, `*`, `lms.*`, or aliases.

- [ ] **Step 2: Extend assignment scope types append-only.** Drop and recreate only the V59 check constraint under a new name so `SELF` and `EDUCATION_UNIT` are accepted while `SYSTEM`, `TENANT`, `CENTER`, `SCHOOL`, and `CLASS` remain valid. Do not edit V59.

- [ ] **Step 3: Seed exact permissions.** Insert workspace permissions and missing exact LMS permissions with `resource`, `action`, `portal`, and `supported_scope` metadata. Mark historical wildcard/alias rows `DEPRECATED` and remove their active role links; keep historical rows for audit.

- [ ] **Step 4: Backfill direct roles into scoped assignments.** Convert `user_roles` rows into `user_role_assignments` idempotently: system super-admin roles become `SYSTEM`; learner roles become `SELF` with `scope_id=user_id`; roles with `user_access_scopes` become one `SCHOOL`/`CENTER` assignment per education unit; remaining tenant-owned roles become `TENANT` assignments. Use the user itself as `assigned_by` only for this deterministic historical backfill and record the event in migration comments/audit metadata.

- [ ] **Step 5: Grant workspace capabilities by role template.** Teacher role templates receive teacher workspace access; learner role receives student workspace access; LMS admin receives admin workspace access; CRM role templates receive CRM workspace access. These grants remain separate from operation grants.

- [ ] **Step 6: Run fresh and upgrade migration tests.**

  ```powershell
  .\mvnw.cmd "-Dtest=FlywayMigrationAppendOnlyContractTest,FlywayUpgradePathIntegrationTest,RuntimePermissionCatalogContractTest" test
  ```

### Task 7: Introduce the typed LMS policy boundary

**Files:** Create `LmsAction.java`, `LmsResourceRef.java`, `LmsAccessPolicy.java`, `LmsAuthorizationQueryPort.java`, `JpaLmsAuthorizationQueryAdapter.java`, `DefaultLmsAccessPolicy.java`, and `DefaultLmsAccessPolicyTest.java`; modify `lms/package-info.java` and `lms/authorization/package-info.java`.

- [ ] **Step 1: Write the action matrix test.** Enumerate every protected operation currently exposed by `LmsController`, `LmsAssignmentController`, the four LMS GraphQL/academic-record controllers, and the matching generated operation names. Each action maps to one exact permission and one workspace capability.

- [ ] **Step 2: Define resource references.** Use a sealed hierarchy with records for `EducationUnitRef`, `ClassRef`, `AssignmentRef`, `AssignmentSelectionRef`, `StudentRef`, `StudentSelfRef`, `AttemptRef`, `QuizRef`, `StudentGroupRef`, and `CourseRef`. Resource refs contain IDs only; they never contain a client tenant or current scope.

- [ ] **Step 3: Define the policy contract.**

  ```java
  public interface LmsAccessPolicy {
      void require(AuthenticatedActor actor, LmsAction action, LmsResourceRef resource);
  }
  ```

  A policy decision first checks the workspace capability, then the exact action permission, then the bound grant against facts returned by `LmsAuthorizationQueryPort`. `platform.super_admin` is the sole bypass.

- [ ] **Step 4: Implement authorization projections.** Add repository methods that return only tenant-bound facts: classroom school, assignment owner/class/recipient IDs/resources, group owner/class/member IDs, quiz assignment links, student account mapping, and attempt assignment/student ownership. The adapter must use `(actor tenant, id)` in every query.

- [ ] **Step 5: Implement teacher rules.** Assignment creation validates every selected class/group/student and every quiz resource before saving. Update recipients validates the existing assignment plus all replacement recipients. Update/delete/progress/grade/attendance/class/resource reads require a matching class, education-unit, course, or ownership scope according to the action.

- [ ] **Step 6: Implement student rules.** Resolve exactly one active `Student` by `(actor tenant, auth_user_id=actor.accountId)`. Missing mapping denies. Assignment/attempt/quiz reads and writes require recipient/enrollment/attempt ownership. A student self grant cannot consume a teacher class grant.

- [ ] **Step 7: Implement admin rules.** Admin requires admin workspace capability plus exact admin permission and organization/education-unit scope unless the actor carries `platform.super_admin`.

- [ ] **Step 8: Run policy tests.** Cover cross-class teacher denial, cross-school denial, out-of-scope group/student/quiz selection, missing student mapping, cross-student attempt denial, explicit deny precedence, multi-role grant isolation, and system bypass.

### Task 8: Move assignment commands behind the LMS policy

**Files:** Modify `LmsOperations.java`, `LmsAssignmentCommandService.java`, `LmsAssignmentController.java`, `LmsAssignmentCommandServiceTest.java`; add policy mocks/facts to the tests.

- [ ] **Step 1: Write failing command tests.** Verify policy is called before the first repository write for create, update, recipient replacement, delete, group create/update/delete. Verify one invalid recipient or quiz prevents all writes.

- [ ] **Step 2: Change the application facade.** `LmsOperations` receives `AuthenticatedActor`, creates resource refs, calls `LmsAccessPolicy.require`, and passes only actor-derived tenant/account IDs to the command service. Controllers do not pass tenant or user IDs from request data.

- [ ] **Step 3: Keep the command service persistence-focused.** Remove authorization decisions and header/current-scope assumptions from `LmsAssignmentCommandService`; preserve idempotency, active quiz-version pinning, recipient flattening, optimistic/idempotent behavior, and cache eviction.

- [ ] **Step 4: Add complete selection validation.** For `class`, `group`, and `students` recipient modes, resolve all resources first and reject unknown/foreign rows with `RESOURCE_NOT_FOUND`. A teacher may not create a valid assignment for one in-scope class while smuggling an out-of-scope group/student/quiz.

- [ ] **Step 5: Run focused command tests.** Expected result: existing six command tests plus new scope/ownership tests pass, and Mockito verifies no persistence interaction on authorization failure.

### Task 9: Close student attempt, read-model, and quiz ownership gaps

**Files:** Modify `LmsAttemptUseCase.java`, `LmsRuntimeService.java`, `LmsStudentReadModelUseCase.java`, `LmsResourceQueryUseCase.java`, `LmsClassroomQueryUseCase.java`, `LmsOperations.java`, `LmsController.java`, and `LmsRuntimeServiceAttemptRulesTest.java`.

- [ ] **Step 1: Write the missing-mapping RED test.** An authenticated account with no `Student.authUserId` mapping cannot list assignments/scores, start an attempt, save a draft, save an answer, sync, or submit. Assert no fallback student ID is accepted.

- [ ] **Step 2: Remove permissive fallback.** Replace `students.findFirstByTenantIdAndAuthUserId(...).orElse(studentId)` and `orElse(true)` with a required mapping from `LmsAccessPolicy`/authorization projection. A missing mapping throws the stable denial/not-found exception before any attempt mutation.

- [ ] **Step 3: Enforce attempt ownership.** `mutable(...)` loads the attempt under actor tenant and asks policy to verify the attempt's assignment, recipient, student ownership, and class scope. A student cannot mutate another student's attempt; a teacher can read progress only through a matching assignment/class/ownership grant.

- [ ] **Step 4: Enforce quiz package access.** A student requesting a quiz package must be a recipient of an assignment containing that quiz. A teacher must have the exact quiz/resource permission and a matching class/education-unit/course scope. Tenant-only lookup is insufficient.

- [ ] **Step 5: Preserve cache isolation.** Cache keys and invalidation retain tenant plus actor/scope fingerprint where data depends on identity or scope. Logout/session behavior remains a follow-on BE-053 concern; this task must not introduce token or authorization-decision caching.

- [ ] **Step 6: Run attempt/read tests.**

  ```powershell
  .\mvnw.cmd "-Dtest=LmsRuntimeServiceAttemptRulesTest,LmsStudentReadModelUseCaseTest,LmsAccessPolicyTest" test
  ```

### Task 10: Unify REST and GraphQL LMS transport

**Files:** Modify `LmsController.java`, `LmsAssignmentController.java`, `LmsClassWorkspaceGraphQlController.java`, `LmsHomeworkWorkspaceGraphQlController.java`, `LmsLearningResourceGraphQlController.java`, `LmsAcademicRecordGraphQlController.java`, `LmsQueryOperations.java`, `LmsAcademicRecordOperations.java`, and their contract tests.

- [ ] **Step 1: Write transport RED tests.** Assert every REST and GraphQL entry point resolves the same actor, invokes the same application operation, contains no business permission string, and cannot accept tenant/portal/current-scope inputs.

- [ ] **Step 2: Inject the actor.** Use `AuthenticatedActorResolver.require()` at the application boundary. Controllers parse DTOs and IDs only. They do not call `SecurityUtils.portalFromPath`, `GraphQlAccessSupport.assertPermission`, `UserAccessContext.from`, or manual scope helpers.

- [ ] **Step 3: Route GraphQL through application operations.** Remove repeated `assertPermission` calls and legacy annotation alternatives (`lms.manage`, `*`). GraphQL field methods pass actor and typed resource refs to the same `LmsOperations`/`LmsQueryOperations` methods used by REST.

- [ ] **Step 4: Normalize error mapping.** REST and GraphQL tests must assert identical `PERMISSION_DENIED`, `WORKSPACE_ACCESS_DENIED`, `RESOURCE_NOT_FOUND`, `VALIDATION_ERROR`, and request-correlation behavior for equivalent scenarios.

- [ ] **Step 5: Remove LMS annotation package.** Delete the LMS business meta-annotations only after the source scan confirms no controller or use case imports them. Platform/RBAC annotations outside LMS remain owned by their modules.

### Task 11: Remove legacy classes and install permanent security ratchets

**Files:** Delete the legacy security classes listed in the file map; modify `ErgPrincipalConstructionContractTest.java`, `BusinessOperationPolicyContractTest.java`, `GraphQlSecurityGuardContractTest.java`, `SecurityHardeningTest.java`, and add `LegacyAuthorizationInputContractTest.java`.

- [ ] **Step 1: Run the zero-reference scan.**

  ```powershell
  rg -n --glob '*.java' 'ErgPrincipal|UserAccessContext|AccessScopeAssembler|X-Tenant-ID|X-Portal|X-Current-|lms\.manage|permissions\(\)\.contains\("\*"\)|portalFromPath' src/main src/test
  ```

  Expected result after the migration: no production authorization references. Test fixtures may mention removed names only in the negative contract assertions.

- [ ] **Step 2: Delete only after the scan is clean.** Remove `ErgPrincipal`, `AccessScopeAssembler`, `UserAccessContext`, `ErgPolicyService`, `PermissionAuthorizationManager`, and unused LMS authorization annotation files. Compile immediately after deletion.

- [ ] **Step 3: Add ratchets.** Contract tests fail if a new client-controlled security header, wildcard/alias permission, manual LMS permission assertion, or direct actor construction appears. Keep stable request ID and RLS tests.

- [ ] **Step 4: Run the complete focused security/LMS suite.**

  ```powershell
  .\mvnw.cmd "-Dtest=AuthenticatedActorTest,EffectiveGrantSetTest,AuthSessionTenantResolverTest,SecurityHardeningTest,LegacyAuthorizationInputContractTest,DefaultLmsAccessPolicyTest,LmsAssignmentCommandServiceTest,LmsRuntimeServiceAttemptRulesTest,LmsRuntimeControllerContractTest,LmsHomeworkWorkspaceGraphQlControllerTest,LmsClassWorkspaceGraphQlControllerTest,LmsAcademicRecordGraphQlControllerTest" test
  ```

### Task 12: Publish backend contracts and verify the handoff

**Files:** `contracts/openapi/erg-openapi.json`, `contracts/openapi/erg-openapi.sha256`, `src/main/resources/graphql/schema.graphqls`, `contracts/graphql/schema.graphqls.sha256`, contract tests, and the BE-034 workbook evidence. Contract files are generated artifacts and must be produced by the repository scripts, never hand-edited.

- [ ] **Step 1: Run Modulith and persistence gates.**

  ```powershell
  .\mvnw.cmd "-Dtest=ModulithVerificationTest,ArchitectureRatchetTest,FlywayMigrationAppendOnlyContractTest,FlywayUpgradePathIntegrationTest,TenantRlsIsolationIntegrationTest,TenantRlsMatrixIntegrationTest" test
  ```

- [ ] **Step 2: Export/check REST and GraphQL contracts.**

  ```powershell
  node scripts/export-openapi-contract.cjs --file contracts/openapi/erg-openapi.json --check
  node scripts/check-graphql-schema.cjs
  node --test scripts/openapi-contract-lib.test.cjs scripts/graphql-schema-contract-lib.test.cjs
  ```

  Expected result: exit 0; snapshots and checksums include the clean-break inputs and stable error codes.

- [ ] **Step 3: Run the backend completion gate.**

  ```powershell
  .\mvnw.cmd verify
  ```

  Expected result: exit 0 with full unit, integration, Modulith, migration, security, REST, and GraphQL evidence. If the full suite exceeds the command timeout, record the exact timeout and run the failed test groups independently; do not claim full verification.

- [ ] **Step 4: Update workbook evidence.** Record changed files, focused test count, migration/contract commands, `mvnw verify` result, and any remaining follow-on work. Keep `BE-034` “Đang làm” until every DoD item passes; do not mark complete because the implementation merely compiles.

- [ ] **Step 5: Prepare the handoff.** State `BE-034` result, changed files, evidence, residual risks, and next dependency `BE-052`. Do not stage, commit, push, publish contracts to external systems, or start FE work without explicit authorization and a successful backend contract gate.

## Definition of Done

`BE-034` is complete only when all of the following have evidence:

1. A single immutable `AuthenticatedActor` is the only authenticated application principal.
2. Bearer authentication discovers tenant server-side from the token hash; no client tenant/portal/current-scope input affects authorization.
3. Effective grants retain permission-to-scope binding, explicit denies win, and wildcard/alias semantics are absent.
4. Workspace capabilities are exact grants used only for workspace context.
5. Every protected LMS REST and GraphQL operation maps to one exact `LmsAction` and calls `LmsAccessPolicy`.
6. Teacher class/education-unit/resource scope, assignment selection, ownership, and update/delete checks are fail closed.
7. Student account mapping, enrollment/recipient ownership, attempt ownership, and quiz access are fail closed; no `orElse(true)` remains.
8. REST and GraphQL return equivalent decisions, stable codes, and request IDs.
9. V86/V87 are append-only, fresh/upgrade migration tests pass, and old migration files are unchanged.
10. OpenAPI/GraphQL export/checksum gates, focused tests, Modulith/RLS tests, and `mvnw verify` pass.
11. Workbook `BE-034` contains exact evidence and remains synchronized with this plan; follow-on tasks `BE-052`, `BE-053`, `BE-054`, and FE tasks are clearly separated.

## Explicit non-scope

This plan does not implement session bootstrap response fields, Auth UI, Authorization Code + PKCE callback UX, BFF cookie/CSRF flow, MFA/recovery screens, workspace chooser, PWA storage clearing, desktop callback handling, frontend route migration, billing, multi-tenant registry, or compatibility adapters for old clients. Those belong to the approved follow-on dependency sequence.
