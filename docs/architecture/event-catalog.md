# Event catalog

| Event | Version | Producer | Listener/side effect | Idempotency key | Payload/PII | Retry/DLQ/retention |
|---|---:|---|---|---|---|---|
| `CalendarBatchPublished` | 1 | `calendar.CalendarOperations` / calendar command | enqueue schedule notification after commit | `schedule-published:<batchId>` | IDs/versions only; no PII | registry resubmit, failed publication quarantine; publication row deleted on completion; notification/audit retention owns history |

The catalog is the compatibility contract. A new field is additive; changing
meaning requires a new schema version and a migration/replay decision.
