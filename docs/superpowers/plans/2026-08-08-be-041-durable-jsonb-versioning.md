# BE-041 — Durable JSONB versioning by bounded context

## Objective

Inventory the LMS JSONB payloads and make durable payload evolution explicit at
the module owner. Historical quiz/attempt/content documents must remain readable
without introducing a universal `JsonDocumentBase` or a framework that hides
the owner and failure policy.

## Constraints

- Preserve existing REST/GraphQL JSON shape and persisted data.
- No destructive migration and no blind rewrite of all JSONB columns.
- Durable payloads expose a bounded `schemaVersion`; transient JSONB remains
  explicitly exempt and documented.
- Unknown future versions fail safe (read-only/empty projection) and emit a
  bounded metric label; they must never be interpreted as the latest version.
- Keep module-specific migrators/readers; share only proven mechanics after two
  independent consumers exist.

## TDD implementation slice

1. Inventory JSONB entity fields, existing `JsonbSchemaMigrator` callers and
   migration fixtures; add RED tests for owner classification, current/N-1/N-2
   reads and future-version fail-safe behavior.
2. Define small module-owned versioned payload contracts for the durable LMS
   payloads that already pass through the quiz/runtime support migrator.
3. Update the migrator/readers to preserve unknown fields, support forward-only
   migrations, and expose bounded version/metric labels without mutating the
   database during reads.
4. Add architecture ratchets preventing raw unversioned durable `Map` reads in
   the migrated paths; document exemptions for transient/client/audit payloads.
5. Run focused migration/JSONB tests, native serialization smoke, Modulith,
   compile and contract checksum gates. Record any environment-only integration
   blockers without weakening tests.

## Not in this slice

- Destructive schema/backfill migration.
- Universal JSON schema registry or cross-module base entity.
- Offline conflict protocol (BE-062) or question-kind registry (BE-042).
