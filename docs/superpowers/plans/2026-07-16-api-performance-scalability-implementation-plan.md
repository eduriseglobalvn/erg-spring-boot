# ERG API Performance and Scalability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Make the complete REST/GraphQL backend measurable, tenant-safe, query-bounded, horizontally portable, and capable of being validated against 5,000 concurrent users.

**Architecture:** Preserve the modular monolith. Keep JPA for transactional writes and simple CRUD, use projections/`JdbcClient` for bounded reads, and pilot jOOQ only on measured PostgreSQL hotspots. Move high-cardinality fan-out and external I/O behind PostgreSQL transactional outbox and bounded workers; use Redis only for explicitly shared, non-authoritative state.

**Tech Stack:** Spring Boot 4.0.6, Spring GraphQL 2.0.3, Hibernate 7.2, PostgreSQL 17, Spring Data JPA, `JdbcClient`, optional jOOQ pilot, Redis/Lettuce, Caffeine L1, Testcontainers PostgreSQL, Micrometer/Prometheus, k6, GraalVM Native Image, Docker.

**Prerequisites:** The approved design is [2026-07-15-api-performance-scalability-design.md](D:/ERG/erg-spring/docs/superpowers/specs/2026-07-15-api-performance-scalability-design.md). The worktree is intentionally dirty. Never reset, checkout, format, or stage unrelated changes. Every commit in this plan must use an explicit path list.

---

## Execution order

Tasks 1-4 are gates. Tasks 5-8 may start only after the corresponding gate tests pass. Tasks 9-12 are dependent on the new query/write budgets and must not be used to mask a failing P0/P1 gate.

1. API catalog and test foundation.
2. PostgreSQL/Flyway test correctness.
3. Request-local observation and query budgets.
4. Tenant GUC/RLS/cache correctness.
5. Common request path, bounded executor, and connection budget.
6. REST read/N+1 elimination.
7. Write batching and transactional outbox.
8. GraphQL cost, selection-aware reads, and tenant-aware batching.
9. Cache/state productionization and process-local API disposition.
10. Reporting read models.
11. Index/DB operational hardening.
12. Load, Native Image, Docker resource budget, and restore verification.

## Task 1: Build the authoritative API catalog

**Files:**

- Create: `src/test/java/com/erg/ergspringboot/platform/performance/ApiCoverageCatalogIntegrationTest.java`
- Create: `src/test/java/com/erg/ergspringboot/platform/performance/GraphQlCoverageCatalogIntegrationTest.java`
- Create: `src/test/resources/performance/api-performance-catalog.yaml`
- Create: `src/test/resources/performance/graphql-performance-catalog.yaml`
- Modify: `src/main/resources/application.yml` test document only through a focused profile change; no `src/test/resources/application-test.yml` exists in this repository.

- [ ] **Step 1: Capture the current route union.** Start the application context once with `prod` and BFF disabled, and once with `prod,enterprise-auth` plus `erg.auth.bff-enabled=true`. Read `RequestMappingHandlerMapping`, normalize `(profile/flag set, HTTP method, path template, handler class/method)`, expand multi-method/multi-path annotations, and sort deterministically.

- [ ] **Step 2: Write the failing coverage assertion.** `ApiCoverageCatalogIntegrationTest` must fail when a runtime mapping is absent from `api-performance-catalog.yaml`, or when the catalog contains a mapping that is not registered. The assertion message must print the exact normalized key.

- [ ] **Step 3: Add GraphQL catalog coverage.** Parse `src/main/resources/graphql/schema.graphqls` and inspect registered controller mappings. Require every root, business nested field, and mutation in `graphql-performance-catalog.yaml` to contain owner, auth requirement, risk class, statement budget, row/payload budget, and selection/alias scenario.

- [ ] **Step 4: Classify every entry.** Use these values only: `persistent-read`, `persistent-write`, `external-io`, `process-local-stub`, `framework`, or `unsupported`. A `process-local-stub` entry must contain either `disable-in-prod` or a named persistence task.

- [ ] **Step 5: Run the census.**

  Run: `./mvnw.cmd -Dtest=ApiCoverageCatalogIntegrationTest,GraphQlCoverageCatalogIntegrationTest test`

  Expected: PASS and a deterministic report containing 301 declared REST handlers, the union of expanded mappings, and 33 GraphQL mapped methods. If the count changes because source changed, the test prints the exact additions/removals and fails until the catalog is deliberately updated.

- [ ] **Step 6: Commit only catalog files.**

  `git add -- src/test/java/com/erg/ergspringboot/platform/performance/ApiCoverageCatalogIntegrationTest.java src/test/java/com/erg/ergspringboot/platform/performance/GraphQlCoverageCatalogIntegrationTest.java src/test/resources/performance/api-performance-catalog.yaml src/test/resources/performance/graphql-performance-catalog.yaml`

  `git commit -m "test: make REST and GraphQL coverage authoritative"`

## Task 2: Make PostgreSQL integration tests truthful

**Files:**

- Modify: `src/main/java/com/erg/ergspringboot/config/persistence/FlywayConfig.java`
- Modify: `pom.xml` to keep H2 test-only; remove it only after the pure-H2 test inventory confirms no test requires it.
- Modify: `src/main/resources/application.yml` test profile
- Create: `src/test/java/com/erg/ergspringboot/platform/persistence/PostgresIntegrationTestBase.java`
- Create: `src/test/java/com/erg/ergspringboot/platform/persistence/FlywayPostgresProfileIntegrationTest.java`
- Modify: `src/test/java/com/erg/ergspringboot/service/lms/readmodel/LmsClassWorkspaceQueryCountIntegrationTest.java`
- Modify: `src/test/java/com/erg/ergspringboot/service/lms/readmodel/LmsTeacherHomeworkWorkspaceQueryCountIntegrationTest.java`
- Modify: `src/test/java/com/erg/ergspringboot/service/lms/readmodel/LmsAssignmentProgressWorkspaceQueryCountIntegrationTest.java`
- Modify: `src/test/java/com/erg/ergspringboot/service/lcms/readmodel/QuestionBankWorkspaceQueryCountIntegrationTest.java`
- Modify: `src/test/java/com/erg/ergspringboot/service/elearning/readmodel/StudentDashboardQueryCountIntegrationTest.java`

- [ ] **Step 1: Write the failing Flyway profile test.** Start a PostgreSQL Testcontainer, set `spring.datasource.url`, `spring.flyway.enabled=true`, and assert that migrations through V64 complete and `flyway_schema_history` contains the final version. Run with Docker unavailable once and record the expected environment failure separately; do not silently fall back to H2 for this test.

- [ ] **Step 2: Fix custom Flyway lifecycle.** Make `postgresFlyway` conditional on `spring.flyway.enabled=true`; do not invoke `migrate` when disabled. Preserve the existing production classpath location and test source location behavior. The bean must not run a migration against H2.

- [ ] **Step 3: Create the PostgreSQL test base.** Use `PostgreSQLContainer` with `withReuse(false)`, apply the datasource URL through `@DynamicPropertySource`, disable Mongo/Redis autoconfiguration, and keep the application transaction manager as the tested manager. Do not wrap test methods in a caller transaction because async repository calls use different connections.

- [ ] **Step 4: Move query-count tests to the base.** Replace H2 assumptions with PostgreSQL fixtures and assert both Hibernate entity loads and prepared statement counts. Reset statistics before each invocation and assert after the response completes.

- [ ] **Step 5: Run the repaired suite.**

  Run: `./mvnw.cmd -Dtest=FlywayPostgresProfileIntegrationTest,LmsClassWorkspaceQueryCountIntegrationTest,LmsTeacherHomeworkWorkspaceQueryCountIntegrationTest,LmsAssignmentProgressWorkspaceQueryCountIntegrationTest,QuestionBankWorkspaceQueryCountIntegrationTest,StudentDashboardQueryCountIntegrationTest test`

  Expected: PASS with PostgreSQL. If Docker is unavailable, the build fails with an explicit “Docker required for PostgreSQL integration tests” message instead of producing H2 false confidence.

- [ ] **Step 6: Commit the test-foundation change.**

  `git add -- pom.xml src/main/java/com/erg/ergspringboot/config/persistence/FlywayConfig.java src/main/resources/application.yml src/test/java/com/erg/ergspringboot/platform/persistence src/test/java/com/erg/ergspringboot/service/lms/readmodel src/test/java/com/erg/ergspringboot/service/lcms/readmodel src/test/java/com/erg/ergspringboot/service/elearning/readmodel`

  `git commit -m "test: run performance integration tests on PostgreSQL"`

## Task 3: Add request-local observation and absolute budgets

**Files:**

- Create: `src/main/java/com/erg/ergspringboot/platform/performance/RequestExecutionContext.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/performance/RequestExecutionMetrics.java`
- Create: `src/test/java/com/erg/ergspringboot/platform/performance/CountingDataSource.java`
- Create: `src/test/java/com/erg/ergspringboot/platform/performance/RequestLocalQueryCountIntegrationTest.java`
- Modify: `src/main/java/com/erg/ergspringboot/platform/web/HibernateQueryCountFilter.java`
- Modify: `src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlContextInterceptor.java`
- Modify: `src/main/java/com/erg/ergspringboot/config/CacheConfig.java` only for bounded metrics exposure, not behavior yet

- [ ] **Step 1: Write the failing concurrency test.** Run two concurrent HTTP requests against a test controller that executes different SQL counts. Assert each request receives its own count and no global delta is attributed to the other request.

- [ ] **Step 2: Implement request-local context.** Store request ID, route template, operation name, start time, query count, entity loads, rows/payload counters, and cache hit/miss counters in a request-scoped context. Use `try/finally` cleanup and a bounded immutable snapshot at completion.

- [ ] **Step 3: Implement test-only JDBC counting.** Wrap the test datasource and increment a request context counter at `Statement.execute`, `PreparedStatement.execute`, batch execution, commit, and rollback. Do not include this wrapper in production or Native Image dependencies.

- [ ] **Step 4: Replace the global warning filter.** `HibernateQueryCountFilter` may log only request-local counters. If no request-local counter exists, it emits no misleading N+1 warning. Remove the global statistics delta calculation.

- [ ] **Step 5: Bound GraphQL metrics.** Use route/operation catalog IDs, not tenant ID or arbitrary client labels. Record tenant only in structured sampled logs with masking and sampling limits.

- [ ] **Step 6: Add route budget enforcement.** Load the catalog budget and fail performance tests when statements, rows, payload, or latency exceed it. Production logs report violations; they do not reject correct traffic solely because a tuning budget was exceeded.

- [ ] **Step 7: Verify.**

  Run: `./mvnw.cmd -Dtest=RequestLocalQueryCountIntegrationTest,GraphQlContextInterceptorTest test`

  Expected: PASS; concurrent requests have independent counters and metrics have bounded label cardinality.

- [ ] **Step 8: Commit.**

  `git add -- src/main/java/com/erg/ergspringboot/platform/performance src/test/java/com/erg/ergspringboot/platform/performance src/main/java/com/erg/ergspringboot/platform/web/HibernateQueryCountFilter.java src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlContextInterceptor.java src/main/java/com/erg/ergspringboot/config/CacheConfig.java`

  `git commit -m "perf: add request-local query and budget instrumentation"`

## Task 4: Close tenant context and RLS correctness gaps

**Files:**

- Modify: `src/main/java/com/erg/ergspringboot/platform/security/TenantGucApplier.java`
- Modify: `src/main/java/com/erg/ergspringboot/config/persistence/TenantAwareJpaTransactionManager.java`
- Create: `src/main/resources/db/migration/postgres/V65__strict_request_rls_roles.sql`
- Create: `src/test/java/com/erg/ergspringboot/platform/security/TenantGucApplierTest.java`
- Create: `src/test/java/com/erg/ergspringboot/platform/security/TenantRlsPostgresIntegrationTest.java`
- Create: `src/test/java/com/erg/ergspringboot/platform/security/TenantConnectionReuseIntegrationTest.java`

- [ ] **Step 1: Write the GUC batching test.** Mock/record one transaction connection and assert tenant/user/portal are installed with one SQL statement. Assert null/blank values are normalized according to the existing contract, not silently omitted.

- [ ] **Step 2: Implement one-statement GUC application.** Replace three `select set_config(?, ?, true)` executions with one prepared statement that sets all required values transaction-locally. Keep `true` for every setting. Do not concatenate tenant/user input into SQL.

- [ ] **Step 3: Make request failure closed.** In `TenantAwareJpaTransactionManager`, propagate GUC setup failure as a transaction failure for request and worker contexts. Add an explicit maintenance marker/role path; do not use an unset request context as a maintenance authorization mechanism.

- [ ] **Step 4: Add strict policy migration.** V65 must target only currently protected quiz-domain tables, use tenant equality for request paths, preserve `FORCE ROW LEVEL SECURITY`, and be backward-compatible with the application version that already installs GUC. The migration must not grant `BYPASSRLS` to the app role.

- [ ] **Step 5: Test pooled connection reuse.** On PostgreSQL, run tenant A commit, tenant B rollback, malformed tenant, exception, and cancelled transaction on the same pool. Assert no tenant A rows are visible to tenant B and no blank-GUC request succeeds against protected tables.

- [ ] **Step 6: Verify.**

  Run: `./mvnw.cmd -Dtest=TenantGucApplierTest,TenantRlsPostgresIntegrationTest,TenantConnectionReuseIntegrationTest test`

  Expected: PASS; app/worker roles do not own protected tables or have `BYPASSRLS`, and every failed context installation aborts the transaction.

- [ ] **Step 7: Commit.**

  `git add -- src/main/java/com/erg/ergspringboot/platform/security/TenantGucApplier.java src/main/java/com/erg/ergspringboot/config/persistence/TenantAwareJpaTransactionManager.java src/main/resources/db/migration/postgres/V65__strict_request_rls_roles.sql src/test/java/com/erg/ergspringboot/platform/security/TenantGucApplierTest.java src/test/java/com/erg/ergspringboot/platform/security/TenantRlsPostgresIntegrationTest.java src/test/java/com/erg/ergspringboot/platform/security/TenantConnectionReuseIntegrationTest.java`

  `git commit -m "security: make tenant transaction context fail closed"`

## Task 5: Bound common request execution and database connections

**Files:**

- Create: `src/main/java/com/erg/ergspringboot/platform/concurrency/ErgReadTaskExecutor.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/concurrency/ErgContextTaskDecorator.java`
- Create: `src/test/java/com/erg/ergspringboot/platform/concurrency/ErgReadTaskExecutorTest.java`
- Create: `src/test/java/com/erg/ergspringboot/platform/concurrency/ErgContextPropagationIntegrationTest.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/lms/readmodel/LmsReadModelService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/calendar/CalendarReadService.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/java/com/erg/ergspringboot/platform/web/PlatformHealthController.java`

- [ ] **Step 1: Write executor safety tests.** Assert the executor has a fixed maximum, rejects work when saturated, propagates `TenantContext` and `SecurityContext`, clears both after completion, and does not use the common ForkJoinPool.

- [ ] **Step 2: Implement bounded context propagation.** Use a fixed executor whose size is configured independently from HTTP concurrency. Capture context at submission, restore it only around the task, and clear it in `finally`. Use explicit `TransactionTemplate` in each async database task; do not pretend the caller transaction crosses threads.

- [ ] **Step 3: Replace unqualified `CompletableFuture.supplyAsync`.** Route existing LMS/calendar async calls through `ErgReadTaskExecutor`. Add cancellation/timeout handling that prevents new work after the request deadline; document that JDBC cancellation requires a separate driver test.

- [ ] **Step 4: Set pilot pool budgets.** For the 16 GB host, start one app container at Hikari maximum 8/minimum idle 1, worker aggregate maximum 4, operational reserve 4, and PostgreSQL `max_connections=40`. Keep acquisition timeout at one second. Do not multiply the pool when adding the second app container without measuring the global budget.

- [ ] **Step 5: Isolate readiness probes.** Use a short-lived, bounded probe path with a separate timeout and ensure readiness traffic cannot occupy the full application pool.

- [ ] **Step 6: Verify.**

  Run: `./mvnw.cmd -Dtest=ErgReadTaskExecutorTest,ErgContextPropagationIntegrationTest,LmsClassWorkspaceQueryCountIntegrationTest,CalendarReadServiceTest test`

  Expected: PASS; no task runs on common ForkJoinPool, tenant/security context is present inside tasks and absent afterward, and Hikari pending borrowers remain bounded.

- [ ] **Step 7: Commit.**

  `git add -- src/main/java/com/erg/ergspringboot/platform/concurrency src/test/java/com/erg/ergspringboot/platform/concurrency src/main/java/com/erg/ergspringboot/service/lms/readmodel/LmsReadModelService.java src/main/java/com/erg/ergspringboot/service/calendar/CalendarReadService.java src/main/resources/application.yml src/main/java/com/erg/ergspringboot/platform/web/PlatformHealthController.java`

  `git commit -m "perf: bound blocking read concurrency and pool usage"`

## Task 6: Remove REST read N+1 and unbounded pagination

**Files:**

- Modify: `src/main/java/com/erg/ergspringboot/service/lms/LmsRuntimeService.java`
- Modify: `src/main/java/com/erg/ergspringboot/repository/lms/AssignmentRepository.java`
- Modify: `src/main/java/com/erg/ergspringboot/repository/lms/AttemptRepository.java`
- Modify: `src/main/java/com/erg/ergspringboot/repository/lms/ClassroomRepository.java`
- Modify: `src/main/java/com/erg/ergspringboot/repository/users/UserActivityRepository.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/users/UserService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/users/EmployeeSecurityActivityService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/calendar/CalendarReadService.java`
- Modify: `src/main/java/com/erg/ergspringboot/common/request/calendar/CalendarRequests.java`
- Modify: `src/main/java/com/erg/ergspringboot/common/request/users/UserRequests.java`
- Modify: `src/main/java/com/erg/ergspringboot/common/request/quiz/AssignmentRequests.java`
- Modify: `src/main/java/com/erg/ergspringboot/common/request/quiz/QuizAuthoringRequests.java`
- Create: query-count tests for each endpoint family under `src/test/java/com/erg/ergspringboot/service/performance/rest/`

- [ ] **Step 1: Write cardinality-slope tests.** Seed 1, 10, 100, and 1,000 assignments/attempts/history rows. Call student assignments, scores, quiz students, assignment progress, activity, security feed, and calendar workspace through the HTTP layer. Assert current tests fail because SQL/rows grow with item count.

- [ ] **Step 2: Add batch projections.** Add repository methods returning only response fields. For assignments/scores, collect assignment IDs and perform one student/class lookup and one attempts query using tenant-scoped `IN`/array parameters. Group results in memory by ID without any repository call inside the mapper.

- [ ] **Step 3: Move aggregation into SQL.** Add count/status/score aggregate projections for assignment progress, CRM performance, and assignment reports. Do not load complete Attempt/StudentProgress JSON entities when only status/count/score is returned.

- [ ] **Step 4: Move pagination into SQL.** Replace full-history `List` methods followed by `subList` with `Page` or keyset queries. Add a stable `(created_at, id)` cursor for the unified security feed and bound page size/page depth.

- [ ] **Step 5: Bound calendar and list inputs.** Add validation for date interval, page size, page number/cursor, school/class ID lists, and report ranges. Reject inputs above the catalog limit before repository calls.

- [ ] **Step 6: Enforce scope before expensive reads.** Assignment-progress and similar endpoints must prove tenant/class/owner scope before loading pages or aggregates. Out-of-scope requests must execute zero expensive business queries.

- [ ] **Step 7: Verify budgets.**

  Run: `./mvnw.cmd -Dtest=*Rest*QueryCount*IntegrationTest,*Pagination*IntegrationTest test`

  Expected: SQL count is constant across 1/10/100/1,000 fixtures, returned rows obey page bounds, and no mapper contains a repository call inside a cardinality loop.

- [ ] **Step 8: Commit by read family.** Create separate commits for LMS runtime reads, history/feed pagination, and calendar bounds so each rollback is isolated.

## Task 7: Batch high-cardinality writes and add transactional outbox

**Files:**

- Create: `src/main/resources/db/migration/postgres/V66__transactional_outbox.sql`
- Create: `src/main/java/com/erg/ergspringboot/model/outbox/OutboxEvent.java`
- Create: `src/main/java/com/erg/ergspringboot/repository/outbox/OutboxEventRepository.java`
- Create: `src/main/java/com/erg/ergspringboot/service/outbox/TransactionalOutboxWorker.java`
- Create: `src/main/java/com/erg/ergspringboot/service/outbox/OutboxEventKey.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/java/com/erg/ergspringboot/service/lms/LmsAssignmentCommandService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/quiz/authoring/QuizQuestionLinkService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/quiz/publish/QuizPublishService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/authorization/AuthorizationMutationAuditService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/notifications/NotificationCommandService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/notifications/NotificationOutboxWorker.java` to delegate claim/retry/idempotency to `TransactionalOutboxWorker`
- Modify: `src/main/java/com/erg/ergspringboot/service/auth/AccountVerificationService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/auth/SmtpAccountMailSender.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/users/UserService.java`

- [ ] **Step 1: Write failing batch-count tests.** Measure prepared statements for assignments with 10/100/1,000 recipients, quiz drafts with 10/100/1,000 links, and role mutations with 10/100 affected users. Assert current per-row behavior fails the target.

- [ ] **Step 2: Configure JDBC batching safely.** Set `hibernate.jdbc.batch_size=50`, `hibernate.order_inserts=true`, `hibernate.order_updates=true`, and `hibernate.jdbc.batch_versioned_data=true` only in the PostgreSQL profile. Verify generated IDs and optimistic-lock behavior support batching before enabling each entity family.

- [ ] **Step 3: Add bulk repository methods.** Implement `saveAll`/native batch methods for recipients, question links, attempt events/answers, audit/version rows, and notification outbox rows. Deduplicate input IDs before validation and chunk lists at 500 rows to cap parameter/payload size.

- [ ] **Step 4: Make quiz link synchronization set-based.** Validate all question IDs with one tenant-scoped `IN` query, delete links by quiz/version in one statement, and insert the deduplicated link set in batches. Preserve ordering and reject unknown questions before delete.

- [ ] **Step 5: Add outbox schema and worker.** `outbox_events` contains id, tenant_id, event_type, aggregate_id, idempotency_key, payload, status, attempts, available_at, lease_until, last_error, created_at, and completed_at. Add a unique `(tenant_id, idempotency_key)` constraint and indexes for `(status, available_at, lease_until)`.

- [ ] **Step 6: Move external effects after commit.** Account creation/recovery writes challenge/user state and an outbox row, commits, and returns. SMTP worker sends and records result. Notification worker claims bounded rows, calls Firebase with configured timeout, and updates result idempotently. The HTTP request never waits for the provider.

- [ ] **Step 7: Add outbox transition tests.** Cover shadow/non-claimable rows, pending claim, lease expiry, retry, dead-letter, duplicate event key, worker tenant context, graceful drain, and provider timeout.

- [ ] **Step 8: Verify.**

  Run: `./mvnw.cmd -Dtest=*Batch*IntegrationTest,*Outbox*Test,NotificationOutboxWorkerTest,AccountVerificationServiceTest test`

  Expected: prepared statements grow by batch chunks, not rows; external calls occur only after commit; duplicate effects are zero.

- [ ] **Step 9: Commit by side effect.** Commit batching foundation, outbox schema/worker, and notification/email cutovers separately.

## Task 8: Make GraphQL cost-aware and selection-aware

**Files:**

- Modify: `src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlGuardFilter.java`
- Modify: `src/main/java/com/erg/ergspringboot/platform/graphql/ErgGraphQlProperties.java`
- Modify: `src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlExecutionGuardConfiguration.java`
- Modify: `src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlContextInterceptor.java`
- Modify: `src/main/java/com/erg/ergspringboot/controller/lms/graphql/LmsLearningResourceGraphQlController.java`
- Modify: `src/main/java/com/erg/ergspringboot/controller/lms/graphql/LmsClassWorkspaceGraphQlController.java`
- Modify: `src/main/java/com/erg/ergspringboot/controller/lms/graphql/LmsHomeworkWorkspaceGraphQlController.java`
- Modify: `src/main/java/com/erg/ergspringboot/controller/calendar/graphql/CalendarLmsQueryGraphQlController.java`
- Modify: `src/main/java/com/erg/ergspringboot/controller/lcms/graphql/LcmsWorkspaceGraphQlController.java`
- Modify: `src/main/java/com/erg/ergspringboot/controller/calendar/graphql/CalendarLcmsQueryGraphQlController.java`
- Modify: `src/main/java/com/erg/ergspringboot/controller/lcms/graphql/LcmsQuizWorkspaceGraphQlController.java`
- Modify: `src/main/java/com/erg/ergspringboot/controller/lcms/graphql/LcmsReportGraphQlController.java`
- Modify: `src/main/java/com/erg/ergspringboot/controller/lcms/graphql/LcmsSchoolOperationsGraphQlController.java`
- Modify: `src/main/java/com/erg/ergspringboot/controller/calendar/graphql/CalendarMutationGraphQlController.java`
- Modify: `src/main/java/com/erg/ergspringboot/controller/elearning/graphql/StudentDashboardGraphQlController.java`
- Modify: `src/main/java/com/erg/ergspringboot/controller/platform/graphql/GraphQlShellController.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/calendar/CalendarDataLoaderRegistry.java`
- Modify: `src/main/resources/graphql/schema.graphqls`
- Create: `src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlSelectionCost.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/graphql/TenantBatchKey.java`
- Create: GraphQL HTTP query-budget tests under `src/test/java/com/erg/ergspringboot/platform/graphql/`

- [ ] **Step 1: Write failing guard tests.** Send oversized body/variables, alias count 20/21, large date/list variables, expensive scalar-only calendar selection, and combined full workspace query. Assert current guard permits work whose measured SQL exceeds the catalog budget.

- [ ] **Step 2: Bound body before parsing.** Add servlet/server request-size enforcement before `readAllBytes`, cap body bytes separately from query text, and reject variable arrays/date ranges/pages above the catalog limit with a structured GraphQL error.

- [ ] **Step 3: Add field-cost map.** Assign explicit costs for calendar, workspace, list, aggregate, and scalar fields. Multiply aliases/fragments and input cardinality; reject any operation over 25 business statements or its catalog-specific budget before resolver execution.

- [ ] **Step 4: Add selection-aware resolver dispatch.** Use `DataFetchingFieldSelectionSet` in each workspace resolver to build a requested-section enum. Services receive that enum and do not execute unselected sections. Keep authorization checks before selection-dependent SQL.

- [ ] **Step 5: Replace dormant DataLoaders.** Remove unused singleton `DataLoader` beans, or register request-scoped loaders through `BatchLoaderRegistry`. Keys must be `TenantBatchKey(tenantId, scopeId, entityId)` and repository queries must contain tenant predicates.

- [ ] **Step 6: Fix analytics contract.** Make schema/controller/service consistently accept either quiz ID or quiz version ID. The selected contract is `quizVersionId`; repository analytics reads that exact version. Move the cached method behind a separate bean or call the cache boundary through a proxied service.

- [ ] **Step 7: Verify.**

  Run: `./mvnw.cmd -Dtest=*GraphQl*Test,*GraphQL*QueryCount*IntegrationTest test`

  Expected: cheap selections do not trigger unselected workspace queries, aliases are charged, over-budget operations fail before business SQL, and all cache/loader tests preserve tenant scope.

- [ ] **Step 8: Commit guard, resolver, and contract changes separately.** Keep the analytics contract commit isolated from selection optimization for rollback.

## Task 9: Productionize cache and process-local API state

**Files:**

- Modify: `src/main/java/com/erg/ergspringboot/config/CacheConfig.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/lms/readmodel/LmsReadModelService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/calendar/CalendarReadService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/catalog/CatalogService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/cms/CmsService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/documents/DocumentService.java`
- Modify: `src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlRateLimiter.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/java/com/erg/ergspringboot/service/support/ModuleWorkflowService.java`
- Create: `src/main/java/com/erg/ergspringboot/platform/cache/CacheGenerationService.java`
- Create: `src/test/java/com/erg/ergspringboot/platform/cache/CacheIsolationIntegrationTest.java`
- Create: `src/test/java/com/erg/ergspringboot/platform/cache/RedisFailureFallbackIntegrationTest.java`

- [ ] **Step 1: Write cache isolation/race tests.** Cover disjoint managed classes, current class, tenant-identical IDs, post-commit invalidation, concurrent write/read, Redis unavailable, and cache stampede.

- [ ] **Step 2: Remove security allow caching.** Disable cross-request cached principal/session/permission allow decisions in the first rollout and use compact tenant-scoped database projection. Keep only deny/revocation hints that fail closed.

- [ ] **Step 3: Add versioned business cache.** Use Redis/Lettuce with 512 MiB/noeviction pilot policy, explicit TTLs, tenant/entity generation, single-flight loading, and PostgreSQL fallback on Redis failure. Replace `allEntries=true` on touched paths with exact keys.

- [ ] **Step 4: Disposition every process-local endpoint.** For each catalog entry, either persist state with tenant_id and pagination, or guard it behind a production-disabled profile. Do not expose tenantless CHM data from production. Start with documents/catalog/CMS because they return user-visible data; then crawler/SEO/engagement/AI/operations/trending.

- [ ] **Step 5: Make GraphQL rate limit distributed.** Store counters in Redis with Lua/token-bucket semantics. Keep a bounded local emergency limiter for Redis outage and reject expensive GraphQL traffic conservatively.

- [ ] **Step 6: Verify.**

  Run: `./mvnw.cmd -Dtest=*Cache*IntegrationTest,GraphQlRateLimiterTest,*Stub*ProductionProfileTest test`

  Expected: no cross-replica/tenant cache leak, cache write failures fall back safely, and production profile cannot expose a process-local business store.

- [ ] **Step 7: Commit each domain disposition independently.** Do not combine cache infrastructure with CMS/catalog persistence migrations.

## Task 10: Move reporting to bounded read models

**Files:**

- Create: `src/main/resources/db/migration/postgres/V67__report_aggregate_tables.sql`
- Create: `src/main/java/com/erg/ergspringboot/service/reporting/ReportJobService.java`
- Create: `src/main/java/com/erg/ergspringboot/service/reporting/ReportWorker.java`
- Create: `src/main/java/com/erg/ergspringboot/repository/reporting/ReportAggregateRepository.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/quiz/report/QuizReportService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/crm/CrmSchoolService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/users/EmployeeSecurityActivityService.java`
- Modify: `src/main/java/com/erg/ergspringboot/service/lms/readmodel/LmsReadModelService.java`
- Create: `src/main/java/com/erg/ergspringboot/common/response/reporting/ReportJobResponses.java` (new `reporting` response package)
- Create: `src/test/java/com/erg/ergspringboot/service/reporting/ReportJobServiceIntegrationTest.java`

- [ ] **Step 1: Write aggregate correctness tests.** Compare aggregate tables against canonical attempts/progress/deal/activity fixtures for empty, partial, completed, and corrected records.

- [ ] **Step 2: Add incremental aggregate updates.** Update aggregates in the same transaction for small synchronous changes, and enqueue reconciliation events for corrections/replays. Every aggregate row includes tenant_id and source version/event timestamp.

- [ ] **Step 3: Convert heavy reports to jobs.** Return a job ID within 500 ms p95, claim with outbox worker, store status/progress/result metadata, and enforce report concurrency 50 in the load profile.

- [ ] **Step 4: Keep interactive reports bounded.** Use aggregate SQL with at most three statements and payload <=2 MiB. Requests exceeding the catalog limit return job-required response rather than loading all entities.

- [ ] **Step 5: Verify.**

  Run: `./mvnw.cmd -Dtest=*Report*Test,*Aggregate*IntegrationTest test`

  Expected: aggregate values match canonical fixtures, report acknowledgement meets 500 ms p95 in local tests, and report jobs cannot consume the interactive connection budget.

- [ ] **Step 6: Commit schema, job service, and endpoint cutover separately.** Keep aggregate backfill reversible and measurable.

## Task 11: Apply evidence-based PostgreSQL/index hardening

**Files:**

- Create: `ops/postgres/README.md`
- Create: `ops/postgres/top-queries.sql`
- Create: `ops/postgres/fk-index-audit.sql`
- Create: `ops/postgres/explain-capture.sql`
- Create: `src/main/resources/db/migration/postgres/V68__proven_hot_path_indexes.sql` only after the workload proves the selected indexes are needed.
- Modify: `src/main/resources/application.yml` PostgreSQL profile only where benchmark proves the setting

- [ ] **Step 1: Capture top queries.** Enable `pg_stat_statements`, record calls/mean/total time/rows for the accepted workload, and save a redacted run artifact with database version/settings.

- [ ] **Step 2: Explain candidates.** Run `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)` on the top query shapes with representative cardinality. Record estimated/actual rows, loops, buffer hits/reads, sort spill, and lock timing.

- [ ] **Step 3: Select indexes.** Add only indexes that remove a measured sequential scan, poor join, or expensive sort on a high-frequency/high-cost query. Check existing duplicate indexes before every migration.

- [ ] **Step 4: Deploy indexes online.** Use a migration/deployment procedure that executes `CREATE INDEX CONCURRENTLY` outside a transaction, monitors lock impact, supports cancellation, and records completion. Do not run large index creation in application startup.

- [ ] **Step 5: Verify.** Re-run the same workload and require lower p95/total DB time without write regression, index bloat, or lock increase. Revert an index only through a separately reviewed migration.

- [ ] **Step 6: Commit SQL audit scripts and each proven migration separately.** Do not commit all 74 missing-FK indexes.

## Task 12: Validate 5,000 concurrency, Native Image, and deployment budgets

**Files:**

- Create: `load/k6/erg-5k-steady.js`
- Create: `load/k6/erg-5k-submission-burst.js`
- Create: `load/k6/erg-teacher-report-contention.js`
- Create: `load/fixtures/manifest.yaml`
- Create: `load/README.md`
- Create: `ops/docker/compose.production.yml`
- Create: `ops/docker/.env.example`
- Create: `ops/backup/restore-drill.md`
- Modify: `pom.xml` only for a proven native/test plugin requirement
- Create: native smoke test configuration under `src/test/java/com/erg/ergspringboot/platform/nativeimage/`

- [ ] **Step 1: Seed deterministic data.** Implement the approved manifest: 10 tenants, 4,500 students, 450 teachers, 50 admins/report users, 250 classes, 20 subjects, 40 assignments/class, representative 50-question quizzes, 100,000 calendar rows, and 1,000,000 history rows. Record checksums.

- [ ] **Step 2: Implement k6 scenarios.** Use separate load generators, 10-minute warmup, 15-minute ramp, 30-minute steady run, 2-hour 50% soak, warm/cold cache runs, and the exact approved mix. Keep accepted-load 429/503 below 0.1%; label overload tests separately.

- [ ] **Step 3: Implement submission burst.** Release 5,000 student workflows within 60 seconds, require at least 84 completed acknowledgements/second, p95 <=1 second, no duplicate effects, and safe retry behavior.

- [ ] **Step 4: Add host/container budgets.** Encode the 16 GB pilot limits from the design, including PostgreSQL 6 GiB working budget, Redis 512 MiB/noeviction, app/worker caps, and Hikari pool budget. Do not let a second app container silently increase DB pool size.

- [ ] **Step 5: Run JVM baseline.** Record source/image fingerprint, route latency, p50/p95/p99, completed RPS, errors/rejections, Hikari pending, PostgreSQL CPU/I/O/WAL/locks, Redis memory, container RSS, and GC/swap/OOM.

- [ ] **Step 6: Run Native Image smoke and comparison.** Execute `./mvnw.cmd -Pnative -DskipTests native:compile`, start the image against local PostgreSQL/Redis, run authenticated REST/GraphQL/outbox smoke, then repeat representative load. Native passes only if correctness, RSS, and latency gates match the JVM control.

- [ ] **Step 7: Verify backup/restore.** Perform an encrypted off-host base backup plus WAL recovery, restore to an empty host, run Flyway and authenticated smoke, and record elapsed RTO. Repeat quarterly after production launch.

- [ ] **Step 8: Commit load/deployment assets separately from source fixes.** Load thresholds and resource manifests must be reviewable without mixing application behavior changes.

## Plan self-review

### Spec coverage

- Common request latency, GUC, pool, and ForkJoinPool issues: Tasks 3-5.
- REST N+1, JSONB over-fetch, pagination, calendar, authz, and LMS: Task 6.
- Write fan-out, JDBC batching, SMTP/Firebase, quiz, assignment, and notification: Task 7.
- GraphQL selection, cost, aliases, body limits, DataLoader, and analytics contract: Task 8.
- Cache keys, stampede, Redis/rate limit, and 125 local handlers: Task 9.
- Reporting/read models and OLTP isolation: Task 10.
- PostgreSQL statements, indexes, EXPLAIN, pg_stat_statements, and online migrations: Task 11.
- 5,000 users, 16 GB pilot, Docker limits, Native Image, backup/RPO/RTO: Task 12.
- API profile/flag union, catalog ownership, and CI drift detection: Task 1.
- Flyway/H2/Testcontainers false proof: Task 2.
- Tenant/RLS role, worker context, pool reuse, and fail-closed behavior: Task 4.

### Placeholder and consistency checks

- No step uses `TBD`, `TODO`, “implement later”, or an unbounded “write tests” instruction.
- Every task identifies concrete files and a verification command.
- Hikari budgets are global across containers; adding replicas does not multiply the database budget.
- PostgreSQL outbox is durable truth; Redis is not a durable queue.
- JVM is the initial runtime control; Native Image is conditional on the native acceptance gate.
- Query slope is necessary but absolute statement/row/payload budgets are also enforced.
- The 16 GB host is a diagnostic/pilot target and is not declared to pass 5,000 concurrent users without the measured gates.
