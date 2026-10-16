# BE-046: modular GraphQL SDL assembly

## Objective

Make GraphQL SDL maintainable by bounded context while keeping one deterministic canonical schema,
the existing checksum and the published API behavior byte-for-byte equivalent. The first slice is
mechanical ownership extraction only; it does not add fields, rename namespaces or introduce
federation/code-first generation.

## Constraints

- `src/main/resources/graphql/schema.graphqls` remains the consumer-facing artifact.
- Module source order is explicit and checked; duplicate root definitions fail the contract test.
- The assembly script normalizes line endings and preserves the canonical trailing newline.
- Spring GraphQL controllers and generated FE contracts are unchanged in this slice.
- Future fields belong in the owning module source, then the canonical artifact, checksum and
  cross-repository generated types are released together.

## Implementation and TDD

1. Split the existing SDL without semantic edits into six ordered module sources: shell,
   school/quiz, elearning, LMS/LCMS, mutation/social and calendar.
2. Add `scripts/assemble-graphql-schema.cjs` with `--write` and check modes; fail when a module is
   missing or the canonical artifact drifts.
3. Add tests for deterministic assembly, canonical root uniqueness and existing GraphQL compatibility.
4. Run the module assembly check, GraphQL checksum, focused schema/shell/Modulith/public API tests,
   compile and the standard contract gates.

## Not in scope

No federation, subgraph deployment, central resolver rewrite, schema field addition, controller
authorization change, or generated contract change.
