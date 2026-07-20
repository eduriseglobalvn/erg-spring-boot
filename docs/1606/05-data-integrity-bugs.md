# Data Integrity & Logic Bugs

## 1. Rate Limiter Counter Cleanup Only on Overflow — 🟠 HIGH

**File:** `platform/graphql/GraphQlRateLimiter.java` lines 46–49

```java
if (counters.size() > MAX_BUCKETS) {
    counters.entrySet().removeIf(entry -> entry.getValue().windowStartMillis < windowStart);
}
```

Stale rate limit entries are only cleaned when `counters.size() > 20,000`. Under low traffic, stale entries accumulate indefinitely, causing a **memory leak**.

Additionally, the cleanup logic removes entries where `windowStartMillis < windowStart`, but this means ALL expired windows are removed at once — which is a full table scan of the ConcurrentHashMap.

**Fix:** Use a scheduled cleanup task or Caffeine-based cache with automatic eviction:
```java
// Replace ConcurrentHashMap with Caffeine cache
private final Cache<GraphQlRateLimitKey, WindowCounter> counters = Caffeine.newBuilder()
    .maximumSize(20_000)
    .expireAfterWrite(60, TimeUnit.SECONDS)
    .build();
```

## 2. Calendar Namespace Comparison Case-Sensitivity — 🟡 MEDIUM

**File:** `platform/graphql/GraphQlAccessSupport.java` lines 48–59

The `isAllowed()` method checks permissions using `List.contains()`:
```java
List<String> permissions = principal.permissions() == null ? List.of() : principal.permissions();
for (String permission : allowedPermissions) {
    if (permissions.contains("*") || permissions.contains(permission)) {
        return true;
    }
}
```

This is **case-sensitive**. If a role stores `LMS.CLASS.READ` but the check is for `lms.class.read`, the permission check fails silently. The `ErgPrincipalService` normalizes permissions with `trim()` but does NOT lowercase them.

**Fix:** Either normalize all permissions to lowercase during storage, or use case-insensitive comparison.

## 3. Missing Input Validation on GraphQL Arguments — 🟡 MEDIUM

**File:** All GraphQL controller files

None of the GraphQL controllers validate their `@Argument` record inputs. For example:

```java
// LmsClassWorkspaceGraphQlController.java
public ClassWorkspaceResponse classWorkspace(@Argument ClassWorkspaceInput input) {
    ClassWorkspaceInput safeInput = input == null ? ClassWorkspaceInput.empty() : input;
    // No validation of classId format, page/size ranges, etc.
```

There is no `@Valid` annotation or manual validation of:
- UUID format for IDs (classId, assignmentId, etc.)
- Page/size bounds (handled in service layer, but no explicit `@Min(0)` / `@Max(100)`)
- Status enum values

**Risk:** Invalid inputs propagate to the repository layer as SQL parameters, which is safe from SQL injection (parameterized queries) but can cause unexpected query results.

**Fix:** Add `jakarta.validation` annotations to the input records and enable `@Valid` on `@Argument` in Spring GraphQL:
```java
public record ClassWorkspaceInput(
    @Nullable String tenantId,
    @NotBlank String classId,
    @Nullable String schoolId,
    @Nullable String studentStatus,
    @Nullable @Min(0) Integer page,
    @Nullable @Min(1) @Max(100) Integer size,
    ...
) {}
```

---

## Summary

| # | Issue | Severity | Effort |
|---|-------|----------|--------|
| 1 | Rate limiter memory leak | High | Small |
| 2 | Permission comparison case-sensitivity | Medium | Small |
| 3 | Missing input validation on GraphQL arguments | Medium | Medium |