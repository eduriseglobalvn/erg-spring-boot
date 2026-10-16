# BE-055 — Operational write API for school, class, student, subject and enrollment

## Objective

Replace the remaining prototype/no-op operational writes with a tenant-scoped command surface that is safe for the ERG single-organization deployment and can later be consumed by the canonical Admin LMS frontend.

## Scope

- Preserve the existing school command API and harden its contract where needed.
- Add explicit class, student, subject and enrollment commands behind bounded-context facades.
- Enforce tenant and managed-school scope from `UserAccessContext`; request headers never define ownership.
- Add optimistic version/conflict handling and idempotent bulk enrollment/import semantics.
- Emit an auditable command outcome for each mutation without leaking PII into application logs.
- Publish REST/GraphQL contract changes and synchronize the FE generated types through the repository contract protocol.

## Files and boundaries

- `src/main/java/com/erg/ergspringboot/controller/lcms/` command controllers.
- `src/main/java/com/erg/ergspringboot/lcms/` or `school/` public operations facades.
- `src/main/java/com/erg/ergspringboot/service/lms/` command use cases for class/student/subject/enrollment.
- `src/main/java/com/erg/ergspringboot/common/request|response/` typed requests and responses.
- `src/main/java/com/erg/ergspringboot/model/lms/` only when an existing aggregate cannot represent the invariant.
- `src/main/resources/db/migration/postgres/` append-only migrations.
- `src/test/java/com/erg/ergspringboot/` focused command, authorization, idempotency and migration tests.
- `contracts/openapi/erg-openapi.json` and FE vendored/generated types only via the sync scripts.

## TDD and verification

1. Add contract tests that fail for missing class/student/subject/enrollment commands, cross-tenant access, stale versions, invalid school/class references, duplicate codes and repeated idempotency keys.
2. Implement the smallest production command slice using existing repositories and authorization annotations; no static rows or client-supplied tenant/actor fields.
3. Add migration/indexes only where required by the commands and verify the Flyway upgrade path.
4. Run focused Maven tests, OpenAPI export/check and FE contract sync/check.
5. Run `./mvnw.cmd -q verify`, `git diff --check`, then update this plan and the canonical workbook evidence.

## Acceptance boundary

The task is complete when the command contract is typed, tenant/scope-safe, conflict-aware, idempotent for bulk operations, migration-safe, covered by integration tests, and consumed by regenerated FE contract types. Production proxy/staging execution remains outside this code task and must be recorded separately if unavailable.
