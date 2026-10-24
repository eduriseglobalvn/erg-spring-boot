# ERG Spring Repository Working Rules

## Worktree Safety

- Task planning and external task tracking in `D:/ERG/TASK/ERG_Task_Management_.xlsx` are optional unless the user explicitly requests them.
- Preserve dirty worktrees and unrelated user changes. Do not reset, discard, stage, commit, push, merge or deploy unless explicitly authorized.

## Canonical ownership

`erg-spring` is the canonical backend implementation and contract publisher for ERG. It owns persistence, authorization, tenant isolation, REST behavior, GraphQL behavior and the published contract sources. Frontend repositories must not redefine these contracts from handwritten DTO assumptions.

Published artifacts:

- REST: `contracts/openapi/erg-openapi.json` and `contracts/openapi/erg-openapi.sha256`.
- GraphQL: `src/main/resources/graphql/schema.graphqls` and `contracts/graphql/schema.graphqls.sha256`.

`erg-system` is the canonical operational frontend consumer. `erg` owns the public CMS application. `elearning` is secondary/legacy until `ERG-066` completes its duplicate-source audit.

## Contract change protocol

Follow `D:/ERG/erg-system/docs/architecture/cross-repo-contract-release-protocol.md`.

- Apply `add -> publish -> sync -> generate -> migrate -> deprecate -> observe -> remove` for breaking or semantic changes.
- Treat field removal/rename/type/nullability changes, enum semantic changes, authorization changes and endpoint moves as breaking.
- Never edit a published snapshot without regenerating and updating its checksum.
- Keep REST and GraphQL validation/error codes stable and correlated by request ID.
- Publish contracts only after authorization, validation, tenant-isolation and contract tests pass.
- Database migrations are append-only. Use a forward fix unless an approved operational runbook explicitly permits rollback.

Required contract gates:

```powershell
node scripts/export-openapi-contract.cjs --file contracts/openapi/erg-openapi.json --check
node scripts/check-graphql-schema.cjs
node --test scripts/openapi-contract-lib.test.cjs scripts/graphql-schema-contract-lib.test.cjs
```

Run focused Maven tests for changed behavior and `./mvnw.cmd verify` before completion. Contract/cache/security/load tasks require their task-specific evidence in addition to Maven success.

## Backend Engineering Rules

- Derive tenant identity from the authenticated security context; never trust a client-controlled tenant header/body field.
- Keep controllers thin and business behavior in application/domain services.
- Separate request/response DTOs from persistence entities.
- Validate retryable commands with idempotency and/or optimistic locking.
- Namespace cache keys by tenant and identity/scope; do not cache secrets, tokens or authorization decisions without an explicit bounded policy.
- Bound async concurrency, retries, scheduling overlap and metric-label cardinality.
- Never log tokens or unnecessary PII.
- Preserve stable error envelopes across REST and GraphQL.
