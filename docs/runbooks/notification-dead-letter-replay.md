# Notification dead-letter replay

When notification deliveries exhaust their retry budget they land in
`notification_outbox` with `status='dead'` and stop being picked up by the
scheduled worker. Use this procedure to return them to the retry queue after the
underlying cause (provider outage, expired credential, quota exhaustion) is
resolved.

1. Confirm the dead backlog and cause first. Check the `notifications.outbox.dead`
   gauge and `GET /api/v1/admin/notifications/stats` (tenant-scoped). Inspect
   `last_error_code`/`last_error_message` on a sample of dead rows to confirm the
   failure is now retryable, not a permanent invalid target.
2. Do not replay invalid-target failures. Rows that died because the device token
   is unregistered (`UNREGISTERED`/`NOT_FOUND`) will only die again; the device is
   already disabled. Replay is for transient/provider failures.
3. Replay is tenant-scoped and bounded. Call
   `POST /api/v1/admin/notifications/outbox/replay-dead?limit=<n>` (requires
   `notifications.manage`). It resets up to `limit` (max 500) dead rows for the
   caller's tenant to `status='retry'`, `attempt_count=0`, `next_attempt_at=now`,
   and clears any stale lease. It does **not** dispatch — the scheduled worker (or
   `POST /outbox/run`) sends on its next pass.
4. Idempotency at replay is safe. Replay only mutates the existing outbox row's
   state; it never creates a new outbox row or message, so replaying twice cannot
   duplicate a delivery. The dispatch itself remains at-least-once, guarded by the
   per-row lease (`FOR UPDATE SKIP LOCKED`).
5. Verify after replay. Re-check stats: `dead` should drop by the requeued count
   and `retry`/`sent` should rise as the worker drains the batch. Rows that fail
   again follow the normal backoff and will re-enter `dead` after `max_attempts`.
6. Record tenant, requeued count, error code and outcome in the incident timeline.
   Never edit delivery-attempt history to hide a retry.
