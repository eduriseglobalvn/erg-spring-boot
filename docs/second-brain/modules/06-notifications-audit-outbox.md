# 06 - Notifications, Audit, Outbox

Phạm vi: `notifications` (inbox, preference, device, push FCM, delivery adapter), `audit` (nhật ký bất biến có hash chain) và hai outbox: `notification_outbox` (push) và `outbox_events` (email tài khoản). Đường dẫn tính từ gốc repo; `P/` = `src/main/java/com/erg/ergspringboot/`.

## 1. Phạm vi và trách nhiệm

- **Notifications**: tạo thông báo (message + recipient inbox + outbox đẩy push), cho người dùng đọc/đánh dấu/lưu trữ inbox, quản lý preference và đăng ký thiết bị FCM; một worker lease-based gửi push qua Firebase HTTP v1 với retry/backoff/dead-letter.
- **Audit**: ghi sự kiện kiểm toán append-only theo tenant, có redaction, idempotency, hash chain SHA-256, retention; đọc/export có phân quyền và lọc theo scope.
- **Outbox giao dịch (`outbox_events`)**: hiện chỉ phục vụ gửi email SMTP (mời giáo viên, mã khôi phục) với payload được mã hóa AES-GCM.
- Không có GraphQL operation nào cho ba miền này (grep `notification|audit|outbox` trong `src/main/resources/graphql` không có kết quả). Chỉ có REST.

## 2. Class/entity chính và vai trò

| Class | File | Vai trò |
|---|---|---|
| NotificationController | `P/controller/notifications/NotificationController.java` | REST inbox/preference của chính người dùng |
| NotificationDeviceController | `P/controller/notifications/NotificationDeviceController.java` | REST đăng ký/heartbeat/thu hồi thiết bị |
| NotificationAdminController | `P/controller/notifications/NotificationAdminController.java` | REST admin: gửi, thống kê, chạy outbox, replay dead |
| NotificationOperations / NotificationAdminOperations | `P/notifications/NotificationOperations.java`, `P/notifications/NotificationAdminOperations.java` | Facade module (controller chỉ gọi facade) |
| NotificationService | `P/service/notifications/NotificationService.java` | Đọc inbox, unread count, mark read, archive, preference |
| NotificationCommandService | `P/service/notifications/NotificationCommandService.java` | `enqueue(NotificationCommand)` - tạo message, recipient, outbox |
| NotificationDeviceService | `P/service/notifications/NotificationDeviceService.java` | register/unregister/heartbeat/revoke thiết bị, sync topic portal |
| NotificationOutboxWorker | `P/service/notifications/NotificationOutboxWorker.java` | Claim batch, gửi, ghi attempt, retry/dead, replay dead |
| NotificationOutboxRetentionJob | `P/service/notifications/NotificationOutboxRetentionJob.java` | Xóa cứng outbox `sent`/`dead` quá hạn theo batch |
| NotificationDeviceMaintenanceService | `P/service/notifications/NotificationDeviceMaintenanceService.java` | Vô hiệu hóa thiết bị stale hằng ngày |
| NotificationMetrics | `P/service/notifications/NotificationMetrics.java` | Micrometer counters/timers/gauges |
| NotificationSecuritySupport | `P/service/notifications/NotificationSecuritySupport.java` | Lấy `ErgPrincipal`, xác thực portal |
| LmsNotificationEventPublisher | `P/service/notifications/LmsNotificationEventPublisher.java` | Dựng NotificationCommand cho sự kiện LMS (bài tập, nộp bài, điểm thấp) |
| CalendarBatchPublishedConsumer | `P/notifications/internal/CalendarBatchPublishedConsumer.java` | Listener Modulith: lịch giảng dạy xuất bản -> thông báo giáo viên |
| NotificationTokenCipher | `P/notifications/NotificationTokenCipher.java` (+ facade deprecated `P/service/notifications/NotificationTokenCipher.java`) | AES-GCM mã hóa registration token (`enc:v1:`) |
| NotificationDeliveryAdapterRegistry / Key / Kinds / Request / Payload | `P/notifications/delivery/*.java` | Định tuyến (channel, targetKind, provider) -> adapter, fail-fast khi thiếu adapter |
| FirebaseNotificationDeliveryAdapter | `P/notifications/delivery/FirebaseNotificationDeliveryAdapter.java` | Adapter duy nhất: registration_token, fid, topic |
| FirebaseHttpV1PushSender / FirebasePushSender / SendResult | `P/notifications/push/*.java` | Gọi FCM HTTP v1, phân loại lỗi |
| ServiceAccountAccessTokenProvider / StaticAccessTokenProvider | `P/notifications/push/*.java` | OAuth2 JWT bearer từ service account / token tĩnh |
| FirebaseNotificationConfiguration, NotificationPushProperties, FirebaseNotificationRuntimeHints | `P/notifications/config/*.java` | Bean theo `erg.notifications.push.enabled`, thuộc tính, hint GraalVM |
| Entity notification | `P/model/notifications/Notification{Message,Recipient,Outbox,DeliveryAttempt,DeviceRegistration,Preference,TopicMembership}Entity.java` | Bảng thật (xem mục 6) |
| Model legacy | `P/model/notifications/{Notification,Digest,DeliveryLog,NotificationPreference,WebhookPref}.java` | POJO Lombok không có `@Entity` (port từ erg-go), không thấy dùng trong luồng hiện tại |
| AuditOperations | `P/audit/AuditOperations.java` | Facade: `record`, `query`, `export` |
| AuditWriteService | `P/audit/internal/AuditWriteService.java` | Append: advisory lock, idempotency, redaction, hash, publish `AuditRecorded` |
| AuditHashChain | `P/audit/internal/AuditHashChain.java` | SHA-256 trên JSON chuẩn hóa |
| AuditRedactionPolicy | `P/audit/internal/AuditRedactionPolicy.java` | Che secret, hash PII, giới hạn kích thước |
| AuditQueryService | `P/audit/internal/AuditQueryService.java` | Đọc theo tenant + scope, redact lại khi đọc |
| AuditRetentionService | `P/audit/internal/AuditRetentionService.java` | Xóa bản ghi hết hạn theo batch (bật maintenance GUC) |
| AuditRecordedConsumer | `P/audit/internal/AuditRecordedConsumer.java` | Listener Modulith chỉ log debug |
| AuditController | `P/controller/audit/AuditController.java` | REST đọc/export (JSON, CSV) |
| AuditRead / AuditExport | `P/audit/authorization/*.java` | Meta-annotation `@PreAuthorize` |
| AuditEvent | `P/model/audit/AuditEvent.java` | Entity `audit_events` |
| AuthorizationMutationAuditService | `P/service/authorization/AuthorizationMutationAuditService.java` | Producer audit chính: thay đổi quyền/role (cộng user_activities, security_events) |
| OutboxEvent / OutboxEventRepository | `P/model/outbox/OutboxEvent.java`, `P/repository/outbox/OutboxEventRepository.java` | Entity/repo `outbox_events` |
| TransactionalAccountMailSender | `P/service/auth/TransactionalAccountMailSender.java` | Ghi intent email vào outbox (ngoài slice nhưng là producer duy nhất) |
| TransactionalOutboxWorker | `P/service/outbox/TransactionalOutboxWorker.java` | Dispatch email, retry/dead |
| OutboxPayloadCipher | `P/service/outbox/OutboxPayloadCipher.java` | AES-GCM mã hóa secret trong payload |
| AuthAuditContext, AuthorizationAuditContext, UserAuditContext | `P/auth/`, `P/authorization/`, `P/users/` | Record metadata (actor, requestId, ip, UA), đổi sang `UserService.AuditContext` |

## 3. Nghiệp vụ và luồng xử lý

### 3.1 Tạo thông báo (`NotificationCommandService.enqueue`) - `P/service/notifications/NotificationCommandService.java:63-142`

Nguồn gọi thực tế trong code: `NotificationAdminOperations.send` (REST admin) và `CalendarBatchPublishedConsumer`. `LmsNotificationEventPublisher` có test nhưng không có caller nào trong `src/main` (xem mục 9).

1. Chuẩn hóa `userIds` (trim, bỏ rỗng, giữ thứ tự, khử trùng) `:164-176`; chuẩn hóa `topicName` `:178-193`.
2. Bắt buộc có ít nhất một `userId` hoặc `topicName` `:144-148` (`BadRequestException`).
3. `data` không được có key rỗng hoặc key dành riêng: `notificationId, eventType, tenantId, portal, sourceType, sourceId, deeplink, deviceRegistrationId` `:26-35`, `:150-162`.
4. Topic: bỏ tiền tố `/topics/`, lower-case, chỉ `[a-z0-9_.~%-]`, bắt buộc bắt đầu bằng `t_<tenant đã sanitize>_` `:183-191` (cách ly tenant).
5. Idempotency: nếu `idempotencyKey` đã tồn tại cho tenant thì trả về message cũ với `recipientCount=0, outboxCount=0`, không tạo gì thêm `:70-79`.
6. Lưu `NotificationMessageEntity` (`eventType` mặc định `GENERAL_NOTICE`, `priority` mặc định `normal`, `channel="push"`, `visibility="private"`) `:81-95`.
7. Tạo `NotificationRecipientEntity` cho các user chưa có (portal mặc định `lms`) `:99-118` - đây là inbox.
8. Với mỗi thiết bị `enabled=true` của các user trong portal đó: tạo một dòng outbox. `targetKind="registration_token"` (targetRef = token đã mã hóa) nếu có token, ngược lại `"fid"` `:120-129`, `:195-209`. Thiết bị không có token lẫn fid bị bỏ qua.
9. Nếu có topic: thêm một outbox `targetKind="topic"` `:135-138`.
10. Outbox khởi tạo: `status=pending`, `attemptCount=0`, `maxAttempts=worker.maxAttempts` (mặc định 8), `nextAttemptAt=now`, `provider="firebase"` `:215-233`.
11. Payload FCM data = `notificationId, eventType, tenantId, portal, [sourceType, sourceId, deeplink]` + `payloadData` + `deviceRegistrationId` (chỉ outbox thiết bị) `:235-253`.

Ghi chú: `@Transactional` bao toàn bộ; outbox được ghi cùng transaction với message nên là transactional outbox thực sự.

### 3.2 Worker gửi push - `P/service/notifications/NotificationOutboxWorker.java`

State machine của `notification_outbox.status`:

```
pending --claim--> processing --success--> sent
                              --invalidTarget--> failed   (và vô hiệu hóa device)
                              --retryable && attempt < maxAttempts--> retry --(đến hạn)--> processing
                              --còn lại (không retry được hoặc hết lượt)--> dead
dead --replay--> retry (attempt_count=0)
processing quá leaseTimeout (locked_at <= now-60s) --reclaim--> processing
```

- Kích hoạt: `@Scheduled(fixedDelay = erg.notifications.push.worker.fixed-delay-ms, mặc định 10000)` chỉ chạy khi `push.enabled && worker.enabled` `:67-72`. Admin có thể gọi tay `runDueBatch(tenantId)` `:78`.
- Claim: trong một transaction, `SELECT ... FOR UPDATE SKIP LOCKED` các dòng `pending/retry` đã đến hạn hoặc `processing` hết lease, `limit pollSize` (kẹp 1..500, mặc định 200), đặt `processing`, `locked_at`, `locked_by="erg-spring"` `:135-151`; SQL tại `P/repository/notifications/NotificationOutboxRepository.java` (`findDueForUpdate*`).
- Gửi: ngoài transaction (HTTP không giữ lock DB) `:89-90`, `:188-209`. Nếu message gốc mất -> `NOTIFICATION_MISSING` (không retry, thành `dead`).
- Ghi kết quả trong transaction mới `:153-186`: luôn lưu một `notification_delivery_attempts` (hash SHA-256 của targetRef, không lưu token) `:211-229`; tăng `attempt_count`; lưu `last_error_*` (cắt 500 ký tự).
- Retry: `backoff = retryAfter` nếu provider trả `Retry-After`, ngược lại `initialBackoff(10s) * 2^min(6, attempt-1)` giới hạn `maxBackoff(60m)` cộng jitter tới 20% `:247-257`.
- `invalidTarget` (mã lỗi chứa `UNREGISTERED`, `NOT_FOUND` hoặc `INVALID_ARGUMENT`) -> outbox `failed`, tắt thiết bị (`enabled=false`, `revokedAt`) nếu payload có `deviceRegistrationId` và cùng tenant `:172-175`, `:231-245`; điều kiện tại `P/notifications/push/FirebaseHttpV1PushSender.java:174-182`.
- Dead-letter replay: `replayDeadLetters(tenantId, limit)` kẹp 1..500, chuyển `dead -> retry`, reset `attempt_count=0`, xóa lease `:113-133`.
- Thiết bị chỉ có `fid`: sender luôn trả `FID_TARGET_UNSUPPORTED` (không retry) `FirebaseHttpV1PushSender.java:42-53` -> hậu quả `dead` (xem mục 9).

### 3.3 Gửi qua FCM - `P/notifications/push/FirebaseHttpV1PushSender.java`

1. Kiểm tra `push.enabled`, `projectId`, target không rỗng, có access token `:62-74` (thiếu token là retryable `ACCESS_TOKEN_MISSING`).
2. Body JSON `message{token|topic, notification{title,body}, data, webpush.fcm_options.link}`; chặn nếu body > `maxPayloadBytes + 2048` -> `PAYLOAD_TOO_LARGE` không retry `:77-80`, `:112-134`.
3. POST `{endpoint}/v1/projects/{projectId}/messages:send`, timeout đọc 10s `:81-87`, `:136-140`.
4. 2xx -> `providerMessageId` = `name`. Lỗi: `errorCode = error.status` hoặc `HTTP_<code>`; retryable nếu HTTP 429 hoặc >= 500 và không phải invalid target; `IOException`/interrupt -> retryable `:89-109`, `:170-172`.
5. Access token: `StaticAccessTokenProvider` nếu có `staticAccessToken`; nếu không `ServiceAccountAccessTokenProvider` (đọc file service account, ký JWT RS256, đổi lấy access token, cache tới trước hạn 60s) nếu có `serviceAccountPath` hoặc env `GOOGLE_APPLICATION_CREDENTIALS`; nếu không có gì -> provider luôn rỗng `FirebaseNotificationConfiguration.java:34-51`; `ServiceAccountAccessTokenProvider.java:41-86`.
6. Khi `push.enabled=false` (mặc định) bean `DisabledFirebasePushSender` trả `PUSH_DISABLED` không retry `FirebaseNotificationConfiguration.java:64-94`.

### 3.4 Inbox, preference, thiết bị (người dùng) - `NotificationService`, `NotificationDeviceService`

- Inbox: phân trang kẹp page 0..10000, size 1..100; `status` chỉ nhận `unread|read` (giá trị khác = tất cả); chỉ lấy bản chưa archive `NotificationService.java:41-59`, `NotificationRecipientRepository.findInbox`.
- `markRead`: tìm recipient theo `id` hoặc `notificationId` của chính user+portal; idempotent (trả `updated=0` nếu đã đọc) `:80-90`, `:153-157`. `markAllRead` bằng UPDATE hàng loạt `:92-96`. `archive` đặt `archivedAt` (không xóa) `:98-104`.
- `updatePreferences`: upsert theo (tenant,user,portal,eventType), chỉ ghi các trường khác null `:114-151`.
- `register` thiết bị `NotificationDeviceService.java:40-78`: bắt buộc `fid` hoặc `registrationToken`; tìm bản ghi hiện có theo `fid` (ưu tiên) hoặc `tokenHash`; token mã hóa bằng `NotificationTokenCipher` và lưu cả SHA-256; chống chiếm thiết bị: nếu bản ghi đang active thuộc user khác -> `ForbiddenException`, nếu đã revoke/disabled thì được gán lại cho user mới `:140-148`; luôn `enabled=true`, `revokedAt=null`; tạo/cập nhật membership topic `t_<tenant>_portal_<portal>` `:157-169`.
- `unregister` im lặng nếu không tìm thấy hoặc không thuộc user/portal `:80-95`; `heartbeat` cập nhật `lastSeenAt` và `permissionState` `:97-105`; `revokeOwnDevice` -> 404 nếu không phải của user `:107-112`.
- Dọn thiết bị stale: cron `0 20 3 * * *`, vô hiệu hóa thiết bị `enabled` có `lastSeenAt/tokenUpdatedAt/updatedAt` cũ hơn `staleRegistrationAge` (30 ngày; <=0 -> 30 ngày) `NotificationDeviceMaintenanceService.java:29-50`, `NotificationDeviceRegistrationRepository.disableStaleDevices`.
- Retention outbox: cron `0 30 3 * * *`, xóa `sent|dead` có `created_at` < now - 90 ngày, batch 1000, lặp tối đa 10000 vòng `NotificationOutboxRetentionJob.java:34-49`. Trạng thái `failed` không bao giờ bị xóa.

### 3.5 Thông báo lịch giảng dạy - `CalendarBatchPublishedConsumer.java:26-43`

`@ApplicationModuleListener` nhận `CalendarBatchPublished`; đọc batch qua projection công khai của module calendar; không có giáo viên thì bỏ qua; ngược lại `enqueue` với `idempotencyKey = event.eventId()`, `eventType=SCHEDULE_PUBLISHED`, `actionUrl=/calendar`, portal `lms`. Module Modulith `notifications` chỉ được phụ thuộc `calendar` (`P/notifications/package-info.java`). Lỗi nghi ngờ trong data map: xem mục 9 (B1).

### 3.6 Audit ghi - `AuditWriteService.append`, `P/audit/internal/AuditWriteService.java:54-103`

1. Khóa `pg_advisory_xact_lock(hashtext(tenantId))` -> tuần tự hóa chuỗi hash theo tenant `:56`, `:112-116`.
2. `idempotencyKey` rỗng thì sinh `audit:<id>`; > 192 ký tự -> lỗi `:57-59`. Nếu key đã tồn tại: cùng ý định (action, resourceType, resourceId, outcome) thì trả bản cũ, khác thì `ConflictException` `:61-68`, `:105-110`.
3. Chuẩn hóa `occurredAt` về UTC; `retentionUntil` mặc định = `occurredAt + 365 ngày` (cấu hình `erg.audit.retention.default-days`, hợp lệ 1..3650) `:70-73`.
4. `details` qua `AuditRedactionPolicy` (mục 4). `correlationId` mặc định = `requestId` hoặc id mới `:84`.
5. `previousHash` = `eventHash` của bản ghi mới nhất cùng tenant (sắp theo `occurredAt desc, id desc`) `:88-89`; `eventHash = SHA-256(JSON chuẩn hóa các trường bất biến + previousHash + redactionVersion)` `AuditHashChain.java:24-44`.
6. `saveAndFlush` rồi publish `AuditRecorded` (Modulith, sau commit) `:95-101`; `AuditRecordedConsumer` hiện chỉ log debug.
7. Producer thực tế: `AuthorizationMutationAuditService` (`AUTHORIZATION_CHANGED`, `AUTHORIZATION_ROLE_CHANGED`), `EnterpriseConnectorService`, `LearningOutcomesService`, `LearningStandardsService` (ngoài slice), và `AuditOperations.export` (`AUDIT_EXPORTED`).

### 3.7 Audit đọc/export

- `AuditQueryService.query` `:26-41`: bắt buộc `tenantId`; lọc `from/to/actorId/resourceType/resourceId/outcome`; lọc scope: nếu `managedEducationUnitIds` hoặc `managedClassIds` chứa `"*"` thì xem tất cả; ngược lại chỉ thấy bản ghi scope null, `TENANT`, hoặc `EDUCATION_UNIT`/`CLASS` nằm trong tập được quản lý (`AuditEventRepository.search`). Danh sách rỗng được thay bằng `__NO_SCOPE__` `:52-55`.
- `details` được redact lần nữa khi đọc `:48`.
- `export` `AuditOperations.java:34-54`: cùng truy vấn, sau đó ghi một audit `AUDIT_EXPORTED` (idempotency `audit-export:<requestId>:<page>:<size>`); CSV hoặc JSON ở controller.

### 3.8 Retention audit - `AuditRetentionService.java`

`@Scheduled(fixedDelay = 3600000)` (1 giờ), mỗi lần xóa tối đa `batch-size` (500) bản ghi có `retention_until < cutoff` và `legal_hold=false`, bằng cách đặt GUC `app.maintenance_mode` và `app.audit_retention_job` cục bộ transaction để vượt RLS và trigger append-only `:39-59`; `AtomicBoolean` chống chạy chồng.

### 3.9 Outbox email - `TransactionalAccountMailSender` + `TransactionalOutboxWorker`

- Producer: `sendTeacherInvite`/`sendRecoveryEmailCode` ghi `outbox_events` trong transaction của người gọi; `idempotencyKey = recipient:SHA256(secret)`; tenant lấy từ `TenantContext` hoặc `"erg"`; secret mã hóa bằng `OutboxPayloadCipher` (`TransactionalAccountMailSender.java:14-36`).
- Worker (`TransactionalOutboxWorker.java:40-97`): mỗi 5s (cấu hình `erg.outbox.worker.fixed-delay-ms`, bật bởi `erg.outbox.worker.enabled`, mặc định true; profile test tắt), claim tối đa 50 dòng (`pending/retry` đến hạn hoặc `processing` có `lease_until < now`), lease 60s, rồi gửi từng dòng tuần tự qua `SmtpAccountMailSender`.
- State machine `outbox_events.status`: `pending -> processing -> sent`; lỗi: `attempts+1`, nếu `attempts >= 8` -> `dead`, ngược lại `retry` với `availableAt = now + min(300, 2^min(8,attempts))` giây. Mọi lỗi RuntimeException đều tính là retry, kể cả event type không hỗ trợ `:74`.
- Tài liệu vận hành: `docs/runbooks/outbox-cutover.md`, `docs/runbooks/outbox-recovery.md` (có tồn tại; không đọc nội dung trong lượt này).

## 4. Quy tắc nghiệp vụ, validation, phân quyền

- **Phân quyền REST** nằm ở `P/platform/security/SecurityConfig.java:187-217`:
  - `/api/v1/audit/**` và `/api/v1/operations/**` cần `operations.manage` (`:187-188`); thêm `@AuditRead` (`audit.read` hoặc `audit.manage`) / `@AuditExport` (`audit.export` hoặc `audit.manage`) ở method `AuditController.java:40,58`. Cần cả hai lớp.
  - Inbox/preference tự phục vụ: `lms.notification.read` hoặc `profile.self` hoặc `*` (`:194-203`); `PUT /preferences` cần `lms.notification.preferences.update` hoặc `profile.self` hoặc `*` (`:204-205`).
  - `/api/v1/notification-devices/**` (POST, unregister, heartbeat, DELETE) cần `profile.self` (`:207-211`).
  - `/api/v1/admin/notifications/**` cần `notifications.manage` (`:213-214`); route còn lại dưới `/api/v1/notifications/**` cũng cần `notifications.manage` (`:216-217`).
  - `NotificationAdminController` không có `@PreAuthorize`; phân quyền hoàn toàn dựa vào SecurityConfig.
- **Cách ly tenant/người dùng**: controller lấy `tenantId`/`userId` từ `ErgPrincipal` (`NotificationSecuritySupport.principal()` `:13-19`), không nhận từ client. Portal: mặc định `lms`, lower-case, phải nằm trong `principal.portals()` hoặc có `*` (`:21-27`); sai -> `ForbiddenException`.
- **Admin send** (`AdminSendRequest`, `P/common/request/notifications/NotificationRequests.java:66-80`): `eventType` bắt buộc, `title` <= 180, `bodyPreview` <= 500; không whitelist `eventType`, `portal`, `priority`; `actionUrl` không kiểm tra định dạng.
- **Device** `RegisterDeviceRequest`: `portal` và `platform` `@NotBlank`; `permissionState` mặc định `granted`; `unregister`/`heartbeat` không có `@Valid`.
- **Audit validation** `AuditQuery.java:16-25`: `page >= 0`, `size` 1..1000 (controller mặc định 100), `from <= to`; `outcome` ngoài `SUCCESS|FAILURE|DENIED|PARTIAL` -> 400 (`AuditController.java:97-104`); format export chỉ `json|csv`.
- **Redaction** `AuditRedactionPolicy.java:35-75`: key chứa `password, token, secret, otp, authorization, cookie, credential, privatekey, api_key, accesskey, refresh` -> `[REDACTED]`; key `email, *phone*, *mobile*, ip, *ipaddress*, *useragent*, address` -> `sha256:<hex>`; tối đa 64 entry/map, 100 phần tử/list, 2048 ký tự/string, độ sâu 6.
- **AuditRecord** bắt buộc `tenantId`, `action`, `resourceType`; `outcome` mặc định SUCCESS (`AuditRecord.java:25-31`).
- **Append-only**: trigger DB chặn UPDATE/DELETE trừ DELETE khi GUC `app.audit_retention_job=true` (V93); RLS FORCE theo `app.tenant_id` hoặc `app.maintenance_mode`.
- **Mã hóa token thiết bị**: AES-GCM 12 byte nonce, tag 128, tiền tố `enc:v1:`; nếu không cấu hình key thì lưu plaintext, trừ khi `registrationTokenEncryptionRequired=true` (khi đó khởi động lỗi nếu thiếu key và từ chối giải mã plaintext) (`NotificationTokenCipher.java:32-88`).
- **Authorization audit**: bắt buộc có actor (`requiredActor`); xử lý theo lô 500 user/tenant, khóa `authzVersion`, tăng version, ghi `user_activities`, `security_events` và một audit event cho mỗi lô (`AuthorizationMutationAuditService.java:50-93`); user thiếu -> `ResourceNotFoundException`.

## 5. API

### REST (không có GraphQL)

| Method | Path | Controller#method | Mô tả |
|---|---|---|---|
| GET | `/api/v1/notifications` | NotificationController#list | Inbox phân trang (`portal`, `status`, `page`=0, `size`=20) |
| GET | `/api/v1/notifications/unread-count` | #unreadCount | Số chưa đọc (chưa archive) |
| GET | `/api/v1/notifications/{id}` | #get | Chi tiết (id là recipientId hoặc notificationId) |
| PATCH | `/api/v1/notifications/read-all` | #readAll | Đánh dấu đã đọc tất cả |
| PATCH | `/api/v1/notifications/{id}/read` | #markRead | Đánh dấu đã đọc một mục |
| DELETE | `/api/v1/notifications/{id}` | #archive | Archive (xóa mềm) |
| GET | `/api/v1/notifications/preferences` | #preferences | Danh sách preference |
| PUT | `/api/v1/notifications/preferences` | #updatePreferences | Upsert preference (body `portal`, `preferences[]`) |
| POST | `/api/v1/notification-devices` | NotificationDeviceController#register | Đăng ký/cập nhật thiết bị |
| POST | `/api/v1/notification-devices/unregister` | #unregister | Thu hồi theo fid hoặc token |
| POST | `/api/v1/notification-devices/heartbeat` | #heartbeat | Cập nhật `lastSeenAt` |
| DELETE | `/api/v1/notification-devices/{deviceId}` | #revoke | Thu hồi thiết bị của mình |
| POST | `/api/v1/admin/notifications/send` và `/broadcast` | NotificationAdminController#send | Tạo thông báo (user và/hoặc topic) |
| GET | `/api/v1/admin/notifications/stats` | #stats | Số outbox `pending/retry/sent/dead` của tenant |
| POST | `/api/v1/admin/notifications/outbox/run` | #runOutbox | Chạy một batch cho tenant |
| POST | `/api/v1/admin/notifications/outbox/replay-dead?limit=100` | #replayDeadLetters | Đưa dead về retry (kẹp 1..500) |
| GET | `/api/v1/audit/events` | AuditController#events | Tìm audit event (lọc + phân trang) |
| GET | `/api/v1/audit/export?format=json|csv` | AuditController#export | Export một trang; CSV tải về `audit-events.csv` |

Tất cả trả `ApiResponse` có `requestId` (trừ CSV). Đường dẫn khác tồn tại ở frontend/contract nằm ngoài slice.

## 6. Dữ liệu

Migration chính: `src/main/resources/db/migration/postgres/` - V10 (toàn bộ bảng notification), V11 (dọn dữ liệu tham chiếu), V15/V16 (FK `NOT VALID`), V19 (cấu hình/hàm cleanup `notification_delivery_attempts`), V66 (`outbox_events`), V80 (index retention), V86 (cột `provider`, `provider_error_*`), V93 (`audit_events`, RLS, trigger, permission `audit.*`).

| Bảng | Điểm đáng nhớ |
|---|---|
| `notification_messages` | Unique `(tenant_id, dedupe_key)` (V10); title <= 180, body <= 500; `payload_data` jsonb; FK `created_by -> users` ON DELETE SET NULL, NOT VALID (V15) |
| `notification_recipients` | FK `notification_id` CASCADE; unique `(notification_id, user_id, portal)`; status `unread|read`, `archived_at`, `delivery_policy` |
| `notification_device_registrations` | Unique `(tenant_id, firebase_installation_id)` và `(tenant_id, registration_token_hash)`; `registration_token` text (mã hóa); `enabled`, `revoked_at`, `last_seen_at` |
| `notification_topic_memberships` | Unique `(device_registration_id, topic_name)`; chỉ là bản ghi trạng thái, không thấy gọi FCM để subscribe (xem mục 9) |
| `notification_preferences` | Unique `(tenant_id, user_id, portal, event_type)` |
| `notification_outbox` | Index pickup `(status, next_attempt_at, created_at)`, `(tenant_id, provider, status, next_attempt_at)`, retention một phần cho `sent/dead`; `target_ref varchar(255)`; không có CHECK status |
| `notification_delivery_attempts` | FK `outbox_id` SET NULL; lưu `target_ref_hash`; retention bằng hàm SQL `cleanup_stale_delivery_attempts()` (V19), cấu hình `notification_retention_config` (90 ngày) - không thấy job Java nào gọi hàm này |
| `outbox_events` | Unique `(tenant_id, idempotency_key)`; CHECK status `pending|processing|retry|sent|dead`; index pickup |
| `audit_events` | Unique `(tenant_id, idempotency_key)`; CHECK `outcome` và `scope_type` (`TENANT|EDUCATION_UNIT|CLASS`); index `(tenant_id, occurred_at DESC, id DESC)`, `(tenant_id, resource_type, resource_id, occurred_at DESC)`; RLS FORCE; trigger append-only |

Cấu hình chính (`src/main/resources/application.yml`): `erg.notifications.push.*` (mặc định `enabled=false`, worker bật, pollSize 200, maxAttempts 8, lease 60s, backoff 10s..60m, `max-payload-bytes` 4096, encryption-required=false), `erg.outbox.worker.*`, `erg.notifications.outbox.retention.*` (90 ngày, batch 1000), `erg.audit.retention.*` (365 ngày, batch 500). Profile test tắt outbox worker và push worker.

Tìm thấy không có RLS trên các bảng `notification_*` / `outbox_events` trong V75/V65 (grep không khớp) - cách ly dựa vào điều kiện `tenant_id` trong repository (nghi ngờ, chưa kiểm toàn bộ V65-V81).

## 7. Tích hợp ngoài và sự kiện

- **FCM HTTP v1** (`https://fcm.googleapis.com`, cấu hình được): OAuth2 service-account JWT bearer (scope `firebase.messaging`) hoặc access token tĩnh; xem 3.3. Không dùng Firebase Admin SDK, tự dùng `java.net.http.HttpClient`.
- **Email SMTP**: `SmtpAccountMailSender` (ngoài slice) được gọi bởi `TransactionalOutboxWorker`.
- **Spring Modulith events**: `CalendarBatchPublished` -> `CalendarBatchPublishedConsumer` (cần publication registry; nếu listener ném lỗi bản ghi publication ở trạng thái chưa hoàn tất); `AuditRecorded` -> `AuditRecordedConsumer` (no-op).
- **Audit hash chain**: mỗi tenant một chuỗi, dùng advisory lock; `previousHash` null cho bản ghi đầu. Không tìm thấy mã verify chuỗi (chỉ có hash khi ghi) - xem 9.
- **Metrics Micrometer**: `notifications.outbox.{pending,retry,dead}` và `notifications.registration.active` (gauge truy vấn DB khi scrape), `notifications.created.count`, `notifications.recipients.count`, `notifications.outbox.created.count` (tag `eventType`, `portal`), `notifications.send.count`/`.latency` (tag `targetKind`, `result`, `errorCode`), `notifications.registration.stale.disabled.count`.
- **GraalVM native**: `FirebaseNotificationRuntimeHints` đăng ký reflection cho `FirebasePushPayload`, `SendResult`, `NotificationPushProperties`.

## 8. Test hiện có

| Test | Kiểm chứng |
|---|---|
| `src/test/.../service/notifications/NotificationCommandServiceTest` | Chặn topic ngoài namespace tenant; chặn key data dành riêng; tạo outbox topic theo tenant |
| `NotificationOutboxWorkerReplayTest` | Replay dead -> retry reset attempt; tenant rỗng không đụng store |
| `NotificationOutboxRetentionJobTest` | Xóa terminal quá hạn; lặp đến batch lẻ; tắt thì bỏ qua |
| `NotificationDeviceMaintenanceServiceTest` | Vô hiệu hóa thiết bị stale |
| `NotificationTokenCipherTest` | Mã hóa/giải mã; từ chối plaintext khi bắt buộc; fail-fast thiếu key |
| `LmsNotificationEventPublisherTest` | Dựng command đúng; nuốt exception (mock service) |
| `notifications/internal/CalendarBatchPublishedConsumerTest` | Cùng idempotency key khi retry; rỗng giáo viên thì bỏ qua (mock `NotificationCommandService`) |
| `notifications/delivery/NotificationDeliveryAdapterRegistryTest` | Trùng/thiếu adapter bị từ chối; key lạ fail-closed |
| `notifications/push/FirebaseHttpV1PushSenderTest` | Payload HTTP v1, Retry-After, UNREGISTERED -> invalid target (HttpServer giả) |
| `notifications/config/FirebaseNotificationConfigurationTest` | Push tắt không tạo HttpClient |
| `NotificationAdminBoundaryTest`, `NotificationBoundaryContractTest` | Ranh giới module (worker chỉ qua registry, calendar không gọi ngược notifications) |
| `audit/AuditWriteServiceTest`, `AuditHashChainTest`, `AuditRedactionPolicyTest`, `AuditQueryServiceTest`, `AuditRetentionServiceTest`, `AuditMigrationContractTest` | Append + hash + publish, idempotent replay/conflict, redaction và giới hạn, lọc tenant/scope, retention có biên, hợp đồng migration và annotation quyền |
| `service/authorization/AuthorizationMutationAuditServiceTest` | Khóa user theo tenant, sự kiện có requestId |
| `service/outbox/TransactionalOutboxWorkerTest`, `TransactionalOutboxMigrationContractTest`, `LegacyOutboxCutoverContractTest` | Claim cross-tenant, lỗi tạm -> retry, schema/runbook |

Khoảng trống rõ ràng (không thấy test): `NotificationService` (inbox/preference/mark read), `NotificationDeviceService` (đặc biệt chiếm thiết bị, re-register chỉ fid), `NotificationOutboxWorker` luồng chính (claim -> sent/failed/dead, disable device), `NotificationController`/`AuditController` ở mức web/security (CSV, 400 outcome), `ServiceAccountAccessTokenProvider`, `TransactionalAccountMailSender`, kiểm tra tích hợp enqueue thật với consumer lịch (test mock nên không bắt được lỗi key dành riêng), test Postgres cho RLS/trigger audit và query `findDueForUpdate`.

## 9. Rủi ro, nợ kỹ thuật, nghi ngờ bug

Mức "xác nhận" = đã đối chiếu code đọc được; "nghi ngờ" = chưa chạy.

- **B1 (xác nhận bằng đọc code, chưa chạy)**: `CalendarBatchPublishedConsumer.java:41` truyền data có key `"eventType"`, nhưng `eventType` nằm trong `RESERVED_DATA_KEYS` (`NotificationCommandService.java:26-35`, kiểm tra tại `:158`) -> `enqueue` luôn ném `BadRequestException`, thông báo lịch xuất bản không bao giờ được tạo. `CalendarBatchPublishedConsumerTest` mock service nên không bắt được.
- **B2**: Preference không được áp dụng khi gửi: `pushEnabled/inboxEnabled/emailEnabled/quietHours` chỉ lưu/đọc trong `NotificationService`; `enqueue` không tra `NotificationPreferenceRepository` (repository `findByTenantIdAndUserIdAndPortalAndEventType` không có caller). Cũng không có kênh email nào trong notification.
- **B3**: Thiết bị chỉ có `firebaseInstallationId` luôn thất bại: `FirebaseHttpV1PushSender.java:42-53` trả `FID_TARGET_UNSUPPORTED` không retry -> outbox thành `dead` (`NotificationOutboxWorker.java:180-183`), sau đó có thể bị replay vô ích.
- **B4 (nghi ngờ)**: `target_ref varchar(255)` (V10) nhưng token mã hóa = `enc:v1:` + base64url(12 + len + 16). Token FCM thường ~140-180 ký tự -> chuỗi mã hóa có thể > 255 -> lỗi insert khi bật mã hóa. Cần kiểm tra độ dài thực tế.
- **B5 (nghi ngờ)**: `NotificationDeviceService.register` `:58-59` ghi `registrationToken`/`hash` bằng giá trị mới (null khi chỉ gửi fid), nên đăng ký lại bằng fid có thể xóa token đã lưu. Ngoài ra nếu gửi cả fid và token mà token thuộc bản ghi khác thì vi phạm unique `(tenant_id, registration_token_hash)` chưa được xử lý.
- **B6**: `INVALID_ARGUMENT` được coi là invalid target -> tắt thiết bị (`FirebaseHttpV1PushSender.java:174-182`, `NotificationOutboxWorker.java:172-175`); lỗi 400 do payload sai (không phải do token) cũng làm vô hiệu hóa thiết bị hợp lệ. Nghi ngờ vì phụ thuộc phản hồi FCM thực.
- **B7**: Idempotency tạo message có race: kiểm tra tồn tại rồi insert (`NotificationCommandService.java:70-95`), hai yêu cầu đồng thời cùng key sẽ vi phạm unique `uq_notification_messages_tenant_dedupe` và ném lỗi DB thô thay vì trả bản cũ.
- **B8**: `LmsNotificationEventPublisher` không có caller trong `src/main/java` (grep); javadoc nói `TEACHER_SCHEDULE_ASSIGNED` "đã cài trong TeachingScheduleCommandService" nhưng không tìm thấy tham chiếu. `ASSIGNMENT_DUE_SOON/OVERDUE` ghi chú là placeholder. Hai hàm dùng `createdBy="system"` -> sẽ vi phạm FK `created_by -> users(id)` (V15, NOT VALID vẫn kiểm với dòng mới). Hàm bắt `Exception` nhưng `enqueue` là `@Transactional` tham gia transaction ngoài; nếu ném RuntimeException thì transaction ngoài có thể bị đánh dấu rollback-only (nghi ngờ, phụ thuộc cách gọi sau này). `Map.of(..., null)` ném NPE khi `assignmentId`/`classId` null.
- **B9**: Cardinality metric: tag `eventType` lấy từ body admin tự do (`NotificationMetrics.created`, `NotificationCommandService.java:140`) -> số label không bị chặn (vi phạm quy tắc AGENTS.md về label cardinality). `errorCode` cũng lấy từ phản hồi provider.
- **B10**: Topic broadcast không tạo `notification_recipients` nên không xuất hiện trong inbox; `syncPortalTopic` chỉ ghi DB, không thấy mã subscribe topic ở phía server (grep `subscribe` chỉ ra membership entity/service), nên việc nhận topic phụ thuộc client đăng ký topic.
- **B11**: Dead-letter không có hàng đợi riêng/cảnh báo; `failed` không tính trong `/stats` và không bị retention dọn; `processing` không có trong stats. Outbox có thể bị gửi trùng (at-least-once) nếu worker chết sau khi FCM nhận (lease 60s) hoặc batch 200 dòng gửi tuần tự lâu hơn lease (gửi ngoài transaction, chưa gia hạn lease).
- **B12**: Cấu hình chết: `defaultTtl`, `senderMode`, `nativeImageStrict`, `credentials.mode`, `collapseKey` (luôn null, `NotificationMessageEntity.collapseKey` không được set), `priority` không ảnh hưởng FCM - chỉ thấy trong `NotificationPushProperties`/entity (grep không có caller).
- **B13**: `OutboxPayloadCipher` mặc định khóa `development-only-change-me` nếu không đặt `ERG_OUTBOX_ENCRYPTION_KEY`/`SECRET_OUTBOX__KEY` (`OutboxPayloadCipher.java:22-24`); không có kiểm tra bắt buộc ở production. Secret đã mã hóa nhưng khóa mặc định công khai trong code.
- **B14**: `TransactionalOutboxWorker` coi mọi lỗi (kể cả event type không hỗ trợ `:74`) là retryable tới 8 lần; gửi 50 mail tuần tự trong khi lease 60s (rủi ro gửi trùng email chứa mã). `transition` bỏ qua dòng nếu `findById` rỗng.
- **B15**: Audit - `legal_hold` không thể bật: trigger V93 chặn mọi UPDATE (chỉ cho DELETE của job retention), trong khi `AuditEvent.legalHold` không `updatable=false` và retention bỏ qua `legal_hold=true`; không thấy API/đường nào đặt `legal_hold`. Quyền `audit.manage` mô tả "retention và legal holds" nhưng không có endpoint tương ứng.
- **B16 (nghi ngờ)**: Hash chain sắp theo `occurredAt desc, id desc` (`AuditEventRepository.findTopByTenantIdOrderByOccurredAtDescIdDesc`) trong khi `occurredAt` có thể do caller truyền (`AuditRecord.occurredAt`); bản ghi lùi thời gian sẽ lấy sai `previousHash` (nhánh chuỗi). Ngoài ra hash tính từ `Instant.toString()` trước khi lưu; Postgres cắt về micro giây nên nếu Clock cho nano giây, hash tái tính từ DB có thể lệch. Không thấy công cụ xác minh chuỗi.
- **B17**: Retention audit mỗi giờ chỉ xóa 500 bản ghi (`AuditRetentionService`), tối đa ~12.000/ngày; có thể tồn đọng nếu lưu lượng audit lớn. Xóa bản ghi cũ cũng làm chuỗi không còn bắt đầu từ null.
- **B18**: `AuthorizationMutationAuditService.idempotencyKey` (`:208-222`) dùng `String.hashCode()` 32 bit; va chạm cùng action/resource/outcome sẽ trả bản cũ im lặng (mất audit), khác ý định sẽ `ConflictException`. Khi thiếu `requestId` hai thay đổi giống hệt nhau sẽ bị gộp thành một audit. Audit nằm trên `postgresTransactionManager` còn service này dùng `@Transactional` mặc định (nghi ngờ: cần xác nhận hai transaction manager có phải cùng DataSource/giao dịch).
- **B19**: CSV export (`AuditController.java:144-147`) chỉ escape dấu nháy và xuống dòng, không vô hiệu hóa công thức bắt đầu bằng `= + - @` (CSV injection khi mở bằng Excel). Export chỉ một trang tối đa 1000 dòng; `details` ghi cả `format: "json-or-csv"` cố định.
- **B20**: Hai cổng quyền chồng nhau cho audit (`operations.manage` ở SecurityConfig và `audit.read`/`audit.manage` ở method) - người chỉ có `audit.read` bị chặn ở lớp trên. Có thể là chủ ý, nhưng không có ghi chú.
- **B21**: `GET /api/v1/notifications/{id}` khai báo trước `/preferences` trong SecurityConfig; hiện cùng bộ quyền nên vô hại, nhưng sẽ sai nếu hai quyền khác nhau.
- **B22**: `NotificationOutboxWorker.send` giải mã token trong adapter; lỗi giải mã (đổi khóa, mất khóa) trả `TARGET_DECRYPT_FAILED` không retry -> `dead` hàng loạt (`FirebaseNotificationDeliveryAdapter.java:51-53`). Thông báo lỗi thô (`exception.getMessage()`) được lưu vào `last_error_message` - cần đảm bảo không chứa token.
- Model legacy (`Notification`, `Digest`, `DeliveryLog`, `NotificationPreference`, `WebhookPref`, `AuditLog`) là POJO còn sót từ erg-go, không có entity/bảng tương ứng.

## 10. Câu hỏi còn mở

1. Ai sẽ gọi `LmsNotificationEventPublisher` và `ASSIGNMENT_DUE_SOON/OVERDUE`? Có job lập lịch dự kiến không? `createdBy="system"` xử lý FK thế nào?
2. Có chủ ý để preference (push/inbox/email/quiet hours) chưa áp dụng khi gửi không, hay thiếu tính năng?
3. Có kế hoạch hỗ trợ FID hay loại bỏ gửi theo FID, và dọn các dòng outbox `dead` do FID?
4. Có công cụ/job xác minh hash chain audit và cơ chế đặt `legal_hold` (kể cả `audit.manage`) ở đâu?
5. Việc subscribe thiết bị vào topic FCM do client hay server làm? Bảng `notification_topic_memberships` dùng để làm gì ngoài ghi nhận?
6. Hàm SQL `cleanup_stale_delivery_attempts()` (V19) có được lịch DB gọi không (không thấy job Java)?
7. Các bảng `notification_*` và `outbox_events` có cần RLS như `audit_events` không?
8. Khóa mã hóa `OutboxPayloadCipher` và `registrationTokenEncryptionKey` ở production có được bắt buộc cấu hình ở lớp triển khai không?
9. Hai tầng quyền cho audit (`operations.manage` + `audit.*`) có chủ ý không?
