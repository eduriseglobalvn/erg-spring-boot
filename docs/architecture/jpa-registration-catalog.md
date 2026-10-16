# JPA registration catalog

`JpaRegistrationCatalog` is the checked-in inventory for PostgreSQL persistence.
Enabled model packages are passed to `PersistenceManagedTypesScanner`; a
partially managed package may additionally allow-list individual entity class
names so legacy siblings stay dormant. All repository interfaces use the
canonical `com.erg.ergspringboot.repository` root convention and are covered
by the inventory test. Counts are source-level ratchets: adding an
entity/repository requires updating the catalog and its owner/status decision
in the same change set.

| Package | Status | Entities | Repositories | Owner / decision |
|---|---:|---:|---:|---|
| auth | enabled | 5 | 6 | Identity/session owner |
| audit | enabled | 1 | 1 | Immutable audit owner |
| authorization | enabled | 6 | 6 | Authorization owner |
| calendar | enabled | 5 | 5 | Calendar owner |
| community | dormant | 6 | 0 | No PostgreSQL runtime owner yet |
| configuration | dormant | 1 | 0 | Retained for legacy inventory; do not scan |
| content | partial | 4 | 1 | Lifecycle trail enabled; public CMS entities remain dormant |
| courses | dormant | 1 | 0 | Legacy course model; no runtime registration |
| crm | enabled | 6 | 6 | CRM compatibility adapter |
| curriculum | enabled | 3 | 3 | Curriculum owner |
| learningcontent | enabled | 4 | 5 | Learning Content adapter |
| learningoutcomes | enabled | 7 | 7 | Learning path, completion, competency and outcome certificate owner |
| learningstandards | enabled | 4 | 4 | SCORM package metadata, LTI registration and xAPI receipt owner |
| enterpriseconnectors | enabled | 3 | 3 | SIS/HRIS/SCIM connector registration, run and command receipt owner |
| lms | enabled | 15 | 15 | LMS runtime owner |
| notifications | enabled | 7 | 7 | Notification persistence owner |
| organization | enabled | 3 | 2 | Organization compatibility adapter |
| outbox | enabled | 1 | 1 | Platform event delivery |
| quiz | enabled | 14 | 14 | LCMS/LMS quiz persistence |
| recruitment | dormant | 2 | 0 | No current product scope |
| reporting | enabled | 1 | 1 | Reporting owner |
| school | enabled | 3 | 4 | School compatibility adapter |
| security | dormant | 1 | 0 | Security model has no JPA runtime owner |
| users | enabled | 5 | 3 | Account/profile owner |

Dormant does not mean deleted: the classes remain available for an explicit
future migration decision. The catalog and tests prevent a broad scanner change
from silently activating them. No table names, Flyway migrations or public API
contracts change in this slice.
