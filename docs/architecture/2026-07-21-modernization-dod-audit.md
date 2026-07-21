# Spring Modulith modernization — Definition of Done audit

## Verified green

- `mvnw verify` passes on `codex/spring-modulith-modernization` after the LMS facade reductions.
- Spring Modulith dependency, module verification, documentation, event registry and native contract tests pass.
- UserService and AuthorizationService use-case boundaries have parity/security/architecture coverage.
- `LmsRuntimeService` is a compatibility facade (92 lines); `LmsReadModelService` is a compatibility facade (100 lines). Runtime, classroom, resource, student read-model, assignment progress, class workspace, student-group and teacher-homework logic lives under `lms/internal`.
- `UserService` and `AuthorizationService` are compatibility facades (87 and 90 lines); their production paths inject the internal user/authorization use cases directly. Calendar catalog and schedule-batch reads now have internal query owners and standalone Modulith Scenario startup remains green.
- Calendar Scenario, event recovery, webhook replay protection, cache stampede and bounded executor tests are present and included in verification.
- Native Linux Docker build passed for the current source as image `sha256:213f94f379607088c1ca088c89ede629c2c5a46f3b599158b02178c28b928327` (127,889,208 bytes). A fresh isolated compose smoke returned `/ready` HTTP 200 with `app=true`, `db.postgres=true` and `redis=true`. A real calendar publish initially exposed two native reflection gaps (`ApplicationListenerMethodAdapter#getTargetMethod` and `CacheInvalidationMessage` record accessors); regression tests and runtime hints now cover both, and the rebuilt image returns HTTP 200 with one affected event. The Windows `native:compile` path is not supported here because Visual Studio 2022 native tools are unavailable.
- Local deterministic A/B preflight on the durable Modulith publish path completed at 50 requests/second for two minutes with zero failures: JVM handled 6,000 requests at p95 319.88 ms; native handled 6,001 requests at p95 43.68 ms. Startup reported by Spring was 64.522 s for JVM and 9.643 s for native; idle native RSS was 219.2 MiB. This proves the workload and native event path, but is not a production capacity claim.
- Calendar batch lifecycle and event mutation vertical slices now have dedicated `calendar.internal` owners: `CalendarBatchLifecycleUseCase` owns publish/cancel state transitions, conflict resolution and `CalendarBatchPublished`; `CalendarEventMutationUseCase` owns update/delete/delete-teacher-schedule transactions and response mapping. `CalendarCommandService` delegates these paths while retaining the public cache-eviction contract. Both use cases preserve legacy constructor compatibility, and Modulith Scenario startup remains green. Focused Calendar verification passed 114 tests; cache/command regression verification passed 32 tests; the subsequent full `mvn verify` passed (20 integration tests plus the complete unit/architecture suite).

## Evidence that remains open

The following are not claimed complete merely because unit/integration verification is green:

1. Production-equivalent JVM-vs-native benchmark with controlled equal CPU/memory limits and complete RSS, p99, CPU, heap and database-pool-wait capture. The local A/B preflight above does not have all of those controls.
2. Numeric Modulith Event Publication Registry overhead benchmark. The protocol and k6 scenario are documented in `docs/performance/modulith-event-overhead.md`; the release gate is p95 increase <= 10%.
3. Sustained 100/500/1,000/5,000-concurrency load evidence with PostgreSQL lock/slow-query, Hikari wait, cache and GC measurements.
4. CI-hosted native image build and k6 release gates; local contract tests prove configuration/artifact presence but do not substitute for those environments.

Until these measurements are attached to the release evidence, the modernization is implementation-complete for the migrated modules but not release-complete under the enterprise performance definition of done.
