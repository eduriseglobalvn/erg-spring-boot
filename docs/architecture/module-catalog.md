# Module catalog (current migration scope)

This catalog deliberately lists only modules that are currently explicit
Spring Modulith modules. Legacy global packages remain outside the Modulith
graph until they have a facade and boundary contract.

| Module | Owner team | Public API | Consumed/emitted events | Persistence ownership | SLO/on-call |
|---|---|---|---|---|---|
| `calendar` | Scheduling BE | `CalendarOperations` | emits `CalendarBatchPublished`; consumes none | calendar batch/event/room tables | command p95 800ms; `calendar-oncall` |
| `crm` | CRM BE | `CrmOperations` | consumes none in current slice; handover side effects remain synchronous | CRM workspace/lead/opportunity/follow-up/handover tables (legacy adapters) | command p95 800ms; `crm-oncall` |
| `elearning` | ELEARNING BE | `ElearningOperations` | student discussion/announcement side effects remain transactional; no process-local events | persistent discussion/read/assignment projections; catalog prototype remains profile-gated | query p95 500ms; `elearning-oncall` |
| `notifications` | Notifications BE | `NotificationOperations` / delivery registry | consumes `CalendarBatchPublished`; provider-neutral delivery | notification/outbox/device tables | enqueue p95 500ms; `notifications-oncall` |
| `organization` | Organization BE | `OrganizationOperations` | consumes none in the current slice | education-unit tables owned by legacy adapter | query p95 300ms; `organization-oncall` |
| `school` | School Operations BE | `SchoolOperations`, `SchoolQueryOperations` | consumes none in the current slice | school operations tables owned by school module | command p95 800ms; `school-oncall` |
| `reporting` | Reporting BE | `ReportingOperations` | consumes assignment/report projection events; emits none yet | report job and report aggregate tables (legacy service compatibility seam) | enqueue p95 800ms; `reporting-oncall` |
| `authorization` | Identity & Security BE | `AuthorizationOperations` | consumes policy-version/security events; emits none from facade | authorization/role/permission tables owned by legacy adapter | decision p95 50ms; `security-oncall` |
| `users` | Identity & Security BE | `UserOperations` | consumes account/security events; emits none from first slice | user/account/session tables owned by legacy adapter | profile p95 300ms; `identity-oncall` |
| `auth` | Identity & Security BE | `AuthOperations` | emits/consumes session and security events through legacy seam | auth session/token history/login attempt tables | login/refresh p95 800ms; `security-oncall` |
| `lms` | Learning Runtime BE | `LmsOperations` (attempt start/submit slice) | attempt events remain in legacy seam pending registry cutover | assignment/attempt/classroom tables owned by legacy adapter | command p95 800ms; `lms-oncall` |
| `lcms` | Content Authoring BE | `LcmsOperations` (question authoring slice) | publish events remain in legacy seam pending registry cutover | question bank/quiz authoring tables owned by legacy adapter | authoring p95 800ms; `lcms-oncall` |
| `learningcontent` | Learning Content BE | `LearningContentOperations` (content/asset command slice) | content lifecycle events remain in legacy seam pending lifecycle cutover | content item/asset/placement tables owned by legacy adapter | command p95 800ms; `learningcontent-oncall` |
| `curriculum` | Curriculum BE | `CurriculumOperations` | consumes/emits none in the current slice | curriculum subject/level/topic tables owned by legacy adapter | query p95 300ms; `curriculum-oncall` |
| `audit` | Security & Compliance BE | `AuditOperations` | emits `AuditRecorded` after commit; consumer is replay-safe and side-effect free | append-only `audit_events` journal with tenant RLS, hash chain and bounded retention | query p95 500ms; export p95 2s/page; `security-oncall` |
| `learningoutcomes` | Learning Outcomes BE | `LearningOutcomesOperations` | emits `LearningPathCreated`, `LearningPathPublished`, `CompetencyProgressRecorded`, `CompetencyAttained`, `LearningPathCompletionEvaluated`, `OutcomeCertificateIssued`, `OutcomeCertificateRevoked`; no synchronous consumers | learning path/node/completion, competency/attainment, outcome certificate and command receipt tables | command p95 800ms; query p95 300ms; `learning-outcomes-oncall` |
| `learningstandards` | Learning Standards BE | `LearningStandardsOperations` | emits `LearningPackageReady`, `LearningPackageQuarantined`, `LtiLaunchAccepted`, `XapiStatementAccepted`; adapter consumers are post-commit and replay-safe | SCORM package metadata, LTI registration, xAPI receipt and command receipt tables; artifacts stay behind storage port | validation p95 2s; query p95 300ms; `learning-standards-oncall` |
| `enterpriseconnectors` | Enterprise Integrations BE | `EnterpriseConnectorOperations` | emits `EnterpriseConnectorRegistered`, `EnterpriseConnectorSyncCompleted`, `EnterpriseConnectorDeprovisioned`; provider adapters are bounded and replay-safe | connector registration, sync run and command receipt tables with tenant RLS; credentials and upstream payloads stay behind ports | dry-run p95 1s; sync command p95 2s; `enterprise-integrations-oncall` |

Every new module must add its public facade, event catalog entry, owner and
architecture test before changing callers.
