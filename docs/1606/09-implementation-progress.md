# Implementation Progress — 2026-06-16

## ✅ Completed: Phase 1 (Critical) — GraphQL Latency Root Causes

### P0-1: Auth Principal Cache TTL — DONE
**File:** `config/CacheConfig.java`
- Changed `authPrincipal` TTL from `5s` → `30s`
- Added `lmsClassWorkspace`, `lmsHomeworkWorkspace`, `lmsAssignmentProgress`, `lmsSchoolClasses`, `studentDashboard`, `calendarWorkspace` caches

### P0-2: Auth Principal Cache Key Fix — DONE
**File:** `platform/security/ErgPrincipalService.java`
- Simplified `@Cacheable` key from `#root.target.cacheTokenHash(#bearerToken)` to `#tenantId + ':' + #bearerToken.hashCode()`
- Added `unless = "#result == null || !#result.isPresent()"` to prevent caching empty results
- Normalized permissions to lowercase in `roles()` and `permissions()` methods

### P0-3: User EntityGraph for StudentDashboard — DONE
**File:** `repository/auth/user/UserRepository.java`
- Added `findWithDetailsByIdAndTenantId()` with `@EntityGraph(attributePaths = {"roles", "roles.permissions", "permissionOverrides", "accessScopes"})`
- Updated `StudentDashboardReadService` to use `findWithDetailsByIdAndTenantId` instead of `findByIdAndTenantId`
- Fixed `StudentDashboardReadServiceTest` to mock the new method

### P0-4: Cacheable on Read Model Services — DONE
**Files:**
- `LmsReadModelService.java`: Added `@Cacheable` on `classWorkspace`, `teacherHomeworkWorkspace`, `assignmentProgressWorkspace`
- `StudentDashboardReadService.java`: Added `@Cacheable` on `studentDashboard`
- `HocLieuLibraryReadService.java`: Added `@Cacheable` on `learningResourceLibrary`

## ✅ Completed: Phase 2 (High) — Parallel Queries & DataLoader

### P1-1: Parallelize Independent Queries — DONE
**Files:**
- `LmsReadModelService.java`: `classWorkspace()` now runs students, assignments, scoreSummary, riskSummary in parallel via `CompletableFuture.supplyAsync()`
- `LmsReadModelService.java`: `teacherHomeworkWorkspace()` now runs all 4 queries in parallel
- `LmsReadModelService.java`: `assignmentProgressWorkspace()` runs summary, attempts, recipients, missingLate in parallel; then latestAttempts + bestAttempts in parallel after recipients are available
- `HocLieuLibraryReadService.java`: `learningResourceLibrary()` runs resourceCards, taxonomyTree, progress, recentOpened in parallel; `resourceCountsByTaxonomyKind()` runs subject/category/section count queries in parallel

### P1-3: Rate Limiter Memory Leak Fix — DONE
**File:** `platform/graphql/GraphQlRateLimiter.java`
- Replaced `ConcurrentHashMap` with `Caffeine` cache (`maximumSize(20_000)`, `expireAfterAccess(1 minute)`)
- Added automatic window reset when a new time window starts
- Prevents unbounded memory growth

## ✅ Completed: Phase 3 (Medium) — Security & Quality

### P2-2: Production Security Hardening — DONE
**File:** `application.yml` — prod profile
- Hardcoded introspection disabled (`spring.graphql.schema.introspection.enabled: false`)
- Forced `auth.cookie.secure: true` and `auth.cookie.same-site: strict`
- Disabled `hibernate.generate_statistics: false` in prod

### P2-3: GraphQL Operation Timing Metrics — DONE
**File:** `platform/graphql/GraphQlContextInterceptor.java`
- Added Micrometer `Timer` per GraphQL operation
- Tracks `graphql.operation.duration` with tags: `operation`, `tenant`, `result`
- Logs errors with operation name and tenant

### P2-4: Hibernate Statistics in Dev — DONE
**File:** `application.yml` — dev profile
- Added `hibernate.generate_statistics: true`

### P2-5: Permission Lowecase Normalization — DONE
**File:** `platform/security/ErgPrincipalService.java`
- `roles()`: Added `.map(String::toLowerCase)`
- `permissions()`: Added `.toLowerCase(Locale.ROOT)` for both granted and denied permissions

### P2-6: Hibernate Query Count Filter — DONE
**File:** `platform/web/HibernateQueryCountFilter.java` (NEW)
- Logs warning when a request triggers >10 queries (N+1 suspicion)
- Only active when Hibernate statistics are enabled (dev/test)
- Zero overhead in production

### P2-7: Input Validation on GraphQL Arguments — DONE
**Files:**
- `LmsClassWorkspaceGraphQlController.java`: Added `@NotBlank` on `classId`, `@Min/@Max` on `page/size`
- `LmsHomeworkWorkspaceGraphQlController.java`: Added `@NotBlank` on `assignmentId`, `@Min/@Max` on `page/size`

### P2-8: Test Fix — DONE
**File:** `StudentDashboardReadServiceTest.java`
- Updated mock from `findByIdAndTenantId` to `findWithDetailsByIdAndTenantId`
- Updated verify call accordingly

## Remaining (Phase 4 — Architecture, backlog)

### Not Yet Implemented
- P3-1: Migrate rate limiter to Redis
- P3-2: Migrate caching to Redis for multi-instance
- P3-3: Add distributed tracing (Micrometer + Jaeger)
- P3-4: Enable Flyway for schema migration
- P3-5: Use projections instead of User entity loading
- DataLoader for non-Calendar GraphQL fields (requires GraphQL schema changes)