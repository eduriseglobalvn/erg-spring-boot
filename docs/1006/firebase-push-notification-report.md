# Report - Firebase Push Notification cho ERG

Ngày cập nhật: 2026-06-10, Asia/Saigon

Source: `D:\ERG\erg-spring`, đối chiếu định hướng FE/PWA/Tauri.

Mục tiêu: đánh giá lợi ích, điểm đã cải thiện, điểm cần khắc phục và rủi ro còn lại khi dùng Google Firebase Cloud Messaging cho ERG.

## 1. Executive summary

ERG nên dùng Firebase Cloud Messaging để giao push notification, nhưng không nên coi Firebase là hệ thống notification chính. Thiết kế đúng là:

- PostgreSQL giữ inbox, read-state, device registry, topic membership, outbox, audit, retry.
- Firebase chỉ giao thông điệp đến thiết bị.
- FE/PWA/Tauri chỉ nhận signal và fetch detail từ BE.

Backend hiện đã đi qua bước nền quan trọng:

- Có schema V10 cho notification.
- Có self/device/admin API tách quyền.
- Có HTTP v1 sender native-image friendly.
- Có transactional outbox worker với lease recovery.
- Có security hardening cho tenant/portal/topic/device.
- Có test mới cho security và service guards.

Module đã hoàn tất phần BE nền tảng và nhiều hardening quan trọng: token encryption opt-in, fake FCM sender tests, metrics nền, stale cleanup và retry jitter. Các phần còn lại trước production là Postgres lock test, retention delivery attempts, provider-level rate limiter, topic sync Firebase thật, native smoke CI và hook event LMS thật.

## 2. Lợi ích chính khi dùng Firebase/FCM

### 2.1 Giảm gánh tự vận hành push infrastructure

Nếu tự vận hành Web Push trực tiếp, BE phải xử lý VAPID, browser push service, invalid subscription, retry và quota từng nền tảng. FCM giúp gom phần giao vận này vào hạ tầng Google và mở đường cho Android/iOS/Tauri adapter sau này.

### 2.2 Phù hợp broadcast quy mô trường/lớp/role

FCM topic phù hợp cho:

- Thông báo toàn giáo viên.
- Thông báo toàn học sinh.
- Thông báo theo tenant/portal.
- Thông báo theo trường/lớp/role.
- Lịch học hoặc thông báo chung không chứa dữ liệu nhạy cảm.

Điểm cần nhớ: topic không thay thế inbox và không dùng cho dữ liệu riêng tư theo user.

### 2.3 Vẫn hỗ trợ cá nhân hóa

Các case cá nhân như bài làm của học sinh, học sinh cần hỗ trợ, bài tập mới, ca dạy bị đổi nên đi direct target. BE tạo inbox record trước, push chỉ chứa `notificationId`, `eventType`, `sourceId`, `deeplink`. Khi user mở thông báo, client fetch detail từ BE.

### 2.4 Hợp với PWA hiện tại và Tauri sau này

Vì BE đã tách inbox/read-state khỏi Firebase, Tauri desktop sau này có thể dùng adapter riêng mà không cần thay đổi notification domain. PWA dùng Firebase Messaging trước; Tauri có thể dùng native notification/polling/WebSocket tùy giai đoạn.

### 2.5 Vận hành được ở quy mô 10.000 user

Với 10.000 user và 5.000 concurrent:

- Topic giảm request fanout cho broadcast.
- Outbox tránh mất notification khi FCM lỗi.
- `SKIP LOCKED` cho phép nhiều worker scale ngang.
- Index inbox theo `tenant_id, user_id, portal, status, created_at` đủ cho read path.
- Push disabled kill switch giúp dừng provider mà không tắt LMS.

## 3. Điểm tốt đã có trong BE hiện tại

### 3.1 Persistence nền

Đã có migration:

- `notification_messages`
- `notification_recipients`
- `notification_device_registrations`
- `notification_outbox`
- `notification_delivery_attempts`
- `notification_topic_memberships`
- `notification_preferences`

Điểm tốt:

- DB là source of truth.
- Có dedupe key.
- Có indexes cho inbox, device, outbox pickup.
- Có delivery attempts để audit.

### 3.2 API boundary đúng hơn

Đã tách:

- `/api/v1/notifications/**`: self API, authenticated.
- `/api/v1/notification-devices/**`: device API, authenticated.
- `/api/v1/admin/notifications/**`: admin API, cần `notifications.manage`.

Điểm vừa khắc phục:

- Self detail/read/archive đã scope theo portal.
- Device heartbeat/unregister/revoke đã scope theo portal.
- Admin stats/manual outbox run đã scope theo tenant.
- Topic raw cross-tenant bị chặn.
- Device active của user khác không bị rebind âm thầm.

### 3.3 Native-image friendly

Đã chọn HTTP v1 sender thay vì thêm `firebase-admin`.

Điểm tốt:

- Không kéo Firebase Admin SDK vào native production path.
- Có `FirebasePushSender` interface.
- App không crash khi chưa có credential; sender trả lỗi retryable.
- RuntimeHints đã tránh `MemberCategory.DECLARED_FIELDS` deprecated.

### 3.4 Worker không giữ DB lock trong lúc gọi mạng

Worker đã chuyển sang:

- Claim trong transaction ngắn.
- Set `processing`, `locked_by`, `locked_at`.
- Gửi FCM ngoài transaction claim.
- Ghi result bằng transaction riêng.
- Re-pick `processing` quá lease timeout.

Đây là điểm quan trọng cho scale và reliability.

## 4. Điểm cần khắc phục trước production

### 4.1 Postgres integration test cho `SKIP LOCKED`

H2 không chứng minh được behavior lock production.

Cần Testcontainers/Postgres test:

- Hai worker không claim cùng outbox.
- Stale `processing` được pick lại sau lease timeout.
- Tenant manual run không pick tenant khác.
- JSONB payload mapping ổn.

Mức ưu tiên: Must fix trước production.

### 4.2 Provider-level rate limiter và delivery window

Hiện backoff đã có exponential, `Retry-After` và jitter, nhưng chưa có rate limiter theo Firebase project hoặc delivery window cho broadcast lớn.

Mức ưu tiên: Should fix trước load test.

### 4.3 Topic membership mới lưu local

Hiện `NotificationDeviceService` tạo local topic membership theo portal, nhưng chưa gọi Firebase subscribe/unsubscribe topic.

Cần thêm:

- Topic sync service.
- Async topic subscribe/unsubscribe worker.
- Batch tối đa 1.000 app instances/request.
- Retry và audit topic sync error.

Mức ưu tiên: Should fix cho broadcast production.

### 4.4 Chưa hook event LMS thật

Nền notification đã có `NotificationCommandService`, nhưng chưa nối event thật:

- Assignment created/published.
- Attempt submitted.
- WorkShift assigned/updated/cancelled.
- Due soon/overdue.
- Support-needed low score/missing.

Khuyến nghị:

- Hook ở command service hoặc `@TransactionalEventListener(AFTER_COMMIT)`.
- Không hook entity listener.
- `Announcement` chưa production vì chưa có JPA/table thật.

Mức ưu tiên: Theo roadmap nghiệp vụ.

### 4.5 Native smoke chưa có trong CI

Cần thêm pipeline:

```powershell
.\mvnw.cmd -Pnative -DskipTests native:compile
.\target\erg-spring.exe --spring.profiles.active=native-smoke
```

Smoke cần cover:

- Push disabled.
- Push enabled nhưng không credential, app không crash.
- HTTP v1 sender staging.
- Invalid target cleanup.

Mức ưu tiên: Must fix trước khi chọn native artifact làm release chính.

### 4.6 Retention delivery attempts

`notification_delivery_attempts` sẽ lớn nhanh khi chạy thật. Cần retention/partition hoặc cleanup policy theo số ngày vận hành.

Mức ưu tiên: Must fix trước production traffic thật.

## 5. Rủi ro và giảm thiểu

| Rủi ro | Tác động | Giảm thiểu |
| --- | --- | --- |
| Firebase 429 khi broadcast lớn | Push bị delay | Topic, delivery window, rate limiter, `Retry-After`, jitter |
| Gửi trùng do retry/event replay | User nhận spam | `dedupe_key`, unique index, idempotency key |
| Token stale | Tốn quota, metric xấu | Heartbeat, stale cleanup, invalid target deactivate |
| Payload chứa dữ liệu nhạy cảm | Privacy issue | Payload chỉ chứa ID/deeplink, detail fetch từ BE |
| Cross-tenant topic | Tenant A gửi nhầm Tenant B | Topic namespace `t_{tenant}_...`, reject raw topic |
| Portal leak | User LMS đọc notification portal khác | Service query theo `tenant + user + portal` |
| Worker chết giữa chừng | Outbox kẹt | `processing` lease timeout và recovery |
| Native image fail vì SDK | Không release native được | HTTP v1 default, Firebase Admin SDK optional sau smoke |
| Registration token plaintext | Secret leakage nếu DB lộ | Encryption at rest, hash lookup, log masking |

## 6. Test đã chạy

Đã chạy thành công:

```powershell
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd "-Dtest=NotificationDeviceServiceTest,NotificationCommandServiceTest,AccessControlSecurityTest" test
```

Kết quả nhóm test mới:

- 22 tests run.
- 0 failures.
- 0 errors.

Coverage thêm:

- Self notification route không cần `notifications.manage`.
- Portal ngoài session bị reject.
- Admin route cần `notifications.manage` và portal `admin`.
- Topic ngoài tenant namespace bị reject.
- Reserved payload key bị reject.
- Active device của user khác không bị rebind.
- Heartbeat không chạm device khác portal.

## 7. Khuyến nghị ưu tiên tiếp theo

1. Thêm Testcontainers/Postgres test cho outbox lock/lease.
2. Thêm retention/partition policy cho `notification_delivery_attempts`.
3. Thêm provider-level rate limiter và delivery window.
4. Thêm topic sync service với Firebase.
5. Nối event `ATTEMPT_SUBMITTED` trước vì source thật đã rõ, sau đó mới đến assignment/workshift.
6. Thêm native smoke profile và CI gate.
7. Bổ sung alerts Prometheus/Grafana cho các metrics đã có.

## 8. Definition of Done tổng

Hệ thống đạt DoD khi:

- Teacher/student bật/tắt notification trên PWA được.
- Device register/unregister idempotent.
- User chỉ thấy notification của mình, đúng tenant và portal.
- Admin không thấy hoặc chạy outbox tenant khác.
- Push fail không làm LMS transaction fail.
- Worker retry đúng, invalid target bị disable.
- Broadcast dùng tenant-scoped topic.
- Load test đạt 10.000 user/5.000 concurrent.
- Native image build/boot/gửi staging push thành công.
- Có metrics, alerts, runbook và kill switch.
