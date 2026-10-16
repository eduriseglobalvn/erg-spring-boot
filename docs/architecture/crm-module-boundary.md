# CRM module boundary

`crm` owns the CRM workspace, lead status, opportunity, follow-up and handover
use cases, plus the SEO school/performance read slice. Controllers depend only
on `crm.CrmOperations`; the public facade contains no JPA entity, repository or
transaction implementation.

`CrmWorkspaceService` and `CrmSchoolService` are explicitly deprecated legacy
adapters during the strangler migration. They retain the existing tenant and
education-unit scope checks, V82 JSONB mappings, idempotency-key replay and
optimistic-lock version checks. Replacing either adapter later must preserve
these invariants and the `/api/v1/crm/**` contract.

CRM intentionally does not introduce a generic workflow base class. Lead,
opportunity, follow-up and handover state machines remain composed in their
bounded use cases, and persistence ownership stays with the current tables
until an additive migration has a verified owner.
