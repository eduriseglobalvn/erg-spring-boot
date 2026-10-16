# BE-035 — Dependency direction closure for explicit Modulith modules

## Objective

Close the remaining dependency-direction gap in the eight explicit Spring
Modulith modules without changing REST/GraphQL behavior, authorization policy,
tenant semantics, or the compatibility adapters that are still required during
the strangler migration.

## Scope

- Treat `src/main/java/com/erg/ergspringboot/{auth,authorization,calendar,curriculum,lcms,lms,reporting,users}` as explicit module boundaries.
- Make the remaining LCMS compatibility seam explicit and fail-closed: module
  public packages may expose ports/facades/events and approved adapters, but
  controllers and other modules must not reach `internal`, repository, entity,
  or arbitrary legacy service packages.
- Add a source-level architecture ratchet for module public-package imports so
  new violations fail before they are hidden by Spring Modulith's explicit
  detection strategy.
- Keep the checked-in catalog/matrix/graph truthful and record any deliberate
  compatibility exception with owner and expiry.

## Non-goals

- No broad rename of the legacy `service.quiz` or `service.lcms` packages in
  this slice.
- No API, schema, authorization, tenant, or database behavior change.
- No production deployment or infrastructure work; BE-053 remains blocked on
  BE-027 for staging OIDC/proxy authority.

## TDD implementation sequence

1. Run the current architecture suite and inventory imports from the eight
   module public packages, controllers, and cross-module callers.
2. Add a red source-level test that rejects public module imports of another
   module's internals, persistence types, and unapproved legacy service
   packages; encode the small, named LCMS compatibility allowlist.
3. Make the minimum source/doc changes required for the test to pass, without
   widening the compatibility allowlist.
4. Split any remaining public port that is carrying persistence types for
   compatibility into a separately named deprecated compatibility port; keep
   cache annotations on legacy service adapters so operational behavior and
   observability do not regress.
5. Run focused architecture tests, compile, and the relevant contract/security
   regression suites.
6. Attempt the full Maven verify and classify infrastructure-only failures
   instead of hiding them behind a green focused suite.
7. Update `Tất cả Task` row `BE-035` with exact evidence, progress, remaining
   work, and any environment blocker.

## Acceptance evidence

- Source ratchet is red for a newly introduced forbidden import and green for
  the current reviewed compatibility seam.
- `ArchitectureRatchetTest`, `DependencyDirectionArchTest`,
  `ModulePublicApiContractTest`, `ModulithVerificationTest`, and
  `ModulithDocumentationTest` pass.
- `mvnw.cmd -DskipTests compile` passes.
- `BusinessCacheInvalidationIntegrationTest` passes after the compatibility
  split; cache keys remain tenant- and user-scoped.
- No contract snapshot is edited by hand; if generated contracts change, they
  are exported from the running application and re-locked.

## Current status (2026-08-08)

The canonical `CalendarReadPort` is now DTO-only. Entity projection helpers
live behind `CalendarReadCompatibilityPort`, which is explicitly deprecated;
the old `CalendarReadService` still delegates through it and preserves the
three `calendarWorkspace` cache annotations. The local slice is verified and
BE-035 remains in progress until the compatibility ports are migrated away and
the allowlists can be removed. Full verify still needs Docker/Testcontainers
and the existing PostgreSQL/JSONB integration fixtures.
