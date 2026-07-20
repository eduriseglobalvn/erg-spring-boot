# API performance implementation status

Updated: 2026-07-16 13:25 Asia/Ho_Chi_Minh

Verification update: 2026-07-16 22:39 Asia/Ho_Chi_Minh

## JVM checkpoint

- Full Maven JVM/PostgreSQL suite: 753 tests, 0 failures, 0 errors, 0 skipped (Maven exit code 0; 166 fresh Surefire reports).
- Docker/Testcontainers was available. Flyway applied all 68 migrations and the PostgreSQL query-count/persistence tests executed instead of being skipped.
- Focused new regression coverage includes School Operations, authorization mutation batching, session bootstrap bounds, LCMS version bounds, report overflow handling, and Calendar catalog bounds.

## Changes completed in this checkpoint

- School Operations reads use database-limited `limit + 1` queries and reject more than 1,000 students, 500 classes, or 500 subjects.
- Authorization mutation audit locks users in tenant-scoped batches of 500 and persists users, activities, and security events through `saveAll`.
- Session bootstrap uses tenant-scoped descendant queries and rejects more than 500 education units or classes.
- LCMS quiz version history is database-limited to 101 rows and rejects more than 100 versions.
- Calendar catalog branches use database-limited Pageable overloads and reject more than 500 options per catalog section; validation exceptions are unwrapped from asynchronous task joins.
- Report workers reject assignment reports above 10,000 result rows instead of completing with a silently truncated JSON result.
- FE migration guidance documents all new bounds and error behavior.
- Redis 8.8 focused JVM suite passed (16 tests), Redis/cache integration suite passed (10 tests), and Docker configuration contract passed.
- PostgreSQL-backed LMS runtime contract passed 4/4 after adding canonical auth-user/student-id mapping and the V68 demo runtime package seed.
- Re-audit repaired stale PostgreSQL fixtures and removed executor-thread JPA fan-out from assignment progress, class workspace, and teacher homework so transaction-local tenant/RLS state is preserved.
- Question-bank usage proof now seeds and explains normalized `lms_quiz_questions` links and confirms `idx_lms_quiz_questions_question` instead of JSON-array expansion.

## Still not proven or not fully implemented

- The Docker PostgreSQL gate is reachable and Flyway successfully applies all 68 migrations. Runtime contract coverage includes the demo student, quiz version/package, submit projection foreign-key path, and bounded LMS/LCMS read models.
- Reporting aggregate tables and incremental aggregate maintenance are not complete; `V67` currently provides durable report jobs, not canonical aggregate projections.
- `pg_stat_statements`, representative `EXPLAIN ANALYZE`, index before/after proof, Native Image smoke, k6 5,000-VU ramp/burst/soak, and backup/restore drill remain unexecuted.
- Some legacy LMS unpaged methods and broad non-hot catalog paths remain and require call-site census before removal.
- The 5,000-concurrent target remains an acceptance workload, not a claim about the 16 GB pilot host.

## Native attempt

`mvnw.cmd -Pnative -DskipTests native:compile` reached GraalVM Native Image and Spring/Hibernate AOT processing, then stopped at the Windows native linker because `vswhere.exe` could not find Visual Studio 2022 17.6 or newer. Repeat from a Visual Studio x64 Native Tools prompt or a Linux CI/native builder; this is an environment prerequisite, not evidence of an application reachability failure.

## Continuation re-audit — 2026-07-17

The Linux Docker native builder completed after two fixes. The accepted artifact for this continuation is `erg-spring:native-local` with digest `sha256:a02df2c43ced337d3928100f8e247d758926572fa969f8fe0fa2b9a30fc71715` and size 127,051,790 bytes. Flyway schema version 69, readiness, authenticated REST assignments (40/40), authenticated GraphQL dashboard self-scope, report enqueue/status, and notification outbox failure transition were smoke-tested. The full Maven rerun produced 767 tests with zero failures/errors/skips.

The Native SpEL reflection defect and canonical LMS student ID defect are fixed and covered by regression tests. A 2-VU/10-second cache k6 smoke had zero request errors but p95 1.04 seconds, crossing the diagnostic 500 ms threshold. No 5,000-VU acceptance or soak result exists yet; this continuation must not be read as a 5,000-user capacity claim.
