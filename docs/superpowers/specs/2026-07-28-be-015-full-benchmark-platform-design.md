# BE-015 Full Benchmark Platform Design

## Status

Approved direction on 2026-07-28: compare JVM and GraalVM Native using an isolated Docker Compose benchmark platform with Prometheus, Grafana, cAdvisor, PostgreSQL exporter, Redis exporters, and k6. Kubernetes is explicitly out of scope and is tracked separately as BE-026.

## Goal

Produce reproducible, machine-qualified evidence for critical REST and GraphQL flows at the 100, 500, 1,000, and 5,000 VU ladder. Compare JVM and Native latency, throughput, failures, startup, CPU, memory, connection-pool behavior, PostgreSQL behavior, and Redis behavior without changing existing application contracts or touching the running erg-dev environment.

This local result is a baseline for the measured Docker Desktop host. It is not a universal production-capacity claim.

## Isolation and ownership

- Compose project name is fixed to erg-benchmark.
- Benchmark containers, networks, and volumes use project labels and names distinct from erg-dev.
- Existing erg-dev and unrelated Docker resources are read-only and must remain running.
- Only load/results/<run-id>/ persists after a run.
- A finally cleanup invokes docker compose down -v --remove-orphans.
- Post-cleanup verification fails the harness if any erg-benchmark container, network, or volume remains.
- Secrets stay in an ignored benchmark env file. Only placeholder examples are versioned.

## Components

### Application images

One multi-stage Dockerfile exposes two explicit runtime targets built from the same working-tree snapshot:

- jvm-runtime: Spring Boot executable JAR on a pinned Java runtime.
- native-runtime: existing GraalVM native executable on Debian slim.

Both images expose the same port, health endpoints, migrations, environment contract, non-root user, and immutable source metadata.

### Data services

- PostgreSQL 17 with pg_stat_statements, I/O timing, bounded connections, and a dedicated runtime role.
- Redis 8.8 state and cache instances remain separate.
- Flyway runs before fixture loading.
- The deterministic scale fixture creates benchmark users, sessions, roles, tenants, LMS data, attempts, reports, history, and calendar rows.

### Load generator

k6 runs inside Compose and sends load over the benchmark network. Versioned scenarios cover:

- REST cacheable/read flow.
- REST command/submission flow.
- GraphQL representative query.
- GraphQL representative command where the published schema exposes a safe deterministic mutation.

Every flow runs a smoke check before load. The canonical ladder is 100, 500, 1,000, and 5,000 VUs. Each step exports a separate summary so lower-step evidence survives an OOM or resource guard at 5,000 VUs.

### Observability

- Prometheus scrapes Spring Actuator, cAdvisor, PostgreSQL exporter, and both Redis exporters.
- k6 uses Prometheus remote write and also saves canonical JSON summaries.
- Grafana is provisioned from versioned data sources and dashboards.
- cAdvisor captures per-container CPU, throttling, RSS/working set, network, filesystem, and restart data.
- JVM-only panels include heap, GC, threads, and class loading.
- Cross-runtime comparisons use metrics available in both modes; JVM-only metrics never participate in a Native pass/fail decision.

## Fair comparison protocol

1. Record Git commit, dirty diff digest, Docker versions, OS, CPU count, memory, image digests, configuration, and timestamps.
2. Build JVM and Native images from the same snapshot.
3. Start fresh data and monitoring services.
4. Run Flyway, load the deterministic fixture, and validate row counts and bearer-token authentication.
5. Start one runtime only and wait for startup/readiness stabilization.
6. Run smoke tests, warmup, then each ladder step.
7. Export k6, Prometheus, PostgreSQL, Redis, cAdvisor, application, and image metadata.
8. Stop the runtime and reset mutable fixture state to the documented baseline.
9. Repeat with the other runtime under identical resource limits.
10. Generate a machine-readable and Markdown comparison.
11. Clean all runtime resources and verify cleanup.

JVM runs first for the primary pass. A short reversed-order smoke comparison detects obvious order/cache bias. The harness does not run JVM and Native concurrently.

## Resource policy

- Backend JVM and Native receive identical CPU limits.
- Memory limits are recorded explicitly. The primary comparison uses one common ceiling; an optional efficiency profile may later use runtime-specific ceilings but cannot replace the common-ceiling result.
- PostgreSQL, Redis, Prometheus, Grafana, exporters, and k6 have fixed limits across both runs.
- Before each ladder step, the harness checks host memory, swap pressure, container OOM state, and service health.
- A resource guard skips only the unsafe next step, preserves completed evidence, and marks the run incomplete rather than passed.

## Pass and fail rules

The agreed cacheable-read gate remains:

- p50 below 150 ms.
- p95 below 300 ms.
- p99 below 800 ms.
- HTTP failure rate below 0.1%.

Command and GraphQL gates use the existing versioned workload model and cannot be relaxed by the benchmark harness.

A runtime passes only if:

- every required scenario and ladder step completed;
- all k6 thresholds passed;
- no application/container OOM or unexpected restart occurred;
- readiness remained healthy;
- PostgreSQL connections, locks, and Hikari pending borrowers remained within recorded safe bounds;
- Redis eviction/error behavior stayed within the selected profile;
- raw evidence and environment metadata are complete.

An incomplete or resource-guarded 5,000-VU run is reported as incomplete, not success. JVM and Native can pass or fail independently.

## Error handling

- Every setup stage is idempotent and health-gated.
- Failed Flyway, seed, auth, or smoke checks stop load generation.
- k6 non-zero exit preserves summaries and triggers cleanup.
- Metrics collection failure marks observability evidence incomplete even if k6 thresholds pass.
- Cleanup runs after success, failure, interruption, or timeout.
- The harness never deletes Docker resources lacking the exact erg-benchmark project label.

## Testing strategy

Implementation follows test-first development:

1. Contract tests fail until the benchmark Compose file declares all required services, isolation labels, health checks, resource bounds, result mounts, and cleanup command.
2. Dockerfile contract tests fail until both JVM and Native runtime targets exist and use a non-root runtime.
3. Harness tests fail until dry-run planning, source metadata, safe cleanup filters, and partial-result preservation work.
4. Scenario tests fail until REST and GraphQL route/schema references remain versioned and threshold-consistent.
5. Compose config validation, focused Maven tests, image builds, smoke tests, and operational ladder runs provide progressively stronger verification.

Mocks may validate command planning, but BE-015 completion requires real Docker services and measured k6 evidence.

## Artifacts retained

Each run stores:

- environment.json
- manifest.json
- jvm/k6/*.json
- jvm/metrics/*.json
- native/k6/*.json
- native/metrics/*.json
- comparison.json
- comparison.md
- service logs needed to explain failures

Prometheus TSDB volumes and Grafana runtime state are disposable. Dashboard definitions and provisioning files are versioned.

## Deployment reuse

Validated image targets, health checks, environment contract, Prometheus scrape configuration, and Grafana dashboards remain reusable for later deployment. Benchmark-only fixture loading, test bearer tokens, k6, cAdvisor host mounts, and local passwords are never enabled by the production Compose path.

## Non-goals

- No Kubernetes, HPA, KEDA, Helm, or cluster autoscaler work in BE-015.
- No claim that Docker Desktop equals production hardware.
- No SLO relaxation.
- No modification of existing REST/GraphQL contracts.
- No cleanup of erg-dev or unrelated Docker resources.
