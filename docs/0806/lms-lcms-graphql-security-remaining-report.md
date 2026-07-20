# LMS/LCMS GraphQL Security -- Remaining Items Detailed Report

**Date:** 2026-06-14 (updated)
**Branch:** `codex/lms-graphql-security-audit-plan`
**Domain:** erg-spring

Reference: `docs/0806/lms-lcms-graphql-security-remaining-work-plan.md`

---

## 1. Summary

This report tracks only the **not-yet-completed** items from the original enterprise plan. Items marked Done in the main report are excluded. The goal is to provide a focused action list for closing remaining gates before production enterprise rollout.

---

## 2. Remaining Items by Priority

### P0 -- Must Fix Before Production

| # | Item | Status | Effort | Dependency |
|---|------|--------|--------|------------|
| 1 | **questionBankWorkspace: PostgreSQL proof + EXPLAIN** | **Not Done** | 1-2 days | Docker/PostgreSQL staging |
| 2 | **Slow-query/JDBC timeout smoke test** | **Not Done** | 0.5 day | PostgreSQL staging |
| 3 | **Full field-level security matrix** | **In Progress** | 2-3 days | None |

### P1 -- Must Fix Before Multi-Instance Production

| # | Item | Status | Effort | Dependency |
|---|------|--------|--------|------------|
| 4 | **Distributed Redis rate limiter** | **Not Done** | 2-3 days | Redis infra |
| 5 | **Distributed cache + stampede guard** | **Not Done** | 2-3 days | Redis infra |
| 6 | **Load/soak test 100->500->1,000->5,000** | **Not Done** | 3-5 days | Tests + staging cluster |
| 7 | **classWorkspace attendance summary** | **Not Done** | 1-2 days | Attendance entity/table |
| 8 | **studentDashboard announcement persistence** | **Not Done** | 1-2 days | Announcement entity/table |

### P2 -- Should Fix Before Native Production

| # | Item | Status | Effort | Dependency |
|---|------|--------|--------|------------|
| 9 | **AOT/native smoke** | **Not Done** | 2-3 days | Native build tooling |
| 10 | **Consolidate placeholder modules** | **Not Done** | 3-5 days | None |
| 11 | **Systematic EXPLAIN/index review (all workspaces)** | **Not Done** | 2-3 days | PostgreSQL |

---

## 3. Detailed Breakdown

### 3.1 questionBankWorkspace PostgreSQL Proof

**Current state:** Test `QuestionBankWorkspaceQueryCountIntegrationTest` exists with proper seed data and assertions, but it's skipped on H2 (cannot prove JSONB/lateral usage query). Testcontainers harness is implemented (`PostgreSqlTestDatabase`, `@EnabledIf` guard, env var support for `ERG_TEST_POSTGRES_URL`).

**Blockers:** Docker daemon not available on current dev machine; no staging PostgreSQL URL set.

**Path to close:**
1. Run with external PostgreSQL: `$env:ERG_TEST_POSTGRES_URL="jdbc:postgresql://host:port/db"` (and username/password)
2. If usage query becomes hot path, create normalized `quiz_question_usage` table replacing JSONB/lateral
3. Document EXPLAIN ANALYZE results for the index review

### 3.2 Slow-query/JDBC Timeout Smoke

**Current state:** Config exists (`hibernate.jdbc.timeout=5s`, `jakarta.persistence.query.timeout=5000ms`). No integration test proves that a deliberately slow query is aborted within the timeout.

**Path to close:**
1. Create test that runs a slow query (e.g., `pg_sleep(10)`) against real PostgreSQL
2. Assert the query is aborted within the configured timeout
3. Document the abort behavior (exception type, error code, connection pool impact)

### 3.3 Field-Level Security Matrix

**Current state:** Gates exist for score summary (`lms.grade.read`), risk summary (`lms.progress.read`/`lms.grade.read`), effective permissions (`rbac.permission.read`), and self-scope for student dashboard. No systematic test matrix covering every sensitive field across all workspaces.

**Fields needing gate verification:**
- Quiz attempt raw answer fields
- Private teacher notes
- Effective permission details
- Student risk flags per student
- Resource progress by user (only self)
- Targeting rules in announcements

**Path to close:**
1. Per workspace, enumerate all fields that could expose cross-user/tenant data
2. Write positive test (authorized user sees field) and negative test (unauthorized user gets null/error)
3. Verify denied permission override wins over wildcard grant for each field

### 3.4 Attendance Summary

**Current state:** The source code has no `Attendance` entity, repository, or table. `classWorkspace` DTO/schema have no attendance field. This is a data-model dependency, not just a missing projection.

**Path to close:**
1. Create attendance table/migration (requires product requirements for attendance model)
2. Add `AttendanceSessionSummaryProjection`
3. Implement read service query for attendance summary per class
4. Add field-level gate (`lms.attendance.read` or similar)

### 3.5 Announcement Persistence

**Current state:** `StudentDashboardAnnouncementReadPort` interface + `NoPersistentStudentDashboardAnnouncementReadPort` fallback (returns empty list) exist. The `model.lms.Announcement` class exists as a plain model but has no `@Entity` annotation and no Flyway migration.

**Path to close:**
1. Create `announcements` table migration
2. Annotate `Announcement` as JPA entity
3. Implement repository + `StudentDashboardAnnouncementReadPort` implementation
4. Wire into `StudentDashboardReadService`

### 3.6 Distributed Rate Limiter

**Current state:** `GraphQlRateLimiter` works in-memory per instance. No Redis/gateway implementation.

**Path to close:**
1. Implement `RedisGraphQlRateLimiter` implementing same interface
2. Key includes `tenantId`, `userId`, `portal`, `operationName`
3. Profile toggle (`in-memory` for dev/test, `redis` for staging/prod)
4. Rejection metrics (`notifications.graphql.rate-limit.rejected`)

### 3.7 Distributed Cache + Stampede Guard

**Current state:** Caffeine local cache with `recordStats()` added. No Redis cache for multi-instance hot data.

**Candidates for Redis cache:**
- Taxonomy tree (resource library)
- Dashboard/summary counters (short TTL)
- Question bank options
- Education unit/class options (stable data)

**Path to close:**
1. Identify workspace data that tolerates short TTL (10-60 seconds)
2. Implement Redis `@Cacheable` with key = `tenant:scope:operation:filters:page`
3. Add TTL jitter and single-flight for hot keys
4. Cache hit/miss metrics via Micrometer

### 3.8 Load/Soak Test

**Current state:** No load tests exist. Query-count integration tests prove bounded SQL at unit level but do not measure p95/p99 under concurrency.

**Path to close:**
1. k6/Gatling scenarios:
   - session/current (high volume)
   - classWorkspace with 200-student class
   - teacherHomeworkWorkspace with 50 assignments
   - studentDashboard (self-scoped)
   - questionBankWorkspace (with filters)
   - resourceLibrary (taxonomy + cards)
2. Incremental load: 100 -> 500 -> 1,000 -> 5,000 virtual users
3. Measure: p50/p95/p99, Hikari active/pending/timeout, DB CPU/slow query, GC pause

### 3.9 AOT/Native Smoke

**Current state:** `native-maven-plugin` exists in `pom.xml`. No native build has been attempted for the current GraphQL/rich-projection codebase.

**Path to close:**
1. `mvn -Pnative -DskipTests native:compile`
2. Smoke test: app context boots, GraphQL schema loads, 1 focused query succeeds
3. Add native hints for: GraphQL schema resources, Jackson DTO records, projection records, Bean Validation, JPA repositories, PostgreSQL driver

---

## 4. Recommended Order of Execution

```
Week 1-2 (P0):
  [1] questionBankWorkspace PostgreSQL proof
  [2] Slow-query/JDBC timeout smoke
  [3] Field-level security matrix tests

Week 3-4 (P1):
  [4] Distributed Redis rate limiter
  [5] Distributed cache + stampede guard
  [6] Load/soak test run + analysis
  [7] studentDashboard announcement persistence
  [8] classWorkspace attendance summary (if attendance model ready)

Week 5-6 (P2):
  [9] AOT/native smoke
  [10] Consolidate placeholder modules
  [11] Systematic EXPLAIN/index review
```

---

## 5. Closure Criteria

A workspace is enterprise-ready when:
- Public DTO/GraphQL types are typed and stable
- Repository projection/read-model queries are bounded
- Permission/scope is enforced in backend (not frontend)
- Sensitive fields have positive + negative test coverage
- Pagination has default size + hard max
- Query-count integration test proves bounded SQL
- PostgreSQL EXPLAIN shows index usage for hot queries
- No N+1 (entityLoadCount == 0 for projection read paths)
- Cache/rate-limit policy documented if hot path
- Load/soak evidence at target concurrency
