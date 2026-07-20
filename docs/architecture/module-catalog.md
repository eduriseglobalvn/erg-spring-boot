# Module catalog (current migration scope)

This catalog deliberately lists only modules that are currently explicit
Spring Modulith modules. Legacy global packages remain outside the Modulith
graph until they have a facade and boundary contract.

| Module | Owner team | Public API | Consumed/emitted events | Persistence ownership | SLO/on-call |
|---|---|---|---|---|---|
| `calendar` | Scheduling BE | `CalendarOperations` | emits `CalendarBatchPublished`; consumes none | calendar batch/event/room tables | command p95 800ms; `calendar-oncall` |
| `reporting` | Reporting BE | `ReportingOperations` | consumes assignment/report projection events; emits none yet | report job and report aggregate tables (legacy service compatibility seam) | enqueue p95 800ms; `reporting-oncall` |

Every new module must add its public facade, event catalog entry, owner and
architecture test before changing callers.
