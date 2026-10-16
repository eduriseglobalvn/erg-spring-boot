# BE-011 — Contract và integration test theo portal (CI report độc lập)

## Task
- **ID:** BE-011 (ERG-SPRING, P0, Testing)
- **Goal:** CI chạy test portal (LMS/LCMS/CRM/ELEARNING) độc lập, chạy cả contract lẫn integration test theo portal, và xuất báo cáo failure độc lập từng portal.
- **Dependency:** BE-004, BE-005, BE-006 (đều Hoàn thành) — đã xác minh.

## Baseline (source hiện tại)
- Branch: codex/spring-modulith-modernization (dirty; nhiều WIP).
- WIP đã có trong worktree (`.github/workflows/ci.yml` +62 dòng, chưa commit):
  - Job `portal-suites`: matrix `[lms, lcms, crm, elearning]`, `fail-fast: false`,
    mỗi portal `./mvnw -Dtest="<glob>" test` rồi upload `surefire-reports` →
    artifact `portal-test-reports-${{ matrix.portal }}`.
  - Job `release-load-gates` (k6) cho tag/dispatch.
  - Untracked test `CiPortalGatesContractTest` khóa cấu trúc yml (text-level) — đang xanh.
- Test đếm được theo glob portal (surefire): LMS 33, LCMS 27, CRM 4, ELEARNING 4 — không portal nào 0 test.

## Vấn đề (gap theo DoD)
- `pom.xml`: surefire **exclude** `**/*IntegrationTest.java` (dòng 231-235); failsafe chỉ chạy khi `verify`.
- `portal-suites` chạy `mvn test` (chỉ surefire) → **integration test theo portal không bao giờ chạy trong leg của portal đó**:
  - LMS: `LmsAssignmentProgressWorkspaceQueryCountIntegrationTest`, `LmsClassWorkspaceQueryCountIntegrationTest`, `LmsTeacherHomeworkWorkspaceQueryCountIntegrationTest`.
  - LCMS: `QuestionBankWorkspaceQueryCountIntegrationTest`.
  - ELEARNING: `StudentDashboardQueryCountIntegrationTest`.
  - CRM: không có integration test.
- Coverage tổng không mất (job `verify` chạy full failsafe), nhưng "integration test **theo portal**" và report integration độc lập từng portal thì thiếu.
- Rủi ro phụ: `mvn test -Dtest=<glob>` fail cứng nếu glob match 0 test (pattern drift) — chưa có safety.

## Invariants giữ nguyên
- Không đổi nội dung test hiện có.
- Không đổi job `verify` (vẫn là full gate chạy toàn bộ failsafe + cross-cutting integration).
- Không làm mất coverage cross-cutting integration (TenantRls, Cache, Auth, ApiCoverage… vẫn chạy ở `verify`).
- Giữ `fail-fast: false` để các portal độc lập.

## File create/modify
1. MODIFY `.github/workflows/ci.yml`
   - `portal-suites`: đổi `mvn test` → `mvn verify` scoped cả surefire (`-Dtest`) lẫn failsafe (`-Dit.test`)
     theo cùng glob portal, thêm `-Dsurefire.failIfNoSpecifiedTests=false` và
     `-Dfailsafe.failIfNoSpecifiedTests=false` (an toàn cho CRM/portal không có integration test).
   - Upload cả `target/surefire-reports` và `target/failsafe-reports` (report contract + integration độc lập/portal).
2. MODIFY `src/test/java/com/erg/ergspringboot/platform/performance/CiPortalGatesContractTest.java`
   - Lock: portal leg dùng `verify`, có `-Dit.test`, có `failIfNoSpecifiedTests=false`, upload failsafe-reports.

## Các bước
1. Sửa ci.yml portal-suites theo trên.
2. Cập nhật CiPortalGatesContractTest lock cấu trúc mới (đỏ trước khi sửa yml nếu chạy ngược, ở đây sửa yml trước rồi lock).
3. Chạy `CiPortalGatesContractTest` → xanh.
4. Chạy 1 portal suite (CRM) local với glob mới (`-Dtest` + `-Dit.test` + failIfNoSpecifiedTests=false, `test` phase) để chứng minh no-empty-match safety hoạt động.
5. Focused gate: `CiPortalGatesContractTest` + CRM portal glob.

## Command & expected
- `mvnw -Dtest=CiPortalGatesContractTest test` → BUILD SUCCESS.
- `mvnw -Dtest="*Crm*Test" -Dsurefire.failIfNoSpecifiedTests=false test` → BUILD SUCCESS (CRM contract tests xanh).

## Rollback
- Chỉ 2 file (ci.yml + contract test). Revert cả hai là đủ; không đổi source/schema.

## Definition of Done (đo được)
- CI portal legs chạy độc lập cả contract (surefire) lẫn integration (failsafe) theo portal.
- Xuất report failure độc lập từng portal (surefire + failsafe artifacts).
- Không fail cứng khi portal không có integration test (CRM).
- Contract test lock cấu trúc CI xanh.

## Không làm (chống scope creep)
- Không viết integration test mới (đã tồn tại; task là tổ chức + report theo portal).
- Không đổi job `verify`, `openapi-contract`, `native`, `k6`.
- Không tách module test mới.

## Final status — 2026-07-24 (Hoàn thành)

### Changed
- `.github/workflows/ci.yml` — `portal-suites` giờ chạy `mvn verify` scoped cả surefire (`-Dtest`) lẫn
  failsafe (`-Dit.test="*<Portal>*IntegrationTest,..."`) theo từng portal; thêm Postgres service +
  `ERG_TEST_POSTGRES_URL` cho integration; `-Dsurefire.failIfNoSpecifiedTests=false` +
  `-Dfailsafe.failIfNoSpecifiedTests=false` (an toàn glob rỗng, vd CRM); upload cả
  `surefire-reports` và `failsafe-reports` → artifact `portal-test-reports-${{ matrix.portal }}`.
- `src/test/.../platform/performance/CiPortalGatesContractTest.java` — thêm test thứ 2 lock cấu trúc
  integration mới (per-portal `IntegrationTest` glob, `it.test`, `failIfNoSpecifiedTests=false`,
  failsafe-reports, Postgres service, `ERG_TEST_POSTGRES_URL`).

### Evidence
- `mvnw -Dtest=CiPortalGatesContractTest test` → 2 tests, 0 failures, BUILD SUCCESS.
- LMS portal command shape chạy local (`mvnw -Dtest="*Lms*Test,*Assignment*Test,*Attempt*Test"
  -Dit.test="*Lms*IntegrationTest,..." -D...failIfNoSpecifiedTests=false verify`) → exit 0.
  - Surefire (contract): các suite LMS xanh (33+ test).
  - Failsafe (integration): **3 test chạy thật** — `LmsAssignmentProgressWorkspaceQueryCountIntegrationTest`,
    `LmsClassWorkspaceQueryCountIntegrationTest`, `LmsTeacherHomeworkWorkspaceQueryCountIntegrationTest`
    (Testcontainers/Docker local), t=1 f=0 e=0 mỗi test. Chứng minh integration report theo portal
    hoạt động end-to-end.
- YAML parse hợp lệ; 7 jobs; matrix `[lms, lcms, crm, elearning]`; Postgres service có mặt.
- CRM leg (glob integration rỗng) không fail nhờ `failsafe.failIfNoSpecifiedTests=false`.

### Intentionally not done
- Không đổi job `verify` (vẫn là full gate); không thêm integration test mới.
- Cross-cutting integration (TenantRls/Cache/Auth/ApiCoverage) vẫn chạy ở `verify` — không nhân đôi
  vào từng portal leg (chúng không thuộc một portal cụ thể).
