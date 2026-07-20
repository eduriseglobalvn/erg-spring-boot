# Authentication Risk, MFA, and Session Handling

## Production authentication

When the enterprise-auth profile is enabled, FE must use the OIDC/Keycloak flow. The local `/api/v1/auth/login` password endpoint is disabled in that profile. Redirect the browser to `/oauth2/authorization/erg-api` and return through the configured callback.

## MFA requirement

Teacher, employee, manager, and admin roles must complete MFA at the identity provider. The ID token must contain an `amr` value such as `otp`, `totp`, `webauthn`, or `passkey`, or an `acr` value proving MFA/strong authentication. Spring rejects privileged OIDC sessions without that evidence with HTTP 403 and `MFA_REQUIRED`.

The FE must show a clear “complete MFA at your organization sign-in” state and must not retry the same API request indefinitely.

## Login abuse response

For login abuse or distributed throttling, the API returns HTTP 429 with:

```http
Retry-After: 60
```

The FE should display a temporary retry message and avoid immediate retry loops. Do not reveal whether an email exists.

## Suspicious session response

When a session is revoked due to `REFRESH_TOKEN_REUSE`, the next authenticated request returns the normal invalid-token response. Clear local access/refresh tokens, terminate the local session, and restart OIDC login. Do not silently retry the old refresh token.

Users should have a session list and a “revoke this session / revoke all sessions” action. Show device, browser, approximate location, and last active time without exposing raw security data.

No existing LMS, LCMS, REST, or GraphQL business payload was changed by this security boundary.
