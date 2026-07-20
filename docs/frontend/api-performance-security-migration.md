# Backend API migration guide for FE

Status: initial guide for the performance/security implementation. This file must be updated in the same change whenever a request or response contract changes.

## Current release

This release contains breaking REST removals and payload changes in addition to internal performance work. FE must apply the migrations below before rollout. Do not treat this batch as wire-compatible with the previous backend revision.

The following are internal-only changes:

- PostgreSQL tenant/user/portal context is installed in one transaction-local statement.
- Blocking read fan-out uses a bounded executor instead of the JVM common ForkJoinPool.
- GraphQL metrics no longer expose `tenant` as a metric label.
- A GraphQL HTTP body larger than 65,536 bytes is rejected before parsing with HTTP `413` and GraphQL extension code `PAYLOAD_TOO_LARGE`.
- The admin unified security feed rejects requests whose requested window (`page * limit`) exceeds 2,000 rows with `400`; use smaller pages, source/user filters, or the future cursor endpoint.
- User activity pagination is now executed in PostgreSQL; the response shape and metadata remain unchanged.
- `schoolOperations` is selection-aware: the backend only loads `academicYears`, `grades`, `classes`, `students`, or `subjects` when the GraphQL selection requests them. Existing queries are compatible; narrow selections are recommended for faster response.
- Teacher invite/reset and recovery-code endpoints now persist a transactional mail intent and return after database commit; `QUEUED_FOR_DELIVERY` means the SMTP provider has not necessarily accepted the message yet. The outbox worker retries transient SMTP failures and dead-letters exhausted events.
- Authentication principal and session bootstrap authorization decisions are no longer cross-request cached; revocation and role changes are observed on the next request. Business read-model caches remain enabled with tenant-scoped keys.
- GraphQL `assignmentReport` accepts optional `page` and `size` (zero-based, maximum size `100`) and returns `page`, `size`, `totalPages`, and `hasNext`; request subsequent pages instead of assuming `students` contains the complete cohort.
- Student assignment and score endpoints always use bounded database pagination: `GET /api/v1/lms/students/me/assignments?page=0&size=50` and `GET /api/v1/lms/students/me/scores?page=0&size=50`. Missing parameters default to `page=0,size=50`; `size` is capped at `100`. Responses include `page`, `size`, `totalItems`, `totalPages`, and `hasNext`.
- GraphQL rate limiting can use a Redis-backed shared counter when `ERG_GRAPHQL_RATE_LIMIT_REDIS_ENABLED=true`; keys are hashed and tenant/user values are never emitted as metric labels. Redis errors are fail-closed by default (`ERG_GRAPHQL_RATE_LIMIT_FAIL_CLOSED_ON_REDIS_ERROR=true`).
- Calendar workspace reads now reject ranges longer than 31 inclusive days or more than 100 distinct IDs in any filter list with `400`; month views should request one month at a time and split custom ranges.
- Heavy assignment-progress exports use `POST /api/v1/reports/jobs/assignment-progress` with `{ "assignmentId": "...", "idempotencyKey": "..." }`. The response is an acknowledgement with `data.id`, `status=queued`, and `progress`; poll `GET /api/v1/reports/jobs/{id}` with backoff until `completed` or `failed`. The full student result is only present in `data.result` after completion; do not retry the original heavy query in a tight loop.
- `learningResourceLibrary` now bounds each taxonomy level (subjects, levels, topics) to 500 nodes at the database query boundary. If the requested tenant tree exceeds that limit, GraphQL returns a validation error; retry with `subjectId`, `gradeId`, or `topicId` filters instead of requesting the entire tree.
- Student-group workspace reads are bounded to 200 groups and 10,000 membership rows at the database boundary; use `classId`, managed class scope, or `ownerUserId` filters for large teacher accounts.
- Legacy permission and global session-bootstrap catalogs are bounded to 500 rows. Use the paged permission catalog endpoint or scoped session requests when an administrator manages more records.
- The curriculum tree endpoint now rejects trees exceeding 500 subjects, levels, or topics with `CURRICULUM_TREE_TOO_LARGE`; FE should request a narrower status/scope instead of rendering an unlimited tenant-wide tree.
- Login-attempt audit responses now return at most the latest 100 attempts for the requested user or email; use the paged unified security feed for deeper history.
- `schoolOperations` rejects selected sections above their workspace bounds: 1,000 students, 500 classes, or 500 subjects. The successful payload shape is unchanged. On HTTP `400 BAD_REQUEST`, switch to the dedicated paged roster/class API instead of retrying the same full GraphQL selection.
- Session bootstrap returns at most 500 manageable education units and 500 manageable classes. Accounts above the bound receive `400 BAD_REQUEST` and must select a narrower organization scope before loading the LMS workspace.
- LCMS quiz version history returns at most 100 versions. Quizzes with deeper history receive `400 BAD_REQUEST`; FE should use the forthcoming paged version-history endpoint rather than assuming the embedded history is unlimited.
- Assignment report jobs are capped at 10,000 result rows. Larger jobs transition back to retry/failed with `lastError` containing `report exceeds 10,000 result rows`; FE must narrow the assignment/report scope and must never render a truncated job as completed.
- Catalog/CMS/document/module-workflow demo endpoints are disabled in the `prod` profile until their tenant-scoped PostgreSQL stores are wired. FE should handle `501 PROCESS_LOCAL_STATE_DISABLED` as an unavailable feature, not retry it.

FE requests already below 64 KiB need no code change. Large GraphQL uploads or large inline variable payloads must move binary content to the existing upload flow and send references instead.

## Active breaking changes

### Login request and retired public auth endpoints

`POST /api/v1/auth/login` renamed the credential field from `email` to `identifier`. The new field accepts the configured login identifier (for example email or username) and is limited to 254 characters.

Old:

```json
{"email":"teacher@example.com","password":"...","portal":"lms"}
```

New:

```json
{"identifier":"teacher@example.com","password":"...","portal":"lms"}
```

The previous public register, PIN verification/resend, forgot/reset-password, Google/social login, and generic OAuth redirect/callback handlers were removed from `AuthController`. Account provisioning and recovery now use the authenticated/admin user lifecycle endpoints. When `erg.auth.bff-enabled=true`, OIDC BFF exposes `GET /api/v1/auth/login`, `/session`, and `/csrf`; FE must not assume those conditional handlers exist when the flag is disabled.

### Admin user creation request

`POST /api/v1/users` no longer accepts a plaintext `password`, a simple `roles` list, or `email` as the field name.

Old:

```json
{
  "email": "teacher@example.com",
  "password": "temporary-secret",
  "fullName": "Teacher A",
  "roles": ["teacher"],
  "phone": "0900000000"
}
```

New:

```json
{
  "primaryEmail": "teacher@example.com",
  "fullName": "Teacher A",
  "phone": "0900000000",
  "recoveryEmail": "recovery@example.com",
  "roleGroupIds": ["teacher"],
  "policies": [
    {
      "scopeType": "school",
      "scopeId": "school-001",
      "roleGroup": "teacher",
      "modules": ["lms"]
    }
  ],
  "credentialDelivery": "email"
}
```

Recovery email challenge/verification is available through `POST /api/v1/users/me/recovery-email/challenges` and `/verify`; password change is `PUT /api/v1/users/me/password`. Admin resend/reset credential operations are separate endpoints and return delivery lifecycle state.

### Legacy HocLieu REST surface removed

All handlers under `/api/v1/hoclieu/**` and `/api/v1/admin/hoclieu/**` were removed together with their request/response types and persistence services. Calls now return `404` because no controller mapping exists. This includes home, library bootstrap/progress, resource/taxonomy CRUD, asset launch/download/slides/annotations, and admin Studio bootstrap.

There is no approved one-to-one replacement mapping yet. `/api/content/**`, `/api/curriculum/**`, and `/api/v1/lcms/**` expose newer learning-content capabilities, but FE must not substitute them by path name alone because their authorization and payload contracts differ. Rollout options are: keep the legacy FE feature disabled, restore/version the old API, or approve an explicit endpoint-by-endpoint migration matrix.

### User creation response

Old `data`:

```json
{
  "id": "user-123",
  "email": "teacher@example.com",
  "fullName": "Teacher A",
  "status": "ACTIVE"
}
```

New `data`:

```json
{
  "user": {
    "id": "user-123",
    "primaryEmail": "teacher@example.com",
    "fullName": "Teacher A",
    "status": "PENDING_ACTIVATION"
  },
  "lifecycle": {
    "status": "PENDING_ACTIVATION",
    "isProfileCompleted": false,
    "recoveryEmailMasked": "t***@example.com",
    "recoveryEmailVerified": false,
    "mustChangePassword": true,
    "nextSteps": ["CHANGE_PASSWORD", "COMPLETE_PROFILE"]
  },
  "deliveryStatus": "QUEUED_FOR_DELIVERY",
  "oneTimePassword": null
}
```

FE must read the identifier from `data.user.id`, primary email from `data.user.primaryEmail`, and onboarding state from `data.lifecycle`. `oneTimePassword` may be null; never assume credentials were delivered when `deliveryStatus` is only `QUEUED_FOR_DELIVERY`.

### Auth profile, user detail, and session bootstrap

- Auth/user profile responses removed `googleSub`, `googleEmail`, and `googleEmailVerified`. Use `provider` and `lastLoginProvider` for display only; no replacement Google identity payload is exposed.
- User detail removed `socialAccounts`. Existing `user.socialLinks` remains the display-oriented social-link map.
- Auth profile and user profile now include `lifecycle`.
- Session bootstrap added top-level `portals` and `lifecycle` before `currentScope`.

Old session bootstrap shape:

```json
{
  "user": {},
  "permissions": {},
  "currentScope": {},
  "manageableUnits": [],
  "manageableClasses": []
}
```

New shape:

```json
{
  "user": {},
  "permissions": {},
  "portals": ["lms", "lcms"],
  "lifecycle": {
    "status": "ACTIVE",
    "isProfileCompleted": true,
    "recoveryEmailMasked": null,
    "recoveryEmailVerified": false,
    "mustChangePassword": false,
    "nextSteps": []
  },
  "currentScope": {},
  "manageableUnits": [],
  "manageableClasses": []
}
```

### LMS runtime package and attempt submission

`QuizPackageResponse` adds an `answerKey` object. Treat it as runtime grading data and do not render or log it in student-facing UI.

Old submit request:

```json
{
  "answers": {"q1": "A"},
  "submittedAt": "2026-07-16T15:00:00Z"
}
```

New submit request:

```json
{
  "answers": {"q1": "A"},
  "submittedAt": "2026-07-16T15:00:00Z",
  "packageHash": "sha256:...",
  "quizVersion": "1",
  "clientResult": {"score": 1},
  "clientEvents": []
}
```

The added submit fields are currently nullable at DTO validation level, but FE should send the package hash/version obtained when the attempt started so the backend can detect stale/offline submissions. Assignment and score list responses changed from `{ "items": [...] }` to include `page`, `size`, `totalItems`, `totalPages`, and `hasNext` as documented above.

## Upcoming contract changes requiring FE work

The following changes are planned but are not active until a versioned release note is added here:

- Large reports will return a job acknowledgement and require polling a job-status endpoint.
- Unbounded list requests will require bounded `page`/`size` or cursor parameters.
- GraphQL operations over the configured complexity budget will return a structured validation error before resolver execution.
- Workspace GraphQL responses may omit sections that were not selected by the query; FE must request every section it renders.

## FE compatibility rules

1. Do not assume a list response contains all records. Render pagination metadata and request the next page/cursor.
2. Treat `429`, `413`, and GraphQL validation errors as actionable user/API-limit states, not as empty data.
3. For report jobs, keep the job ID and poll with backoff; do not retry the original heavy report request in a tight loop.
4. Do not send duplicate IDs in bulk request arrays. The backend will reject or deduplicate them depending on the endpoint contract.
5. Keep GraphQL selection sets minimal and request only the workspace sections displayed by the current screen.

## Contract update checklist

When a backend change modifies payloads, add before merge:

- endpoint or GraphQL operation name;
- old request/response example;
- new request/response example;
- migration and fallback behavior;
- FE owner and rollout flag;
- focused contract test and release version.
