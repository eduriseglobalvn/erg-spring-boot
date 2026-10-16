# BE-062 — Offline sync idempotency, version and conflict contract

## Objective

Publish one server-authoritative mutation protocol for offline-capable LMS writes. The protocol must
bind every replay to tenant/account scope, preserve an operation id and idempotency key, expose a
server clock/version, and return a typed conflict when a stale version or reused key is detected.

## Scope

- Add an append-only migration/table for replay receipts with tenant/account/operation/entity/key,
  payload hash, status, result version and server timestamps; enforce uniqueness by scoped key.
- Add typed request/ack/conflict records and a JDBC service that atomically reserves, replays and
  completes receipts. A different payload for the same scoped key is rejected fail-closed.
- Apply the protocol to the existing quiz attempt start/submit path, attendance upsert,
  gradebook score upsert, assignment create, quiz draft autosave and learning-content metadata
  mutation boundaries without weakening their current tenant/scope or optimistic-lock checks.
- Add GraphQL input fields where the existing academic-record mutations are the public contract;
  keep source-compatible constructors for legacy tests/callers.
- Preserve server time as the authority, bound payload size/retention, and never persist bearer
  tokens or raw sensitive payloads in replay receipts.

## Test-first sequence

1. Write protocol tests for normalization, payload hashing, reserve/replay, key collision,
   pending receipt, server-time and conflict response semantics.
2. Write migration/constraint tests and contract tests for the six mutation boundaries.
3. Implement the protocol service and migration, then integrate one boundary at a time while
   keeping existing idempotency/version behavior backward compatible.
4. Regenerate runtime OpenAPI/GraphQL contracts and FE generated types; run focused suites and
   `./mvnw.cmd -q -DforkCount=0 -DreuseForks=false verify`.

## Acceptance

- Replaying the same operation for the same tenant/account returns the first acknowledgement and
  creates no second side effect; a changed payload returns `409 IDEMPOTENCY_CONFLICT`.
- Stale `expectedVersion` returns typed `409 STALE_WRITE` with current server version/entity,
  while a missing expected version is only accepted on explicitly legacy-compatible flows.
- Batch/replay receipts are isolated by tenant and account, server time is exposed in UTC, and
  retention/index constraints are documented.
- Quiz, attendance, gradebook, assignment, draft and resource metadata contract tests pass;
  OpenAPI/GraphQL locks and FE generated types remain synchronized.

## Non-goals

No client IndexedDB/outbox implementation, cross-domain cookie changes, or production infrastructure
cutover is included here; those remain FE-106/ERG-092 and infrastructure-gated tasks.
