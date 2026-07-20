# Application module dependency matrix

The migration currently recognizes only `calendar` as an explicit Spring
Modulith application module. Legacy horizontal packages are intentionally not
detected until they have a public facade and a reviewed dependency contract.

| Module | Public API | Allowed dependencies | Event policy |
|---|---|---|---|
| calendar | `CalendarOperations`, `CalendarBatchPublished`, typed calendar DTOs | platform/shared kernel and explicitly approved legacy adapters during strangler migration | `CalendarBatchPublished` is durable; notification listener is idempotent |

No module may import another module's `internal` package, persistence entity or
repository. New modules must add a row here and an `@ApplicationModule` package
annotation before code is merged.
