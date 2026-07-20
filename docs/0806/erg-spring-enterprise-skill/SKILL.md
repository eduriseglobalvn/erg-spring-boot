---
name: erg-spring-enterprise
description: Use this skill whenever working on D:\ERG\erg-spring backend, especially Spring Boot 4, Java 25, LMS/LCMS/CRM/Elearning REST APIs, GraphQL rollout, security/RBAC/scope, DTO/response contracts, repositories, read models, GraalVM native-image readiness, performance, N+1 prevention, and enterprise maintainability. Always use it before adding endpoints, changing security, creating DTOs, implementing LMS/LCMS data reads, or reviewing backend code in this repository.
---

# ERG Spring Enterprise Skill

This skill captures the backend rules for `erg-spring`. Use it to keep the codebase secure, maintainable, native-image friendly, and ready for enterprise scale.

## Load order

1. Read this `SKILL.md` first.
2. For implementation/review details, open `references/architecture-rules.md`.
3. For GraphQL/LMS/LCMS rollout decisions, open `references/graphql-lms-lcms-rules.md`.
4. For security reviews, open `references/security-checklist.md`.
5. For DTO/response/API contracts, open `references/api-contract-rules.md`.

## Repository baseline

The backend target is:

- Spring Boot 4.x.
- Java 25.
- PostgreSQL via JPA/Flyway.
- REST-first for auth, current session/scope, commands, files, imports/exports, and quiz runtime.
- GraphQL read-projection only for heavy screens.
- Future GraalVM native image.
- Enterprise target: maintainable code, bounded queries, no trust-boundary shortcuts, 5,000 concurrent users through horizontal scale, caching, pagination, and async heavy work.

## Current status guard after report item 27

Use this wording until newer source evidence supersedes it:

- `teacherHomeworkWorkspace` has `AssignmentCardProjection`, `TeacherHomeworkSummaryProjection` for active/overdue/pending-review counters, and a first Hibernate-statistics query-count integration proof through `LmsTeacherHomeworkWorkspaceQueryCountIntegrationTest` (`prepareStatementCount <= 4`, `entityLoadCount == 0`). A real aggregate bug was fixed by using `count(distinct case ... assignment.id ...)` for active/overdue counters across the attempt join. Do not say teacher homework counters or first query-count proof are missing. Remaining gates: class options, pending-review/overdue detail lists, PostgreSQL EXPLAIN/index evidence, load/soak proof, and native smoke.
- `learningResourceLibrary` has resource card, taxonomy, count, progress, and recent projections plus a first Hibernate-statistics query-count integration proof through `HocLieuLibraryQueryCountIntegrationTest` (`prepareStatementCount <= 9`, `entityLoadCount == 0`). Do not say its first SQL query-count proof is missing. Remaining gates: PostgreSQL EXPLAIN/index evidence, cache stampede guard proof, load/soak proof, distributed cache/rate-limit decisions, and native smoke.
- `studentDashboard` has assignment slice/counters/next actions, `StudentDashboardScoreSummaryProjection`, and a first Hibernate-statistics query-count integration proof through `StudentDashboardQueryCountIntegrationTest` (`prepareStatementCount <= 6`, `entityLoadCount <= 2`). Do not say score summary or first query-count proof is missing. Remaining gates: announcement projection, PostgreSQL EXPLAIN/index evidence, load/soak proof, and native smoke.
- `questionBankWorkspace` has batch `QuestionUsageCountProjection`; do not describe question usage as a placeholder. Remaining gates: DB integration/query-count proof, EXPLAIN/index review for the native JSONB/lateral usage query, load/soak proof, and native smoke.
- `QuestionBankWorkspaceQueryCountIntegrationTest` is PostgreSQL-only for the usage-count path because the repository uses JSONB/lateral SQL. H2 skips are not production proof. Count it as closed only after the test runs on PostgreSQL/Testcontainers/staging DB with bounded query count.
- `accessManagementWorkspace` has user rows, role/scope options, effective-permission batch reads, and a first Hibernate-statistics query-count proof through `AccessManagementWorkspaceQueryCountIntegrationTest` (`prepareStatementCount <= 7`, `entityLoadCount == 0`). Remaining gates: deeper field-level permission matrix, denied override proof, PostgreSQL EXPLAIN/index evidence, load/soak proof, and native smoke.
- `classWorkspace` has class info, roster page, assignment summary page, first implementations of nullable `scoreSummary` and `riskSummary`, and a first Hibernate-statistics query-count integration proof. Score summary is gated by `lms.grade.read`/`lms.manage`, and risk summary is gated by `lms.progress.read`/`lms.grade.read`/`lms.manage`. Remaining implementation gaps are attendance summary, PostgreSQL EXPLAIN/index evidence, load/soak proof, and native smoke.
- Bounded repository-interaction tests are useful proof at service/read-model level, but they are not a replacement for SQL query-count integration tests with Hibernate statistics or datasource proxy.
- Production GraphQL gates remain incomplete until query-count proof exists for every heavy workspace, slow-query timeout smoke, EXPLAIN/index review, distributed rate-limit/cache, load/soak, native smoke, and field-level permission matrix are done.
- Local Caffeine caches record stats through `CacheConfig.recordStats()` and `CacheConfigTest`; do not report missing local cache hit/miss observability unless this source evidence changes. Distributed Redis/gateway cache/rate-limit/stampede gates remain open.

## Non-negotiable rules

### Security first

- Never trust public request headers for identity, roles, permissions, or managed scope.
- Bearer/cookie token must be verified against active server-side session/principal.
- `X-Portal`, `X-Tenant-ID`, and requested scope are inputs to validate, not proof of permission.
- Service layer must enforce permission and scope. Frontend route guards are UX only.
- Empty managed scope means no access unless the principal has explicit global scope/permission.
- Deny override must win over wildcard grants.

### REST and GraphQL separation

- Keep REST for commands, auth/current, file, import/export, quiz attempt sync/submit, and audit-sensitive writes.
- Use GraphQL only for read projections that need to combine many datasets.
- Use one GraphQL endpoint: `POST /api/v1/graphql`.
- Namespace schema roots: `lms`, `lcms`, `crm`, `elearning`.
- Do not add GraphQL mutations in v1 unless explicitly approved after security review.

### DTO and response contracts

- Public controllers return `ApiResponse<T>`.
- `T` should be a typed Java record response, not raw entity and not loose `Map<String,Object>`.
- Request bodies should be typed records with validation annotations.
- Use `PageRequestDto` / `PageResponse<T>` or module-specific typed pagination.
- Only allow dynamic maps for explicitly dynamic metadata/quiz answer payloads, with size/depth validation.
- DTO is the public API contract; Projection is the database/read-model contract. Heavy reads need both.
- Projection is not optional for LMS/LCMS/HocLieu/Elearning workspace reads. Virtual Threads improve request concurrency but do not fix N+1, broad entity loading, Hikari saturation, or heap allocation.
- DTO alone is acceptable only for bounded current/scope/detail reads. For list/table/dashboard/workspace/report reads, use Projection/Page/Aggregate Projection first, then map to DTO.
- Never expose Projection directly from REST controllers or GraphQL resolvers.

### Architecture boundaries

- Controller: HTTP mapping, auth context extraction, response wrapping only.
- Service: business rules, permissions, scope validation, transactions.
- Read-model service: heavy screen reads, projections, batching, caching.
- Repository: bounded database query methods and projections.
- DTO mapper: no entity leakage.
- Do not put business logic in controller.

### Performance rules

- No `findAll().stream().filter()` for production endpoints.
- No broad tenant read then filter in memory for workspace screens.
- No repository call inside loops for page/nested results.
- Use pagination, record/interface projections, aggregate projections, batch `IN (...)` queries, DataLoader for GraphQL, and query-count tests.
- For 5,000-concurrency readiness, prove bounded query count before load testing. Hikari pool size must follow DB capacity and instance count, not concurrent user count.
- Cache only scoped read-model results; Redis/Caffeine cache keys must include tenant, user/scope, operationName/filter/page, with TTL jitter or single-flight/distributed lock for hot paths.
- Use `@EntityGraph` only for small/detail reads with explicit relation needs.
- Treat Hibernate batch fetch size as a safety net, not the primary fix for heavy workspace reads.
- Do not use `FetchType.EAGER` as an N+1 fix. It usually creates larger object graphs and less predictable query plans.
- Virtual Threads are a concurrency tool for request handling, not a data-access optimization. They do not make DB connections, bad query shapes, or external dependencies safe.
- Keep JDBC/JPA query timeout configured and verify it with a slow-query smoke/integration test before production.
- Heavy report/export work returns an async REST job id.

### Native-image rules

- Prefer records, constructor injection, typed DTOs, explicit resources.
- Avoid unnecessary reflection/dynamic class loading.
- Keep Jackson/validation/JPA native hints in mind when introducing dynamic structures.
- Avoid secret/config work in static initializers.

## Standard workflow for backend changes

1. Identify whether the change is REST command, REST current/scope, or GraphQL read projection.
2. Check permission and scope requirements before designing DTOs.
3. Define request/response records and pagination.
4. Add repository methods that query by tenant/scope/page directly.
5. Add service-level guards.
6. Add contract tests and security tests.
7. For heavy reads, add query-count integration tests, not only mocked repository tests.
8. For hot projection queries, add PostgreSQL EXPLAIN/index evidence before load testing.
9. Run `mvn test` when feasible.

## Required review questions

Ask these before finishing any backend work:

- Can a client spoof identity, tenant, portal, role, permission, or managed scope?
- Does service layer enforce the same access rule as the route?
- Can this endpoint read all tenant data?
- Does response leak password hash, token hash, credentials, raw answers, denied permissions, or private audit data?
- Is pagination enforced?
- Does the query select only needed columns through projection when the screen is heavy?
- Does query count grow with nested rows?
- Is this code native-image friendly?
- Is the API contract typed and easy to maintain?

## Output style when reporting to user

When giving a plan or audit, structure it in Vietnamese and include:

- Ket luan ngan.
- Findings theo muc do uu tien.
- Source files da kiem tra.
- Ke hoach sua theo phase.
- Test/security/performance acceptance criteria.

Keep the final answer concise, but make the document itself detailed enough for future maintainers.
