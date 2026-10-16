# BE-067 — publish learner assignment-to-quiz identity

> Status: completed — BE-067 published and verified 2026-08-09; opened from the ERG-100 contract audit.

## Goal

Expose the already persisted assignment-to-quiz relationship in the learner dashboard GraphQL contract so the elearning frontend can open the exact assigned assessment without treating an assignment id as a quiz id.

## Scope and safety boundary

- Change only the learner dashboard response DTO, GraphQL schema/module, resolver mapping, contract tests and generated/published contract artifacts owned by `erg-spring`.
- `quizId` is derived from the tenant-scoped `StudentDashboardAssignmentProjection`; never accept it from the learner request and never widen the dashboard authorization scope.
- Keep the field nullable for legacy assignments that have no quiz relationship. A missing value is a valid unavailable state, not a fallback to a sample quiz.
- Do not change assignment ownership, attempt policy, due-date semantics or cache scope.
- Follow `add -> publish -> sync -> generate -> migrate` for the additive contract field. FE will sync the published GraphQL contract only after BE tests and contract checks pass.

## TDD sequence

1. Add RED response/resolver tests proving a tenant-scoped assignment card returns its persisted `quizId`, and a quiz-less assignment returns null.
2. Add the nullable `quizId` field to the response record and GraphQL schema/module; map `projection.quizId()` directly.
3. Run focused dashboard/GraphQL tests and published-contract checks.
4. Sync `erg-system` GraphQL contract and generated types, then update ERG-100 FE mapper/tests to consume the field.
5. Run BE verify and FE contract/type/lint/full-test/build/budget gates.

## Definition of done

- `StudentDashboardAssignmentCard.quizId` is present in the published GraphQL schema and generated contract.
- Resolver tests cover populated and null quiz identity, tenant/self authorization remains unchanged, and no fixture fallback is introduced.
- BE contract checks and focused/full verification pass.
- FE consumes `quizId` only when returned by the backend; missing values still render an explicit unavailable state.

## Rollback

Revert the additive DTO/schema/mapping and contract artifacts together; do not remove or rename existing fields.
