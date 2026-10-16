# BE-044 — Notifications Modulith consumer and provider registry

## Objective

Make notifications an explicit consumer boundary for calendar/LMS events and
isolate Firebase behind a delivery adapter registry. Workers dispatch persisted
delivery kinds through one keyed handler, preserving tenant, retry, DLQ and
idempotency behavior.

## Constraints

- Calendar internals may publish events but must not import notification
  services; notifications reads calendar only through public projections.
- No duplicate delivery through both custom outbox and Modulith publication.
- Provider credentials and payloads stay server-side; delivery errors are bounded
  and retryable without leaking secrets.
- Keep existing notification REST/GraphQL contracts and migration compatibility.

## TDD implementation slice

1. Inventory notification entities, workers, Firebase calls, calendar listeners,
   outbox/event paths and persisted kinds; add RED tests for duplicate handler,
   unsupported kind, tenant reconstruction, retry/DLQ and idempotent replay.
2. Add `NotificationDeliveryAdapter` and immutable registry keyed by channel,
   target and provider; register Firebase as the first adapter.
3. Move the CalendarBatchPublished consumer under notifications and use the
   calendar public projection API; remove worker imports of provider details.
4. Preserve additive persistence/error fields and make one dispatch path explicit;
   add Native/provider contract tests and source boundary ratchets.
5. Run notification/calendar scenario tests, Modulith, compile and contract
   checks; record external provider smoke limitations.

## Not in this slice

- APNs/Web Push implementation beyond registry extension points.
- Offline sync protocol (BE-062).
