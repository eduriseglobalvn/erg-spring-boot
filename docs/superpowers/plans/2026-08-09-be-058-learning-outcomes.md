# BE-058: Learning path, certificate and competency domain

> Status: active — BE-058 implementation and verification are in progress.

## Goal

Introduce a tenant-scoped `learningoutcomes` Spring Modulith module for learning
paths, competency attainment and outcome certificates. The new module must own
its contracts and persistence, keep profile credentials (`users.certificates`)
separate, and avoid importing another module's entity or repository.

## Current-state findings

- The source has curriculum/content/LMS primitives but no canonical learning-path
  aggregate, competency taxonomy or outcome-certificate lifecycle.
- `model.users.Certificate` is a legacy profile credential without tenant or
  outcome semantics; it must remain compatible and must not be repurposed.
- Retry safety, scope checks and events are available as platform conventions but
  no learning-outcome command boundary currently consumes them.

## Design

1. Add an explicit `learningoutcomes` module with a thin `LearningOutcomesOperations`
   facade and module-owned request/response records. Controllers use only this
   facade and `UserAccessContext`.
2. Model tenant-owned aggregates/tables: `learning_paths`,
   `learning_path_nodes`, `competencies`, `competency_attainments`,
   `learning_path_completions` and `outcome_certificates`. References to
   content, learners and classes are IDs; no cross-module JPA associations are
   introduced. Completion evaluation records the required-node rule and an
   auditable evidence reference; competency attainment carries a bounded
   progress value and evidence trace.
3. Require an idempotency key on every mutation. Use a bounded command receipt
   table with a unique `(tenant_id, idempotency_key, operation)` key so retries
   replay the original aggregate response and conflicting payloads fail closed.
   Use JPA optimistic versions on mutable aggregates and reject stale writes.
4. Enforce tenant and managed education-unit/class scope in the application
   service. Add RLS and FORCE RLS for every new table; no client tenant field is
   trusted. Keep verification/query endpoints bounded and tenant scoped.
5. Publish versioned `LearningPathCreated`, `CompetencyAttained` and
   `OutcomeCertificateIssued` events after commit with IDs, versions, tenant,
   actor and correlation metadata only. Add event catalog and replay-safe
   listener contract tests; do not create synchronous cross-module calls.
6. Add typed module authorization annotations (`learning.outcomes.read`,
   `learning.outcomes.manage`, `learning.outcomes.issue_certificate`) and seed
   permissions in the append-only migration. Issue certificates with a unique
   non-guessable verification identifier and support authenticated verify and
   revoke flows; anonymous/public verification remains deferred until a
   privacy/anti-enumeration contract exists.
7. Expose the same bounded read and mutation use cases through REST and
   GraphQL, with the same tenant/scope/idempotency policy. Keep GraphQL
   resolvers thin and delegate to `LearningOutcomesOperations`.
8. Add focused domain, repository, migration, controller, concurrency,
   idempotency, completion, revoke/verify, scope and Modulith tests. Regenerate
   OpenAPI and GraphQL contracts from runtime, synchronize the performance
   route/field catalogs, sync FE generated types, and run all required gates.

## Implementation order

- Establish RED tests for idempotency conflict/replay, scope fail-closed,
  optimistic version and certificate/competency invariants.
- Add migration/entities/repositories and the application port/facade.
- Add REST adapters and typed policies; publish events and audit mutation
  outcomes through the existing audit boundary.
- Add architecture/catalog/contract evidence, regenerate OpenAPI and run the
  focused suite followed by full `mvnw verify`.

## Non-goals

- Do not change `users.Certificate` semantics or delete legacy profile data.
- Do not couple the module directly to LMS/LCMS persistence entities.
- Do not add an anonymous/public certificate lookup endpoint before
  anti-enumeration and revocation disclosure rules are approved.
