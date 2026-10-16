# Learning Standards boundary

The `learningstandards` Spring Modulith module is the only owner of SCORM,
xAPI and LTI protocol state. REST adapters call `LearningStandardsOperations`
and never expose JPA entities or provider SDK types.

## Runtime contract

- SCORM 1.2 and 2004 package metadata is stored in a tenant-scoped lifecycle:
  `UPLOADING -> VALIDATING -> READY | QUARANTINED`.
- Package bytes are addressed by a tenant/key-safe object-storage port. The
  checked-in filesystem adapter is for local development only; production
  deployments replace it with R2/S3 without changing the application facade.
- LTI 1.3 launches accept only signed RS256 tokens whose issuer, audience,
  deployment, nonce, expiry, target host and public key match a tenant-owned
  registration. No remote JWKS or provider call runs on the request path.
- xAPI stores only bounded identifiers, URI fields, a payload digest and an
  actor hash. Accepted statements publish a post-commit Modulith event to the
  provider-neutral forwarding port; raw statement bodies never cross the
  event boundary.

## Invariants

Every command is tenant-scoped, permission checked again at the application
boundary, idempotency-keyed (or deterministically keyed for LTI/xAPI),
optimistically versioned where mutable, audited and published after commit.
The V96 migration enables and forces PostgreSQL RLS for all four tables,
provides bounded indexes and seeds `learning.standards.read/manage/launch`.

Invalid manifests, unsafe paths, oversized metadata, digest mismatches,
invalid signatures and target-host violations fail closed. A quarantined
package cannot be launched. The event catalog contains only IDs, versions,
hashes and correlation metadata.

## Extension rule

Add a provider under `learningstandards/internal` implementing one of the
module-owned ports (`LearningStandardsArtifactStorage`,
`LtiLaunchTokenVerifier` or `XapiStatementForwarder`). Do not add provider
SDK imports to controllers, entities or the public facade. Add conformance and
contract tests before enabling the adapter in a deployment profile.
