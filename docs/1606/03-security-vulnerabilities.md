# Security Vulnerability Audit

## 1. GraphQL Introspection Can Be Enabled via Environment Variable — 🟡 MEDIUM

**File:** `application.yml` line 91

```yaml
spring:
  graphql:
    schema:
      introspection:
        enabled: ${SPRING_GRAPHQL_SCHEMA_INTROSPECTION_ENABLED:${ERG_GRAPHQL_INTROSPECTION_ENABLED:false}}
```

The default is `false` (good), but it can be toggled ON via environment variables. In production, this should be **hardcoded to false** or require a separate secret to enable.

The `GraphQlGuardFilter` also checks for introspection keywords (`__schema`, `__type`) in the query string (line 194), which provides defense-in-depth. However, the Spring GraphQL framework's own introspection support is controlled by the property above — if both are enabled, the filter is bypassed.

**Risk:** An attacker who gains access to env vars can enable introspection and discover the entire schema.

**Fix:** In production profile, force introspection off:
```yaml
# application.yml — prod profile
spring:
  graphql:
    schema:
      introspection:
        enabled: false  # hardcoded, no env override in prod
```

## 2. Rate Limiter Uses In-Memory ConcurrentHashMap — 🟡 MEDIUM

**File:** `platform/graphql/GraphQlRateLimiter.java`

```java
private final Map<GraphQlRateLimitKey, WindowCounter> counters = new ConcurrentHashMap<>();
```

**Issues:**
- **Not distributed:** If running multiple instances, each instance has its own rate limit state. An attacker can distribute requests across instances to bypass limits.
- **No expiration:** Stale counters are only cleaned when `MAX_BUCKETS (20,000)` is exceeded. Under light load, stale entries persist indefinitely.
- **Fixed-window algorithm:** The current implementation uses a fixed-window rate limiter which allows burst traffic at window boundaries (2x the limit in a short period).

**Fix:** Use Redis-backed rate limiting for distributed deployments.

## 3. CORS Allows Credentials with Wildcard-Like Origins — 🟡 MEDIUM

**File:** `application.yml` lines 223–228

The CORS configuration allows credentials (`allow-credentials: true`) with a list of specific origins — this is **correctly configured**. However:
- The origins are configurable via env vars, meaning someone could add `*` or overly broad domains
- Port `:3001` is included for production domains — unusual for production (typically 443/80)

**Fix:** Validate CORS origins at startup (reject `*` when `allow-credentials` is true). Remove port numbers from production domains in default config.

## 4. Missing CSRF Protection When Cookie Auth Is Enabled — 🟡 MEDIUM

**File:** `platform/security/SecurityConfig.java` line 37, `application.yml` lines 196–199

CSRF is disabled (acceptable for JWT bearer API). But the application also supports **cookie-based auth**:

```yaml
auth:
  cookie:
    enabled: true
    secure: false   # Cookie NOT marked as Secure
    same-site: lax
```

When cookie-based auth is enabled, CSRF protection should be active. With `secure: false`, cookies are sent over HTTP, making them vulnerable to interception.

**Fix:** Set `auth.cookie.secure: true` in production. Consider adding CSRF protection when cookie auth is enabled.

## 5. Stack Trace Exposure in Dev Profile — 🟡 MEDIUM

**File:** `application.yml` line 82

```yaml
include-stacktrace: on_param
```

Anyone can append `?trace=true` to any API URL to see the full Java stack trace. The prod profile correctly disables this, but the default `dev` profile allows it.

**Fix:** Add a startup warning if stack traces are enabled and `APP__ENV` is not `development`.

## 6. Auth Cookie Secure Flag Defaults to False — 🟡 MEDIUM

Already covered in #4 — `auth.cookie.secure: false` by default means auth cookies can be sent over plain HTTP, enabling man-in-the-middle session theft.

## 7. Sensitive Variable Detection Over-Aggressive — 🟢 LOW

**File:** `platform/graphql/GraphQlGuardFilter.java` lines 303–315

The `contains()` check for `password`, `secret`, `token` would reject legitimate variables like `resetTokenRequested`. Since REST auth endpoints bypass GraphQL guard filtering, this is low risk.

---

## Summary

| # | Issue | Severity | Effort | Priority |
|---|-------|----------|--------|----------|
| 1 | Introspection toggleable via env | Medium | Small | P2 |
| 2 | In-memory rate limiter not distributed | Medium | Medium | P2 |
| 3 | CORS origins configurable, may include `*` | Medium | Small | P3 |
| 4 | Cookie secure flag defaults to false | Medium | Small | P1 |
| 5 | Stack traces on `?trace=true` in dev | Medium | Small | P2 |
| 6 | Cookie not Secure over HTTP | Medium | Small | P1 |
| 7 | Sensitive variable detection over-aggressive | Low | Small | P3 |

**Overall Security Rating:** Good — Solid foundations (JWT auth, permission-based field authorization, guard filters, rate limiting, introspection disabled, query depth/complexity limits). Issues are mostly configuration-related.