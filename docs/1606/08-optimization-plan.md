# Optimization Plan — Prioritized Implementation Roadmap

## Phase 1: Critical (1–2 days) — Fix GraphQL Latency Root Causes

### P0-1: Increase Auth Principal Cache TTL
**File:** `config/CacheConfig.java`
**Change:** `Duration.ofSeconds(5)` → `Duration.ofSeconds(30)`
**Effort:** 1 line change
**Impact:** -200ms per request (eliminates repeated auth DB calls)

### P0-2: Fix Auth Principal Cache Key Expression
**File:** `platform/security/ErgPrincipalService.java`
**Change:** Simplify `@Cacheable` key from SpEL `#root.target.cacheTokenHash(#bearerToken)` to `#tenantId + ':' + #bearerToken.hashCode()`
**Effort:** 1 line change
**Impact:** Prevents cache misses caused by proxy-based AOP issues

### P0-3: Add `@EntityGraph` to `UserRepository.findByIdAndTenantId`
**File:** `repository/auth/user/UserRepository.java`
**Change:**
```java
@EntityGraph(attributePaths = {"roles", "roles.permissions", "permissionOverrides", "accessScopes"})
Optional<User> findByIdAndTenantId(String id, String tenantId);
```
**Effort:** 2 lines
**Impact:** Eliminates N+1 in `StudentDashboardReadService` (-100ms)

### P0-4: Add `@Cacheable` to `LmsReadModelService` Workspace Methods
**File:** `service/lms/readmodel/LmsReadModelService.java`
**Change:** Add `@Cacheable` to `classWorkspace()`, `teacherHomeworkWorkspace()`, `assignmentProgressWorkspace()`
**New caches:**
```java
cache("lmsClassWorkspace", Duration.ofSeconds(30), 500),
cache("lmsHomeworkWorkspace", Duration.ofSeconds(30), 500),
cache("lmsAssignmentProgress", Duration.ofSeconds(30), 500),
```
**Effort:** Small
**Impact:** -50–200ms on repeated queries within TTL

---

## Phase 2: High (3–5 days) — Parallel Queries & DataLoader

### P1-1: Parallelize Independent Queries in Read Model Services
**Files:**
- `service/lms/readmodel/LmsReadModelService.java`
- `service/hoclieu/readmodel/HocLieuLibraryReadService.java`
- `service/elearning/readmodel/StudentDashboardReadService.java`

**Pattern:**
```java
// Before: sequential
ClassWorkspaceProjection classInfo = classroomRepository.findClassWorkspace(...);
Page<StudentRosterProjection> students = studentRepository.findClassRoster(...);
Page<AssignmentCardProjection> assignments = assignmentRepository.findClassAssignmentCards(...);

// After: parallel (classInfo must run first, then students + assignments in parallel)
ClassWorkspaceProjection classInfo = classroomRepository.findClassWorkspace(...);
CompletableFuture<Page<StudentRosterProjection>> studentsF = supplyAsync(() -> 
    studentRepository.findClassRoster(...));
CompletableFuture<Page<AssignmentCardProjection>> assignmentsF = supplyAsync(() -> 
    assignmentRepository.findClassAssignmentCards(...));
Page<StudentRosterProjection> students = studentsF.join();
Page<AssignmentCardProjection> assignments = assignmentsF.join();
```

**Important:** Virtual threads must be enabled (`spring.threads.virtual.enabled: true`) or use a dedicated thread pool.

**Effort:** Medium
**Impact:** -40–60% latency on workspace endpoints

### P1-2: Add DataLoader for Non-Calendar GraphQL Fields
**Files:** New `LmsDataLoaderRegistry.java`, `HocLieuDataLoaderRegistry.java`

**Key DataLoaders needed:**
1. Student by classId (for `ClassWorkspace.students`)
2. Assignment by classId (for `ClassWorkspace.assignments`)
3. Latest/Best attempt scores by assignmentId + studentIds
4. User profile by userId (for `StudentDashboard`)
5. Announcement by classId + studentId

**Effort:** Medium–Large
**Impact:** Eliminates N+1 when GraphQL resolves nested fields independently

### P1-3: Fix Rate Limiter Memory Leak
**File:** `platform/graphql/GraphQlRateLimiter.java`
**Change:** Replace `ConcurrentHashMap` with Caffeine cache:
```java
private final Cache<GraphQlRateLimitKey, WindowCounter> counters = Caffeine.newBuilder()
    .maximumSize(20_000)
    .expireAfterWrite(60, TimeUnit.SECONDS)
    .build();
```
**Effort:** Small
**Impact:** Prevents memory leak under low-traffic conditions

### P1-4: Add Caching to StudentDashboardReadService
**File:** `service/elearning/readmodel/StudentDashboardReadService.java`
**New cache:** `cache("studentDashboard", Duration.ofSeconds(30), 2_000)`
**Effort:** Small
**Impact:** -100ms on repeated dashboard loads

---

## Phase 3: Medium (1 week) — Security & Quality

### P2-1: Add Input Validation to GraphQL Arguments
**Files:** All GraphQL controller input records
**Change:** Add `jakarta.validation` annotations (`@NotBlank`, `@Min`, `@Max`) and enable validation in GraphQL config
**Effort:** Medium

### P2-2: Harden Security Config for Production
**Changes:**
- Force introspection off in prod profile (hardcoded, no env override)
- Set `auth.cookie.secure: true` in prod
- Validate CORS origins at startup
**Effort:** Small

### P2-3: Add GraphQL Operation Timing Metrics
**File:** `platform/graphql/GraphQlContextInterceptor.java`
**Change:** Add Micrometer `Timer` around GraphQL execution
**Effort:** Medium

### P2-4: Enable Hibernate Statistics in Dev
**File:** `application.yml` — dev profile
**Change:** `generate_statistics: true` + expose metrics
**Effort:** Small

### P2-5: Fix Permission Comparison Case-Sensitivity
**File:** `platform/graphql/GraphQlAccessSupport.java` and `ErgPrincipalService.java`
**Change:** Normalize all permissions to lowercase during `ErgPrincipal` construction
**Effort:** Small

---

## Phase 4: Architecture (2+ weeks) — Long-Term Improvements

### P3-1: Migrate Rate Limiter to Redis
**Change:** Replace in-memory `ConcurrentHashMap` with Redis-backed sliding window
**Effort:** Medium–Large

### P3-2: Migrate Caching to Redis for Multi-Instance
**Change:** Replace Caffeine `CacheManager` with `RedisCacheManager` for production
**Effort:** Medium

### P3-3: Add Distributed Tracing
**Change:** Add Micrometer Tracing + Jaeger/Zipkin bridge to `pom.xml`
**Effort:** Medium

### P3-4: Enable Flyway for Schema Migration
**Change:** Enable Flyway, create migration scripts from current schema
**Effort:** Large

### P3-5: Use Projections Instead of Entity Loading for Read Models
**Change:** Replace `User` entity loading with dedicated projections in `StudentDashboardReadService` and `ErgPrincipalService`
**Effort:** Large

---

## Expected Outcomes

### After Phase 1 (Critical):
| Endpoint | Current Latency | Expected Latency |
|----------|----------------|------------------|
| classWorkspace | 1–2s | 400–600ms |
| teacherHomeworkWorkspace | 1–2s | 400–600ms |
| assignmentProgressWorkspace | 1–2s | 400–600ms |
| studentDashboard | 1–2s | 300–500ms |
| learningResourceLibrary | 1–2s | 300–500ms |

### After Phase 2 (High):
| Endpoint | Expected Latency |
|----------|-----------------|
| classWorkspace | 200–400ms |
| teacherHomeworkWorkspace | 200–400ms |
| assignmentProgressWorkspace | 200–400ms |
| studentDashboard | 150–300ms |
| learningResourceLibrary | 150–300ms |

### After Phase 3 (Medium):
- Full input validation on all GraphQL endpoints
- Production-grade security hardening
- Observable query performance metrics
- No more N+1 surprises (Hibernate stats visible)

### After Phase 4 (Architecture):
- Distributed rate limiting
- Distributed caching for multi-instance deployments
- End-to-end distributed tracing
- Automated schema migrations
- Reduced entity loading overhead
