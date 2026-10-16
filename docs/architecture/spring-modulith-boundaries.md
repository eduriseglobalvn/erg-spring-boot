# Spring Modulith boundary policy

The explicit application modules are `auth`, `authorization`, `calendar`, `crm`,
`curriculum`, `elearning`, `enterpriseconnectors`, `lcms`, `learningcontent`, `learningoutcomes`, `learningstandards`, `lms`, `notifications`, `organization`,
`reporting`, `school` and `users`. Legacy horizontal packages remain compatibility seams
until a vertical slice has a reviewed public API.

Rules for the migration:

- Controllers are adapters. They must call a module facade and must not access a
  repository or JPA entity directly.
- A module exposes only root-package facades, typed DTOs and documented events.
  Implementation packages will be placed below `internal/` and are not imported
  by another module.
- `ArchitectureRatchetTest` freezes the current horizontal-package ceilings and
  reviewed external imports of module internals. Baseline rows require an owner
  and expiry date and may only be removed, never expanded without architecture review.
- Synchronous facade calls are reserved for invariants and strong consistency.
  Durable module events are for notifications, projections and integrations.
- Event handlers are at-least-once and therefore idempotent. Event payloads carry
  identifiers, versions and correlation metadata, never JPA entities or large API
  response graphs.
- `ApplicationModules.verify()` runs in CI. Runtime verification stays disabled
  until the complete module graph has been migrated and reviewed.

The explicit detection strategy is deliberately scoped to tests so legacy
packages are not accidentally treated as business modules during migration.
