# BE-009 — Chuẩn hóa idempotency và conflict-code parity

## Task
- **ID:** BE-009 (ERG-SPRING, P0, Concurrency)
- **Goal:** Retry request không tạo dữ liệu trùng; conflict trả error code rõ ràng, nhất quán.
- **Dependency:** BE-006 (Hoàn thành) đã xác minh.

## Baseline (source hiện tại, không tin metadata)
- Branch: codex/spring-modulith-modernization (dirty; nhiều WIP không liên quan).
- WIP đã có trong worktree: `V76__command_idempotency_uniqueness.sql` — thêm unique partial index cho
  `lms_assignments(tenant_id, idempotency_key)` và `quiz_versions(tenant_id, quiz_id, idempotency_key)`.
- Idempotency đã hand-rolled per-command:
  - Create assignment: app check `findByTenantIdAndIdempotencyKey` + (V76) DB unique.
  - Publish quiz: mandatory key + replay + (V76) DB unique.
  - Attempt start: `start_idempotency_key` + V30 unique.
  - Calendar bulk: key + payload-hash + advisory lock + V17 unique (mạnh nhất).
  - Report job / outbox: có key + unique.
- Conflict handler hiện tại (`GlobalExceptionHandler`):
  - `ObjectOptimisticLockingFailureException`/`OptimisticLockException` → 409 `STALE_WRITE`.
  - `DataIntegrityViolationException` → 409 `DATA_CONFLICT` (không leak SQL).
  - `ConflictException` (ApiException) → 409 `CONFLICT` (vd calendar payload mismatch).

## Vấn đề (parity gap theo DoD)
Khi một retry **cùng idempotency key** lọt qua app-check và bị chặn ở DB bởi unique index
`uq_*_idempotency` (V76/V17/V30/V66/V67), nó nổi lên dưới dạng `DATA_CONFLICT` — **không phân biệt**
với bất kỳ unique-constraint conflict nào khác. Client không biết đây là "retry đã race" (nên coi như
thành công/replay) hay là xung đột dữ liệu nghiệp vụ thật. DoD yêu cầu "conflict trả error code rõ ràng".

## Invariants giữ nguyên
- Không đổi HTTP status (vẫn 409 cho mọi conflict).
- Không leak tên constraint/SQL ra message.
- Không đổi hành vi replay hiện có (200 + resource cũ) của các command.
- Không thêm shared idempotency framework (ngoài scope; per-command đang hoạt động).
- Không thêm JPA `@Version` cho entity INSERT-only (QuizVersion) hoặc entity không cần (ngoài scope P0).
- Không xây dựng attendance command (chưa tồn tại — không phải regression).

## File create/modify
1. MODIFY `src/main/java/com/erg/ergspringboot/exception/GlobalExceptionHandler.java`
   - Trong `handleDataIntegrity`: nếu root-cause message chứa tên unique idempotency index
     (`_idempotency`), map sang code riêng `IDEMPOTENCY_CONFLICT` (vẫn 409, message an toàn, không leak SQL).
   - Ngược lại giữ `DATA_CONFLICT`.
2. MODIFY `src/test/java/com/erg/ergspringboot/exception/GlobalExceptionHandlerTest.java`
   - Thêm test: idempotency-index violation → 409 `IDEMPOTENCY_CONFLICT`, không leak tên index.
   - Giữ test hiện có (`DATA_CONFLICT` cho constraint khác) không đổi.

## Các bước
1. Test đỏ: thêm 2 test (idempotency-index → IDEMPOTENCY_CONFLICT; non-idempotency unique giữ DATA_CONFLICT).
2. Implementation tối thiểu: phân loại trong `handleDataIntegrity` bằng cách quét message chain
   tìm marker `_idempotency` (an toàn: chỉ đọc, không leak).
3. Test xanh: chạy `GlobalExceptionHandlerTest`.
4. Verify V76 contract test hiện có (`CommandIdempotencyMigrationContractTest`) vẫn xanh.
5. Focused gate: `GlobalExceptionHandlerTest` + `CommandIdempotencyMigrationContractTest` + `LmsAssignmentCommandServiceTest`.

## Command & expected
- `mvnw -Dtest=GlobalExceptionHandlerTest,CommandIdempotencyMigrationContractTest,LmsAssignmentCommandServiceTest test` → BUILD SUCCESS, 0 failures.

## Rollback
- Chỉ 2 file; revert handler edit + test edit là đủ. Không có migration mới, không đổi schema.

## Definition of Done (đo được)
- Duplicate idempotency-key race → 409 `IDEMPOTENCY_CONFLICT` phân biệt được với `DATA_CONFLICT`.
- Không leak tên constraint/SQL.
- V76 DB guard được contract test khóa (đã có).
- Focused tests xanh.

## Không làm (chống scope creep)
- Không thêm shared idempotency filter/interceptor.
- Không thêm @Version cho các entity target.
- Không tạo attendance feature.
- Không refactor calendar advisory-lock path (đã mạnh nhất).

## Final status — 2026-07-24 (Hoàn thành)

Khảo sát toàn bộ landscape idempotency/optimistic-locking (subagent) và đóng hai gap DoD:

### Gap 1 — DB guard còn thiếu cho create-quiz
`lms_quizzes.idempotency_key` (V23) là command retryable duy nhất có key nhưng **không có unique index**
(app-check `findByTenantIdAndIdempotencyKey` không chống được race). Đã mở rộng V76 thêm dedup +
`uq_lms_quizzes_tenant_idempotency` theo đúng pattern các bảng khác. Các command còn lại đã có DB guard:
assignment + quiz_versions (V76), attempt-start (V30), calendar batch (V17), report_job (V67), outbox (V66).

### Gap 2 — Conflict-code parity
Duplicate idempotency-key race lọt xuống DB nay được phân loại riêng: `handleDataIntegrity` quét message
chain tìm marker `idempotency`, trả **409 `IDEMPOTENCY_CONFLICT`** (raced retry) thay vì `DATA_CONFLICT`
chung chung — không leak tên constraint/SQL. Các conflict khác giữ nguyên: `STALE_WRITE` (optimistic),
`DATA_CONFLICT` (unique khác), `CONFLICT` (ConflictException, vd calendar payload mismatch).

### Changed
- `exception/GlobalExceptionHandler.java` — phân loại idempotency-index violation → `IDEMPOTENCY_CONFLICT` + helper `mentionsIdempotencyConstraint` (quét cause chain, guard chống self-cause loop).
- `db/migration/postgres/V76__command_idempotency_uniqueness.sql` — thêm dedup + `uq_lms_quizzes_tenant_idempotency`.
- `test/.../exception/GlobalExceptionHandlerTest.java` — thêm test IDEMPOTENCY_CONFLICT + giữ DATA_CONFLICT cho constraint khác.
- `test/.../service/lms/CommandIdempotencyMigrationContractTest.java` — assert thêm `uq_lms_quizzes_tenant_idempotency`.

### Evidence
- Focused: `mvnw -Dtest=GlobalExceptionHandlerTest,CommandIdempotencyMigrationContractTest test` → 9 tests, 0 failures, BUILD SUCCESS.
- Package regression (`exception.*` + `service.lms.*` + `service.quiz.*` + repository quiz): 52 tests, 51 pass, 1 error.
- 1 error = `QuizAuthoringCorePersistenceTest` (`@SpringBootTest`) do môi trường: Firebase `HttpClient` không mở loopback selector trong sandbox — cùng root cause đã ghi ở BE-014/BE-017, không liên quan scope. DB-level prof (unique index thực sự chặn duplicate) chạy qua các test `@EnabledIf(postgresProofDatabaseAvailable)` trong CI có Docker.

### Intentionally not done
- Không thêm shared idempotency framework (per-command đang hoạt động; ngoài scope P0).
- Không thêm JPA `@Version` (các command target là INSERT-only hoặc dùng advisory-lock/status-guard; STALE_WRITE handler vẫn phục vụ các entity có @Version như School*).
- Không tạo attendance command (chưa tồn tại — không phải regression BE-009).
- Attempt-submit dùng status-guard (idempotent qua trạng thái "submitted"), không thêm key riêng — ngoài scope tối thiểu.
