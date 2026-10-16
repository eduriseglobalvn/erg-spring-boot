# BE-063: Shared learning-content lifecycle

## Goal

Implement one tenant-scoped, permission-checked lifecycle contract for curriculum nodes,
canonical learning resources, question-bank questions and quizzes. The lifecycle must make
draft/review/approve/publish/archive/rollback transitions explicit, reject illegal transitions,
keep published snapshots immutable, and leave a durable transition trail that can later be
consumed by the audit/export task without coupling the authoring modules to the audit module.

## Current-state findings

- Curriculum subject/level/topic and `content_items` already have free-form `status` columns,
  but commands accept arbitrary status values and hard-delete remains the only irreversible path.
- `QuizPublishService` already creates immutable `quiz_versions` and flips the active version,
  but quiz status transitions and rollback are not represented as one shared contract.
- Question-bank and content routes use separate ad-hoc update/publish semantics; no common
  transition matrix, transition reason, actor, or optimistic record-version guard exists.
- Existing `content.manage`, `curriculum.manage`, and `lms.quiz.publish` permission vocabulary
  is the safe compatibility boundary; new lifecycle endpoints will fail closed through explicit
  `lifecycle.*` permissions with the existing manage permissions as compatibility aliases.

## Design

1. Add a public `content.lifecycle` contract containing canonical resource kinds and states:
   `DRAFT -> IN_REVIEW -> APPROVED -> PUBLISHED`, with `DRAFT/IN_REVIEW/APPROVED/PUBLISHED`
   allowed to move to `ARCHIVED`; `ARCHIVED -> DRAFT` is the only restore path. Publish and
   archive transitions require an explicit reason and actor; approval cannot be performed by the
   same actor that submitted review unless the caller has the elevated manage permission.
2. Add a pure transition policy and typed result/error codes. It is unit-tested independently,
   so no controller can bypass the matrix by writing `status` directly.
3. Add a tenant-scoped `content_lifecycle_transitions` append-only table with entity kind/id,
   from/to state, actor, reason, source version, resulting version, correlation/idempotency key,
   timestamps, RLS, and a uniqueness guard for replay. This is a lifecycle trail, not the final
   immutable audit implementation owned by BE-061.
4. Add a `ContentLifecycleOperations` facade and REST command endpoint under `/api/v1/content`.
   The command loads the target, verifies tenant/scope/permission, checks optimistic version,
   applies only the validated state transition, and returns a typed response. Existing create/
   update paths normalize status through the same policy; publish remains snapshot-producing for
   quizzes and never mutates a published version.
5. Integrate curriculum subject/level/topic, `ContentItem`, `Question`, and `Quiz` adapters.
   Quiz publish delegates its final state change to the lifecycle policy while retaining compile,
   answer-key isolation, active-version switching and idempotent publish behavior. Rollback for
   quizzes creates a new draft fork from the selected immutable version; it never edits a version.
6. Add focused tests for every matrix edge, cross-tenant denial, optimistic stale write,
   duplicate idempotency replay, approval separation, immutable-version behavior, and migration
   contract. Capture OpenAPI and synchronize FE generated REST types; run architecture, contract,
   focused and full Maven verification.

## Files expected

- `src/main/java/com/erg/ergspringboot/content/lifecycle/**`
- `src/main/java/com/erg/ergspringboot/controller/content/ContentLifecycleController.java`
- lifecycle adapters under curriculum/learningcontent/quiz authoring boundaries
- `src/main/resources/db/migration/postgres/V92__content_lifecycle_contract.sql`
- focused unit, repository, controller and migration tests
- runtime OpenAPI, FE vendored REST contract/types and architecture/performance catalog entries

## Verification

- RED: transition matrix and illegal-transition tests fail before implementation.
- GREEN: focused lifecycle/adapters/controller/migration/architecture suites pass.
- `bun run contract:check` in `erg-system` after OpenAPI sync.
- `./mvnw.cmd -q -DforkCount=0 -DreuseForks=false verify` with Surefire/Failsafe counts recorded.
