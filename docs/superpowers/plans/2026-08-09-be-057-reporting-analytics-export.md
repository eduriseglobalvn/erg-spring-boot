# BE-057 — Production reporting, analytics and export

## Objective

Turn the existing assignment-progress/quiz reporting slice into a tenant-scoped,
traceable reporting contract that is useful to the ERG LMS now and safe to extend
to larger centres later. Reports must be based on normalized read models, expose
explicit KPI definitions and timezone/filter metadata, and keep heavy exports off
the request thread with bounded retention.

## Scope

- Add a typed reporting query contract for assignment progress with page, size,
  class/status/submitted-time filters, timezone validation and a source snapshot
  timestamp. Preserve the existing GraphQL response compatibility.
- Add a KPI summary contract for assignment progress (recipients, submission,
  in-progress, needs-review, not-started and average percent) whose definition and
  source model are explicit in the response; no static fixture rows.
- Extend the async report-job contract with filter/request metadata, CSV/JSON
  format, expiry, bounded result rows and a tenant-scoped download endpoint.
  Idempotent enqueue remains scoped to tenant + idempotency key and job status
  remains observable through the existing endpoint.
- Enforce tenant and class scope in the reporting facade before query/enqueue;
  validate requested timezone and reject unsupported formats/filters fail-closed.
- Keep PII out of logs, cap exports at 10,000 rows, expire result access after a
  documented TTL, and retain the existing normalized `student_progress` and
  assignment aggregate read paths.

## Test-first sequence

1. Add red contract tests for filter normalization/timezone/format validation,
   KPI definitions, tenant/class authorization, CSV escaping and expired download.
2. Add migration/response contract tests for job metadata/expiry and indexes.
3. Implement typed requests/responses, query service, REST endpoints and worker
   export path while preserving old constructors and GraphQL behavior.
4. Regenerate runtime OpenAPI, sync FE vendored types, run focused reporting tests,
   `bun run contract:check` in FE and reliable full Maven verify.

## Acceptance

- Report APIs return only tenant/scope-authorized normalized data with bounded,
  deterministic pagination and explicit KPI source/definition/timezone metadata.
- Large export requests return an idempotent queued job; worker produces bounded
  JSON/CSV output, progress/status/error are observable, download is tenant-scoped
  and expires; no request thread performs the full export.
- Repeated idempotency keys do not create duplicate jobs; changed request payload
  for a reused key is rejected; unsupported format/filter/timezone is a typed 4xx.
- Focused tests, OpenAPI/GraphQL locks, FE generated types and full verify pass.

## Non-goals

No BI warehouse, arbitrary SQL/report builder, cross-tenant reporting, client-side
dashboard redesign, or production object-storage credentials are introduced here.
The bounded database result is the ERG-internal first release; object storage can
be added behind the same download contract later.
