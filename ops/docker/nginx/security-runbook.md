# Authentication and DDoS Security Runbook

## Required production topology

Public DNS must point to a managed DDoS/WAF proxy or an authenticated tunnel. The physical server must not expose Spring Boot, PostgreSQL, or Redis ports to the Internet. The origin firewall accepts only the selected edge provider or tunnel connector.

Set `HTTP__TRUSTED_PROXY_CIDRS` to the exact proxy/container hops. Do not use `0.0.0.0/0` or leave a public origin reachable while forwarding headers are enabled.

## Required environment settings

```env
ERG_AUTH_LOGIN_RATE_LIMIT_ENABLED=true
ERG_AUTH_LOGIN_RATE_LIMIT_SOURCE_PER_MINUTE=30
ERG_AUTH_LOGIN_RATE_LIMIT_ACCOUNT_PER_MINUTE=10
ERG_AUTH_LOGIN_RATE_LIMIT_DEVICE_PER_MINUTE=20
ERG_GRAPHQL_RATE_LIMIT_ENABLED=true
ERG_GRAPHQL_RATE_LIMIT_REDIS_ENABLED=true
ERG_GRAPHQL_RATE_LIMIT_FAIL_CLOSED_ON_REDIS_ERROR=true
HTTP__TRUSTED_PROXY_CIDRS=<exact-edge-and-nginx-cidrs>
```

## Incident: credential stuffing

1. Confirm WAF and Nginx 429 rates.
2. Correlate source, subnet, tenant, account, device, and security events; do not block only one IP.
3. Apply temporary edge challenge/block rules.
4. Revoke sessions only for confirmed compromise or refresh-token reuse.
5. Never permanently lock accounts solely from attacker-controlled failed passwords.
6. Notify affected users and require password reset/MFA step-up when compromise evidence exists.

## Incident: refresh-token replay

The application emits `REFRESH_TOKEN_REUSE` with `CRITICAL` severity and revokes active sessions for the account. Preserve the security event, inspect the session/device/IP records, invalidate credentials if compromise is confirmed, and notify the user.

## Incident: origin exposure

Immediately remove direct DNS records, rotate the origin IP if it was exposed, close public backend/data ports, and allow only edge proxy ranges or the tunnel. A CDN alone is not sufficient if the attacker knows and can reach the origin IP directly.

## MFA status

Do not claim MFA protection until a real WebAuthn/passkey or TOTP verification flow is deployed and tested. Password-only login from a new device must not be described as MFA-protected.
