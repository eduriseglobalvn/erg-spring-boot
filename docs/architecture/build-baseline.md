# Build baseline

Captured on 2026-07-20 from commit `0fe43c2` on Java 25.0.3/GraalVM and Maven 3.9.15.

## Full Surefire baseline

Command:

~~~powershell
.\mvnw.cmd test
~~~

Result:

- Exit code: `1`.
- Elapsed: `268.643 s` (Maven reported `04:23 min`).
- Tests: `833`.
- Failures: `1`.
- Errors: `0`.
- Skipped: `3`.
- Database path: PostgreSQL 17.10 Testcontainers on Docker Desktop; Flyway validated and applied all 71 migrations.
- Spring Boot: `4.0.6`.

Expected failure (must be fixed by the API decision in Phase 1, not by weakening the test):

`LearningResourceLegacyApiRemovalTest.canonicalCurriculumAndContentRestSurfaceIsCommandOnlyForLearningResourceReads` detects `@GetMapping("/tree")` in `CurriculumController` under the legacy `/api/curriculum` mapping.

## Focused contract baselines

Commands were run separately with Maven Surefire:

| Group | Selector | Tests | Failures | Errors | Skipped | Result |
|---|---|---:|---:|---:|---:|---|
| Legacy curriculum route | `LearningResourceLegacyApiRemovalTest` | 3 | 1 | 0 | 0 | FAIL (expected) |
| Native contract | `NativeImageBuildContractTest` | 2 | 0 | 0 | 0 | PASS |
| Security contracts | `*Security*Test` | 86 | 0 | 0 | 0 | PASS |
| Outbox contracts | `*Outbox*Test` | 2 | 0 | 0 | 0 | PASS |
| Query-count integration | `*QueryCount*Test` | 5 | 0 | 0 | 0 | PASS |

## Baseline warnings to track

- Lombok and Mockito emit Java 25 dynamic-agent/deprecation warnings; these are not baseline failures but should be addressed in the CI/toolchain phase.
- Existing logs show an LMS submit path at 12 statements against the current warning threshold of 10; query-count thresholds must become explicit assertions during the read-model/hot-path phases.
- Common configuration still defaults to the `dev` profile and has Flyway disabled by default. These are recorded findings for Phase 4.3 and are intentionally unchanged in the baseline commit.

## Gate interpretation

The baseline is usable for migration because the only full-suite failure is the already documented route contract. Phase 1.2 must make the canonical route decision and turn the full suite green before platform/package migration begins. No platform dependency upgrade is allowed to hide or reorder this failure.
