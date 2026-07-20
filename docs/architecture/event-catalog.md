# Event catalog

| Event | Version | Producer | Listener/side effect | Idempotency key | Retention |
|---|---:|---|---|---|---|
| `CalendarBatchPublished` | 1 | `calendar.CalendarOperations` / calendar command | enqueue schedule notification after commit | `schedule-published:<batchId>` | publication row deleted on completion; notification/audit retention owns history |

The catalog is the compatibility contract. A new field is additive; changing
meaning requires a new schema version and a migration/replay decision.
