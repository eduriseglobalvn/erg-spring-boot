# BE-059: SCORM, xAPI and LTI standards boundary

> Status: active — dependency-ready implementation starts 2026-08-09.

## Goal

Introduce a tenant-scoped `learningstandards` Spring Modulith module for external
learning standards without leaking SCORM/xAPI/LTI protocol details into the core
learning-content and learning-outcomes domains. The module must be adapter-first,
Native-image safe, retry-safe and secure by default.

## Current-state findings to verify

- Inventory existing package/object-storage/client code for SCORM, xAPI, LTI,
  launch, package upload and external statement forwarding before adding a second
  implementation.
- Confirm whether any legacy tables/routes or generic integration services already
  persist package metadata, launch secrets or statements. Existing behavior must
  remain compatible or receive an explicit adapter/bridge.
- Treat `users.Certificate`, learning paths and content lifecycle as separate
  bounded contexts. Standards artifacts reference scalar IDs and immutable
  snapshots; they do not create cross-module JPA associations.

## Design constraints

1. Add an explicit `learningstandards` module with a thin
   `LearningStandardsOperations` facade, module-owned request/response records and
   named interfaces for authorization. Controllers and GraphQL resolvers depend on
   the facade only; protocol libraries stay behind ports.
2. Define protocol-neutral ports first:
   - package validation/storage/launch adapter for SCORM 1.2/2004;
   - signed LTI 1.3 launch adapter with key/JWKS provider;
   - xAPI statement validation, deduplication and forwarding adapter.
   Ports must be deterministic interfaces with no reflection-only types so JVM and
   GraalVM Native builds can discover every implementation at build time.
3. Persist only bounded, tenant-scoped metadata: package/version/artifact status,
   launch registration, xAPI statement receipt and idempotency receipt. Store
   binaries and secrets through existing server-side storage/secret ports; never
   persist bearer tokens, client secrets, raw JWTs or unbounded request bodies.
4. Enforce append-only migrations, tenant FORCE RLS, scoped authorization and
   optimistic versions. Package lifecycle is `UPLOADING -> VALIDATING -> READY`
   or `QUARANTINED`; invalid packages are never launchable. LTI registrations are
   active only after issuer/audience/key validation. xAPI statements are accepted
   once per `(tenant, statement_id, payload_hash)` and replay the prior result.
5. Make commands idempotent with bounded receipts and payload hashes. Conflicting
   retries fail with the stable `IDEMPOTENCY_CONFLICT` envelope; duplicate xAPI
   statements and launch handshakes replay without side effects. Bound package
   size, statement size, batch size, retry count and metric-label cardinality.
6. Validate security at the boundary: SSRF-safe launch URLs, allowlisted schemes and
   hosts, strict JWT/JWS algorithm and key validation, issuer/audience/nonce checks,
   encrypted server-side secret references, redacted logs, and no tenant supplied
   by request body/header. All external callbacks use timeouts and bounded retries.
7. Publish post-commit Modulith events with IDs, tenant, aggregate/version,
   correlation and causation metadata only (`LearningPackageReady`,
   `LearningPackageQuarantined`, `LtiLaunchAccepted`, `XapiStatementAccepted`).
   No synchronous cross-module persistence call is allowed.
8. Expose bounded REST and GraphQL operations only where a stable product contract
   exists: package create/upload/validate/launch, LTI launch registration and xAPI
   statement ingestion/status. Keep public launch routes explicitly reviewed; all
   admin/learner reads and mutations use typed policies. Anonymous statement query
   and package enumeration are not allowed.

## Implementation order

- Inventory current integrations, storage and migrations; record compatibility gaps.
- Write RED tests for package quarantine, LTI signature/nonce/issuer checks,
  xAPI idempotency/conflict, tenant isolation, SSRF rejection and size limits.
- Add module package metadata, protocol-neutral ports, bounded domain records,
  repositories, migration/RLS and idempotency receipts.
- Implement storage/validation/launch adapters behind ports, typed authorization,
  post-commit events and audit calls; keep controllers thin.
- Add REST/GraphQL adapters, runtime OpenAPI/GraphQL contract publication and
  performance/module/event/JPA catalogs. Add Native reflection/proxy checks where
  the build requires them.
- Run focused security/concurrency/contract tests, GraalVM Native compile smoke
  (or document the exact environment limitation), then full Maven verification.

## Non-goals

- Do not embed SCORM/xAPI/LTI protocol state into `learningcontent`, `lms` or
  `learningoutcomes` entities.
- Do not expose public package listings, anonymous certificate/statement lookup or
  arbitrary remote URL fetches.
- Do not store secrets/tokens/raw statements beyond the bounded retention policy.
- Do not replace a discovered legacy integration until a characterization test and
  compatibility adapter prove equivalent behavior.

## Acceptance evidence

- Adapter/module boundary and Modulith verification pass; no forbidden entity or
  repository imports.
- Migration/RLS/idempotency/security/concurrency tests pass, including quarantine,
  signature and SSRF cases.
- REST/GraphQL runtime contracts, checksums and catalogs are synchronized.
- JVM focused/full Maven gates pass; Native smoke or an explicit reproducible
  limitation is recorded; no unbounded threads, retries, queues or payloads.

## Implementation evidence (2026-08-09)

- Added the explicit `learningstandards` module, typed REST facade/policies and
  V96 append-only migration with four tenant FORCE-RLS tables, bounded indexes
  and read/manage/launch permissions.
- Added pure SCORM manifest/path validation, native-friendly RS256 LTI
  verification, tenant-safe local artifact adapter, xAPI digest/actor-hash
  policy and post-commit forwarding port/consumer.
- Added idempotent application service commands, audit/event metadata, runtime
  OpenAPI route inventory (544 routes) and module/JPA/event documentation.
- Focused validator, storage, migration, authorization, service and forwarding
  tests are green. Remaining gate: rerun the complete focused suite and full
  Maven verify, then synchronize BE-059 completion evidence in the workbook.
