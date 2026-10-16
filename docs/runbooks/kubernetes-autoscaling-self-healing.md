# ERG Spring Kubernetes autoscaling and self-healing runbook

## Purpose

This chart turns the measured single-pod capacity boundary into a horizontally scalable deployment. The 2026-07-29 benchmark proved that one 4-CPU JVM pod passes the 100-VU REST/GraphQL SLO but saturates at REST 500 VUs; Native shows the same boundary. Scaling is therefore a platform responsibility, not a reason to relax latency thresholds.

## Release contract

- Pin `image.digest` in production. Tags are allowed only for the disposable kind test.
- Use `helm upgrade --install`; do not hand-edit live Deployments.
- Rolling update uses `maxUnavailable: 0`, readiness/startup/liveness probes, a 60-second grace period and a pre-stop delay.
- Keep at least two replicas. PDB `maxUnavailable: 1` protects voluntary disruption; involuntary failure still relies on the Deployment controller and scheduler.
- Pods are spread across zones and hosts. Cluster Autoscaler must have node groups in at least two zones and enough headroom for one surge pod.

## Autoscaling modes

Choose exactly one mode in values:

- `hpa`: default, `autoscaling/v2`, CPU 60% and memory 75%, aggressive scale-up and a five-minute scale-down stabilization window. Metrics Server is required.
- `keda`: requires KEDA and Prometheus. It scales on `hikaricp_connections_pending`; update the Prometheus address and labels for the target cluster.
- `disabled`: fixed replicas for deterministic testing only.

Never install HPA and KEDA against the same Deployment. Verify with:

```powershell
kubectl -n erg get hpa,scaledobject
```

## Database connection budget

The template fails when:

`effective replicas × perPodMaxConnections > postgresMaxConnections - reservedConnections`

Default production budget is `15 × 8 = 120`, leaving 40 of PostgreSQL's 160 connections for migrations, exporters and operator access. If the Cluster Autoscaler or HPA maximum changes, change PostgreSQL/PgBouncer capacity or the per-pod pool in the same reviewed release. Never raise replicas independently of this budget.

## Production prerequisites

1. Multi-zone worker node groups with Cluster Autoscaler installed and tested.
2. Metrics Server for HPA, or KEDA plus Prometheus for KEDA mode.
3. Prometheus Operator CRDs when `serviceMonitor.enabled=true`.
4. An existing Secret containing datasource, Redis and signing credentials. The chart never creates secrets.
5. Managed PostgreSQL and Redis endpoints reachable under the NetworkPolicy. Set the smallest possible `networkPolicy.externalCIDRs`; the default permits data services only inside the release namespace.
6. Ingress namespace labels matching `networkPolicy.ingressNamespaceLabels`.

## Deploy and observe

```powershell
helm lint helm/erg-spring -f values-production.yaml
helm template erg helm/erg-spring -n erg -f values-production.yaml > rendered.yaml
helm upgrade --install erg helm/erg-spring -n erg -f values-production.yaml --wait --timeout 10m
kubectl -n erg rollout status deployment/erg-erg-spring --timeout=10m
kubectl -n erg get pods -o wide
kubectl -n erg get hpa,pdb
```

Observe p50/p95/p99, HTTP failures, CPU throttling, RSS, Hikari active/pending, PostgreSQL connections/locks and Redis hit/miss/eviction. Hikari pending must return to zero after scale-out. High pending with idle PostgreSQL capacity indicates a per-pod pool issue; high database connections indicates the replica budget is unsafe.

## Scale and failover acceptance

The disposable kind harness validates rendering, multi-node placement, pod deletion recovery, rolling readiness continuity and node drain recovery:

```powershell
powershell -NoProfile -File load/scripts/kubernetes-platform-test.ps1
```

After cluster-level recovery passes, run the versioned REST/GraphQL 100→500→1,000→5,000 ladder through the ingress. Store raw k6 summaries, pod/node events and Prometheus snapshots. BE-015/BE-026 remain incomplete if any required step is missing or breaches SLO.

## Incident response

- Pods Pending: inspect resource requests, taints and topology constraints; verify Cluster Autoscaler can add a node in an eligible zone.
- HPA at maximum: stop increasing traffic, inspect CPU throttling and Hikari pending, then raise the maximum only with a reviewed connection budget.
- Crash loop: inspect startup probe and migration logs. Do not bypass Flyway or RLS checks.
- Node failure: confirm replacement pods become Ready on another node and PDB permits voluntary drain.
- Prometheus/KEDA unavailable: switch to HPA only through a Helm release; never leave both controllers active.

## Rollback

```powershell
helm -n erg history erg
helm -n erg rollback erg <revision> --wait --timeout 10m
kubectl -n erg rollout status deployment/erg-erg-spring --timeout=10m
```

Rollback is application/chart-only. Database migrations are append-only; use a forward fix for schema changes. After rollback, verify readiness, error envelopes, tenant isolation, Hikari budget and p95 before reopening traffic.
