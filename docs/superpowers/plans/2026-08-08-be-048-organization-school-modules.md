# BE-048: explicit Organization and School modules

## Objective

Create explicit Modulith boundaries for organization hierarchy and school operations while keeping
existing contracts stable. The first vertical slice moves education-unit reads behind an organization
public facade and ratchets school public facades; persistence is migrated incrementally, not by a
destructive package move.

## Constraints

- Tenant, RLS, cache and REST/GraphQL payload behavior remain unchanged.
- Public packages expose operations/projections only; JPA entities and repositories stay internal.
- Existing horizontal services remain deprecated compatibility adapters until each owner slice is
  migrated and its callers are moved.
- No mass rename or schema rewrite.

## TDD slice

1. Add explicit `organization` and `school` module descriptors and verify the dependency graph.
2. Move education-unit read mapping into `OrganizationEducationUnitQuery`; expose it through
   `OrganizationOperations`; update the legacy controller to call the facade.
3. Keep `SchoolOperations`/`SchoolQueryOperations` as the public school boundary and add source
   ratchets preventing persistence imports in those classes.
4. Run organization/school service tests, architecture/Modulith/public API tests, compile and
   `mvnw verify`.

## Not in scope

No destructive entity/repository package move, migration rename, RLS change, or new school CRUD
surface beyond the existing command/query contract.
