# BE-049: Learning Content Modulith boundary

## Goal and verified dependency

Migrate the learning-content vertical slice behind an explicit Spring Modulith
module while preserving the existing REST/OpenAPI behavior, tenant/RLS rules,
cache invalidation and database schema. Workbook dependencies `BE-033`,
`BE-034`, `BE-035`, `BE-036` and `BE-039` are complete; the source already has
an initial facade/adaptor slice that this task must harden rather than duplicate.

## Baseline and invariants

- Repository: `D:/ERG/erg-spring`; worktree is intentionally dirty. Only
  learning-content boundary files, architecture documentation/tests and the
  task plan/workbook may change.
- Keep both `/api/content/**` compatibility routes and `/api/v1/content/**`
  canonical routes; do not change response field names or status codes.
- Tenant comes from `UserAccessContext`/security context. No client tenant
  header/body may override it; all repository calls remain tenant-scoped.
- Persistence entities, repositories and legacy service DTOs must not cross the
  public `learningcontent` facade or controller boundary.
- Existing `learningResourceLibrary` cache eviction and PostgreSQL transaction
  semantics remain intact. No destructive migration or package move.
- Storage details remain behind a small provider/adapter seam; this task does
  not invent a generic workflow or upload provider.

## Exact implementation scope

1. Add/complete module-owned request and response contracts under
   `com.erg.ergspringboot.learningcontent.api` and keep the facade free of
   `model`, `repository` and horizontal service DTO imports.
2. Add the missing module read facade/endpoint for the existing
   `LearningContentService.listContentItems` behavior without changing the
   public route contract; map list responses to module-owned DTOs.
3. Keep `LegacyLearningContentAdapter` as the only reviewed compatibility seam
   to the legacy persistence service and add a source ratchet proving the
   controller/facade never return entities or repositories.
4. Document the module owner, dependencies, persistence ownership, cache and
   old-route deprecation policy in `docs/architecture`; update the catalog and
   Modulith tests for the explicit module.
5. Add focused tests for facade mapping, tenant propagation, list behavior,
   route compatibility and module dependency direction, then run the full BE
   verification gate.

## Expected files

Create/modify only the learning-content module API/facade/adapter/controller,
related architecture tests/docs, this plan and workbook evidence. Do not edit
generated OpenAPI files by hand; regenerate only if a verified route/DTO change
requires it.

## TDD and verification commands

1. Establish baseline with focused learning-content and architecture tests.
2. Write/extend failing boundary and list-contract tests.
3. Implement the smallest DTO/facade/controller/adapter changes.
4. Run:

```powershell
./mvnw.cmd -q '-Dproject.build.directory=target-be049' '-Dtest=LearningContentBoundaryTest,LearningContentCanonicalRouteTest,LearningContentServiceTest,ArchitectureRatchetTest,ModulithDocumentationTest,ModulithVerificationTest,ModulePublicApiContractTest' test
./mvnw.cmd -q '-Dproject.build.directory=target-be049' verify
```

5. Run contract/drift checks if source contracts change; otherwise record that
   no generated contract was edited.

## Rollback and non-goals

Rollback is file-scoped: revert only the BE-049 files if a route or contract
regression appears. Do not roll back unrelated dirty worktree edits. This task
does not implement a new authoring UI, destructive database migration, storage
vendor integration, or future content lifecycle/event bus beyond the existing
module boundary.

## Definition of done

- `ApplicationModules.verify()` and module direction tests pass.
- Controller calls only `LearningContentOperations` and returns DTOs.
- Public facade exposes no entity/repository/horizontal persistence type.
- Existing route snapshots and focused service tests pass unchanged.
- `mvnw verify` exits 0; workbook row BE-049 contains completion evidence and
  the exact plan/test paths.
