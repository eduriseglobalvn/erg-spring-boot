# BE-050: CRM Modulith boundary

## Goal and verified dependency

Make CRM an explicit Spring Modulith module for workspace, lead,
opportunity, follow-up and handover flows while preserving the V82 additive
contract, tenant/RLS behavior, idempotency keys and optimistic locking.
Workbook dependencies `BE-011`, `BE-033`, `BE-034`, `BE-035`, `BE-036`, `BE-039`
and `BE-041` are complete. The current CRM services already contain the
business invariants; this slice moves callers behind one public facade instead
of duplicating or rewriting those invariants.

## Baseline and invariants

- Repository `D:/ERG/erg-spring` is dirty; only CRM module/controller,
  architecture documentation/tests, this plan and workbook evidence are in
  scope.
- Keep `/api/v1/crm/**` paths, payload field names, status codes and
  `X-Idempotency-Key` semantics unchanged.
- `UserAccessContext` remains the sole tenant and education-unit scope source;
  do not trust a tenant body/header override.
- Preserve version checks, append-only V82 JSONB shape and repository tenant
  predicates. No destructive migration or generic workflow base class.
- Legacy services remain deprecated compatibility adapters; controllers call
  only `crm.CrmOperations` after this task.

## Exact implementation scope

1. Add explicit `crm` module descriptor with an allow-list to organization and
   a public `CrmOperations` facade for both workspace and SEO read slices.
2. Change `CrmWorkspaceController` and `CrmSchoolController` to depend only on
   the facade; keep common typed DTOs at the transport edge for exact contract
   compatibility.
3. Mark `CrmWorkspaceService` and `CrmSchoolService` as deprecated legacy
   adapters and add source/constructor ratchets proving persistence types do
   not cross the public module boundary.
4. Add module catalog, dependency matrix, generated-module checkpoint and
   boundary documentation; update Modulith/public API tests.
5. Run focused CRM/controller/architecture tests and the full Maven gate.

## Files and tests

Create `src/main/java/com/erg/ergspringboot/crm/package-info.java`,
`crm/CrmOperations.java`, `src/test/java/.../crm/CrmBoundaryTest.java` and
`docs/architecture/crm-module-boundary.md`. Modify only CRM controllers,
legacy service annotations, module docs/tests and task evidence.

```powershell
./mvnw.cmd -q '-Dproject.build.directory=target-be050' '-Dtest=CrmWorkspaceServiceTest,CrmSchoolServiceTest,CrmWorkspaceControllerTest,CrmSchoolControllerContractTest,CrmBoundaryTest,ArchitectureRatchetTest,ModulithDocumentationTest,ModulithVerificationTest,ModulePublicApiContractTest' test
./mvnw.cmd -q '-Dproject.build.directory=target-be050' verify
```

## Definition of done

- `ApplicationModules.verify()` recognizes `crm` and all focused tests pass.
- Both CRM controllers call only `CrmOperations`; no controller imports a CRM
  service, repository or entity.
- Tenant/scope, idempotency, version and JSONB behavior remains covered by the
  existing service tests.
- Full `mvnw verify` exits 0 and workbook BE-050 carries evidence and paths.

## Non-goals and rollback

Do not package-move all CRM entities/repositories, change V82, introduce a
workflow framework, add provider integrations or modify FE. Rollback is
file-scoped to the CRM facade/controller/module documentation changes; preserve
all unrelated dirty worktree edits.
