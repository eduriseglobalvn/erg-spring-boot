# Outbox and event recovery

1. Check `spring_modulith_event_publication`/`event_publication` backlog and
   listener failure metrics before touching rows.
2. Confirm the listener's idempotency key (`listenerName + eventId` or the
   documented business key) before resubmitting anything.
3. Allow `ModuleEventRecoveryJob` to claim failed/incomplete publications; it
   uses bounded batches and a minimum age to avoid racing an active instance.
4. Inspect failed rows after resubmit. A non-retryable error is quarantined for
   manual remediation; do not loop it indefinitely.
5. For legacy V66 rows, use the lease/claim procedure in `outbox-cutover.md`
   (when present) and never run a second dispatcher against the same queue.
6. Record event id, listener, tenant, correlation id, attempt count and outcome
   in the incident timeline. Never edit business audit history to hide a retry.
