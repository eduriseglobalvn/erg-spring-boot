# BE-038 — LMS state policy and command/query ports

## Objective

Give assignment and attempt state one module-owned policy and narrow
use-case-specific query/command ports. Existing persisted strings and REST /
GraphQL responses must remain compatible while new recipient/state variants can
be added without editing every service and repository.

## Constraints

- No full CQRS database or universal repository abstraction.
- Keep existing state strings, idempotency and optimistic-lock behavior.
- Unknown persisted state is represented as an explicit compatibility value and
  never silently treated as a successful/completed state.
- Query ports return immutable projections; commands remain transaction-bound.
- Do not change generated contracts unless a parity test proves an additive field
  is required.

## TDD implementation slice

1. Inventory assignment/attempt status literals, repository methods and callers;
   add RED tests for status normalization, terminal/active classification,
   unknown-state fail-closed behavior and query projection shape.
2. Add `lms` state policy records/enums and narrow assignment/attempt query ports
   for the existing read paths.
3. Add repository adapters that map current JPA rows into projections; migrate
   one assignment read path and one attempt read path while retaining explicit
   compatibility constructors.
4. Keep command repositories for mutations, add source ratchets preventing the
   new read ports from leaking entities or becoming a generic repository.
5. Verify contract fixtures, query count/parity, architecture/Modulith, compile,
   OpenAPI and GraphQL checks.

## Not in this slice

- Offline idempotency/conflict protocol (BE-062).
- Quiz-kind registry (BE-042).
- Schema backfill or destructive state migration.
