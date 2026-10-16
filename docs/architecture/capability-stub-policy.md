# Production capability and prototype policy

Production controllers must never turn a process-local map or generic workflow helper into a
successful-looking business capability. A prototype surface is either:

1. profile-gated with `@Profile("prototype")` (controller and service), so it is absent from the
   production graph; or
2. a published contract that returns the stable `NOT_IMPLEMENTED` API envelope with a named
   capability code, until an owner implements and tests the persistent vertical slice.

`ModuleWorkflowService`, `CmsService`, `CatalogService` and `DocumentService` therefore stay out of
production, together with their prototype controllers. Calendar Excel import is a published
exception: its REST and GraphQL routes fail explicitly with
`CALENDAR_EXCEL_IMPORT_NOT_IMPLEMENTED` and the old process-local parser service is prototype-only.
Clients can show an unavailable-capability state instead of interpreting `saved`, `queued` or
`healthy` as durable business state.

The architecture tests are source ratchets: a newly published stub or an ungated prototype must fail
the build until it is assigned an owner, persistence boundary, authorization policy and contract
tests.
