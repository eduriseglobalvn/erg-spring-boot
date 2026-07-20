# Authentication Abuse, DDoS, and Risk-Based Security Design

## Objective

Protect the ERG authentication boundary against volumetric DDoS, credential stuffing, password spraying, account-lockout denial of service, spoofed client IP headers, stolen employee credentials, suspicious sessions, and refresh-token replay without changing LMS business payloads.

The design targets the current single physical server while remaining correct when Spring Boot is scaled to multiple containers. Controls that must be globally consistent use Redis state, not process memory.

## Security invariants

1. Untrusted clients cannot choose the client IP used by security controls.
2. Failed logins cannot permanently block a victim account solely because an attacker knows its email.
3. Login limits are enforced across all backend replicas and across IP, subnet, account, tenant, and device dimensions.
4. A valid password from a high-risk context does not immediately produce a fully trusted long-lived session.
5. Privileged employee and teacher accounts require MFA; high-risk events require step-up authentication.
6. Refresh-token replay revokes the token family and emits a high-severity security event.
7. Origin services cannot be reached directly from the public Internet in production.
8. Authentication responses do not reveal whether an account exists.

## Architecture

```text
Internet
  -> managed DDoS/WAF edge (Cloudflare or equivalent)
  -> origin firewall / authenticated tunnel
  -> Nginx connection and route rate limits
  -> Spring Security authentication abuse controls
  -> Redis 8.8 distributed counters and risk state
  -> PostgreSQL users, sessions, login attempts, security events
```

The managed edge handles attacks that would saturate the company's Internet link. Nginx sheds excess connections and cheap route abuse before Java. Spring Boot owns identity-aware limits, risk decisions, MFA state, session rotation, audit, and tenant-aware authorization.

## Trusted client IP boundary

Spring Boot will use a single `TrustedClientIpResolver`. It accepts forwarded headers only when the immediate peer belongs to configured trusted proxy CIDRs. It walks the `X-Forwarded-For` chain from right to left, removes trusted hops, validates every address, and selects the first untrusted hop. With an untrusted immediate peer, it ignores forwarded headers and uses `remoteAddr`.

Nginx replaces client-controlled forwarding headers at the public boundary. Production explicitly configures trusted edge proxy ranges. Controllers and auth services may not parse `X-Forwarded-For` themselves.

## Distributed login abuse limiting

The login guard runs before password hashing when safe and again after identity resolution. Redis counters cover independent windows and dimensions:

- source IP and subnet burst;
- normalized account identifier hash;
- tenant plus account;
- device fingerprint;
- number of distinct accounts attempted by one source;
- number of distinct sources attempting one account.

Keys contain hashes, never raw email addresses. Limits have short and long windows. Responses are generic `429` with `Retry-After`; account existence remains undisclosed. Redis errors fail closed for privileged login and use a deliberately lower-capacity local emergency limiter for ordinary accounts.

## Account-lockout policy

Five invalid attempts no longer set `User.status=BLOCKED`. Automated failures create a temporary authentication throttle/challenge state, preserve existing sessions unless compromise evidence exists, emit a security event, and allow recovery or MFA verification. Permanent blocking remains an explicit administrative or confirmed-compromise action.

This prevents attackers from denying access to teachers by repeatedly submitting a known email with bad passwords.

## Risk-based authentication

A bounded risk evaluator consumes server-observed signals:

- new device or fingerprint;
- new country/region/ASN when available;
- hosting, proxy, VPN, Tor, or poor IP reputation;
- impossible travel relative to a recent successful login;
- unusual login time;
- recent credential failures;
- source attempting many accounts;
- concurrent or rapidly changing session characteristics;
- privileged role and sensitive portal.

Risk outcomes are `ALLOW`, `STEP_UP`, and `DENY`. Missing geolocation or reputation data never silently lowers a previously high risk. Raw fingerprint and precise location data have retention limits; only necessary derived signals are retained in security events.

## MFA and step-up

Admin, employee, school manager, and teacher roles require MFA. WebAuthn/passkeys are the preferred phishing-resistant factor; TOTP is the operational fallback. Email OTP is limited to recovery and bootstrap flows and cannot authorize the highest-risk administrative actions.

Until the factor succeeds, the server issues only a short-lived, single-purpose challenge token. It does not issue the normal refresh token. Sensitive actions require recent step-up authentication even inside an existing session.

## Session and refresh-token protection

Refresh tokens rotate on every use and belong to a token family. Reuse of an already consumed token revokes the family and related active sessions, creates a `REFRESH_TOKEN_REUSE` high-severity event, and notifies the user/administrator. Password reset, MFA reset, account recovery, or confirmed compromise revokes other sessions.

Session monitoring compares coarse IP network, device, browser, location, timing, and privilege-sensitive behavior. High-risk drift triggers step-up or revocation rather than trusting a valid bearer token indefinitely.

## Nginx and edge controls

Nginx defines separate request zones for login/recovery, GraphQL, uploads, and general APIs, plus per-IP connection limits. Login and recovery receive low burst allowances; GraphQL keeps operation-aware limits in Spring as the authoritative inner control. Nginx returns JSON-compatible `429` and `Retry-After` where possible.

The production runbook requires proxied DNS or an authenticated tunnel, firewall allowlisting, closed backend/PostgreSQL/Redis ports, origin IP review, and a tested emergency bypass procedure that does not expose the origin casually.

## Observability and incident response

Metrics and bounded audit events cover allowed, challenged, denied, and throttled logins; MFA results; new devices; refresh replay; session revocation; Redis limiter failures; Nginx 429s; and WAF actions. Alerts use rates and tenant/account correlation rather than logging credentials or tokens.

Users can inspect and revoke active sessions. Administrators can revoke an employee's sessions and place an account into confirmed-compromise state. Runbooks distinguish DDoS, credential stuffing, account takeover, and false-positive recovery.

## Compatibility

Existing successful low-risk login payloads remain compatible. New challenge responses use an additive error/code contract and short-lived challenge identifier. No LMS, LCMS, GraphQL business payload changes are required. FE documentation is required for MFA enrollment, step-up, challenge, suspicious-login notification, and session-management flows.

## Verification

- Unit tests for trusted proxy parsing, IPv4/IPv6, spoofed and malformed forwarding chains.
- Regression test proving repeated invalid attempts cannot mutate a user to permanently blocked.
- Redis integration tests for all dimensions, replica consistency, TTL, concurrency, and failure policy.
- Authentication integration tests for generic responses, valid low-risk login, step-up, denial, MFA, token rotation, and replay.
- Nginx config validation and burst/connection tests through the edge port.
- Security tests for bypass variants and tenant isolation.
- k6 abuse scenarios separate from normal 5,000-user capacity tests.
- Native Image build and authenticated smoke tests.

## Explicit limits

Application and Nginx controls cannot absorb a volumetric attack that saturates the company's upstream bandwidth. Production readiness therefore depends on a managed external DDoS/WAF layer and an origin firewall/tunnel. Geolocation and IP reputation require a selected provider or edge-supplied, cryptographically trusted signals; the application will expose an interface and safe fallback without inventing location from untrusted headers.
