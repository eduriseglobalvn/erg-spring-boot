# Thẩm định enterprise-scale 5000 user đồng thời — erg-spring

**Ngày:** 2026-07-25
**Nhánh:** `codex/spring-modulith-modernization`
**Cách làm:** 4 agent quét song song (concurrency/pool, heavy-query/N+1, enterprise-readiness, caching/session), sau đó xác minh trực tiếp mọi con số cấu hình trên `application.yml`.

---

## Bối cảnh

Sau vòng audit đầu (đã fix A1-C4), câu hỏi tiếp theo: codebase có phục vụ nổi **5000 user đồng thời** và đã chuẩn enterprise chưa. Tài liệu này xếp hạng theo tác động lên khả năng chịu tải, đã xác minh trực tiếp — không chép mù báo cáo agent.

---

## PHÁT HIỆN NỀN TẢNG — đính chính 2 giả định

Hai sự thật kiến trúc mà cả 4 agent hội tụ, thay đổi cách đọc mọi con số bên dưới:

1. **Session lưu Postgres, KHÔNG phải Redis.** `AuthSessionRepository extends JpaRepository` → bảng `user_sessions`. `spring-session-data-redis` có trong pom nhưng Redis autoconfig bị **exclude** (`application.yml:17`), không có `@EnableRedisHttpSession`. Điểm tốt: `lastActiveAt` chỉ ghi khi login/refresh, **không ghi mỗi request** → không đập DB mỗi request để check session (session check là 1 SELECT theo access-token-hash, có index).

2. **Redis mặc định TẮT hoàn toàn.** `l2-enabled=false` (`:201`), `erg.redis.state.enabled=false` (`:214`), `erg.redis.cache.enabled=false`. Nghĩa là mọi cache chỉ là **Caffeine local per-instance**, và invalidation cross-instance là **no-op**. Đây là gốc rễ của nhiều vấn đề đa-instance bên dưới.

---

## 🔴 CRITICAL — nút thắt số 1 (cả 4 agent độc lập đều chỉ vào đây)

### E1. HikariCP pool = 8, connection-timeout = 1000ms
**File:** `application.yml:28-30` — `maximum-pool-size=8`, `minimum-idle=1`, `connection-timeout=1000ms`. **Đã xác minh trực tiếp.**

**Vì sao chặn scale (số học):**
- Mỗi request giữ connection ~50ms → trần DB-bound mỗi instance ≈ 8 / 0,05s = **~160 req/s**.
- Tomcat mặc định 200 thread (không cấu hình `threads.max`), virtual threads **tắt** (`:20`, đã xác minh). 200 thread tranh 8 connection → 192 thread block.
- Khi hàng đợi pool ~192, thời gian chờ trung bình ≈ (192-8)/160 ≈ **1,15s > 1000ms** → hàng loạt `SQLTransientConnectionException` → HTTP 500. Instance bắt đầu từ chối từ **~180-200 request đồng thời**.

**Càng nguy hiểm vì:** session check + mọi read/write JPA đều đi qua chính pool 8 này (session ở Postgres). Cache-miss storm (khi Redis tắt + cache local eviction) dồn thẳng xuống 8 connection.

**Fix (prod):**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: ${DB__MAX_OPEN_CONNS:25}
      minimum-idle: ${DB__MAX_IDLE_CONNS:10}
      connection-timeout: ${SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT:5000}
```
Postgres chịu tốt ~200-300 connection tổng → 5-8 instance × 25 = an toàn. `minimum-idle=1` gây cold-start latency mỗi lần tải tăng đột biến — nâng lên 10.

**Đây là thay đổi cấu hình prod, blast radius cao (ảnh hưởng tải DB thật). Cần xác nhận capacity Postgres trước khi áp.**

---

## 🔴 CRITICAL — vận hành

### E2. `/ready` luôn trả HTTP 200 kể cả khi DB chết
**File:** `PlatformHealthController.java:54-69` — tính `status="degraded"` khi DB fail nhưng vẫn `ok(data)` → luôn 200.
**Vì sao đau:** K8s readiness probe chỉ nhìn HTTP status → **pod có DB down vẫn nhận traffic**. Thêm nữa Redis bị exclude → `isRedisReady()` luôn false → status luôn "degraded" ngay cả khi khỏe (probe vô nghĩa).
**Fix:** dùng thẳng `/actuator/health/readiness` (đã bật `probes.enabled=true`, `:141`), bỏ endpoint tự chế; hoặc trả 503 khi check bắt buộc fail + loại Redis khỏi readiness.

### E3. Không có data lifecycle cho bảng phình vô hạn
**Bằng chứng:** chỉ có retention cho `refresh_token_history` (30 ngày) + stale devices. **Không có** cho `notification_outbox` (rows `sent` không bao giờ xóa), quiz attempts (3 jsonb/row), api access log, audit, webhook inbox.
**Vì sao đau:** ở 5k user, outbox + attempts tăng tuyến tính → worker `findDueForUpdate` và index chậm dần theo tháng.
**Fix:** retention job + partition theo tháng cho attempts/outbox/audit.

---

## 🟠 CAO — heavy query (đường nóng theo user)

### E4. `LmsStudentReadModelUseCase` — 2 overload cũ nạp 500 full entity + jsonb, lọc trong Java
**File:** `LmsStudentReadModelUseCase.java:30,51` → `findTop500ByTenantIdOrderByCreatedAtDesc`. Nạp tới 500 `Assignment` full (kèm jsonb `student_ids`) rồi `filter()` trong RAM. `listScores` còn gọi tiếp `findTop3ByAssignments` cho cả 500 id.
**Vì sao đau:** 5k user × 500 row jsonb kéo về heap + lọc CPU mỗi request.
**Fix:** xóa 2 overload cũ, buộc dùng overload phân trang đã có (`:39,:59` gọi `findStudentAssignments` — đã lọc bằng SQL + phân trang). *Cần trace caller để chắc không còn ai gọi bản cũ.*

### E5. Repo trả `List` không phân trang trên entity nhiều jsonb
**File:** `QuizRepository.java:16` (`Quiz` có 8 cột jsonb), `QuizBankItemRepository.java:9`, `AttemptRepository.java:37,39` (3 jsonb).
**Fix:** thêm `Pageable` + projection scalar. Projection paged đã tồn tại cho attempts (`findQuizAttemptSummaries`) — chuyển caller sang. *Cần trace caller trước khi sửa.*

### E6. Correlated subquery `NOT EXISTS` thiếu index phủ
**File:** `AttemptRepository.java:211,253` (`findLatest/BestAttemptScoresForRecipients`) — tự-join tương quan theo `(tenant_id, assignment_id, student_id)` so `updated_at`/`percent`, nhưng index chỉ có `(tenant_id, assignment_id)`.
**Fix:** index `(tenant_id, assignment_id, student_id, updated_at DESC, id)`, hoặc viết lại bằng `DISTINCT ON (student_id) ORDER BY updated_at DESC` (rẻ hơn NOT EXISTS trên Postgres).

### E7. `readOnly=true` thiếu trên read use case nóng
**File:** `LmsStudentReadModelUseCase` (class, `:14`) không có `@Transactional` nào → mỗi repo call mở tx read-write riêng.
**Fix:** `@Transactional(value="postgresTransactionManager", readOnly=true)` cấp class. (Đây là backlog A5 vòng trước — giờ có địa chỉ cụ thể.)

---

## 🟠 CAO — đa-instance & observability

### E8. Cache phân quyền không invalidate cross-instance
**File:** `CacheConfig.java:116-117` — `authorizationRoles`/`authorizationPermissions` chỉ ở L1 Caffeine, publisher no-op khi Redis state tắt. Admin thu hồi quyền → chỉ instance xử lý request đó xóa cache; instance khác giữ quyền cũ tới **60s**.
**Fix:** bật `erg.redis.state.enabled=true` → `RedisCacheGenerationService` lan generation qua pub/sub. TTL 60s cho phân quyền là ceiling rủi ro bảo mật — cân nhắc evict theo sự kiện.

### E9. Rate limiter per-instance → limit rò rỉ N lần
**File:** `GraphQlRateLimiter.java:60`, `AuthLoginRateLimiter.java:84` — fallback `LocalEmergencyRateLimitCounter` (Caffeine in-process) khi `redis-enabled=false` (mặc định).
**Vì sao đau:** N instance sau LB → limit hiệu dụng = N × giá trị cấu hình. Brute-force login vượt limit dễ hơn N lần.
**Fix:** bật Redis cho rate-limit (`redis-enabled=true`) để đúng distributed.

### E10. Correlation/trace id không vào MDC → log prod không truy vết được
**File:** `RequestIdFilter.java:29-30` chỉ set request attribute + response header, **không đẩy vào SLF4J MDC**. `erg.logging.format` mặc định `console`, profile prod không bật JSON.
**Fix:** filter đẩy `requestId`+`traceId` vào MDC + pattern; bật `format: json` ở prod.

### E11. Thiếu business metrics cho login & quiz attempt
**Bằng chứng:** `NotificationMetrics` rất tốt (gauge outbox pending/retry/dead) nhưng **không có** counter login success/failure hay timer attempt submit — 2 path business-critical.
**Fix:** `Counter` login result + `Timer` attempt submit → alert được brute-force / attempt failure spike.

---

## 🟡 TRUNG BÌNH

- **E12. `CREATE INDEX` không `CONCURRENTLY`** (`V77`): Flyway chạy trong transaction → index thường lấy SHARE lock chặn ghi role/permission suốt build. Ở 5k user chặn login lúc migrate. → migration riêng ngoài transaction với `CREATE INDEX CONCURRENTLY`. **Áp dụng luôn cho V77 của tôi.**
- **E13. Executor tự định nghĩa nhỏ:** `ErgReadTaskExecutor` core=max=4, queue=32, AbortPolicy (`:27`) → bão hòa nhanh, reject fan-out. → nâng 8-16 core, queue 64-128.
- **E14. Deadlock/lock timeout → 500 chung, không retryable:** `GlobalExceptionHandler:277` — deadlock (40P01) rơi vào 500. → map `CannotAcquireLockException` → 409/503 + `Retry-After`.
- **E15. Admission semaphore không blocking:** `CacheLoadCoordinator:38` — 1024 permit, `tryAcquire()` không chờ → cold-start nhiều key riêng biệt ném `RejectedExecutionException` cho user thay vì degrade. → `tryAcquire(timeout)` hoặc fallback loader trực tiếp.
- **E16. JWT secret rỗng vẫn boot được:** `ProductionConfigurationSafetyTest` chỉ assert text YAML, không verify secret runtime (`:285-286` default rỗng). → `@Validated @NotBlank` trên AuthProperties, fail-fast prod.
- **E17. Không có ShedLock cho cron dọn dẹp:** 4 cron (retention, device, metrics, recovery) chạy N instance đồng thời. Đa số idempotent nên thấp. `WORKER_ID="erg-spring"` hardcode khó debug. → ShedLock + WORKER_ID theo hostname.

---

## Điểm đã đạt tầm enterprise (ghi nhận, không sửa)

- **Outbox lease-based:** `FOR UPDATE SKIP LOCKED` + `locked_at/locked_by` → chống trùng đa-instance ở tầng DB, không cần ShedLock cho worker chính.
- **CI/CD mạnh:** Testcontainers postgres:17, OpenAPI/GraphQL contract lock + breaking-change gate, gitleaks + OWASP dependency-check, **k6 gate 5k VU** (steady/submission-burst/outbox). Đây là hàng enterprise thực thụ.
- **Migration expand/backfill an toàn:** FK `NOT VALID` rồi validate riêng, ADD COLUMN nullable có comment rolling-deploy.
- **Assignment progress sharded 64-way + trigger** (V69) → né hot-row khi hàng nghìn học sinh nộp cùng lúc.
- **Cache degrade tốt:** circuit breaker `CacheFailurePolicy` + `NoOpRedisCacheStore` fallback; session ở Postgres nên **Redis chết không logout ai**.
- **Idempotency (V76) + optimistic lock → 409, GUC transaction-local sạch, error masking chuẩn** (stacktrace/message never).
- **CalendarConflictService, notification inbox, attempt read projections:** đều batch `IN` + projection + index phủ, không N+1.

---

## THỨ TỰ MỞ KHÓA SCALE (đề xuất)

**Phải làm trước khi phục vụ 5k (chặn scale trực tiếp):**
1. **E1** — Hikari pool 8→25 + timeout 1s→5s. *Cần xác nhận capacity Postgres.* Đây là nút thắt số 1 mọi agent chỉ vào.
2. **E2** — sửa readiness probe (dùng actuator, bỏ endpoint giả).
3. Bật virtual threads HOẶC đặt `tomcat.threads.max` tương xứng pool DB (làm cùng E1, nếu không sẽ chỉ dời nghẽn xuống DB).

**Đa-instance đúng nghĩa (nếu chạy >1 instance sau LB):**
4. **E8/E9** — bật Redis state + rate-limit distributed (invalidation quyền + limit không rò rỉ).
5. **E10/E11** — MDC correlation-id + JSON log + business metrics (không có thì mù khi sự cố ở scale).

**Bền vững:**
6. **E3** — retention/partition cho outbox/attempts/audit.
7. **E4/E5/E6/E7** — heavy query + index + readOnly (đường nóng theo user).
8. **E12-E17** — hardening còn lại.

---

## VERIFY

- **E1:** load test k6 (đã có `load/k6/erg-5k-steady.js`) trước/sau nâng pool, đo p95 + tỷ lệ 500. Kỳ vọng hết `SQLTransientConnectionException`.
- **E2:** `curl /actuator/health/readiness` khi tắt DB → kỳ vọng 503.
- **E4/E6:** bật `hibernate.generate_statistics=true`, mở bảng điểm lớp đông, đếm query + đo thời gian trước/sau index.
- **E8/E9:** chạy 2 instance, thu hồi quyền ở instance A → kỳ vọng instance B thấy ngay (sau khi bật Redis state).
- **Chung:** `./mvnw verify` với Testcontainers (cần Docker — sandbox hiện chặn socket nên phải chạy ở môi trường có Docker).

**Lưu ý:** báo cáo này chỉ liệt kê + đề xuất, **chưa sửa gì**. E1 và các mục bật Redis là quyết định vận hành có blast radius cao — cần bạn xác nhận trước khi áp.
