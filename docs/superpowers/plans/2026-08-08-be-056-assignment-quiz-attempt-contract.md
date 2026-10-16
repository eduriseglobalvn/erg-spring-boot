# BE-056 — Assignment / Quiz / Attempt contract and immutable policy snapshot

## Goal

Make the LMS attempt contract server-authoritative and safe for enterprise use:

- assignment, quiz, package and attempt identifiers remain distinct and are never inferred from another client-provided identifier;
- the published quiz package and assignment attempt policy are resolved on the server;
- starting an attempt persists an immutable policy/content snapshot and a server-calculated deadline so resume cannot drift when the source assignment or quiz changes;
- submit and resume use server time, are safe under retries/concurrency, and expose the policy metadata required by teacher/student clients;
- migrations are append-only, tenant-scoped, and compatible with the existing runtime REST/GraphQL contracts.

## Baseline and constraints

- Primary implementation is `src/main/java/com/erg/ergspringboot/lms/internal/attempt/LmsAttemptUseCase.java` and the LMS runtime DTO/port layer.
- `Assignment` already owns attempt limit, duration and schedule; the active published package owns quiz version, package hash, grading mode and question payload.
- Existing clients/tests construct legacy request records, so additive DTO compatibility constructors are required where fields are added.
- Existing dirty worktree changes are preserved. No unrelated route, grading, cache or persistence rewrite is in scope.
- PostgreSQL migrations are append-only; no destructive migration or contract-file hand edit is allowed.

## Implementation map

1. Add a durable attempt policy/content snapshot and submit idempotency key to the `Attempt` aggregate, repository mapping, and the next PostgreSQL migration.
2. Add a typed `AttemptPolicySnapshotResponse` to the runtime response without removing legacy response fields.
3. Resolve the active package on the server at start, reject a supplied package id/hash mismatch, derive quiz version/grading/question count/grading mode from that package, and persist the snapshot only on first start.
4. Calculate duration/deadline from the assignment and server UTC time; resume the persisted snapshot unchanged; ignore client timestamps for expiry decisions.
5. Make submit replay-safe for already submitted attempts and for an explicit idempotency key, retaining the first server result and preventing concurrent duplicate grading.
6. Add focused unit/contract tests for identifier mismatch, server package derivation, immutable snapshot/resume, server-time expiry, concurrent/idempotent submit, and response metadata.
7. Run focused tests, `./mvnw.cmd -q -Dproject.build.directory=target-be056 verify`, contract/architecture ratchets, and update module/runtime documentation if the public shape changes.

## TDD / verification sequence

1. Add failing tests for each invariant before changing the implementation.
2. Implement the smallest production change and make focused tests green.
3. Run the full Maven verify in an isolated target directory so existing target output is not overwritten.
4. Confirm OpenAPI/GraphQL generation and architecture checks remain green; inspect the migration and response JSON shape.
5. Mark BE-056 complete in `D:/ERG/TASK/ERG_Task_Management_.xlsx` only after all checks pass, with links to this plan, migration, tests and verification output.

## Explicit non-goals

- Do not move the LMS module or rewrite the existing grading algorithm.
- Do not trust client `packageId`, `packageHash`, `quizVersion`, score or submitted timestamp as authority.
- Do not introduce localStorage/token changes here; those belong to the FE/BE session-contract tasks.
- Do not declare FE types synced until the published backend contract is regenerated through the repository contract protocol.
