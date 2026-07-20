# Backend Security Remediation Design

## Scope

Remediate the eight reportable findings from standard Codex Security scan `0e6f55bd-5eb0-4a31-99c9-5ffb421034a4` without changing unrelated backend behavior or overwriting the existing dirty worktree.

## Security invariants

1. A delegated administrator can grant only roles permitted by an explicit actor privilege ceiling; mutable role names cannot manufacture system authority.
2. An authorization decision evaluates explicit DENY across the complete requested alternative set before any grant, identically for REST and GraphQL.
3. Every employee and authorization object read/write is scoped to the authenticated tenant and education-unit boundary before repository access.
4. Failed-login telemetry, lockout, and security-driven session revocation remain durable when the login request fails.

## Architecture

### RBAC policy boundary

Add a focused policy component that owns protected-role identifiers, reserved role names, actor role ceilings, and full-alternative permission evaluation. `AuthorizationService`, `UserService`, REST authorization, and GraphQL authorization call this shared policy instead of implementing independent string rules.

System roles remain readable for compatibility, but ordinary create/update endpoints cannot create or rename roles into the `system.*` namespace. Assignments to protected roles require an authenticated actor that already holds equivalent platform authority.

### Tenant object boundary

Controller methods pass the authenticated `ErgPrincipal` tenant and scope to service methods. Repository methods resolve users using tenant-qualified predicates. Bulk operations reject the entire request if any requested user is outside scope or missing; read APIs return not-found for foreign targets to avoid tenant enumeration.

### Authentication failure boundary

Move failed-login recording and the threshold-triggered enforcement writes into a dedicated Spring bean using `REQUIRES_NEW`. `AuthService.login` remains the decision coordinator, but failure state commits independently before `AuthFailure` leaves the outer transaction.

## Compatibility and errors

- Existing successful login, password hashing, session creation, response DTOs, and error types remain unchanged.
- Existing platform super-admin flows remain available through explicit protected-role authorization.
- Foreign-tenant and unknown targets use the existing `ResourceNotFoundException` semantics.
- Invalid protected role creation/update/assignment uses `BadRequestException` or `UnauthorizedException` according to whether the input is structurally forbidden or the actor lacks authority.

## Verification

Each finding gets a regression test that fails before the production change. Focused service/security tests prove the invariant; controller contract tests prove tenant/actor data reaches the service. A Spring transaction integration test proves failure records survive rollback. The final gate runs focused Maven tests, then the complete test suite, followed by a diff-based bypass review.

## Deliberate non-goals

- No frontend changes.
- No unrelated security refactor.
- No Deep Scan or goal tooling.
- No automatic commit, reset, or cleanup of pre-existing worktree changes.
