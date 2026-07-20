# API performance release-readiness re-audit

Date: 2026-07-16 (Asia/Ho_Chi_Minh)

Scope:

- `docs/superpowers/specs/2026-07-15-api-performance-scalability-design.md`
- `docs/superpowers/plans/2026-07-16-api-performance-scalability-implementation-plan.md`
- Redis 8.8 design and implementation plan dated 2026-07-16
- Current working tree on `codex/lms-graphql-security-audit-plan`

## Verdict

The implementation is not complete against all acceptance criteria in the approved plans, and the repository does not yet contain evidence that the system supports 5,000 concurrent users. Redis 8.8 infrastructure and many correctness/read-model improvements are substantially implemented, but release acceptance remains blocked by missing capacity/native/database evidence and several code/catalog gaps.

Passing unit/integration tests prove selected behavior and query bounds. They do not prove p95/p99 latency, completed RPS, Redis/PostgreSQL saturation behavior, or host capacity at 5,000 concurrent users.

## Fresh verification evidence

- Initial full Maven run on the re-audited tree: 793 tests, 0 failures, 7 errors, 0 skipped; Maven exit code 1.
- The seven errors exposed stale PostgreSQL fixtures and transaction-context loss in `LmsReadModelService`.
- Fixture/schema drift was corrected for canonical curriculum IDs, normalized question types, required users/quizzes/quiz versions, and normalized `lms_quiz_questions` links.
- `LmsReadModelService` no longer submits JPA/JDBC reads to executor threads for assignment progress, class workspace, and teacher homework. Those reads remain on the transaction-bound thread so the EntityManager snapshot and transaction-local tenant/RLS GUCs are preserved.
- Focused PostgreSQL follow-up: all seven formerly failing test classes now have passing Surefire reports. The normalized question-usage EXPLAIN uses `idx_lms_quiz_questions_question` and no longer expands JSON arrays.
- Final full Maven rerun after the fixes: Maven exit code 0. Fresh Surefire aggregation contains 166 report files and 753 tests, 0 failures, 0 errors, and 0 skipped (report timestamps 22:34-22:39 Asia/Ho_Chi_Minh).

## Release-blocking findings

### 1. Performance/capacity acceptance has not been executed

The repository contains k6 scripts and fixture assets, but there is no accepted 5,000-VU ramp, steady, burst, and soak result bundle. There is therefore no evidence for the approved p95/p99, error-rate, completed-RPS, Hikari-waiter, PostgreSQL, Redis, RSS, GC, swap, or OOM gates.

Required evidence still missing:

- JVM 5,000-VU baseline on the target single-host topology;
- 5,000-submission burst and idempotency/retry proof;
- warm-cache, cold-cache, Redis-state outage, and Redis-cache outage runs;
- 30-minute steady run and two-hour 50% soak;
- source revision, dirty-tree fingerprint, images, fixture checksum, raw k6 output, and host metrics for every run.

### 2. Native Image is smoke-validated but not capacity-accepted

The original Windows native compile reached Spring/Hibernate AOT and stopped because the Windows host lacks the required Visual Studio 2022 native toolchain. A later Linux Docker builder produced a native image and authenticated smoke evidence; the full capacity, RSS, and latency comparison gates remain unproven.

### 3. PostgreSQL evidence is incomplete

Flyway applies all 68 migrations in PostgreSQL Testcontainers. However, the accepted workload has not been captured with `pg_stat_statements`, and representative `EXPLAIN (ANALYZE, BUFFERS)` before/after evidence has not been recorded for the top production query shapes. Index scripts are assets, not proof that an index improves the workload without write/lock regressions.

### 4. Cache invalidation is still broad

Static scan found 74 `@CacheEvict(... allEntries = true)` occurrences across 13 services. This does not satisfy the plan requirement to replace touched hot paths with exact tenant/entity generation invalidation. At 5,000 concurrent users these calls can create cross-tenant cold-cache storms.

Affected families include auth/session, authorization, calendar, LMS student groups, question bank/quiz authoring, schools, and users.

### 5. API catalog is not authoritative

`ApiCoverageCatalogIntegrationTest` counts mapping annotations and checks catalog strings. It does not:

- boot both relevant profiles/feature-flag variants;
- enumerate and normalize `(HTTP method, path, handler)` tuples;
- require one catalog record per REST route;
- classify pagination, query/row/payload budgets, state ownership, cache policy, and production disposition per route;
- detect removal or path/handler drift when annotation counts happen to remain equal.

The current REST catalog contains a declared count, not the required endpoint inventory.

### 6. Breaking API changes were under-documented

The complete legacy HocLieu controller/service/model/repository surface was removed. The previous FE guide incorrectly stated that valid calls remained wire-compatible. Auth, user, session, LMS package, submit-attempt, and paginated list payloads also changed.

`docs/frontend/api-performance-security-migration.md` now documents these active breaking changes with old/new examples. No replacement mapping for `/api/v1/hoclieu/**` is assumed; that product/API decision remains explicit.

### 7. Process-local business stores remain

`DocumentService`, `CmsService`, `CatalogService`, and `ModuleWorkflowService` still hold business state in `ConcurrentHashMap`. The production profile guard prevents exposure, which is a safe temporary disposition, but the endpoints are not productionized or horizontally scalable until backed by tenant-scoped PostgreSQL/Redis persistence.

### 8. Reporting is partial

Durable report jobs exist, but the planned canonical aggregate tables, incremental aggregate maintenance, reconciliation, and OLTP-contention proof are incomplete. A report job queue alone does not satisfy Task 10.

### 9. Backup/restore is documented but not proven

`ops/backup/restore-drill.md` is a runbook. No completed off-host encrypted backup/WAL restore, RTO, post-restore Flyway result, or authenticated smoke evidence is recorded.

## Plan task disposition

| Task | Disposition | Evidence/gap |
|---|---|---|
| 1. API catalog | Incomplete | Count/string tests are not an authoritative route catalog. |
| 2. PostgreSQL test gate | Substantial | Testcontainers/Flyway and the fresh 753-test suite pass; production-like scale evidence remains separate. |
| 3. Measurement integrity | Partial | Some query-count and metric fixes exist; accepted workload artifacts are absent. |
| 4. Tenant/RLS correctness | Substantial, not closed | Strict policies/tests exist; re-audit found and fixed LMS executor context loss; Calendar still uses executor-thread JDBC paths. |
| 5. Common path/pool safety | Partial | Pool budgets/config exist; production-like connection-pressure proof is absent. |
| 6. REST N+1/pagination | Partial | Multiple LMS/read/history paths are bounded; legacy unpaged and non-hot paths remain. |
| 7. Batch writes/outbox | Substantial, not accepted | Batching/outbox code and tests exist; 10/100/1,000 statement-slope/provider-load proof is incomplete. |
| 8. GraphQL cost/selection | Substantial, not accepted | Guard/selection work exists; complete catalog-budget and production query evidence is incomplete. |
| 9. Cache/process-local state | Incomplete | Redis tier exists; 74 broad evictions and four local business stores remain. |
| 10. Reporting read models | Incomplete | Jobs exist; canonical aggregate maintenance and contention proof remain. |
| 11. PostgreSQL/index hardening | Incomplete | Scripts exist; accepted workload capture and before/after proof do not. |
| 12. 5,000/native/deployment | Incomplete | Native smoke and restore drill exist; 5,000-VU acceptance and soak remain unexecuted. |

## Redis 8.8 disposition

Implemented and previously verified:

- split `redis-state` and `redis-cache` on Redis 8.8;
- state AOF/noeviction and cache LFU policies;
- bounded Lettuce clients;
- Redis 8.8 `INCREX` limiter with Redis 7.4 Lua fallback;
- typed tenant-safe cache keys, L1/L2, single-flight, failure fallback, generation invalidation, and Pub/Sub invalidation;
- focused JVM tests, Redis 8.8 Testcontainers tests, and Docker configuration checks.

Not accepted yet:

- 5,000-VU cache/rate-limit capacity on the target topology;
- long-run memory fragmentation/eviction/latency evidence;
- Native Image runtime smoke using both Redis roles;
- removal of broad application-level `allEntries=true` invalidations.

## Changes made during this re-audit

- repaired seven PostgreSQL integration fixtures to match the current FK/canonical curriculum schema;
- changed question-bank usage fixtures and EXPLAIN proof from JSON-array expansion to normalized `lms_quiz_questions` links;
- removed transaction-breaking executor fan-out from three LMS read-model paths;
- corrected teacher-homework absolute statement budget to the observed fixed budget of nine;
- expanded the FE migration guide for HocLieu removal and auth/user/session/LMS payload changes.

## Required next acceptance sequence

1. Remove Calendar transaction-context-losing JDBC fan-out and add a PostgreSQL/RLS regression.
2. Replace broad invalidations on hot paths with exact generation invalidation and run cross-replica/outage tests.
3. Build the authoritative REST/GraphQL catalog and make CI fail on normalized route drift.
4. Complete reporting aggregates and remaining bounded read/write slope tests.
5. Run `pg_stat_statements` and representative EXPLAIN before/after capture on deterministic scale data.
6. Build and smoke the Native Image in a suitable Windows native-tools shell or Linux builder.
7. Run the approved k6 acceptance matrix and backup/restore drill on the target physical host.

Only after all acceptance gates pass may the release claim support for 5,000 concurrent users.

## Continuation evidence — 2026-07-17 Asia/Ho_Chi_Minh

The continuation closed two deterministic regressions found during the native canary:

- `BusinessCacheRuntimeHints` now registers `StudentDashboardReadScope`, `ErgPrincipal`, and `UserAccessContext` for public SpEL method invocation, declared constructors, and fields. `CacheNativeImageContractTest` is green.
- `LmsRuntimeService.listStudentAssignments(..., page, size)` resolves the authenticated user to the canonical LMS `Student.id` once, then passes that ID to the recipient join. `LmsRuntimeServiceAttemptRulesTest` is green.

Fresh verification:

- Full Maven run: 171 Surefire reports, 767 tests, 0 failures, 0 errors, 0 skipped (`target/full-maven-final-rerun.log`).
- Native image rebuilt successfully after the fixes. Image digest: `sha256:a02df2c43ced337d3928100f8e247d758926572fa969f8fe0fa2b9a30fc71715`; image size 127,051,790 bytes.
- Native Flyway completed against PostgreSQL 17.10 at schema version 69. Readiness returned HTTP 200; startup log reports 11.25 seconds and the smoke RSS sample was approximately 224.8 MiB.
- Authenticated native REST assignment smoke with `seed-tenant-01`: HTTP 200, 40 returned items, 40 total items.
- Authenticated native GraphQL `studentDashboard` self-scope smoke: data returned without `SpelEvaluationException` or GraphQL internal error.
- Native report job smoke: enqueue HTTP 200 (`queued`), status HTTP 200 (`completed`). Notification smoke exercised device registration, notification enqueue (`outbox=1`), and worker failure transition to `dead` with Firebase disabled.
- Small native Redis/cache k6 smoke: 2 VUs for 10 seconds, request/check error rate 0%; measured p95 was 1.04 seconds, so the 500 ms acceptance threshold failed. This is a diagnostic smoke, not a capacity run.

The 5,000-VU steady, 5,000-workflow burst, 30-minute acceptance, soak, and host-resource gates remain unexecuted. The native image is therefore smoke-validated, not capacity-accepted. The failed small k6 threshold is retained as an optimization signal rather than hidden.
