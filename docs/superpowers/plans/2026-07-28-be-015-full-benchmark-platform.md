# BE-015 Full Benchmark Platform Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Build and execute an isolated Docker benchmark platform that compares the same ERG Spring source as JVM and GraalVM Native under versioned REST and GraphQL load while collecting complete application, container, PostgreSQL, Redis, and k6 evidence.

**Architecture:** A dedicated erg-benchmark Compose project owns disposable PostgreSQL, Redis, k6, Prometheus, Grafana, cAdvisor, and exporters. JVM and Native images are built from one Dockerfile and run sequentially with identical resource limits and deterministic fixture state; a PowerShell harness records metadata, preserves results, and always removes only resources bearing the exact benchmark project label.

**Tech Stack:** Spring Boot 4, Java 25, GraalVM Native Image, Docker Compose, PostgreSQL 17, Redis 8.8, k6, Prometheus, Grafana, cAdvisor, JUnit 5, AssertJ, PowerShell.

---

Repository policy forbids commit, stage, push, merge, or deployment without explicit authorization. Commit checkpoints from the generic workflow are replaced by git diff checks and evidence snapshots.

## File map

- Modify Dockerfile: add jvm-builder and jvm-runtime while preserving native-builder and native-runtime behavior.
- Create ops/docker/compose.benchmark.yml: isolated benchmark platform and profiles.
- Create ops/docker/.env.benchmark.example: non-secret defaults and resource limits.
- Create ops/docker/benchmark/prometheus.yml: scrape and k6 remote-write configuration.
- Create ops/docker/benchmark/grafana/provisioning/datasources/prometheus.yml: provision Prometheus.
- Create ops/docker/benchmark/grafana/provisioning/dashboards/dashboards.yml: provision dashboards.
- Create ops/docker/benchmark/grafana/dashboards/erg-benchmark.json: JVM/Native, DB, Redis, k6, and container panels.
- Create load/k6/erg-graphql-critical.js: authenticated GraphQL query baseline.
- Create load/scripts/benchmark.ps1: orchestrate build, fixture, sequential runs, evidence, and cleanup.
- Create load/scripts/benchmark-lib.ps1: pure command planning, labels, metadata, and cleanup guards.
- Create load/scripts/benchmark-lib.Tests.ps1: PowerShell unit tests for safe orchestration.
- Create src/test/java/com/erg/ergspringboot/platform/performance/BenchmarkPlatformContractTest.java: lock Compose and Dockerfile requirements.
- Create src/test/java/com/erg/ergspringboot/platform/performance/K6GraphQlCriticalContractTest.java: lock GraphQL route, operation, auth, and SLO.
- Modify load/README.md: document full platform and local-capacity caveat.
- Create load/results/<run-id> at runtime only: canonical raw evidence.

### Task 1: Lock the benchmark platform contract

**Files:**
- Create: src/test/java/com/erg/ergspringboot/platform/performance/BenchmarkPlatformContractTest.java
- Test: src/test/java/com/erg/ergspringboot/platform/performance/BenchmarkPlatformContractTest.java

- [ ] **Step 1: Write the failing Compose contract test**

    package com.erg.ergspringboot.platform.performance;

    import static org.assertj.core.api.Assertions.assertThat;

    import java.nio.file.Files;
    import java.nio.file.Path;
    import org.junit.jupiter.api.Test;

    class BenchmarkPlatformContractTest {
        private final String compose = Files.readString(Path.of("ops/docker/compose.benchmark.yml"));
        private final String dockerfile = Files.readString(Path.of("Dockerfile"));

        BenchmarkPlatformContractTest() throws Exception {}

        @Test
        void composeOwnsFullIsolatedObservabilityPlatform() {
            assertThat(compose)
                    .contains("name: erg-benchmark")
                    .contains("backend-jvm:")
                    .contains("backend-native:")
                    .contains("postgres:")
                    .contains("redis-state:")
                    .contains("redis-cache:")
                    .contains("prometheus:")
                    .contains("grafana:")
                    .contains("cadvisor:")
                    .contains("postgres-exporter:")
                    .contains("redis-state-exporter:")
                    .contains("redis-cache-exporter:")
                    .contains("k6:");
        }

        @Test
        void imagesExposeComparableNonRootRuntimeTargets() {
            assertThat(dockerfile)
                    .contains("AS jvm-runtime")
                    .contains("AS native-runtime")
                    .contains("USER erg")
                    .contains("HEALTHCHECK");
        }

        @Test
        void composeBoundsResourcesAndPersistsOnlyResults() {
            assertThat(compose)
                    .contains("mem_limit:")
                    .contains("cpus:")
                    .contains("load/results")
                    .contains("read_only: true")
                    .doesNotContain("container_name:");
        }
    }

- [ ] **Step 2: Run the test and verify RED**

Run:

    ./mvnw.cmd -Dtest=BenchmarkPlatformContractTest test

Expected: FAIL because ops/docker/compose.benchmark.yml does not exist and Dockerfile lacks jvm-runtime/native-runtime target names.

- [ ] **Step 3: Record the RED evidence**

Save the Surefire failure and command line in the working notes. Do not alter the assertion to match existing files.

### Task 2: Add comparable JVM and Native image targets

**Files:**
- Modify: Dockerfile
- Test: src/test/java/com/erg/ergspringboot/platform/performance/BenchmarkPlatformContractTest.java

- [ ] **Step 1: Add the minimal JVM stages**

Add before the native builder:

    FROM eclipse-temurin:25-jdk AS jvm-builder
    WORKDIR /workspace
    COPY .mvn/ .mvn/
    COPY mvnw pom.xml ./
    RUN --mount=type=cache,target=/root/.m2 chmod +x mvnw && ./mvnw -B -ntp -DskipTests dependency:go-offline
    COPY src/ src/
    RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp -Dmaven.test.skip=true package

    FROM eclipse-temurin:25-jre AS jvm-runtime
    RUN groupadd --system erg && useradd --system --gid erg --home-dir /app --shell /usr/sbin/nologin erg
    WORKDIR /app
    COPY --from=jvm-builder --chown=erg:erg /workspace/target/erg-spring-0.0.1-SNAPSHOT.jar /app/erg-spring.jar
    USER erg
    EXPOSE 1606
    HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=8 CMD wget -q -O /dev/null http://127.0.0.1:1606/ready || exit 1
    ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/erg-spring.jar"]

Rename the existing final stage from runtime to native-runtime and update its COPY source without changing native compilation.

- [ ] **Step 2: Run the contract test and verify the Dockerfile assertion passes**

Run:

    ./mvnw.cmd -Dtest=BenchmarkPlatformContractTest test

Expected: still FAIL only because compose.benchmark.yml is absent.

- [ ] **Step 3: Validate both Docker targets parse**

Run:

    docker build --check --target jvm-runtime .
    docker build --check --target native-runtime .

Expected: both commands exit 0 with no undefined-stage errors.

### Task 3: Create the isolated Compose and observability platform

**Files:**
- Create: ops/docker/compose.benchmark.yml
- Create: ops/docker/.env.benchmark.example
- Create: ops/docker/benchmark/prometheus.yml
- Create: ops/docker/benchmark/grafana/provisioning/datasources/prometheus.yml
- Create: ops/docker/benchmark/grafana/provisioning/dashboards/dashboards.yml
- Create: ops/docker/benchmark/grafana/dashboards/erg-benchmark.json
- Test: src/test/java/com/erg/ergspringboot/platform/performance/BenchmarkPlatformContractTest.java

- [ ] **Step 1: Create Compose with explicit services and profiles**

The Compose file must use:

    name: erg-benchmark
    services:
      backend-jvm:
        profiles: ["jvm"]
        build:
          context: ../..
          target: jvm-runtime
        cpus: 4.0
        mem_limit: 4g
        read_only: true
      backend-native:
        profiles: ["native"]
        build:
          context: ../..
          target: native-runtime
        cpus: 4.0
        mem_limit: 4g
        read_only: true

Both backends share the same environment anchor, tmpfs /tmp, health check, data network, monitoring network, PostgreSQL, Redis, and resource limits. They must never share a Compose profile.

Add pinned services:

    postgres: postgres:17-alpine
    redis-state: redis:8.8.0-alpine3.23
    redis-cache: redis:8.8.0-alpine3.23
    prometheus: prom/prometheus:v3.5.0
    grafana: grafana/grafana:12.1.0
    cadvisor: gcr.io/cadvisor/cadvisor:v0.52.1
    postgres-exporter: prometheuscommunity/postgres-exporter:v0.17.1
    redis-state-exporter: oliver006/redis_exporter:v1.77.0
    redis-cache-exporter: oliver006/redis_exporter:v1.77.0
    k6: grafana/k6:1.1.0

Use benchmark-only named volumes for PostgreSQL, Redis, Prometheus, and Grafana. Mount load/results read-write only into k6; config and load scripts are read-only.

- [ ] **Step 2: Add Prometheus scrape and remote-write receiver**

prometheus.yml must scrape:

    scrape_configs:
      - job_name: erg-backend
        metrics_path: /actuator/prometheus
        static_configs:
          - targets: ["backend:1606"]
      - job_name: cadvisor
        static_configs:
          - targets: ["cadvisor:8080"]
      - job_name: postgres
        static_configs:
          - targets: ["postgres-exporter:9187"]
      - job_name: redis
        static_configs:
          - targets: ["redis-state-exporter:9121", "redis-cache-exporter:9121"]

Prometheus command includes --web.enable-remote-write-receiver and uses a bounded retention time.

- [ ] **Step 3: Provision Grafana**

Provision one Prometheus datasource at http://prometheus:9090 and a file dashboard provider targeting /var/lib/grafana/dashboards. Dashboard panels must cover k6 p50/p95/p99/failures/RPS, backend CPU/RSS/restarts, Hikari active/idle/pending, JVM heap/GC, PostgreSQL connections/locks/transactions, and Redis memory/hits/misses/evictions.

- [ ] **Step 4: Run Compose and contract validation**

Run:

    docker compose --env-file ops/docker/.env.benchmark.example -f ops/docker/compose.benchmark.yml config
    ./mvnw.cmd -Dtest=BenchmarkPlatformContractTest test

Expected: Compose config exits 0; BenchmarkPlatformContractTest passes.

### Task 4: Add versioned GraphQL critical-load coverage

**Files:**
- Create: load/k6/erg-graphql-critical.js
- Create: src/test/java/com/erg/ergspringboot/platform/performance/K6GraphQlCriticalContractTest.java
- Modify: load/README.md

- [ ] **Step 1: Write the failing GraphQL contract test**

    package com.erg.ergspringboot.platform.performance;

    import static org.assertj.core.api.Assertions.assertThat;

    import java.nio.file.Files;
    import java.nio.file.Path;
    import org.junit.jupiter.api.Test;

    class K6GraphQlCriticalContractTest {
        @Test
        void graphqlScenarioPinsEndpointAuthOperationAndSlo() throws Exception {
            String script = Files.readString(Path.of("load/k6/erg-graphql-critical.js"));
            assertThat(script)
                    .contains("/graphql")
                    .contains("query LmsNamespace")
                    .contains("Authorization")
                    .contains("X-Tenant-ID")
                    .contains("target: 100")
                    .contains("target: 500")
                    .contains("target: 1000")
                    .contains("target: 5000")
                    .contains("p(50)<150")
                    .contains("p(95)<300")
                    .contains("p(99)<800")
                    .contains("rate<0.001");
        }
    }

- [ ] **Step 2: Run and verify RED**

Run:

    ./mvnw.cmd -Dtest=K6GraphQlCriticalContractTest test

Expected: FAIL because erg-graphql-critical.js is absent.

- [ ] **Step 3: Implement the minimal authenticated GraphQL query scenario**

The script posts this stable operation:

    query LmsNamespace {
      lms {
        namespace
        status
      }
    }

Use endpoint BASE_URL + /graphql, bearer token ACCESS_TOKEN or deterministic erg-load-seed-admin-00001, tenant seed-tenant-01, JSON content type, the same four-step ladder, and the read SLO thresholds. Validate HTTP 200, absence of GraphQL errors, namespace lms, and status value.

- [ ] **Step 4: Run focused contracts**

Run:

    ./mvnw.cmd -Dtest=K6GraphQlCriticalContractTest,K6SteadyLoadBaselineContractTest,K6RouteCatalogContractTest test

Expected: all tests pass without changing existing REST routes or thresholds.

### Task 5: Build a safe idempotent benchmark harness

**Files:**
- Create: load/scripts/benchmark-lib.ps1
- Create: load/scripts/benchmark-lib.Tests.ps1
- Create: load/scripts/benchmark.ps1

- [ ] **Step 1: Write failing Pester-style library tests**

Tests must assert:

    Describe "benchmark cleanup safety" {
      It "uses only the erg-benchmark project" {
        (Get-BenchmarkComposeArgs) -join " " | Should -Match "erg-benchmark"
      }
      It "always requests volume and orphan cleanup" {
        (Get-BenchmarkDownArgs) -join " " | Should -Match "down -v --remove-orphans"
      }
      It "rejects a non-benchmark project label" {
        { Assert-BenchmarkProject "erg-dev" } | Should -Throw
      }
    }

- [ ] **Step 2: Run and verify RED**

Run:

    Invoke-Pester load/scripts/benchmark-lib.Tests.ps1

If Pester is unavailable, use a small PowerShell assertion runner in the same test file and run it directly. Expected: FAIL because benchmark-lib.ps1 does not exist.

- [ ] **Step 3: Implement pure safety helpers**

benchmark-lib.ps1 defines:

    function Assert-BenchmarkProject([string]$Name) {
        if ($Name -ne "erg-benchmark") { throw "Refusing non-benchmark project: $Name" }
    }

    function Get-BenchmarkComposeArgs {
        @("--project-name", "erg-benchmark", "--env-file", "ops/docker/.env.benchmark", "-f", "ops/docker/compose.benchmark.yml")
    }

    function Get-BenchmarkDownArgs {
        @((Get-BenchmarkComposeArgs) + @("down", "-v", "--remove-orphans"))
    }

It also provides metadata serialization, project-label verification, result directory creation, and a resource guard. No helper accepts an arbitrary deletion label.

- [ ] **Step 4: Implement orchestration with try/finally**

benchmark.ps1:

    try {
        Assert-BenchmarkProject "erg-benchmark"
        Write-EnvironmentMetadata
        Build-BenchmarkImages
        Start-DataAndObservability
        Invoke-FlywayAndSeed
        Invoke-RuntimeBenchmark -Runtime jvm
        Reset-MutableFixture
        Invoke-RuntimeBenchmark -Runtime native
        Write-Comparison
    }
    finally {
        Stop-BenchmarkPlatform
        Assert-NoBenchmarkResources
    }

Each runtime executes auth and REST/GraphQL smoke, warmup, then individual step runs. A failed step preserves its output and stops later steps for that runtime.

- [ ] **Step 5: Run harness tests and dry run**

Run:

    powershell -NoProfile -File load/scripts/benchmark-lib.Tests.ps1
    powershell -NoProfile -File load/scripts/benchmark.ps1 -DryRun

Expected: tests pass; dry run lists only erg-benchmark operations and makes no Docker changes.

### Task 6: Verify configuration and build both images

**Files:**
- Modify only files identified by failing tests.

- [ ] **Step 1: Run all focused contract tests**

Run:

    ./mvnw.cmd -Dtest=BenchmarkPlatformContractTest,K6GraphQlCriticalContractTest,K6SteadyLoadBaselineContractTest,K6RouteCatalogContractTest,CiPortalGatesContractTest test

Expected: all focused tests pass with zero failures/errors/skips.

- [ ] **Step 2: Validate Compose and pull pinned infrastructure images**

Run:

    docker compose --project-name erg-benchmark --env-file ops/docker/.env.benchmark -f ops/docker/compose.benchmark.yml config
    docker compose --project-name erg-benchmark --env-file ops/docker/.env.benchmark -f ops/docker/compose.benchmark.yml pull postgres redis-state redis-cache prometheus grafana cadvisor postgres-exporter redis-state-exporter redis-cache-exporter k6

Expected: config and pulls exit 0.

- [ ] **Step 3: Build JVM and Native sequentially**

Run:

    docker compose --project-name erg-benchmark --env-file ops/docker/.env.benchmark -f ops/docker/compose.benchmark.yml --profile jvm build backend-jvm
    docker compose --project-name erg-benchmark --env-file ops/docker/.env.benchmark -f ops/docker/compose.benchmark.yml --profile native build backend-native

Expected: both images build from the same source metadata. Record duration, digest, and size.

### Task 7: Execute real smoke and load runs

**Files:**
- Runtime output: load/results/<run-id>

- [ ] **Step 1: Start data and observability and validate health**

Run:

    powershell -NoProfile -File load/scripts/benchmark.ps1 -Phase Infrastructure

Expected: PostgreSQL, both Redis instances, Prometheus, Grafana, cAdvisor, and exporters are healthy; fixture validation passes.

- [ ] **Step 2: Run JVM smoke and ladder**

Run:

    powershell -NoProfile -File load/scripts/benchmark.ps1 -Runtime jvm

Expected: REST and GraphQL smoke pass; each completed ladder step writes k6 summary and metrics snapshots.

- [ ] **Step 3: Reset deterministic mutable state**

The harness expires/recreates benchmark sessions, clears only redis-cache, preserves redis-state integrity, and re-applies idempotent fixture SQL. It validates expected row counts before Native starts.

- [ ] **Step 4: Run Native smoke and ladder**

Run:

    powershell -NoProfile -File load/scripts/benchmark.ps1 -Runtime native

Expected: same scenarios, thresholds, limits, and evidence schema as JVM.

- [ ] **Step 5: Verify result integrity**

Check manifest, environment, JVM and Native summaries, Prometheus snapshots, container stats, PostgreSQL metrics, Redis metrics, logs, image metadata, comparison JSON, and comparison Markdown. Missing 5,000-VU evidence keeps BE-015 incomplete.

### Task 8: Cleanup, regression verification, and task synchronization

**Files:**
- Modify: load/README.md
- Modify: D:/ERG/TASK/ERG_Task_Management_.xlsx through artifact-tool only

- [ ] **Step 1: Force cleanup in a final independent command**

Run:

    docker compose --project-name erg-benchmark --env-file ops/docker/.env.benchmark -f ops/docker/compose.benchmark.yml down -v --remove-orphans

Expected: exit 0 even if the harness already cleaned up.

- [ ] **Step 2: Prove no benchmark resources remain**

Run filtered Docker inspections for label com.docker.compose.project=erg-benchmark across containers, networks, and volumes.

Expected: three empty result sets. Confirm erg-dev containers remain healthy.

- [ ] **Step 3: Run regression verification**

Run:

    ./mvnw.cmd -Dtest=BenchmarkPlatformContractTest,K6GraphQlCriticalContractTest,K6SteadyLoadBaselineContractTest,K6RouteCatalogContractTest,CiPortalGatesContractTest test
    node scripts/export-openapi-contract.cjs --file contracts/openapi/erg-openapi.json --check
    node scripts/check-graphql-schema.cjs
    node --test scripts/openapi-contract-lib.test.cjs scripts/graphql-schema-contract-lib.test.cjs
    git diff --check

Expected: all commands pass. If full mvnw verify is run, record its exact result separately and do not hide unrelated dirty-worktree failures.

- [ ] **Step 4: Update BE-015 from measured evidence**

If every required ladder step and scenario passed, set BE-015 to Hoàn thành, progress 1, completion/update date 2026-07-28, and append exact result paths plus summarized p50/p95/p99, throughput, errors, CPU, RSS, DB, Redis, and cleanup evidence.

If any required run is incomplete, keep BE-015 Đang làm or Bị chặn with truthful progress and the exact blocker. Never mark completion from contract tests or smoke runs alone.

- [ ] **Step 5: Verify workbook and handoff**

Inspect BE-015 and BE-026, reconcile Dashboard totals, scan formula errors, render all sheets, and save the canonical workbook path. Report changed source files, test results, benchmark limits, retained evidence, and cleanup proof.
