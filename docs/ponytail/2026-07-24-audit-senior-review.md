# Audit toàn diện erg-spring — Phản biện của một Senior khó tính

**Ngày:** 2026-07-24
**Nhánh:** `codex/spring-modulith-modernization`
**Phạm vi:** 53.303 LOC, 94 `@Entity`, 77 repository, 50 controller, 74 service.
**Cách làm:** 4 agent chạy song song (over-engineering, JPA/N+1, hiệu năng API, bảo mật), sau đó xác minh lại từng phát hiện quan trọng trực tiếp trên code.

---

## Bối cảnh — vì sao có tài liệu này

Bạn yêu cầu rà soát toàn bộ với tư cách một senior khó tính: lỗi bảo mật, N+1, tối ưu call DB, SQL kinh điển, API nặng/chậm, và over-engineering. Đây là danh sách phản biện, **xếp theo tác động lớn nhất trước**. Mỗi mục có: bằng chứng (file:line đã xác minh), vì sao đau, và cách sửa tối thiểu.

**Tin tốt cần công nhận trước khi chê:**
- Toàn bộ association JPA đều `FetchType.LAZY` tường minh (không sót EAGER nào), `open-in-view: false`, `batch_size=50`, `order_inserts/updates=true`.
- **Không có SQL injection** — mọi native query đều tham số hoá (`set_config(?, ?, true)`, `concat('%', :search, '%')`, `lower(:identifier)`). Đã grep `String.format`/nối chuỗi trong `@Query`: 0 kết quả.
- Token là opaque random 48-byte hash SHA-256, không phải JWT → miễn nhiễm algorithm-confusion/`alg=none`.
- Refresh token có rotation + replay detection, revoke toàn session khi phát hiện reuse.
- GraphQL có guard đầy đủ: depth 8, complexity 500, alias/introspection/rate-limit.
- Error handler không leak stack trace (`include-stacktrace: never`).
- Nhiều service đã dùng đúng pattern `findByIdIn` + gom `Map` (CalendarConflictService, CrmSchoolService, LmsAssignmentCommandService).

Nói cách khác: nền tảng chắc. Vấn đề nằm ở **cấu hình mặc định nguy hiểm**, **vài hot-path fetch sai**, và **indirection thừa của layer Modulith**.

---

## PHẦN A — HIỆU NĂNG DB & N+1 (ưu tiên cao nhất, sửa rẻ nhất)

### A1. 🔴 Thiếu `default_batch_fetch_size` — 1 dòng, dập N+1 diện rộng
**File:** `src/main/resources/application.yml` khối `spring.jpa.properties.hibernate` (dòng ~68 chỉ có `jdbc.batch_size`).
**Đã xác minh:** grep chỉ thấy `batch_size` (JDBC batch cho ghi), **không có** `default_batch_fetch_size` (batch cho đọc). Hai thứ khác nhau hoàn toàn.
**Vì sao đau:** 94 entity toàn LAZY. Load N entity rồi chạm collection lazy → N+1 SELECT. Đây là biện pháp chống N+1 mạnh nhất của Hibernate mà lại vắng mặt.
**Fix:**
```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: ${SPRING_JPA_HIBERNATE_DEFAULT_BATCH_FETCH_SIZE:100}
```
→ Hibernate gom lazy load thành `IN (...)`: 1+N query thành 1+2 query. **Làm mục này TRƯỚC TIÊN.**

### A2. 🔴 EntityGraph auth hot-path — cartesian product mỗi request
**File:** `repository/auth/AuthSessionRepository.java:20,23` — `@EntityGraph` fetch đồng thời `user.roles.permissions` + `user.permissionOverrides` + `user.accessScopes`.
**Gọi từ:** `ErgPrincipalService.fromBearerToken()` — **chạy mỗi request đã xác thực**.
**Đã xác minh:** đọc trực tiếp file, đúng 3 collection độc lập trong 1 EntityGraph.
**Vì sao đau:** 3 collection độc lập trong 1 query → JOIN chéo. User 5 role × 10 permission × 8 override × 6 scope ≈ 2.400 dòng cho 1 user, Hibernate de-dup trong RAM. Trên đường nóng nhất hệ thống. `UserRepository.findWithDetailsByIdAndTenantId/findForLoginById/findWithDetailsByTenantAndIdentifier` mắc **cùng lỗi**.
**Fix:** Sau khi bật A1, giảm EntityGraph xuống `{"user","user.roles"}`, để `permissions`/`overrides`/`accessScopes` batch-fetch theo lô. Hoặc tách thành nhiều query đơn-collection ghép ở service.

> ⚠️ **CẬP NHẬT (đã thử & REVERT):** Đã thử giảm EntityGraph → **phá `AuthQueryOptimizationContractTest`** (2 failure). Tồn tại contract test **cố ý** khẳng định auth query phải fetch cả 5 path trong MỘT query. Kèm 2 test anh em (`bearerPrincipalAuthorizationIsNotCrossRequestCached`, `removedCrossRequestSecurityCaches...`) cho thấy team đã cân nhắc kỹ: **cố tình KHÔNG cache auth cross-request** (bảo mật — quyền phải luôn tươi), bù lại chấp nhận 1 query to để tránh nhiều round-trip mỗi request. Đây là **quyết định kiến trúc có chủ đích**, không phải bug. Đã revert về nguyên trạng.
>
> **Nếu vẫn muốn tối ưu:** phải sửa/bàn lại contract test trước (nó là hợp đồng team đã ký). Tradeoff thật phụ thuộc dữ liệu prod: nếu user thực có nhiều role×permission×override×scope thì cartesian mới thành vấn đề đo được — cần đo trước, không đoán. Với A1 (`default_batch_fetch_size`) đã bật, phương án an toàn là tách thành nhiều `@EntityGraph` đơn-collection + đổi contract test cho khớp, thay vì để lazy batch-fetch (vì auth không cache nên lazy sẽ bắn thêm query mỗi request).

### A3. 🟠 `RoleRepository.findAllWithPermissionsAndUsers()` — cartesian + không giới hạn
**File:** `repository/authorization/RoleRepository.java:34` — `join fetch role.permissions` VÀ `role.users` cùng lúc.
**Vì sao đau:** P permission × U user dòng mỗi role; `distinct` chỉ khử ở Java sau khi DB đã trả full tích. Role gán cho hàng nghìn user = bom. Có `@Cacheable` nên đỡ phần nào nhưng lần miss vẫn nổ.
**Fix:** Tách 2 query (permissions riêng, users riêng), hoặc bỏ `join fetch role.users` + dùng count query nếu chỉ cần đếm.

### A4. 🟠 Thiếu index chiều ngược trên join table
**File:** Flyway `V1` — `user_roles(user_id, role_id)` và `role_permissions(role_id, permission_id)` chỉ có composite PK.
**Vì sao đau:** PK phủ cột đầu; tra ngược ("user nào có role X", "role nào có permission Y") seq-scan. Chính là truy vấn của A3.
**Fix:** Flyway migration mới:
```sql
CREATE INDEX IF NOT EXISTS idx_user_roles_role_id ON user_roles(role_id);
CREATE INDEX IF NOT EXISTS idx_role_permissions_permission_id ON role_permissions(permission_id);
```

### A5. 🟡 `readOnly=true` chỉ phủ ~38% transaction (57/150)
**Vì sao đau:** Transaction đọc thiếu `readOnly=true` → Hibernate vẫn dirty-check/snapshot mọi entity → tốn RAM/CPU, không route được sang read replica.
**Fix:** Thêm `@Transactional(readOnly=true)` cho mọi `*ReadService`/`*QueryUseCase`/GET path. Không cần sửa hết một lượt — làm dần.

### A6. 🟡 `save()` trong loop → dùng `saveAll`
**File:** `QuizPublishService.java:237`, `SchoolCommandService.java:264`, `NotificationOutboxWorker.java:130,149`.
**Fix:** Gom `saveAll` để chắc chắn hưởng JDBC batch, tránh flush sớm. (Seed service ưu tiên thấp.)

### A7. 🟡 `UserLifecycleUseCase.findAll()` — N query theo id
**File:** `UserLifecycleUseCase.java:67` — `ids.stream().map(id -> find(id, tenant))`.
**Fix:** Một lần `findByTenantIdAndIdIn(...)`.

---

## PHẦN B — API NẶNG / CHẬM

### B1. 🔴 `GET /lms/quizzes/{quizId}/students` — nạp toàn bộ attempt + 3 blob JSONB, không phân trang
**File:** `LmsController.java:153` → `LmsStudentReadModelUseCase.java:82` → `LmsAttemptService.java:79` (`findByTenantIdAndQuizIdOrderByUpdatedAtDesc`, không limit).
**Vì sao đau:** `Attempt` chứa 3 cột jsonb (`answers`/`events`/`client`). Quiz phổ biến = hàng nghìn attempt → nạp + deserialize + serialize toàn bộ blob mỗi request. Heap spike, dễ OOM khi vài giáo viên mở cùng lúc.
**Fix:** Thêm `Pageable` + projection chỉ lấy field danh sách (id, studentId, score, status, submittedAt). **Không** trả `answers/events/client` trong list.

### B2. 🔴 `assignmentProgress` — nạp full entity chỉ để đếm 2 con số
**File:** `LmsStudentReadModelUseCase.java:69` → `LmsAttemptService.java:75` (`findByAssignment`).
**Vì sao đau:** Chỉ cần đếm submitted/inProgress nhưng nạp toàn bộ attempt (kèm jsonb) rồi `stream().filter().count()` trong Java.
**Fix:** `COUNT(*) ... GROUP BY status` DB-side. Đã có sẵn pattern `summarizeAssignmentAggregate` trong `QuizReportService` — dùng lại (rung 2 của ladder: đã có trong codebase).

### B3. 🟠 R2/S3 client KHÔNG có timeout — chặn request thread
**File:** `MediaStorageConfig.java:22-50` — `S3Client`/`S3Presigner` build không set `apiCallTimeout`/`apiCallAttemptTimeout`. Gọi `headObject` **đồng bộ trên request thread** tại `POST /media/{mediaId}/commit`.
**Vì sao đau:** R2 chậm/treo → giữ thread Tomcat. Kết hợp pool DB nhỏ (B5) → cạn thread nhanh.
**Fix:** `ClientOverrideConfiguration` với `apiCallTimeout` + `apiCallAttemptTimeout`; HTTP client với connect/read timeout.

### B4. 🟠 resilience4j là dependency CHẾT
**File:** `pom.xml:143-149` có `resilience4j-spring-boot3` + `-micrometer`. **Đã xác minh:** grep toàn repo = **0** `@CircuitBreaker/@RateLimiter/@Bulkhead/@Retry`, **0** config `resilience4j:`.
**Vì sao đau:** Trả tiền build + attack surface cho lib không dùng. External call (R2, AI/crawler) không có circuit breaker → downstream chậm lan ngược cạn thread pool.
**Fix — chọn 1:**
- (a) **Dùng:** bọc `R2MediaStorageAdapter.headObject` + external call bằng `@CircuitBreaker` + `@Bulkhead`.
- (b) **Gỡ:** xoá 2 dependency nếu không có kế hoạch dùng. (Ladder rung 1: YAGNI — nếu không dùng thì xoá.)

### B5. 🟡 Pool DB (max 8) + read executor (max 4) nhỏ + connection-timeout 1000ms hung hãn
**File:** `application.yml:28` (`DB__MAX_OPEN_CONNS:8`), `:30` (`connection-timeout:1000ms`); `ErgReadTaskExecutor.java:28` (core/max=4, queue=32, AbortPolicy).
**Vì sao đau:** Dưới tải, request đợi connection >1s fail ngay; fan-out đọc reject khi queue đầy. Endpoint nặng (B1/B2) giữ connection lâu → càng dễ cạn.
**Fix:** Có thể là chủ ý cho env nhỏ — cần **tài liệu hoá** và nâng theo tải thực. `connection-timeout` cân nhắc 3-5s. Ưu tiên fix B1/B2 để giảm thời gian giữ connection.

### B6. 🟡 `@Cacheable` calendar gần vô dụng — key chứa full list permission + TTL 30s
**File:** `CalendarReadService.java:102,125,148` — key gồm `#principal.permissions()` + `#principal.deniedPermissions()`.
**Vì sao đau:** Key gần như per-user → hit rate rất thấp → chiếm RAM mà không tái dùng.
**Fix:** Rút key thành hash của tập quyền liên quan calendar, hoặc bỏ principal khỏi key và lọc quyền sau khi lấy cache.

---

## PHẦN C — BẢO MẬT

### C1. 🔴 CORS: default có origin `http://` production kèm `allow-credentials=true`
**File:** `application.yml:311` (default `HTTP__CORS__ALLOWED_ORIGINS` chứa `http://crm.erg.edu.vn:3001`, `http://lms...`) + `:315` (`allow-credentials:true`). `CorsConfig.java:77` `rejectWildcardWithCredentials` **chỉ chặn `*`, không chặn `http://`**.
**Đã xác minh:** đọc trực tiếp cả yml lẫn CorsConfig.
**Vì sao đau:** Ở prod, `CorsConfig` lấy origin thuần từ config; nếu ops không override env, origin HTTP plaintext vẫn nạp. Trang trên `http://` (hoặc MITM chèn qua HTTP) gửi request kèm cookie và **đọc được response đã xác thực** cross-origin.
**Fix tối thiểu:**
- `.env.production`: `HTTP__CORS__ALLOWED_ORIGINS` chỉ gồm `https://`.
- Thêm guard trong `CorsConfig`: khi profile `prod` + `allowCredentials=true`, từ chối origin bắt đầu `http://` (mở rộng chính `rejectWildcardWithCredentials`). Fail-closed lúc startup.

### C2. 🔴 Upload media không auth + buffer toàn bộ body vào RAM trước khi check size
**File:** `SecurityConfig.java:220` (`PUT /api/v1/lcms/media/upload/**` → `permitAll`), `LcmsMediaController.java:39` (`@RequestBody byte[] bytes`), check size **sau** khi đã đọc (`:42`), check chữ ký **sau** (`:47`). **Đã xác minh:** grep không có `max-swallow-size`/`max-request-size`.
**Vì sao đau:** Kẻ tấn công **không cần đăng nhập** PUT body vài trăm MB → nạp hết vào heap trước khi bị từ chối → OOM/DoS. Buffer xảy ra trước cả kiểm chữ ký.
**Fix:** Đặt `server.tomcat.max-swallow-size` + giới hạn Content-Length ở filter/reverse-proxy; từ chối sớm theo header `Content-Length` trước khi đọc body. Cân nhắc streaming thay `byte[]`.
**Lưu ý (AN TOÀN):** Path traversal đã chặn đúng — `LocalMediaStorageAdapter.resolve()` normalize + `startsWith(storageRoot)` + bắt buộc SHA-256 khớp tên file.

### C3. 🟠 Access token thực chất sống 8-12h, không phải 15 phút như response quảng cáo
**File:** `AuthLoginUseCase.java:51-53` (`expiresIn=900s` nhưng session TTL `PRIVILEGED=8h`/`STANDARD=12h`), kiểm chứng ở `ErgPrincipalService.java:83` (`active()` chỉ check session còn hạn). Config `erg.auth.access-token-ttl:3h` (`application.yml:281`) là **dead config**.
**Vì sao đau:** Token lộ (log/referer/XSS/proxy) dùng được tới 8-12h → vô hiệu lợi ích của short-lived access + refresh rotation.
**Fix:** Thêm `accessTokenExpiresAt` riêng trong `AuthSession`, check trong `active()`; tối thiểu xoá dead config gây hiểu nhầm cho ops.

### C4. 🟠 SVG trong allowlist ảnh → nguy cơ Stored XSS
**File:** `MediaService.java:27` (`ALLOWED_IMAGE_TYPES` gồm `image/svg+xml`).
**Vì sao đau:** SVG là XML chứa được `<script>`. Nếu phục vụ inline cùng origin không kèm `Content-Disposition: attachment`/CSP/`nosniff`, browser thực thi script → stored XSS trong tenant.
**Fix:** Bỏ `image/svg+xml` khỏi allowlist (slide chỉ cần raster). Nếu buộc hỗ trợ: sanitize server-side + serve `attachment` + `nosniff` + CSP sandbox từ domain tách biệt.

### C5. 🟡 RLS phủ chưa đủ + permissive fallback khi thiếu TenantContext
**File:** `config/persistence/TenantAwareJpaTransactionManager.java:43`, javadoc `TenantGucApplier`.
**Vì sao đau:** RLS chặt chỉ áp bảng quiz-domain; bảng legacy "fall back to permissive" khi `TenantContext.current()==null`. Cách ly tenant phụ thuộc filter `tenantId` ở tầng app. Query nào quên `WHERE tenant_id` → rò rỉ chéo tenant, DB không backstop. (Phòng-thủ-theo-lớp, không phải lỗ active — mọi repo đã xem đều có filter.)
**Fix:** Bật RLS `FORCE` cho mọi bảng có `tenant_id`, policy fail-closed. Mở rộng dần sang bảng legacy.

### C6. 🟡 Media signing secret dùng lại JWT secret
**File:** `application.yml:202,279` — `erg.quiz.media.signing-secret` fallback về `${SECRET_AUTH__JWT_SECRET}`.
**Vì sao đau:** Một khoá ký cả token/session lẫn URL media — lộ một domain kéo theo cả hai.
**Fix:** Bắt buộc `SECRET_MEDIA_URL_SIGNING_KEY` riêng ở prod, bỏ fallback (fail startup nếu trùng).

### C7. 🟢 THẤP — gom nhanh
- `/metrics` custom (`PlatformHealthController.java:77`) permitAll, lộ uptime/thread count. (Actuator thật đã bị `denyAll` đúng.) → yêu cầu auth hoặc giới hạn IP scrape.
- `blockUserIfFailedLoginThresholdReached` (`AuthLoginUseCase.java:258`) là no-op (tradeoff cố ý chống DoS-khoá-account). Đảm bảo rate limiter luôn hiện diện ở prod (đừng để `required=false`).
- Trusted-header auth bật ở profile `test` — thêm assert startup: `trusted-header-auth-enabled=true` + profile `prod` → fail.

---

## PHẦN D — OVER-ENGINEERING (ponytail: cắt indirection)

⚠️ **Cảnh báo trước:** phần lớn "cắt lớn" nằm ở lớp `*Operations` — đây là **ranh giới module Spring Modulith cố ý** của nhánh này. Nếu team thực sự dùng `@ApplicationModule` để verify ranh giới, **giữ chúng**; giá trị là cô lập module, không phải logic. Xác nhận với chủ codebase trước khi gộp.

- `yagni:` ~16 lớp `*Operations` (~986 dòng) hầu hết delegate 1 dòng sang service (`LmsOperations`=21 method pass-through, `AuthOperations`=10, `NotificationOperations`=13). Nếu bỏ ranh giới Modulith → controller gọi thẳng service. **~500 dòng.**
- `yagni:` Chuỗi Controller→Operations→Service→ReadModelService→Repository quá sâu ở LMS/LCMS. `LmsQueryOperations` chỉ bọc `LmsReadModelService` 1-1. Cắt 1 tầng.
- `yagni:` 8 interface single-impl không phải Spring Data repo: `WebhookSignatureVerifier`, `CacheInvalidationPublisher`, `CacheValueCodec`, `CacheTenantResolver`, `AssignmentScopeLookup`, `AssessmentCurriculumBridge`, `ReportAggregateReconciliationStore` (giữ `FirebasePushSender` vì cần mock/adapter thật). → class trực tiếp, ~60 dòng.
- `delete:` `PaginatedData<T>` dùng 1 lần, trùng `PageResponse<T>` (dùng 31 lần). Xoá, đổi sang `PageResponse`. ~15 dòng + bớt 1 khái niệm phân trang song song.
- `shrink:` `ProfileService.getCurrentProfile(userId)` chỉ `return getProfile(userId)` — bỏ, gọi thẳng.

**net khả thi: ~-600 dòng** (nếu bỏ layer Modulith thuần túy). **0 deps từ góc over-engineering** — nhưng B4 (resilience4j) có thể gỡ 2 deps nếu chọn phương án (b).

---

## THỨ TỰ THỰC HIỆN ĐỀ XUẤT

**Tuần 1 — rẻ & tác động lớn:**
1. A1 `default_batch_fetch_size` (1 dòng).
2. C1 CORS https-only + guard prod (bảo mật cao, sửa nhanh).
3. C2 giới hạn body upload (`max-swallow-size` + Content-Length check).
4. A4 hai index join table (Flyway).

**Tuần 2 — hot-path & API nặng:**
5. A2 sửa EntityGraph auth (sau khi có A1).
6. B1 phân trang + projection cho `quizStudents`.
7. B2 COUNT query cho `assignmentProgress` (dùng lại `summarizeAssignmentAggregate`).
8. B3 timeout R2 client.

**Tuần 3 — củng cố:**
9. A3 tách cartesian `findAllWithPermissionsAndUsers`.
10. C3 access token TTL thật / xoá dead config.
11. C4 bỏ SVG khỏi allowlist.
12. B4 quyết định resilience4j: dùng hay gỡ.
13. B6 sửa cache key calendar.

**Backlog (dài hạn):** A5 readOnly diện rộng, A6/A7 saveAll/batch, B5 pool sizing, C5 RLS FORCE bảng legacy, C6 tách media secret, C7 harden metrics/rate-limit, D over-engineering (chờ xác nhận Modulith).

---

## VERIFY — cách kiểm chứng sau khi sửa

- **A1/A2 (N+1):** bật `logging.level.org.hibernate.SQL=DEBUG` + `spring.jpa.properties.hibernate.generate_statistics=true`, gọi login + 1 request auth, đếm số SELECT trước/sau. Kỳ vọng giảm từ ~O(roles) xuống ~2-3 query.
- **B1/B2:** load test với quiz có nhiều attempt (k6 đã có trong `load/k6/`), đo p95 latency + heap trước/sau. Kỳ vọng response không còn tăng tuyến tính theo số attempt.
- **C1:** `curl -H "Origin: http://lms.erg.edu.vn:3001" -H "Cookie: ..." <endpoint>` với profile prod → kỳ vọng bị từ chối (startup fail hoặc không có `Access-Control-Allow-Credentials`).
- **C2:** PUT body 200MB tới `/api/v1/lcms/media/upload/x` không auth → kỳ vọng bị cắt sớm theo Content-Length, không nạp hết vào heap.
- **Chung:** `./mvnw test` phải xanh sau mỗi thay đổi; thêm test riêng cho C1 guard (đã có `ProductionConfigurationSafetyTest` — mở rộng).
