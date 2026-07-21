# Spring Modulith modernization — Definition of Done audit

## Verified green

- `mvnw verify` passes on `codex/spring-modulith-modernization` after the LMS facade reductions.
- Spring Modulith dependency, module verification, documentation, event registry and native contract tests pass.
- UserService and AuthorizationService use-case boundaries have parity/security/architecture coverage.
- `LmsRuntimeService` is a compatibility facade (92 lines); `LmsReadModelService` is a compatibility facade (100 lines). Runtime, classroom, resource, student read-model, assignment progress, class workspace, student-group and teacher-homework logic lives under `lms/internal`.
- Calendar Scenario, event recovery, webhook replay protection, cache stampede and bounded executor tests are present and included in verification.
- Native Linux Docker build passed for the current source as image `sha256:325ad22d7d12344a41c3d34a185bcbbf96d32bb29b7a8afaf4296f9b100ae7c1` (127,810,918 bytes). Runtime health smoke evidence remains the previously recorded release smoke; the Windows `native:compile` path is not supported here because Visual Studio 2022 native tools are unavailable.

## Evidence that remains open

The following are not claimed complete merely because unit/integration verification is green:

1. Production-equivalent JVM-vs-native benchmark with the same seeded workload, RSS, steady-state throughput, p95/p99, CPU, heap and database-pool wait.
2. Numeric Modulith Event Publication Registry overhead benchmark. The protocol and k6 scenario are documented in `docs/performance/modulith-event-overhead.md`; the release gate is p95 increase <= 10%.
3. Sustained 100/500/1,000/5,000-concurrency load evidence with PostgreSQL lock/slow-query, Hikari wait, cache and GC measurements.
4. CI-hosted native image build and k6 release gates; local contract tests prove configuration/artifact presence but do not substitute for those environments.

Until these measurements are attached to the release evidence, the modernization is implementation-complete for the migrated modules but not release-complete under the enterprise performance definition of done.
