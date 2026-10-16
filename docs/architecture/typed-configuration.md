# Typed configuration boundary

BE-045 keeps configuration namespaces small and binds them through Spring Boot
`@ConfigurationProperties`. The application environment variable names remain
unchanged because they are still declared in `application.yml`.

| Namespace | Owner | Runtime consumers | Bounds |
| --- | --- | --- | --- |
| `erg.module-events` | `platform/events` | Modulith I/O/CPU executors and durable recovery job | bounded pool sizes/queues, positive delays, bounded recovery batch/in-flight |
| `erg.performance.read-executor` | `platform/concurrency` | blocking read fan-out executor | `max-size >= core-size`, non-negative queue, positive shutdown timeout |
| `erg.http.cors` | `platform/web` | CORS adapter | non-negative max-age; wildcard/credential and prod plaintext checks remain fail-closed |
| `erg.http.trusted-proxy-cidrs` | `platform/web/security` | client-IP resolver | parsed only as configured CIDRs; untrusted forwarding headers are ignored |
| `erg.auth.bff-enabled`, `erg.auth.bff-tenant-id` | `platform/security` | BFF security chain, authentication filter, OIDC bridge and CORS adapter | BFF tenant must be non-blank |

The old `erg.modules.*.new-handler-enabled` values and `MODULES__ENABLED`
were removed on 2026-08-09. A repository-wide search found no production or
test consumer, so retaining them would only create a misleading configuration
surface. A future rollout flag must be introduced beside its real consumer and
must ship with a canary/rollback test.

The compatibility constructors on the affected components are intentionally
kept for embedders and unit tests. Spring uses the typed-properties constructors
in production. This keeps configuration binding AOT-friendly without exposing
secrets through logs or actuator endpoints.

## Smoke evidence

- `TypedConfigurationContractTest` covers default binding, invalid concurrency,
  event and CORS/BFF values, raw-lookup ratchets, and AOT-discoverable property
  annotations.
- JVM compile and focused tests run with JDK/GraalVM 25.
- `mvnw.cmd -Pnative -DskipTests spring-boot:process-aot` passed on
  2026-08-09. Generated bean definitions and reachability metadata include all
  four typed namespaces and their nested types.
- A full `native:compile` link was attempted on the same JVM/GraalVM 25 host;
  the source/AOT phase passed, but Windows could not link because Visual Studio
  2022 Build Tools (`vswhere.exe`) is not installed. The linker limitation is
  environmental and is recorded as such; CI or a developer machine with the
  required MSVC toolchain must provide the final native binary evidence.
