# Caching Improvements

## Current Cache Configuration

**File:** `config/CacheConfig.java`

```java
manager.setCaches(List.of(
    cache("authPrincipal", Duration.ofSeconds(5), 10_000),
    cache("sessionBootstrap", Duration.ofSeconds(60), 2_000),
    cache("hoclieuLibraryBootstrap", Duration.ofSeconds(60), 2_000),
    cache("hoclieuStudioBootstrap", Duration.ofSeconds(60), 200),
    cache("hoclieuResources", Duration.ofSeconds(60), 2_000),
    cache("hoclieuResourceDetail", Duration.ofSeconds(60), 2_000),
    cache("lmsClassesBySchool", Duration.ofSeconds(60), 2_000),
    cache("organizationEducationUnits", Duration.ofSeconds(60), 500),
    cache("organizationEducationUnitEntities", Duration.ofSeconds(60), 500),
    cache("authorizationRoles", Duration.ofSeconds(60), 500),
    cache("authorizationPermissions", Duration.ofSeconds(60), 500)
));
```

## Issues and Improvement Recommendations

### 1. Auth Principal Cache TTL Too Short — 🔴 CRITICAL

`authPrincipal` has a 5-second TTL. Every authenticated request that falls outside the cache window triggers:
- Session lookup by accessTokenHash
- User entity fetch with 5-level EntityGraph (user → roles → permissions → overrides → scopes)

**Impact:** With a 5-second TTL:
- Each user's auth resolved via DB **12 times per minute**
- 100 concurrent users = **1,200 DB queries/minute** just for auth
- This alone accounts for **200–500ms** of the 1–2s latency

**Fix:** Increase to 30–60 seconds:
```java
cache("authPrincipal", Duration.ofSeconds(30), 10_000),
```

### 2. No Caching on LMS Read Model Queries — 🟠 HIGH

The `LmsReadModelService` has **zero caching**. Every GraphQL query for class workspace, homework workspace, or assignment progress hits the database directly.

These are read-heavy workloads that rarely change between requests:
- `classWorkspace()` — 5 DB queries per call
- `teacherHomeworkWorkspace()` — 4 DB queries per call
- `assignmentProgressWorkspace()` — 6–7 DB queries per call

**Fix:** Add cache entries:
```java
cache("lmsClassWorkspace", Duration.ofSeconds(30), 500),
cache("lmsHomeworkWorkspace", Duration.ofSeconds(30), 500),
cache("lmsAssignmentProgress", Duration.ofSeconds(30), 500),
cache("lmsTeacherAssignmentCards", Duration.ofSeconds(30), 1_000),
```

And annotate `LmsReadModelService` methods:
```java
@Cacheable(cacheNames = "lmsClassWorkspace",
    key = "{#tenantId, #classId, #schoolId, #studentStatus, #page, #size}")
public ClassWorkspaceResponse classWorkspace(...) { ... }
```

### 3. No Caching on StudentDashboard — 🟡 MEDIUM

`StudentDashboardReadService` has **no caching**. Each dashboard load makes 5–7 DB queries.

**Fix:** Add:
```java
cache("studentDashboard", Duration.ofSeconds(30), 2_000),
```

### 4. No Caching on Calendar Read Model — 🟡 MEDIUM

`CalendarReadService` has **no caching**. Calendar queries are expensive (multi-table joins + teacher batch loading + reference data).

**Fix:** Add:
```java
cache("calendarWorkspace", Duration.ofSeconds(30), 1_000),
```

Note: Calendar data changes frequently (CRUD operations), so `@CacheEvict` should be added to calendar write methods.

### 5. No Caching on AccessManagementWorkspace — 🟡 MEDIUM

`LcmsWorkspaceGraphQlController.accessManagementWorkspace()` resolves effective permissions per user, each requiring multiple DB queries.

**Fix:** Add caching with user-specific keys that evict on role/permission changes.

### 6. In-Memory Caffeine Cache Not Distributed — 🟡 MEDIUM

All caches use Caffeine (in-memory). In a multi-instance deployment, each instance has its own cache, meaning:
- Cache misses are multiplied by the number of instances
- `@CacheEvict` on one instance doesn't evict from other instances

**Fix:** For production with multiple instances, use Redis-backed caching:
```java
// Use Spring Data Redis cache manager
@Bean
CacheManager cacheManager(RedisConnectionFactory factory) {
    RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(Duration.ofSeconds(60));
    return RedisCacheManager.builder(factory)
        .cacheDefaults(config)
        .build();
}
```

### 7. Caffeine `maximumSize` Not `maximumWeight` — 🟢 LOW

Using `maximumSize` counts entries, not memory. A cache entry holding a large `ErgPrincipal` with 50 permissions uses the same "slot" as a simple string. For production, consider `maximumWeight` with a custom weigher.

---

## Summary

| # | Issue | Severity | Effort | Impact |
|---|-------|----------|--------|--------|
| 1 | Auth cache 5s too short | Critical | Small | -200ms |
| 2 | No caching on LMS read models | High | Medium | -50–200ms |
| 3 | No caching on StudentDashboard | Medium | Small | -100ms |
| 4 | No caching on Calendar reads | Medium | Medium | -100ms |
| 5 | No caching on AccessManagement | Medium | Small | -50ms |
| 6 | Caffeine not distributed | Medium | Large | P2 |
| 7 | maximumSize vs maximumWeight | Low | Small | P3 |