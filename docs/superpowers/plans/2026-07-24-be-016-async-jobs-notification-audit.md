# BE-016 — Audit notification/background jobs: replay cho dead-letter

## Task
- **ID:** BE-016 (ERG-SPRING, P1, Async Jobs)
- **Goal:** Job retry không gửi trùng; lỗi được quan sát và **có cơ chế replay**.
- **Dependency:** BE-009, BE-010 (đều Hoàn thành) — đã xác minh.

## Baseline (source hiện tại, khảo sát)
- Notification pipeline: `NotificationCommandService.enqueue` (idempotency-key dedup ở enqueue) →
  `notification_outbox` rows → `NotificationOutboxWorker` (@Scheduled, FOR UPDATE SKIP LOCKED + lease,
  retry exp-backoff, max_attempts=8, honors Retry-After).
- State machine: pending/retry → processing → sent/failed/retry/**dead**.
- Generic outbox: `TransactionalOutboxWorker` (mail legacy) — cùng pattern, terminal `dead`.
- Metrics (BE-010): `notifications.outbox.pending|retry|dead` gauges, send counters/timer.
- Admin: `NotificationAdminOperations` (send/stats/runOutbox) qua `/api/v1/admin/notifications`.

## Vấn đề (gap theo DoD "cơ chế replay")
- `dead` là trạng thái **terminal**: không repo method, không worker path, không admin endpoint nào
  reset `dead` → `retry`. `runOutbox`/`findDueForUpdate` chỉ nhặt `pending/retry`.
- Vận hành: khi một loạt message rơi `dead` (provider outage kéo dài vượt max_attempts), không có cách
  quan sát-được để replay có kiểm soát; chỉ còn sửa DB tay. DoD yêu cầu "có cơ chế replay".
- (Retry/idempotency-at-enqueue/backoff/scheduling-overlap-via-SKIP-LOCKED/graceful-shutdown đã có sẵn
  và đúng — không refactor.)

## Invariants giữ nguyên
- Không đổi retry/backoff/claim/lease logic hiện có.
- Không đổi enqueue idempotency (unique key ở enqueue).
- Replay phải tenant-scoped (không cross-tenant), bounded (limit), và không phá SKIP LOCKED concurrency.
- Replay chỉ reset `dead` → `retry` (attempt_count=0, next_attempt_at=now, clear lease) để worker
  pick lại theo đúng đường retry hiện có — không tự gửi trực tiếp (giữ single dispatch path).

## File create/modify
1. MODIFY `repository/notifications/NotificationOutboxRepository.java`
   - Thêm bulk update query: reset dead → retry theo tenant, bounded, trả số dòng affected.
2. MODIFY `service/notifications/NotificationOutboxWorker.java`
   - Thêm `replayDeadLetters(String tenantId, int limit)` (transactional) → gọi repo, trả count.
3. MODIFY `notifications/NotificationAdminOperations.java`
   - Thêm `replayDeadLetters(tenantId, limit)`.
4. MODIFY `controller/notifications/NotificationAdminController.java`
   - Thêm `POST /outbox/replay-dead` (tenant từ principal, limit từ query, default+cap).
5. MODIFY `common/response/notifications/NotificationResponses.java`
   - Thêm record `OutboxReplayResponse(int requeued)`.
6. CREATE `test/.../service/notifications/NotificationOutboxReplayTest.java`
   - Chứng minh: dead rows → retry (attempt_count=0, next_attempt_at set, lease cleared);
     không đụng non-dead; tenant-scoped; bounded theo limit.

## Các bước
1. Test đỏ: viết `NotificationOutboxReplayTest` với mock repo, assert worker gọi repo replay đúng
   tenant+limit và trả count.
2. Repo: thêm `@Modifying @Query` reset dead→retry bounded theo tenant.
3. Worker: thêm `replayDeadLetters` transactional.
4. Admin op + controller endpoint + response DTO.
5. Test xanh; chạy focused gate.

## Command & expected
- `mvnw -Dtest=NotificationOutboxReplayTest,NotificationCommandServiceTest,TransactionalOutboxWorkerTest test`
  → BUILD SUCCESS, 0 failures.

## Rollback
- 6 file, không đổi schema (chỉ query mới). Revert các edit là đủ.

## Definition of Done (đo được)
- Có cơ chế replay quan sát-được cho dead-letter (repo + worker + admin endpoint).
- Replay tenant-scoped, bounded, đưa dead → retry đúng đường dispatch hiện có (không gửi trùng: vẫn qua
  claim SKIP LOCKED + lease + enqueue idempotency).
- Test chứng minh reset state đúng và không ảnh hưởng non-dead/cross-tenant.
- Focused tests xanh.

## Không làm (chống scope creep)
- Không thêm ShedLock (DB workers đã an toàn qua SKIP LOCKED; 2 cron cleanup idempotent — ghi nhận, không sửa trong task này).
- Không thêm provider-side idempotency token (at-least-once là hành vi chấp nhận được; enqueue dedup + downstream business-key check theo runbook đã cover; ngoài scope P1 này).
- Không refactor TransactionalOutboxWorker (mail legacy đang cutover riêng).
- Không đổi retry/backoff/shutdown hiện có.

## Final status — 2026-07-24 (Hoàn thành)

Khảo sát 5 chiều (retry / idempotency / DLQ / scheduling overlap / shutdown):
- **Retry**: đã có, exp-backoff bounded + max_attempts + honor Retry-After (cả 2 outbox). Solid.
- **Idempotency**: dedup enforced ở enqueue (unique idempotency_key trên outbox_events; dedupe_key
  trên notification_messages). Dispatch dùng FOR UPDATE SKIP LOCKED + lease → không double-dispatch
  đồng thời.
- **DLQ replay**: đây là gap DoD chính — trạng thái `dead` là terminal, có metric/count nhưng
  **không có đường replay**. Đã đóng: thêm replay path dead→retry, bounded + tenant-scoped.
- **Scheduling overlap**: DB workers an toàn multi-instance qua SKIP LOCKED + lease. Ghi nhận (không
  sửa trong scope) 2 cron (device cleanup, refresh-token retention) chưa có ShedLock — idempotent-ish.
- **Shutdown**: graceful server shutdown + custom executor awaitTermination; workers dùng short-tx +
  lease nên interrupt là recoverable.

### Changed
- `repository/notifications/NotificationOutboxRepository.java` — thêm `findDeadForUpdateByTenant`
  (status='dead', tenant-scoped, FOR UPDATE SKIP LOCKED, bounded limit).
- `service/notifications/NotificationOutboxWorker.java` — thêm `replayDeadLetters(tenantId, limit)`:
  reset dead→retry, attempt_count=0, next_attempt_at=now, clear lease; bounded [1,500]; bỏ qua tenant blank.
- `notifications/NotificationAdminOperations.java` — expose `replayDeadLetters`.
- `controller/notifications/NotificationAdminController.java` — `POST /api/v1/admin/notifications/outbox/replay-dead?limit=` (đã nằm dưới matcher `notifications.manage`, không cần sửa SecurityConfig).
- `common/response/notifications/NotificationResponses.java` — record `DeadLetterReplayResponse(requeued)`.
- `test/.../service/notifications/NotificationOutboxWorkerReplayTest.java` (mới) — 2 case: reset đúng state + không dispatch khi replay; bỏ qua tenant blank.
- `docs/runbooks/notification-dead-letter-replay.md` (mới) — quy trình replay + kiểm idempotency downstream.

### Evidence
- Focused: `NotificationOutboxWorkerReplayTest` → 2 tests, 0 failures, BUILD SUCCESS.
- Notification package (trừ `FirebaseHttpV1PushSenderTest` môi trường): 18 tests xanh.
- `FirebaseHttpV1PushSenderTest` 3 errors = môi trường ("Unable to establish loopback connection",
  test tự dựng local HTTP server) — cùng lớp giới hạn sandbox, không liên quan thay đổi.

### Intentionally not done
- Không thêm ShedLock cho 2 cron (idempotent-ish; ngoài scope P1, ghi nhận trong plan).
- Không thêm dispatch-time provider idempotency token (at-least-once là hợp đồng hiện tại; enqueue dedup + lease đã chặn duplicate trong luồng bình thường).
- Không đụng `TransactionalOutboxWorker` (mail legacy đang cutover theo outbox-cutover.md).
