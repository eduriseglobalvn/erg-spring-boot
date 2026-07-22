# API inventory and production surface

This is the route contract for the current release. The machine-readable route appendix is
[`api-performance-catalog.yaml`](../../src/test/resources/performance/api-performance-catalog.yaml).
It contains every normalized Spring MVC method/path/handler mapping and the existing
statement, row, payload, state-owner, cache and pagination metadata. The existing
`ApiCoverageCatalogIntegrationTest` compares that appendix with the runtime
`RequestMappingHandlerMapping`; a new production mapping therefore fails CI until it is
explicitly inventoried.

REST route count: `488`

GraphQL mapped field count: `33`

## Route metadata contract

Every REST row in the appendix must have:

- `owner`: inherited from the controller package matrix below;
- `status`: `canonical`, `legacy`, `prototype`, or `framework`;
- authentication and permission: protected by the SecurityConfig matcher for `/api/v1/**`
  unless an exact public exception is listed in the security contract;
- tenant scope: required for business state and included in the query/cache key;
- idempotency: required for create/retryable commands and webhook/provider delivery;
- persistence source and replacement: represented by `classification`, `state-owner` and
  `disposition` in the catalog during the strangler migration.

The inventory does not duplicate 488 rows in prose. The catalog is the row-level source of
truth, and `RouteInventoryContractTest` verifies that every catalog handler has an owner,
that the declared count is exact, and that all GraphQL fields are owned below.

## Controller owner matrix

| Controller prefix | Module owner | Default status | Authentication / tenant policy |
|---|---|---|---|
| `com.erg.ergspringboot.controller.ai` | `platform-ai` | `prototype` | authenticated; tenant-scoped; replace Map payloads before production |
| `com.erg.ergspringboot.controller.auth` | `auth` | `canonical` | login/refresh exact public exceptions; session routes authenticated |
| `com.erg.ergspringboot.controller.authorization` | `authorization` | `canonical` | authenticated + permission; tenant/education-unit scope |
| `com.erg.ergspringboot.controller.bot` | `platform-webhook` | `canonical` | exact provider verification; durable enqueue; no blanket public access |
| `com.erg.ergspringboot.controller.calendar` | `calendar` | `canonical` | authenticated + calendar permission; tenant/class scope |
| `com.erg.ergspringboot.controller.catalog` | `learningcontent` | `legacy` | authenticated for writes; tenant-scoped; prototype rows disabled in production |
| `com.erg.ergspringboot.controller.cms` | `learningcontent` | `legacy` | authenticated for writes; typed DTO migration required |
| `com.erg.ergspringboot.controller.crawler` | `platform-crawler` | `prototype` | authenticated; process-local stubs disabled in production |
| `com.erg.ergspringboot.controller.crm` | `crm` | `canonical` | authenticated + CRM permission; tenant-scoped |
| `com.erg.ergspringboot.controller.curriculum` | `curriculum` | `canonical` | authenticated + curriculum permission; canonical prefix is `/api/v1/curriculum` |
| `com.erg.ergspringboot.controller.documents` | `documents` | `canonical` | authenticated + document permission; tenant ownership required |
| `com.erg.ergspringboot.controller.engagement` | `crm` | `canonical` | public read only where explicitly configured; mutations authenticated and tenant-scoped |
| `com.erg.ergspringboot.controller.lcms` | `lcms` | `canonical` | authenticated + authoring permission; tenant/school scope |
| `com.erg.ergspringboot.controller.learningcontent` | `learningcontent` | `legacy` | authenticated + content permission; migrate to `/api/v1` |
| `com.erg.ergspringboot.controller.lms` | `lms` | `canonical` | authenticated + LMS permission; tenant/class/attempt scope |
| `com.erg.ergspringboot.controller.notifications` | `notifications` | `canonical` | authenticated except exact provider/admin policies; tenant ownership |
| `com.erg.ergspringboot.controller.operations` | `platform-operations` | `prototype` | admin-only; process-local rows disabled in production |
| `com.erg.ergspringboot.controller.organization` | `organization` | `canonical` | authenticated + organization permission; tenant scope |
| `com.erg.ergspringboot.controller.platform` | `platform` | `canonical` | management network/admin only; never public Internet |
| `com.erg.ergspringboot.controller.profiles` | `users` | `canonical` | authenticated; user/tenant ownership |
| `com.erg.ergspringboot.controller.reporting` | `reporting` | `canonical` | authenticated + report permission; tenant/class scope |
| `com.erg.ergspringboot.controller.seo` | `learningcontent` | `legacy` | public read only by exact matcher; writes authenticated |
| `com.erg.ergspringboot.controller.sessions` | `auth` | `canonical` | authenticated; session owner or admin |
| `com.erg.ergspringboot.controller.trending` | `platform-trending` | `prototype` | authenticated/admin; process-local state disabled in production |
| `com.erg.ergspringboot.controller.users` | `users` | `canonical` | authenticated; tenant and scoped-role policy |

## Status and persistence policy

| Catalog classification | Status rule | Persistence/source of truth | Next action |
|---|---|---|---|
| `persistent-read` | `canonical` when `/api/v1`, otherwise `legacy` | PostgreSQL projection/read model or durable external source | move legacy callers, keep typed projection |
| `persistent-write` | `canonical` when `/api/v1`, otherwise `legacy` | PostgreSQL aggregate plus audit/outbox | facade first, then module migration |
| `external-io` | `canonical` only with bounded durable job | provider/API adapter; no provider call in command transaction | idempotency, timeout, retry/DLQ |
| `process-local-stub` | `prototype` | no production durable state | disable in production or implement entity/migration/job before re-enabling |
| `unsupported` | `prototype` | none | remove from production surface |
| `framework` | `framework` | Spring/Actuator/GraphQL infrastructure | keep exact management/network policy |

Legacy routes are not aliases forever. A legacy route needs a deprecation metric, owner and
removal date in the release notes. The curriculum legacy tree is removed in Phase 1.2 after
the canonical `/api/v1/curriculum/tree` contract is green.

Runtime enforcement is provided by `LegacyApiRegistry` and `LegacyApiDeprecationFilter`. Registered
legacy requests receive `Deprecation: true`, an RFC 8594-compatible `Sunset` date, `X-ERG-API-Owner`,
and (when a canonical successor is already mounted) a `successor-version` `Link`. The low-cardinality
`erg.api.deprecated.requests{route_group,owner}` counter is the removal-readiness signal. Content and
curriculum placement commands now expose `/api/v1/content/**` and `/api/v1/curriculum/topics/**`
successors beside their compatibility routes. Removal remains gated by the legacy request counter and
the published sunset date; the old handlers are aliases to the same use cases, not duplicated logic.

## Known boundary findings

These controllers are explicitly tracked for prototype/direct-service remediation:

- `AiContentController`, `CourseController`, `ElearningController`,
  `PageMenuController`, `PostController`, `CrawlerController`, `RssBlacklistController`,
  `DocumentController`, `EngagementController`, `AnalyticsOperationsController`,
  `MigrationPlatformController`, `SeoController` and `TrendingBotController` inject or
  delegate to `ModuleWorkflowService`, `CmsService`, `CatalogService` or `DocumentService`.
- Public handler signatures still using dynamic `Map<String,Object>` are limited by
  `PublicApiTypeContractTest` to `AuthController#logout`, `AuthController#logoutAll`,
  `LcmsQuizBundleController#exportActive`, `LcmsQuizBundleController#exportVersion`,
  `LearningContentController#deleteContentItem`, `NotificationDeviceController#revoke` and
  `NotificationDeviceController#unregister`. These are reviewed migration debt (the two quiz
  bundle exports are provider-neutral dynamic documents); new public handlers must use typed
  records and every migration must shrink this method-level allowlist.
- `permitAll` and webhook exceptions are controlled by exact SecurityConfig matchers and are
  re-tested in the webhook/security phase. No route may infer public access from a broad
  controller prefix.
- Controller/entity/repository coupling is an explicit Phase 3 ArchUnit violation for each
  migrated module; the compatibility allowlist must shrink after every migration commit.

## GraphQL ownership

All operations use `/api/v1/graphql`; authorization is enforced at the application boundary,
not only by field naming. The 33 cataloged fields are listed here so adding a field requires
an owner and permission decision:

### `lms` / `calendar`

`learningResourceLibrary`, `classWorkspace`, `teacherHomeworkWorkspace`,
`assignmentProgressWorkspace`, `studentGroups`, `myTeachingCalendar`, `myStudentCalendar`,
`previewTeachingSchedule`, `createTeachingSchedule`, `updateTeachingScheduleEvent`,
`deleteTeachingScheduleEvent`, `deleteTeacherSchedule`, `publishTeachingScheduleBatch`,
`cancelTeachingScheduleBatch`, `createRoom`, `importTeachingScheduleFromExcel`, `lms`

### `lcms` / `school`

`questionBankWorkspace`, `accessManagementWorkspace`, `teachingCalendarWorkspace`,
`teachingScheduleBatches`, `teachingScheduleBatch`, `quizWorkspace`, `quizDraft`,
`quizVersions`, `quizQuestionAnalytics`, `assignmentReport`, `schoolDirectory`,
`schoolOperations`, `lcms`

### `users` / `reporting` / `crm` / `learningcontent`

`studentDashboard`, `crm`, `elearning`

GraphQL schema changes must update this ownership section, the performance catalog and the
typed input/output contract tests in the same change.

CI verifies `contracts/graphql/schema.graphqls.sha256` against the canonical
`src/main/resources/graphql/schema.graphqls` via `scripts/check-graphql-schema.cjs`. Pull
requests additionally run `GraphQlSchemaCompatibilityTest` against the base branch. The
checker rejects removed types/fields/arguments/input fields, incompatible type/nullability
changes, newly required arguments and removed enum values. A breaking change still requires an
explicit migration/deprecation decision and updated typed contract tests in the same pull
request.
