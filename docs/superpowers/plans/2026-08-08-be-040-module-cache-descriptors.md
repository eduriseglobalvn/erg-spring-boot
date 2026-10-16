# BE-040 — Module-owned cache descriptors and Native hints

## Objective

Move cache metadata ownership from the central cache configuration into the
bounded context that owns each cached DTO. Keep names, TTLs, keys, scope
fingerprints, invalidation and L2 serialization behavior unchanged while making
new cache additions local and Native-safe.

## Constraints

- No cache secret, bearer token, authorization decision or tenant-unscoped data.
- Duplicate cache names fail fast; every `@Cacheable` name has one descriptor.
- Platform code aggregates descriptors and hints but does not import domain DTOs.
- Preserve existing cache names/TTL/key semantics and compatibility constructors.

## TDD implementation slice

1. Inventory `@Cacheable` names, `CacheConfig`, L2 graphs and Native hints; add
   RED tests for duplicate names, missing descriptors and platform/domain import
   boundaries.
2. Add immutable `CacheDescriptor` and `CacheDescriptorRegistry` contracts plus
   module contributor beans for existing caches.
3. Refactor platform config/runtime hints to consume the registry and migrate
   names without changing keys, TTLs or invalidation behavior.
4. Add descriptor/native serialization tests, cache isolation/stampede checks and
   source ratchets; run JVM and Native contract gates.

## Not in this slice

- New cache names or business behavior.
- Offline persistence/conflict protocol (BE-062).
