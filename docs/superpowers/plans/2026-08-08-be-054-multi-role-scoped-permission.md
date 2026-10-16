# BE-054 — Multi-role account and scoped permission

## Objective

Make one account capable of holding several roles and organization/class/course
scopes while preserving the existing session and authorization contracts. A
verified session may expose capabilities for Admin, Teacher and Student
workspaces, but the workspace hint never grants access by itself.

## Constraints

- Keep the ERG single-organization deployment model; tenant and RLS semantics
  remain unchanged.
- Preserve existing role/permission names and JSON fields additively.
- No wildcard or portal alias may be treated as proof of authorization.
- Every grant/deny and scope decision is fail-closed and tenant-bound.
- Do not change password, token rotation or schema behavior in this slice.

## Implementation slice

1. Write RED tests for role/scope normalization, deny precedence, tenant
   mismatch, capability derivation and multi-role workspace mapping.
2. Introduce an immutable authorization capability policy in the authorization
   owner. It consumes typed role/scope values and returns canonical workspace
   capabilities plus scoped decisions; missing data returns deny.
3. Route existing principal/session capability derivation through the policy,
   retaining a named legacy adapter for old portal lists and serialized fields.
4. Add a small authorization snapshot contract for role assignments and scopes;
   avoid returning JPA entities from the public policy API.
5. Add architecture/source ratchets proving authorization does not use the
   requested hostname or an unverified portal alias as a grant.

## Verification

- Focused policy, principal/session, authorization and workspace capability tests.
- Architecture and Modulith verification.
- Compile plus REST/OpenAPI and GraphQL contract checks.
- Run full verification where the local Docker/PostgreSQL runner is available;
  classify Testcontainers-only failures instead of weakening security tests.

## Not in this slice

- BFF cookie/CORS/CSRF rollout (BE-053).
- Frontend route migration and UI switcher staging.
- New billing, generalized multi-tenancy or custom-domain behavior.
