# BE-065 canonical workspace capability namespace

## Goal

Introduce a canonical backend workspace vocabulary without renaming legacy REST/GraphQL/persistence namespaces:

```text
ADMIN, TEACHER, STUDENT, CRM
```

Legacy `lcms`, `lms`, `elearning`, `crm` remain compatibility/domain identifiers where contracts or storage require them. Authorization must use capability/session context, never hostname or a raw client portal string.

## Scope

1. Add a typed backend workspace capability value and a single mapping boundary.
2. Use the mapping in session/bootstrap projection only; do not change published API schemas in this slice.
3. Add tests for canonical mapping, invalid values and legacy compatibility.
4. Add a contract/governance note for the later BE-052/053/054 release.

## Non-goals

- No database rename or destructive migration.
- No REST/GraphQL breaking change.
- No change to permission evaluation semantics.
- No client-controlled tenant or hostname authorization.

## Verification

```powershell
.\mvnw.cmd -Dtest=<focused workspace capability tests> test
node scripts/export-openapi-contract.cjs --file contracts/openapi/erg-openapi.json --check
node scripts/check-graphql-schema.cjs
```
