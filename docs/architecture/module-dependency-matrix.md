# Application module dependency matrix

The migration currently recognizes eighteen explicit Spring Modulith application
modules. Legacy horizontal packages remain compatibility seams and are guarded
by a shrinking ratchet until their callers move behind module public APIs.

| Module | Public API | Allowed dependencies | Event policy |
|---|---|---|---|
| calendar | `CalendarOperations`, `CalendarBatchPublished`, typed calendar DTOs | platform/shared kernel and explicitly approved legacy adapters during strangler migration | `CalendarBatchPublished` is durable; notification listener is idempotent. Entity projection helpers are isolated behind deprecated `CalendarReadCompatibilityPort`. |
| crm | `CrmOperations` | `organization` and the reviewed legacy CRM adapters | tenant-scoped workspace commands preserve idempotency/optimistic locking; no event in current slice |
| elearning | `ElearningOperations`, module-owned command/query records | `lms`, `users` and reviewed persistent adapters; catalog prototype is not a production dependency | dashboard/discussion/announcement reads and writes are tenant/self-scoped; side effects are transactional |
| notifications | notification delivery registry and typed consumers | `calendar` public event plus platform/shared kernel | provider-neutral, at-least-once delivery with retry/DLQ and tenant scope |
| organization | `OrganizationOperations` | `authorization`, `users` and platform/shared kernel | no module event in current slice; education-unit reads remain DTO-only |
| school | `SchoolOperations`, `SchoolQueryOperations` | `organization` and platform/shared kernel | no module event in current slice; commands preserve tenant/RLS/idempotency |
| auth | `AuthOperations` | platform security and reviewed legacy adapter | authentication/session events remain versioned |
| authorization | `AuthorizationOperations`, `AuthorizationCommandOperations` | platform security and reviewed legacy adapter | policy changes use durable events where asynchronous |
| curriculum | `CurriculumOperations` | platform security and reviewed legacy adapter | no module event in current slice |
| lcms | `LcmsOperations` | platform/shared kernel and the two reviewed `Lcms*Compatibility` construction adapters only | publish side effects occur after commit; legacy service imports are fail-closed by architecture tests |
| learningcontent | `LearningContentOperations`, module-owned request/response records | `curriculum` for topic placement and the reviewed legacy learning-content adapter only | command responses are assembled into DTOs; persistence entities do not cross the public boundary; old `/api` aliases are deprecation-compatible |
| lms | `LmsOperations` | platform/shared kernel and reviewed legacy adapter | attempt/assignment events remain versioned |
| reporting | `ReportingOperations` | platform/shared kernel and reviewed legacy adapter | consumes projection events idempotently |
| users | `UserOperations` | platform security and reviewed legacy adapter | account/security events remain versioned |
| learningoutcomes | `LearningOutcomesOperations` and module-owned request/response records | `authorization`, `audit`, platform security | versioned outcome events carry IDs/versions only; no cross-module persistence import |
| learningstandards | `LearningStandardsOperations` and module-owned request/response records | `authorization`, `audit`, platform security | SCORM/xAPI/LTI metadata is tenant-scoped; protocol payloads and provider SDK types stay behind adapters; accepted events are post-commit and replay-safe |
| enterpriseconnectors | `EnterpriseConnectorOperations` and provider-neutral ports | `authorization`, `audit`, platform security | SIS/HRIS/SCIM registration, bounded dry-run/sync/retry/deprovision; secrets and provider payloads stay behind adapters; command events are tenant-scoped and idempotent |

No module may import another module's `internal` package, persistence entity or
repository. New modules must add a row here and an `@ApplicationModule` package
annotation before code is merged.
