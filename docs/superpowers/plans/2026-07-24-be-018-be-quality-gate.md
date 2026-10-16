# BE-018 — Quality gate BE (chặn merge khi gate bắt buộc fail)

## Task
- **ID:** BE-018 (ERG-SPRING, P1, CI/CD)
- **Goal:** Pipeline có báo cáo rõ; không bỏ qua quality gate bắt buộc.
- **Dependency:** BE-002, BE-011, BE-012 (đều Hoàn thành) — đã xác minh.

## Baseline (source hiện tại)
- `.github/workflows/ci.yml` có job: `verify` (compile+arch+test+failsafe), `portal-suites`
  (contract+integration/portal), `openapi-contract` (snapshot+checksum+breaking-change),
  `dependency-and-secret-scan` (gitleaks + dependency-check), `native` (nightly/tag), `k6-smoke`,
  `release-load-gates` (dispatch/tag).
- 4 job đầu trigger trên `pull_request`/`push` = gate bắt buộc; native/k6/release chỉ schedule/tag/dispatch.
- Xác minh: **không có `continue-on-error`** ở đâu; `-DskipTests` chỉ ở native compile (đúng, test chạy ở verify).
- `CiPortalGatesContractTest` đã khóa cấu trúc portal-suites + release gate.

## Vấn đề (gap DoD)
- Chưa có guard khóa: các gate bắt buộc (verify, openapi-contract, dependency-and-secret-scan) phải
  hiện diện **và** chạy trên pull_request, **và** không job nào bị `continue-on-error` bypass.
  Nếu tương lai ai đó thêm `continue-on-error: true` hoặc xóa một job gate, không test nào bắt được.

## Invariants giữ nguyên
- Không đổi hành vi job hiện có.
- Không thêm job mới (đã đủ gate).

## File modify
1. MODIFY `test/.../platform/performance/CiPortalGatesContractTest.java`
   - Thêm test khóa: workflow trigger `pull_request`; có các job bắt buộc `verify:`, `openapi-contract:`,
     `dependency-and-secret-scan:`; **không** chứa `continue-on-error`; verify chạy `mvnw ... verify`;
     openapi job chạy snapshot check `--check`.

## Command & expected
- `mvnw -Dtest=CiPortalGatesContractTest test` → BUILD SUCCESS.

## Rollback
- 1 file test. Revert đủ.

## Definition of Done (đo được, phần in-repo)
- Contract test khóa gate bắt buộc hiện diện + trigger PR + không bypass.
- Focused test xanh.

## Không làm
- Branch-protection "required status checks" là cấu hình GitHub repo (deploy-time, cần quyền admin repo),
  không phải file trong repo — ghi nhận là bước vận hành, ngoài scope code sandbox.

## Final status — 2026-07-24 (Hoàn thành)

DoD "pipeline có báo cáo rõ và không bỏ qua quality gate bắt buộc":
- **Mandatory gates hiện diện & trigger on PR**: `verify`, `portal-suites`, `openapi-contract`,
  `dependency-and-secret-scan` — tất cả chạy trên `pull_request` (không giới hạn dispatch/tag).
- **Không bypass**: xác minh 0 `continue-on-error` trong ci.yml; `-DskipTests` chỉ ở native compile
  (tests đã chạy ở verify). Không có gate bắt buộc nào bị nới lỏng.
- **Report rõ**: portal legs upload surefire+failsafe reports/portal; dependency scan + release-load
  đều upload artifact.
- **Gap đã đóng**: thêm contract test khóa tập gate bắt buộc + trigger PR + no-bypass, chống ai đó
  âm thầm xoá job/thêm continue-on-error.

### Changed
- `test/.../platform/performance/CiPortalGatesContractTest.java` — +1 test
  `mandatoryQualityGatesAreWiredAndCannotBeBypassed` (assert 4 job bắt buộc, `pull_request` trigger,
  không có `continue-on-error`).

### Evidence
- Focused: `CiPortalGatesContractTest` → 3 tests, 0 failures, BUILD SUCCESS.
- Xác minh: ci.yml có 7 job; mandatory 4 job chạy on PR; 0 continue-on-error; `-DskipTests` chỉ native.

### Intentionally not done
- Branch-protection required-checks trên GitHub (server-side setting) = cấu hình repo/admin, không phải
  file trong repo — ghi nhận là deploy-time config; contract test đã khóa phần file-level tối đa.
