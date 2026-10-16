# ERG Auth, Workspace, and LMS Authorization Clean-Break Design

> Status: approved in conversation on 2026-08-03; implementation plan review is the next gate before production changes.
>
> Primary active task: `BE-034`. Follow-on tasks: `BE-052`, `BE-053`, `BE-054`, `ERG-088` through `ERG-097`.

## 1. Purpose

ERG needs one understandable authentication model for the shared login at `auth.erg.edu.vn`, one workspace-selection model for Admin, Teacher, Student, and CRM, and one fail-closed authorization model for LMS operations.

This design replaces the current compatibility-oriented model. It intentionally removes client-controlled tenant, portal, and current-scope headers rather than preserving adapters for them. Backend and frontend will cut over together against newly published REST and GraphQL contracts.

## 2. Problems confirmed in the current source

The current implementation has several independent representations of the same security facts:

- `ErgPrincipal.attributes` contains authorization scope values as untyped keys.
- `AccessScopeSet` is canonical in intent but is reconstructed through `AccessScopeAssembler` from legacy attributes.
- `UserAccessContext` duplicates managed/current education-unit and class fields alongside `AccessScopeSet`.
- REST scope selection may silently fall back to the first managed scope, while GraphQL may throw when given the same invalid selection.
- `ErgPrincipalService` derives portals from the login session, while the OIDC bridge derives portals from permission namespaces.
- `SecurityUtils.portalFromPath` turns API path naming into an authorization input.
- `SecurityConfig` and method annotations both contain business permissions, while some GraphQL controllers repeat manual checks again.
- LMS assignment writes verify tenant but do not consistently verify the teacher's class or education-unit scope.
- LMS student attempt authorization can default to allowed when no student mapping is found.

The result is difficult to reason about and contains real cross-class authorization risk inside one tenant.

## 3. Goals

1. Provide one ERG login at `auth.erg.edu.vn` for LMS and CRM.
2. Allow one account to hold multiple role assignments and use multiple workspaces without logging in again.
3. Make the authenticated actor the only source of tenant, account, session, and permission-to-scope grant facts.
4. Make workspace access explicit but separate from business-operation authorization.
5. Put LMS authorization at the LMS application boundary so REST and GraphQL cannot diverge.
6. Require exact permissions plus resource scope or ownership for every protected LMS operation.
7. Make missing identity mappings, invalid scopes, and foreign resources fail closed.
8. Keep authorization code and tests readable without introducing a generic policy DSL.
9. Publish synchronized REST and GraphQL contracts and migrate the canonical frontend in the same release train.

## 4. Non-goals

- Do not create a separate authentication microservice.
- Do not create another frontend repository for the Auth UI.
- Do not build generalized multi-tenancy, a tenant registry, custom domains, billing, or licensing.
- Do not implement the desktop application; only preserve OIDC/PKCE compatibility for it.
- Do not use hostname, route prefix, role name, or frontend menu visibility as proof that an API action is authorized.
- Do not add a generic expression language, policy DSL, or dynamically interpreted authorization rules.
- Do not preserve the old tenant/portal/current-scope headers through compatibility adapters.
- Do not edit previously published Flyway migrations. Every database change is append-only.

## 5. Domain topology and ownership

| Surface | Responsibility | Canonical owner |
|---|---|---|
| `erg.edu.vn` | Public ERG site | `erg` |
| `auth.erg.edu.vn` | Login, MFA, recovery, SSO, consent/error UI | Auth module in `erg-spring`; Auth entry point in `erg-system` |
| `lms.erg.edu.vn/admin` | LMS administration | `erg-system` + backend modules |
| `lms.erg.edu.vn/teacher` | Teacher workspace | `erg-system` + LMS backend module |
| `lms.erg.edu.vn/student` | Student workspace | `erg-system` + LMS/ELEARNING backend modules |
| `crm.erg.edu.vn` | CRM workspace | `erg-system` + CRM backend module |
| `api.erg.edu.vn` | Native/integration API boundary | `erg-spring` |

Web and PWA traffic should normally call a same-origin `/api` BFF/proxy on their application host. `api.erg.edu.vn` remains available for approved native clients and integrations. This avoids broad credentialed CORS for normal browser use.

`auth.erg.edu.vn` is a deployment surface, not a new microservice. The existing Spring application keeps the canonical auth/session implementation, and the existing frontend repository provides a dedicated Auth entry point.

## 6. Canonical authenticated actor

The backend will use one immutable actor value throughout authenticated application code. Permissions and scopes must remain bound together; independently unioning all permissions and all scopes would let a permission from one role assignment use a scope from another assignment.

```java
public record AuthenticatedActor(
        AccountId accountId,
        SessionId sessionId,
        TenantId tenantId,
        Set<RoleName> roles,
        EffectiveGrantSet grants,
        Set<PermissionCode> deniedPermissions
) {}

public record EffectiveGrant(
        PermissionCode permission,
        AccessScopeSet scopes
) {}
```

Rules:

- `tenantId` is established by verified authentication. ERG currently resolves it server-side as the single organization `erg`.
- Roles are immutable facts used for administration and audit. Application policy does not use `if role == teacher`.
- Every effective exact permission grant carries the `AccessScopeSet` produced by the role assignment that granted it.
- Exact denies are actor-wide in this release and win over all matching grants.
- `AccessScopeSet` is the only authorization-scope representation. It contains managed constraints keyed by scope type; it does not contain a client-selected current scope.
- A platform-wide grant uses an explicit scope mode in `AccessScopeSet`, not a synthetic `"*"` scope identifier.
- Profile attributes such as display name and email do not live in the authorization actor.
- `ErgPrincipal.attributes`, `portals`, and duplicated legacy access-context fields are removed.
- Missing tenant, account, session, grant set, or deny set prevents actor construction.

`platform.super_admin` is the only explicit system-level bypass capability. Business code does not recognize `*`, namespace wildcards, or aliases such as `lms.manage`.

## 7. Workspace capabilities

Workspace access is derived from exact effective grants:

| Workspace | Required capability | Default path |
|---|---|---|
| Admin | `lms.workspace.admin.access` | `/admin` |
| Teacher | `lms.workspace.teacher.access` | `/teacher` |
| Student | `lms.workspace.student.access` | `/student` |
| CRM | `crm.workspace.access` | `crm.erg.edu.vn` |

Workspace capabilities serve two purposes only:

1. Build the session bootstrap response and frontend workspace chooser.
2. Require the correct product context before a module evaluates an operation.

They do not authorize business actions. A teacher who can enter `/teacher` still needs `lms.assignment.create`, `lms.grade.write`, or another exact operation permission and must be within the resource scope.

After login:

- One allowed workspace redirects directly to it.
- Multiple allowed workspaces use the last valid workspace preference; otherwise the frontend shows the chooser.
- A user can switch workspace without authenticating again.
- A deep link to a forbidden workspace is rejected and redirected to an allowed workspace or chooser.
- Permission revocation invalidates or refreshes the bootstrap and removes access to the current workspace.

The selected workspace is not sent as a trusted authorization header.

## 8. Shared Auth and SSO flow

### 8.1 Browser and PWA

1. The application redirects to `auth.erg.edu.vn` with an allowlisted return URI, state, nonce, and PKCE challenge.
2. Auth performs login, account-state checks, and MFA policy.
3. Auth issues an authorization code. Tokens never appear in the browser URL.
4. The application BFF exchanges the code server-side.
5. The application receives a host-only `HttpOnly`, `Secure`, `SameSite=Lax` session cookie.
6. The application requests session bootstrap and routes to the allowed workspace.

The central Auth SSO cookie is host-only to `auth.erg.edu.vn`. LMS and CRM each use their own host-only application session cookie. ERG must not use a shared `.erg.edu.vn` application cookie.

Switching from LMS to CRM may perform a short authorization redirect through Auth, but the existing Auth SSO session means the user does not enter credentials again.

### 8.2 CSRF and browser storage

- Session cookies are HttpOnly and inaccessible to JavaScript.
- State-changing BFF calls require a same-origin CSRF token using a documented double-submit or response-header pattern.
- Access and refresh tokens are never stored in `localStorage`, `sessionStorage`, IndexedDB, TanStack persisted cache, service-worker cache, or URL parameters.
- Logout clears application query cache and account-scoped offline data.
- Multi-tab logout/session changes may use `BroadcastChannel`, but messages contain only a session-state event, never a token.

### 8.3 Desktop compatibility

A future desktop client uses the same issuer at `auth.erg.edu.vn` with Authorization Code + PKCE, an allowlisted native callback, OS secure storage, and no browser application cookie reuse.

## 9. Session bootstrap contract

The authenticated bootstrap response contains only facts the frontend needs to render and navigate:

```json
{
  "account": {
    "id": "account-id",
    "displayName": "User name"
  },
  "session": {
    "id": "session-id",
    "expiresAt": "2026-08-03T12:00:00Z"
  },
  "workspaces": ["TEACHER", "STUDENT"],
  "grants": [
    {
      "permission": "lms.assignment.read",
      "scopes": {
        "EDUCATION_UNIT": ["unit-1"],
        "CLASS": ["class-1"]
      }
    },
    {
      "permission": "lms.assignment.submit",
      "scopes": {
        "SELF": ["account-id"]
      }
    }
  ],
  "defaultWorkspace": "TEACHER"
}
```

The frontend may use scoped grants to hide unavailable controls and populate selectors, but all API calls are independently authorized on the server. It must not flatten grants into a permission/scope cross-product.

The bootstrap does not accept `tenantId`, portal, hostname-derived role, or current-scope input from the client.

## 10. Clean-break API contract

The coordinated release removes these authenticated application inputs:

- `X-Tenant-ID`
- `X-Portal`
- `X-Current-EducationUnit-ID` and aliases
- `X-Current-Class-ID` and aliases
- authenticated GraphQL `tenantId` input fields

Resource identifiers remain ordinary business input. For example, `classId`, `assignmentId`, `attemptId`, `studentId`, and `educationUnitId` are needed to address a resource. The server loads the resource under the actor tenant and validates scope or ownership.

Old clients are intentionally unsupported after the coordinated cutover. The old headers are not silently honored, and the old contract is not preserved through source compatibility adapters.

The contract release sequence is:

1. Implement and verify the new backend behavior and snapshots.
2. Publish new OpenAPI and GraphQL contract files with checksums.
3. Sync and regenerate frontend transport types.
4. Migrate frontend calls and route/bootstrap logic.
5. Run BE, FE, and cross-repository gates.
6. Cut over Auth/LMS/CRM together.

## 11. LMS application-boundary authorization

The LMS module owns a small, typed policy boundary:

```java
public interface LmsAccessPolicy {
    void require(AuthenticatedActor actor, LmsAction action, LmsResourceRef resource);
}
```

`LmsAction` maps one-to-one to an exact permission code. `LmsResourceRef` is a sealed LMS-owned resource reference such as education unit, class, assignment, student, attempt, quiz, or course. This is not a generic platform policy DSL.

REST and GraphQL controllers:

- Resolve the same `AuthenticatedActor` from the security context.
- Parse and validate transport input.
- Call the same LMS application operation.
- Do not contain business permission strings or manual scope branching.

The LMS use case begins by calling `LmsAccessPolicy`. Authorization succeeds only when at least one effective grant has the exact requested permission and its own bound scope covers the resource. The policy may load a minimal authorization projection through LMS-owned query ports. Business repositories and entities remain internal.

`SecurityConfig` keeps only public endpoints, authentication endpoints, infrastructure endpoints, CSRF/session setup, authenticated fallback, and final deny-all. It does not contain LMS, LCMS, CRM, reporting, or calendar operation permissions.

Controller meta-annotations and repeated GraphQL `assertPermission` calls are removed as business-policy sources. The application boundary is the single source of operation authorization.

## 12. LMS authorization rules

### 12.1 Teacher

Teacher operations require all of:

1. `lms.workspace.teacher.access`.
2. The exact operation permission.
3. Class, education-unit, course, or resource scope appropriate for the addressed resource.

Examples:

- Creating an assignment validates every selected class, group, student, and quiz resource before writing anything.
- Updating recipients revalidates all new recipients and the existing assignment scope.
- Updating or deleting an assignment requires ownership or managed class scope according to the operation policy.
- Reading progress requires assignment class scope or permitted assignment ownership.
- Attendance and grade writes require class scope and subject/course assignment where that data exists.

Having an exact permission does not grant access to every resource in the tenant.

### 12.2 Student

Student operations require all of:

1. `lms.workspace.student.access`.
2. The exact self-operation permission.
3. A verified mapping from the authenticated account to an LMS student.
4. Enrollment, assignment-recipient, or attempt ownership for the addressed resource.

Missing student mapping is denied. Unknown assignment recipients are denied. The implementation must remove any `orElse(true)` or equivalent permissive fallback.

A student cannot gain access from a managed teacher class scope, and a teacher cannot use a student self permission without a verified student identity and ownership.

### 12.3 Admin

Admin operations require all of:

1. `lms.workspace.admin.access`.
2. The exact administration permission.
3. Organization or education-unit scope unless the actor has `platform.super_admin`.

Admin workspace access alone is not global authority.

### 12.4 Multi-role account

A single account may have Teacher and Student capabilities at the same time. The actor carries a set of scoped effective grants, not independent unions of permissions and scopes. Teacher permission granted for class A cannot consume a Student or Admin scope for class B. Each operation evaluates one exact grant against the addressed resource; the frontend-selected workspace does not elevate the actor.

## 13. Permission catalog and data migration

New append-only Flyway migrations start after the current highest published version (`V85` at design time). They will:

- Add the four workspace capability permissions.
- Add or reconcile exact LMS permissions required by application actions.
- Grant workspace capabilities and exact permissions to the correct role templates.
- Revoke or mark obsolete the generic `lms.portal.access` grant according to the existing schema's supported lifecycle field.
- Remove role grants that relied on aliases or wildcard behavior.
- Preserve historical migration files such as `V4` and `V52` unchanged.

The runtime does not recognize wildcard or alias semantics after cutover even if historical rows remain for audit.

## 14. Error semantics

REST and GraphQL use the same stable codes and request correlation:

| Condition | HTTP meaning | Stable code | Disclosure rule |
|---|---:|---|---|
| Missing or invalid session | 401 | `AUTHENTICATION_REQUIRED` | No account detail |
| Session revoked/replaced | 401 | `AUTH_SESSION_REVOKED` or `AUTH_SESSION_REPLACED` | No token detail |
| MFA required | 403 | `MFA_REQUIRED` | Return next Auth action only |
| Workspace unavailable | 403 | `WORKSPACE_ACCESS_DENIED` | Return allowed workspace list only from bootstrap flow |
| Exact permission missing | 403 | `PERMISSION_DENIED` | Do not disclose role internals |
| Known resource outside scope/ownership | 404 | `RESOURCE_NOT_FOUND` | Prevent resource enumeration |
| Invalid resource selection/input | 400 | `VALIDATION_ERROR` | Field-safe details |
| Optimistic version/idempotency conflict | 409 | `CONFLICT` | Return safe conflict metadata |
| CSRF failure | 403 | `CSRF_VALIDATION_FAILED` | No session/token content |

GraphQL error extensions carry the same code and request ID as REST.

## 15. Frontend Auth architecture

The canonical frontend repository owns one Auth entry point deployed at `auth.erg.edu.vn` and one shared auth platform package used by LMS and CRM.

Responsibilities:

- Start login and MFA/recovery flows.
- Handle allowlisted callback/return navigation without reading tokens.
- Fetch and validate session bootstrap through generated contract types.
- Expose account, session state, allowed workspaces, and scoped effective grants without flattening their bindings.
- Resolve one workspace directly or show the chooser for multiple workspaces.
- Keep workspace route guards and menu visibility consistent with bootstrap.
- Refresh session and handle revocation without redirect loops.
- Broadcast logout/session-state changes across tabs without token data.
- Clear account-scoped Query cache, persisted cache, service-worker data, IndexedDB drafts, and offline outbox state on logout or account change.

Frontend guards improve UX only. They never replace backend authorization.

## 16. Security controls

- Authorization Code + PKCE for browser/PWA and future desktop clients.
- MFA policy for privileged roles and risk-based cases defined by the existing Auth security design.
- Host-only cookies; no shared `.erg.edu.vn` application cookie.
- Secure, HttpOnly session cookie and same-origin CSRF protection.
- Strict redirect URI and `returnTo` allowlists; no open redirects.
- Session fixation prevention, rotation after authentication/privilege change, bounded idle/absolute expiry, and revocation.
- Rate limiting and lockout for login, MFA, recovery, refresh, and callback abuse.
- Audit login success/failure, MFA, refresh, logout, session revocation, permission change, and workspace-capability change.
- Never log tokens, authorization codes, CSRF values, passwords, recovery secrets, or unnecessary PII.
- Bounded metric labels; account, token, session, class, and resource IDs are not metric labels.

## 17. Test strategy

Implementation follows test-first RED-GREEN-REFACTOR cycles.

### 17.1 Backend unit and module tests

- Actor construction rejects missing account, session, tenant, scoped grants, or denies.
- Permission matching is exact; aliases and wildcards do not grant access.
- Explicit deny wins over an exact grant.
- A permission from one role assignment cannot use the scope from another role assignment.
- Workspace derivation produces Admin, Teacher, Student, and CRM correctly.
- LMS action mapping covers every protected public operation.
- Teacher class and education-unit scope decisions are deterministic.
- Student ownership requires a real account-to-student mapping.
- `platform.super_admin` is the only system bypass.

### 17.2 Backend integration and contract tests

- No authenticated API trusts tenant, portal, or current-scope headers.
- Authenticated GraphQL inputs no longer contain tenant IDs.
- Teacher scoped to class A cannot read or mutate class B.
- Assignment create/update cannot select an out-of-scope class, group, student, or quiz.
- Student A cannot read or submit Student B's assignment or attempt.
- Missing student mapping is denied.
- Multi-role account can perform only actions supported by one matching scoped grant and addressed-resource facts.
- REST and GraphQL return equivalent decisions and stable error codes.
- Session rotation, revocation, replacement, expiry, MFA, CSRF, and redirect allowlist behavior pass.
- OpenAPI and GraphQL snapshots/checksums remain synchronized.
- Modulith verification and PostgreSQL/RLS tests pass.
- Full `mvnw verify` passes.

### 17.3 Frontend tests

- One workspace redirects directly.
- Multiple workspaces use a valid last preference or show the chooser.
- Forbidden deep links return to an allowed workspace without loops.
- Permission revocation ejects the current workspace after session refresh.
- No token is written to browser storage, URL, logs, or telemetry.
- Logout/account switch clears all account-scoped online and offline data.
- Multi-tab logout works without sharing token material.
- PWA installation/update preserves the valid session and drafts.
- Auth callback rejects invalid state, nonce, return target, or expired flow.

### 17.4 Cross-repository release gates

- Backend contract export/checksum gates.
- Frontend contract sync/generation/drift gate.
- Backend full verification.
- Frontend lint, typecheck, tests, build, route, dependency, bundle, and governance gates.
- E2E Admin, Teacher, Student, multi-role, and CRM login/workspace/error flows.

## 18. Implementation decomposition

### `BE-034` — authorization foundation and LMS correctness

- Replace legacy principal/access-context representations with `AuthenticatedActor` and canonical scopes.
- Remove client-controlled tenant/portal/current-scope inputs from application authorization.
- Remove wildcard/alias business authorization.
- Add LMS application-boundary policy and close teacher/student scope and ownership gaps.
- Reduce `SecurityConfig` to platform concerns.

### `BE-052` — session bootstrap contract

- Publish account/session/workspaces/scoped effective grants/default workspace.
- Provide server-validated workspace preference behavior.

### `BE-053` — Auth domain, BFF, cookies, CSRF, and SSO

- Deliver the browser/PWA Auth flow for `auth.erg.edu.vn`.
- Apply rotation, revocation, redirect allowlist, MFA, rate-limit, and audit controls.

### `BE-054` — multi-role scoped assignments

- Normalize multiple role assignments into permission-to-scope effective grants without cross-role scope leakage.
- Publish stable administration and audit behavior.

### `ERG-088` through `ERG-097` — frontend domain/Auth/workspace migration

- Implement Auth entry point, domain resolver, routes, callback, session bootstrap, guards, chooser, and workspace switcher.
- Remove legacy portal keys/headers and migrate all generated contract consumers.
- Run cross-role and cross-domain E2E verification.

Only one code-changing task is active per repository checkout. `BE-034` completes before `BE-052`; frontend contract migration starts only after the backend contract is published and pinned.

## 19. Rollback and operational behavior

This is a coordinated clean break, so rollback is release-level rather than a runtime compatibility layer:

- Preserve the previous deployable FE and BE artifacts.
- Database migrations remain additive and forward-compatible with the previous schema where practical.
- If cutover gates fail, restore both previous FE and BE artifacts together.
- Do not partially roll back only frontend or backend after the contract boundary changes.
- Do not re-enable legacy tenant/portal/current-scope headers as an emergency bypass.

## 20. Definition of done

The design is implemented only when:

1. One shared Auth login supports Admin, Teacher, Student, multi-role, and CRM workspaces.
2. Tenant and permission-to-scope grants come only from verified server-side identity, scoped role assignments, and resource facts.
3. No legacy tenant/portal/current-scope input influences authorization.
4. Exact permissions and typed LMS resource policy protect every LMS operation.
5. Cross-class teacher access and cross-student ownership attempts are denied.
6. Missing identity mappings fail closed.
7. REST and GraphQL decisions and errors are equivalent.
8. Frontend uses generated bootstrap/contracts and stores no token material.
9. Offline/account data is isolated and cleared correctly.
10. Backend, frontend, contract, security, governance, and E2E gates pass with evidence in the canonical workbook.
