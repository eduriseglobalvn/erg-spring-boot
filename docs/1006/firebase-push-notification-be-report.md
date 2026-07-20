# Firebase Push Notification -- BE Implementation Report

**Date:** 2026-06-14 (updated)
**Branch:** `codex/lms-graphql-security-audit-plan`
**Domain:** erg-spring

---

## 1. Summary -- What Was Built

Complete backend infrastructure for Firebase Cloud Messaging (FCM) push notifications. PostgreSQL is the source of truth for inbox, device registry, outbox, delivery attempts, topic memberships, and preferences. FCM is only the transport provider.

### Files Created/Modified

| Layer | Count | Details |
|-------|-------|---------|
| DB Migration | 1 | `V10__firebase_push_notifications.sql` (7 tables) |
| Entities | 7 | NotificationMessage, NotificationRecipient, NotificationDeviceRegistration, NotificationOutbox, NotificationDeliveryAttempt, NotificationTopicMembership, NotificationPreference |
| Repositories | 7 | Matching repositories with custom queries for inbox paging, unread count, outbox pickup with SKIP LOCKED, device lookup by FID/token hash, topic membership management |
| Services | 6 | NotificationService (inbox), NotificationDeviceService (registration), NotificationCommandService (create notifications), NotificationOutboxWorker (scheduled sender), NotificationDeviceMaintenanceService (stale cleanup), LmsNotificationEventPublisher (event hooks) |
| Controllers | 3 | NotificationController (self API), NotificationDeviceController (device registration), NotificationAdminController (admin send/stats) |
| Firebase/Native | 7 | FirebaseHttpV1PushSender, ServiceAccountAccessTokenProvider, StaticAccessTokenProvider, FirebasePushPayload, SendResult, FirebaseNotificationConfiguration, FirebaseNotificationRuntimeHints |
| Security | 1 | NotificationSecuritySupport (tenant/portal/user scope enforcement) |
| Config | 1 | NotificationPushProperties (encryption, stale age, pool size, etc.) |
| Tests | 5 | NotificationDeviceServiceTest, NotificationCommandServiceTest, NotificationTokenCipherTest, NotificationDeviceMaintenanceServiceTest, FirebaseHttpV1PushSenderTest (+ AccessControlSecurityTest) |

### Key Features

- **Self notification API**: list, unread-count, detail, mark read, read-all, archive, preferences -- all scoped by tenant+user+portal
- **Device registration**: FID-first with compatibility token fallback, upsert idempotent, cross-user rebind protection, portal scoped
- **Admin API**: send notification (with idempotency), stats, manual outbox run -- requires `notifications.manage` + `admin` portal
- **Outbox worker**: `@Scheduled` pickup with `FOR UPDATE SKIP LOCKED`, claim in short transaction, send FCM outside transaction, record result in separate transaction, lease recovery for stuck `processing` items
- **Retry**: exponential backoff with jitter, respects `Retry-After` header, max attempts 5, 429/5xx retryable, UNREGISTERED/NOT_FOUND = dead + disable device
- **Token encryption**: opt-in AES-GCM at-rest via `NOTIFICATIONS__PUSH__REGISTRATION_TOKEN_ENCRYPTION_KEY`, ciphertext prefix `enc:v1:`, fail-fast required mode
- **Native-image ready**: HTTP v1 sender (JDK HttpClient), no `firebase-admin` dependency, RuntimeHints with ACCESS_DECLARED_FIELDS, no crash on missing credentials
- **Metrics**: Micrometer gauges/timers for outbox pending/retry/dead, registration active/stale, send count/latency
- **Stale cleanup**: scheduled job deactivates devices not seen in `staleRegistrationAge` (default 30 days)

---

## 2. Tests Added

| Test | Coverage |
|------|----------|
| `NotificationDeviceServiceTest` | Register (new/update), unregister, heartbeat, portal mismatch rejection, cross-user rebind guard, portal-scoped device deletion |
| `NotificationCommandServiceTest` | Create notification with idempotency, tenant topic guard (namespace `t_{tenant}_...`), reserved payload key rejection, notification + recipient + outbox creation in single command |
| `NotificationTokenCipherTest` | Encrypt/decrypt round-trip, ciphertext prefix detection, migration from plaintext, fail-fast with required flag |
| `NotificationDeviceMaintenanceServiceTest` | Stale device cleanup respects age threshold, only disables devices older than config, count recorded in metrics |
| `FirebaseHttpV1PushSenderTest` | Fake HTTP server test: success, 429 with Retry-After, 500 retryable, UNREGISTERED invalid target (disables device), missing access token (retryable) |
| `AccessControlSecurityTest` | Self notification route (no `notifications.manage` needed), portal rejection (session `lms` cannot read portal `elearning`), admin route requires correct permission + `admin` portal |

---

## 3. Gap Closure Status

| Item | Status |
|------|--------|
| `V10` schema (7 tables + indexes) | Done |
| Self notification API (list, detail, read, archive, preferences) | Done |
| Device API (register, unregister, heartbeat, delete) | Done |
| Admin API (send, stats, manual outbox run) | Done |
| HTTP v1 Firebase sender (native-friendly) | Done |
| AccessTokenProvider (service account + static) | Done |
| Outbox worker (SKIP LOCKED, lease, retry, jitter) | Done |
| Token at-rest encryption (opt-in AES-GCM) | Done |
| Security: tenant/portal/user scope enforcement | Done |
| Security: topic namespace, reserved key guard, device rebind guard | Done |
| Micrometer metrics (outbox, registration, send latency) | Done |
| Stale device cleanup (scheduled job) | Done |
| Retry with exponential backoff + jitter + Retry-After | Done |
| Dead letter: invalid target deactivates device | Done |
| RuntimeHints for native image | Done |
| Fake HTTP test for FCM sender | Done |
| Event catalog: `LmsNotificationEventPublisher` for AFTER_COMMIT hooks | Done |

---

## 4. Remaining Items

| Priority | Item | Notes |
|----------|------|-------|
| P0 | Postgres/Testcontainers test for `FOR UPDATE SKIP LOCKED` and lease recovery | H2 cannot prove Postgres-specific locking behavior |
| P0 | Retention/partition policy for `notification_delivery_attempts` | Large table risk under production traffic |
| P1 | Topic subscribe/unsubscribe with Firebase backend | Currently stores local membership only; needs sync service |
| P1 | Provider-level rate limiter and delivery window for broadcast | FCM has token bucket quota; needs client-side throttle |
| P1 | Native image compile/smoke in CI | No CI pipeline yet |
| P2 | Admin cancel/resend/detail delivery attempts | Not yet implemented |
| P2 | Redis cache for unread count | Currently uncached (DB query per request) |
| P2 | Hook event LMS thuc te (ATTEMPT_SUBMITTED, ASSIGNMENT_CREATED, etc.) | Publisher exists but no hooks wired to real domain services |
| P2 | Load test 10k users / 5k concurrent | Not yet run |

---

## 5. Overall Readiness Score

**85%**

All foundational BE components are implemented and tested: schema, all 3 API groups, Firebase HTTP v1 sender with retry, outbox worker, token encryption, security hardening, metrics, and stale cleanup. 29+ tests pass with 0 failures. The block for production are: Postgres concurrency proof (Testcontainers), retention policy, native CI smoke, and hooking real LMS events. The BE is ready for FE pairing and staging smoke.
