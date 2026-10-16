# BE-068 — Explicit learner offline policy contract

> Status: completed — BE-068 implementation and contract publication completed 2026-08-09 after ERG-107 FE audit found the learner attempt contract had no explicit offline capability.

## Goal

Publish an immutable, tenant-scoped `offlineAllowed` decision and the exact package version needed by the learner attempt policy snapshot. Default deny must remain safe for all existing assignments; only an explicit assignment opt-in can enable server-authoritative offline resume/sync.

## Contract boundary

- `erg-spring` owns the assignment policy, published package lineage and attempt policy snapshot.
- `erg-system` consumes the additive REST fields after this task publishes and locks OpenAPI.
- The client cannot enable offline by route, quiz ID, grading mode alone, or a client-controlled request field.
- `offlineAllowed` is effective only when the assignment opts in and the published package is `server-authoritative`; client-first packages are denied by the server policy because their answer key must not be copied into the learner snapshot.
- Existing rows migrate to `offline_allowed=false`; existing requests remain source-compatible through overloaded DTO constructors and nullable additive request fields.

## Exact scope

- `src/main/java/**/model/lms/Assignment.java`
- `src/main/java/**/common/request/quiz/AssignmentRequests.java`
- `src/main/java/**/common/dto/lms/LmsRuntimeDtos.java`
- `src/main/java/**/lms/internal/attempt/LmsAttemptUseCase.java`
- `src/main/java/**/service/lms/LmsAssignmentCommandService.java`
- `src/main/java/**/lms/internal/readmodel/LmsStudentReadModelUseCase.java`
- `src/main/resources/db/migration/postgres/V90__lms_assignment_offline_policy.sql`
- focused policy/assignment/controller tests, OpenAPI export/checksum and generated contract sync.

## TDD sequence

1. Add RED tests proving default-deny, explicit server-authoritative opt-in, client-first denial, exact `quizVersion` publication and assignment write round-trip.
2. Add the nullable request/response fields, persistence column, immutable attempt policy mapping and tenant-scoped migration.
3. Run focused Maven tests and REST controller contract tests.
4. Publish OpenAPI/checksum, run full BE verify and contract gates.
5. Sync `erg-system` REST contract/types and run FE contract/type/API/full verification.

## Safety invariants

- `offlineAllowed` is computed from server-side assignment/package state only.
- Missing/invalid package lineage, non-server-authoritative grading, anonymous/incorrect tenant or default-null assignment state yields false.
- Policy snapshot carries `quizVersion` (label) and `quizVersionId` (lineage ID); FE must bind both package hash and version before resuming.
- No answer key, access token, signed URL or authorization decision is persisted by BE or added to the learner package through this task.

## Verification

```powershell
./mvnw.cmd -q -Dtest=LmsAttemptPolicyContractTest,LmsAssignmentCommandServiceTest,LmsControllerTest test
./mvnw.cmd -q verify
node scripts/assemble-openapi-contract.cjs --write
node scripts/export-openapi-contract.cjs --file contracts/openapi/erg-openapi.json --check
node scripts/check-openapi-contract.cjs
```

Then in `erg-system`: `bun run contract:rest:sync`, `bun run verify:api-contract`, `bun run test`, `bun run lint`, `bun run build`, `bun run budget:check`.

## Rollback

The migration is additive and default-deny. Revert application mapping only if required; retain the column and published field for forward compatibility.
