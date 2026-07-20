# Spring Modulith boundary policy

The first migrated application module is `com.erg.ergspringboot.calendar`.
Its package is explicitly annotated with `@ApplicationModule`; all other legacy
horizontal packages remain outside the Modulith graph until they are migrated.

Rules for the migration:

- Controllers are adapters. They must call a module facade and must not access a
  repository or JPA entity directly.
- A module exposes only root-package facades, typed DTOs and documented events.
  Implementation packages will be placed below `internal/` and are not imported
  by another module.
- Synchronous facade calls are reserved for invariants and strong consistency.
  Durable module events are for notifications, projections and integrations.
- Event handlers are at-least-once and therefore idempotent. Event payloads carry
  identifiers, versions and correlation metadata, never JPA entities or large API
  response graphs.
- `ApplicationModules.verify()` runs in CI. Runtime verification stays disabled
  until the complete module graph has been migrated and reviewed.

The explicit detection strategy is deliberately scoped to tests so legacy
packages are not accidentally treated as business modules during migration.
