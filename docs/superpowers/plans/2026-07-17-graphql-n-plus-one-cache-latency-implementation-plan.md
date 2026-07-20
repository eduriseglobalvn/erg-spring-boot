# GraphQL N+1, Cache, and Heavy-Read Latency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove and improve bounded SQL, tenant-correct caching, representative data, and sub-second latency for GraphQL and high-frequency heavy reads.

**Architecture:** Preserve controllers and DTO contracts while introducing request-local measurements, explicit projection read models, versioned Redis/L1 cache descriptors, and deterministic fixture-backed acceptance tests. Heavy reports remain asynchronous jobs.

**Tech Stack:** Spring Boot 4, Spring GraphQL, Spring Data JPA/JdbcClient, PostgreSQL 17, Redis 8.8, Caffeine, Testcontainers, GraalVM Native Image, k6 2.0.

---

### Task 1: Build the GraphQL performance/cache matrix

**Files:**
- Modify: `src/test/resources/performance/graphql-performance-catalog.yaml`
- Create: `src/test/java/com/erg/ergspringboot/performance/GraphQlPerformancePolicyContractTest.java`

- [ ] Write a failing contract asserting every persistent GraphQL read defines statement, row, payload, latency, cacheability, TTL, scope dimensions, generation domain, cold/warm scenario, and fixture ID.
- [ ] Run `./mvnw.cmd -q -Dtest=GraphQlPerformancePolicyContractTest test`; expect failure listing missing coordinates/fields.
- [ ] Classify all 33 coordinates without changing the schema.
- [ ] Re-run the contract; expect exit 0.

### Task 2: Add request-local GraphQL/cache measurement

**Files:**
- Create: `src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlPerformanceObservation.java`
- Modify: `src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlContextInterceptor.java`
- Modify: `src/main/java/com/erg/ergspringboot/platform/cache/LayeredBusinessCache.java`
- Test: `src/test/java/com/erg/ergspringboot/platform/graphql/GraphQlPerformanceObservationTest.java`

- [ ] Write failing concurrent tests proving operation/cache counters cannot leak between requests and labels are bounded catalog IDs.
- [ ] Run the focused test and verify the expected cross-request contamination/missing-counter failure.
- [ ] Implement immutable request snapshots and hit/miss/load-duration counters with `try/finally` cleanup.
- [ ] Re-run focused tests and existing cache/GraphQL metrics tests; expect exit 0.

### Task 3: Prove and remove taxonomy/organization tree N+1

**Files:**
- Modify: `src/main/java/com/erg/ergspringboot/repository/curriculum/CurriculumSubjectRepository.java`
- Modify: `src/main/java/com/erg/ergspringboot/repository/curriculum/CurriculumLevelRepository.java`
- Modify: `src/main/java/com/erg/ergspringboot/repository/curriculum/CurriculumTopicRepository.java`
- Create: `src/main/java/com/erg/ergspringboot/repository/curriculum/projection/CurriculumTreeRow.java`
- Modify the existing curriculum/learning-resource read service selected by source tracing.
- Test: `src/test/java/com/erg/ergspringboot/service/curriculum/CurriculumTreeQueryCountIntegrationTest.java`

- [ ] Seed 1/10/100/1,000 topics and write a failing query-slope test with a three-statement absolute budget.
- [ ] Verify the red test fails for cardinality slope or budget.
- [ ] Implement one tenant-scoped flat projection/CTE and pure in-memory tree assembly; preserve ordering and payload.
- [ ] Add tenant-isolation and empty-tree tests, then run focused PostgreSQL tests to green.

### Task 4: Bound learning-resource and question-bank workspaces

**Files:**
- Modify: `src/main/java/com/erg/ergspringboot/service/learningcontent/LearningResourceLibraryReadService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/lcms/readmodel/LcmsWorkspaceReadService.java`
- Modify: relevant repositories under `repository/learningcontent` and `repository/lms/projection`
- Test: existing `QuestionBankWorkspaceQueryCountIntegrationTest` plus a new learning-resource query-count test.

- [ ] Add 1/10/100/1,000 slope cases and assert no JSON/media entity load when fields are unselected.
- [ ] Run red tests and capture statements/rows.
- [ ] Replace mapper-time reads with tenant-scoped batch projections and bounded pagination.
- [ ] Run query-count, selection-set, payload, and tenant tests to green.

### Task 5: Bound quiz workspace/package/analytics

**Files:**
- Modify: `src/main/java/com/erg/ergspringboot/service/lcms/readmodel/LcmsQuizWorkspaceReadService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/quiz/runtime/QuizRuntimePackageService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/quiz/report/QuizReportService.java`
- Modify repositories under `repository/quiz/version`, `repository/quiz/runtime`, and `repository/lms`
- Test: `src/test/java/com/erg/ergspringboot/service/quiz/QuizGraphQlQueryCountIntegrationTest.java`

- [ ] Write failing query-slope tests for quiz versions/sections/questions/options and question analytics.
- [ ] Verify published package cache keys include tenant, quiz version, and package version.
- [ ] Implement bounded batch projections and aggregate SQL; do not materialize complete answer/event JSON for statistics.
- [ ] Run cache isolation, analytics correctness, Native hints, and query-count tests to green.

### Task 6: Bound teacher reports and student ability statistics

**Files:**
- Modify: `src/main/java/com/erg/ergspringboot/service/lms/readmodel/LmsReadModelService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/elearning/readmodel/StudentDashboardReadService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/quiz/report/QuizReportService.java`
- Add PostgreSQL migration only if measurement proves a missing aggregate/index.
- Test: existing workspace query-count tests plus `TeacherReportAggregateQueryCountIntegrationTest`.

- [ ] Add failing 1/10/100/1,000 tests for class workspace, homework, assignment progress, dashboard, score distribution, and corrected results.
- [ ] Verify authorization happens before expensive reads.
- [ ] Use existing assignment aggregate, add question/score aggregate only when canonical comparison is exact, and keep large exports as jobs.
- [ ] Reconcile aggregate-vs-canonical fixtures and run focused tests to green.

### Task 7: Apply versioned cache policy to every cacheable GraphQL read

**Files:**
- Modify: `src/main/java/com/erg/ergspringboot/platform/cache/BusinessCacheCatalog.java`
- Modify: `src/main/java/com/erg/ergspringboot/config/CacheConfig.java`
- Modify targeted read services and mutation invalidators.
- Test: `src/test/java/com/erg/ergspringboot/platform/cache/GraphQlBusinessCachePolicyTest.java`

- [ ] Write failing tests for tenant/user/scope/selection separation, post-commit invalidation, Redis outage, and 20-request stampede.
- [ ] Register exact descriptors/TTL/generation domains; remove hot-path `allEntries=true` only where the new exact invalidation test exists.
- [ ] Verify PostgreSQL fallback and fail-closed authorization behavior.
- [ ] Run Redis 8.8 focused suite and Native cache hints to green.

### Task 8: Complete deterministic fixtures

**Files:**
- Modify: `load/fixtures/manifest.yaml`
- Modify: `load/fixtures/seed-postgres-scale.sql`
- Modify: `scripts/seed-local-postgres.ps1`
- Test: `src/test/java/com/erg/ergspringboot/performance/PerformanceFixtureContractTest.java`

- [ ] Write a failing contract for organization tree, curriculum hierarchy, content placements, quiz versions/options, attempts/answers, analytics distributions, corrections, and reports.
- [ ] Add idempotent non-destructive inserts and expected checksums/counts.
- [ ] Run the local seed twice and require identical manifest counts.
- [ ] Execute authenticated smoke queries for every fixture family.

### Task 9: Benchmark and report every target operation

**Files:**
- Modify: `load/k6/redis-8-8-cache.js`
- Create: `load/k6/graphql-heavy-reads.js`
- Create: `docs/2026-07-17-graphql-n-plus-one-cache-latency-report.md`
- Create FE guide only if a payload/schema change occurs.

- [ ] Add cold, warm, Redis-down, concurrent-report, taxonomy, quiz, dashboard, and teacher-report scenarios with stable operation tags.
- [ ] Run authenticated diagnostic smoke and retain raw JSON.
- [ ] Run the approved 5,000-VU matrix only with separate load-generator resources; never label a short local run as acceptance.
- [ ] Report per-operation p50/p95/p99, SQL/rows, cache hit ratio, errors, DB/Redis/Hikari/host saturation, and all remaining failures.

### Task 10: Final regression and Native verification

- [ ] Run all focused query-count, cache, fixture, GraphQL coverage, RLS, Redis, report, and Native contract tests.
- [ ] Run `./mvnw.cmd -q test` and aggregate fresh Surefire XML totals.
- [ ] Rebuild the Linux native image and smoke readiness, authenticated taxonomy/quiz/report/dashboard GraphQL, REST hot reads, Flyway, and outbox.
- [ ] Run `git diff --check`, review payload diffs, and update the detailed implementation report without claiming unexecuted capacity gates.

## Self-review

All approved scope areas map to a task. The plan contains no destructive data operation, no payload redesign, no blanket index creation, and no promise that source-level changes alone prove one-second latency.

