# Native Image smoke gate

The native binary is an acceptance gate, not a replacement for the JVM control run.

1. Build locally: `./mvnw.cmd -Pnative -DskipTests native:compile`, or build the Linux image with `docker build --platform linux/amd64 -t erg-spring:native-local .`.
2. The runtime image reads Flyway SQL from `filesystem:/app/db/migration/postgres`; `compose.native.yml` sets this explicitly because Native Image resources do not expose a normal classpath URL scanner.
3. Start the generated executable with PostgreSQL and Redis using the same `SPRING_*`
   and tenant/RLS settings as the JVM run.
4. Execute authenticated smoke requests for login/session bootstrap, one REST student
   assignment page, one GraphQL workspace query, report enqueue/status, and one outbox
   delivery transition.
5. Record binary fingerprint, startup-to-ready time, RSS, p95 latency, error rate, and
   query count beside the JVM control run.

The gate passes only when the native process is functionally correct and does not regress
the agreed latency/error/RSS budgets. A successful compilation alone is not evidence that
the native runtime gate passed.

The native profile is an explicit build target. Linux/CI build:

```bash
./mvnw -Pnative -DskipTests native:compile
```

Run with the production profile and external secrets:

```bash
SPRING_PROFILES_ACTIVE=prod \
FLYWAY_LOCATIONS=filesystem:/app/db/migration/postgres \
docker compose --env-file ops/docker/.env.production \
  -f ops/docker/compose.yml \
  -f ops/docker/compose.native.yml up -d
```

`compose.native.yml` is an overlay; `compose.yml` owns PostgreSQL, Redis and
the `edge`/`data` networks. Native AOT keeps the Modulith event registry and
generated module metadata, but excludes Modulith Insight runtime graph
observability because ArchUnit classpath scanning does not support native
`resource:/` locations. Module verification, diagrams and Insight remain JVM
CI/staging gates.

The 2026-07-20 local Linux-builder smoke produced a 316.81 MB executable,
started the production-profile AOT application in 9.067 seconds, applied
Flyway through V73 and returned HTTP 200 from `/ready` with PostgreSQL healthy.
This is a functional smoke result, not a JVM-vs-native throughput decision.

Do not bake `.env`, private keys, JWT secrets or webhook tokens into the image.
Windows developers should use the Linux CI/container smoke path for native builds.
