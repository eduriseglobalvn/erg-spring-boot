# BE-011 ELEARNING Discussion and Announcement Contract Plan

> Status: completed — canonical BE-011 evidence is in `D:/ERG/TASK/ERG_Task_Management_.xlsx`.

## Goal and dependency

- Publish the missing authenticated ELEARNING commands for discussion create/reply/reaction and announcement read state required by FE tasks ERG-082, ERG-022 and ERG-041.
- CRM contract slice is complete and verified; this is the next BE-011 portal-contract slice.

## Baseline and invariants

- Characterize the existing student dashboard GraphQL operations, schema, resolvers, services, persistence and permission checks before choosing exact owners.
- Tenant, school and actor identity come from the authenticated principal; commands never trust client-controlled tenant or user identity.
- Retryable commands use explicit idempotency or optimistic versioning as appropriate and return the existing stable GraphQL error/correlation contract.
- Existing read operations and UI-facing field semantics remain backward compatible.
- Flyway changes are append-only; published schema/checksum and generated contract artifacts are never edited outside their official scripts.

## Expected file scope

- `src/main/resources/graphql/schema.graphqls` through the canonical schema workflow.
- Existing ELEARNING/student dashboard GraphQL resolver and application-service owners identified by characterization.
- Discussion, reply/reaction and announcement-read persistence owners or one append-only migration if the current model is insufficient.
- Focused resolver/service/authorization/tenant/idempotency tests.
- GraphQL schema checksum through `scripts/check-graphql-schema.cjs`.
- This plan.

## Execution

1. Inventory the exact FE operations and current BE schema/resolver/persistence coverage; record the smallest compatible contract.
2. Write RED tests for create discussion, reply, reaction toggle/idempotency, announcement-read state, tenant isolation, permission denial and invalid input.
3. Implement the smallest controller/resolver, DTO/input, application-service and persistence changes that satisfy the tests.
4. Run focused Maven tests, GraphQL schema/checksum gates and portal contract tests.
5. Run `mvnw.cmd verify`, publish measured evidence, then sync/regenerate the FE GraphQL contract.

## Verification commands

```powershell
.\mvnw.cmd -Dtest=<focused classes> test
node scripts/check-graphql-schema.cjs
node --test scripts/graphql-schema-contract-lib.test.cjs
.\mvnw.cmd verify
```

Every command must exit 0. FE sync follows only after BE verification.

## Rollback

- Revert only this slice's resolver/service/model/test files through targeted patches; never reset the dirty worktree.
- Forward-fix any released Flyway migration; never modify or delete a published migration.

## Definition of Done

- ERG-082/022/041 can consume generated operations without fixture or handwritten contract fallbacks.
- Authorization, tenant isolation, validation and retry semantics are covered by focused tests.
- Published GraphQL schema and checksum pass, full Maven verify passes, and FE sync/codegen succeeds.
- Workbook and plan contain final evidence and the next BE-011 slice is explicit.

## Out of scope

- UI changes, Matching files, LMS attendance/gradebook contracts, unrelated security/cache/load work and generated FE type hand edits.
