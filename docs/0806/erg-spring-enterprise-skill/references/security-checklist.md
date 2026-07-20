# Security checklist for erg-spring

## Trust boundary

- Public clients must not be able to set:
  - `X-User-ID`
  - `X-Roles`
  - `X-Permissions`
  - `X-Denied-Permissions`
  - `X-Managed-EducationUnit-IDs`
  - `X-Managed-Class-IDs`
- Header principal is allowed only in test/internal gateway profile and must be disabled by default.
- Bearer token must map to an active server-side session.
- Revoked/expired session and inactive user must fail.

## Scope

- Managed scope comes from session/database/principal attributes only.
- Requested scope can come from request header/body/query/GraphQL input, but must be validated.
- Empty managed scope is deny-by-default.
- Global scope must be explicit: `*` or equivalent global role/permission.
- Teacher access must be limited by school/class.
- Student access must be self-only unless explicitly delegated.

## Permission

- Route permission is not enough for GraphQL.
- GraphQL must check field-level permissions in resolver/read-model service.
- Deny override wins.
- Sensitive permissions:
  - `rbac.permission.read`
  - `users.manage`
  - `lms.grade.read`
  - `lms.attendance.read`
  - `lms.scope.read`
  - `audit.read`
  - `hoclieu.asset.download`

## Sensitive data

Never return or log:

- password hash.
- access/refresh token or token hash.
- raw credentials from import after initial controlled display.
- Authorization header.
- cookies.
- API keys/secrets.
- raw quiz answers unless explicitly authorized.
- denied permissions unless user has permission to inspect effective access.

## Required tests

- No token -> 401.
- Spoofed header auth in production -> 401.
- Wrong portal -> 403.
- Tenant mismatch -> 403/404.
- Teacher cannot query class outside managed scope.
- Student cannot query another student dashboard/attempt.
- User without RBAC permission cannot inspect effective permissions.
- Deny override beats wildcard.
