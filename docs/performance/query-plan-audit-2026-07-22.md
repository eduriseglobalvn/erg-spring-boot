# PostgreSQL query-plan audit — 2026-07-22

## Scope

This audit locks representative tenant-scoped read shapes for the LMS/LCMS/calendar hot paths. The test runs against PostgreSQL (Testcontainers or `ERG_TEST_POSTGRES_URL`), seeds two tenants, runs `ANALYZE`, and captures `EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)`.

| Query shape | Source/read path | Required index | Guard |
|---|---|---|---|
| Assignment page by tenant/class/status ordered by due date | `AssignmentRepository` projections used by LMS read models | `idx_lms_assignments_read_class_status_due` | plan contains table, tenant predicate and `Execution Time`; no query-per-row behavior |
| Attempt history by tenant/student ordered by update time | `AttemptRepository` projections/dashboard | `idx_lms_attempts_read_student_updated` | same plan contract |
| Calendar school/time-range read | `TeachingScheduleEventRepository` / calendar read model | `idx_tse_tenant_school_timerange` or the more selective range plan `idx_tse_tenant_timerange` | same plan contract; planner choice is recorded, not forced |
| Question usage by page of question IDs | `QuestionBankWorkspaceQueryCountIntegrationTest` | `idx_lms_quiz_questions_question` | existing regression also rejects `jsonb_array_elements_text` |

## Reproduction

```powershell
.\mvnw.cmd -Dtest=PostgresQueryPlanAuditIntegrationTest,QuestionBankWorkspaceQueryCountIntegrationTest test
```

The test is intentionally a plan regression, not a release-load claim. Production acceptance still requires representative scale data, `pg_stat_statements`, p95/p99, buffer/cache behavior, lock waits, connection-pool saturation and before/after index write-cost measurements. No pool/thread tuning is justified by this artifact alone.

## Security and N+1 invariants

- Every audited SQL shape contains `tenant_id` in the predicate and uses tenant-leading indexes.
- The five existing workspace query-count integration tests remain the N+1 guard; their limits are fixed by workspace and must not grow with item cardinality.
- Query-count green plus an index plan does not prove endpoint latency under 5,000 concurrent users; BE-015 and production-equivalent load evidence remain open.
