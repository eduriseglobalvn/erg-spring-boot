# Quiz FE/BE Integration Report - 2026-07-08

## Summary

Muc tieu chinh la de FE tich hop quiz runtime that ma khong doi UI, dong thoi giam tai BE khi hang nghin hoc sinh mo bai cung luc.

Ket luan kien truc:
- BE giu canonical immutable package, cache manh, khong shuffle theo tung hoc sinh.
- FE shuffle deterministic theo attempt seed cho testing mode.
- Training va testing tach logic runtime.
- FE dung TanStack Query cho runtime package va question-bank workspace cache de phu hop web hien tai va future Tauri fork.
- Quiz editor da co API adapter va TanStack Query/mutation layer cho authoring, publish, version, media, bundle.
- Quiz report da co BE GraphQL va FE TanStack Query data layer cho analytics/progress; `HomeworkProgressPage` da wire ca assignment report va question analytics qua pinned quiz version.
- BE report reads da co Caffeine caches `quizAssignmentReport` va `quizQuestionAnalytics` TTL 30s, tenant-scoped cache keys, va after-commit eviction tren attempt/assignment writes de giam aggregate query load ma khong giu stale report.
- FE dashboard/session bootstrap cache da duoc scope theo portal/tenant/account o TanStack key va theo portal/account/token fingerprint trong in-memory cache de tranh reuse `/sessions/current` cua user cu khi doi account hoac fork sang Tauri.
- FE LCMS/admin operation read keys da duoc scope theo tenant cho education units, learning resources workspace va user-access queries de tranh cross-tenant cache bleed trong admin screens.
  - FE LMS class-workspace read keys da duoc gom ve helper tenant-scoped theo usage/school/class cho assign homework, class management, class students va student-group roster.
- FE LMS/LCMS teaching-calendar read keys da duoc gom ve `teachingCalendarQueryKeys` theo portal/tenant cho workspace va reference catalog caches.
- Du lieu demo FE runtime va question-bank da duoc seed sang BE. Runtime demo di qua authoring draft -> publish pipeline that; question-bank demo seed exact IDs de FE adapter hydrate dung UI hien co.

## Implemented

### Backend

- Fixed BE/FE authoring contract gaps found during audit:
  - GraphQL `QuizDraftDocument` schema now exposes `settings`, `playerTemplate`, `tree`, matching `QuizDraftDocumentResponse` and FE query.
  - `LcmsQuizAuthoringController` now maps `POST /api/v1/lcms/quizzes/{quizId}/validate`.
  - `LcmsQuizAuthoringController` now maps `POST /api/v1/lcms/quizzes/{quizId}/publish`.
  - `LcmsQuizAuthoringController` now maps `POST /api/v1/lcms/quizzes/{quizId}/versions/{versionId}/fork`.
  - Added `LcmsQuizAuthoringControllerTest` for validate/publish/fork delegation with tenant/user/idempotency evidence.
- Added `QuestionBankDemoBootstrapSeedService`.
- Added `QuizDemoBootstrapSeedService`.
- Wired question-bank and quiz demo seeds into `LmsBootstrapSeedService.seedDefaultHierarchy()`.
- Question-bank seed mirrors FE mock IDs:
  - subjects: `ic3-gs6`, `mathematics`, `english`, `science`;
  - questions: `qb-ic3-001` through `qb-ic3-012`, `qb-math-001`, `qb-math-002`, `qb-eng-001`, `qb-sci-001`;
  - quiz-bank rows: `quiz-ic3-l1-foundation-train`, `quiz-ic3-l2-excel-test`, `quiz-ic3-l3-security-train`, `quiz-ic3-l3-mock-test`.
- Seed creates and publishes two stable quiz IDs:
  - `erg-training-demo`
  - `erg-testing-demo`
- Seed fixture covers 14 question types:
  single choice, multiple response, true/false, short answer, numeric, sequence, matching, fill blank, select-from-lists, drag-words, hotspot, drag-drop, likert, essay.
- Seed uses real authoring/publish flow:
  `lms_quizzes` -> `quiz_drafts` -> `QuizPublishService.publish()` -> `quiz_versions` + `quiz_packages`.
- Training demo is `client-first` so FE can local-grade/offline-grade.
- Testing demo is `server-authoritative` so package does not leak answer key.

### Frontend

- Added runtime TanStack Query layer:
  `src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-query.ts`
- Added question-bank TanStack Query layer:
  `src/features/lcms/quiz/question-bank/api/question-bank-query.ts`
- Expanded question-bank GraphQL question rows so FE can read seeded BE question metadata:
  `objective`, `difficulty`, `tags`, `choices`, `answer`, `masteryRate`, `schoolsUsing`.
- Expanded question-bank GraphQL quiz options so FE can read quiz-bank composition from BE instead of guessing from local templates:
  `kind`, `subjectId`, `levelId`, `categoryId`, `questionIds`.
- Added quiz-editor API adapter:
  `src/features/lcms/quiz/quiz-editor/api/quiz-editor-api.ts`
- Added quiz-editor TanStack Query/mutation layer:
  `src/features/lcms/quiz/quiz-editor/api/quiz-editor-query.ts`
- Exported query helpers/hooks from runtime index.
- Exported question-bank query helpers/hooks from question-bank index.
- Exported quiz-editor API/query helpers from quiz-editor index.
- `PlayerShell` now uses `useQuizPackageQuery(quizId, portal)`.
- Retry uses `quizPackageQuery.refetch()`.
- Package hydrate no longer uses a state counter that can refetch repeatedly when query data changes.
- Runtime local fallback is limited to the no-API-base dev/offline path. With a configured API base, package/start/submit errors propagate instead of silently using local quiz data or local grading.
- Runtime API contract is stricter: with a configured API base, start attempt must return a real `attemptId`, and final submit must return either a valid full `Attempt`/`attempt`/`result` shape or BE's aggregate `AttemptSubmitResponse(score,maxScore,percent,passed)`. FE no longer marks a local attempt as server-started or replaces BE submit scores with local grading.
- Runtime draft/answer/sync endpoints are now represented in FE:
  - `saveAttemptDraft` calls `PATCH /api/v1/lms/attempts/{attemptId}/draft`.
  - `saveAttemptAnswer` calls `PUT /api/v1/lms/attempts/{attemptId}/answers/{questionId}`.
  - `syncAttempt` calls `POST /api/v1/lms/attempts/{attemptId}/sync` and fails on BE conflicts.
  - `PlayerShell` debounces draft sync for 1.5s after Begin/server-start, so loading a package still creates no BE write load.
  - Training per-question submit now persists the graded answer through `saveAttemptAnswer`.
  - `PlayerShell` listens for browser `online` after a server attempt has started and replays the current event log through `syncAttempt` once per event-log signature, supporting offline/Tauri reconnect without tight retry loops.
  - Runtime mutations now have stable TanStack `mutationKey` values for MutationCache/devtools/offline instrumentation: `attempt-draft`, `attempt-answer`, and `attempt-sync`.
- `api-client.ts` now treats HTTP `304 Not Modified` as a successful empty revalidation response. This lets runtime package loading receive `undefined` on 304 and reuse the normalized package memory cache instead of surfacing a false `HTTP_304` error.
- `QuestionBankWorkspace` now uses `useQuestionBankWorkspaceQuery()` so cache policy/key live in the feature API layer, not directly in the UI component.
- Quiz-editor layer now covers:
  - workspace read via GraphQL;
  - draft hydrate via GraphQL JSON string parsing;
  - BE draft shape normalization into full FE editor settings/tree/template shape;
  - versions read via GraphQL;
  - create/save/validate/publish/fork via REST;
  - media presign/commit via REST;
  - offline bundle export via REST.
- `/quiz-editor?quizId=...` now flows through LCMS shell into `QuizEditorWorkspace`, fetches `useQuizEditorDraftQuery(quizId)`, and hydrates `useQuizEditorState` once per `quizId:docVersion` without changing the editor UI.
- API-enabled editor draft hydrate now fails clearly if BE returns invalid JSON for draft blobs, instead of silently falling back to mock/default editor data.
- `/quiz-bank` rows now open `/quiz-editor?quizId={id}` using the quiz ID from BE/read model, without adding visible buttons or columns.
- "Create quiz from selected questions" now calls `createQuizEditorQuiz`, builds an editor draft tree from selected question-bank questions, saves the draft to BE, then navigates to `/quiz-editor?quizId={newQuizId}`. If no API base is configured, it falls back to the previous local import flow for offline/dev use; if API base is configured and create/save fails, the error is shown and FE does not open a local draft that could be mistaken for saved BE data. This logic is extracted into `src/features/lcms/create-quiz-from-bank.ts` and covered by `src/features/lcms/create-quiz-from-bank.test.ts`.
- Ribbon `Publish` now saves the current draft, publishes it with deterministic idempotency key `quiz-editor-publish:{quizId}:{docVersion}`, invalidates editor/runtime TanStack Query caches, and clears FE normalized runtime package memory cache for that quiz ID.
- Quiz-editor cache policy:
  - workspace key includes tenant and normalized filters, stale 60s, GC 30m;
  - draft key includes tenant and quizId, stale 15s, GC 30m;
  - versions key includes tenant and quizId, stale 60s, GC 30m;
  - mutations invalidate workspace/draft/version keys according to blast radius.
- Quiz-editor authoring command coverage now includes the BE single-slide and reorder endpoints:
  - `upsertQuizEditorSlide` -> `POST /api/v1/lcms/quizzes/{quizId}/slides`;
  - `reorderQuizEditorSlides` -> `POST /api/v1/lcms/quizzes/{quizId}/reorder`.
- Quiz-editor mutations now have stable mutation keys for create/save/upsert-slide/reorder/validate/publish/fork/media/bundle, improving TanStack Devtools, MutationCache instrumentation, and future Tauri/offline handling.
- Added quiz-report API adapter:
  `src/features/lcms/quiz/quiz-reports/api/quiz-report-api.ts`
- Added quiz-report TanStack Query layer:
  `src/features/lcms/quiz/quiz-reports/api/quiz-report-query.ts`
- Quiz-report layer covers BE LCMS GraphQL:
  - `quizQuestionAnalytics(quizVersionId)` for per-question analytics from normalized runtime rows.
  - `assignmentReport(assignmentId)` for cohort/student progress from normalized progress rows.
- Quiz-report cache policy:
  - question analytics key: `["quiz-reports","question-analytics",tenantId,quizVersionId]`;
  - assignment report key: `["quiz-reports","assignment-report",tenantId,assignmentId]`;
  - stale 60s, GC 30m, because report data can change after submit/review and must not use package-level stale timing.
- Quiz-report fallback is empty only when no API base is configured. With API base configured, GraphQL/auth failures propagate instead of returning mock report data.
- Exported quiz-report helpers/hooks from `src/features/lcms/quiz/quiz-reports/index.ts`.
- Wired `assignmentReport` into `src/features/lms/components/homework-progress-page.tsx` without changing the UI:
  - the existing table/stat cards now prefer BE `assignmentReport` data when API base is configured;
  - the older `assignmentProgressWorkspace` remains a secondary BE read path;
  - mock/fallback rows are used only when no API base is configured.
- `PlayerShell` now invalidates `quiz-reports` root plus the assignment-specific report key after final submit succeeds, so homework/report screens refetch normalized score/progress instead of keeping stale TanStack cache.
- Added `src/features/lms/components/homework-progress-page.test.ts` to lock mapping from BE assignment report rows into the existing progress table shape.
- Added LMS assignment command API adapter:
  `src/features/lms/api/lms-assignment-command-api.ts`
- Added LMS assignment command TanStack mutation layer:
  `src/features/lms/api/lms-assignment-command-query.ts`
- Assignment command layer covers BE distribution writes:
  - `POST /api/v1/lms/assignments` with `X-Idempotency-Key`;
  - `PATCH /api/v1/lms/assignments/{assignmentId}`;
  - `PUT /api/v1/lms/assignments/{assignmentId}/recipients`;
  - `DELETE /api/v1/lms/assignments/{assignmentId}`;
  - `POST/PUT/DELETE /api/v1/lms/student-groups`.
- Assignment command mutations have stable mutation keys for Tauri/offline instrumentation and invalidate homework/progress/report caches after writes.
- BE assignment command writes now clear backend read-model caches after transaction commit:
  - create assignment;
  - update assignment metadata/status;
  - update recipients;
  - delete assignment.
- Cleared BE caches: `lmsHomeworkWorkspace`, `lmsClassWorkspace`, `lmsAssignmentProgress`, `studentDashboard`. This keeps FE TanStack invalidation from refetching stale BE cache entries during the 30s read-model TTL.
- Idempotent create replay returns the existing assignment without cache eviction, because it performs no write.
- BE attempt/runtime writes now clear the same read-model caches after transaction commit:
  - `startAttempt`;
  - `saveDraft`;
  - `saveAnswer`;
  - `submitAttempt`.
- Submit still persists the authoritative attempt and refreshes `student_progress` before cache eviction, so `assignmentReport`, homework progress, class workspace, and student dashboard refetch against the fresh projection/read rows.
- Idempotent start replay and rejected submit do not evict because they perform no write.
- `syncAttempt` intentionally does not clear these read caches yet, because the current implementation only replays the append-only client event log and does not merge visible answer/status/projection state. If sync later mutates visible attempt data, it must join the same after-commit eviction path.
- `AssignHomeworkPage` now calls BE create assignment when API base is configured, preserving the existing UI. No-API-base keeps the previous local callback path for dev/offline use; API-enabled failures stop the workflow instead of creating a fake local assignment. `LmsTeacherShell` also no longer adds or merges local `createdRuns` with fake `assignment-${Date.now()}` IDs in API-backed mode; the API-backed homework list relies on BE workspace refetch/invalidation only, locked by `resolveLmsTeacherRuns`. Homework workspace and assignment-progress TanStack keys now include tenant scope to avoid cross-tenant cache bleed.
- Fixed homework/progress cache-key mismatch by routing both reads and assignment mutation invalidation through `lmsAssignmentReadQueryKeys`:
  - `homeworkWorkspace()` maps to the actual teacher shell homework query key;
  - `assignmentProgress(assignmentId)` maps to the homework progress query key;
  - assignment mutations now invalidate the same keys that `HomeworkPanel`/`HomeworkProgressPage` use.
- Added BE student-group read model for LMS:
  - GraphQL `lms.studentGroups(input:{tenantId,classId})`;
  - response fields `id`, `name`, `classId`, `note`, `color`, `ownerUserId`, `studentIds`, `studentCount`, `createdAt`, `updatedAt`;
  - read scope is guarded by tenant, permission, `managedClassIds`, and owner user visibility;
  - member IDs are loaded in bulk through `findByTenantIdAndGroupIdIn`, avoiding per-group member queries;
  - BE cache `lmsStudentGroups` records Caffeine stats, TTL 30s, size 1000.
- Added migration `V31__student_group_ui_metadata.sql` so current FE group metadata persists:
  - `student_groups.note text`;
  - `student_groups.color varchar(64)`.
- Student-group REST command contract now persists and returns `note` and `color` in addition to `name`, `classId`, and `studentIds`.
- `StudentGroupsPage` now persists group commands through BE when API base is configured, without changing the page layout:
  - create calls `POST /api/v1/lms/student-groups` before adding a server-backed group locally;
  - the initial FE mock groups are promoted to BE on first committed edit/member change and then use the returned `groupId`;
  - name/note edits remain local while typing and persist on blur to avoid mutation-per-keystroke load;
  - color/member changes commit immediately; delete calls BE for server-backed groups and rolls back locally on failure;
  - no-API-base keeps the existing local/offline behavior.
- `StudentGroupsPage` now hydrates initial groups from BE with `useLmsStudentGroupsQuery()` when API base is configured. API-enabled empty means an actual empty BE group list; local/mock groups are only the no-API-base path.
- `StudentGroupsPage` now hydrates the student roster used for group membership from BE `classWorkspace` with TanStack `useQueries` when API base is configured. API-enabled empty/loading/error states no longer fall back to `classroomStudents`, preventing mock student IDs from being persisted into BE groups.
- Student group query/mutation hooks now have scoped query keys and metadata for persistence instrumentation:
  - read key `["lms","student-groups",tenantId,classId|"all"]`;
  - stale 30s, GC 30m;
  - mutations invalidate `["lms","student-groups"]`, homework workspace, and report root caches.
- Current student-group BE contract now persists `name`, `classId`, `note`, `color`, and `studentIds`, so the existing UI metadata survives reload/device switch once the Postgres migration has run.
- Elearning student dashboard GraphQL integration was hardened:
  - `loadStudentDashboardData` now uses mock data only when no API base is configured;
  - API-enabled GraphQL/auth failures propagate as TanStack Query errors instead of returning mock assignments;
  - API-enabled empty assignments/announcements stay empty instead of being refilled from mock dashboard data;
  - `StudentDashboardWorkspace` no longer injects mock assignments/announcements on API-enabled error/empty states;
  - student dashboard query key now includes tenant scope plus account id.
- LMS teacher bootstrap/scope integration was hardened:
  - `listEducationUnits` and `loadLmsDashboardBootstrap` now use mock data only when no API base is configured;
  - API-enabled `/api/v1/sessions/current` failures, invalid payloads, and empty education-unit responses propagate as query errors or real empty state instead of switching to classroom mock data;
  - `LmsTeacherShell` no longer fills BE-empty homework workspace data with local `assignmentRuns` when API base is configured;
  - no-API-base keeps the existing classroom mock path for local/dev/offline UI work.
- LMS/LCMS learning-resource fallback behavior was hardened:
  - `loadLearningResourceLibraryBootstrap` and `loadLearningResourceLibraryProgress` now propagate API-enabled REST failures instead of returning mock bootstrap data or empty progress;
  - teacher managed-school mapping keeps API-enabled empty education-unit data empty, and `listLearningResourceManagedSchools` propagates API-enabled list failures instead of returning classroom mock schools;
  - `LearningResourceAuthoringWorkspace` no longer replaces API-enabled empty/error workspace data with `mockExplorerModel`/`mockExplorerResources`;
  - the LCMS authoring quiz attach list no longer falls back to local quiz-bank items when the question-bank query is API-enabled empty/error;
  - `TaxonomyCreateDialog` now resolves attachable exercises without falling back to `mockExerciseLibrary` when API base is configured and quiz-bank data is empty/error.
- LMS classroom/account/admin fallback behavior was hardened:
  - `ClassManagementPage` and `ClassStudentsWorkspace` no longer use mock/fallback students when API base is configured and class workspace data is missing or empty;
  - `LmsLoginLogsPage` no longer displays mock login sessions for API-enabled empty/error data; its TanStack query key is scoped by tenant and account id to avoid session-log cache bleed;
  - `getCenters()` now returns an empty list only in no-API-base dev/offline mode and propagates API-enabled load failures.
- Assign-homework recipient selection was hardened:
  - class mode in `AssignHomeworkPage` now loads students from BE `classWorkspace` through TanStack Query when API base is configured;
  - API-enabled missing/empty class students stay empty instead of falling back to `classroomStudents` mock data;
  - group mode now loads BE `lms.studentGroups` for the selected class through TanStack Query and maps those groups into the existing group picker shape;
  - API-enabled missing/empty groups stay empty instead of falling back to `assignmentGroups` mock data;
  - grade mode now loads students for each selected grade class from BE `classWorkspace` through TanStack `useQueries`;
  - API-enabled grade submit is unblocked because class/group/grade recipients now resolve from BE-backed class student maps instead of mock class/student IDs;
  - student-group reads are lazy-enabled only in group mode, the main class workspace query is lazy-enabled only in class/group mode, and grade class workspace queries are lazy-enabled only in grade mode to avoid unnecessary BE reads.
- BE `AssignmentProgressWorkspace.assignment` now exposes pinned assignment resources:
  - GraphQL `AssignmentDetail.resources`;
  - fields `quizId`, `quizVersionId`, `quizVersionLabel`, `orderIndex`;
  - read path uses `AssignmentResourceProjection` instead of materializing `AssignmentResource` entities, keeping the progress read model projection-only.
- BE assignment list read models now expose the same pinned resources:
  - GraphQL `HomeworkAssignmentCard.resources`;
  - GraphQL `AssignmentSummaryItem.resources`;
  - `LmsReadModelService` fetches resources with one batch projection query for the current assignment page, not per assignment row.
- FE LMS GraphQL model now carries assignment `resources`; `AssignmentRun` carries optional `quizId`, `quizVersionId`, and `quizVersionLabel` so downstream report/runtime screens can use the exact quiz version pinned at assign time.
- `HomeworkProgressPage` now resolves `quizVersionId` from the selected run first, then from `assignmentProgressWorkspace.assignment.resources[0]`, and calls `useQuizQuestionAnalyticsQuery` when API base is configured. Because homework/class assignment lists now include resources, most navigation paths already have the pinned version before the progress detail query returns.
- The existing homework overview card now shows a compact question analytics summary (`questionCount` and average correct rate) without changing the main table/filter layout.

### Documentation

- Updated `docs/0706/quiz-fe-integration-guide.md` with:
  - TanStack Query package cache behavior.
  - Seeded demo quiz IDs.
  - Seeded question-bank mock IDs.
  - Quiz-editor API/query layer and remaining UI route wiring.
  - Runtime seed pipeline and 14-type coverage.
  - Remaining E2E/Postgres verification needs.

## Decisions

### Shuffle location

Shuffle should stay in FE.

Reason: for 5000 learners opening the same quiz, BE must serve one cached canonical package per quiz/version. If BE shuffles per learner, package generation and cache cardinality become per attempt/student and the cache loses most of its value.

### Training vs testing

Training:
- No shuffle.
- Step-by-step local grading/feedback.
- Can use `client-first` package with answer key.

Testing:
- Deterministic FE shuffle for questions and choices.
- No per-question feedback.
- Final submit only.
- BE remains authoritative for pinned-version grading.

### Caching

Caching is layered:
- BE Caffeine cache for runtime package.
- HTTP `ETag` and `Cache-Control`.
- FE normalized package memory cache for 304/body-empty cases.
- FE API client 304 handling so browser/HTTP revalidation, TanStack Query, and normalized runtime memory cache work as one cache stack.
- FE TanStack Query cache for component-level fetch reuse and Tauri-friendly data flow.
- Question-bank query key includes tenant scope: `["question-bank","workspace",tenantId]`.
- Quiz-editor query keys include tenant/filter scope for workspace and tenant/quizId scope for draft/version.
- Runtime package query key includes tenant, portal, and quiz scope.
- Quiz-report query keys include tenant and report identity scope; report stale time is intentionally shorter than immutable package stale time.

## Verification

Passed:

```powershell
.\mvnw "-DskipTests" compile
```

```powershell
.\mvnw "-Dtest=LcmsQuizAuthoringControllerTest,GraphQlEndpointConfigurationTest,LcmsQuizWorkspaceGraphQlControllerTest,CacheConfigTest,LmsControllerHttpCacheTest,LmsRuntimeServiceAttemptRulesTest,QuizCompileServiceTest,GradingEngineTest" test
```

Result: 40 backend tests passed.

```powershell
.\mvnw "-Dtest=LcmsQuizAuthoringControllerTest,GraphQlEndpointConfigurationTest,LcmsQuizWorkspaceGraphQlControllerTest,CacheConfigTest,LmsControllerHttpCacheTest,LmsRuntimeServiceAttemptRulesTest,QuizCompileServiceTest,GradingEngineTest,QuestionBankDemoSeedParityTest" test
```

Result after adding parity guard: 42 backend tests passed.

Re-run after FE cache-scope hardening and stricter FE mock seed parity:

```powershell
.\mvnw "-Dtest=LcmsQuizAuthoringControllerTest,GraphQlEndpointConfigurationTest,LcmsQuizWorkspaceGraphQlControllerTest,CacheConfigTest,LmsControllerHttpCacheTest,LmsRuntimeServiceAttemptRulesTest,QuizCompileServiceTest,GradingEngineTest,QuestionBankDemoSeedParityTest" test
```

Result: 43 backend tests passed.

Additional BE verification after expanding question-bank quiz option metadata:

```powershell
.\mvnw "-Dtest=LcmsWorkspaceReadServiceTest,LcmsWorkspaceGraphQlControllerTest,GraphQlEndpointConfigurationTest" test
```

Result: 18 backend tests passed. This verifies the GraphQL/read-model contract still compiles and maps question-bank question metadata plus quiz-bank `kind`, `subjectId`, `levelId`, `categoryId`, and `questionIds`.

```powershell
bun run typecheck
```

```powershell
bun run test src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-api.test.ts src/features/lcms/quiz/quiz-runtime/api/local-quiz-attempt-store.test.ts
```

Result: 10 frontend runtime tests passed.

```powershell
bun run test src/features/lcms/quiz/quiz-editor/api/quiz-editor-api.test.ts
```

Result: 3 frontend quiz-editor normalizer tests passed.

Additional FE verification after quiz-bank open editor + publish cache invalidation + create-from-bank helper extraction:

```powershell
bun run typecheck
```

```powershell
bun run test src/features/lcms/create-quiz-from-bank.test.ts src/features/lcms/quiz/question-bank/api/question-bank-api.test.ts src/features/lcms/quiz/question-bank/api/question-bank-query.test.ts src/features/lcms/quiz/quiz-editor/api/quiz-editor-api.test.ts src/features/lcms/quiz/quiz-editor/api/quiz-editor-query.test.ts src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-api.test.ts src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-query.test.ts src/features/lcms/quiz/quiz-runtime/api/local-quiz-attempt-store.test.ts src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-fallback.test.ts
```

Result after question-bank quiz metadata wiring, stricter runtime response-shape guards, aggregate submit response mapping, runtime draft/answer/sync mutations, stable runtime mutation keys, reconnect event replay wiring, and editor invalid JSON guard: 10 frontend test files passed, 47 tests passed.

Additional FE report data-layer verification:

```powershell
bun run test src/features/lcms/quiz/quiz-reports/api/quiz-report-api.test.ts src/features/lcms/quiz/quiz-reports/api/quiz-report-query.test.ts
```

Result: 2 frontend report test files passed, 5 tests passed. Coverage locks report GraphQL operation names/variables, tenant-scoped query keys, short-lived report cache policy, and API-enabled error propagation without mock fallback.

Combined FE quiz/data-layer/report/assignment targeted suite:

```powershell
bun run test src/features/lms/api/lms-assignment-command-api.test.ts src/features/lms/api/lms-assignment-command-query.test.ts src/features/lms/components/homework-progress-page.test.ts src/features/lcms/create-quiz-from-bank.test.ts src/features/lcms/quiz/question-bank/api/question-bank-api.test.ts src/features/lcms/quiz/question-bank/api/question-bank-query.test.ts src/features/lcms/quiz/quiz-editor/api/quiz-editor-api.test.ts src/features/lcms/quiz/quiz-editor/api/quiz-editor-query.test.ts src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-api.test.ts src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-query.test.ts src/features/lcms/quiz/quiz-runtime/api/local-quiz-attempt-store.test.ts src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-fallback.test.ts src/features/lcms/quiz/quiz-reports/api/quiz-report-api.test.ts src/features/lcms/quiz/quiz-reports/api/quiz-report-query.test.ts src/lib/api-client.test.ts
```

Result after adding quiz-editor slide/reorder adapter, mutation keys, homework/progress cache-key normalization, and re-running after student-group cache key/invalidation wiring: 15 frontend test files passed, 60 tests passed.

Additional BE student-group read-model verification:

```powershell
.\mvnw "-Dtest=LmsHomeworkWorkspaceGraphQlControllerTest,LmsReadModelServiceTest,CacheConfigTest,GraphQlEndpointConfigurationTest" test
```

Result after adding `lms.studentGroups`, `lmsStudentGroups` cache, scoped controller read, read-model member bulk lookup, and `note/color` persistence: 5 backend test files passed, 33 tests passed.

Additional FE student-group command wiring verification:

```powershell
bun run typecheck
```

Result: passed.

```powershell
bun run test src/features/lms/api/lms-assignment-command-query.test.ts src/features/lms/api/lms-assignment-command-api.test.ts
```

Result after adding student-group GraphQL read adapter/query options/cache key/invalidation metadata and `note/color` payload/read mapping: 2 frontend test files passed, 8 tests passed.

Re-run combined FE quiz/data-layer/report/assignment targeted suite after student-group read hydration:

```powershell
bun run test src/features/lms/api/lms-assignment-command-api.test.ts src/features/lms/api/lms-assignment-command-query.test.ts src/features/lms/components/homework-progress-page.test.ts src/features/lcms/create-quiz-from-bank.test.ts src/features/lcms/quiz/question-bank/api/question-bank-api.test.ts src/features/lcms/quiz/question-bank/api/question-bank-query.test.ts src/features/lcms/quiz/quiz-editor/api/quiz-editor-api.test.ts src/features/lcms/quiz/quiz-editor/api/quiz-editor-query.test.ts src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-api.test.ts src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-query.test.ts src/features/lcms/quiz/quiz-runtime/api/local-quiz-attempt-store.test.ts src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-fallback.test.ts src/features/lcms/quiz/quiz-reports/api/quiz-report-api.test.ts src/features/lcms/quiz/quiz-reports/api/quiz-report-query.test.ts src/lib/api-client.test.ts
```

Result after adding `note/color` persistence mapping: 15 frontend test files passed, 64 tests passed.

Additional verification after pinned assignment resources + `quizQuestionAnalytics` UI wiring:

```powershell
bun run typecheck
```

Result: passed.

```powershell
bun run test src/features/lms/api/lms-graphql-api.test.ts src/features/lms/components/homework-progress-page.test.ts src/features/lcms/quiz/quiz-reports/api/quiz-report-api.test.ts src/features/lcms/quiz/quiz-reports/api/quiz-report-query.test.ts src/features/lms/api/lms-assignment-command-api.test.ts src/features/lms/api/lms-assignment-command-query.test.ts
```

Result: 6 frontend test files passed, 18 tests passed.

```powershell
.\mvnw "-Dtest=LmsReadModelServiceTest,LmsHomeworkWorkspaceGraphQlControllerTest" test
```

Result: 2 backend unit test files passed, 22 tests passed.

```powershell
.\mvnw "-Dtest=LmsReadModelServiceTest,LmsHomeworkWorkspaceGraphQlControllerTest,LmsAssignmentProgressWorkspaceQueryCountIntegrationTest" test
```

Status: unit tests passed, but the integration test did not reach the new query-count assertions because H2 failed on Postgres-only Flyway migration `V11__relationship_integrity_seed_cleanup.sql` at `DELETE ... USING`. Re-run this proof with Docker/Postgres or a test-profile migration fix before treating query-count evidence as production-grade.

Additional verification after extending pinned resources to assignment list cards:

```powershell
.\mvnw "-Dtest=LmsReadModelServiceTest,LmsHomeworkWorkspaceGraphQlControllerTest" test
```

Result: 2 backend unit test files passed, 22 tests passed. Coverage locks `AssignmentDetail.resources`, `HomeworkAssignmentCard.resources`, and `AssignmentSummaryItem.resources` mapping, including batch resource lookup for non-empty assignment pages.

```powershell
bun run typecheck
```

Result: passed.

```powershell
bun run test src/features/lms/api/lms-graphql-api.test.ts src/features/lms/components/homework-progress-page.test.ts src/features/lcms/quiz/quiz-reports/api/quiz-report-query.test.ts src/features/lms/api/lms-assignment-command-query.test.ts
```

Result: 4 frontend test files passed, 10 tests passed.

Additional verification after BE assignment read-cache eviction hardening:

```powershell
.\mvnw "-Dtest=LmsAssignmentCommandServiceTest,LmsReadModelServiceTest,LmsHomeworkWorkspaceGraphQlControllerTest,CacheConfigTest" test
```

Result: 4 backend test files passed, 29 tests passed. Coverage locks assignment command cache clears for write paths and no eviction for idempotent create replay.

```powershell
bun run test src/features/lms/api/lms-assignment-command-query.test.ts src/features/lms/api/lms-assignment-command-api.test.ts
```

Result: 2 frontend test files passed, 8 tests passed.

Additional verification after BE attempt/runtime read-cache eviction hardening:

```powershell
.\mvnw "-Dtest=LmsRuntimeServiceAttemptRulesTest" test
```

Result: 1 backend test file passed, 7 tests passed. Coverage locks cache eviction for successful `startAttempt`, `saveAnswer`, and `submitAttempt`, and no eviction for idempotent start replay or rejected submit.

```powershell
.\mvnw "-Dtest=LmsRuntimeServiceAttemptRulesTest,LmsAssignmentCommandServiceTest,LmsReadModelServiceTest,LmsHomeworkWorkspaceGraphQlControllerTest,CacheConfigTest,QuizReportServiceTest" test
```

Result: 6 backend test files passed, 40 tests passed. This verifies runtime cache eviction, assignment command cache eviction, LMS read-model mapping, homework GraphQL contract, cache registry, and quiz report aggregation together.

Additional FE verification after Elearning student dashboard API fallback hardening:

```powershell
bun run test src/features/elearning/student-dashboard/api/student-dashboard-api.test.ts
```

Result: 1 frontend test file passed, 3 tests passed. Coverage locks no-API-base mock usage, API-enabled GraphQL failure propagation, and API-enabled empty dashboard behavior.

```powershell
bun run typecheck
```

Result: passed.

Additional FE verification after LMS teacher bootstrap/scope API fallback hardening:

```powershell
bun run test src/features/lms/infrastructure/lms-dashboard-api.test.ts src/features/lms/api/lms-graphql-api.test.ts src/features/lms/api/lms-assignment-command-query.test.ts
```

Result: 3 frontend test files passed, 8 tests passed. Coverage locks API-enabled bootstrap/list-education-unit failure propagation, no silent classroom mock fallback, and LMS assignment query cache invalidation contracts.

```powershell
bun run typecheck
```

Result: passed.

Additional FE verification after LMS/LCMS learning-resource API fallback hardening:

```powershell
bun run test src/features/lms/learning-resources/api/learning-resource-explorer-operations-api.test.ts src/features/lms/learning-resources/api/teacher-resource-dashboard-schools.test.ts
```

Result: 2 frontend test files passed, 9 tests passed. Coverage locks no-API-base mock behavior, API-enabled library bootstrap/progress failure propagation, API-enabled empty managed schools, and no silent classroom mock fallback on managed-school failures.

```powershell
bun run typecheck
```

Result: passed.

Additional FE verification after LMS classroom/account/admin API fallback hardening:

```powershell
bun run test src/features/lms/classroom/components/classroom-api-fallback.test.ts src/features/lcms/admin-operations/api/centers-api.test.ts src/features/lms/components/lms-login-logs-page.test.ts
```

Result: 3 frontend test files passed, 8 tests passed. Coverage locks API-enabled empty class workspace data, login sessions, and centers load behavior so they no longer fall back to mock data.

```powershell
bun run typecheck
```

Result: passed.

Additional FE verification after LCMS attach-exercise dialog fallback hardening:

```powershell
bun run test src/features/lcms/admin-operations/utils/learning-resource-content-dialog.test.ts src/features/lms/classroom/components/classroom-api-fallback.test.ts src/features/lcms/admin-operations/api/centers-api.test.ts src/features/lms/components/lms-login-logs-page.test.ts src/features/lms/learning-resources/api/learning-resource-explorer-operations-api.test.ts src/features/lms/learning-resources/api/teacher-resource-dashboard-schools.test.ts
```

Result: 6 frontend test files passed, 24 tests passed. Coverage locks API-enabled attach-exercise empty behavior, classroom/account/admin fallbacks, and learning-resource API fallback behavior together.

```powershell
bun run typecheck
```

Result: passed.

Additional FE verification after assign-homework class recipient fallback hardening:

```powershell
bun run test src/features/lms/components/assign-homework-student-utils.test.ts src/features/lms/classroom/components/classroom-api-fallback.test.ts src/features/lcms/admin-operations/utils/learning-resource-content-dialog.test.ts src/features/lcms/admin-operations/api/centers-api.test.ts src/features/lms/components/lms-login-logs-page.test.ts
```

Result: 5 frontend test files passed, 17 tests passed. Coverage locks assign-homework class recipient resolution, classroom/account/admin fallback behavior, and LCMS attach-exercise fallback behavior.

```powershell
bun run typecheck
```

Result: passed.

Additional FE verification after assign-homework group recipient fallback hardening:

```powershell
bun run test src/features/lms/components/assign-homework-student-utils.test.ts src/features/lms/api/lms-assignment-command-query.test.ts src/features/lms/api/lms-assignment-command-api.test.ts src/features/lms/classroom/components/classroom-api-fallback.test.ts
```

Result: 4 frontend test files passed, 16 tests passed. Coverage locks class/group recipient fallback behavior and assignment command cache/mutation contracts together.

```powershell
bun run typecheck
```

Result: passed.

Additional FE verification after assign-homework grade recipient fallback hardening:

```powershell
bun run test src/features/lms/components/assign-homework-student-utils.test.ts src/features/lms/api/lms-assignment-command-query.test.ts src/features/lms/api/lms-assignment-command-api.test.ts src/features/lms/classroom/components/classroom-api-fallback.test.ts
```

Result: 4 frontend test files passed, 17 tests passed. Coverage locks class/group/grade recipient fallback behavior and assignment command cache/mutation contracts together.

```powershell
bun run test src/features/lms/components/assign-homework-student-utils.test.ts src/features/lms/api/lms-assignment-command-query.test.ts src/features/lms/api/lms-assignment-command-api.test.ts src/features/lms/classroom/components/classroom-api-fallback.test.ts src/features/lcms/admin-operations/utils/learning-resource-content-dialog.test.ts src/features/lcms/admin-operations/api/centers-api.test.ts src/features/lms/components/lms-login-logs-page.test.ts
```

Result: 7 frontend test files passed, 28 tests passed. Coverage rechecks assign-homework class/group/grade fallback behavior together with LCMS content dialog, centers, login-log, and classroom API fallback behavior.

```powershell
bun run typecheck
```

Result: passed.

Additional FE verification after assign-homework lazy query enabling:

```powershell
bun run test src/features/lms/api/lms-assignment-command-query.test.ts src/features/lms/components/assign-homework-student-utils.test.ts src/features/lms/api/lms-assignment-command-api.test.ts src/features/lms/classroom/components/classroom-api-fallback.test.ts
```

Result: 4 frontend test files passed, 18 tests passed. Coverage locks the student-groups query `enabled` option plus class/group/grade recipient fallback behavior.

```powershell
bun run typecheck
```

Result: passed.

Additional FE verification after student-groups roster hardening:

```powershell
bun run test src/features/lms/api/lms-assignment-command-query.test.ts src/features/lms/api/lms-assignment-command-api.test.ts src/features/lms/components/assign-homework-student-utils.test.ts src/features/lms/classroom/components/classroom-api-fallback.test.ts
```

Result: 4 frontend test files passed, 18 tests passed. Coverage rechecks student-groups query options/contracts and assign-homework recipient fallback behavior after wiring group roster reads to BE class workspace.

```powershell
bun run typecheck
```

Result: passed.

```powershell
bun run typecheck
```

Result after adding quiz-report API/query layer: passed.

Additional FE verification after removing API-backed fake assignment runs:

```powershell
bun run test src/features/lms/api/lms-assignment-command-query.test.ts src/features/lms/api/lms-assignment-command-api.test.ts src/features/lms/components/assign-homework-student-utils.test.ts src/features/lms/classroom/components/classroom-api-fallback.test.ts src/features/lms/components/homework-progress-page.test.ts
```

Result: 5 frontend test files passed, 21 tests passed. Coverage rechecks assignment command cache/query contracts, recipient fallback behavior, classroom fallback behavior, and homework progress/report mapping after preventing API-backed fake local assignment rows.

```powershell
bun run typecheck
```

Result: passed.
Additional FE verification after locking API-backed LMS teacher runs resolution:

```powershell
bun run test src/features/lms/components/lms-teacher-shell.test.ts src/features/lms/api/lms-assignment-command-query.test.ts src/features/lms/api/lms-assignment-command-api.test.ts src/features/lms/components/homework-progress-page.test.ts
```

Result: 4 frontend test files passed, 14 tests passed. Coverage locks that API-backed LMS teacher homework lists use only BE workspace runs and never merge local created/mock assignment rows.

```powershell
bun run typecheck
```

Result: passed.
Additional FE verification after tenant-scoping LMS homework/progress query keys:

```powershell
bun run test src/features/lms/api/lms-assignment-command-query.test.ts src/features/lms/components/lms-teacher-shell.test.ts src/features/lms/components/homework-progress-page.test.ts src/features/lms/api/lms-assignment-command-api.test.ts
```

Result: 4 frontend test files passed, 14 tests passed. Coverage locks tenant-scoped homework/progress query keys, API-backed teacher run resolution, assignment command contracts, and homework progress/report mapping.

```powershell
bun run typecheck
```

Result: passed.
Additional FE verification after tenant/account-scoping account login-session query keys:

```powershell
bun run test src/lib/query-keys.test.ts src/features/lms/components/lms-login-logs-page.test.ts src/features/lms/api/lms-assignment-command-query.test.ts src/features/lms/components/lms-teacher-shell.test.ts
```

Result: 4 frontend test files passed, 9 tests passed. Coverage locks account login-session query keys by tenant/account, API-enabled login-session empty behavior, LMS assignment cache keys, and API-backed teacher run resolution.

```powershell
bun run typecheck
```

Result: passed.
Additional FE API-cache verification:

```powershell
bun run test src/lib/api-client.test.ts
```

Result: 10 frontend API-client tests passed, including HTTP 304 revalidation and the current no-client-portal-header contract.

Question-bank cache/data ownership update:
- `question-bank-query.ts` now owns mock `initialData` and `placeholderData`.
- Mock `initialData` is marked with `initialDataUpdatedAt: 0`, so it keeps the UI populated but does not mask BE data for the 5-minute stale window when an API base is configured.
- `loadQuestionBankData(tenantId)` now receives tenant scope from the query options; secondary consumers reuse `questionBankWorkspaceQueryOptions()` instead of calling it directly.
- `loadQuestionBankData` now returns mock only when no API base is configured. With a real API base, GraphQL/auth failures propagate as TanStack Query errors instead of silently rendering mock as if it were BE data.
- When API base is configured and BE returns an empty question-bank workspace, FE now returns empty `questions`/`quizzes`/`subjects` instead of replacing it with local mock rows. Local templates are only used to enrich BE-returned IDs.
- Question-bank question rows now use BE `objective`, `difficulty`, `tags`, `choices`, `answer`, `masteryRate`, and `schoolsUsing`; local templates only fallback for legacy/missing fields.
- Question-bank quiz rows now use BE `kind`, `subjectId`, `levelId`, `categoryId`, and `questionIds`; local templates only supply UI enrichment such as existing duration/source labels for seeded IDs.
- The learning-resource authoring secondary consumer now reuses `questionBankWorkspaceQueryOptions()` instead of declaring its own non-tenant-scoped question-bank key/cache policy.
- `QuestionBankWorkspace` reads `subjects`, `questions`, and `quizzes` directly from TanStack Query data.
- Component local state is limited to UI-only state such as filters, search, selection, and auto-pick controls.
- If question-bank query fails, `QuestionBankWorkspace` shows a sync warning and uses an empty interactive dataset so stale `initialData` mock cannot be selected or used to create a fake BE quiz.
- Added `question-bank-query.test.ts` to lock query key, stale/gc policy, fallback initial data, and stale initial-data timestamp.
- Added `question-bank-api.test.ts` to lock API-enabled empty workspace behavior and BE-ID enrichment without inventing extra mock rows.
- Added question-bank API coverage to lock BE question metadata/choices mapping.
- Added question-bank API coverage to lock that quiz-bank composition is mapped from BE metadata instead of local mock template order.
- Added `quiz-editor-query.test.ts` to lock tenant-scoped workspace/draft/version keys and cache policy.
- Added `quiz-runtime-query.test.ts` to lock tenant + portal + quiz scoped runtime package key and cache policy.
- Added runtime API failure tests to lock that package load, start attempt, and final submit do not fall back to local mock behavior when API base is configured.
- Added runtime API contract tests to lock that API-enabled start attempt cannot use a local attempt ID when BE omits `attemptId`, API-enabled submit accepts BE's aggregate `AttemptSubmitResponse`, and FE keeps BE submit scores instead of replacing them with local grading.
- Added runtime API contract tests for BE draft/answer/sync endpoints: draft save accepts BE `AttemptResponse` snapshots, save-answer requires `saved=true`, and sync fails on BE conflicts.
- Added editor API coverage to lock that API-enabled invalid draft JSON fails instead of hydrating mock/default editor data.
- Added `api-client.test.ts` coverage to lock that HTTP `304` resolves as `undefined`, allowing runtime package memory cache to handle BE ETag revalidation.
- Added create-from-bank tests to lock that no-API-base uses the local dev import path, while API-enabled create/save failures stop the workflow and do not open a fake local editor draft.

Additional BE seed verification hardening:

```powershell
.\mvnw "-Derg.reseed.devdb=true" "-Dtest=DatabaseReseedRunnerTest" test
```

Status: not passed yet because the local Docker Desktop daemon is not running. `docker version` reports that the client exists but cannot connect to `npipe:////./pipe/dockerDesktopLinuxEngine`.

What changed:
- `DatabaseReseedRunnerTest` now uses `@ActiveProfiles("test")`.
- `DatabaseReseedRunnerTest` now registers `PostgreSqlTestDatabase` through `@DynamicPropertySource`.
- The test no longer silently falls back to H2 for Postgres-only migrations.
- The test asserts exact FE mock question IDs, quiz-bank IDs, published demo quiz versions, and `quiz_packages` rows for `erg-training-demo` and `erg-testing-demo`.

This is a better failure mode: without Docker or `ERG_TEST_POSTGRES_URL`, the seed proof fails explicitly instead of producing weak or false H2 evidence.

Static FE mock vs BE seed parity audit:

```powershell
.\mvnw "-Dtest=QuestionBankDemoSeedParityTest" test
```

Result: 3 backend parity tests passed.

- FE `mock-question-bank.ts` question IDs: 16.
- BE `QuestionBankDemoBootstrapSeedService` seeded question IDs: 16.
- Missing question IDs in BE seed: none.
- Extra seeded question IDs not present in FE mock: none.
- FE question type/difficulty/status metadata is guarded against BE seed metadata.
- FE quiz-bank rows: 4.
- BE seeded quiz-bank rows: 4.
- Missing quiz-bank rows in BE seed: none.
- FE quiz-bank kind/status/questionIds composition is guarded against BE seed composition.
- FE question type demos: 14.
- BE runtime demo question types: 14.
- Missing runtime question types in BE demo seed: none.

This audit is source-level evidence only; the stronger proof still requires the Postgres-backed seed test above to pass.

Additional BE report-cache hardening:

```powershell
.\mvnw "-Dtest=CacheConfigTest,LmsRuntimeServiceAttemptRulesTest,LmsAssignmentCommandServiceTest,QuizReportServiceTest" test
```

Result: 4 backend test files passed, 19 tests passed.

What changed:
- Added Caffeine caches `quizQuestionAnalytics` and `quizAssignmentReport`, each with 30s TTL and cache stats enabled for load dashboards.
- Added tenant-scoped `@Cacheable` keys for `QuizReportService.questionAnalyticsByVersion(tenantId, quizVersionId)` and `QuizReportService.assignmentReport(tenantId, assignmentId)`.
- Extended attempt-write after-commit eviction to clear `quizAssignmentReport` and `quizQuestionAnalytics` together with LMS homework/class/progress/student-dashboard caches.
- Extended assignment-write after-commit eviction to clear the same report caches after create/update recipients/update/delete assignment.
- Left `syncAttempt` without report eviction because it currently writes append-only client events only and does not change visible progress/report state.

FE integration impact:
- FE should keep using the existing `quiz-reports` TanStack keys and mutation invalidation; no UI or query contract change is required.
- BE now absorbs repeated report reads during teacher/homework-progress refreshes, while writes still force fresh aggregate reads after commit.
- Production proof still needs a real Postgres/auth/RLS browser roundtrip to observe report cache hit/evict behavior with submitted attempts.

Additional FE dashboard bootstrap cache hardening:

```powershell
bun run test src/lib/query-keys.test.ts src/features/lms/infrastructure/lms-dashboard-api.test.ts
bun run typecheck
```

Result: 2 frontend test files passed, 6 tests passed; TypeScript project build passed.

What changed:
- `queryKeys.dashboard.bootstrap` now includes portal, tenant id, and account id instead of using the shared `['dashboard','bootstrap']` key.
- `queryKeys.lmsTeacherShell.bootstrap` delegates to the LMS-scoped dashboard bootstrap key.
- `DashboardShell` and `LmsTeacherShell` now pass tenant/account into bootstrap query keys without changing UI.
- `loadCurrentSessionBootstrap` now scopes its 60s in-memory cache and in-flight request by portal/session portal/account/token fingerprint, so `/api/v1/sessions/current` data is not reused across accounts.

FE integration impact:
- This closes a Tauri/offline-readiness risk where a long-running frontend process could show the previous account's manageable schools/classes/scope after switching sessions.
- No GraphQL/REST contract changed; this is cache ownership only.

Additional FE admin-operation query-key hardening:

```powershell
bun run test src/lib/query-keys.test.ts
bun run typecheck
```

Result: query-key tests passed, 3 tests passed; TypeScript project build passed.

What changed:
- `queryKeys.adminOperations.educationUnits` now includes tenant id for both root and filtered keys.
- `queryKeys.adminOperations.learningResourcesV2` and `learningResourcesWorkspace` now include tenant id while preserving tenant-root prefix invalidation.
- `queryKeys.adminOperations.userAccess` keys (`users`, `options`, `detailAccess`, `detailUser`, `preview`, `scopes`) now include tenant id.
- Existing call sites continue to work through the default `getDefaultTenantId()`, so no UI or data-contract change is required.

FE integration impact:
- LCMS/admin screens no longer share admin education-unit/resource/user-access cache entries across tenants in long-running browser/Tauri sessions.

Additional FE LMS class-workspace query-key hardening:

```powershell
bun run test src/features/lms/api/lms-assignment-command-query.test.ts src/features/lms/components/assign-homework-student-utils.test.ts src/features/lms/classroom/components/classroom-api-fallback.test.ts src/features/lms/components/lms-teacher-shell.test.ts
bun run typecheck
```

Result: 4 frontend test files passed, 15 tests passed; TypeScript project build passed.

What changed:
- Added `lmsAssignmentReadQueryKeys.classWorkspace({ tenantId, usage, schoolId, classId })`.
- Replaced direct class-workspace keys in `AssignHomeworkPage` for class mode and grade fan-out.
- Replaced direct class-workspace keys in `ClassManagementPage` and `ClassStudentsWorkspace`.
- Replaced direct class-roster keys in `StudentGroupsPage`.

FE integration impact:
- Class workspace reads now carry tenant scope and a usage discriminator, so different projections/page sizes do not overwrite each other in long-running browser/Tauri caches.
- No UI, GraphQL, or REST contract changed.

Additional FE teaching-calendar query-key hardening:

```powershell
bun run test src/features/teaching-calendar/api/teaching-calendar-query-keys.test.ts
bun run typecheck
```

Result: 1 frontend test file passed, 1 test passed; TypeScript project build passed.

What changed:
- Added `teachingCalendarQueryKeys.portalRoot(portal, tenantId)`, `.workspace(portal, usage, input)`, and `.referenceCatalog(portal, tenantId)`.
- `LmsCenterUpCalendarWorkspace` now uses the tenant/portal-scoped helpers for teacher workspace and reference catalog queries.
- `LcmsGoogleCalendarWorkspace` now uses the same helpers for LCMS workspace/reference catalog queries.
- Create-schedule success invalidation now targets the tenant-scoped portal root instead of broad hardcoded arrays.

FE integration impact:
- LMS/LCMS calendar data no longer shares reference catalog or workspace cache entries across tenants in long-running browser/Tauri sessions.
- No UI, GraphQL, or REST contract changed.

Additional FE notification query-key hardening:

```powershell
bun run test src/features/notifications/api/notification-api.test.ts
bun run test src/features/lms/components/lms-teacher-shell.test.ts
bun run typecheck
```

Result: notification API tests passed, 5 tests passed; LMS teacher shell tests passed, 2 tests passed; TypeScript project build passed.

What changed:
- Added `NotificationQueryScope` and tenant/account scoped notification keys for inbox, unread count, detail, preferences, and device registration caches.
- Added `resolveNotificationQueryScope(portal)` so LMS notification callers use the active portal session account id plus `getDefaultTenantId()`.
- Updated `LmsNotificationCenter`, `LmsNotificationListPage`, `LmsNotificationDetailPage`, and `LmsTeacherShell` to query and invalidate scoped notification roots.
- Updated Service Worker notification-message invalidation in `LmsTeacherShell` to invalidate the same tenant/account root as the unread badge query.
- Updated notification API tests to assert the current `api-client` contract: normal API requests no longer send `X-Portal` because BE derives portal/tenant from URL and authenticated session.

FE integration impact:
- Long-running web/Tauri sessions no longer reuse notification inbox/unread/detail/device caches across accounts or tenants.
- No notification API payload, route, or UI contract changed.

Additional FE LMS learning-resource library query-key hardening:

```powershell
bun run test src/features/lms/learning-resources/hooks/use-learning-resource-library-catalog.test.ts
bun run test src/features/lms/components/lms-teacher-shell.test.ts
bun run typecheck
```

Result: learning-resource library hook tests passed, 2 tests passed; LMS teacher shell tests passed, 2 tests passed; TypeScript project build passed.

What changed:
- `libraryBootstrapQueryKey` now uses an object scope and includes tenant id with school id and academic year.
- `libraryProgressQueryKey` now includes tenant id and account id because lesson progress is teacher-specific data.
- `LearningResourceDashboardScopeProvider` accepts optional `tenantId` and `accountId` while preserving existing school/year scope.
- `LmsTeacherShell` passes `tenantId` and `account?.id` into the resource-library scope provider.
- Resource-library prefetching and returned `progressQueryKey` now use the same scoped key as the active queries.

FE integration impact:
- Learning-resource library catalog and progress caches no longer bleed across tenants or teachers in long-running browser/Tauri sessions.
- No learning-resource API payload or UI contract changed.

Additional FE question-bank shared key hardening:

```powershell
bun run test src/lib/query-keys.test.ts
bun run test src/features/lcms/quiz/question-bank/api/question-bank-query.test.ts
bun run typecheck
```

Result: shared query-key tests passed, 4 tests passed; question-bank query tests passed, 1 test passed; TypeScript project build passed.

What changed:
- `queryKeys.questionBank.workspace()` in the shared registry now accepts `tenantId` with `getDefaultTenantId()` fallback and returns `question-bank/workspace/tenantId`.
- Added a contract test so future LCMS screens do not reintroduce a global question-bank workspace key.
- Existing `questionBankQueryKeys.workspace(tenantId)` and `questionBankWorkspaceQueryOptions(tenantId)` already matched the scoped shape; this patch aligns the shared registry with that source of truth.

FE integration impact:
- New FE integrations that import shared `queryKeys.questionBank` will not accidentally share question/quiz bank data across tenants.
- No question-bank API payload or UI contract changed.

Additional FE quiz-editor/runtime invalidation hardening:

```powershell
bun run test src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-query.test.ts src/features/lcms/quiz/quiz-editor/api/quiz-editor-query.test.ts
bun run typecheck
rg "invalidateQueries\(\{ queryKey: quizEditorQueryKeys\.root|invalidateQueries\(\{ queryKey: quizRuntimeQueryKeys\.root" src/features/lcms/quiz src/components/quiz -n
```

Result: quiz-runtime/editor query tests passed, 5 tests passed; TypeScript project build passed; root-invalidation search returned no matches.

What changed:
- Added `quizRuntimeQueryKeys.packageRoot(tenantId)` as a tenant-scoped invalidation prefix for runtime packages.
- Quiz-editor create/save/upsert-slide/reorder-slide/publish mutations now invalidate `quizEditorQueryKeys.workspaceRoot()` plus exact draft/version keys instead of `quizEditorQueryKeys.root`.
- Publish still clears the FE normalized runtime package cache for the published quiz id, but TanStack invalidation now targets `quizRuntimeQueryKeys.packageRoot()` instead of every runtime query.

FE integration impact:
- Quiz authoring writes no longer cause broad cross-tenant/cross-feature refetches in long-running web/Tauri sessions.
- Publish still refreshes runtime packages after BE publish/cache eviction, but with less client-side blast radius.
- No quiz-editor/runtime API payload or UI contract changed.

Additional FE LMS assignment invalidation hardening:

```powershell
bun run test src/features/lms/api/lms-assignment-command-query.test.ts src/features/lms/components/assign-homework-student-utils.test.ts src/features/lms/classroom/components/classroom-api-fallback.test.ts src/features/lms/components/lms-teacher-shell.test.ts
bun run typecheck
rg "invalidateQueries\(\{ queryKey: lmsAssignmentReadQueryKeys\.(homeworkRoot|assignmentProgressRoot|studentGroupsRoot)" src/features/lms -n
```

Result: LMS assignment/query tests passed, 4 files and 15 tests passed; TypeScript project build passed; broad LMS assignment root invalidation search returned no matches.

What changed:
- Added `lmsAssignmentReadQueryKeys.homeworkTenantRoot(tenantId)`.
- Added `lmsAssignmentReadQueryKeys.assignmentProgressTenantRoot(tenantId)`.
- Added `lmsAssignmentReadQueryKeys.studentGroupsTenantRoot(tenantId)`.
- Assignment mutations now invalidate homework/progress tenant roots instead of broad roots.
- Student-group mutations now invalidate student-groups/homework tenant roots instead of broad roots.
- Assignment report exact invalidation now passes tenant id explicitly.

FE integration impact:
- Assignment/student-group writes no longer fan out cache invalidation across every tenant's LMS homework/progress/group cache in long-running web/Tauri sessions.
- No LMS assignment/student-group API payload or UI contract changed.

Additional FE quiz-report invalidation hardening:

```powershell
bun run test src/features/lms/api/lms-assignment-command-query.test.ts src/features/lcms/quiz/quiz-reports/api/quiz-report-query.test.ts
bun run typecheck
rg "invalidateQueries\(\{ queryKey: quizReportQueryKeys\.root|quizReportQueryKeys\.root" src -n
```

Result: LMS assignment and quiz-report query tests passed, 2 files and 6 tests passed; TypeScript project build passed; search only finds the internal `reportTenantRoot` helper definition.

What changed:
- Added `quizReportQueryKeys.reportTenantRoot(tenantId)`.
- Report query keys now use `quiz-reports/tenantId/assignment-report/assignmentId` and `quiz-reports/tenantId/question-analytics/quizVersionId`.
- Runtime submit in `PlayerShell` invalidates the assignment report exact key plus the tenant report root instead of global report root.
- LMS assignment/student-group mutations invalidate tenant report root instead of global report root.

FE integration impact:
- Report reads now align with the BE tenant-scoped Caffeine report caches added earlier.
- Submit/assignment writes no longer refetch report caches for unrelated tenants in long-running browser/Tauri sessions.
- No report API payload or UI contract changed.

Additional FE runtime package memory-cache hardening:

```powershell
bun run test src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-api.test.ts src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-query.test.ts
bun run typecheck
```

Result: runtime API/query tests passed, 2 files and 22 tests passed; TypeScript project build passed.

What changed:
- `getQuizPackage(quizId, portal, tenantId)` now accepts tenant id with `getDefaultTenantId()` fallback.
- `normalizedPackageCache` now uses `tenantId:portal:quizId` instead of `portal:quizId`.
- `quizPackageQueryOptions` passes the same tenant id from the TanStack query key into `getQuizPackage`, so TanStack cache ownership and normalized memory-cache ownership match.
- Added regression coverage proving tenant B does not reuse tenant A's normalized package when a revalidation returns an empty/304 body.
- `clearNormalizedQuizPackageCache(quizId)` remains compatible because the new key still ends with `:quizId`.

FE integration impact:
- Runtime package hot-path caching is now tenant-safe for long-running browser/Tauri processes.
- This closes a high-impact 304/revalidation edge case for 5000-student load scenarios without changing runtime API payloads or UI.

Additional FE runtime local-attempt persistence hardening:

```powershell
bun run test src/features/lcms/quiz/quiz-runtime/api/local-quiz-attempt-store.test.ts src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-api.test.ts src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-query.test.ts
bun run typecheck
```

Result: runtime local-store/API/query tests passed, 3 files and 26 tests passed; TypeScript project build passed.

What changed:
- Added `LocalQuizAttemptScope` with `tenantId`, `portal`, and `accountId`.
- Local attempt sessions now store optional `tenantId`, `portal`, and `accountId`.
- Local attempt storage keys now use `tenantId:portal:accountId:assignmentId:quizId` instead of `assignmentId:quizId`.
- `PlayerShell` resolves the current elearning viewer and passes scoped ownership into session restore/create/failure paths.
- Unscoped `getSession()` remains backward-compatible by reading the default anonymous scoped key first and then the legacy key.

FE integration impact:
- Offline drafts, answers, bookmarks, and client event queues no longer bleed across students/teachers/tenants in the same browser or future Tauri process.
- Existing legacy anonymous local sessions remain readable for dev/test compatibility.
- No runtime API payload or UI contract changed.

## Remaining Issues

- Full Postgres-backed seed verification is now wired but still needs Docker Desktop running or an external Postgres configured through `ERG_TEST_POSTGRES_URL`, `ERG_TEST_POSTGRES_USERNAME`, and `ERG_TEST_POSTGRES_PASSWORD`.
- Need one Postgres-backed E2E run with seed enabled to verify:
  - question-bank seed inserts all FE mock IDs;
  - GraphQL `questionBankWorkspace` returns BE data, not fallback mock;
  - bootstrap seed inserts/publishes both demo quizzes;
  - `GET /api/v1/lms/quizzes/erg-training-demo/package`;
  - `GET /api/v1/lms/quizzes/erg-testing-demo/package`;
  - testing package omits answer key;
  - training package includes answer key;
  - start/submit roundtrip works with real auth/session.
- Remaining FE/BE proof still needs a Postgres-backed authenticated E2E pass, because current verification proves compile/unit/targeted behavior but not the whole deployed auth + RLS + Flyway + browser flow.
- Question-bank seed mirrors exact IDs and semantic content. Vietnamese content is ASCII-normalized in Java seed to avoid broad encoding churn; FE uses exact IDs to hydrate the rich existing UI template, so visual behavior stays unchanged.
- Student group `note/color` now persist through BE contract and migration. Remaining proof is still E2E: run migrated Postgres/auth/RLS browser flow to confirm create/edit/reload/group-assignment/report with real sessions.
- Assignment progress now exposes pinned quiz resources and FE uses them for question analytics. Remaining proof is still E2E: submit attempts on the assigned quiz version, then verify `assignmentReport` and `quizQuestionAnalytics` refresh in the browser with real auth/RLS.

Additional FE API-client GET dedupe hardening:

```powershell
bun run test src/lib/api-client.test.ts
bun run test src/lib/api-client.test.ts src/features/lms/learning-resources/api/teacher-resource-dashboard-schools.test.ts
bun run typecheck
```

Result: `api-client.test.ts` passed 12/12; combined targeted suite passed 2 files and 18 tests; TypeScript project build passed.

What changed:
- GET in-flight dedupe no longer keys only by URL and Authorization.
- The dedupe key now includes resolved portal, Authorization, browser cache mode, and stable response-variant request headers: `Accept`, `Accept-Language`, `If-Modified-Since`, `If-None-Match`, and `Range`.
- Volatile headers such as `X-Request-ID` remain excluded so duplicate identical GETs still collapse.
- Added regression coverage for same URL/shared teacher token across explicit `lms` and `lcms` portals.
- Added regression coverage for same URL/token with `no-store` and `cache: "default"` running concurrently.

FE integration impact:
- Reduces risk of cross-portal or wrong cache-mode promise reuse in long-running web/Tauri sessions.
- Keeps burst-load protection for identical GETs while avoiding response-shape/cache-policy bleed.
- No API payload or UI contract changed.

Additional FE LMS teacher resource in-flight cache hardening:

```powershell
bun run test src/features/lms/learning-resources/api/teacher-resource-dashboard-schools.test.ts
bun run test src/lib/api-client.test.ts src/features/lms/learning-resources/api/teacher-resource-dashboard-schools.test.ts
bun run typecheck
```

Result: teacher resource dashboard tests passed 6/6; combined targeted suite passed 2 files and 18 tests; TypeScript project build passed.

What changed:
- `subjectTreeCache` and `subjectProgressCache` now include a teacher dashboard scope in their module-level in-flight keys.
- Scope includes tenant id (`VITE_TENANT_ID` fallback `erg`), portal `lms`, and the current LMS access token.
- Added regression coverage proving identical subject/school/year requests with different credentials no longer reuse the same in-flight promise for tree/progress.

FE integration impact:
- Prevents stale teacher resource tree/progress responses from bleeding across account/tenant changes in one browser/Tauri process.
- This is an in-memory in-flight-only cache change; no UI, API payload, or TanStack query key contract changed.

Remaining proof status:
- These fixes further harden FE caching for 5000-student/teacher burst scenarios, but they do not replace the still-required Postgres/auth/RLS/browser E2E and burst/cache verification listed below.

Additional BE cache verification and legacy FE attempt-store audit:

```powershell
rg "lib/api" src -n
./mvnw "-Dtest=CacheConfigTest,LmsRuntimeServiceAttemptRulesTest,LmsAssignmentCommandServiceTest,QuizReportServiceTest" test
```

Result: no direct UI/runtime consumer imports `@/lib/api`; BE targeted cache/report suite passed 4 files and 19 tests.

What changed:
- No code change for `src/lib/attempt-store.ts`; it is currently only reachable through legacy `src/lib/api.ts`.
- The active quiz player/runtime path uses `localQuizAttemptStore`, which was already scoped by tenant/portal/account in the previous checkpoint.
- BE Caffeine/runtime/assignment/report cache tests were rerun after FE cache hardening and remain green.

Follow-up note:
- If `src/lib/api.ts` is revived later, either retire it in favor of the LCMS quiz-runtime APIs or add the same tenant/portal/account scope to its `browserAttemptStore` keys before exposing it to UI again.

Additional BE runtime package cache publish-eviction coverage:

```powershell
./mvnw "-Dtest=QuizPublishServiceTest" test
./mvnw "-Dtest=CacheConfigTest,LmsRuntimeServiceAttemptRulesTest,LmsAssignmentCommandServiceTest,QuizReportServiceTest,QuizPublishServiceTest,LmsControllerHttpCacheTest" test
```

Result: new publish service test passed 1/1; combined BE cache/runtime/report suite passed 6 files and 21 tests.

What changed:
- Added `QuizPublishServiceTest.publishEvictsRuntimePackageCacheForTenantQuiz`.
- The test exercises the publish happy path with mocked repositories/compiler/signer/cache manager.
- It verifies `quizRuntimePackage` evicts the exact key `tenant-a:quiz-1` after publish, and the quiz row points at the new active version/hash.

Integration impact:
- FE publish already clears the normalized runtime package memory cache; BE now has regression coverage proving the server-side package cache is also invalidated after LCMS publish.
- This protects the 5000-student load path from serving a stale package for up to the 10-minute Caffeine TTL after an author publishes a replacement version.
- No API payload or UI contract changed.

Additional FE API-enabled fallback audit and PWA notification storage hardening:

```powershell
bun run test src/features/lms/mobile/hooks/use-pwa-notifications.test.tsx
bun run test src/features/lms/mobile/hooks/use-pwa-notifications.test.tsx src/features/notifications/api/notification-api.test.ts
bun run typecheck
```

Result: new PWA notification hook tests passed 3/3; combined notification suite passed 2 files and 8 tests; TypeScript project build passed.

What changed:
- Added `getPwaNotificationDeviceStorageKey()` in `use-pwa-notifications.ts`.
- PWA notification device localStorage is now scoped by `tenantId` and LMS `accountId` when a signed-in LMS session exists.
- Anonymous/no-session mode keeps the legacy `erg-lms-pwa-notification-device` key for local/dev compatibility.
- Signed-in accounts no longer treat a legacy global device record as enabled.
- Added regression coverage for scoped key generation and legacy global isolation.

Audit notes:
- Student dashboard API path already propagates GraphQL failures when API base is configured and keeps API-enabled empty results empty.
- Learning-resource library fallback from GraphQL to REST BE is acceptable; it only returns mock data when no API base is configured or explicit authoring mock mode is enabled.

Integration impact:
- Aligns local PWA notification device persistence with the already tenant/account-scoped notification TanStack query keys.
- Reduces account bleed risk for long-running web sessions and future Tauri desktop forks.
- No visual UI change and no API payload change.

Additional FE LMS class workspace query-key hardening:

```powershell
bun run test src/features/lms/api/lms-assignment-command-query.test.ts
bun run test src/features/lms/api/lms-assignment-command-query.test.ts src/features/lms/components/assign-homework-student-utils.test.ts src/features/lms/classroom/components/classroom-api-fallback.test.ts src/features/lms/components/lms-teacher-shell.test.ts src/features/lms/mobile/hooks/use-pwa-notifications.test.tsx
bun run typecheck
```

Result: LMS assignment query-key test passed 4/4; targeted LMS/PWA suite passed 5 files and 18 tests; TypeScript project build passed.

What changed:
- `lmsAssignmentReadQueryKeys.classWorkspace` now includes LMS account id in addition to tenant, usage, school, and class.
- The default account scope is resolved from `readStoredAuthSession("lms")`, with `anonymous` fallback for no-session/local mode.
- Added `classWorkspaceTenantRoot(tenantId)`.
- Assignment and student-group mutations now invalidate the class workspace tenant root, because class workspace data includes roster and active assignment reads.

Integration impact:
- Prevents class roster/assignment workspace data from bleeding across teachers/accounts in a long-running browser or future Tauri desktop process.
- Keeps all existing UI and API payload contracts unchanged.

Additional FE LCMS question-bank API fallback hardening:

```powershell
bun run test src/features/lcms/quiz/question-bank/api/question-bank-query.test.ts src/features/lcms/quiz/question-bank/api/question-bank-api.test.ts
bun run typecheck
```

Result: question-bank API/query suite passed 2 files and 5 tests; TypeScript project build passed.

What changed:
- `questionBankWorkspaceQueryOptions` now checks `hasApiBase()` before adding mock `initialData`.
- When API base is enabled, the TanStack query no longer seeds `mockQuestionBankData` into the cache and `placeholderData` only reuses previous data.
- When API base is not configured, the local/dev mock fallback remains unchanged.
- `QuestionBankWorkspace` now uses an empty typed data object while API-backed data is still loading or has errored, avoiding crashes without showing mock FE content.

Integration impact:
- LCMS question bank now treats BE GraphQL workspace data as authoritative whenever API integration is active.
- API failures or empty BE workspaces are no longer hidden by FE mock initial data.
- This keeps the production path aligned with the seed-parity work: FE mock examples belong in BE seed/test data, not in runtime cache when API-backed.
- No UI layout, labels, or API payload contracts changed.

Additional BE LMS class selector cache-key hardening:

```powershell
.\mvnw "-Dtest=ServiceBoundaryArchitectureTest,CacheConfigTest" test
.\mvnw "-Dtest=CacheConfigTest,LmsRuntimeServiceAttemptRulesTest,LmsAssignmentCommandServiceTest,QuizReportServiceTest,QuizPublishServiceTest,LmsControllerHttpCacheTest" test
```

Result: architecture/cache suite passed 6 tests; combined BE cache/runtime/report suite passed 6 files and 21 tests.

What changed:
- `LmsRuntimeService.listClassesBySchool` cache key for `lmsClassesBySchool` now includes `#access.currentEducationUnitId()`.
- Added `ServiceBoundaryArchitectureTest.lmsClassesBySchoolCacheKeyIncludesResolvedDefaultSchoolScope` to prevent regressing the key.

Why it matters:
- `listClassesBySchool` accepts nullable `schoolId`; when it is absent, the service resolves the school from the current access context.
- The previous cache key used raw `schoolId`, tenant, and managed school ids, so two long-running sessions with the same managed scope but different current school could reuse the wrong class list.
- The new key keeps BE class selector caching cheap while preventing cross-current-school bleed.
- No API payload or FE UI contract changed.

Final no-goal checkpoint for remaining production proof:

```powershell
docker version
.\mvnw "-Dtest=GraphQlEnterpriseSecurityAuditTest,GraphQlSecurityGuardContractTest,TenantContextTest,PermissionAuthorizationManagerTest,AccessControlSecurityTest,LcmsWorkspaceGraphQlControllerTest,LmsClassWorkspaceGraphQlControllerTest,StudentDashboardGraphQlControllerTest" test
.\mvnw "-Dtest=DatabaseReseedRunnerTest,TenantGucApplierIntegrationTest,QuestionBankWorkspaceQueryCountIntegrationTest,LmsAssignmentProgressWorkspaceQueryCountIntegrationTest" test
```

Result:
- Docker client exists, but daemon is unavailable at `npipe:////./pipe/dockerDesktopLinuxEngine`.
- No `ERG_TEST_POSTGRES_URL` is configured in the current shell.
- Security/GraphQL suite passed build with 53 tests, 0 failures, 0 errors, and 17 Postgres-dependent AccessControl tests skipped.
- Postgres proof suite passed build with 5 skipped tests, 0 failures, and 0 errors because no Docker/external PostgreSQL is available.

What changed:
- Updated `GraphQlEnterpriseSecurityAuditTest` to assert the current bounded Caffeine-backed `GraphQlRateLimiter` implementation instead of the previous `ConcurrentHashMap` expectation.
- Marked `AccessControlSecurityTest` as Postgres-proof-only with `@EnabledIf("postgresProofDatabaseAvailable")`, preventing accidental H2 boot against PostgreSQL-only migrations.
- Added the same Postgres availability guard and dynamic PostgreSQL registration to `LmsAssignmentProgressWorkspaceQueryCountIntegrationTest`.

Remaining blocker:
- Full production proof is not executable in this environment until Docker Desktop is running or `ERG_TEST_POSTGRES_URL` points at a real PostgreSQL database.
- Once available, rerun the Postgres proof suite without skips, then run API-backed browser E2E for login/session, LCMS question bank/editor/publish, LMS assignment, student training/testing attempt, submit/report, and cache/burst paths.
