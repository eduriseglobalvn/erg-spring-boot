# BE-053: BFF cookie, CORS, CSRF and logout hardening

## Goal

Make the browser/PWA authentication transport a server session with an HttpOnly,
Secure, SameSite cookie and an explicit CSRF token. The BFF must not need client
tenant/portal headers or bearer tokens, and logout must invalidate the HTTP
session. Desktop/PKCE remains a separate future transport.

## Local slice

- Keep `server.servlet.session.cookie` pinned to the `__Host-erg_session` contract.
- Expose `/api/v1/auth/csrf` only through the Spring CSRF repository and protect
  mutating requests when BFF mode is enabled.
- Add token-free BFF logout that invalidates the HTTP session and clears the
  security context.
- Make BFF CORS headers explicit and remove `Authorization`, `X-Tenant-ID`,
  `X-Portal`, current-scope and trusted-identity headers from the BFF allowlist.
- Add focused tests for wildcard/credential rejection, BFF header projection,
  logout/session invalidation and no token material in the session contract.

## Deferred dependency

Production DNS/reverse-proxy/cookie canary and staging OIDC validation remain
blocked by `BE-027` infrastructure authority. This slice does not claim that
staging gate is complete.

## Verification

1. Run focused CORS, SecurityConfig/BFF, logout and auth tests.
2. Run `mvnw.cmd -DskipTests compile`.
3. Run OpenAPI/GraphQL checks and the FE contract gate after any public DTO change.
4. Record full `mvnw verify` infrastructure/auth architecture blockers in the
   workbook instead of masking them.
