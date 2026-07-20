# GraphQL, LMS, LCMS rules

## Endpoint

Use one endpoint:

```http
POST /api/v1/graphql
```

Root:

```graphql
type Query {
  lms: LmsQuery!
  lcms: LcmsQuery!
  crm: CrmQuery!
  elearning: ElearningQuery!
}
```

## What belongs in GraphQL

Use GraphQL for heavy read projections:

- LMS class workspace.
- LMS homework workspace.
- LMS resource library.
- LCMS question bank workspace.
- LCMS access management read workspace.
- Student dashboard.

Do not use GraphQL v1 for:

- login/refresh/logout.
- current session/scope.
- create/update/delete commands.
- import/export.
- upload/download/asset streaming.
- quiz attempt sync/submit.
- payment/delete/critical audit workflows.

## Resolver rules

- Resolver reads context, delegates to read-model service.
- Resolver does not call repositories directly.
- Resolver does not call broad runtime services that contain in-memory filtering/N+1.
- Resolver/read-model service uses projections for base rows, cards, summaries, and aggregates.
- Resolver requires operationName.
- Resolver enforces namespace/portal access.
- Read-model service enforces field-level permissions.

## Guard rails

Production GraphQL must have:

- GraphiQL off.
- Introspection off except dev/admin/internal.
- max depth 6-8.
- max complexity.
- GraphQL execution timeout 3-5 seconds.
- JDBC/JPA query timeout configured and smoke-tested separately.
- default page size 20-50.
- hard max 200.
- rate limit by tenant/user/operationName.
- sensitive variable masking.

## First query rollout order

1. `lms.learningResourceLibrary(input)`
2. `lms.classWorkspace(classId, range, page)`
3. `lms.teacherHomeworkWorkspace(scope, filters, page)`
4. `elearning.studentDashboard`
5. `lcms.questionBankWorkspace(filters, page)`
6. `lcms.accessManagementWorkspace(filters, page)`

## Current status wording guard

When reviewing the current source, distinguish completed first-slice/shell work from enterprise-ready implementation:

- `learningResourceLibrary` has the main read-model shape and a first Hibernate-statistics query-count integration proof through `HocLieuLibraryQueryCountIntegrationTest` (`prepareStatementCount <= 9`, `entityLoadCount == 0`). It still needs PostgreSQL EXPLAIN/index review, cache stampede guard proof, load proof, distributed cache/rate-limit decisions, and native smoke.
- `classWorkspace` has class info, roster page, assignment summary page, first implementations of nullable `scoreSummary` through `ClassScoreSummaryProjection` and nullable `riskSummary` through `ClassRiskSummaryProjection`, plus a first Hibernate-statistics query-count integration proof. Score summary is field-level gated by `lms.grade.read`/`lms.manage`, while risk summary is gated by `lms.progress.read`/`lms.grade.read`/`lms.manage`. It is not complete until attendance summary, PostgreSQL EXPLAIN/index evidence, and load/native gates are done.
- `teacherHomeworkWorkspace` is a read-only first slice backed by assignment cards, `TeacherHomeworkSummaryProjection` for active/overdue/pending-review counters, and a first Hibernate-statistics query-count proof through `LmsTeacherHomeworkWorkspaceQueryCountIntegrationTest` (`prepareStatementCount <= 4`, `entityLoadCount == 0`). It still needs class options, pending-review/overdue detail lists, PostgreSQL EXPLAIN/index evidence, load/soak proof, and native smoke.
- `studentDashboard` is a self-scoped first slice with assignment projection slice/counters/next actions, score summary projection, and a first Hibernate-statistics query-count proof through `StudentDashboardQueryCountIntegrationTest` (`prepareStatementCount <= 6`, `entityLoadCount <= 2`). It still needs announcement projection, PostgreSQL EXPLAIN/index evidence, load/soak proof, and native smoke.
- `questionBankWorkspace` has a projection-backed first implementation for question page, taxonomy options, quiz options, and bounded usage count projection; it still needs DB integration/query-count proof, EXPLAIN/index review for native JSONB usage query, and load/native gates.
- Its usage-count query is PostgreSQL JSONB/lateral SQL. H2 cannot prove that path; a skipped H2 test is acceptable only when the report still marks PostgreSQL query-count proof as open.
- `accessManagementWorkspace` has a projection-backed first implementation for user rows, role/scope options, gated effective-permission batch reads, and a first Hibernate-statistics query-count proof through `AccessManagementWorkspaceQueryCountIntegrationTest` (`prepareStatementCount <= 7`, `entityLoadCount == 0`). It still needs deeper field-level matrix tests, denied override proof, PostgreSQL EXPLAIN/index evidence, load/soak proof, and native smoke.

## N+1 prevention

For each workspace:

- Query base page once through projection.
- Collect IDs.
- Batch load related data with `IN` and summary projections.
- Group in memory after data is bounded.
- Use projection queries for counts/summaries.
- Add query-count integration tests with Hibernate statistics or datasource proxy.
- Treat DTO as the public GraphQL/REST contract and Projection as the DB/read-model contract; GraphQL should return DTOs assembled from projections.
- Use DataLoader only after the base projection has bounded the row set.
- For load readiness, prove query count is bounded before enabling a workspace in 5,000-concurrency tests.
- Virtual Threads do not excuse N+1. If a resolver causes repository calls per row or per nested field, it is not production-ready.
- DTO is the public API contract. Projection is the DB/read-model contract. DTO alone is not enough if the implementation loads full entities for heavy screens.
- Hikari pool must be sized by DB capacity and instance count, not by concurrent users. Projection, paging, timeout, cache stampede guard, and read replica strategy are the load-bearing layers.
- Current first query-count integration proofs exist for `learningResourceLibrary`, `classWorkspace`, `studentDashboard`, `teacherHomeworkWorkspace`, and `accessManagementWorkspace`. `questionBankWorkspace` still needs PostgreSQL query-count proof because H2 cannot prove its JSONB/lateral usage-count query.
- DTO-only is not enough for heavy reads. DTO is the public contract; Projection is the DB/read-model contract that prevents broad entity loading and reduces Hikari hold time.
- For list/table/workspace/report reads, require base Projection + aggregate Projection + bounded batch query before mapping to DTO.
- `@EntityGraph` is acceptable only for small detail reads with explicit relation needs. It is not the standard fix for LMS/LCMS workspace screens.
- `hibernate.default_batch_fetch_size` is only a safety net for leftover lazy relations, not the primary N+1 solution.
- Virtual Threads do not make an unsafe resolver production-ready. If SQL count grows with row count, the workspace must be redesigned with projection before load testing.

Never:

- Load all tenant assignments/classes/students/resources then filter in Java.
- Lookup assignment/class/student inside a loop of attempts or rows.
- Return raw answers by default.
- Build one repository query per GraphQL field if the field appears inside a list.

## Projection operating standard

Treat Projection as the internal read contract and DTO as the public API contract.

Prefer:

- Record/class constructor projections for new enterprise read models, especially GraphQL workspaces and native-image-sensitive code.
- Closed interface projections only for small, simple query shapes.
- Aggregate projections for every count/sum/avg/status widget.
- Native SQL projections only with PostgreSQL integration proof, EXPLAIN/index review, and a normalization/read-table plan if the query becomes hot.

Avoid:

- Open/SpEL projections on hot paths.
- Returning projection objects directly from REST controllers or GraphQL resolvers.
- EntityGraph for workspace/list/report reads.
- FetchType.EAGER as an N+1 fix.

Before adding or approving a projection query, verify:

- `tenantId` and managed scope are filtered in SQL/JPQL.
- List/card/table reads have `Pageable`, default size, and hard max.
- The query selects only the columns needed by the screen.
- Counters are computed by DB aggregate projection, not by iterating collections.
- Query-count integration test uses enough nested rows to expose N+1.
- Projection read paths do not materialize entities unless explicitly justified.
- Sensitive fields are absent or field-level gated before mapping to DTO.
- Cache keys include tenant/user/scope/operation/filter/page and hot keys have stampede protection.
- Native-image rollout has smoke coverage for GraphQL schema/resources, Jackson DTOs, projection records, validation, JPA, and PostgreSQL driver.

Workspace-specific projection backlog:

- `lms.assignmentProgressWorkspace`: `AssignmentDetailProjection`, `RecipientProgressProjection`, `AttemptScoreProjection`, `MissingLateStudentProjection`, `AssignmentProgressSummaryProjection`.
- `lms.classWorkspace`: `ClassAttendanceSummaryProjection`, `AttendanceSessionSummaryProjection`, optional `StudentRiskProjection`.
- `lms.teacherHomeworkWorkspace`: `ClassOptionProjection`, `PendingReviewProjection`, `OverdueStudentProjection`.
- `elearning.studentDashboard`: `AnnouncementCardProjection`, optional `StudentNextActionProjection`.
- `lcms.questionBankWorkspace`: normalized `QuestionUsageProjection` or usage read table if JSONB/lateral usage query is hot.
- `lcms.accessManagementWorkspace`: `EffectivePermissionProjection`, `DeniedPermissionProjection`, `AccessWarningProjection`.
- `lms.learningResourceLibrary`: `ResourceAccessProjection`, optional `ResourceRecommendationProjection`.

Current `assignmentProgressWorkspace` status:

- First slice exists with `AssignmentDetailProjection`, aggregate `AssignmentProgressProjection`, and paged `AttemptSummaryProjection`.
- Query-count proof exists through `LmsAssignmentProgressWorkspaceQueryCountIntegrationTest` with `prepareStatementCount <= 4` and `entityLoadCount == 0`.
- Do not report this query as fully missing anymore.
- Remaining backlog: recipient roster, missing/late students, latest/best attempt per recipient, PostgreSQL EXPLAIN/index evidence, load/soak proof, and native smoke.

## Permission examples

- `lms.classWorkspace`: `lms.class.read` plus class scope.
- `scoreSummary`: `lms.grade.read`.
- `riskSummary`: `lms.progress.read` or `lms.grade.read`.
- `attendanceSummary`: `lms.attendance.read`.
- `teacherHomeworkWorkspace`: `lms.assignment.read`.
- `learningResourceLibrary`: `hoclieu.resource.read` plus school scope.
- `questionBankWorkspace`: `lms.quiz.read` or `hoclieu.manage`.
- `accessManagementWorkspace`: `lms.scope.read`; effective permissions require `rbac.permission.read`.
