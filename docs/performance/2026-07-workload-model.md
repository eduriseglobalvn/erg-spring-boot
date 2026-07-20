# July 2026 workload model

These are initial release gates, not measured production capacity:

| Workload | Shape | Payload/rows | Initial threshold |
|---|---|---|---|
| Cacheable read | 70% of traffic, 100–5,000 VUs, 70/30 read/write mix, 50 RPS per 1k VUs | 1–4 KiB JSON, 10k assignment rows/tenant | error <1%, p95 <300 ms, p99 <800 ms |
| Standard command | 20% of traffic, bounded burst, 10 RPS per 1k VUs | 1–8 KiB JSON, 5k attempt rows/tenant | error <1%, p95 <800 ms, p99 <1.5 s |
| Webhook ingress | 10% burst, 100 VUs, signed provider payloads | 0.5–2 KiB, 100k inbox receipts | HTTP 202, error <1%, p95 <500 ms, p99 <1 s |
| Event/outbox dispatch | independent 16-worker scenario, 50 dispatches/s target | 1–2 KiB event, 10k pending publications | no duplicate logical success, lag returns to zero |

Each run records p50/p95/p99, completed RPS, Hikari pending borrowers, pool
wait, PostgreSQL CPU/IO/WAL/locks, Redis memory, RSS, GC and swap. Dataset seed
is `load/fixtures/manifest.yaml`; a run without that fixture is exploratory.

Acceptance requires separate steady-read, submission-burst, webhook-burst and
outbox-dispatch runs. Mixed traffic is useful for soak testing but cannot hide
a bottleneck in a release decision.
