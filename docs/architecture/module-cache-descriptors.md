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
