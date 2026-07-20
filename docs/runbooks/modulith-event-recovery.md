# Spring Modulith event recovery runbook

## Scope

Spring Modulith's JDBC Event Publication Registry is the durable source of
truth for new module events. Delivery is at-least-once; a listener may run
again after a timeout, process restart, or an exception. Consumers must keep
their business idempotency key (for example,
`schedule-published:<batchId>`).

## Normal recovery

`ModuleEventRecoveryJob` runs every 30 seconds by default. Each pass asks the
registry to resubmit failed and incomplete publications older than 30 seconds,
with a batch size of 100 and at most 16 in-flight publications. It does not
create a memory queue and it does not invoke listeners directly.

The job can be disabled during an incident or controlled replay:

```text
ERG_MODULE_EVENTS_RECOVERY_ENABLED=false
```

Restart the application after changing this setting. Re-enable it after the
database and downstream provider are healthy.

## Incident procedure

1. Check publication lag, failed rows, completion attempts and listener error
   logs. Confirm whether the problem is database connectivity, a provider
   outage, invalid event data, or a listener regression.
2. If a provider is failing, pause recovery and fix/rate-limit the provider
   first. Do not increase thread pools or batch size to compensate.
3. If the listener is retryable, leave recovery enabled and monitor the
   backlog until it drains. Recovery is safe across instances because the
   registry owns publication state.
4. If the event is permanently invalid, stop automatic recovery, deploy a
   forward fix or a versioned consumer, then resume. Do not delete rows to make
   the metric green.
5. Verify idempotency by checking the downstream business key before any
   manual replay. Never replay the same business consequence through both the
   Modulith registry and the legacy custom outbox.

## Retention and cleanup

Completion mode is `delete` for the hot publication table. Audit/history must
be retained in the owning business/audit table, not by keeping every completed
publication row forever. Monitor table bloat and index growth after sustained
load.

