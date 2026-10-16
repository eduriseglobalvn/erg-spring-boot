# BE-043 — Explicit tenant context for async and event replay

## Objective

Make tenant propagation explicit and leak-safe across fan-out, scheduled work and
durable Modulith listeners. Request authentication must never be captured into a
replay task; durable events reconstruct tenant/system context from immutable
metadata.

## Constraints

- Preserve bounded execution lanes and existing retry/idempotency behavior.
- `TenantContext.Scope` must restore the previous context on every close,
  including exceptional and nested execution.
- No executor/new thread/@Async outside `platform.concurrency`.
- Scheduled jobs must declare a tenant or maintenance scope; absent tenant fails
  closed rather than inheriting an ambient request.

## TDD implementation slice

1. Inventory `TenantContext`, task decorators, listeners, schedulers and executor
   creation; add RED tests for nested restore, cleanup, cross-tenant rejection
   and replay without authentication.
2. Add an AutoCloseable tenant carrier/scope and one platform task decorator that
   captures only tenant + correlation metadata (never principal/token).
3. Migrate async/event/scheduled call sites to the carrier and reconstruct
   tenant context from immutable event metadata before invoking module facades.
4. Add source ratchets banning ad-hoc executors and ambient auth capture outside
   concurrency/events packages; add bounded queue/max-in-flight assertions.
5. Run focused concurrency/event tests, Modulith scenarios, compile and contract
   gates; record external integration blockers honestly.

## Not in this slice

- New notification providers (BE-044).
- Offline conflict contract (BE-062).
