# Backend observability and SLO triage

Use `ops/observability/grafana/erg-backend-slo.json` as the canonical dashboard. REST and GraphQL latency/error panels are low-cardinality; never tag metrics with tenant, user, request ID or raw query. Request and trace IDs belong in logs/traces and are textbox correlation inputs only.

For a latency incident, compare HTTP/GraphQL p95 with Hikari pending borrowers, PostgreSQL lock/IO, Redis/cache hit ratio and Modulith publication lag. A saturated dependency is fixed with query/index/admission work, not an unbounded thread or connection increase. Production sampling defaults to 10% and may be raised temporarily through `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` during a bounded incident window.
