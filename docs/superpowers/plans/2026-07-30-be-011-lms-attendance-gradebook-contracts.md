# BE-011 LMS Attendance and Gradebook Contract Plan

> Status: completed — BE-011 is tracked in `D:/ERG/TASK/ERG_Task_Management_.xlsx`; this final BE slice unblocks ERG-074.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish authenticated, tenant/class-scoped LMS attendance and teacher-gradebook GraphQL read/write contracts so the canonical frontend can remove production mock data.

**Architecture:** Add append-only V85 persistence with tenant RLS and natural-key uniqueness. A JDBC application service owns bounded workspace reads and optimistic/idempotent upserts; a thin GraphQL controller derives tenant and actor from `ErgPrincipal`, enforces existing `lms.attendance.*` and `lms.grade.*` permissions and class scope, then delegates to the service. FE syncs the published schema via the existing official contract scripts.

**Tech Stack:** Java 25, Spring Boot 4, Spring GraphQL, JdbcTemplate, PostgreSQL/Flyway, JUnit/Mockito, Maven.

---

## Invariants

- Never trust client tenant/actor; tenant and updatedBy come from authenticated principal.
- Require class scope and read/write permissions; bounded date range and result sizes.
- Attendance natural key: tenant/class/student/subject/date/lesson. Grade natural key: tenant/class/student/column.
- Writes are retry-safe via natural-key upsert and support expected-version conflict detection.
- Flyway is append-only; contracts/generated FE types are not hand-edited.
- Preserve existing BE dirty work and prior CRM/ELEARNING BE-011 slices.

## Files and steps

- [x] Add V85 tables, tenant-leading indexes, constraints and tenant RLS for attendance records, gradebook columns and scores.
- [x] Add typed public response/input records in `lms/LmsAcademicRecordResponses.java`.
- [x] Add internal `LmsAcademicRecordUseCase` with bounded reads, deterministic ordering and versioned upserts.
- [x] Add internal `LmsAcademicRecordGraphQlController` with principal tenant/actor, permission and class-scope checks.
- [x] Extend `schema.graphqls` with `attendanceWorkspace`, `gradebookWorkspace`, `upsertAttendance`, and `upsertGradebookScore`.
- [x] Add focused controller/use-case/schema/migration tests and performance-catalog coordinates.
- [x] Run focused Maven tests, GraphQL/schema/migration gates and full `mvnw.cmd verify`.
- [x] Publish checksum for the FE sync handoff.

## Verification evidence

- Focused academic-record and reporting compatibility suite: 8 tests passed.
- Tenant migration policy and V85 contract suite: 3 tests passed.
- GraphQL schema/controller/performance catalog drift gate: 3 tests passed.
- Full `mvnw.cmd verify`: 1,042 unit tests and 42 integration tests passed; Flyway upgraded through V85; GraphQL inspection reported no unmapped fields, arguments, or nullness errors.
- Published GraphQL schema SHA-256: `4b4b6dd73415a8f6cef884c4e1bbc3b6c266700914dcb0157d99327ec86c77e0`.

## Rollback

- Revert only new Java/schema/test files and issue a forward migration if persistence changes need correction. Never edit V85 after publication.

## Definition of Done

- Auth, tenant, permission, class scope, bounded reads, retry/optimistic writes and RLS have executable evidence.
- Full Maven verify passes and the published GraphQL checksum is regenerated.
- FE can query real attendance/gradebook data and explicit dev/test fixtures remain the only mock path.
