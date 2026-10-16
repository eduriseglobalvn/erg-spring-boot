# BE-070 — Portable exam package and update-feed contract

> Status: completed — BE-070 is the only dependency-ready backend task after the
> ERG-120 Tauri foundation. This plan is contract-first and does not claim
> that a portable package can be trusted merely because it is a signed file.

## Goal

Publish a versioned backend contract for Teacher-authorized export and
Student-facing discovery/download of a portable exam package. The package
must be tenant-scoped, integrity-protected and explicit about the compatible
desktop runtime version, while keeping answer validation and release policy
server-owned wherever the package mode permits.

## Scope boundary

- Inspect existing quiz/learning-content, tenant, authorization, storage and
  OpenAPI conventions before choosing controller/service/package names.
- Add request/response DTOs, application services and contract tests only where
  the existing backend has a stable extension point.
- Publish a manifest/update-feed shape with immutable package id, semantic
  package version, exam/content revision, tenant scope, platform/architecture,
  minimum desktop version, digest, signature metadata, expiry/revocation
  state and download URL metadata.
- Enforce authorization and tenant identity from the authenticated security
  context. Client tenant headers/body values are never authoritative.
- Validate package state transitions, idempotent export requests and bounded
  download/update metadata. Use stable error envelopes and request IDs.
- Export OpenAPI and checksum artifacts through the repository's contract
  protocol; do not hand-edit generated FE types.

## Explicit non-goals

- No signing private key in the repository, database or API response.
- No unsigned executable, arbitrary archive, answer key or raw token returned
  by a new endpoint.
- No client-side claim that a portable file prevents copying, debugging or
  OS-level tampering. ERG-122 defines package runtime verification; ERG-123
  defines signed `.exe`/`.dmg` release delivery and updater feeds.
- No online kiosk/session behavior; that is BE-069 and remains blocked by the
  BFF/session contract dependency.

## TDD sequence

1. Read existing security, tenant, quiz, storage, OpenAPI and error-envelope
   patterns; identify the smallest compatible package aggregate/service.
2. Write failing unit tests for manifest validation, signature/digest format,
   version ordering, expiry/revocation, tenant isolation, authorization,
   idempotency and stable error codes.
3. Add the domain/application DTOs and service behavior; keep controllers thin
   and avoid persistence changes unless the audited extension point requires a
   forward-only migration.
4. Add REST contract tests and export OpenAPI. Verify every response omits
   private signing material, access/refresh tokens and cross-tenant URLs.
5. Run focused Maven tests, OpenAPI/GraphQL contract checks, then the required
   `./mvnw.cmd verify` gate. Sync the FE vendored contract only after the BE
   snapshot and checksum are published.
6. Update the workbook with exact files, evidence and any follow-up blocked by
   ERG-122/ERG-123.

## Security invariants

- Package identifiers, tenant IDs and download URLs are not authorization
  proofs; the authenticated principal and server-side policy are.
- Digests are computed over canonical bytes; signatures use an allowlisted
  algorithm/key id and are verified before a package is advertised as ready.
- Revoked/expired packages never appear in the active update feed.
- Export and publish commands are idempotent or guarded by optimistic
  concurrency so retries cannot create conflicting releases.
- Logs contain package ids/request ids, never tokens, signing keys, answer
  keys or unnecessary learner PII.

## Completion gate

BE-070 is complete only when the published contract, authorization/tenant
tests, validation/error tests, OpenAPI checksum and Maven verification all
pass. The FE portable runtime remains blocked until it consumes this pinned
contract in ERG-122/ERG-123.

## Completion evidence — 2026-08-09

- Added tenant-scoped package metadata, expiry/revocation/channel/runtime
  fields and optimistic `lock_version` migration `V99__portable_package_update_contract.sql`.
- Added typed manifest, bounded update-feed, HTTPS download redirect and
  idempotent revoke endpoints in `LcmsQuizBundleController`.
- Added recursive answer-key/credential redaction, SHA-256/Ed25519 readiness
  checks, asset node/byte bounds, rollback selection and stable ETags in
  `PortableQuizBundleService`.
- Added Ed25519 signature verification, manifest/update/revocation tests and
  route/performance inventory entries.
- Published and checked `contracts/openapi/erg-openapi.json` plus checksum;
  GraphQL checksum and contract-library checks pass.
- Focused Maven tests: 16 tests pass. Full `./mvnw.cmd verify` passes with the
  real PostgreSQL/Testcontainers integration suite. FE vendored OpenAPI,
  lock and generated types were synchronized; `bun run verify:api-contract`
  passes.
