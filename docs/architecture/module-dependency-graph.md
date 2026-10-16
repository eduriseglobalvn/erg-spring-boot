# Module dependency graph

The current explicit graph contains eighteen migrated module boundaries:

```text
ErgSpringApplication
        |
        v
     calendar       crm              curriculum       elearning       enterpriseconnectors       reporting        authorization       users       auth       lms       lcms       learningcontent       learningoutcomes       learningstandards       notifications       organization       school
        |                         |                  |                         |                    |                 |
        +--> legacy services <----+------------------+----------------+----------+---------+-------+----+-----------------+----------------+
        +--> platform persistence/security adapters
```

The compatibility seam is temporary. A caller may depend on
`CalendarOperations` and the public `CalendarBatchPublished` event; it must
not import `calendar.internal`, a calendar repository, or a JPA entity. The
graph is generated from the compiled application by the Modulith
`Documenter` test and is checked by `ApplicationModules.verify()`.

The remaining calendar entity projection helpers are isolated behind the
explicitly deprecated `CalendarReadCompatibilityPort`; `CalendarReadPort`
itself exposes only module-owned response contracts. LCMS legacy service construction is isolated to
`LcmsCommandCompatibility` and `LcmsReadCompatibility`, and the architecture
ratchet rejects any new legacy or persistence import in the rest of the public
`lcms` package.

Learning Content exposes `LearningContentOperations` and DTO-only request/response
records. `LegacyLearningContentAdapter` is the temporary implementation seam;
the public controller never imports a learning-content entity, repository or
legacy service DTO.

Notifications consumes the public calendar event through its provider-neutral
delivery registry. Organization owns the education-unit read facade, and School
owns the school operations command/query facades; their persistence adapters
remain strangler seams until the next migration slice.

Reporting owns the web-facing authorization boundary through
`ReportingOperations`; the legacy job and LMS assignment services remain a
temporary implementation seam until their internal slices are migrated.

Learning Outcomes owns learning paths, competency attainment and outcome
certificates. It references content, learner and class identifiers only, and
depends on the public authorization and audit facades rather than persistence
types from those modules.

Learning Standards owns SCORM package metadata, LTI registrations and xAPI
statement receipts. Artifact bytes, signature verification and provider
forwarding remain behind module-owned ports; no protocol payload, token or
provider SDK type crosses the public facade.

Enterprise Connectors owns tenant-scoped SIS/HRIS/SCIM registration and bounded
sync-run lifecycle records. Provider selection, secret references and upstream
payloads remain behind the module-owned provider port; the reference adapter is
deterministic and network-free for tests/local development.
