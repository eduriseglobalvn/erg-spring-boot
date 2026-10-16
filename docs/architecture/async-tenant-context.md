# Async tenant context and event replay

`TenantContext` now exposes an `AutoCloseable` `Scope` and a small `Carrier`.
Scopes restore nested/previous state exactly once, including exceptions. Carriers
contain only tenant/user/portal metadata needed for RLS and diagnostics; they do
not contain a Spring `SecurityContext`, principal object, bearer token or
authorization decision.

`ErgReadTaskExecutor` propagates the tenant carrier and request correlation
counter only. Worker threads begin with no authentication context, so a read
cannot accidentally authorize as the submitting request. `TransactionalOutboxWorker`
uses explicit maintenance scope for leasing and reconstructs a tenant scope from
the immutable outbox event for dispatch.

Durable Modulith events must carry `ModuleEventMetadata` (event version, tenant,
actor, aggregate/correlation/causation IDs). Replay code reconstructs tenant and
system actor context from that metadata; it never inherits an ambient request.
Scheduled maintenance uses the explicit `TenantContext.maintenance()` scope.

All new fan-out/scheduled/replay code must use the platform executor and
try-with-resources scope. Ad-hoc executors, `new Thread`, common-pool work and
authentication propagation are prohibited outside the platform concurrency
boundary.
