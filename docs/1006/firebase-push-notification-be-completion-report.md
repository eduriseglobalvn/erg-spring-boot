    # Báo cáo hoàn thành BE - Firebase Push Notification

Ngày: 2026-06-10, Asia/Saigon

Phạm vi: `D:\ERG\erg-spring`

## 1. Kết luận

Phần BE nền tảng cho Firebase push notification đã được triển khai đến mức có thể bắt đầu tích hợp FE PWA và chạy staging smoke. Hệ thống đã có persistence, self/device/admin API, Firebase HTTP v1 sender, outbox worker, security boundary, token encryption opt-in, stale cleanup, metrics nền và test coverage trọng điểm.

Phần chưa làm không còn là blocker để FE bắt đầu nối API, nhưng vẫn là blocker trước production traffic thật: Postgres concurrency test, native smoke CI, topic sync Firebase thật, retention delivery attempts, provider-level rate limiter và hook event LMS thật.

## 2. Những phần đã hoàn thành

### Persistence

- Thêm migration `V10__firebase_push_notifications.sql`.
- Thêm bảng:
  - `notification_messages`
  - `notification_recipients`
  - `notification_device_registrations`
  - `notification_outbox`
  - `notification_delivery_attempts`
  - `notification_topic_memberships`
  - `notification_preferences`
- Thêm entity/repository tương ứng.
- Cập nhật JPA scan trong `PostgresJpaConfig`.

### API

- `GET /api/v1/notifications`
- `GET /api/v1/notifications/unread-count`
- `GET /api/v1/notifications/{id}`
- `PATCH /api/v1/notifications/{id}/read`
- `PATCH /api/v1/notifications/read-all`
- `DELETE /api/v1/notifications/{id}`
- `GET/PUT /api/v1/notifications/preferences`
- `POST /api/v1/notification-devices`
- `POST /api/v1/notification-devices/unregister`
- `POST /api/v1/notification-devices/heartbeat`
- `DELETE /api/v1/notification-devices/{deviceId}`
- `POST /api/v1/admin/notifications/send`
- `GET /api/v1/admin/notifications/stats`
- `POST /api/v1/admin/notifications/outbox/run`

### Security

- Self API chỉ cần authenticated, không cần `notifications.manage`.
- Admin API cần `notifications.manage` và portal `admin`.
- Self detail/read/archive đã scope theo `tenantId + userId + portal`.
- Device heartbeat/unregister/revoke đã scope theo portal.
- Device register không rebind active device của user khác.
- Admin stats và manual outbox run đã tenant-scoped.
- Admin topic bắt buộc prefix tenant namespace `t_{tenant}_...`.
- Admin payload chặn reserved keys như `tenantId`, `notificationId`, `portal`, `deviceRegistrationId`.

### Firebase/native-image

- Không thêm `firebase-admin`.
- Sender mặc định là `FirebaseHttpV1PushSender`.
- Dùng `AccessTokenProvider` tách credential.
- Hỗ trợ static access token cho smoke/dev.
- Hỗ trợ service account file qua `GOOGLE_APPLICATION_CREDENTIALS`.
- Nếu thiếu credential, app không crash; sender trả `ACCESS_TOKEN_MISSING`.
- Có RuntimeHints cho DTO/properties liên quan và đã bỏ `DECLARED_FIELDS` deprecated.

### Outbox worker

- Claim batch bằng transaction ngắn.
- Set `status=processing`, `locked_by`, `locked_at`.
- Gửi FCM ngoài transaction claim.
- Ghi result bằng transaction riêng.
- Có `FOR UPDATE SKIP LOCKED`.
- Có recovery cho `processing` quá lease timeout.
- Có exponential backoff, `Retry-After` và jitter.
- Invalid target disable device nếu có `deviceRegistrationId`.

### Token protection

- Thêm `NotificationTokenCipher`.
- Khi cấu hình `NOTIFICATIONS__PUSH__REGISTRATION_TOKEN_ENCRYPTION_KEY`, token được lưu dạng `enc:v1:*`.
- Worker decrypt token trước khi gọi FCM.
- Có flag `NOTIFICATIONS__PUSH__REGISTRATION_TOKEN_ENCRYPTION_REQUIRED=true` để fail-fast production.
- Vẫn đọc plaintext cũ nếu chưa bật required để hỗ trợ migration dần.

### Maintenance và metrics

- Thêm stale cleanup job theo `erg.notifications.push.stale-registration-age`, mặc định 720h.
- Thêm Micrometer metrics:
  - `notifications.outbox.pending`
  - `notifications.outbox.retry`
  - `notifications.outbox.dead`
  - `notifications.registration.active`
  - `notifications.created.count`
  - `notifications.recipients.count`
  - `notifications.outbox.created.count`
  - `notifications.send.count`
  - `notifications.send.latency`
  - `notifications.registration.stale.disabled.count`

## 3. Test đã chạy

```powershell
.\mvnw.cmd "-Dtest=Notification*Test,FirebaseHttpV1PushSenderTest,AccessControlSecurityTest" test
```

Kết quả:

- 29 tests run.
- 0 failures.
- 0 errors.

Các test chính:

- `AccessControlSecurityTest`
- `NotificationCommandServiceTest`
- `NotificationDeviceServiceTest`
- `NotificationDeviceMaintenanceServiceTest`
- `NotificationTokenCipherTest`
- `FirebaseHttpV1PushSenderTest`

Test coverage đã có:

- Route self/admin notification.
- Portal rejection.
- Tenant-scoped topic guard.
- Reserved payload key guard.
- Device rebind guard.
- Device portal mismatch guard.
- Token encrypt/decrypt/fail-fast.
- Fake FCM HTTP v1 success.
- Fake FCM 429 `Retry-After`.
- Fake FCM invalid target.
- Stale cleanup.

## 4. Còn chưa làm

Các mục này chưa làm vì cần môi trường staging/production-like hoặc sprint domain riêng:

- Postgres/Testcontainers test cho `FOR UPDATE SKIP LOCKED` và lease recovery thật.
- Native image compile/smoke trong CI.
- Topic subscribe/unsubscribe thật với Firebase; hiện mới lưu local membership.
- Provider-level rate limiter và delivery window cho broadcast lớn.
- Retention/partition cleanup cho `notification_delivery_attempts`.
- Hook event nghiệp vụ thật:
  - `ATTEMPT_SUBMITTED`
  - `ASSIGNMENT_CREATED`
  - `TEACHER_SCHEDULE_ASSIGNED/UPDATED/CANCELLED`
  - `ASSIGNMENT_DUE_SOON/OVERDUE`
  - `SUPPORT_NEEDED_*`
- Admin cancel/resend/detail delivery attempts.
- Redis cache unread count.
- Announcement push, vì `Announcement` hiện chưa có persistence thật.

## 5. Khuyến nghị bước tiếp theo

Thứ tự nên làm:

1. FE PWA nối device registration và inbox API.
2. Chạy staging với `NOTIFICATIONS__PUSH__ENABLED=true`, `FIREBASE_PROJECT_ID`, `GOOGLE_APPLICATION_CREDENTIALS`.
3. Bật `NOTIFICATIONS__PUSH__REGISTRATION_TOKEN_ENCRYPTION_KEY` ở staging.
4. Thêm native smoke profile/CI.
5. Thêm Postgres lock integration test.
6. Hook `ATTEMPT_SUBMITTED` sau commit làm event nghiệp vụ đầu tiên.
7. Làm topic sync Firebase thật cho broadcast theo tenant/portal/class/role.

## 6. Trạng thái production readiness

| Hạng mục | Trạng thái |
| --- | --- |
| Schema/inbox/outbox | Xong nền |
| Security self/admin/device | Xong |
| Firebase HTTP v1 sender | Xong nền |
| Native-image strategy | Xong thiết kế, chưa CI smoke |
| Token at-rest protection | Xong opt-in |
| Retry/lease/jitter | Xong nền |
| Metrics nền | Xong |
| Stale cleanup | Xong |
| Postgres concurrency proof | Chưa |
| Topic sync Firebase thật | Chưa |
| Event LMS thật | Chưa |
| Load test 10k/5k | Chưa |

## 7. Kết luận vận hành

BE hiện đủ để FE bắt đầu tích hợp PWA notification theo contract mới và đủ an toàn để chạy staging smoke. Chưa nên bật production traffic thật cho toàn trường/lớp cho đến khi có Postgres concurrency test, native smoke CI, topic sync thật, rate limiter và retention delivery log.
