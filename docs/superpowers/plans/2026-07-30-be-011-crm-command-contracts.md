# BE-011 CRM Command Contract Implementation Plan

> Status: active — canonical task BE-011 evidence is in `D:/ERG/TASK/ERG_Task_Management_.xlsx`.

## Goal and dependencies

- Publish the missing CRM lead/opportunity/follow-up/handover read and command contract required by FE `ERG-081` and `ERG-039`.
- Dependencies BE-004, BE-005 and BE-006 are complete.
- The user explicitly authorized implementing BE dependencies to finish all blocked FE tasks.

## Baseline

- Published REST currently exposes only `GET /api/v1/crm/seo/{seoId}/schools` and `/performance`.
- GraphQL `crm` is a health namespace only.
- CRM entities/repositories already exist for school profiles, deals, contacts, activities and referrals.
- `erg-spring` is dirty from unrelated BE tasks. BE-027 Docker/Nginx changes are preserved and its workbook status is now blocked on BE-015/BE-026.
- Relevant CRM source files must be checked for pre-existing diffs before patching.

## Invariants

- Tenant and actor identity come from the authenticated security context; never accept client-controlled tenant or owner identity.
- Commands require `crm.deal.manage`; reads use the narrow CRM read permission.
- Retryable writes use idempotency/optimistic locking where applicable and return the standard `ApiResponse` envelope with request ID.
- DTOs do not expose JPA entities and input validation is explicit.
- Existing two CRM GET endpoints remain compatible.
- No frontend UI changes in this BE slice.

## Exact file scope after characterization

- `src/main/java/com/erg/ergspringboot/controller/crm/CrmSchoolController.java`
- `src/main/java/com/erg/ergspringboot/common/dto/crm/CrmSchoolDtos.java`
- `src/main/java/com/erg/ergspringboot/service/crm/CrmWorkspaceService.java` (new)
- CRM profile/deal/activity models and repositories; `CrmHandover` model/repository (new).
- `src/main/resources/db/migration/postgres/V82__crm_workspace_contract.sql` (append-only).
- Focused CRM controller/service/migration tests.
- `contracts/openapi/erg-openapi.json` and `.sha256` only through the official export command.
- This plan.

The existing `CrmSchoolService` and its legacy reads stay compatible. Unrelated dirty files remain untouched.

## Execution order

1. Characterize the current FE CRM workspace/domain types/actions and existing BE CRM model/repositories/security context.
2. Write RED contract/application tests for tenant-scoped reads, lead/deal lifecycle, follow-up, handover, validation, authorization and idempotency/conflict behavior.
3. Implement the smallest cohesive DTO/controller/application-service/repository changes that satisfy the real FE contract.
4. Run focused Maven tests, export OpenAPI, validate checksums and breaking-change gates.
5. Run `mvnw.cmd verify`, publish evidence, sync FE contract and continue ERG-081.

## Commands

```powershell
.\mvnw.cmd -Dtest=<focused CRM tests> test
node scripts/export-openapi-contract.cjs --file contracts/openapi/erg-openapi.json
node scripts/export-openapi-contract.cjs --file contracts/openapi/erg-openapi.json --check
node --test scripts/openapi-contract-lib.test.cjs scripts/graphql-schema-contract-lib.test.cjs
.\mvnw.cmd verify
```

Expected exit code is 0 for every completion gate.

## Rollback

- Revert only the CRM files and generated contract/checksum through targeted patches; never reset the dirty worktree.
- No destructive data migration is planned. If persistence requires schema changes, add a new Flyway migration and forward-fix only.

## Definition of Done

- FE can implement `ERG-081` without fixtures or invented endpoints.
- Tenant/auth/validation/idempotency tests and portal contract tests pass.
- Published OpenAPI and checksum are consistent and FE sync succeeds.
- No regression in existing CRM school/performance reads.
- Workbook and plan contain measured evidence.

## Out of scope for this slice

- ELEARNING discussion/announcement and LMS attendance/gradebook contracts; they follow after CRM closure.
- Docker/Nginx, Kubernetes/load work and unrelated dirty files.
