# Multi-role and scoped authorization

The authenticated principal may contain multiple backend-assigned roles and
scope sets. A request path/domain only identifies the workspace being asked
for; it never grants that workspace.

`AuthorizationCapabilityPolicy` derives canonical capabilities (`ADMIN`,
`TEACHER`, `STUDENT`, `CRM`) from verified roles and permissions. It ignores
the legacy `portals` list and client headers. Legacy portal values are retained
only as an adapter output for old JSON clients. A wildcard permission grants
platform-wide capabilities only when the verified `system.super_admin`,
`erg_super_admin` or `global_admin` role is also present; a role name alone
does not grant a permission. Explicit denies remove a capability before any
grant is considered.

Tenant equality is checked before scope evaluation. Non-platform principals
must have the requested ID in their normalized `AccessScopeSet`; missing or
unknown scope data denies by default. The policy returns immutable capability
lists so FE workspace switching can display available workspaces without
becoming an authorization authority.

## Compatibility boundary

`ErgPrincipal.portals` and the old `X-Portals` header remain serialized for
legacy clients, but `ErgAuthenticationFilter` and `ErgPolicyService` no longer
use them to authorize a request. This is intentionally a clean security
boundary: aliases such as `lcms`, `lms` and `elearning` map the requested route
to a canonical workspace, while the verified role/permission snapshot decides
whether access is allowed.
