# Notifications provider registry

BE-044 makes `notifications` the owner of notification consumers and delivery orchestration.
Calendar publishes the public `CalendarBatchPublished` event and exposes a small
`CalendarPublishedBatchProjection`; it does not import notification services or repositories from
another module. `notifications.internal.CalendarBatchPublishedConsumer` consumes the event and
uses only that projection to build an idempotent `NotificationCommand`.

## Delivery boundary

`NotificationOutboxWorker` is provider-neutral. It loads the durable message and outbox row, builds
`NotificationDeliveryRequest`, and calls `NotificationDeliveryAdapterRegistry`. The registry key is
`(channel, targetKind, provider)`. At startup it rejects duplicate handlers and missing handlers for
all currently persisted keys:

- `push / registration_token / firebase`
- `push / fid / firebase`
- `push / topic / firebase`

`FirebaseNotificationDeliveryAdapter` is the first adapter and is the only class that knows the
Firebase sender and encrypted registration-token format. Adding Web Push or APNs adds an adapter and
a migration/catalog key; it does not change the worker or calendar module.

## Persistence and retry

`notification_outbox.provider` pins routing for retries. Existing rows are backfilled to `firebase`
by the additive migration `V86__notification_delivery_provider_registry.sql`. Delivery attempts keep
the legacy `firebase_error_code` for compatibility and also persist provider-neutral error code and
message fields. The existing bounded lease, retry/backoff, dead-letter, and replay state machine is
unchanged. The event idempotency key remains the Modulith event id, and notification message dedupe
is enforced by the existing tenant/dedupe uniqueness contract, so the same business event has one
inbox/outbox command path.

Native/runtime behavior remains explicit: Firebase sender payload/result types stay registered by the
existing notification runtime-hints registrar, while the registry and request records are ordinary
Java records with no reflective provider discovery.
