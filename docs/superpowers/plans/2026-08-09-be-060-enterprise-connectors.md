# BE-060 Enterprise Connectors Plan

Date: 2026-08-09
Task: BE-060 — Xây connector SIS, HRIS và SCIM provisioning
Owner: ERG-SPRING / Platform
Status: Activated

## Objective

Create a provider-neutral Spring Modulith boundary for SIS, HRIS and SCIM
provisioning. A connector must be replaceable without leaking provider DTOs,
credentials, HTTP clients or persistence entities into the application core.

## Scope

- define a versioned public `enterpriseconnectors` module and small operations
  facade;
- model provider registration, sync runs, mappings, dry-run diffs, retry and
  deprovision commands with tenant ownership and optimistic versioning;
- expose provider ports for roster/user/course reads and mutation/deprovision,
  plus a registry that selects a provider by tenant/configuration;
- implement one deterministic sandbox/reference adapter (no external network
  dependency in tests);
- store secrets server-side through a bounded secret-reference port; never
  persist or log raw tokens/credentials;
- make commands idempotent and audit every mutation, including retries and
  deprovision actions;
- publish stable REST contracts and module/event catalog entries only after
  authorization, validation, tenant-isolation and contract tests pass;
- add migration-safe PostgreSQL tables, tenant RLS, indexes, and repository
  ownership ratchets;
- add an extension guide describing provider registration, capability/version
  negotiation, failure classification and retry policy.

## Invariants

1. Tenant identity comes only from the authenticated security context.
2. Dry-run is read-only and produces a bounded deterministic diff.
3. Every mutating command requires an idempotency key and a bounded payload.
4. Retries use explicit bounded attempts/backoff; no unbounded threads or
   provider calls inside request transactions.
5. Deprovision is explicit, auditable and never inferred from a partial page.
6. Provider failures are normalized into stable error codes and do not expose
   secrets or upstream payloads.
7. Core ports contain scalar/immutable projections only; provider SDK classes,
   JPA entities and Spring infrastructure stay inside adapters.

## Implementation sequence

1. Inventory existing integrations and freeze the legacy behavior baseline.
2. Add module metadata, public DTOs/ports, typed authorization policies and
   route catalog entries.
3. Add migration, entities/repositories and tenant/RLS contract tests.
4. Implement registry, idempotency receipts, audit/event publication and the
   deterministic reference adapter.
5. Add REST endpoints for registration, dry-run, sync, retry and deprovision;
   keep controllers thin and enforce request bounds.
6. Add architecture/source ratchets preventing provider/persistence leakage.
7. Run focused unit/module/integration tests, OpenAPI/GraphQL gates and full
   Maven verification; record evidence in the task workbook.

## Acceptance evidence

- at least one sandbox/reference connector works end to end;
- roster/user/course mapping, dry-run diff, retry and deprovision contracts
  are covered by tests;
- secrets are referenced server-side only and absent from logs/events;
- duplicate commands replay safely and conflicting keys fail closed;
- tenant isolation/RLS and typed authorization tests pass;
- Modulith verification, route/performance catalog and published contracts are
  synchronized;
- full `./mvnw.cmd -q -DcodexOwner=primary verify` is green with no failures,
  errors or skipped tests.

## Non-goals

- no real SIS/HRIS vendor integration or cloud secret-provider deployment in
  this change set;
- no destructive legacy schema move or removal of existing integration code;
- no frontend/BFF changes beyond publishing the backend contract.
