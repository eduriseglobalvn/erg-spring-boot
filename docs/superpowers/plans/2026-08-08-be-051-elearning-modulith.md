# BE-051: ELEARNING Modulith boundary

## Goal and verified dependency

Make the ELEARNING production slice explicit and typed for catalog, discussion,
announcement and student dashboard consumption, without allowing
`CatalogService`/process-local prototypes to masquerade as production data.
Workbook dependencies `BE-011`, `BE-033`, `BE-034`, `BE-035`, `BE-036`, `BE-039`
and `BE-047` are complete.

## Baseline and invariants

- Repository `D:/ERG/erg-spring` is dirty. Scope is limited to the `elearning`
  module/facades/adapters/controllers, relevant source ratchets/tests/docs,
  this plan and workbook evidence.
- Preserve existing REST/GraphQL operation names, response shapes, tenant and
  permission checks, query cache scope and offline read semantics.
- Production paths must use persistent stores/query ports. Any process-local
  `ConcurrentHashMap` or prototype service is profile-gated and fail-closed.
- Side effects publish after commit; synchronous calls are for invariants only.
  No new generic workflow abstraction or destructive schema move.
- Public module APIs expose DTOs/ports only; controllers must not import JPA
  entities/repositories or `CatalogService` directly.

## Exact implementation scope

1. Add an explicit `elearning` Modulith descriptor and public facade(s) for the
   existing catalog, discussion, announcement and dashboard slices, with an
   allow-list for approved organization/curriculum dependencies only.
2. Route the affected REST/GraphQL controllers through the facade/ports and
   keep typed DTOs at the transport boundary.
3. Mark any remaining horizontal service usage as deprecated compatibility
   seams; profile-gate or remove process-local production stubs and add a source
   ratchet for persistent production stores.
4. Add module catalog/dependency/boundary documentation and architecture tests
   for public API direction, event policy, tenant scope and prototype exposure.
5. Run focused ELEARNING/catalog/dashboard/engagement tests and the full Maven
   verification gate.

## TDD and verification

```powershell
./mvnw.cmd -q '-Dproject.build.directory=target-be051' '-Dtest=*Elearning*,*Catalog*,*Engagement*,ArchitectureRatchetTest,ModulithDocumentationTest,ModulithVerificationTest,ModulePublicApiContractTest,ProductionCapabilityStubContractTest' test
./mvnw.cmd -q '-Dproject.build.directory=target-be051' verify
```

If a wildcard test selector is unsupported, use the exact discovered classes
from `rg` and record them in workbook evidence. Do not edit generated contracts
unless a route/operation change is unavoidable.

## Definition of done

- `ApplicationModules.verify()` recognizes `elearning` and all focused module
  tests pass.
- Controllers use public facade/ports; public package exposes no persistence
  type; production cannot construct process-local stubs.
- Existing REST/GraphQL contracts, tenant/policy checks and persistent read
  behavior remain green; full `mvnw verify` exits 0.
- Workbook BE-051 is complete with evidence and exact paths.

## Non-goals and rollback

No redesign of the student UI, schema rewrite, new external provider, generic
workflow engine or FE changes. Roll back only BE-051 files if a contract
regression appears; preserve unrelated dirty worktree edits.
