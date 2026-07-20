# Báo cáo các mục chưa hoàn thành — Remaining Gaps

**Ngày cập nhật:** 2026-06-15  
**Branch:** `codex/lms-graphql-security-audit-plan`  
**Phạm vi:** Tổng hợp từ 5 plan tại `docs/0806/`, `docs/1006/`, `docs/1406/`

---

## Mục lục

- [P0 — Bắt buộc trước production](#p0--bắt-buộc-trước-production)
- [P1 — Cần làm trước scale](#p1--cần-làm-trước-scale)
- [P2 — Hardening / V2](#p2--hardening--v2)
- [Tổng quan thống kê](#tổng-quan-thống-kê)

---

## P0 — Bắt buộc trước production

| # | Item | Plan gốc | Files liên quan | Ghi chú |
|---|------|----------|----------------|---------|
| 1 | **Build compile verification** | Teaching Calendar + tất cả | Toàn bộ codebase | Chưa từng chạy `mvnw test-compile` để biết code mới (calendar package, restructure) có compile pass không |
| 2 | **questionBank PostgreSQL proof** | GraphQL Enterprise §3.1 — `lcms.questionBankWorkspace` | `service/lcms/readmodel/LcmsWorkspaceReadService.java`, `repository/lcms/projection/QuestionUsageCountProjection.java` | JSONB/lateral usage query chỉ chạy trên PostgreSQL. H2 skip. Cần Testcontainers + EXPLAIN ANALYZE |
| 3 | **Wire notification thật vào domain service** | Push Notif BE §9 — Event Catalog V1 | `service/notifications/LmsNotificationEventPublisher.java`, `service/lms/LmsRuntimeService.java` | File publisher đã có nhưng chưa được gọi từ `LmsRuntimeService.submitAttempt()`. Worktree tạo stub — không phải thật |
| 4 | **Copy worktree files về source chính** | GraphQL Remaining + Push Notif BE | `TeachingScheduleSecurityTest.java`, `QuestionBankWorkspaceQueryCountIntegrationTest.java`, `LmsNotificationEventPublisherTest.java` | Workflow tạo 3 file real trong worktrees nhưng chưa copy về `src/` chính |
| 5 | **Xóa stub file sai package** | — | `service/lms/LmsNotificationEventPublisher.java` | Worktree tạo file stub sai vị trí — cần xoá và wire đúng `service/notifications/LmsNotificationEventPublisher` |

---

## P1 — Cần làm trước scale

| # | Item | Plan gốc | Files liên quan | Ghi chú |
|---|------|----------|----------------|---------|
| 6 | **TeachingScheduleSecurityTest (real)** | GraphQL Remaining §3.6 — Field-level security matrix | `platform/security/TeachingScheduleSecurityTest.java` | Cần @SpringBootTest + MockMvc. Worktree có file real nhưng chưa copy |
| 7 | **questionBank H2 query-count test** | GraphQL Enterprise §3.1 | `service/lcms/readmodel/QuestionBankWorkspaceQueryCountIntegrationTest.java` | Worktree có file real nhưng chưa copy. Verify query bounded cho non-JSONB paths |
| 8 | **Calendar tests trong package mới** | Teaching Calendar | `service/calendar/*`, `controller/calendar/*`, `repository/calendar/*` | Đã xóa test cũ ở `lms/schedule/` nhưng chưa viết test mới ở `calendar/` package (trừ RestControllerTest) |
| 9 | **Distributed Redis rate limit** | GraphQL Enterprise §4.1 | `platform/graphql/GraphQlRateLimiter.java`, `config/CacheConfig.java` | Chỉ có in-memory limiter. Multi-instance bypass được. Cần gateway hoặc Redis |
| 10 | **Distributed cache + stampede guard** | GraphQL Enterprise §4.2 | `config/CacheConfig.java`, `service/**/readmodel/*` | Cache key chưa có tenant/user scope đầy đủ. Caffeine local chưa có single-flight cho hot key |
| 11 | **Slow-query/JDBC timeout smoke** | GraphQL Enterprise §4.3 | `config/persistence/PostgresJpaConfig.java`, `application.yml` | Config đã có `hibernate.jdbc.timeout` nhưng chưa có test chứng minh nó abort query thật trên DB |
| 12 | **Postgres/Testcontainers SKIP LOCKED test** | Push Notif BE — §4.3 must-fix | `repository/notifications/NotificationOutboxRepository.java`, `service/notifications/NotificationOutboxWorker.java` | Outbox worker dùng `FOR UPDATE SKIP LOCKED` — H2 không chứng minh được locking behavior |
| 13 | **LmsNotificationEventPublisherTest** | Push Notif BE §9 | `service/notifications/LmsNotificationEventPublisherTest.java` | Worktree tạo file (15 tests) nhưng chưa copy. Verify: 5 event types, empty-list guard, exception safety |
| 14 | **V19 retention scheduled job** | Push Notif BE §4.3 must-fix | `resources/db/migration/postgres/V19__notification_retention_policy.sql`, `service/notifications/NotificationDeviceMaintenanceService.java` | V19 SQL function + cron đã có. Nhưng chưa có Java scheduled job gọi `cleanup_stale_delivery_attempts()` định kỳ |

---

## P2 — Hardening / V2

| # | Item | Plan gốc | Files liên quan | Ghi chú |
|---|------|----------|----------------|---------|
| 15 | **Native/AOT smoke build** | GraphQL Enterprise §5.1 | `pom.xml` (native-maven-plugin), GraphQL schema, Jackson DTO, JPA entities | Chưa build `mvn -Pnative native:compile`. Chưa kiểm tra reflective access |
| 16 | **Native profile `native-smoke`** | Push Notif BE §6 | `src/main/resources/application-native-smoke.yml` (chưa tạo) | Cần profile boot với HTTP v1 sender, không credential, không crash |
| 17 | **Load/soak 100 → 5.000 concurrency** | GraphQL Enterprise §4.4 | All GraphQL workspace + REST endpoints | Chưa chạy k6/Gatling. Cần: p95/p99, Hikari metrics, GC, query-count monitoring |
| 18 | **Redis cache cho unread count** | Push Notif BE §12 | `controller/notifications/NotificationController.java`, `service/notifications/NotificationService.java` | Unread count đang query DB mỗi request (~1.000 RPS target) |
| 19 | **Provider-level rate limiter (FCM)** | Push Notif BE §12 | `notifications/push/FirebaseHttpV1PushSender.java`, `service/notifications/NotificationOutboxWorker.java` | FCM token bucket quota (600K/min). Worker không throttle trước khi gửi |
| 20 | **Topic subscribe/unsubscribe Firebase backend** | Push Notif BE §12 | `notifications/push/FirebasePushSender.java`, `service/notifications/NotificationTopicMembershipRepository.java` | Membership local-only. Chưa sync với Firebase HTTP v1 topic management API |
| 21 | **Admin cancel/resend delivery attempts** | Push Notif BE §12 | `controller/notifications/NotificationAdminController.java` | Chỉ có send/stats/outbox-run. Thiếu cancel pending, resend dead, delivery detail |
| 22 | **Batch/chunk direct token sends** | Push Notif BE §12 | `service/notifications/NotificationOutboxWorker.java` | Worker xử lý 1 outbox record/lần. Không batch cho multicast |
| 23 | **Student calendar view** | Teaching Calendar §5.6 (Remaining) | GraphQL schema (`LmsQuery`), `service/calendar/CalendarReadService.java` | `myStudentCalendar` chưa có. Cần map `principal.userId()` → `Student.academicClassId` |
| 24 | **EXPLAIN ANALYZE / index review** | Teaching Calendar + GraphQL Enterprise | All `teaching_schedule_*` tables, LMS join queries | Chưa chạy trên PostgreSQL thật. Index composite có thể thiếu cho range query có filter |
| 25 | **Calendar test migration** | Teaching Calendar | `src/test/java/com/erg/ergspringboot/controller/calendar/`, `service/calendar/`, `repository/calendar/` | Tests cũ ở `lms/schedule/` đã xóa. Cần viết lại 6 service tests + 1 repo test + 3 controller tests cho package mới |

---

## Tổng quan thống kê

| Mức ưu tiên | Số lượng | Mô tả |
|-------------|----------|-------|
| 🔴 P0 | 5 | Chặn production — compile, PostgreSQL, notification wiring |
| 🟡 P1 | 9 | Cần làm trước scale — tests, Redis, timeout, retention |
| 🟠 P2 | 11 | Hardening — native, load test, student view, cleanup |
| **Tổng** | **25** | |

### War room ưu tiên đề xuất

```text
Tuần này (P0):
  1. Build compile + fix errors (~2h)
  2. Copy worktree files về source chính (~30m)
  3. Wire notification thật vào LmsRuntimeService (~1h)
  4. questionBank PostgreSQL test (~1-2 ngày, cần Testcontainers)
  5. Xoá stub sai package (~5m)

Tuần sau (P1):
  6-8. Calendar tests + Security test + Notification test
  9-11. Redis rate limit + cache + timeout smoke
  12-14. SKIP LOCKED test + retention job

Sprint sau (P2):
  15-25. Native, load test, admin features, student view
```

---

*Báo cáo này được tổng hợp từ: `docs/0806/lms-lcms-graphql-security-enterprise-plan.md`, `docs/0806/lms-lcms-graphql-security-remaining-work-plan.md`, `docs/1006/firebase-push-notification-be-plan.md`, `docs/1406/teaching-calendar-backend-plan.md` và kết quả thực thi codebase thực tế.*
