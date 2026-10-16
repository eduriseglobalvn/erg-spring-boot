# BE-061: Immutable audit log, retention and controlled export

> Status: completed — BE-061 implementation, contract synchronization, focused audit tests, and full Maven verification are complete; evidence is recorded in the canonical workbook.

## Goal

Provide one tenant-scoped audit contract for sensitive business actions. The
audit stream must be append-only and tamper-evident at the application/database
boundary, keep actor/scope/action/resource/outcome/correlation metadata, redact
PII before persistence, enforce bounded retention, and expose permission-checked
query/export without coupling audit storage to application logs.

## Current state

- `SecurityEvent` and `UserActivity` are feature-specific stores with different
  shapes and no common export/retention contract.
- Modulith durable events already carry tenant, actor, aggregate, correlation
  and causation metadata, but there is no canonical audit projection.
- Existing authorization/user flows emit legacy audit records; they must remain
  compatible while the new stream becomes the canonical boundary.
- The persistence catalog keeps dormant packages out of the JPA unit, so the
  audit entity must be registered explicitly under its owning package.

## Design

1. Add a framework-neutral `audit` application boundary with typed commands,
   query/export DTOs, outcome and resource enums, and a redaction policy. No
   controller or service writes a JPA entity directly.
2. Add an append-only `audit_events` table with tenant/resource/action/actor,
   access scope, outcome, request/correlation/causation IDs, redacted JSON
   details, previous hash, event hash, timestamps and retention metadata.
   Database grants/RLS, immutable triggers and a unique idempotency key prevent
   cross-tenant reads, updates/deletes and duplicate writes.
3. Persist audit records in the command transaction through a small port. Use
   a deterministic canonical hash payload and a per-tenant previous-hash chain;
   verification is bounded and read-only. Never log secrets, tokens, OTPs or
   raw credential material.
4. Publish a Modulith audit event after commit with the same correlation and
   causation IDs. Consumers remain bounded and replayable; audit persistence is
   not an in-memory queue and does not block request threads beyond the local
   transaction.
5. Add tenant/scope/permission-checked REST query and export endpoints. Export
   is paged, bounded by time/row/byte limits, redacts again at the response
   boundary, emits an audit record for the export itself and never serves an
   expired job/result.
6. Add a scheduled, bounded retention job that deletes only records past the
   policy cutoff through a database-owned function/partition-safe path. Keep a
   legal-hold/retention override explicit and fail closed when policy is absent.
7. Keep `SecurityEvent`/`UserActivity` compatibility readers during migration;
   add adapters for the already-audited authorization/user actions and prove
   their new records carry tenant, actor and correlation identity.

## Files expected

- `src/main/java/com/erg/ergspringboot/audit/**`
- `src/main/java/com/erg/ergspringboot/controller/audit/**`
- audit entity/repository and persistence catalog registration
- `src/main/resources/db/migration/postgres/V93__immutable_audit_log.sql`
- REST/OpenAPI/performance inventory updates
- focused redaction/hash/RLS/retention/export/security tests

## Verification

- Unit tests cover redaction, canonical hashing, idempotent replay, tenant/scope
  denial, export bounds and retention cutoff.
- Migration contract tests reject destructive SQL, missing RLS/immutability and
  unbounded indexes.
- Spring context, route inventory, Modulith, security and query-count tests
  pass; OpenAPI/GraphQL checksum gates remain synchronized.
- `./mvnw.cmd -q -DcodexOwner=primary verify` passes with zero failures/errors.
