# Modulith canary rollback

Disable the canary handler flag for the affected module and route traffic back
through its compatibility facade. Compare error rate, p95/p99, DB lock wait,
event lag and business-failure metrics before and after the switch.

Do not roll back Flyway destructively. Keep migrations backward-compatible for
one deployment window and use a forward fix for data/schema repair. Event
consumers must accept both event versions during the window; drain or safely
resubmit publications after the application rollback.
