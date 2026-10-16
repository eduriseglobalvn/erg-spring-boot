# ADR-064 — SaaS productization gate

Date: 2026-08-09  
Status: Proposed gate; no runtime SaaS features enabled  
Owner: ERG-SPRING / Platform  
Related task: BE-064

## Decision

Keep ERG's current single-tenant runtime unchanged until a concrete business
trigger exists. Prepare only a deterministic, provider-free decision gate and
test harness for a future second tenant. If the gate is approved, introduce
each capability behind its own bounded module and versioned contract; do not
create a generic `SaaSService`, a global feature-flag framework or a shared
billing/domain mega-table.

The authenticated principal remains the only source of tenant identity. A
request header or body tenant value may be accepted as a consistency hint but
can never select authorization scope. PostgreSQL RLS and application policy
checks remain defense in depth, and any new tenant-owned table must ship with
the same migration-time RLS proof before it is used.

## Current-state inventory

- `AuthenticatedTenant` and `TenantContext` fail closed when an authenticated
  tenant is missing; context propagation deliberately carries no token or
  `SecurityContext`.
- Existing tenant-scoped records, permission grants, workspace capabilities,
  idempotency receipts, Modulith events and audit journals are the source of
  truth for current runtime behavior.
- Commercial entitlements, subscriptions/invoices, payment-provider webhooks,
  custom-domain registry and TLS/DNS automation do not exist in the runtime.
- Therefore BE-064 adds no Flyway migration, controller, scheduled job,
  network client or cache namespace. The harness under `src/test` models only
  the invariants that must be true before implementation begins.

## Threat model

| Threat | Failure mode | Required control and evidence |
| --- | --- | --- |
| Cross-tenant read/write | An ID, cache key or async job from tenant A is used under tenant B. | Principal-derived tenant, mandatory `(tenant_id, id)` lookups, RLS `USING/CHECK`, tenant-namespaced cache/idempotency keys, isolation harness. |
| Entitlement bypass | A user receives a feature through a role, stale cache or client-supplied plan. | Server-side entitlement decision `(tenant, subject, feature, version, expiry)`, deny-by-default, bounded cache invalidation, audit decision reason. |
| Billing replay/race | Provider webhook is retried, delivered out of order or applied concurrently. | Durable inbox keyed by provider event id + tenant, payload hash conflict detection, optimistic version, monotonic state machine, outbox after commit, replay/concurrency tests. |
| Billing data exposure | Card/PAN, provider token or raw webhook payload enters logs/events. | Store provider reference only, encrypt/secret-manager references, redact at boundary, no payment data in domain events, retention/deletion policy. |
| Custom-domain takeover | A domain is claimed by two tenants or verification is replayed after transfer. | Normalize IDN/ASCII host, unique ownership per domain, short-lived hashed challenge, DNS/HTTPS proof, ownership epoch and audit trail. |
| Host-header poisoning | Arbitrary `Host` chooses a tenant or redirect. | Explicit verified-domain lookup, allowlist, canonical HTTPS redirect, no tenant selection from unverified host. |
| Async amplification | Renewal/entitlement/domain jobs create unbounded threads or retries. | Durable queue, bounded concurrency, retry budget/DLQ, idempotency and metric-cardinality limits. |
| Migration bypass | New table is owned by a migration role and bypasses RLS. | Non-owner integration role, forced RLS migration test, append-only forward migration and rollback runbook. |

## Proposed ownership and boundary matrix

| Capability | Future owner | Tenant key / aggregate | Source of truth | Allowed dependencies | Explicit non-goals |
| --- | --- | --- | --- | --- | --- |
| Tenant lifecycle | `platform/tenant` | `tenant` | tenant registry + audit | security principal, RLS, audit | no runtime provisioning in this gate |
| Entitlement/licensing | `platform/entitlements` | `tenant + subject + feature` | versioned entitlement grant | authorization policy, cache, audit | never infer from UI or plan name |
| Billing lifecycle | `platform/billing` | `tenant + subscription/invoice` | provider reference + inbox/outbox | entitlement port, audit, bounded scheduler | no PCI data, no provider SDK in domain |
| Custom domain | `platform/domains` | normalized domain + ownership epoch | registry + verification record | tenant registry, secret reference, audit | no DNS/TLS client in command transaction |
| Product events | `platform/events` | tenant-scoped event metadata | Modulith durable publication | audit, bounded executor | no cross-tenant fan-out |

Each future module owns its commands, policies, persistence adapter and event
contracts. Other modules depend on a narrow inbound port, never on its JPA
entity or provider SDK. Public contracts use additive versioning and follow
`add -> publish -> sync -> generate -> migrate -> deprecate -> observe ->
remove`.

## State and idempotency rules

### Entitlements

Evaluate `tenantId`, authenticated subject, feature key, grant version and
expiry together. A missing grant, mismatched tenant or expired grant is a
deny. Cache keys must include tenant, subject, feature and grant version; an
authorization decision must not be cached indefinitely.

### Billing

The first implementation must model a small state machine, not a boolean
`paid` flag. A safe baseline is:

`PENDING -> ACTIVE -> PAST_DUE -> ACTIVE | CANCELED`, with `CANCELED`
terminal unless a new subscription is created. Every provider event carries a
tenant, provider event id, payload hash and observed version. Replaying the
same idempotency key returns the original result; reusing the key with a
different payload is a conflict. Out-of-order events are recorded and do not
move the aggregate backwards without an explicit reconciliation command.

### Custom domains

Normalize a hostname before ownership checks, reject empty/wildcard/control
characters, and store only a hash of the verification challenge. Verification
must check the current owner and ownership epoch; a stale challenge cannot
re-claim a transferred domain. Certificate issuance and DNS writes belong to
separate, retry-bounded adapters after the database transaction commits.

## Rollout, observability and cost guardrails

1. **Trigger:** approve a second-tenant business case, data residency and
   legal/billing ownership; capture provider SLAs and deletion obligations.
2. **Design:** publish the boundary matrix and threat model; add contract and
   non-owner RLS tests before any table is created.
3. **Shadow:** calculate entitlement/billing/domain decisions without changing
   authorization or routing; compare decisions by tenant and request id.
4. **Canary:** one staging tenant and one verified domain; bounded worker pool,
   retry budget and DLQ; monitor isolation-deny, replay-conflict, webhook lag,
   domain-verification and provider-error metrics.
5. **Enforce:** enable one capability at a time behind an explicit, expiring
   rollout decision; retain a read-only fallback and a forward-fix runbook.

Initial guardrails for any future implementation: max 16 concurrent provider
operations per process, max 3 automatic retries with exponential backoff,
bounded inbox/outbox retention, no unbounded metric labels, and p95 command
latency targets measured separately from provider latency.

## Go / no-go checklist

**Go** only when all items are true:

- a funded second tenant and staging data set exist;
- data ownership, support/on-call and legal/billing responsibilities are
  assigned;
- provider references, retention, residency and deletion rules are approved;
- non-owner RLS, cross-tenant cache, entitlement expiry, billing replay/race
  and domain takeover tests pass;
- migration, observability, cost ceiling and forward rollback/fix runbooks are
  reviewed.

**No-go** while ERG is single-tenant, provider ownership is unclear, or the
harness has any cross-tenant read/write, replay-conflict or stale-domain proof
failure. The no-go result is a valid completion of this gate and avoids
runtime complexity before it is justified.
