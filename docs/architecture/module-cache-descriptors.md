# Module-owned cache descriptors

`CacheDescriptor` and `CacheDescriptorRegistry` remain platform contracts. The
descriptor values and Native reflection graphs are contributed by bounded-context
contributors, while `CacheConfig` only aggregates them and rejects duplicates.

The migration preserves the existing 26 cache names, TTLs, key semantics,
tenant/scope requirements, L2 allowlist and invalidation behavior. Missing or
duplicate descriptors fail at registry construction; a cache cannot silently
fall back to an untyped JPA/entity value.

`LmsCacheDescriptorContributor` owns LMS/LCMS/quiz/learner cache graphs and
`PlatformCacheDescriptorContributor` owns organization, school, calendar,
curriculum and learning-content graphs. `CacheRuntimeHintsContributor` follows
the same split for Native reflection hints. The compatibility
`platform.cache.BusinessCacheRuntimeHints` facade remains for existing imports,
but it contains no business DTO list.

When a new `@Cacheable` name is added, the owning contributor and its typed value
graph must be updated in the same change. Never cache secrets, bearer tokens,
authorization decisions or tenant-unscoped data.

## Principal cache (the one bounded exception)

`platform.security.PrincipalCache` caches the authenticated `ErgPrincipal` so a
warm request authenticates without SQL (PERF-01: 4 statements per request before,
0 after). It is not a Spring `@Cacheable` and has no descriptor; its policy is:

- **Key:** SHA-256 hash of the access token (`AuthTokenService.hashToken`), never
  the raw token. Neither the token nor the hash is logged. The value carries its
  own tenant; a request whose `X-Tenant-ID` differs from it is rejected.
- **Bounds:** in-process Caffeine only (never Redis), `expireAfterWrite` 30 s,
  `maximumSize` 10 000. An entry also never outlives the session or access-token
  expiry it was loaded with. Only successful loads are stored.
- **Eviction:** `PrincipalCacheInvalidationListener` listens to Hibernate
  insert/update/delete and collection events on `AuthSession`, `User`,
  `UserRoleAssignment`, `UserPermissionOverride` and `UserAccessScope` (evict
  that user) and `Role`/`Permission` (evict all). This covers logout, logout-all,
  admin revoke, `session_replaced`, refresh, account status changes, lifecycle
  flags (onboarding, recovery email, must-change-password), password change and
  role/permission changes. Eviction runs after commit; a load that overlapped an
  eviction is not stored. These tables must keep being written through JPA: a
  bulk JPQL/JDBC update would bypass the listener and wait out the 30 s TTL.
- **Other instances:** a per-user eviction bumps the generation
  `principal / ergPrincipal#<userId>` through `CacheInvalidationService`
  (Redis generation + pub/sub when `erg.redis.state.enabled`), and a cached entry
  is used only while its generation is current. Role/permission definition
  changes clear only the local instance; other instances catch up within 30 s.
  If the generation store is unreachable, nothing is cached.
- **Guarantee:** logout and every change above take effect on the next request on
  the instance that made the change; on another instance as soon as the
  invalidation message arrives, and in all cases within 30 s.
- The tenant GUCs (`TenantGucApplier`) are still applied per connection from the
  request's `TenantContext`; caching the principal does not skip them.
