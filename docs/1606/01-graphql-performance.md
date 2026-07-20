# GraphQL Performance — Root Cause Analysis of 1–2s Latency

## Problem Statement
All GraphQL APIs return responses in 1–2 seconds, even with very little data in the database. This is abnormal — with empty/near-empty tables, responses should be in the 50–200ms range.

## Root Cause Analysis

### 1. Auth Principal Lookup on Every Request — 🔴 CRITICAL

**File:** `platform/security/ErgAuthenticationFilter.java` → `ErgPrincipalService.fromBearerToken()`

**What happens on each GraphQL request:**
1. `ErgAuthenticationFilter.doFilterInternal()` intercepts every request
2. Calls `principalService.fromBearerToken(bearerToken, tenantId)`
3. This calls `sessionRepository.findPrincipalByAccessTokenHashAndTenantId()`
4. The `@EntityGraph` loads: `user → roles → permissions → permissionOverrides → accessScopes`
5. This is a single query **but** it loads 5 related collections with `LEFT OUTER JOIN` which creates a massive Cartesian product

**The caching problem:**
- Cache TTL is only **5 seconds** (`CacheConfig.java` line 21: `cache("authPrincipal", Duration.ofSeconds(5), 10_000)`)
- Cache key uses `#root.target.cacheTokenHash(#bearerToken)` — but Spring SpEL evaluates `#root.target` which can cause issues with proxy-based AOP
- With a 5-second TTL, every active user session causes **12 DB round-trips per minute**
- If 100 users are active, that's **1,200 queries/minute** just for authentication

**Why this causes 1–2s latency:**
- The `@EntityGraph` with 5 nested relationships generates a SQL query with multiple LEFT JOINs
- On PostgreSQL with near-empty data, the query planner still does sequential scans for session lookup + user join + N+1 collections
- The Hibernate session pulls all the entities into the persistence context, which adds overhead for entity management

**Fix:**
```java
// CacheConfig.java — Increase auth principal TTL
cache("authPrincipal", Duration.ofSeconds(30), 10_000),  // was 5s, now 30s

// ErgPrincipalService.java — Simplify cache key expression
@Cacheable(
    cacheNames = "authPrincipal",
    key = "#tenantId + ':' + #bearerToken.hashCode()",
    unless = "#result == null || !#result.isPresent()"
)
```

**Estimated impact:** Reduces per-request latency by **200–500ms** (eliminates repeated auth DB calls within TTL window).

### 2. No DataLoader for Most GraphQL Fields — 🔴 CRITICAL

**What is DataLoader?**  
DataLoader is a pattern that batches multiple field-level DB lookups into a single query. Without it, each GraphQL field resolver triggers an independent DB query.

**Current state:**
- ✅ `CalendarDataLoaderRegistry` exists for `CalendarEventTeacher` and `CalendarEvent` by batch
- ❌ No DataLoaders for LMS fields (`ClassInfo`, `StudentRoster`, `AssignmentSummary`, `ScoreSummary`, `RiskSummary`)
- ❌ No DataLoaders for HocLie fields (`TaxonomyNode`, `ResourceProgress`, `RecentOpened`)
- ❌ No DataLoaders for eLearning fields (`Student`, `User` profile, `Assignment`, `Attempt`, `Announcements`)
- ❌ No DataLoaders for LCMS fields (`Question`, `Quiz`, `UserAccess`)

**Why this matters for latency:**
GraphQL queries often request nested fields. For example, `classWorkspace` returns `students`, `assignments`, `scoreSummary`, and `riskSummary` — each triggering its own repository call. While these aren't N+1 per se (they're called once in the service), the **serial execution within a single service call** means each query waits for the previous one.

More importantly, when multiple GraphQL queries are issued in parallel (common in frontend dashboards), each one goes through the full auth+query pipeline independently, causing **connection pool saturation**.

**Fix:** Add `@BatchMapping` or DataLoader for:
1. User profiles in StudentDashboard
2. Assignment details in ClassWorkspace
3. Attempt scores in AssignmentProgressWorkspace
4. Teacher assignments in HomeworkWorkspace

**Estimated impact:** Reduces latency by **30–50%** for multi-field GraphQL queries.

### 3. Serial Query Execution in Read Model Services — 🟡 MEDIUM

**File:** `service/lms/readmodel/LmsReadModelService.java`

The `classWorkspace()` method executes **5 separate sequential queries**:
1. `classroomRepository.findClassWorkspace()` — fetches class info
2. `studentRepository.findClassRoster()` — fetches students (depends on classId from #1)
3. `assignmentRepository.findClassAssignmentCards()` — fetches assignments
4. `attemptRepository.summarizeClassWorkspaceScores()` — fetches score summary
5. `studentRepository.summarizeClassWorkspaceRisks()` — fetches risk summary

Queries #2 and #3 could run in parallel since they don't depend on each other's results. Similarly, #4 and #5 could run in parallel.

The `teacherHomeworkWorkspace()` method executes **4 sequential queries**:
1. Assignment cards
2. Summary aggregation
3. Class options
4. Pending reviews + overdue students

**The `assignmentProgressWorkspace()` method executes 6 queries:**
1. Assignment detail
2. Assignment progress summary
3. Attempt summaries (paginated)
4. Assignment recipients
5. Latest attempt scores (by recipient IDs)
6. Best attempt scores (by recipient IDs)
7. Missing/late students

Queries #5 and #6 in `assignmentProgressWorkspace` both need `recipientIds` from #4, but could run in parallel after that.

**The `HocLieuLibraryReadService.learningResourceLibrary()` method executes 5 queries:**
1. Resource cards (paginated)
2. Taxonomy tree nodes
3. Resource count by subject ids
4. Resource count by category ids  
5. Resource count by section ids
6. Progress states
7. Recently opened resources

Queries #3, #4, #5 could run in parallel after #2 provides the node IDs.

**Fix:** Use `CompletableFuture` to parallelize independent queries in read model services:
```java
// Example for classWorkspace:
CompletableFuture<Page<StudentRosterProjection>> studentsFuture = 
    CompletableFuture.supplyAsync(() -> studentRepository.findClassRoster(...));
CompletableFuture<Page<AssignmentCardProjection>> assignmentsFuture = 
    CompletableFuture.supplyAsync(() -> assignmentRepository.findClassAssignmentCards(...));
// ... join and combine
```

**Estimated impact:** Reduces latency by **40–60%** for workspace endpoints that make 4+ independent queries.

### 4. Connection Pool Sizing — 🟢 LOW

**File:** `application.yml` line 22–29

```yaml
hikari:
  maximum-pool-size: 25
  minimum-idle: 5
```

With 25 max connections and potential parallel queries, connection pool exhaustion could occur under moderate load. Consider:
- **Increase `maximum-pool-size` to 50** for GraphQL-heavy workloads
- **Add connection pool metrics** via HikariCP metrics (already have Micrometer/Prometheus)

### 5. Hibernate `format_sql: true` in Dev Profile — 🟢 LOW

**File:** `application.yml` line 56

```yaml
format_sql: true
```

This adds formatting overhead to every SQL statement. While useful for debugging, it should be **OFF in production**. The `prod` profile already sets `format_sql: false`, but the `dev` profile (default) has it enabled.

**Fix:** Keep it for dev, but ensure the production profile is always active in deployed environments.

---

## Summary of Latency Root Causes

| Cause | Impact | Fix Effort | Priority |
|-------|--------|-----------|----------|
| Auth Principal 5s cache TTL | 200–500ms per request | Small | P0 |
| No DataLoader for LMS/HocLieu/eLearning | 100–300ms per field | Medium | P0 |
| Serial query execution in services | 200–500ms per workspace | Medium | P1 |
| Lack of `@Cacheable` on read models | 50–200ms per repeated query | Small | P1 |
| Connection pool too small for concurrent load | Variable | Small | P2 |

**Total estimated reduction:** Implementing all P0+P1 fixes should bring GraphQL latency from 1–2s → **200–400ms** under normal load.