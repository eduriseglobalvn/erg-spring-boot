# ERG load validation

Run the steady and submission scenarios only against a deterministic fixture loaded from
`load/fixtures/manifest.yaml`. Supply `BASE_URL` and a test-only `ACCESS_TOKEN`.

The 5,000-VU scenario is an acceptance workload, not a claim that the 16 GB pilot host can
pass it. Record p50/p95/p99, completed RPS, rejected requests, Hikari pending borrowers,
PostgreSQL CPU/I/O/WAL/locks, Redis memory, RSS, GC and swap for every run.

## Disposable JVM/Native benchmark platform

The isolated `erg-benchmark` Compose project includes PostgreSQL, separate Redis state/cache,
Prometheus, Grafana, cAdvisor, PostgreSQL/Redis exporters and k6. It never shares project labels,
networks or volumes with `erg-dev`.

```powershell
# Validate orchestration without changing Docker state.
powershell -NoProfile -File load/scripts/benchmark-lib.Tests.ps1
powershell -NoProfile -File load/scripts/benchmark.ps1 -DryRun

# Build comparable images from the current working-tree snapshot.
docker compose --project-name erg-benchmark --env-file ops/docker/.env.benchmark.example `
  -f ops/docker/compose.benchmark.yml --profile jvm build backend-jvm
docker compose --project-name erg-benchmark --env-file ops/docker/.env.benchmark.example `
  -f ops/docker/compose.benchmark.yml --profile native build backend-native

# Correctness smoke (default) or the complete measured load profile.
powershell -NoProfile -File load/scripts/benchmark.ps1 -Runtime All
powershell -NoProfile -File load/scripts/benchmark.ps1 -Runtime All -FullLoad
```

Smoke runs validate authentication, routing and response correctness; cold-start latency is not an
acceptance measurement. A full run warms each REST and GraphQL business path at 100 VUs for five
minutes before measured receipts, so JVM JIT compilation does not pollute the first p95/p99 sample.
The versioned p50/p95/p99/error thresholds remain enabled for `-FullLoad`.

Before load begins, the harness proves the Prometheus backend/exporter targets, cAdvisor container
series, Hikari metrics, and Grafana datasource/dashboard are queryable. cAdvisor v0.57.0 is required
for Docker Engine 29 with the containerd image store; older 0.52.x images can report healthy while
silently omitting current containers. Raw summaries, validated monitoring receipts, image metadata,
`comparison.json`, and `comparison.md` are retained under `load/results/<run-id>/`. All Compose
resources are removed in `finally` unless `-KeepInfrastructure` is explicitly selected for diagnostics.
The ladder is fail-fast per runtime: a failed step is retained and higher steps for that runtime are
not launched. JVM and Native outcomes remain independent.

Additional isolated gates are available for `webhook-burst.js`, `outbox-dispatch.js`
and `modulith-events.js`. The latter two are operational smoke/command probes;
they do not manufacture worker backlog or prove throughput. Use a seeded staging
environment and attach the resulting metrics before marking a performance gate green.

## 2000-CCU exam-day scenarios (S2, S3, S5)

Model and pass/fail gates: `docs/performance/2026-07-workload-model.md` (login p95 < 1.5 s / p99 < 3 s;
draft and submit p95 < 800 ms / p99 < 1.5 s; cached reads p95 < 300 ms). Thresholds are in each script and
pinned by `K6ExamScenariosContractTest`. Run against a freshly seeded benchmark DB, never production.
Acceptance runs happen on the target host with k6 on a second machine in the same LAN, through nginx;
a run on a developer machine only proves the scripts work.

| Script | What one VU does | Main variables (defaults) |
|---|---|---|
| `erg-login-storm.js` (S2) | One `POST /api/v1/auth/login`; arrivals climb linearly from 0 over the ramp, all from one IP. Any 429 fails the run. | `LOGINS` (2000), `RAMP_SECONDS` (60), `LOGIN_PASSWORD` (required), `USER_PREFIX` (`seed-student-`), `USER_COUNT` (4500), `USER_OFFSET` (0) |
| `erg-exam-session.js` (S3) | Home list → runtime package (`GET /api/v1/lms/quizzes/{quizId}/package`) → start attempt with its id/hash → `PATCH .../draft` every 5 s with only changed answers → submit. Counter `exam_submit_acknowledged` = submissions to reconcile with the DB. | `VUS` (2000), `RAMP_SECONDS` (60), `EXAM_MINUTES` (10), `DRAFT_INTERVAL_S` (5), `CHANGE_PROB` (0.3), `ASSIGNMENT_SLOT` (03), `STUDENT_OFFSET` (0), `RUN_ID` |
| `erg-teacher-progress.js` (S5) | GraphQL `assignmentProgressWorkspace` (the frontend query) every 15 s. Run at the same time as S3. | `VUS` (100), `DURATION` (10m), `POLL_S` (15), `ASSIGNMENT_SLOT` (03) |

All three take `BASE_URL`, and `SMOKE=true` for a one-VU correctness pass (error thresholds only, like the
other scripts: a cold JVM is not a latency sample). `ACCESS_TOKEN`, `TENANT_ID`,
`ASSIGNMENT_ID` and `QUIZ_ID` override the fixture identities. Without them, VUs use the fixture tokens
`erg-load-<user-id>` from `seed-postgres-scale.sql`.

Fixture notes:

- Seed users have a placeholder password hash, so S2 needs a test-only password set on the benchmark DB
  first. Generate a Spring-compatible hash (Node 24.7+), then update only the `erg_benchmark` database:

  ```powershell
  $env:LOGIN_PASSWORD = 'k6-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
  $hash = node -e "const c=require('crypto'),n=c.randomBytes(16),b=x=>x.toString('base64').replace(/=+$/,'');const h=c.argon2Sync('argon2id',{message:process.env.LOGIN_PASSWORD,nonce:n,parallelism:1,tagLength:32,memory:16384,passes:2});process.stdout.write('`$argon2id`$v=19`$m=16384,t=2,p=1`$'+b(n)+'`$'+b(h))"
  docker compose --project-name erg-benchmark --env-file ops/docker/.env.benchmark.example `
    -f ops/docker/compose.benchmark.yml exec -T postgres psql -U erg -d erg_benchmark `
    -c "UPDATE users SET password_hash = '$hash' WHERE id LIKE 'seed-student-%'"
  ```

- S3 starts a new attempt on assignment slot `ASSIGNMENT_SLOT` of the student's class. Each slot already
  has one submitted attempt per student and the limit is 3, so a slot can be used twice; use another slot
  (03..40) or reseed for further runs. Slots 01-02 hold the `erg-5k-submission-burst.js` fixture attempts.
- After S3, check that no submission was lost: the number of `exam_submit_acknowledged` must equal
  `SELECT count(*) FROM lms_attempts WHERE assignment_id LIKE 'seed-assignment-%-03' AND status <> 'in_progress' AND id NOT LIKE 'att-%'`
  (adjust the slot).
- S5 teachers: seed teacher n is homeroom teacher (`role_lms_homeroom_teacher`, class scope) of class n, but
  only classes whose tenant matches the teacher's are seeded that way (30 classes); VUs cycle over them.

Known blockers found on 2026-11-13 with a local smoke run (main `615a4af`), to fix before measuring:

- `GET /api/v1/lms/quizzes/{quizId}/package` answers 500 "Cannot encode cache quizRuntimePackage" as soon as
  the quiz has a published package: the cache descriptor declares `Optional<QuizPackageResponse>` but the
  cache receives the unwrapped value. S3 therefore fails at its package step until this is fixed.
- Login allows 30 attempts per minute per source IP (`ERG_AUTH_LOGIN_RATE_LIMIT_SOURCE_PER_MINUTE`). From one
  IP the 31st login in a minute gets 429, so S2 fails by design until G2-04 settles that limit.

```powershell
# Inside the benchmark network (BASE_URL=http://backend:1606 is preset for the k6 service):
$c = @('compose','--project-name','erg-benchmark','--env-file','ops/docker/.env.benchmark.example','-f','ops/docker/compose.benchmark.yml','--profile','load','run','--rm')
docker @c -e SMOKE=true k6 run /load/k6/erg-exam-session.js
docker @c -e LOGINS=200 -e LOGIN_PASSWORD=$env:LOGIN_PASSWORD k6 run --summary-export=/results/s2-200.json /load/k6/erg-login-storm.js
# S3 + S5 together, from two terminals:
docker @c -e VUS=500 -e RUN_ID=s3-500 k6 run --summary-export=/results/s3-500.json /load/k6/erg-exam-session.js
docker @c k6 run --summary-export=/results/s5-100.json /load/k6/erg-teacher-progress.js
```

Baseline ladder for G2-01: 200 / 500 / 1000 VUs (S2: `LOGINS`), recording what the section above
lists. Note which scenario breaks first and at how many VUs.

Redis 8.8 profiles use `load/k6/redis-8-8-cache.js`:

```powershell
$env:CACHE_MODE='cold' # warm, cache-outage, state-outage, or 5k
$env:BASE_URL='http://127.0.0.1:1606'
$env:ACCESS_TOKEN='<test-only-token>'
k6 run --out json=load/results/redis-8-8-cold.json load/k6/redis-8-8-cache.js
```

Flush only `redis-cache` before a cold run. Never flush `redis-state` or PostgreSQL. Stop one
Redis role at a time for degradation profiles and restore it before the next profile. The `5k`
profile requires a separate Vietnam-LAN/region load generator, 10-minute warmup, 30-minute
steady phase, and collection of host/container/PostgreSQL/Hikari metrics. A local smoke run is
not capacity acceptance.
