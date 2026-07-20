# Legacy outbox cutover

The custom V66 outbox remains only for the two legacy mail event types
`ACCOUNT_TEACHER_INVITE` and `ACCOUNT_RECOVERY_CODE`. Calendar and future
business module events belong exclusively to Spring Modulith's Event
Publication Registry; never publish one logical event to both mechanisms.

Target shutdown date: **2026-08-31**, subject to the following evidence:

1. The legacy backlog is continuously `0` (`backlog = 0`) for one full deployment window and
   the parity dashboard shows every new business event owned by the registry.
2. Retry/dead counts, provider delivery and idempotency-key metrics are
   reconciled; no event type outside the two legacy mail types is claimed by
   this worker.
3. Deploy with `ERG_OUTBOX_WORKER_ENABLED=false` in canary, observe one window,
   then remove the worker/table in a forward migration release. Do not delete
   V66 rows while they are pending or processing.

Rollback is an application/configuration forward fix: re-enable the worker only
for the two legacy mail types if the canary detects unsent mail. It must not be
used to replay Modulith business events.
