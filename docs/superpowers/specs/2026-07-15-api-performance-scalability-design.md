# ERG Backend API Performance and Scalability Design

**Status:** Approved design direction on 2026-07-15

**Scope:** Current working tree of `erg-spring`

**Audience:** Backend engineers, database engineers, QA/performance engineers, and production operators

**Target:** All application REST and GraphQL APIs, with an acceptance target of 5,000 concurrent users and a path to scale beyond one physical host

## 1. Decision summary

Adopt a hybrid modular-monolith architecture:

- Keep Spring Data JPA for transactional writes and simple CRUD.
- Use explicit projections and Spring `JdbcClient` for bounded read models by default.
- Pilot jOOQ only for a small number of complex PostgreSQL read models where benchmarks prove that reducing many round trips to one to three SQL statements materially improves latency.
- Make GraphQL selection-aware. Use `@BatchMapping` or `BatchLoaderRegistry` only for genuine field-level relationship resolution, with request-scoped, tenant-aware keys.
- Move high-cardinality fan-out, email, push notification, and heavy report generation behind transactional outbox and bounded workers.
- Replace production business state held in process-local maps with PostgreSQL or Redis, or disable those endpoints in production until a durable implementation exists.
- Treat Native Image as a memory/startup optimization and deployment option, not as a database-latency solution. Every new library must pass an AOT/native smoke gate.

The initial production topology is one physical server in Vietnam running application, PostgreSQL, Redis, workers, reverse proxy, and observability containers. The current Aiven database is temporary. The 16 GB/i5-8500T class machine is a pilot baseline, not evidence that the 5,000-concurrent acceptance target has been achieved. Capacity acceptance remains benchmark-driven and may require hardware expansion.

## 2. Why this design is necessary

The audit found real N+1 paths, but N+1 is only one part of the latency problem. A single request can already be slow because every transaction pays fixed network and setup costs, several read models fan out blocking JDBC calls, large collections are loaded and filtered in memory, and synchronous external calls keep transactions open.

The current implementation cannot scale by simply starting more application containers:

- Local Caffeine caches are not coherent across replicas.
- GraphQL rate limiting is process-local.
- About 125 REST handlers use tenantless process-local maps or static responses.
- Each application replica currently requests a Hikari pool of 25 connections while the temporary Aiven PostgreSQL service exposes only 20 total connections.
- Some requests create several concurrent JDBC tasks, so a small number of requests can consume the entire database pool.
- Reporting and interactive OLTP traffic compete for the same PostgreSQL resources.

Docker containers on one physical machine improve packaging and process isolation, but do not add CPU, RAM, disk IOPS, or host-level availability. The design therefore protects the single-host deployment while keeping service boundaries portable to more hosts later.

## 3. Confirmed audit coverage

### 3.1 HTTP and GraphQL inventory

- 37 REST controller classes.
- 301 declared REST handler methods.
- 484 expanded `(HTTP method, path)` mappings after aliases are expanded.
- 298 handlers and 481 expanded mappings are unconditional.
- Three OIDC BFF handlers are conditional on `erg.auth.bff-enabled=true`.
- One GraphQL SDL file with 4 root query fields, 20 business query resolvers, and 9 mutations.
- Zero GraphQL subscriptions and zero `@BatchMapping` methods.
- 111 repository source files and 96 service source files were included in the source census.
- The existing Postman collection contains 516 requests and includes obsolete or duplicate entries, so it cannot be the authoritative endpoint catalog.

### 3.2 Runtime facts from the current temporary environment

The audit ran on branch `codex/lms-graphql-security-audit-plan` at Git HEAD `ed95711` with a heavily modified/untracked working tree. The findings therefore describe the inspected working-tree contents, not a clean committed revision.

- PostgreSQL server execution time for `SELECT 1`: approximately 0.065 ms.
- Client-observed `SELECT 1` median: approximately 89 ms; p95 approximately 115 ms.
- A new TLS connection: approximately 1,866 ms.
- Three separate transaction GUC statements: approximately 269 ms median.
- The current database is small; the largest table is approximately 770 rows and the database cache-hit ratio is approximately 99.98%.
- GraphQL access-log sample: p50 approximately 1,298 ms, p95 approximately 1,794 ms, maximum approximately 2,413 ms.
- `/lms/education-units` showed approximately 848 ms despite only one counted Hibernate business query.
- Temporary Aiven PostgreSQL: `max_connections=20`, `shared_buffers=190MB`, `work_mem=2MB`.

The checked query-count suite was invoked with:

    .\mvnw.cmd "-Dtest=LmsClassWorkspaceQueryCountIntegrationTest,LmsTeacherHomeworkWorkspaceQueryCountIntegrationTest,LmsAssignmentProgressWorkspaceQueryCountIntegrationTest,QuestionBankWorkspaceQueryCountIntegrationTest,StudentDashboardQueryCountIntegrationTest" test

It did not produce valid query-count proof: Docker/Testcontainers was unavailable, H2 fallback was selected, PostgreSQL Flyway migration V11 failed on `DELETE ... USING`, and the five-test selection ended with three errors and two skips.

These facts show that the temporary remote database makes round-trip count dominate latency. Moving PostgreSQL onto the local production host should reduce network time, but will not remove query fan-out, pool contention, CPU/heap amplification, or write N+1 behavior.

The current-environment measurements were collected on 2026-07-15 from a Vietnam-based client against PostgreSQL 17.10 on temporary Aiven infrastructure. The retained audit does not contain the complete raw sample count, exact Aiven region, or a reproducible benchmark bundle. These numbers are diagnostic evidence only and are not acceptance baselines. Every acceptance run must persist the source revision, dirty-tree fingerprint, application profile, container image digest, database settings, fixture manifest, client/load-generator location, commands/scripts, raw samples, and start/end timestamps.

### 3.3 Evidence classification

The issue register uses these evidence classes:

- **SC — static confirmed:** the code/configuration shape exists in the audited working tree.
- **CM — current environment measured:** a measurement was observed in the temporary environment, with the provenance limitation above.
- **CR — deterministic correctness/security defect:** the behavior can be proven from code/configuration and must be covered by a regression test.
- **PP — production-like proof required:** exact latency, throughput, memory, plan, pool, or tuning impact must be demonstrated on representative PostgreSQL data/topology.

`PP` does not weaken an `SC` or `CR` finding. It prevents a static risk or temporary-environment number from being presented as production capacity proof.

| ID | Evidence | Primary anchor | Required proof or regression |
|---|---|---|---|
| PERF-001 | SC, CM, PP | `TenantGucApplier` | One-statement GUC microbenchmark and RLS transaction test |
| PERF-002 | SC, CR, PP | `TenantAwareJpaTransactionManager`, migration V29 | Fail-closed role/policy and pool-reuse tests |
| PERF-003 | SC, CR, PP | `LmsReadModelService`, `CalendarReadService` | Tenant-context and Hikari concurrency tests |
| PERF-004 | SC, CR | `LmsRuntimeService`, `SessionService` | Cross-user/scope cache isolation tests |
| PERF-005 | SC, CR | calendar room repository, `DocumentService` | Cross-tenant lookup tests |
| PERF-006 | SC, CR | `LcmsReportGraphQlController`, `QuizReportService` | GraphQL contract and cache-hit tests |
| PERF-007 | SC, CR | `HibernateQueryCountFilter` | Concurrent request-local counter test |
| PERF-008 | SC, CR | `FlywayConfig`, H2 test profile | PostgreSQL integration-suite boot test |
| PERF-009 | SC, CM, PP | datasource/Hikari config, Aiven settings | Global connection-budget load test |
| PERF-010 | SC, PP | `ErgPrincipalService`, `AuthSessionRepository` | Cold/warm auth row-amplification benchmark |
| PERF-011 | SC, CR, PP | application timeout/rate config, SMTP sender | Timeout and dependency-degradation tests |
| PERF-012 | SC, PP | `PlatformHealthController` | Probe-frequency connection-pressure test |
| PERF-013 | SC, PP | `LmsRuntimeService` | 1/10/100/1,000 query-slope tests |
| PERF-014 | SC, PP | LMS/organization/authorization reads | Rows-read versus rows-returned tests |
| PERF-015 | SC, PP | Assignment/Attempt entities and mappers | DB bytes, JSON decode, heap benchmark |
| PERF-016 | SC, PP | `UserService`, employee security service | Deep-page rows-read benchmark |
| PERF-017 | SC, PP | unified security feed | Page-depth latency/heap benchmark |
| PERF-018 | SC, PP | `CalendarReadService` and controller | 1/31/365-day query/payload benchmark |
| PERF-019 | SC, PP | calendar, authorization, curriculum services | Route-specific absolute query budgets |
| PERF-020 | SC, PP | JPA configuration and write services | Prepared/batch statement benchmark |
| PERF-021 | SC, PP | `QuizQuestionLinkService`, draft reorder | Question/slide cardinality benchmark |
| PERF-022 | SC, PP | `LmsAssignmentCommandService` | Quiz/student fan-out benchmark |
| PERF-023 | SC, PP | authorization mutation audit | Affected-user transaction benchmark |
| PERF-024 | SC, PP | `NotificationCommandService` | User/device fan-out benchmark |
| PERF-025 | SC, PP | `NotificationOutboxWorker` | Sender latency/concurrency benchmark |
| PERF-026 | SC, PP | account/user mail flows | Transaction hold-time benchmark |
| PERF-027 | SC, PP | calendar normalizer/expansion/conflicts | Generated-object heap/CPU cap tests |
| PERF-028 | SC, PP | GraphQL workspace resolvers | Cheap/full selection SQL comparison |
| PERF-029 | SC, PP | GraphQL schema and resolver matrix | Combined-operation statement benchmark |
| PERF-030 | SC, PP | GraphQL guard/instrumentation | Alias/fragment cost-rejection tests |
| PERF-031 | SC, CR, PP | `GraphQlGuardFilter` | Body/variable/date/list boundary tests |
| PERF-032 | SC, CR | `CalendarDataLoaderRegistry` | Request/tenant isolation tests or removal |
| PERF-033 | SC, PP | `GraphQlContextInterceptor` | Metric-cardinality budget test |
| PERF-034 | SC, CR, PP | CHM-backed service classes | Restart, replica, tenant, heap tests |
| PERF-035 | SC, PP | broad cache eviction annotations | Cross-tenant miss-storm benchmark |
| PERF-036 | SC, PP | `CacheConfig` runtime-package cache | Weight/RSS/eviction benchmark |
| PERF-037 | SC, CR, PP | local cache/rate limiter | Cross-replica consistency tests |
| PERF-038 | SC, PP | CRM/report/school/feed services | OLTP contention and aggregate benchmark |
| PERF-039 | SC, CM, PP | live catalog audit | `pg_stat_statements` plus EXPLAIN proof |
| PERF-040 | SC, PP | Flyway index migrations | Staging lock-duration deployment test |
| PERF-041 | SC, PP | native Maven plugin/runtime hints | Native build and authenticated E2E smoke |
| PERF-042 | SC, PP | `pom.xml` and source usage scan | Dependency/AOT/image-size comparison |

## 4. Confirmed issue register

Each item below must map to an implementation task and an automated acceptance check. The evidence table above records whether the item is static, measured, deterministic, or requires production-like proof.

### P0: correctness, tenant isolation, and measurement integrity

1. **PERF-001 — Tenant GUC is three network round trips per transaction.** `TenantGucApplier` sends tenant, user, and portal through three separate `select set_config` statements. Only tenant is currently referenced by RLS policies.
2. **PERF-002 — RLS setup fails open for the currently protected quiz-domain tables.** `TenantAwareJpaTransactionManager` catches GUC setup failure and continues; the current policies also permit access when the tenant GUC is absent or empty. Maintenance access must use an explicit privileged path rather than an unset request context.
3. **PERF-003 — Thread-bound transaction and tenant context are lost.** `LmsReadModelService` and `CalendarReadService` submit blocking repository calls to the common ForkJoinPool. Spring transactions and `TenantContext` do not automatically propagate to those threads, so those calls can also miss transaction-local RLS context.
4. **PERF-004 — Cache scope defects can return incorrect data.** `lmsClassesBySchool` omits managed class scope/user identity, and `sessionBootstrap` omits current class even though both values affect the response.
5. **PERF-005 — Tenant lookup defects exist.** Calendar room duplicate lookup is not tenant-scoped. Document map operations also ignore the stored tenant.
6. **PERF-006 — GraphQL report contract mismatch.** Schema/controller accept `quizVersionId`, while the service treats it as a quiz ID; the intended cached method is then self-invoked and bypasses the Spring cache proxy.
7. **PERF-007 — Existing per-request query count is unreliable.** `HibernateQueryCountFilter` subtracts process-global Hibernate statistics, so concurrent requests contaminate each other; asynchronous GraphQL completion also makes lifecycle attribution unsafe.
8. **PERF-008 — Flyway/test configuration is inconsistent.** Custom Flyway creation always invokes migrations and ignores the normal `spring.flyway.enabled=false` expectation. PostgreSQL-specific migrations then fail against the H2 fallback, preventing query-count tests from proving their claims.

### P1: common request path and connection safety

9. **PERF-009 — Hikari budget exceeds the temporary database budget.** One instance is configured for 25 connections while the database exposes 20. A multi-container deployment would multiply this mismatch.
10. **PERF-010 — Authentication cold path is expensive.** Bearer authentication loads a five-collection user graph and active scoped assignments. The join may amplify rows and must be measured on representative role/permission cardinality.
11. **PERF-011 — Application timeout properties are partly declarative only.** Several HTTP timeout/rate-limit properties are present without a matching general REST implementation. SMTP has no repository-configured connect/read/write timeout.
12. **PERF-012 — Readiness performs live blocking dependency probes.** High-frequency probes can compete for database/Redis connections if orchestrator intervals or failure behavior are misconfigured.

### P2: REST read amplification and pagination

13. **PERF-013 — Classic REST N+1 exists.** Student assignments/scores, quiz-student listing, and membership/class checks perform repository calls per assignment or attempt.
14. **PERF-014 — Full-tenant reads precede JVM filtering.** LMS classes, assignments, education units, student groups, and several authorization/catalog paths load broad collections before scope filtering.
15. **PERF-015 — Large JSONB entities are loaded unnecessarily.** Attempt answer/event payloads and assignment JSON fields are materialized for summaries and counts that do not need them.
16. **PERF-016 — Pagination is implemented after full reads.** User activity, employee security events, login attempts, and several other list endpoints load complete histories before slicing.
17. **PERF-017 — Unified feed cost grows with page depth.** Four sources each fetch `page × limit`, then Java merges and sorts the results.
18. **PERF-018 — Calendar ranges and catalogs are unbounded.** Workspace endpoints can return arbitrary date ranges and duplicate reference/catalog queries.
19. **PERF-019 — Fixed duplicate reads exist.** Calendar scope checks and command services reload the same object; feature config and curriculum hard-delete paths also repeat reads.

### P3: write fan-out and external I/O

20. **PERF-020 — JDBC write batching is not configured.** Assignment recipients/resources, quiz sections/questions/links, grading rows, attempt events, preferences, and role audit/version records are persisted per item.
21. **PERF-021 — Quiz autosave rewrites all links.** Each mutation validates every question separately, deletes all links, and reinserts every link. Reordering also contains quadratic list work.
22. **PERF-022 — Assignment distribution is synchronous fan-out.** Quiz-version checks/resources and recipient rows scale with quiz/student count inside the request transaction.
23. **PERF-023 — Authorization mutations fan out per affected user.** Each user can incur locking, version, activity, and security-event operations in one large transaction.
24. **PERF-024 — Notification enqueue is `U + U×D` fan-out.** User/device checks and outbox rows scale with recipients and devices.
25. **PERF-025 — Firebase dispatch is sequential.** Manual outbox processing may perform up to 500 blocking HTTP calls on the request thread, with per-item result transactions.
26. **PERF-026 — SMTP is called inside database transactions.** Account creation, credential reset, and recovery challenge can hold locks and Hikari connections while waiting on mail infrastructure.
27. **PERF-027 — Calendar bulk input permits explosive expansion.** Existing bounds can still generate hundreds of thousands of events and millions of teacher-assignment objects; conflict detection includes quadratic group scans.

### P4: GraphQL cost and over-fetching

28. **PERF-028 — GraphQL is eager at the workspace boundary.** Child fields use default property resolution and do not create field-level N+1, but each business resolver builds the complete DTO regardless of the selection set.
29. **PERF-029 — One combined GraphQL request can be extremely expensive.** Static estimates approach 69 LMS business statements, 53 LCMS statements, and about 128 total when all business fields are selected; these estimates exclude authentication and transaction-GUC statements.
30. **PERF-030 — Complexity does not represent database cost.** A cheap-looking scalar selection can trigger approximately 16 calendar statements, and allowed aliases can multiply that work while consuming one rate-limit unit.
31. **PERF-031 — GraphQL variables/body are insufficiently bounded.** Query text is capped, but the filter reads the full body before enforcement and large variable values/filter lists/date ranges remain possible.
32. **PERF-032 — Calendar DataLoader configuration is dormant and unsafe if activated.** Two singleton raw DataLoader beans have no consumers, are not registered through request-scoped Spring batching, and use repository methods without tenant predicates.
33. **PERF-033 — GraphQL metrics have high-cardinality tags.** Client-controlled operation names and tenant IDs should not be unbounded metric labels.

### P5: state, cache, reporting, schema, and native readiness

34. **PERF-034 — About 125 handlers are process-local stubs.** AI, catalog, CMS, crawler, documents, engagement, operations, platform, SEO, trending, and webhook behavior is often stored in unbounded tenantless maps or returned as fabricated static state.
35. **PERF-035 — Cache invalidation is overly broad.** Many mutations clear entire caches across tenants, causing avoidable cold-start storms.
36. **PERF-036 — Cache memory is bounded only by entry count.** `quizRuntimePackage` can retain up to 10,000 potentially large packages without weight-based limits.
37. **PERF-037 — Local cache/rate-limit state prevents clean horizontal scaling.** Replica-local Caffeine and GraphQL rate limits diverge between containers.
38. **PERF-038 — Reporting runs against OLTP entities.** CRM performance, assignment reports, school operations, security feeds, and analytics load/aggregate broad row sets in memory.
39. **PERF-039 — Missing and duplicate indexes require workload validation.** Live catalog inspection found 74 foreign keys without a matching leading index and three duplicate-index pairs. Adding all 74 blindly is explicitly rejected; priority must come from measured workload and `EXPLAIN (ANALYZE, BUFFERS)`.
40. **PERF-040 — Production index deployment can lock tables.** Existing Flyway index migrations do not use an explicit online/concurrent production strategy.
41. **PERF-041 — Native readiness is unproven.** The native plugin and a few runtime hints exist, but there is no successful native artifact/CI smoke proof for the complete application.
42. **PERF-042 — Unused/heavy dependencies increase AOT surface.** H2 is runtime-scoped, Mongo/Redis starters are largely excluded or unused, and Resilience4j dependencies have no source usage. Dependency cleanup must be evidence-based and preserve intended future features.

## 5. Alternatives considered

### 5.1 JPA-only hardening

Use projections, entity graphs, batch `IN` queries, Hibernate batching, and pagination while preserving all read/write service shapes.

**Advantages:** smallest dependency and AOT change; suitable for CRUD and many bounded list APIs.

**Rejected as the only strategy because:** calendar, workspace, feed, and reporting paths would still need several round trips and complex manual aggregation. Entity graphs can also create Cartesian amplification.

### 5.2 Hybrid JPA plus explicit read models — selected

Use JPA for writes, projection/JdbcClient for most reads, and targeted jOOQ for a small number of measured PostgreSQL hotspots. Preserve the modular monolith and introduce outbox/worker and reporting read-model boundaries.

**Advantages:** incremental rollout, explicit SQL/query budgets, good Native Image control, and no premature distributed-system operational burden.

**Trade-off:** engineers maintain read contracts separately from write entities. This is accepted because read shapes and write invariants already differ materially.

### 5.3 Broad Blaze-Persistence adoption or immediate microservices

**Rejected for the first optimization program.** Blaze-Persistence introduces deep ORM/GraphQL/AOT integration risk before baseline defects are fixed. Microservices on one physical server add network, deployment, data consistency, and observability complexity without adding host capacity or availability.

## 6. Target architecture

### 6.1 Initial single-host topology

1. Reverse proxy terminates TLS, enforces request-size limits, provides coarse admission control, and forwards trusted request identity headers only from an authenticated boundary.
2. One or two application containers handle interactive REST/GraphQL requests. JVM is the initial runtime unless the native acceptance gate has passed; Native Image becomes the preferred runtime only after an authenticated canary matches JVM correctness and meets the performance/RSS gates. The JVM image remains the rollback control.
3. Hikari pools are small and globally budgeted. The sum of every app pool, worker pool, migration/operations reserve, and emergency reserve must remain below the PostgreSQL connection budget.
4. PostgreSQL runs on local NVMe-backed storage and receives explicit memory/CPU/I/O priority over app and monitoring containers.
5. Redis holds distributed rate-limit state, ephemeral job-progress acceleration, and only those business caches requiring cross-replica consistency. PostgreSQL outbox/job rows remain durable truth. A small local L1 cache is allowed only with tenant-correct keys and cataloged staleness.
6. Worker containers process outbox jobs for email, push notifications, recipient fan-out, compile/export, and reports with bounded concurrency and retry/dead-letter policy.
7. Prometheus-compatible metrics and structured logs observe the system without probing dependencies at a rate that consumes material database capacity.
8. Backups leave the physical host, and restore drills are part of release readiness.

### 6.2 Future multi-host topology

Application and worker containers remain stateless and can move behind a load balancer. PostgreSQL and Redis interfaces remain unchanged. A transaction pooler, PostgreSQL replica, or separate reporting database is introduced only after measurement shows that the single-host primary or connection model is the bottleneck.

## 7. Request data flows

### 7.1 Interactive read

1. Proxy validates size/rate and forwards the request.
2. Authentication resolves a compact principal from a tenant-aware cache or bounded query.
3. Service validates tenant/scope before expensive reads.
4. Read model executes a fixed query budget, pages in PostgreSQL, and maps directly to a response DTO.
5. Optional cache uses a complete tenant/user/scope/input key and targeted invalidation.
6. Response metrics record route template, status, latency, statement count, rows/payload estimate, and pool wait without high-cardinality user/tenant labels.

### 7.2 High-cardinality write

1. Request validates cardinality and idempotency.
2. A short transaction stores the command, immediate invariant changes, and outbox record.
3. API returns the committed resource or an accepted job identifier.
4. A bounded worker claims jobs with skip-locked/lease semantics, writes in batches, and records progress.
5. Retries are idempotent; permanent failure moves to a dead-letter state visible to operators.

### 7.3 Reporting

1. Small pre-aggregated reports return synchronously from aggregate/read-model tables.
2. Heavy reports create a job and return an acknowledgement within 500 ms p95.
3. Worker reads a stable snapshot or aggregate source, generates the result, and stores job/output metadata.
4. The primary OLTP workload has an explicit concurrency/statement-timeout budget separate from reporting.

## 8. Technology decisions

### 8.1 N+1 and query tooling

- Spring GraphQL `@BatchMapping` is approved only for genuine nested field resolution.
- Spring Data projection and JPQL constructor projection are preferred for clear bounded read shapes.
- Spring `JdbcClient` is the first escape hatch for aggregate/native PostgreSQL reads because it adds no new query DSL dependency.
- jOOQ Community Edition is approved as a pilot for one calendar/workspace/report hotspot after JVM and Native Image smoke tests. The pilot passes only if it reduces at least four sequential business statements to at most two or improves representative p95 latency by at least 30%, without response-contract drift, native reflection errors, or more than one generated-code step in the Maven build. Commercial-edition features require a separate license decision.
- Blaze-Persistence is not part of the first program.
- Hibernate global default batch fetching is not accepted as a substitute for endpoint-specific query budgets.
- Test query attribution uses a test-scoped JDBC counting proxy tied to request/test context; it is excluded from the production/native artifact. Production query ranking uses `pg_stat_statements`, route metrics, and Hikari metrics rather than full SQL logging.

### 8.2 PostgreSQL

- Enable `pg_stat_statements` in the production-like environment.
- Use `EXPLAIN (ANALYZE, BUFFERS)` on captured representative queries before adding indexes.
- Page/filter/order inside SQL. Use keyset pagination for deep feeds where stable ordering is available.
- Use JDBC batching and driver rewrite only after write-count and correctness tests prove the configuration.
- Use backward-compatible migrations. Large production indexes require a non-blocking/concurrent deployment procedure outside a single transactional startup migration.
- The application database role must not own tenant tables and must not have `BYPASSRLS`. Tenant tables protected by RLS use `FORCE ROW LEVEL SECURITY` where the owner could otherwise bypass policy. Request and worker transactions install tenant context with transaction-local `set_config(..., true)` before tenant SQL.
- Missing, blank, malformed, or failed tenant context aborts request/worker transactions. Migrations and maintenance use a separately provisioned, audited role rather than the application role with an absent GUC.
- Pool-reuse tests cover commit, rollback, exception, cancelled request, and tenant A→tenant B reuse on the same physical connection.

### 8.3 GraphQL

- Business query cost is based on measured resolver/database cost, not only AST field count.
- Selection-aware resolvers do not execute unselected workspace sections.
- Body bytes, variable nesting/cardinality, aliases, date spans, pages, and list inputs are bounded before materialization.
- A timeout response is not considered successful cancellation unless downstream JDBC/worker work is demonstrably stopped or bounded.
- DataLoader keys include tenant and any security scope affecting the result, and loaders are request-scoped.

### 8.4 Cache and Redis

- Correctness and tenant isolation precede cache hit rate.
- Spring Data Redis with Lettuce is the selected Redis client because it is already represented in the dependency stack. Distributed cache is used only when cross-replica coherence is required.
- Keys contain tenant plus every response-affecting permission/scope/input dimension.
- Maximum stale authorization allow is zero. In the first rollout, principal/session/permission allow decisions are not cached across requests; a compact PostgreSQL projection is authoritative. Redis may cache revocation/deny hints to fail closed, but a missing/unavailable Redis value never creates an allow. Any later security-allow cache requires its own atomic consistency design and approval.
- Non-security business-cache invalidation runs after commit, targets tenant/entity keys, and carries a monotonically increasing generation. L1 entries are accepted only when their generation matches L2. A route may tolerate at most 30 seconds of business-data staleness only when that policy is recorded in the API catalog; otherwise its cache is disabled. Whole-cache eviction requires an explicit justification and metric.
- Redis outage behavior is explicit: durable jobs continue through PostgreSQL outbox; security reads fall back to PostgreSQL; expensive public/GraphQL traffic uses a conservative local emergency limiter or returns controlled 503; business cache misses query PostgreSQL within the DB bulkhead.
- Large caches are bounded by weight/bytes as well as count, and use single-flight/stampede protection for expensive reads.
- Cross-replica tests race write/commit/invalidation/read and verify that no authorization allow survives revocation, cataloged business staleness never exceeds its limit, and no tenant receives another tenant's value.

### 8.5 Native Image

- Maintain JVM and native build paths during rollout.
- Each new reflective/proxy-heavy library requires AOT processing, native compilation, startup smoke, and an authenticated database flow.
- Performance comparison includes RSS, startup, throughput, p95/p99, CPU, and database statement count. Native is not accepted merely because compilation succeeds.

### 8.6 Operational implementation choices

- k6 is the selected load-test runner. Load generators run on separate machines so their CPU/network do not consume the production host budget.
- Initial durable background execution uses PostgreSQL transactional outbox plus bounded Spring workers claiming rows with lease/`SKIP LOCKED` semantics. Redis is not the source of truth for durable jobs.
- Redis Lua/token-bucket operations provide distributed rate limiting after correctness tests. Reverse-proxy limits remain a coarse outer guard.
- PgBouncer is deferred on the single-host first release. Introduce transaction pooling only when multiple app/worker pools or connection acquisition measurements justify it, and only after transaction-local GUC/prepared-statement compatibility tests pass.

## 9. Workstream decomposition and order

The program is intentionally split because each workstream must be independently testable, deployable, and reversible.

1. **Foundation and correctness:** authoritative route catalog, PostgreSQL test foundation, query/connection metrics, GUC/RLS correctness, cache-scope defects, and GraphQL contract defects.
2. **Common request path:** authentication projection/cache, GUC round-trip reduction, transaction/context safety, pool budget, timeouts, and overload handling.
3. **REST reads:** LMS N+1, database pagination, projection/JSONB reduction, calendar query consolidation, and fixed duplicate reads.
4. **Write path:** JDBC batching, bulk repository operations, outbox/worker, notification/email, assignment/quiz/authorization fan-out, and calendar expansion caps.
5. **GraphQL read models:** selection-aware execution, cost weights, tenant-aware batching, body/cardinality guards, and a jOOQ/JdbcClient pilot.
6. **State and cache productionization:** replace or disable the 125 process-local handlers, introduce Redis-backed shared concerns, and remove broad invalidation.
7. **Reporting:** aggregate tables/materialized views, async jobs, bounded OLTP access, and a future read-replica interface.
8. **Load/native/deployment:** workload models, 5,000-concurrent gates, container resource budgets, native comparison, backup/restore, and future multi-host scale-out.

Dependencies are one-way: measurement/correctness precedes optimization; bounded query/write paths precede full load tests; JVM correctness precedes native acceptance.

## 10. Verification strategy

### 10.1 Authoritative API coverage

- Build the union of REST routes from these supported configurations: `prod` with BFF disabled, and `prod,enterprise-auth` with `erg.auth.bff-enabled=true`. The `test` profile validates trusted-header test plumbing but cannot replace either production mapping census.
- Enumerate REST routes from Spring `RequestMappingHandlerMapping`, expand method/path aliases, and normalize each entry as `(required profile/flag set, HTTP method, canonical path template, handler)` before comparing it with a committed performance catalog.
- Every REST catalog entry records owner, production/stub disposition, auth/scope rule, side-effect class, pagination/cardinality limits, cache policy, statement/row/payload budget, dependency class, and required test/load scenario. A process-local stub must be marked `disable-in-prod` or linked to its persistence plan.
- Parse GraphQL SDL/runtime wiring and require every root, nested business field, and mutation to have an owner, risk tier, authorization expectation, selection/alias/fragment cost scenario, statement/row/payload budget, and test scenario.
- CI fails when a new route or GraphQL operation lacks catalog classification.
- Framework endpoints such as Actuator/Springdoc are cataloged separately from application controllers.

### 10.2 Query-slope tests

For each suspected N+1/fan-out path, run PostgreSQL fixtures at cardinalities 1, 10, 100, and where safe 1,000. Constant slope is necessary but not sufficient: every route/resolver must also remain below its absolute catalog budget for statements, rows read, payload bytes, and latency. A bulk write may grow in affected rows but must use a bounded number of prepared/batched executions rather than one round trip per row.

Default budgets apply until an operation-specific plan establishes a stricter value:

| Class | Business SQL budget | Row/payload budget |
|---|---:|---|
| Simple detail/CRUD read | at most 3 | one bounded aggregate/entity response; no unrelated collection graph |
| Paged list with enrichment | at most 5 | page size at most 100; rows read at most 3× returned rows plus fixed lookup rows |
| Interactive workspace | at most 8 during JPA migration; target at most 3 for an approved JdbcClient/jOOQ hotspot | payload at most 1 MiB; every child list paged/bounded |
| Synchronous aggregate/report | at most 3 aggregate statements | payload at most 2 MiB; otherwise async report job |
| One GraphQL HTTP operation | at most 25 total business statements after alias/fragment expansion | aggregate selected payload at most 2 MiB |

An exception requires an explicit catalog value, `EXPLAIN`/load evidence, reviewer approval, and a time-bounded remediation issue. A constant 128-statement GraphQL request therefore fails even though its slope is constant.

Priority scenarios include:

- Student assignments and scores.
- Quiz students and assignment progress.
- Assignment recipients/resources.
- Quiz autosave/publish.
- Attempt sync/grading.
- Authorization bulk mutations.
- Notification enqueue and outbox dispatch.
- User bulk status/delete.
- Calendar workspace and bulk expansion.
- Activity/security feeds and deep pagination.
- CRM/report aggregation.

### 10.3 Cache/security tests

- Two users with identical tenant/school inputs but disjoint managed classes never share cached class results.
- Current class changes are reflected by session bootstrap.
- Two tenants using identical entity IDs never share DataLoader/cache data.
- Application/worker database roles are non-owner, lack `BYPASSRLS`, and cannot assume the maintenance role.
- Missing, blank, malformed, or failed GUC application aborts request/worker transactions.
- The same physical pooled connection is reused across tenant A commit, tenant B rollback, exception, and cancellation without tenant leakage.
- Worker/outbox claims restore tenant context from the durable job record before tenant SQL and reject records with missing/mismatched tenant identity.
- Out-of-scope GraphQL requests execute zero expensive business queries.
- Cache expiry and 20 concurrent identical cold requests do not create an uncontrolled stampede.
- Cross-replica commit/invalidation/read races satisfy zero stale authorization allow and the per-route non-security staleness limit.

### 10.4 Load profiles

Production access logs replace the synthetic mix when representative logs exist. Until then, the following is a deliberately defined acceptance envelope, not a claim about real user percentages.

#### Versioned acceptance dataset

- Deterministic seed and manifest committed with the load suite.
- 10 tenants; 5,000 accounts comprising 4,500 students, 450 teachers, and 50 admin/report users.
- 250 classes, 20 subjects, 40 assignments per class, 50 questions per representative quiz, and up to three attempts per completed assignment.
- Ninety days of calendar data with enough events/teachers/rooms to reach at least 100,000 event/assignment rows.
- At least 1,000,000 activity/security-feed rows for deep-pagination scenarios.
- Tenant IDs, scope distributions, cache keys, database statistics, and fixture checksums recorded in the run artifact.

#### Environment and run manifest

- Load generators run on separate machines on the same Vietnam LAN/region and never share CPU/RAM/disk with the server under test.
- The accepted topology records host CPU/RAM/disk, container limits, app/JVM-or-native image digests, replica count, Hikari budgets, PostgreSQL/Redis versions/settings, network path, and source/dirty-tree fingerprints.
- Every scenario runs once with warm auth/business caches and once after controlled cache invalidation/restart.
- Warm-up is 10 minutes, ramp is 15 minutes, accepted steady duration is 30 minutes, and soak is 2 hours at 50% of accepted steady throughput.

#### Steady learning profile

- Closed model: 5,000 simultaneously active virtual users with uniformly distributed 5–15 second think time.
- Open-rate confirmation: at least 500 completed requests/second for 30 minutes.
- Synthetic request mix: 55% content/class/assignment reads, 20% attempt autosave/sync, 10% dashboard/calendar, 8% auth/session/profile, 5% teacher authoring/assignment commands, and 2% bounded report/admin requests.
- Capacity passes only if at least 99.9% of offered accepted-load requests complete without application error, 429, or 503. Deliberate rejections are counted as failures in this scenario.

#### Deadline submission burst

- All 5,000 student VUs begin sync/submit workflows within a 60-second release window, including safe idempotent retries for injected connection loss.
- Minimum completed acknowledgement throughput is 84 submissions/second over the release window; p95 acknowledgement remains at or below 1 second.
- Duplicate grading/progress/outbox effects are zero. At least 99.9% complete without 429/503; failed transient requests recover within the documented retry window.

#### Teacher authoring and reporting contention

- Up to 450 teacher VUs autosave drafts; controlled subsets publish quizzes and distribute assignments while steady student traffic continues.
- Fifty concurrent report jobs are submitted. Job acknowledgement is at or below 500 ms p95; heavy generation executes only in the bounded worker pool.
- Interactive SLOs and DB connection reserves remain satisfied while reports run.

#### Cache-cold, restart, and degradation

- Restart/cold-cache runs keep the 5,000-VU closed model active and permit at most 0.1% 429/503 responses.
- Dependency-degradation and deliberate-overload tests are separate from capacity acceptance. They may reject work, but must keep PostgreSQL responsive, preserve committed data, cap queues/memory, and return to accepted SLOs within two minutes after the fault is removed.
- SMTP/Firebase/Redis delay, worker pause, database connection pressure, and one app-container restart are injected independently so failure attribution remains clear.

The 16 GB pilot host runs the same scripts to locate saturation and validate safety, but it does not receive a 5,000-user capacity pass unless every gate above succeeds.

## 11. Acceptance gates

### 11.1 Correctness and security

- Zero known cross-tenant cache, loader, or repository lookup defects.
- Request traffic is fail-closed when tenant context cannot be installed.
- Application/worker roles are non-owner, lack `BYPASSRLS`, and pass commit/rollback/pool-reuse tenant-isolation tests; maintenance credentials are not available to request containers.
- All high-cardinality commands are idempotent or have documented non-retry semantics.
- Every application route/GraphQL operation is present in the authoritative catalog.

### 11.2 Performance

- Interactive API target: p95 at or below 500 ms and p99 at or below 1 second on the accepted production topology.
- Submission acknowledgement target: p95 at or below 1 second.
- Heavy reports return a job acknowledgement at or below 500 ms p95 and do not hold an HTTP request/DB transaction for full generation.
- Total application-error plus 429/503 rate is below 0.1% during every accepted-capacity scenario. Rejections are excluded only from separately labeled deliberate-overload/degradation tests and can never be used to claim 5,000-user capacity.
- No Hikari acquisition timeout occurs; connection-acquisition p95 is at or below 50 ms and the pool has zero continuously pending borrowers for more than one second during accepted steady load.
- Query-count slope is constant for bounded reads and every route/resolver satisfies the absolute statement/row/payload catalog budget.
- No full-table/full-tenant entity materialization for paginated endpoint results unless a measured, documented exception is approved.
- During accepted steady load, PostgreSQL/container CPU averages below 70% with no five-minute interval above 85%, host memory remains below 85% with no swap/OOM, disk utilization averages below 80%, and database lock/statement timeouts remain below 0.1% of requests.

### 11.3 Resilience and operations

- Overload returns controlled 429/503 responses instead of exhausting PostgreSQL.
- Worker retries, leases, and dead-letter behavior are observable and idempotent.
- Initial single-host service objective is 99.5% monthly availability excluding at most one announced 30-minute maintenance window. This is not high availability and is revised only after a second failure domain exists.
- Encrypted off-host base backups plus WAL archiving provide an RPO at or below 15 minutes and an initial-host RTO at or below 4 hours. A full restore is rehearsed quarterly and timed from empty host/database to authenticated API smoke success.
- The system returns to accepted SLOs within two minutes after a tested transient Redis/SMTP/Firebase/worker-pressure fault is removed.
- Application can run in JVM mode if a native regression is found.
- Native build, authenticated smoke, GraphQL smoke, Flyway migration, and PostgreSQL integration tests pass in CI.

## 12. Rollout and rollback

Observability and rollback switches are deployed before behavior changes. Security/correctness defects precede caching or query consolidation.

### 12.1 GUC/RLS transition

1. Provision separate migration/maintenance and non-owner application/worker roles; verify the application roles lack `BYPASSRLS` and cannot use maintenance credentials.
2. Deploy application code that installs validated transaction-local tenant context and aborts on failure while existing policies are still rollback-compatible.
3. Run commit/rollback/pool-reuse and worker tenant tests in staging; canary request traffic and verify tenant audit metrics.
4. Replace permissive absent/blank-GUC policy branches with strict tenant equality and `FORCE ROW LEVEL SECURITY` for owned tenant tables.
5. Roll back application code only while the prior compatible policy is intentionally retained. After strict policy activation, rollback uses the previous tested application image that already installs GUC; it never restores fail-open request behavior as an ordinary performance rollback.

### 12.2 Outbox transition without duplicate side effects

1. Add outbox schema, unique idempotency/event key, status/lease fields, and metrics; worker remains disabled.
2. Producers write non-claimable `SHADOW` outbox rows while the synchronous sender remains authoritative. Shadow rows record comparison/result metadata but can never be sent by a worker.
3. For one event type/tenant canary, atomically switch the feature flag so new rows are `PENDING`, synchronous send is skipped, and the worker is enabled. The unique event key protects retries; historical `SHADOW` rows remain non-claimable.
4. Expand event types/tenants at 5%, 25%, then 100%; compare sent/failed/duplicate counts and queue age at each step.
5. Drain all old synchronous work before removing the old path. Rollback routes only new events back to the legacy sender; already committed `PENDING` rows continue draining through the worker so the same external effect is never handed to two send mechanisms.

### 12.3 Read-model transition

1. Execute the new read model in shadow mode for sampled read-only traffic and compare normalized DTOs, statement/row budgets, and tenant/scope decisions; only the old response is returned.
2. Enable the new route/resolver behind a per-operation flag for 5%, 25%, then 100% of traffic.
3. Rollback is a flag change to the old read path; migrations remain additive until the new path has completed at least 24 hours and one verified deployment at 100%.

### 12.4 Redis/cache transition

1. Deploy Redis connectivity, outage fallback, generation keys, and metrics with cache reads disabled.
2. Enable L2 business-cache reads for a tenant canary; security-sensitive values continue to fall back to PostgreSQL on Redis failure.
3. Enable after-commit versioned invalidation and run cross-replica write/read races.
4. Add optional L1 only after generation matching passes. Rollback disables L1/L2 reads; PostgreSQL remains authoritative and durable jobs remain in the PostgreSQL outbox.

### 12.5 Native runtime transition

1. Build JVM and native images from the same revision and run migration, authentication, REST, GraphQL, cache, worker, and database smoke tests.
2. Run the native image in non-production load comparison, then one canary container at 5%, 25%, and 100% traffic if correctness/RSS/latency gates pass.
3. Rollback routes traffic to the retained JVM image. Native-specific migrations are forbidden.

### 12.6 Worker and migration safety

- Worker shutdown first fails readiness, stops claiming, finishes or releases leased jobs, records lease state, and only then exits.
- Large index creation is executed through an approved concurrent/online procedure with lock monitoring and cancellation criteria, not an unbounded application-startup migration.
- Destructive/irreversible schema work requires a tested backup/restore, explicit maintenance window, completed dual-read/write cutover, and a separate approval; it is never bundled with a latency optimization deployment.
- Canary load increases in controlled steps and stops automatically if CPU exceeds 85% for five minutes, disk utilization exceeds 90% for one minute, Hikari has pending borrowers for more than one second, accepted-load error/rejection exceeds 0.1%, or a tenant/correctness assertion fails.

## 13. Resource-budget policy for the initial host

The following is the conservative starting profile for the known 16 GB pilot host. It is a saturation-discovery configuration, not a 5,000-user capacity claim:

| Consumer | Initial 16 GB budget |
|---|---:|
| OS, filesystem cache, Docker overhead, emergency reserve | at least 3 GiB |
| PostgreSQL process/container working budget | at most 6 GiB, starting `shared_buffers` around 2 GiB and `work_mem` at 4 MiB |
| Interactive app containers combined | at most 2 GiB; start with one container, add the second only after RSS/load proof |
| Worker containers combined | at most 1 GiB with independent concurrency caps |
| Redis | 512 MiB `maxmemory` with no durable-job dependency |
| Reverse proxy | 256 MiB |
| Metrics/logging containers and bounded retention | at most 1.5 GiB |
| Unallocated safety/headroom | at least 1.75 GiB |

The initial local PostgreSQL experiment uses `max_connections=40`; no component may consume that budget implicitly. With two app containers, each starts at Hikari maximum 8/minimum idle 1, workers share at most 4, migration/probe tooling uses at most 4, and at least 16 connections remain unavailable to normal app borrowing for administration, autovacuum/background work, transient overlap, and safety. A one-app pilot does not reassign the unused second-app budget automatically. Connection acquisition waits at most one second before controlled overload handling.

Any hardware upgrade produces a new versioned resource manifest from benchmark results rather than multiplying pool sizes by RAM. The deployment always enforces these invariants:

- PostgreSQL has reserved memory and disk I/O priority.
- Container memory limits leave an operating-system/filesystem-cache reserve.
- The 16 GB profile sets Redis `maxmemory=512mb` and `maxmemory-policy=noeviction`; every cache/rate key has an explicit TTL, cache-write failure falls back to PostgreSQL, and durable jobs remain in PostgreSQL outbox.
- App and worker concurrency is capped independently from incoming HTTP concurrency.
- Sum of Hikari pools plus operational reserve never exceeds the PostgreSQL connection budget.
- Reporting and workers cannot consume all DB connections or CPU needed by interactive requests.
- Local metrics/log retention is at most seven days and at most the smaller of 20 GiB or 10% of the data disk; off-host archival is configured separately.

## 14. Documentation and plan outputs

After this design is approved in its written form, create separate implementation plans in this order:

1. Authoritative REST/GraphQL catalog and route-metric plan.
2. PostgreSQL Testcontainers, Flyway, and request-local query-attribution plan.
3. Tenant GUC, database-role, RLS, and cross-tenant cache-correctness plan.
4. Authentication cold path, timeout, Hikari/global connection budget, and overload plan.
5. LMS student assignment/score/quiz-student REST read plan.
6. Calendar read consolidation, range bounds, and bulk-expansion safety plan.
7. User/activity/security-feed/authorization pagination and duplicate-read plan.
8. Projection/JSONB reduction and workload-driven index plan for remaining REST reads.
9. Hibernate/JDBC batching and reusable bulk-repository foundation plan.
10. Quiz autosave/publish and assignment distribution fan-out plan.
11. User/authorization bulk mutation and audit/version batching plan.
12. Notification/email transactional-outbox and bounded-worker plan.
13. GraphQL body/cardinality/cost/metric guard plan.
14. GraphQL selection-aware workspace and tenant-aware batch-loader plan.
15. JdbcClient/jOOQ hotspot pilot plan with explicit adoption/rollback benchmark.
16. Redis versioned cache, distributed limiter, and cross-replica consistency plan.
17. Process-local API disposition plan, followed by separate persistence plans for catalog/CMS/documents, crawler/SEO/engagement, and AI/operations/trending domains that remain production requirements.
18. Reporting aggregate/materialized-view and async-report plan.
19. k6 dataset/scenario/capacity/soak test plan.
20. JVM/native, Docker resource, backup/restore, and future multi-host deployment plan.

Each plan must identify exact files, failing tests first, commands and expected results, rollback points, and focused commits. No implementation plan may silently mix unrelated workstreams.

## 15. Source evidence anchors

- `src/main/resources/application.yml`: datasource pool, JPA, GraphQL, cache-adjacent and profile configuration.
- `src/main/java/com/erg/ergspringboot/config/persistence/TenantAwareJpaTransactionManager.java`
- `src/main/java/com/erg/ergspringboot/platform/security/TenantGucApplier.java`
- `src/main/java/com/erg/ergspringboot/platform/web/HibernateQueryCountFilter.java`
- `src/main/java/com/erg/ergspringboot/service/lms/LmsRuntimeService.java`
- `src/main/java/com/erg/ergspringboot/service/lms/readmodel/LmsReadModelService.java`
- `src/main/java/com/erg/ergspringboot/service/calendar/CalendarReadService.java`
- `src/main/java/com/erg/ergspringboot/service/calendar/CalendarConflictService.java`
- `src/main/java/com/erg/ergspringboot/service/quiz/authoring/QuizQuestionLinkService.java`
- `src/main/java/com/erg/ergspringboot/service/quiz/publish/QuizPublishService.java`
- `src/main/java/com/erg/ergspringboot/service/notifications/NotificationCommandService.java`
- `src/main/java/com/erg/ergspringboot/service/notifications/NotificationOutboxWorker.java`
- `src/main/java/com/erg/ergspringboot/service/users/UserService.java`
- `src/main/java/com/erg/ergspringboot/service/users/EmployeeSecurityActivityService.java`
- `src/main/java/com/erg/ergspringboot/config/CacheConfig.java`
- `src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlGuardFilter.java`
- `src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlContextInterceptor.java`
- `src/main/java/com/erg/ergspringboot/service/calendar/CalendarDataLoaderRegistry.java`
- `src/main/resources/graphql/schema.graphqls`

## 16. Technical references

- Spring GraphQL annotated controllers and `@BatchMapping`: https://docs.spring.io/spring-graphql/reference/controllers.html
- Spring transaction thread-bound semantics: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html
- Spring Boot Native Image/AOT model: https://docs.spring.io/spring-boot/reference/packaging/native-image/introducing-graalvm-native-images.html
- jOOQ `MULTISET`: https://www.jooq.org/doc/latest/manual/sql-building/column-expressions/multiset-value-constructor/
- GraalVM reachability metadata for jOOQ: https://github.com/oracle/graalvm-reachability-metadata/tree/master/metadata/org.jooq/jooq
- PostgreSQL `pg_stat_statements`: https://www.postgresql.org/docs/current/pgstatstatements.html
- PostgreSQL `EXPLAIN`: https://www.postgresql.org/docs/current/using-explain.html

## 17. Explicit non-goals

- Do not promise 5,000 concurrent users solely from source-level optimization or Native Image compilation.
- Do not add every missing foreign-key index without workload proof.
- Do not convert the modular monolith to microservices during this program.
- Do not introduce Blaze-Persistence as the first response to N+1.
- Do not use virtual threads, a larger Hikari pool, Redis, or PgBouncer as a substitute for bounded query/write design.
- Do not optimize process-local stub endpoints as if they were production implementations; persist or disable them first.
