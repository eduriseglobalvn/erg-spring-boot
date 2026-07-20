# Module catalog (current migration scope)

This catalog deliberately lists only modules that are currently explicit
Spring Modulith modules. Legacy global packages remain outside the Modulith
graph until they have a facade and boundary contract.

| Module | Owner boundary | Public API | Events | Persistence ownership |
|---|---|---|---|---|
| `calendar` | schedule, room, conflict and calendar projections | `CalendarOperations` | `CalendarBatchPublished` | calendar batch/event/room tables |

Every new module must add its public facade, event catalog entry, owner and
architecture test before changing callers.

