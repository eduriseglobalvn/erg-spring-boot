# 07 - Platform Infrastructure (platform / config / common / exception / utils)

> Phạm vi đọc: `platform/**`, `config/**`, `common/**`, `exception/**`, `utils/**`, `ErgSpringApplication.java`, `model/BaseEntity.java`, `src/main/resources/application*.yml`, và test tương ứng (platform, config, architecture, common, exception).
> Mọi đường dẫn tính từ gốc repo `erg-spring`; viết tắt `B = src/main/java/com/erg/ergspringboot`. Số dòng là số dòng trong working tree tại thời điểm đọc (nhánh `Backup`, có thay đổi chưa commit ở `application.yml`).
> Quy ước: "Sự thật" = đọc trực tiếp từ code. "Nghi ngờ" = suy luận từ code, chưa chạy runtime để xác minh.

---

## 1. Phạm vi và trách nhiệm

Lớp platform là phần hạ tầng xuyên suốt mà mọi module nghiệp vụ (LMS, LCMS, CRM, E-learning, auth, users...) dùng chung: dựng chuỗi bảo mật Spring Security, xác thực bằng bearer token opaque (tra bảng session phía server) hoặc BFF/OIDC cookie, suy ra tenant từ principal đã xác thực rồi đẩy xuống PostgreSQL (GUC + RLS), phân quyền theo permission + workspace, giới hạn tốc độ và bảo vệ GraphQL, cache 2 tầng (Caffeine L1 + Redis L2 tuỳ chọn) có "generation" theo tenant, hộp thư webhook có chống replay, thực thi bất đồng bộ có giới hạn, và phong bì lỗi/phản hồi thống nhất (`ApiResponse`) cho REST và GraphQL.
`common/` chứa DTO/request/response (record) dùng chung; `exception/` chứa cây exception nghiệp vụ + `GlobalExceptionHandler`; `config/` chứa cấu hình JPA/Flyway/Cache/OpenAPI; `utils/IdGenerator` sinh UUIDv7; `BaseEntity` là `@MappedSuperclass` (id, createdAt, updatedAt).

Quy mô đã đọc: ~190 file Java main (toàn bộ `platform/`, `config/`, `exception/`, `utils/`, `ErgSpringApplication`, `BaseEntity`; các record DTO trong `common/` chỉ đọc lướt), 3 file yml, ~140 file test.

---

## 2. Thành phần chính

### 2.1 Bảo mật (`B/platform/security`)

| Thành phần | File | Vai trò ngắn |
|---|---|---|
| Security filter chain | `platform/security/SecurityConfig.java:48-260` | Bảng route -> permission; mặc định `/api/v1/**` cần xác thực, còn lại `denyAll` (dòng 252-254). CSRF cookie + session `IF_REQUIRED` khi BFF bật (50-75), ngược lại CSRF tắt + `STATELESS` (76-78). |
| Xác thực bearer | `platform/security/ErgAuthenticationFilter.java:51-74` | Đọc `Authorization: Bearer`, tra principal qua `ErgPrincipalService`; header `X-User-ID/X-Roles/...` chỉ dùng khi `erg.security.trusted-header-auth-enabled=true`. |
| Dựng principal | `platform/security/ErgPrincipalService.java:51-214` | Hash token -> tra session đang hoạt động theo tenant; gộp role + permission + override (grant/deny) + role-assignment có scope. |
| Principal | `platform/security/ErgPrincipal.java:6-73` | record bất biến: userId, sessionId, tenantId, roles, permissions, deniedPermissions, portals, accountType, accessLevel, attributes. |
| Policy phân quyền | `platform/security/ErgPolicyService.java:27-103` | Deny-override, wildcard (`*`, `x.*`, `*.read`), alias (`content.manage` -> `content.*`...), baseline `profile.self`. |
| Capability / workspace | `platform/security/AuthorizationCapabilityPolicy.java`, `WorkspaceCapabilityMapper.java`, `WorkspaceCapability.java` | Đổi role/permission đã xác thực thành workspace `ADMIN/TEACHER/STUDENT/CRM`; portal cũ (`lcms/lms/elearning/crm`) chỉ là khoá tra cứu, không phải quyền. |
| Gắn vào Spring Security | `platform/security/PermissionAuthorizationManager.java:21-51`, `MethodAuthorizationEvaluator.java` (bean `methodAuthorization`) | `require`/`requireAny` cho `.access(...)` ở SecurityConfig; evaluator cho `@PreAuthorize`. |
| Tenant context | `platform/security/TenantContext.java`, `TenantContextFilter.java`, `TenantGucApplier.java`, `config/persistence/TenantAwareJpaTransactionManager.java` | ThreadLocal tenant/user/portal -> `set_config('app.tenant_id'...)` mỗi transaction. |
| Tra tenant từ token | `platform/security/JdbcAuthSessionTenantResolver.java` | Gọi hàm SQL `public.erg_resolve_session_tenant(?)` (SECURITY DEFINER) trước khi RLS có hiệu lực. |
| Phạm vi truy cập | `platform/security/AccessScopeSet.java`, `AccessScopeAssembler.java`, `UserAccessContext.java`, `SystemActorContext.java` | Scope EDUCATION_UNIT/CLASS (+CAMPUS, PROGRAM...), `*` = toàn quyền trong tenant; fingerprint SHA-256 để làm cache key. |
| OIDC/BFF | `platform/security/OidcPrincipalBridgeFilter.java:61-98`, `OidcMfaPolicy.java`, `ErgAuthProperties.java` | Chỉ tạo bean khi `erg.auth.bff-enabled=true`; cầu nối OidcUser -> `ErgPrincipal`; bắt MFA cho role đặc quyền. |
| Role bảo vệ | `platform/security/ProtectedRolePolicy.java` | Role bắt đầu bằng `system.` là dành riêng; `system.god_mode` không gán được. |
| Chính sách namespace cũ | `platform/security/LegacyNamespacePolicy.java` | Alias `lcms/lms/elearning/crm` -> ADMIN/TEACHER/STUDENT/CRM, sunset mặc định 2026-10-31. |
| Lỗi security | `platform/security/SecurityErrorResponseWriter.java:22-36` | Ghi `ApiResponse.failure` JSON + `X-Request-ID` + `Cache-Control: no-store` cho 401/403 từ filter chain. |
| Guard khởi động | `platform/security/TrustedHeaderAuthProductionGuard.java:23-30` | Ném lỗi khi trusted-header-auth bật cùng profile `prod`. |
| IP tin cậy | `platform/security/TrustedClientIpResolver.java:31-46` | Duyệt `X-Forwarded-For` từ phải sang trái, chỉ tin proxy trong `erg.http.trusted-proxy-cidrs`. |
| Prometheus | `platform/security/PrometheusAccessRequestMatcher.java:22-27` | `GET /actuator/prometheus` công khai chỉ khi `erg.observability.prometheus-public-enabled=true`. |
| Đánh dấu endpoint công khai | `platform/security/ReviewedPublicEndpoint.java` | Annotation đánh dấu đã review; test hợp đồng bắt buộc có trên controller công khai. |

### 2.2 Web / request (`B/platform/web`)

| Thành phần | File | Vai trò |
|---|---|---|
| Request ID / correlation | `web/RequestCorrelation.java:12-88`, `web/RequestIdFilter.java:21-44` | Một nguồn sự thật cho `X-Request-ID` (attribute `request_id` + `requestId` cũ), MDC `requestId`/`traceId`, bọc request để header luôn có giá trị. |
| Access log | `web/ApiAccessLogFilter.java:37-70` | Log 1 dòng `api METHOD path -> status (ms) [requestId, tenantId, userId, ip]`, che token/secret/password trong path & query khi `erg.logging.mask-sensitive=true`. |
| Đếm query Hibernate | `web/HibernateQueryCountFilter.java:25-41` + `performance/RequestExecutionContext.java` + `RequestExecutionStatementInspector.java` | Cảnh báo N+1 khi > 10 statement/request. |
| CORS | `web/CorsConfig.java:91-117,169-178`, `web/ErgHttpProperties.java` | Allowlist origin; có chế độ BFF riêng. |
| Deprecation | `web/LegacyApiDeprecationFilter.java:40-59`, `web/LegacyApiRegistry.java:19-27` | Thêm header `Deprecation`, `Sunset`, `Link: successor-version`, `X-ERG-API-Owner` + counter `erg.api.deprecated.requests`. |
| Giới hạn upload ẩn danh | `web/MediaUploadSizeLimitFilter.java:22-36` | Từ chối `Content-Length` thiếu hoặc > `MediaService.MAX_IMAGE_BYTES` cho `/api/v1/lcms/media/upload/`. |
| Health | `web/PlatformHealthController.java:36-86` | `/api/v1/health`, `/healthz`, `/api/v1/ready`, `/ready`, `/metrics` (text thô: uptime, thread). |

### 2.3 GraphQL (`B/platform/graphql`)

| Thành phần | File | Vai trò |
|---|---|---|
| Guard HTTP | `graphql/GraphQlGuardFilter.java:56-139` | Chỉ POST; giới hạn body, độ dài query, depth, alias, operationName bắt buộc, rate limit, chặn introspection, chặn biến "nhạy cảm", giới hạn mảng biến. |
| Guard thực thi | `graphql/GraphQlExecutionGuardConfiguration.java:14-25`, `GraphQlDeadlineInstrumentation.java:29-65` | `MaxQueryDepth`, `MaxQueryComplexity`, deadline (AbortExecutionException). |
| Context | `graphql/GraphQlContextInterceptor.java:28-53`, `GraphQlRequestContextFactory.java:22-72`, `GraphQlRequestContext.java` | Dựng context từ principal đã xác thực; header tenant/portal chỉ để đối chiếu; `timeout` + timer `graphql.operation.duration`. |
| Quyền ở resolver | `graphql/GraphQlAccessSupport.java:33-113` | `verifiedTenantId`, `assertPermission`, `assertEducationUnitScope`, `assertClassScope`... uỷ quyền cho cùng `ErgPolicyService` với REST. |
| Lỗi | `graphql/ErgGraphQlExceptionResolver.java:28-74` | Map exception -> `extensions.code/status/request_id/details`, không lộ message nội bộ. |
| Rate limit | `graphql/GraphQlRateLimiter.java:50-107`, `graphql/ratelimit/*` | Cửa sổ cố định 60s; Redis (INCREX hoặc Lua) hoặc Caffeine cục bộ. |

### 2.4 Cache (`B/platform/cache`, `B/config/CacheConfig.java`)

| Thành phần | File | Vai trò |
|---|---|---|
| Descriptor | `cache/CacheDescriptor.java:8-40`, `CacheDescriptorRegistry.java`, `CacheDescriptorContributor.java` | Tên cache `[A-Za-z0-9._-]{1,96}`, kiểu giá trị, TTL L1/L2, trọng lượng L1, size tối đa (<= 1 MiB), `tenantRequired`, `cacheNulls`. |
| Cache 2 tầng | `cache/ErgTieredCache.java`, `ErgTieredCacheManager.java` | L1 Caffeine (khoá theo tenant) + L2 Redis tuỳ chọn theo `erg.cache.l2-names`. |
| Khoá | `cache/CacheKeyFactory.java:26-41` | `erg:cache:v2:{<sha256(tenant)[0..16]>}:<cache>:g<generation>:<sha256(canonical key)>`. |
| Generation | `cache/LocalCacheGenerationService.java`, `RedisCacheGenerationService.java`, `CacheInvalidationService.java` | Xoá cache theo tenant bằng cách tăng số generation (không quét khoá). |
| Pub/Sub | `cache/RedisCacheInvalidationPublisher.java` (kênh `erg:state:v1:cache-invalidation`), `RedisCacheInvalidationSubscriber.java`, `CacheCoordinationConfiguration.java` | Đồng bộ generation giữa các instance. |
| Chống bão cache | `cache/CacheLoadCoordinator.java:28-53` | Single-flight theo khoá + semaphore giới hạn số lần nạp đồng thời. |
| Chịu lỗi Redis | `cache/CacheFailurePolicy.java:24-39` | Circuit breaker đơn giản (ngưỡng lỗi liên tiếp, thời gian mở). |
| Mã hoá | `cache/JacksonCacheValueCodec.java`, `CacheEncoding.java` | JSON theo `valueType` của descriptor; vượt `maximumSerializedBytes` thì bỏ qua L2. |
| Số liệu | `cache/CacheMetrics.java` | `erg.cache.operations{cache,tier,operation,outcome}`; nhãn cắt tối đa 96 ký tự. |
| Danh mục cache | `config/LmsCacheDescriptorContributor.java`, `config/PlatformCacheDescriptorContributor.java` | 12 + 14 cache (xem 2.4.1). |
| Native hints | `config/CaffeineNativeRuntimeHints.java`, `config/*CacheRuntimeHintsContributor.java`, `platform/cache/BusinessCacheRuntimeHints.java` | Đăng ký reflection cho GraalVM. |

#### 2.4.1 Danh mục cache và TTL (sự thật)

Mọi descriptor dùng helper `descriptor(name, type, ttl, l1MiB)` => `l1Ttl = ttl`, `l2Ttl = ttl x 4`, `l1MaximumWeightBytes = l1MiB x 1 MiB`, `maximumSerializedBytes = 1 MiB`, `tenantRequired = true`, `cacheNulls = false` (`config/LmsCacheDescriptorContributor.java:46-49`, `config/PlatformCacheDescriptorContributor.java:41-44`).

| Cache | L1 TTL | L2 TTL (x4) | L1 MiB |
|---|---|---|---|
| lmsClassWorkspace, lmsHomeworkWorkspace, lmsAssignmentProgress, lmsStudentAssignments, quizQuestionAnalytics, quizAssignmentReport, studentDashboard | 30s | 120s | 64 |
| lmsStudentGroups | 30s | 120s | 32 |
| lcmsQuestionBankWorkspace, lcmsQuizWorkspace | 30s | 120s | 128 |
| quizRuntimePackage (`Optional<QuizPackageResponse>`) | 10 phút | 40 phút | 256 |
| lmsSchoolClasses (kiểu `Object`) | 60s | 240s | 64 |
| contentLibraryBootstrap, contentResources, contentResourceDetail, lmsClassesBySchool, organizationEducationUnits, organizationEducationUnitEntities | 60s | 240s | 32 |
| contentStudioBootstrap, authorizationRoles, authorizationPermissions | 60s | 240s | 16 |
| lcmsSchoolDirectory, calendarWorkspace | 30s | 120s | 64 |
| learningResourceLibrary | 60s | 240s | 128 |
| lcmsSchoolOperations | 30s | 120s | 128 |
| curriculumTree | 15 phút | 60 phút | 32 |

Ràng buộc: cache có trong `erg.cache.l2-names` mà kiểu giá trị là `Object` thì `ErgTieredCacheManager` ném lỗi khi khởi động (`cache/ErgTieredCacheManager.java:76-78`); tên L2 không tồn tại cũng ném lỗi (82-86). Nhiều cache khai báo `Object` (content*, lmsSchoolClasses, authorization*, organizationEducationUnitEntities, lmsClassesBySchool) nên **không thể** đưa lên L2 trừ khi đổi kiểu.

### 2.5 Redis (`B/platform/redis`)

| Thành phần | File | Vai trò |
|---|---|---|
| Thuộc tính | `redis/ErgRedisProperties.java:13-17,37-116` | Hai endpoint độc lập `state` (timeout 1s/1s, pool 16, minIdle 4) và `cache` (250ms/500ms, pool 24, minIdle 4); `@AssertTrue` bắt buộc có URL khi `enabled=true` (27-35). |
| Factory | `redis/RedisClientFactory.java:17-54` | Lettuce có pool, validate kết nối, SSL nếu URL `rediss://`. |
| Cấu hình | `redis/RedisInfrastructureConfig.java:24-88` | `stateRedisConnectionFactory` (bean `@Primary`, tên phụ `redisConnectionFactory`) và `cacheRedisConnectionFactory`; `@EnableScheduling`. |
| Health | `redis/RedisStateHealthIndicator.java` (lỗi => DOWN), `redis/RedisCacheHealthIndicator.java` (lỗi => trạng thái `DEGRADED`, fallback postgresql) | |
| Metrics | `redis/RedisMetricsBinder.java:39-52` | Gauge bộ nhớ/evicted/rejected/AOF, refresh `erg.redis.metrics.refresh-interval` (mặc định 15s). |
| Lưu trữ cache | `cache/RedisCacheStoreConfiguration.java` (khi `erg.redis.cache.enabled=true`), `cache/RedisTemplateCacheStore.java` | `RedisTemplate<String, byte[]>`. |

### 2.6 Webhook (`B/platform/webhook` + `controller/bot/WebhookController.java`)

| Thành phần | File | Vai trò |
|---|---|---|
| Discord | `webhook/DiscordEd25519SignatureVerifier.java:28-55` | Xác thực Ed25519 trên `timestamp + rawBody`, cửa sổ thời gian `erg.webhook.timestamp-tolerance` (5 phút). |
| Telegram | `webhook/TelegramSecretTokenVerifier.java:13-19` | So sánh hằng-thời-gian secret token; token rỗng => từ chối. |
| Chống replay | `webhook/WebhookReplayGuard.java:13-22` | `INSERT ... ON CONFLICT DO UPDATE ... WHERE expires_at < now()` vào `webhook_receipts`; trùng và chưa hết hạn => `WebhookReplayException` (409). |
| Hộp thư | `webhook/WebhookInboxService.java:29-37` | Trong 1 transaction: claim replay + ghi `webhook_inbox(status='pending')`; counter `webhook.accepted`. |
| Thuộc tính | `webhook/WebhookProperties.java` | `max-body-bytes` 64 KiB, `replay-ttl` 24h, `timestamp-tolerance` 5m, khoá/secret (không ghi giá trị). |
| Controller | `controller/bot/WebhookController.java:38-60` | `POST /webhooks/discord`, `POST /webhooks/telegram` (ngoài slice nhưng là điểm vào). |

### 2.7 Thực thi bất đồng bộ và sự kiện

| Thành phần | File | Vai trò |
|---|---|---|
| Read executor | `concurrency/ErgReadTaskExecutor.java:33-77`, `ErgReadExecutorProperties.java` | `ThreadPoolExecutor` có biên, `AbortPolicy`, daemon thread `erg-read-N`; tự mang `TenantContext` + `RequestExecutionContext` sang luồng con, **không** mang SecurityContext. |
| Module event executors | `concurrency/ModuleEventTaskExecutorConfig.java:15-48` | `moduleEventIoExecutor` (đồng thời mang tên bean `applicationTaskExecutor`) và `moduleEventCpuExecutor`; đều `AbortPolicy`. |
| Event metadata | `events/ModuleEventMetadata.java:17-25` | Phong bì bắt buộc: eventId, eventVersion>=1, tenantId, aggregateId... `correlationId` mặc định = eventId. |
| Phục hồi event | `events/ModuleEventRecoveryJob.java:56-71` | `@Scheduled` resubmit `FailedEventPublications` + `IncompleteEventPublications` của Spring Modulith, chống chạy chồng bằng `AtomicBoolean`. |
| Sync offline | `sync/LmsSyncOperationService.java:47-116` | Biên "receipt" idempotency cho mutation offline LMS (bảng `lms_sync_operations`). |
| Process-local | `persistence/ProcessLocalStatePolicy.java:16-21` | Chặn store in-memory demo khi `erg.process-local.enabled=false`. |
| Danh mục JSONB | `persistence/JsonbDurabilityCatalog.java:24-77` | Kiểm kê 50 trường JSONB, phân loại DURABLE_VERSIONED / DURABLE_OWNER_READER / TRANSIENT_EVENT / EXEMPT_AUDIT_OR_PII. |
| Native hints | `nativeimage/ApiResponseNativeRuntimeHints.java`, `ModulithNativeRuntimeHints.java` | Cần cho GraalVM (JSON envelope và Modulith listener). |

### 2.8 Phong bì lỗi và phản hồi (`B/common/response`, `B/exception`)

| Thành phần | File | Vai trò |
|---|---|---|
| `ApiResponse<T>` | `common/response/ApiResponse.java:10-79` | `{statusCode, message, data, errors, timestamp, path, request_id}`. `errors` là `TransportError` sealed: `Code` (chuỗi mã), `Fields` (mảng `FieldErrorDetail{field, tag, actual_tag, message}`), `Conflict` (`ConflictPayload`). |
| `ErrDetail` | `common/response/ErrDetail.java` | `{code, message, details}`; `failure()` chọn `Fields` nếu có details, ngược lại `Code`. |
| Phân trang | `common/response/PageResponse.java` (offset, zero-based), `PaginatedData.java` (`@Deprecated`, dạng Go cũ), `common/dto/PageRequestDto.java:10-24` (mặc định page 0, size 20, tối đa 100) | |
| Cây exception | `exception/ApiException.java` + `BadRequestException`(400 `BAD_REQUEST`), `UnauthorizedException`(401 `UNAUTHORIZED`), `ForbiddenException`(403 `FORBIDDEN`), `ResourceNotFoundException`(404 `RESOURCE_NOT_FOUND`), `ConflictException`(409 `CONFLICT`), `NotImplementedException`(501, mã tuỳ chọn), `StaleDraftException`(409 `STALE_DRAFT`), `SyncConflictException`(409, mang `ConflictPayload`), `AccountLifecycleException`, `auth/AuthFailure` (13 lớp con) | |
| Handler | `exception/GlobalExceptionHandler.java` | `@RestControllerAdvice`, 14 handler (xem mục 3.3). |
| ID | `utils/IdGenerator.java:13-25` | UUIDv7 (48-bit timestamp ms + 12 bit random + variant) bằng `SecureRandom`; không đơn điệu trong cùng 1 ms. |
| BaseEntity | `model/BaseEntity.java:16-69` | `id` String(36) sinh UUIDv7 ở `@PrePersist`, `createdAt/updatedAt` (`OffsetDateTime.now()`), `equals` theo id + class Hibernate, `hashCode` = hash của class (hằng theo class). |

### 2.9 Cấu hình persistence (`B/config/persistence`)

| Thành phần | File | Vai trò |
|---|---|---|
| JPA | `PostgresJpaConfig.java:23-105` | Datasource Hikari từ `PostgresHikariProperties` (record, tiền tố `spring.datasource.hikari`); EMF `postgres` `@DependsOn("postgresFlyway")`; transaction manager = `TenantAwareJpaTransactionManager`. |
| Quét entity | `JpaRegistrationCatalog.java:17-41` | Danh mục module model: enabled (auth, audit, authorization, calendar, crm, curriculum, enterpriseconnectors, learningcontent, learningoutcomes, learningstandards, lms, notifications, organization, outbox, quiz, reporting, school, users), dormant (community, configuration, courses, recruitment, security), partiallyManaged (`content`: chỉ `ContentLifecycleTransition`). |
| Hibernate | `JpaPersistenceSupport.java:43-50` | `hbm2ddl=validate` (mặc định), `StatementInspector` đếm câu lệnh, naming strategy snake_case, `generateDdl=false`. |
| Flyway | `FlywayConfig.java:24-69` | Bean chỉ migrate khi `spring.flyway.enabled=true`; baselineOnMigrate=true, baselineVersion=0; user/password riêng `spring.flyway.user/password` (tách quyền owner); xem rủi ro vị trí migration (mục 7). |
| OpenAPI | `config/OpenApiConfig.java:18-33` | Title "ERG Spring API" v1, scheme `bearerAuth`, `erg.openapi.server-url`. |

---

## 3. Luồng xử lý xuyên suốt

### 3.1 Request HTTP từ vào đến ra (REST)

Thứ tự trong chuỗi dưới đây là thứ tự logic. Lưu ý về thứ tự thực tế của các servlet filter ở mục 7 (R-09).

1. **LegacyApiDeprecationFilter** (`@Order(HIGHEST_PRECEDENCE + 30)`, `web/LegacyApiDeprecationFilter.java:19,30-38`): nếu URI khớp `/api/crawler/**`, `/api/content/**`, `/api/curriculum/topics/**` (`web/LegacyApiRegistry.java:19-27`) thì thêm header `Deprecation: true`, `Sunset`, `X-ERG-API-Owner`, `Link successor-version` và tăng counter.
2. **Spring Security `springSecurityFilterChain`** (`platform/security/SecurityConfig.java:48-260`):
   - CORS: `CorsConfig` đăng ký qua `WebMvcConfigurer` (`web/CorsConfig.java:169-178`); `OPTIONS /**` cho qua (dòng 98).
   - `ErgAuthenticationFilter` chèn trước `UsernamePasswordAuthenticationFilter` (255):
     - BFF bật mà có bearer => `sendError(401, "BFF_COOKIE_AUTH_REQUIRED")` (`ErgAuthenticationFilter.java:54-57`).
     - Chưa có Authentication và có bearer (hoặc header tin cậy khi bật) => `principalFrom` (58-72).
     - Có bearer: lấy `X-Tenant-ID` (nếu có) làm gợi ý tra cứu; nếu không có thì `ErgPrincipalService.resolveTenantId` suy ra tenant từ session phía server (103-123). Trong lúc tra cứu, `TenantContext` tạm thời được đặt rồi khôi phục (130-153) để truy vấn session đi qua RLS.
     - Portal trong path (`SecurityUtils.portalFromPath`, `SecurityUtils.java:51-64`) phải khớp workspace đã xác thực, nếu không `403 "Portal session does not match requested API."` (63-66, 212-218).
     - Principal "bị hạn chế vòng đời" (`lifecycleRestricted`: hồ sơ chưa hoàn tất, email khôi phục chưa xác minh, hoặc phải đổi mật khẩu — `ErgPrincipalService.java:216-220`) chỉ được gọi `/api/v1/users/me**`, `/api/v1/sessions/`, `/api/v1/auth/logout|refresh` (67-70, 224-231), còn lại `403 ACCOUNT_LIFECYCLE_RESTRICTED`.
     - Thành công: `UsernamePasswordAuthenticationToken(principal, null, authorities)` với authority `role:<tên>` và `perm:<tên>` (201-210).
   - Nếu BFF bật: `OidcPrincipalBridgeFilter` sau `OAuth2LoginAuthenticationFilter` (256-258) đổi OidcUser thành `ErgPrincipal` (tenant lấy từ claim `tenant_id|tenantId|tenant` hoặc `erg.auth.bff-tenant-id`; header `X-Tenant-ID` chỉ để đối chiếu — `OidcPrincipalBridgeFilter.java:67-74,105-115`; MFA bắt buộc cho role chứa admin/teacher/instructor/employee/manager/staff — `OidcMfaPolicy.java:12-31`).
   - `authorizeHttpRequests` (97-254): so khớp theo thứ tự khai báo; `permissions.require(...)` / `requireAny(...)` gọi `AuthorizationOperations.isAllowed` => `ErgPolicyService`. Thiếu quyền => `accessDeniedHandler` ghi `403 ACCESS_DENIED` bằng `SecurityErrorResponseWriter` (94-95); chưa xác thực => `401 AUTHENTICATION_REQUIRED` hoặc `AUTH_SESSION_REPLACED` (80-93).
3. **TenantContextFilter** (`@Order(LOWEST_PRECEDENCE)`, `TenantContextFilter.java:26-49`): nếu có `ErgPrincipal` thì `TenantContext.set(tenantId, userId, portal đầu tiên khác "*")`, `finally TenantContext.clear()`.
4. **RequestIdFilter**, **ApiAccessLogFilter**, **HibernateQueryCountFilter**, **MediaUploadSizeLimitFilter**, **GraphQlGuardFilter**: `OncePerRequestFilter` dạng `@Component` không có `@Order`.
5. **Controller/Service** (ngoài slice): `@Transactional("postgresTransactionManager")` => `TenantAwareJpaTransactionManager.doBegin` gọi `TenantGucApplier.apply` chạy `set_config('app.tenant_id'|'app.user_id'|'app.portal'|'app.maintenance_mode', ..., true)` (transaction-local) (`config/persistence/TenantAwareJpaTransactionManager.java:37-67`, `TenantGucApplier.java:34-49`). Nếu áp GUC lỗi => `IllegalStateException`, transaction bị huỷ (63-66).
6. **Lỗi**: `GlobalExceptionHandler` chuyển exception thành `ApiResponse` + header `X-Request-ID` (xem 3.3). Phản hồi 401/403 từ chain đi qua `SecurityErrorResponseWriter` (cùng dạng).

### 3.2 Xác thực bearer chi tiết (session opaque, không phải JWT thuần)

`ErgPrincipalService.fromBearerToken(token, tenantId)` (`platform/security/ErgPrincipalService.java:51-77`):
- Băm token bằng `AuthTokenService.hashToken`; `resolveTenantId` gọi `JdbcAuthSessionTenantResolver` (hàm SQL SECURITY DEFINER `erg_resolve_session_tenant`) để lấy tenant duy nhất; kết quả không tồn tại hoặc nhập nhằng => từ chối (không rơi về tenant mặc định) (`ErgPrincipalService.java:79-94`, `JdbcAuthSessionTenantResolver.java:12-31`).
- Tenant client gửi phải trùng tenant của session, nếu khác => `Optional.empty()` (69-72).
- Session hợp lệ khi: chưa bị revoke, `expiresAt` còn hạn, `accessTokenExpiresAt` còn hạn (hoặc null cho session cũ), user `ACTIVE` (177-190).
- Principal: roles (user.roles + role-assignment đang hiệu lực, lower-case), permissions = hợp các role, rồi áp `UserPermissionOverride` (hết hạn thì bỏ; "grant/allow/rỗng" thêm vào granted và gỡ khỏi denied; ngược lại thêm vào denied) (247-281); assignment cộng thêm quyền của role và gỡ khỏi denied (346-359); `accessLevel = "admin"` nếu có `*` (208); scope: EDUCATION_UNIT từ `UserAccessScope` (module chứa `lms`, rỗng hoặc `*`) + assignment scope CENTER/SCHOOL; CLASS từ assignment scope CLASS; permission `*` => `["*"]` (306-329).
- Lý do thu hồi phiên được chẩn đoán cho client: `session_replaced` => `AUTH_SESSION_REPLACED`; `status_changed_inactive` => `ACCOUNT_DISABLED` (167-175) — chỉ khi không có `tenantResolver` (đường tương thích), xem R-14.

### 3.3 Chuỗi lỗi (REST)

`GlobalExceptionHandler` (`exception/GlobalExceptionHandler.java`):

| Exception | Status | `errors` | Dòng |
|---|---|---|---|
| `MethodArgumentNotValidException` | 422 | mảng field `{field, tag, actual_tag, message}` | 27-53 |
| `ConstraintViolationException` | 422 | mảng field (`ConstraintViolation`) | 55-77 |
| `MissingServletRequestParameterException` | 400 `INVALID_REQUEST` | mã | 79-94 |
| `HttpMessageNotReadableException` | 400 `INVALID_REQUEST` ("invalid JSON body") | mã | 96-111 |
| `QuizPublishService.ValidationFailedException` | 422 | mảng field | 113-130 |
| `ApiException` | status/mã của exception; `SyncConflictException` => `errors` kiểu `Conflict`; `AUTH_TOO_MANY_ATTEMPTS` thêm `Retry-After: 60` | 132-156 |
| `ObjectOptimisticLockingFailureException` / `OptimisticLockException` | 409 `STALE_WRITE` | 158-172 |
| `DataIntegrityViolationException` | 409 `IDEMPOTENCY_CONFLICT` nếu chuỗi cause chứa "idempotency", ngược lại `DATA_CONFLICT`; không lộ tên constraint | 174-208 |
| `AccessDeniedException` | 403 `ACCESS_DENIED` | 210-226 |
| `AuthenticationCredentialsNotFoundException` | 401 `AUTHENTICATION_REQUIRED` (kể cả `WebhookVerificationException`, `AuthenticationRequiredException`) | 228-244 |
| `NoResourceFoundException`, `NoHandlerFoundException` | 404 `NOT_FOUND` | 246-280 |
| `Exception` | 500 `INTERNAL_SERVER_ERROR` (log đầy đủ kèm requestId, không lộ message) | 282-296 |

`requestId` lấy qua `RequestCorrelation.current(request)` (317-323), nên mọi lỗi mang cùng `request_id` như header `X-Request-ID`.

### 3.4 Luồng GraphQL

1. Security: chỉ `POST /api/v1/graphql` và phải `authenticated()` (`SecurityConfig.java:131`); đường dẫn do `spring.graphql.http.path` (mặc định `/api/v1/graphql`, `application.yml:96-98`).
2. `GraphQlGuardFilter` (`graphql/GraphQlGuardFilter.java:56-139`) theo thứ tự: POST => `Content-Length`/đọc stream <= `maxRequestBodyBytes` (256 KiB) => JSON object => `query` bắt buộc, <= `maxQueryLength` (20 000) => độ sâu `{` <= `maxDepth` (8) => số alias <= `maxAliases` (20) => `operationName` bắt buộc (mặc định) => rate limit (429 + `Retry-After`) => chặn `__schema`/`__type` khi introspection tắt (403) => chặn biến có tên chứa password/secret/token... (400) => mảng biến <= `maxVariableArrayItems` (500). Lỗi trả dạng `{"errors":[{"message","extensions":{"code"}}]}` (không dùng `ApiResponse`).
3. `GraphQlContextInterceptor` dựng `GraphQlRequestContext` (`GraphQlRequestContextFactory.java:22-72`): từ chối nếu header `X-Tenant-ID` khác tenant principal (`AccessDeniedException`) hoặc `X-Portal` không thuộc portals của principal (`*` cho phép tất cả); `requestId` lấy từ `X-Request-ID` rồi `X-Trace-ID` (63-64). Thực thi bọc `timeout(erg.graphql.execution-timeout=5s)`.
4. Instrumentation: `MaxQueryDepthInstrumentation(max(1,maxDepth))`, `MaxQueryComplexityInstrumentation(max(1,maxComplexity=500))`, `GraphQlDeadlineInstrumentation` (`GraphQlExecutionGuardConfiguration.java:19-25`).
5. Resolver gọi `GraphQlAccessSupport` (`assertPermission` => `ErgPolicyService.isAllowedAny(principal, null, tenantId, permissions)`); lỗi qua `ErgGraphQlExceptionResolver`: `ApiException`, `ConstraintViolationException` (422 `VALIDATION_ERROR`), `AccessDeniedException` (403), `AuthenticationCredentialsNotFoundException` (401), còn lại 500 `INTERNAL_SERVER_ERROR` (log) (`ErgGraphQlExceptionResolver.java:48-74`).

### 3.5 Luồng cache (đọc với `@Cacheable`)

`ErgTieredCache.get(key, loader)` (`cache/ErgTieredCache.java:110-127`): `lookup` (L1 -> L2) -> nếu miss, `CacheLoadCoordinator.load` (single-flight theo `LoadKey(cacheName, tenant+key)`), recheck, gọi loader, `put` nếu khác null (hoặc `cacheNulls`). `lookup` (179-204): L1 hit trả ngay; L2 chỉ khi `redisKey != null` (generation >= 0 và tenant hợp lệ); lỗi Redis đi qua `CacheFailurePolicy` trả `null`; dữ liệu hỏng => xoá khoá Redis và coi như miss.
Vô hiệu hoá: `clear()` (155-169) xoá L1 của tenant hiện tại rồi `CacheInvalidationService.clearTenantCache` — nếu đang trong transaction thì chỉ chạy `afterCommit`, rollback thì không tăng generation (`cache/CacheInvalidationService.java:34-41,49-62`); `apply` tăng generation và publish thông điệp (65-71); instance khác nhận qua `RedisCacheInvalidationSubscriber` (`acceptRemote` lấy max, xoá L1 theo tenant) (24-35).

### 3.6 Luồng webhook

`POST /webhooks/discord|telegram` (permitAll, `SecurityConfig.java:106`) -> `WebhookController` đọc `byte[]` thô -> kiểm kích thước (`WebhookProperties.maxBodyBytes`) -> xác thực chữ ký/secret -> parse payload -> `WebhookInboxService.accept` (claim replay + insert `webhook_inbox` pending) -> `202 Accepted`. Khoá replay: Discord = `payload.id` (hoặc chữ ký nếu thiếu id), Telegram = `update_id`.

---

## 4. Quy tắc

### 4.1 Tenant isolation
- **Tenant lấy từ phiên đã xác thực, không tin client**: `fromBearerToken` bỏ kết quả nếu `X-Tenant-ID` khác tenant của session (`ErgPrincipalService.java:69-72`); `AuthenticatedTenant.require` ném `AuthenticationRequiredException` khi rỗng, không có tenant mặc định (`AuthenticatedTenant.java:9-12`); `TenantContext.of` dùng nó (`TenantContext.java:35-38`); GraphQL: `verifiedTenantId` trả tenant principal và từ chối header khác (`GraphQlAccessSupport.java:33-40`); OIDC: header chỉ để đối chiếu (`OidcPrincipalBridgeFilter.java:69-74`).
- **RLS PostgreSQL**: GUC `app.tenant_id|app.user_id|app.portal|app.maintenance_mode` được set transaction-local cho mỗi transaction (`TenantGucApplier.java:21-49`); runtime phải dùng role không-owner, Flyway dùng credential owner riêng (javadoc `TenantGucApplier.java:16-17`, `FlywayConfig.java:43-44`). Không có `TenantContext` (route công khai, job nền) => GUC không được set và policy "rơi về permissive" theo javadoc (`TenantAwareJpaTransactionManager.java:19-23`) — xem R-13.
- **Tác vụ nền**: `TenantContext.maintenance()` (`__maintenance__`, cờ `maintenance=true`, "never for HTTP requests" — `TenantContext.java:44-47`); `SystemActorContext.forTask(task, tenantId)` tạo `UserAccessContext` `system:<task>` với scope `*` (cần task + tenant không rỗng — `SystemActorContext.java:9-24`).
- **Luồng bất đồng bộ**: `ErgReadTaskExecutor` chỉ mang `TenantContext.Carrier` (tenant/user/portal/maintenance) và `RequestExecutionContext`, không mang principal/SecurityContext (`ErgReadTaskExecutor.java:58-72`; test `TenantContextPropagationContractTest`).
- **Cache**: khoá L1 là `ScopedCacheKey(tenantId, key)` (`ErgTieredCache.java:206-208`); khoá L2 chứa hash tenant + generation theo tenant (`CacheKeyFactory.java:38-40`); tất cả descriptor hiện hữu `tenantRequired=true`; test `noCacheNameLooksLikeSecretOrSessionMaterial` (không cache bí mật/session).
- **Cache key scope**: `UserAccessContext.scopeFingerprint()` (SHA-256 của scope) được thiết kế để đưa vào cache key (`AccessScopeSet.java:150-160`, test `AccessScopeCacheKeyContractTest`).
- **Tra tenant trước RLS**: chỉ thông qua hàm SQL hẹp `erg_resolve_session_tenant` (SECURITY DEFINER) (`JdbcAuthSessionTenantResolver.java:12`).

### 4.2 Phân quyền ở tầng platform
- **Hai lớp**: (1) route-level trong `SecurityConfig` (`permissions.require/requireAny`), (2) method-level `@EnableMethodSecurity` + `MethodAuthorizationEvaluator` (bean `methodAuthorization`) với portal `null`; (3) scope dữ liệu (đơn vị giáo dục/lớp) ở tầng service (`ErgPolicyService.matrix()` tự khai `lmsScopeEnforcedInServiceLayer: true` — dòng 77).
- **Deny thắng grant**: `decide()` tính `denied` trước, `granted = !denied && (baseline || match)` (`ErgPolicyService.java:27-43`); `isAllowedAny` từ chối ngay nếu bất kỳ phương án nào bị deny (54-70); test `specificDenyWinsAcrossAllRequireAnyAlternatives`.
- **Wildcard được hỗ trợ**: `*`, `prefix.*`, `*.suffix` (92-103); alias: `roles.* -> rbac.*`, `content.manage -> content.*|curriculum.*|cms.*|courses.*|media.*`, `system.logs -> audit.read` (13-21), đệ quy sâu tối đa 5 (112-114).
- **Baseline**: mọi principal đã xác thực có `profile.self` (25, 31); quyền sở hữu đối tượng ("thông báo của mình") do service kiểm tra bằng principal.
- **Workspace**: portal trong path (`/api/v1/admin|users` => admin; `/api/content|curriculum` => lcms; `/api/v1/elearning` => elearning; `/api/v1/lms` => lms; `/api/v1/users/me`, `/api/v1/sessions` => null) phải khớp workspace được cấp (`SecurityUtils.portalFromPath`). Workspace chỉ suy ra từ role/permission đã xác thực, không từ `portals` hay header (`WorkspaceCapabilityMapper.java:50-71`, `AuthorizationCapabilityPolicy.java:15-28`); `*` chỉ coi là "platform-wide" khi có role hệ thống (`system.super_admin|erg_super_admin|global_admin`) và không bị deny `*` (49-60). Một deny workspace (`admin.*`, `workspace.*`, `*`) xoá workspace tương ứng (`WorkspaceCapabilityMapper.java:115-124`).
- **Role bảo vệ**: tên role tạo mới không được bắt đầu bằng `system.` (`ProtectedRolePolicy.java:17-25`); gán/sửa role `system.*` cần principal có permission `*`; `system.god_mode` cấm gán (27-40).
- **Trusted-header auth**: tắt mặc định, bật ở profile `test`, và `TrustedHeaderAuthProductionGuard` làm khởi động thất bại nếu bật cùng `prod` (`ErgSecurityProperties.java:16`, `TrustedHeaderAuthProductionGuard.java:23-30`).
- **Route công khai có chủ đích** (`SecurityConfig.java:98-130,170`): health/ready/metrics, `v3/api-docs`, `scalar`, robots/sitemap; webhook POST; `auth/login|refresh` (POST) và `GET /api/v1/auth/login`, `/oauth2/**`, `/login/oauth2/**`; GET của `pages`, `menus/structure`, `posts` (trừ `hidden|trash|preview` cần quyền `cms.posts.update|delete|*`), `courses`, `elearning/categories|levels`, `recruitment/jobs`, `reviews` (+ `stats`; `/reviews/admin*` cần đăng nhập), `public-disclosure`, `trending`; POST `recruitment/apply`, `reviews`, `reviews/*/helpful`; `PUT /api/v1/lcms/media/upload/quiz-media/**` (URL ký sẵn; có `MediaUploadSizeLimitFilter`). Test `PublicRoutePolicyContractTest` khoá thứ tự matcher và yêu cầu `@ReviewedPublicEndpoint`.
- **Permission theo nhóm route chính**: roles/permissions => `rbac.role.*|rbac.permission.read`; `/api/v1/users/**` => `users.manage`; `/users/me` => `account.self.*|profile.self`; CRM => `crm.school.read|crm.analytics.read|crm.deal.manage`; content/curriculum => `content.*`/`lms.resource.*`/`curriculum.manage`; operations/audit => `operations.manage`; migration/platform => `platform.read`; crawler/rss => `crawler.manage`; notifications => `lms.notification.*|profile.self|notifications.manage`; e-learning => `elearning.manage`; seo => `seo.manage`; documents => `documents.manage`; ai-content => `ai-content.use`, `api-keys.manage`.
- **LMS/LCMS**: không có matcher riêng trong SecurityConfig, rơi về `/api/v1/**` `.authenticated()` (252); quyền chi tiết do method security/service (xác nhận gián tiếp qua test `SecurityHardeningTest` và `RuntimePermissionCatalogContractTest`).

### 4.3 Rate limit
- **GraphQL**: cửa sổ cố định theo đồng hồ 60 giây, giới hạn = `requestsPerMinute (120) + burst (30)` = 150 (`GraphQlRateLimiter.java:56-58`); khoá = SHA-256(tenant, user, operationName); người dùng chưa xác thực dùng `X-Tenant-ID` + hash header Authorization hoặc IP (`GraphQlGuardFilter.java:142-167`). Backend: Redis nếu `erg.graphql.rate-limit.redis-enabled=true` **và** có bean router (cần `erg.redis.state.enabled=true`) (60-63); `INCREX` (Redis 8.8) nếu hỗ trợ, ngược lại Lua (`GraphQlRateLimitCounterRouter.java:21-26`); lỗi Redis => từ chối (`fail-closed-on-redis-error=true`) hoặc rơi về bộ đếm cục bộ Caffeine (tối đa 20 000 khoá, hết hạn sau 1 phút không truy cập) (`GraphQlRateLimiter.java:87-92`, `LocalEmergencyRateLimitCounter.java:13-16`).
- **Đăng nhập**: thuộc tính `erg.auth.login-rate-limit` (nguồn 30/phút, tài khoản 10/phút, thiết bị 20/phút) và `rapid-session-replacement` (ngưỡng 5 trong 10 phút) khai báo ở `ErgAuthProperties.java:55-89`; bộ giới hạn thực thi nằm ngoài slice (module auth). Chú ý `erg.auth.login-rate-limit` được đặt trong yml chỉ ở profile `prod` (`application.yml:552-559`), ngoài ra dùng default Java.
- **HTTP chung**: `erg.http.rate-limit.*` có trong yml (`application.yml:349-352`) nhưng **không thấy** class nào trong slice đọc khoá này (R-12).
- `AuthFailure.TooManyAttempts` => 429 + `Retry-After: 60` cố định (`GlobalExceptionHandler.java:152-154`).

### 4.4 CORS
- Danh sách origin gộp: cấu hình + `REQUIRED_ALLOWED_ORIGINS` (localhost:3000/3001/3003, 127.0.0.1, `*.erg.edu.vn:3001`) **ngoài prod**; ở profile `prod` chỉ dùng cấu hình (`web/CorsConfig.java:17-32,101-107`).
- Fail-closed khi khởi động: wildcard origin + credentials (139-150); origin `http://` + credentials trong prod (155-167).
- Header cho phép: chế độ thường = cấu hình + `Accept, Authorization, Content-Type, X-Tenant-ID, X-Request-ID, X-Trace-ID, X-Idempotency-Key, X-Portal` (34-43); chế độ BFF loại bỏ mọi header định danh (`Authorization`, `X-Tenant-ID`, `X-Portal`, `X-User-ID`, `X-Roles`, `X-Permissions`, ...) và chỉ thêm `X-XSRF-TOKEN` (45-52, 119-131).
- `maxAge <= 0` => 1 giờ (114); mặc định cấu hình 86 400s (`ErgHttpProperties.java:41`); `exposedHeaders` mặc định `X-Request-ID, X-Trace-ID, Deprecation, Sunset, Link, X-ERG-API-Owner`.
- `allowCredentials` mặc định `true` (`ErgHttpProperties.java:38`).

### 4.5 Validation
- Bean Validation => `GlobalExceptionHandler` trả 422 `VALIDATION_ERROR` với mảng `{field, tag, actual_tag, message}` (sắp xếp theo field rồi tag, ổn định thứ tự) (27-53). Cấu hình có validate khi khởi động: `@Validated` + `@AssertTrue` trong `ErgRedisProperties`, `ErgAuthProperties`, `ErgHttpProperties`, `ErgReadExecutorProperties`, `ErgModuleEventsProperties`, và setter kiểm biên trong `ErgCacheProperties.setMaxInFlightLoads`.
- Phân trang: `PageRequestDto` chuẩn hoá page >= 0, size trong 1..100 (mặc định 20) (`PageRequestDto.java:14-24`).
- Sync offline: `idempotencyKey` <= 255, `operationId` <= 128, `entityType/entityId` <= 160; thiếu `idempotencyKey` => coi là request cũ không phát lại được (`LmsSyncOperationService.java:46-66`).
- Idempotency: cùng key nhưng khác `operationId` hoặc hash payload => 409 `IDEMPOTENCY_CONFLICT`; đã `SUCCEEDED` => trả kết quả phát lại (`replayed=true`); còn `PENDING` => 409 `SYNC_PENDING` (retryable) (67-75). Hash payload = SHA-256 trên JSON chuẩn hoá (map sắp xếp khoá) (127-134, 177-192).

### 4.6 Cache policy
- Không cache null (mọi descriptor `cacheNulls=false`); không cache giá trị > `maximumSerializedBytes` vào L2 (vẫn nằm L1) (`JacksonCacheValueCodec.java:24-27`).
- L2 tắt mặc định: `erg.cache.l2-enabled=false`, `l2-names` rỗng; chỉ cache có tên trong `l2-names` mới dùng Redis (`CacheConfig.java:86`, `ErgTieredCacheManager.java:74-75`).
- Lỗi Redis không làm hỏng request: `CacheFailurePolicy` mở mạch sau `circuit-failure-threshold` (3) lỗi liên tiếp trong `circuit-open-duration` (5s) và trả `null`/no-op (`CacheFailurePolicy.java:24-39`); mất generation (`-1`) => bỏ qua L2 (`ErgTieredCache.java:214-215`).
- Single-flight: giới hạn số khoá đang nạp `erg.cache.max-in-flight-loads` (1024); đầy => `RejectedExecutionException` bọc thành `ValueRetrievalException` (`CacheLoadCoordinator.java:38-41`).
- Vô hiệu hoá chỉ sau commit (xem 3.5).
- Khoá cache không chứa token; test `noCacheNameLooksLikeSecretOrSessionMaterial` kiểm tên cache.

---

## 5. Cấu hình quan trọng (không ghi giá trị bí mật)

### 5.1 Cấu trúc profile của `application.yml` (quan trọng)
File gồm nhiều document bằng `---` (các dòng: 119, 465, 477, 531, 586):

| Document | Dòng | Kích hoạt | Nội dung |
|---|---|---|---|
| Chung | 1-117 | luôn | `spring.*` (datasource, hikari, flyway, JPA, mail, graphql), `server.address|port|forward-headers-strategy=framework|shutdown=graceful` |
| `staging` | 119-463 | `on-profile: staging` | `server.tomcat|error`, `management.*`, `springdoc`, `scalar`, `logging`, và **toàn bộ khối `erg.*`** (mail, observability, webhook, module-events, cache, quiz, redis, process-local, outbox, reporting, performance, openapi, notifications, auth, http (CORS, trusted proxy, rate-limit), queue, r2, ai, telemetry, logging, graphql, scraper, trending, compose, tenant, lifecycle) |
| `dev` | 465-475 | `dev` | `generate_statistics: true` |
| `test` | 477-529 | `test` | tắt scheduling, H2 PostgreSQL-mode, tắt worker, `erg.security.trusted-header-auth-enabled: true` |
| `prod` | 531-584 | `prod` | introspection tắt, `erg.process-local.enabled`, `erg.auth.login-rate-limit|rapid-session-replacement|refresh-token-history|cookie(secure, strict)`, `springdoc/scalar` tắt, `server.error.*: never`, log INFO |
| `redis` | 586-615 | `redis` | bật `erg.redis.state|cache`, `erg.cache.l2-enabled`, `erg.graphql.rate-limit.redis-enabled` |

Hệ quả cần kiểm chứng: xem R-01.
Các file khác: `application-local.yml` (bật Flyway, `generate_statistics`, lộ message lỗi, bật springdoc/scalar, `erg.process-local.enabled=true`); `application-enterprise-auth.yml` (BFF: `erg.auth.bff-enabled=true`, `erg.auth.local-login-enabled=false`, OAuth2 client Keycloak `erg-api`, `spring.session.redis.*` namespace `erg:session`, cookie `__Host-erg_session` http-only/secure/lax, CORS origin riêng + `X-XSRF-TOKEN`).

### 5.2 Khoá cấu hình theo nhóm (mặc định trong code Java / yml)

**Bảo mật & auth**
- `erg.security.trusted-header-auth-enabled` = `false` (chỉ `true` ở `test`).
- `erg.auth.bff-enabled` = `false`; `erg.auth.bff-tenant-id` = `erg` (không được rỗng); `erg.auth.local-login-enabled`.
- `erg.auth.login-rate-limit.{enabled=true, source-per-minute=30, account-per-minute=10, device-per-minute=20}`; `erg.auth.rapid-session-replacement.{threshold=5, window-minutes=10}`.
- `erg.auth.cookie.{enabled, secure, same-site, session-name=__Host-erg_session, domain}`; `erg.auth.{max-failed-login=5, failed-login-window=15m, block-duration=15m, geo-block-enabled=true, allowed-continents=AS, refresh-token-ttl=7d}`; `jwt-*`, `argon2-*` (giá trị bí mật qua biến `SECRET_*`, không ghi ở đây).
- `erg.observability.prometheus-public-enabled` = `false`; `management.endpoints.web.exposure.include` = `health,info,prometheus`.
- `erg.logging.mask-sensitive` = `true`.

**HTTP/CORS**
- `erg.http.trusted-proxy-cidrs` = rỗng (mặc định không tin proxy nào); `erg.http.cors.{allowed-origins, allowed-methods=GET,POST,PUT,DELETE,PATCH,OPTIONS, allowed-headers, exposed-headers, allow-credentials=true, max-age=86400}`.
- `erg.http.rate-limit.*` (có trong yml, không thấy nơi đọc — R-12).

**GraphQL** (`ErgGraphQlProperties.java:11-21`)
- `erg.graphql.{require-operation-name=true, introspection-enabled=false, max-query-length=20000, max-request-body-bytes=262144, max-depth=8, max-complexity=500, execution-timeout=5s, max-aliases=20, reject-sensitive-variables=true, max-variable-array-items=500}`; `erg.graphql.rate-limit.{enabled=true, redis-enabled=false, fail-closed-on-redis-error=true, requests-per-minute=120, burst=30}`.
- `spring.graphql.http.path=/api/v1/graphql`; `spring.graphql.graphiql.enabled=false`; `spring.graphql.schema.locations=classpath:/graphql/`; introspection schema tắt (ghi đè cứng `false` ở `prod`).

**Cache & Redis**
- `erg.cache.{l2-enabled=false, l2-names=[], circuit-open-duration=5s, circuit-failure-threshold=3, max-in-flight-loads=1024}`.
- `erg.redis.state.{enabled=false, url, connect-timeout=1s, command-timeout=1s, pool-size=16, min-idle=4}`; `erg.redis.cache.{enabled=false, url, connect-timeout=250ms, command-timeout=500ms, pool-size=24, min-idle=4}`; `erg.redis.metrics.refresh-interval=15s`.
- URL Redis chỉ qua biến môi trường (không hard-code); state có thể dùng `SPRING_DATA_REDIS_URL` làm fallback, cache **không** có fallback.
- Bật phân tán: profile `redis` (`SPRING_PROFILES_ACTIVE=prod,redis`, xem `docs/ponytail/redis-enable-runbook.md`).

**Webhook**: `erg.webhook.{max-body-bytes=65536, replay-ttl=24h, timestamp-tolerance=5m, discord-public-key, telegram-secret-token}` (hai khoá cuối là bí mật, mặc định rỗng => mọi webhook bị từ chối).

**Concurrency/Events**
- `erg.performance.read-executor.{core-size, max-size, queue-capacity, shutdown-timeout}`: mặc định Java 8/16/64/5s (`ErgReadExecutorProperties.java`); yml đặt 4/4/32/5s.
- `erg.module-events.recovery.{enabled=true, fixed-delay=30s, batch-size=100 (<=1000), max-in-flight=16 (<=128), min-age=30s}`; `io.{core-size=8, max-size=16, queue-capacity=0 (<=128)}`; `cpu.{parallelism=4, queue-capacity=32 (<=256)}`.
- Spring Modulith: `completion-mode: delete`, `registry-trigger-annotation: ApplicationModuleListener`, schema do Flyway quản (V73), `detection-strategy: explicitly-annotated` + `runtime.verification-enabled: true` chỉ ở `staging`.

**Persistence**
- `spring.datasource.{url,username,password}` bắt buộc (không có mặc định); Hikari: `maximum-pool-size` (`DB__MAX_OPEN_CONNS`, mặc định 25), `minimum-idle` 5, `connection-timeout` 5000ms, `auto-commit=false`.
- `spring.jpa.open-in-view=false`, `ddl-auto=validate`, batch fetch 100, JDBC batch 50, timeout JDBC 5s, query timeout 5000ms, múi giờ Hibernate `Asia/Ho_Chi_Minh`.
- `spring.flyway.{enabled=true (FLYWAY_ENABLED), locations=classpath:db/migration, user/password riêng nếu có}`; `FlywayConfig` mặc định `classpath:db/migration/postgres`, không-prod và có thư mục `src/main/resources/db/migration/postgres` thì dùng `filesystem:` (`FlywayConfig.java:59-69`).
- `spring.autoconfigure.exclude` mặc định loại Mongo và Data Redis (Redis được tự quản bởi `RedisInfrastructureConfig`).
- `spring.threads.virtual.enabled` mặc định `false`.

**Cờ tính năng/vận hành khác**: `erg.process-local.enabled` (false trừ `local`); `erg.lifecycle.*-on-startup` (đều `false`); `erg.outbox.worker.*`, `erg.reporting.worker.*`, `erg.reporting.reconciliation.*`, `erg.notifications.push.*` (bật bằng `NOTIFICATIONS__PUSH__ENABLED`), `erg.tenant.enabled=false` (`TENANT__ENABLED`; không thấy class platform nào đọc — chưa xác minh nơi dùng), `erg.compose.enabled`, `erg.telemetry.enabled`, `erg.r2.enabled`, `erg.openapi.server-url`.

---

## 6. Test hiện có

### 6.1 Architecture (`src/test/java/com/erg/ergspringboot/architecture`)
Rule thực sự được enforce:
- `ArchitectureRatchetTest` (`:47-137`):
  - `BaseEntity` chỉ có đúng 3 field `createdAt, id, updatedAt`, `onCreate` có `@PrePersist`, `onUpdate` có `@PreUpdate` (47-58).
  - Import vào `<module>.internal.*` từ package bên ngoài module không được tăng quá baseline `internal-access-baseline.tsv`; mỗi dòng baseline phải có owner và `expiresOn >= hôm nay` (60-71). Hiện chỉ có 1 dòng: `service/organization/EducationUnitService.java -> organization.internal.OrganizationEducationUnitQuery`, owner BE-048, **hết hạn 2026-09-30** (R-15).
  - Số file Java của các package ngang "legacy" không được vượt trần: `controller=59, service=116, repository=133, model=182, common=48` (`horizontal-package-baseline.properties`) (73-84).
  - Chỉ entity JPA mới được `extends BaseEntity` (86-103).
  - `docs/architecture/module-catalog.md` phải khớp chính xác danh sách module Modulith phát hiện được (105-117).
  - `@Service` dùng `ConcurrentHashMap` phải có `@Profile("prototype")` (119-137).
- `ModulithVerificationTest`: 17 module bắt buộc tồn tại (calendar, crm, elearning, reporting, authorization, users, auth, lms, lcms, curriculum, learningcontent, notifications, organization, school, audit, learningoutcomes, learningstandards) và `modules.verify()` (strategy `explicitly-annotated`).
- `DependencyDirectionArchTest` (ArchUnit): controller `calendar` không phụ thuộc `repository/model/jakarta.persistence`; controller `reporting` thêm không phụ thuộc `service`; `LcmsQuestionBankAuthoringController`, `LcmsQuizAuthoringController`, `LcmsMediaController` chỉ qua facade (không `repository/model/jakarta.persistence/service`).
- `ModulePublicPackageDirectionTest`: package `lcms` công khai không import `service./repository./model./jakarta.persistence.` ngoại trừ 2 adapter `@Deprecated` (`LcmsCommandCompatibility`, `LcmsReadCompatibility`); `CalendarReadPort` không lộ kiểu `model.*`.
- `ModulePublicApiContractTest`: constructor các `*Operations` (Auth, Curriculum, AuthorizationCommand, User, Crm, Elearning, LearningContent) đúng chữ ký; các service cũ là adapter `@Deprecated` không dùng `.internal.`; facade `calendar`/`reporting` không lộ `@Entity/Repository`.
- `PublicApiTypeContractTest`: controller REST không được dùng `Map` trong tham số/kiểu trả về, ngoài allowlist 4 phương thức (`AuthController#logout|logoutAll`, `NotificationDeviceController#revoke|unregister`).
- `RouteInventoryContractTest`: số route REST/GraphQL trong `api-performance-catalog.yaml` khớp `docs/architecture/api-inventory.md`; mỗi controller có owner; mỗi classification có chính sách.
- `ProductionPrototypeExposureTest`: 13 controller + 4 service nguyên mẫu phải có `@Profile("prototype")`.
- `ProductionCapabilityStubContractTest`: import Excel lịch trả `NotImplementedException("CALENDAR_EXCEL_IMPORT_NOT_IMPLEMENTED")` thay vì `UnsupportedOperationException`.
- `EventBoundaryContractTest`, `EventPublicationRegistryContractTest` (Flyway V73 sở hữu bảng `event_publication`, lịch không nhân đôi qua outbox cũ), `ModulithDocumentationTest` (tài liệu + sinh PlantUML/canvas), `SpringModulithDependencyContractTest` (Modulith 2.1.0 và các starter).

### 6.2 Platform / security
- Xác thực/tenant: `SecurityHardeningTest` (482 dòng; chặn header giả mạo, BFF từ chối bearer, tenant lookup phục hồi context, `AUTH_SESSION_REPLACED`, principal bị hạn chế vòng đời bị 403, resolver tenant nghiệp vụ không fallback `erg` trong một số thư mục), `ErgPrincipalServiceTest`, `OidcPrincipalBridgeFilterTest`, `TenantContextTest`, `TenantGucApplierIntegrationTest`, `TenantRlsIsolationIntegrationTest`, `TenantRlsMatrixIntegrationTest` (mọi bảng tenant có policy đọc/ghi chặt; catalog gate), `TenantMigrationPolicyContractTest`, `StrictRlsMigrationContractTest`.
- Quyền: `AuthorizationCapabilityPolicyTest`, `PermissionAuthorizationManagerTest`, `ProtectedRolePolicyTest`, `WorkspaceCapabilityMapperTest`, `LegacyNamespacePolicyTest`, `MethodAuthorizationEvaluatorTest`, `RuntimePermissionCatalogContractTest` (mọi runtime permission đều được seed), `PublicRoutePolicyContractTest`, `PortalErrorEnvelopeIntegrationTest` (401 cùng phong bì cho các portal), `ErgPrincipalConstructionContractTest`.
- Khác: `TrustedClientIpResolverTest`, `PrometheusAccessRequestMatcherTest`, `SecurityErrorResponseWriterTest`, `UserAccessContextFailClosedTest`, `AccessScope*Test`, `GraphQlSecurityGuardContractTest`, `GraphQlEnterpriseSecurityAuditTest`, `WebhookSecurityBoundaryTest` (nội dung: `SecurityConfig` không chứa chuỗi `"/webhooks/**"`; không kiểm tra bản thân permitAll của hai đường dẫn cụ thể).
- Cache: `ErgTieredCacheTest`, `CacheKeyFactoryTest`, `CacheStampedeIntegrationTest`, `CacheGenerationIntegrationTest`, `PostCommitInvalidationTest` (rollback không tăng generation), `BusinessCacheIsolationIntegrationTest` (descriptor tenant-safe, tên không giống bí mật), `BusinessCacheInvalidationIntegrationTest`, `JacksonCacheValueCodecTest`, `CacheNativeImageContractTest`.
- GraphQL: `GraphQlRequestContextFactoryTest`, `GraphQlAccessSupportPermissionTest`, `ErgGraphQlExceptionResolverTest`, `GraphQlDeadlineInstrumentationTest`, `GraphQlRateLimiterTest`, `FixedWindowRateLimitCounterIntegrationTest` (INCREX vs Lua), `GraphQlModuleAssemblyContractTest` (schema lắp từ module khớp bản canonical), `GraphQlPolicyUnificationContractTest` (GraphQL dùng cùng policy với REST).
- Web: `ApiAccessLogFilterTest`, `CorsConfigSecurityTest`, `LegacyApiDeprecationFilterTest`, `RequestIdFilterTest`, `PlatformHttpSmokeTest` (health/ready/metrics, CORS preflight CRM/LCMS, scalar).
- Config: `ApplicationYamlMigrationTest`, `ProductionConfigurationSafetyTest`, `GraphQlEndpointConfigurationTest`, `TypedConfigurationContractTest`, `FlywayConfigTest`, `FlywayMigrationAppendOnlyContractTest` (migration liên tục, không gap, không down), `FlywayUpgradePathIntegrationTest`, `JpaRegistrationCatalogTest`, `PostgresJpaConfigTest`.
- Khác: `ErgReadTaskExecutorTest`, `ExecutorResourceContractTest`, `ModuleEventRecoveryJobTest`, `ModuleEventMetadataTest`, `RedisHealthAndMetricsTest`, `RedisInfrastructureConfigTest`, `JsonbDurabilityCatalogTest`, `ProcessLocalStatePolicyTest`, `LmsSyncOperationServiceTest` (chỉ kiểm hash), `WebhookSecurityTest`, `GlobalExceptionHandlerTest` (gọi handler trực tiếp, 8 case), `TransportContractTest`, `SaasProductizationGateHarnessTest` (harness mô phỏng, xác nhận **không** có runtime SaaS: `platform/saas` không tồn tại).
- Nhiều test hạ tầng/vận hành (`platform/performance/*`: k6, Kubernetes, Docker, OpenAPI snapshot, query plan) thuộc slice khác về nội dung; chỉ ghi nhận có tồn tại.

### 6.3 Khoảng trống rõ ràng (không thấy test trong slice)
- Không có test riêng cho `TenantContextFilter`, `MediaUploadSizeLimitFilter`, `HibernateQueryCountFilter`, `PlatformHealthController.ready` ngoài smoke, `TenantAwareJpaTransactionManager` (đường lỗi áp GUC), `ErgPolicyService` ở cấp quyết định `isAllowed` cho principal có `*` nhưng không có role hệ thống (R-05), `CacheFailurePolicy`, `CacheLoadCoordinator` (ngoài tích hợp), `RedisCacheGenerationService` (mất message pub/sub), `WebhookInboxService`, `DiscordEd25519SignatureVerifier` với chữ ký sai/khoá sai độ dài, `ModuleEventTaskExecutorConfig` (hành vi khi từ chối).
- `GlobalExceptionHandlerTest` không có case cho `HttpRequestMethodNotSupportedException`, `HttpMediaTypeNotSupportedException`, `MethodArgumentTypeMismatchException`, upload quá cỡ (R-02).
- Không có test xác minh thứ tự thực tế các servlet filter (R-09), không có test tích hợp cho `sendError` trong filter xác thực (R-03).
- `ProductionConfigurationSafetyTest`/`ApplicationYamlMigrationTest` kiểm tra bằng `String.contains` trên toàn file nên không phát hiện việc khoá nằm trong document profile nào (R-01).
- Không có test bao `LegacyApiRegistry` sunset đã quá hạn hoặc `LegacyNamespacePolicy.isCompatibilityActive` được dùng ở đâu (chưa tìm thấy chỗ gọi trong slice).

---

## 7. Rủi ro, nợ kỹ thuật, nghi ngờ bug

Mức: Cao / Vừa / Thấp. Mọi mục "nghi ngờ" chưa được xác minh bằng chạy ứng dụng (không được phép chạy trong nhiệm vụ này).

**R-01 (Cao, nghi ngờ). Khối `erg.*` nằm trong document YAML chỉ kích hoạt với profile `staging`.**
Bằng chứng: `application.yml:119-123` mở document `on-profile: staging`, document này kéo dài tới dòng 463 và chứa `management.*`, `server.tomcat`, toàn bộ `erg.*` (webhook, cache, redis, auth, http/CORS/trusted-proxy, graphql, module-events...). `Dockerfile:41` và `ops/docker/.env.production` đặt `SPRING_PROFILES_ACTIVE=prod`; `.env` cục bộ đặt `dev`. Với `prod`/`dev` không kèm `staging`, các placeholder trong khối này (ví dụ `${HTTP__CORS__ALLOWED_ORIGINS}`, `${HTTP__TRUSTED_PROXY_CIDRS}`, `${SECRET_DISCORD_WEBHOOK_PUBLIC_KEY}`, `${SECRET_TELEGRAM_WEBHOOK_TOKEN}`, `${AUTH__COOKIE__*}`) có vẻ không được nạp; chỉ biến môi trường có tên khớp relaxed binding (ví dụ `ERG_HTTP_CORS_ALLOWED_ORIGINS`, `ERG_GRAPHQL_*`, `ERG_REDIS_*`) mới tác dụng, còn lại về mặc định Java. Bằng chứng gián tiếp: commit `2ed0a08 fix(ops): bind ERG_HTTP_CORS_ALLOWED_ORIGINS in native compose for production CORS`, và `Dockerfile` phải tự đặt `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE`. Hệ quả cụ thể nếu đúng: prod mặc định CORS rỗng (do `CorsConfig` không gộp origin mặc định ở prod), `trusted-proxy-cidrs` rỗng (IP đăng nhập/giới hạn lấy `remoteAddr`), secret webhook rỗng (mọi webhook bị 401), `management.endpoint.health.show-details` không bị ép `never`. Cần xác minh bằng `--debug`/actuator env ở profile `prod`.

**R-02 (Vừa, nghi ngờ). Lỗi Spring MVC chuẩn bị gói thành 500.**
`GlobalExceptionHandler` không kế thừa `ResponseEntityExceptionHandler` và không có handler cho `HttpRequestMethodNotSupportedException`, `HttpMediaTypeNotSupportedException`, `MethodArgumentTypeMismatchException`, `MaxUploadSizeExceededException`... nên rơi vào `@ExceptionHandler(Exception.class)` trả 500 `INTERNAL_SERVER_ERROR` và log ERROR (`GlobalExceptionHandler.java:282-296`). Hệ quả: gọi sai method/content-type sẽ ra 500 thay vì 405/415/400, gây nhiễu cảnh báo.

**R-03 (Vừa, nghi ngờ). `sendError(...)` trong filter không đi qua phong bì `ApiResponse`.**
`ErgAuthenticationFilter.java:55,64,68` và `MediaUploadSizeLimitFilter.java:31` dùng `response.sendError`. Mã phản hồi có thể bị chuyển sang `/error` (bị `denyAll` ở `SecurityConfig.java:254` và/hoặc trả trang Tomcat); `ApiResponseNativeRuntimeHints.java:9-17` mô tả đúng triệu chứng "Tomcat HTML 401" khi native thiếu hint. Các mã nghiệp vụ `ACCOUNT_LIFECYCLE_RESTRICTED`, `BFF_COOKIE_AUTH_REQUIRED`, "Portal session does not match requested API." có thể không tới được client dạng mã ổn định. Unit test dùng `MockHttpServletResponse` nên chỉ thấy status.

**R-04 (Vừa). Khoá rate limit GraphQL chứa `operationName` do client tự chọn.**
`GraphQlGuardFilter.java:142-159` + `GraphQlRateLimiter.java:109-111`: khoá = (tenant, user, operationName). `operationName` chỉ được kiểm "không rỗng", không đối chiếu với tài liệu thật, nên một người dùng đã xác thực có thể đổi `operationName` mỗi request để mỗi tên có hạn mức riêng (150/phút/tên), vô hiệu hoá giới hạn theo người dùng. Bộ đếm cục bộ giới hạn 20 000 khoá (`LocalEmergencyRateLimitCounter.java:13-16`) nên bị lấp đầy cũng là bề mặt bộ nhớ. Hạn mức theo người dùng (không theo tên) mới đúng ý định tài liệu ở `GraphQlRateLimiter.java:15-25`.

**R-05 (Cao nếu đúng, nghi ngờ). `*` được coi là mọi quyền ngay cả khi không có role hệ thống ở `ErgPolicyService`.**
`AuthorizationCapabilityPolicy.isPlatformWide` yêu cầu role hệ thống + `*` + không deny `*` (49-60), và test `wildcardRequiresVerifiedSystemRoleAndExplicitDenyWins` khẳng định. Nhưng `ErgPolicyService.matchesAny(principal, ...)` (82-86) là `isPermissionWildcardAllowed(...) || matches(permission, required)` và `matches` trả `true` cho grant `"*"` vô điều kiện (92-94). Vì vậy điều kiện "chỉ role hệ thống" chỉ có hiệu lực ở lớp workspace (`hasWorkspace`), còn với route không có portal (portal `null` => `hasWorkspace` luôn `true`, `AuthorizationCapabilityPolicy.java:19-21`) thì permission `*` gán cho principal không có role hệ thống vẫn cấp quyền. Nguồn gán `*` được kiểm soát ở DB/`ProtectedRolePolicy` (cần `*` mới gán role protected), nên mức độ phụ thuộc quy trình cấp quyền; test `roleNameAloneDoesNotCreateRuntimeAuthority` chỉ phủ trường hợp "có role, không có permission". Cũng lưu ý `AccessScopeAssembler.from` coi `permissions.contains("*")` là toàn quyền scope (`AccessScopeAssembler.java:23-25`) mà không xét role hệ thống (bù bởi `canAccessScope` ở `AuthorizationCapabilityPolicy.java:38-46`, nhưng `AccessScopeAssembler.from` được gọi trực tiếp ở `UserAccessContext.from` và `GraphQlRequestContextFactory`).

**R-06 (Vừa). Cache L2 có thể phục vụ dữ liệu cũ khi mất thông điệp invalidation.**
`RedisCacheGenerationService.current` đọc cache cục bộ `local` và không bao giờ làm mới từ Redis nếu đã có (`:19-27`); chỉ cập nhật khi tự `increment` hoặc nhận pub/sub (`acceptRemote`). Nếu instance khác tăng generation mà thông điệp pub/sub bị mất (mất kết nối, restart sau), instance này vẫn đọc khoá theo generation cũ. Thêm nữa, `CacheInvalidationService.apply` bỏ qua publish khi `increment` trả `-1` (lỗi Redis) (`:65-71`) nên invalidation bị mất âm thầm. Giới hạn bởi TTL L2 = 4x L1: phổ biến 2-4 phút, nhưng `quizRuntimePackage` 40 phút, `curriculumTree` 60 phút. Đang tắt mặc định (L2 tắt), nên rủi ro chỉ có khi bật profile `redis`.

**R-07 (Thấp). Mã hoá JSON hai lần cho mỗi `put` và encode cả khi không dùng L2.**
`ErgTieredCache.put` luôn gọi `codec.encode` (`:137`) kể cả khi store là `NoOpRedisCacheStore`, và `estimateWeight` (weigher Caffeine, `:224-231`) encode lại mỗi lần ghi L1. Chi phí CPU/GC cho DTO lớn (tới vài MiB).

**R-08 (Thấp, nghi ngờ). L1 cache `tenantRequired` vẫn lưu khi không có tenant.**
`scoped()` tạo `ScopedCacheKey(null, key)` (`ErgTieredCache.java:206-208`) và `put` ghi L1 trước khi kiểm tra tenant (`:133`); chỉ L2 bị bỏ qua khi `tenantRequired && tenant == null` (`:212`). Gọi `@Cacheable` ngoài request/ngoài `TenantContext` có thể dùng chung một slot L1 "null tenant" giữa các ngữ cảnh.

**R-09 (Vừa, nghi ngờ). Thứ tự servlet filter chưa tường minh.**
Chỉ `LegacyApiDeprecationFilter` (`HIGHEST_PRECEDENCE + 30`) và `TenantContextFilter` (`LOWEST_PRECEDENCE`) có `@Order`. `RequestIdFilter`, `ApiAccessLogFilter`, `HibernateQueryCountFilter`, `MediaUploadSizeLimitFilter`, `GraphQlGuardFilter` là `@Component` không `@Order` => mặc định thấp nhất, tức chạy sau `springSecurityFilterChain` (thứ tự mặc định -100) và thứ tự giữa chúng không xác định. Hệ quả: request bị chặn ở chain (401/403/CORS) không có dòng access log và MDC `requestId`; `ErgAuthenticationFilter` (cũng là `@Component`) còn được Boot đăng ký như servlet filter, được `OncePerRequestFilter` chặn chạy lặp. `MediaUploadSizeLimitFilter` và `GraphQlGuardFilter` chạy sau xác thực (không ảnh hưởng đúng/sai logic, nhưng `MediaUploadSizeLimitFilter` đứng sau chain cho route permitAll).

**R-10 (Thấp). Access log tin tưởng header IP.**
`ApiAccessLogFilter.resolveClientIp` (`:83-98`) lấy `CF-Connecting-IP`, `X-Forwarded-For`, `X-Real-IP` vô điều kiện, trong khi bảo mật dùng `TrustedClientIpResolver`. Chỉ ảnh hưởng nhật ký (có thể bị giả mạo), không ảnh hưởng quyết định bảo mật. `X-Request-ID` do client cung cấp được dùng nguyên văn (chỉ trim, không giới hạn độ dài/ký tự) (`RequestCorrelation.java:24-30`).

**R-11 (Vừa). Webhook công khai đọc cả thân request rồi mới kiểm giới hạn.**
`WebhookController` nhận `@RequestBody byte[]` và gọi `enforceBodyLimit` sau đó (`controller/bot/WebhookController.java:38-60`); `permitAll` ở `SecurityConfig.java:106`. Khác với `MediaUploadSizeLimitFilter` (từ chối trước khi đọc), thân lớn có thể được nạp vào heap trước khi bị từ chối. Chặn dựa vào giới hạn Tomcat/ingress (chưa xác minh trong slice).

**R-12 (Thấp). Cấu hình có thể thành "cờ chết".**
`erg.http.rate-limit.*` (yml 349-352), `erg.tenant.*` (452-455), `erg.compose.*`, `erg.queue.*`... không có class nào trong slice đọc; có thể được dùng ở module khác nhưng chưa tìm thấy. Mã chết xác nhận: `SecurityConfig.requestPath` (`:269-272`), `ErgAuthenticationFilter.csvOrDefault` (`:242-245`), `TenantGucApplier.setLocal` (`:56-63`), `CacheConfig.cacheManager()` không phải bean (`:62-67`, dùng cho test), `TenantContext.DEFAULT_TENANT_ID` (hằng không thấy dùng ngoài khai báo).

**R-13 (Vừa, thiết kế). RLS "rơi về permissive" khi không có TenantContext.**
`TenantAwareJpaTransactionManager.java:19-23,43-47`: không có `TenantContext` thì không set GUC. Request công khai (CMS, đánh giá, tuyển dụng, webhook inbox, media upload) và job nền chạy không có tenant, nên bảo vệ tenant ở các truy vấn đó dựa vào code ứng dụng, không dựa vào RLS. `SecurityUtils.normalizeTenant` còn trả `"erg"` khi rỗng (`SecurityUtils.java:66-68`) và vẫn được dùng (`controller/documents/DocumentController.java:79`), trái tinh thần "không fallback tenant mặc định". Test `authenticatedBusinessTenantResolversNeverFallbackToErg` chỉ quét 4 thư mục.

**R-14 (Thấp, nghi ngờ). `authenticationFailureCode` trả rỗng khi có `tenantResolver`.**
`ErgPrincipalService.authenticationFailureCode(bearerToken)` (`:128-137`) trả `Optional.empty()` khi `tenantResolver != null` (tức production), nên nhánh `AUTH_SESSION_REPLACED`/`ACCOUNT_DISABLED` ở `ErgAuthenticationFilter.markBearerAuthenticationFailure` (`:76-91`) chỉ hoạt động ở đường tương thích (không có resolver) — còn `SecurityConfig.java:83-92` kỳ vọng mã này. Cần xác minh nơi khác có đặt `AUTH_FAILURE_CODE_ATTRIBUTE` hay không (`ErgAuthenticationFilter` chỉ đặt `AUTHENTICATION_REQUIRED` ở `:110`).

**R-15 (Vừa, rất có thể sai test). Baseline kiến trúc đã quá hạn.**
`src/test/resources/architecture/internal-access-baseline.tsv` chỉ có một dòng, hết hạn `2026-09-30`; `ArchitectureRatchetTest.java:65-69` yêu cầu `expiresOn >= LocalDate.now()`. Với ngày hệ thống 2026-11-03 test `externalImportsOfModuleInternalsCannotGrowBeyondReviewedBaseline` sẽ fail cho tới khi gỡ dòng (BE-035) hoặc gia hạn. Tương tự: sunset mặc định `LegacyNamespacePolicy` và route `crawler` ở `LegacyApiRegistry` là 2026-10-31 (đã qua) nhưng route vẫn phục vụ và vẫn phát header `Sunset` quá khứ; `LegacyApiRegistry(LocalDate today)` nhận tham số nhưng không dùng (`LegacyApiRegistry.java:19-27`).

**R-16 (Vừa, nghi ngờ). Receipt sync `PENDING` không hết hạn.**
`LmsSyncOperationService.reserve` và `complete` là hai transaction riêng (`:47,98`); nếu tiến trình chết giữa hai bước, bản ghi `PENDING` tồn tại vĩnh viễn và mọi retry trả `409 SYNC_PENDING` (retryable) (`:67-75`) vì chỉ `abandon` thủ công xoá. Không thấy job dọn dẹp trong slice.

**R-17 (Thấp). `applicationTaskExecutor` bị thay bằng executor module-event.**
`ModuleEventTaskExecutorConfig.java:15-31`: bean `moduleEventIoExecutor` mang thêm tên `applicationTaskExecutor`, `AbortPolicy`, queue mặc định 0, tối đa 16 luồng. Mọi `@Async`/MVC async mặc định của Spring cũng chạy trên pool này và có thể bị `RejectedExecutionException` khi quá tải. Cần xác minh nơi dùng `@Async`.

**R-18 (Thấp). Các điểm nhỏ khác.**
- `application.yml` (document chung) có `spring.data.mongodb.uri: ${SPRING_DATA_MONGODB_URI:${MONGODB__URI}}` không mặc định; chỉ lỗi nếu Mongo được bật.
- `FlywayConfig.java:59-69`: mọi profile không phải `prod` (kể cả `staging`) chạy từ thư mục repo sẽ migrate từ `filesystem:` nguồn thay vì classpath.
- `BaseEntity.hashCode()` trả hash của class (hằng theo class) (`BaseEntity.java:66-68`): đúng hợp đồng nhưng `HashSet/HashMap` của entity cùng loại suy biến O(n).
- `IdGenerator.newUuidV7` không đảm bảo đơn điệu trong cùng mili-giây (`:17-25`).
- `SecurityConfig.java:60-64` hard-code cookie `__Host-erg_session`, khác với khoá cấu hình `erg.auth.cookie.session-name`.
- `GraphQlGuardFilter.maxDepth` đếm cặp `{}` thô (`:234-261`), kể cả literal object trong đối số; chỉ là lớp chặn thô, lớp thật là `MaxQueryDepthInstrumentation`.
- `/metrics` (permitAll) trả số liệu text tối giản (uptime, thread) không xác thực (`PlatformHealthController.java:76-86`).
- `/actuator/health` không nằm trong danh sách `permitAll` (chỉ `/actuator/prometheus` khi bật cờ) => bị `denyAll`; probe nên dùng `/ready` (đúng như `Dockerfile` HEALTHCHECK).

---

## 8. Câu hỏi còn mở

1. Production thực sự chạy với tập profile nào (`prod` hay `prod,staging`)? Nếu chỉ `prod`, các giá trị trong document `staging` (R-01) được cấp bằng cơ chế nào ngoài relaxed binding `ERG_*`?
2. `ErgPolicyService.matches("*")` cho phép mọi permission dù không có role hệ thống (R-05) là chủ ý (dựa vào kiểm soát gán `*` ở DB) hay sai lệch so với `AuthorizationCapabilityPolicy.isPlatformWide`?
3. Ai là chủ của việc gỡ/gia hạn dòng baseline BE-048/BE-035 đã hết hạn 2026-09-30 và sunset 2026-10-31 của `crawler`/namespace cũ (R-15)? Thực tế đã chuyển client chưa?
4. `erg.http.rate-limit.*`, `erg.tenant.*`, `erg.queue.*`, `erg.compose.*` được module nào đọc? Có nên xoá cấu hình mồ côi?
5. Có chủ đích dùng `operationName` trong khoá rate limit GraphQL (để tách hạn mức theo thao tác) hay nên đổi thành hạn mức theo người dùng (R-04)?
6. Với route công khai và job nền (không có `TenantContext`), chiến lược RLS mong muốn là gì (GUC `maintenance_mode`? role riêng)? `SystemActorContext` và `TenantContext.maintenance()` hiện được dùng ở đâu ngoài test?
7. Giới hạn kích thước thân request cho `/webhooks/*` được thực thi ở đâu (Tomcat, nginx, ingress) (R-11)?
8. `AUTH_FAILURE_CODE_ATTRIBUTE` cho `AUTH_SESSION_REPLACED`/`ACCOUNT_DISABLED` có được đặt ở đường production không (R-14)?
9. Có job nào dọn `lms_sync_operations` `PENDING` quá hạn và `webhook_receipts`/`webhook_inbox` đã hết hạn không (R-16)? Ai xử lý hàng `webhook_inbox` trạng thái `pending`? (Không thấy consumer trong slice.)
10. Các `@Async` / `CompletableFuture` nào đang dùng `applicationTaskExecutor` (R-17)?
