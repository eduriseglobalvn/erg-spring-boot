# BE-064: SaaS productization gate

Date: 2026-08-09  
Task: BE-064 — Thiết kế gate tenant isolation, entitlement, billing và custom domain  
Status: Activated

## Objective

Prepare a reversible architecture decision and executable test harness for a
future multi-tenant SaaS trigger without adding runtime tenancy, billing or
custom-domain complexity while ERG remains a single tenant.

## Guardrails

- No new runtime tenant, billing, entitlement or custom-domain service is
  enabled by this gate.
- Tenant identity remains derived from the authenticated principal and every
  proposed boundary must preserve RLS/permission fail-closed behavior.
- Any future rollout must use append-only migrations, versioned contracts,
  idempotent commands, bounded asynchronous work and an explicit rollback
  path.
- The harness uses deterministic in-memory fixtures; it does not contact a
  payment provider, DNS provider or external identity service.

## Deliverables

1. Threat model covering cross-tenant reads/writes, entitlement bypass,
   billing replay/refund races, custom-domain takeover and secret leakage.
2. Data ownership and boundary matrix for tenant, account, entitlement,
   subscription/invoice, domain-verification and audit records.
3. Contract/test harness that proves tenant isolation, entitlement decisions,
   idempotent billing lifecycle transitions and domain verification ownership.
4. Migration/rollout notes, observability requirements, cost/concurrency limits,
   estimate and explicit go/no-go criteria.

## Go/no-go criteria

Go only when a business trigger exists, a second tenant is funded for staging,
legal/billing ownership is assigned, provider SLAs and data-retention rules are
approved, and the harness passes isolation plus replay/concurrency tests. Until
then, keep the current single-tenant runtime unchanged.

## Verification

- Focused deterministic harness tests are green.
- Spring Modulith boundary and contract gates remain green.
- `mvnw.cmd verify` remains green with no new runtime tables or external calls.
