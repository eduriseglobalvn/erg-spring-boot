# BE-042 — Fail-closed question-kind registry

## Objective

Give authoring, compile, validation and grading one immutable question-kind
descriptor registry. A new kind must be present in every required phase or the
application/test gate fails; unknown auto-gradable kinds must never receive a
default grade.

## Constraints

- Preserve behavior for all currently published kinds and historical payloads.
- Keep the matching-question UI/backend semantics unchanged.
- No runtime plugin JARs, dynamic class loading, scripting or reflection-based
  registry. Spring handlers are discovered at build/startup time and validated
  by immutable descriptors.
- Manual/survey kinds may intentionally omit auto-grading, but the exception is
  explicit in the descriptor and test matrix.

## TDD implementation slice

1. Inventory all authoring aliases, compiler mappings, sanitizers, validators
   and grader switches; add a completeness test that fails for a missing phase
   or duplicate runtime kind.
2. Introduce an immutable `QuestionKindDescriptor` and module-owned registry with
   canonical kind, authoring aliases, grading policy and phase capabilities.
3. Route mapping/compile/validation/grading through the registry while keeping
   existing handler logic as small phase adapters. Unknown kinds fail closed at
   authoring and grade boundaries.
4. Add source ratchets and historical fixture tests for all supported kinds,
   native-safe construction, duplicate detection and manual-kind exceptions.
5. Run focused quiz tests, Modulith/public API, compile, GraphQL/OpenAPI checks;
   record any environment-only integration limitations.

## Not in this slice

- New question types or UI changes.
- Offline conflict protocol (BE-062).
- Content lifecycle (BE-063) beyond exposing the registry capability.
