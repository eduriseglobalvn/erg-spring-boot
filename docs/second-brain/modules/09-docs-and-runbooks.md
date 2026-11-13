# 09 - Tài liệu dự án và Runbook vận hành (erg-spring)

> Phạm vi: `AGENTS.md`, `HELP.md`, `docs/**` (khoảng 170 file .md/.json), `load/README.md`, `postman/`, `load/`, `ops/` ở mức tóm tắt.
> Quy ước: đường dẫn tính từ gốc repo `erg-spring`; chỉ ghi điều đã đọc; không chép credential.
> Mức đọc: toàn bộ `docs/architecture` (31) và `docs/runbooks` (12) đọc đủ; `docs/performance`, `docs/engineering`, `docs/frontend`, `docs/1606`, các file gốc `docs/*.md` và `docs/ponytail` đọc đủ hoặc gần đủ; các kế hoạch/báo cáo rất dài (`docs/0706`, `0806`, `1006`, `1307`, `1406`, `superpowers/plans`, `superpowers/specs`) đọc heading + phần mở đầu + kết luận/mục còn dở.

---

## 1. Tổng quan tài liệu

### 1.1 Quy tắc làm việc (AGENTS.md, HELP.md)

- `AGENTS.md` (repo): kế hoạch/task tracking ngoài (workbook `D:/ERG/TASK/ERG_Task_Management_.xlsx`) là tùy chọn trừ khi user yêu cầu; giữ nguyên worktree bẩn, không reset/stage/commit/push/merge/deploy nếu chưa được cho phép.
- `erg-spring` là backend chuẩn (canonical) và là bên publish contract: REST `contracts/openapi/erg-openapi.json` + `.sha256`; GraphQL `src/main/resources/graphql/schema.graphqls` + `contracts/graphql/schema.graphqls.sha256`. `erg-system` là frontend vận hành, `erg` là CMS công khai, `elearning` là secondary/legacy (chờ `ERG-066`).
- Giao thức đổi contract: `add -> publish -> sync -> generate -> migrate -> deprecate -> observe -> remove`; field xóa/đổi tên/đổi kiểu/nullability, đổi ngữ nghĩa enum, đổi quyền, di chuyển endpoint đều là breaking.
- Gate bắt buộc: `export-openapi-contract.cjs --check`, `check-graphql-schema.cjs`, `node --test` hai file lib test, focused Maven + `./mvnw.cmd verify`.
- Quy tắc backend: tenant lấy từ security context (không tin header/body); controller mỏng; DTO tách entity; idempotency/optimistic lock cho lệnh retry; cache key namespace theo tenant; bound async/retry; không log token/PII; error envelope ổn định REST+GraphQL.
- `HELP.md`: boilerplate Spring Initializr (liên kết tài liệu Spring Boot 4.0.6, Maven, GraalVM native, JPA, Web). Không có nội dung nghiệp vụ.

### 1.2 Bản đồ thư mục `docs/`

| Thư mục / file | Số file | Mục đích | Trạng thái |
|---|---:|---|---|
| `docs/architecture/` | 31 | Chính sách và tài liệu hiện hành của kiến trúc Spring Modulith: catalog module, ma trận phụ thuộc, event policy/catalog, boundary từng module, ADR SaaS, audit DoD, inventory API | Hiện hành (nguồn đáng tin nhất) |
| `docs/runbooks/` | 12 | Runbook vận hành: Flyway, Kubernetes autoscaling, Modulith event recovery/rollback, outbox cutover/recovery, notification dead-letter, observability SLO, runtime role/RLS, signed URL, webhook replay, native release | Hiện hành |
| `docs/engineering/` | 2 | `ci-gates.md` (bảng gate CI), `test-strategy.md` (Surefire vs Failsafe, Scenario) | Hiện hành |
| `docs/performance/` | 4 | Workload model, quyết định JVM/Native, benchmark overhead Modulith event, audit query plan | Hiện hành, nhiều gate còn mở |
| `docs/frontend/` | 3 | Hướng dẫn migration FE: breaking changes API, auth risk/MFA, curriculum tree | Hiện hành (phải cập nhật cùng mỗi thay đổi contract) |
| `docs/superpowers/specs/` | 8 | Thiết kế đã duyệt (API performance, Redis 8.8, Docker native, auth abuse/DDoS, GraphQL N+1, benchmark BE-015, auth clean-break, security remediation) | Hiện hành (thiết kế gốc) |
| `docs/superpowers/plans/` | 57 | Kế hoạch triển khai từng task `BE-xxx` (2026-07-15 .. 2026-08-09) và kế hoạch lớn (modulith, perf, Redis, auth). Checkbox hầu như không được tick; trạng thái thật nằm ở workbook task | Lịch sử/kế hoạch, dùng làm bối cảnh quyết định |
| `docs/ponytail/` | 3 | 2 báo cáo audit "senior review" (2026-07-24/25) + `redis-enable-runbook.md` | Hiện hành một phần |
| `docs/2026-07-*.md`, `docs/api-contract-workflow.md`, `docs/REMAINING-GAPS-REPORT.md` | 8 | Báo cáo performance tháng 7, seed local, workflow contract, báo cáo gap cũ | Hỗn hợp (xem mục 5) |
| `docs/0706/` | 3 | Kế hoạch/báo cáo/hướng dẫn tích hợp FE-BE cho Quiz (2026-07-06..09) | Nền tảng thiết kế quiz, phần "Remaining" đã cũ |
| `docs/0806/` | 5 + skill | Kế hoạch/báo cáo enterprise GraphQL LMS/LCMS (2026-06-09..14) + bản copy skill `erg-spring-enterprise-skill/` | Lịch sử, nhiều chồng lấn |
| `docs/1006/` | 12 | Audit bảo mật (06-10), FK integrity, Firebase push (BE/FE), runbook reset/seed, Postman | Lịch sử (era V1..V16) |
| `docs/1107/` | 1 | Kế hoạch Authentication/Authorization enterprise (2026-07-11) | Lịch sử, một phần đã được thay thế bởi clean-break 08-03 |
| `docs/1307/` | 1 | Kế hoạch API Quản lý trường & Giám sát đào tạo (2026-07-13, 1218 dòng) | Kế hoạch; hiện thực nằm ở module `school` |
| `docs/1406/` | 5 | Kế hoạch/báo cáo/hướng dẫn FE cho Lịch giảng dạy (Teaching Calendar, 2026-06-14/15) | Lịch sử; hiện thực nằm ở module `calendar` |
| `docs/1606/` | 10 | Audit tổng 2026-06-16 (GraphQL latency, N+1, security, JPA, cache, observability) + tiến độ | Lịch sử, phần lớn đã bị vượt qua |

Ghi chú quy ước đặt tên: tên thư mục `ddmm` (0806, 1006, 1406, 1606, 1107, 1307) không nhất quán với `0706` (chứa tài liệu ngày 2026-07-06..09); từ tháng 7 trở đi dùng tên ISO (`2026-07-16-...`).

### 1.3 Thư mục ngoài `docs/` (tóm tắt)

- `load/README.md`: quy trình validation tải. Scenario steady/submission chạy trên fixture xác định `load/fixtures/manifest.yaml`; cần `BASE_URL` và `ACCESS_TOKEN` test. Kịch bản 5.000 VU là workload nghiệm thu, không phải cam kết host 16 GB đạt. Nền tảng benchmark JVM/Native cô lập (`erg-benchmark` Compose: PostgreSQL, Redis state/cache riêng, Prometheus, Grafana, cAdvisor v0.57.0, exporter, k6) chạy bằng `load/scripts/benchmark.ps1` (`-DryRun`, `-Runtime All`, `-FullLoad`, `-KeepInfrastructure`). Ladder fail-fast theo từng runtime. Script k6: `erg-5k-steady.js`, `erg-5k-submission-burst.js`, `erg-graphql-critical.js`, `webhook-burst.js`, `outbox-dispatch.js`, `modulith-events.js`, `redis-8-8-cache.js`. Kết quả nằm ở `load/results/<run-id>/` (32 thư mục, 2026-07-28..29, gồm `comparison.md/json`, kết quả kind/Kubernetes).
- `postman/`: `erg-spring-full.postman_collection.json` (~640 KB, sinh từ OpenAPI bằng `scripts/export-postman.cjs`), `erg-spring-auth.postman_collection.json`, `erg-spring-local.postman_environment.json`. Chỉ ghi output dưới `postman/` theo `docs/api-contract-workflow.md`.
- `docs/1006/erg-full-api-postman-collection.json` (~645 KB) và `docs/1006/erg-spring-local.postman_environment.json` là bản cũ trùng chức năng với `postman/` (hash env khác nhau).
- `ops/`: `docker/` (compose dev/native/monitor/benchmark, nginx, postgres init, README bốn chế độ chạy), `kubernetes/kind`, `native/README.md`, `observability/grafana` (dashboard `erg-backend-slo.json`), `postgres/` (script chẩn đoán `explain-capture.sql`, `top-queries.sql`, `fk-index-audit.sql`...), `backup/` (`backup.sh`, `restore-drill.md`). `helm/erg-spring` là chart Kubernetes. `scripts/` chứa tooling contract (OpenAPI/GraphQL export-check, oasdiff wrapper), docker, reset/seed Postgres, migrate Redis 8.8 local.
- `.github/workflows/ci.yml` (được các plan BE-011/BE-018 nhắc): job `verify`, `portal-suites` (matrix lms/lcms/crm/elearning), `openapi-contract`, `dependency-and-secret-scan` (gitleaks + dependency-check), `native` (nightly/tag), `k6-smoke`, `release-load-gates` (dispatch/tag).
- `backups/` (không theo dõi bởi git): bản dump Aiven và bản mirror local (xem 3.4).

---

## 2. Quyết định kiến trúc (ADR-like)

### 2.1 Nền tảng và cấu trúc module

| # | Quyết định | Nguồn |
|---|---|---|
| A1 | Kiến trúc modular monolith với Spring Modulith; 18 module tường minh (calendar, crm, elearning, notifications, organization, school, reporting, authorization, users, auth, lms, lcms, learningcontent, curriculum, audit, learningoutcomes, learningstandards, enterpriseconnectors). Package ngang (legacy) là "strangler seam" bị khóa bằng `ArchitectureRatchetTest`, chỉ được giảm. | `docs/architecture/module-catalog.md`, `module-dependency-matrix.md`, `spring-modulith-boundaries.md` |
| A2 | Controller là adapter, chỉ gọi facade `*Operations`; không lộ entity/repository; module khác không import `internal`. `ApplicationModules.verify()` chạy trong CI. | `docs/architecture/spring-modulith-boundaries.md` |
| A3 | Facade đồng bộ cho invariant/strong consistency; event bền (post-commit) cho notification/projection/integration. Chỉ `@ApplicationModuleListener`; giao hàng at-least-once, listener idempotent; payload chỉ gồm ID/version/tenant/actor/correlation. Provider ngoài không bao giờ gọi trong transaction lệnh. | `docs/architecture/event-policy.md`, `event-catalog.md` |
| A4 | Chưa đẩy event ra Kafka/AMQP vì không có consumer ngoài tiến trình; giữ JDBC Event Publication Registry. Chỉ mở lại khi có consumer ngoài được duyệt (chọn 1 broker, version schema, DLQ, test broker down). | `docs/architecture/external-event-decision.md` |
| A5 | Outbox cũ (V66) chỉ còn cho 2 loại mail legacy `ACCOUNT_TEACHER_INVITE`, `ACCOUNT_RECOVERY_CODE`; mọi event nghiệp vụ mới chỉ đi qua Modulith registry, không bao giờ publish đôi. | `docs/runbooks/outbox-cutover.md`, `event-policy.md` |
| A6 | Tenant context bất đồng bộ: `TenantContext.Scope` (AutoCloseable) + `Carrier` chỉ mang tenant/user/portal, không mang SecurityContext/token; event bền phải mang `ModuleEventMetadata` để replay dựng lại tenant/system actor. Cấm `new Thread`, ad-hoc executor, common pool ngoài boundary platform. | `docs/architecture/async-tenant-context.md` |
| A7 | Cấu hình dùng `@ConfigurationProperties` nhỏ, có ràng buộc (`erg.module-events`, `erg.performance.read-executor`, `erg.http.cors`, `erg.http.trusted-proxy-cidrs`, `erg.auth.bff-*`); đã xóa flag `erg.modules.*.new-handler-enabled` (2026-08-09) vì không có consumer. | `docs/architecture/typed-configuration.md` |
| A8 | Capability prototype không được giả làm tính năng production: hoặc `@Profile("prototype")`, hoặc trả envelope `NOT_IMPLEMENTED` có mã (ví dụ `CALENDAR_EXCEL_IMPORT_NOT_IMPLEMENTED`). `ModuleWorkflowService`, `CmsService`, `CatalogService`, `DocumentService` không nằm trong graph production. | `docs/architecture/capability-stub-policy.md`, `elearning-module-boundary.md` |
| A9 | Đăng ký JPA có inventory (`JpaRegistrationCatalog`): enabled/partial/dormant (community, configuration, courses, recruitment, security, content-CMS là dormant); thêm entity/repository phải cập nhật catalog (ratchet). | `docs/architecture/jpa-registration-catalog.md` |
| A10 | Cache descriptor thuộc bounded context (26 tên cache, TTL, key, L2 allowlist giữ nguyên); thiếu/trùng descriptor fail khi khởi động; Native hint chia theo contributor. | `docs/architecture/module-cache-descriptors.md` |
| A11 | Mọi cột `jsonb` được phân loại (`DURABLE_VERSIONED`, `DURABLE_OWNER_READER`, `TRANSIENT_EVENT`, `EXEMPT_AUDIT_OR_PII`); đọc qua `JsonbSchemaMigrator.readSafely`, version tương lai/hỏng trả "unavailable" chứ không hạ cấp; không có base class JSON chung; backfill là migration forward-only riêng. | `docs/architecture/durable-jsonb-inventory.md` |
| A12 | `QuestionKindRegistry` là nguồn sự thật bất biến cho 14 kind câu hỏi (compiler, publish-validator, grading dùng chung); kind lạ -> lỗi unsupported (không chấm 0); likert = survey, essay = manual. | `docs/architecture/question-kind-registry.md` |
| A13 | `LmsStatePolicy` là từ vựng duy nhất cho state assignment/attempt; filter lạ -> `BadRequestException` (fail-closed); chuỗi DB giữ nguyên; query port chỉ trả projection. | `docs/architecture/lms-state-policy.md` |
| A14 | Module `users` sở hữu account; module khác chỉ dùng port/projection (`UserDirectoryPort`, `UserAuthorizationPort`); lookup theo ID phải có tenant. | `docs/architecture/user-account-boundary.md` |
| A15 | Notifications sở hữu consumer + registry adapter giao hàng khóa `(channel, targetKind, provider)`; Firebase là adapter đầu tiên; `notification_outbox.provider` ghim routing khi retry (V86). | `docs/architecture/notifications-provider-registry.md` |
| A16 | Learning Standards (SCORM/xAPI/LTI): vòng đời gói `UPLOADING -> VALIDATING -> READY | QUARANTINED`; LTI chỉ nhận token RS256 ký sẵn, không gọi JWKS trên request path; xAPI chỉ lưu digest + actor hash; V96 RLS forced. | `docs/architecture/learning-standards-boundary.md` |
| A17 | Enterprise Connectors (SIS/HRIS/SCIM): lưu `secret://` reference, mọi mutation cần idempotency key + optimistic version + permission; adapter `REFERENCE` xác định, không cần mạng; V97 RLS forced; deprovision cần danh sách ID tường minh. | `docs/architecture/enterprise-connectors-boundary.md` |
| A18 | CRM, Learning Content, Organization/School: facade `*Operations`, adapter legacy "deprecated" giữ nguyên invariants (idempotency, version, V82 JSONB); alias `/api/content/**` chỉ là tương thích, không là bản cài đặt thứ hai. | `docs/architecture/crm-module-boundary.md`, `learningcontent-module-boundary.md`, `organization-school-module-boundary.md` |
| A19 | Hợp đồng gói thi di động (Tauri): manifest có digest SHA-256 + chữ ký Ed25519 + HTTPS artifact; `portableReady` fail-closed; strip `answerKey`; tối đa 10.000 node/1 GiB asset; private key chỉ ở môi trường release; ETag/304. | `docs/architecture/portable-package-update-contract.md` |
| A20 | ADR-064: giữ single-tenant; chỉ có harness quyết định "go/no-go" cho SaaS (entitlement, billing, custom domain). Không thêm migration/controller. Kết quả no-go là hoàn thành hợp lệ. | `docs/architecture/saas-productization-gate.md` |

### 2.2 Bảo mật và xác thực

| # | Quyết định | Nguồn |
|---|---|---|
| S1 | Quyền workspace (`ADMIN`, `TEACHER`, `STUDENT`, `CRM`) suy ra từ role/permission đã xác minh; `portals`, `X-Portals`, domain/path không cấp quyền; explicit deny thắng grant; wildcard chỉ có hiệu lực khi có role super-admin xác minh; scope thiếu/lạ -> deny. | `docs/architecture/multi-role-scoped-authorization.md`, `docs/superpowers/plans/2026-08-06-be-065-*.md` |
| S2 | Auth clean-break (BE-034): một actor đã xác thực là nguồn duy nhất cho tenant/account/session/scope; LMS authorization đặt ở application boundary để REST và GraphQL không lệch; thiếu mapping danh tính -> deny; không giữ adapter cho header tenant/portal cũ. | `docs/superpowers/specs/2026-08-03-erg-auth-workspace-lms-authorization-clean-break-design.md` |
| S3 | Đăng nhập dùng `identifier` (username nội bộ hoặc email); bỏ register công khai, forgot/reset, Google/Apple/OAuth; provisioning admin cấp credential tạm một lần (trạng thái `INVITED`); session tuyệt đối 8h (vai trò đặc quyền) / 12h (thường); giới hạn 2 web + 1 mobile/PWA, vượt quota -> revoke session cũ + `DEVICE_LIMIT_EXCEEDED` (V43, V44, V47, V48). | `docs/1107/auth-authorization-enterprise-plan.md`, `docs/frontend/api-performance-security-migration.md` |
| S4 | BFF: cookie `__Host-erg_session` HttpOnly/Secure/SameSite + CSRF; OIDC bridge chỉ nhận user nội bộ ACTIVE và lấy role/permission từ DB, không tin claim IdP. | `docs/1107/auth-authorization-enterprise-plan.md`, `docs/superpowers/plans/2026-08-08-be-053-bff-cookie-cors-csrf.md` |
| S5 | Chống lạm dụng đăng nhập: `TrustedClientIpResolver` (chỉ tin XFF khi peer thuộc CIDR proxy tin cậy), counter Redis đa chiều (IP/subnet/account/tenant/device), 5 lần sai không còn khóa vĩnh viễn user (throttle/challenge), rủi ro cao -> step-up MFA, replay refresh token -> thu hồi cả family. | `docs/superpowers/specs/2026-07-17-auth-abuse-ddos-risk-based-security-design.md`, `docs/frontend/2026-07-17-auth-risk-mfa-session-guide.md` |
| S6 | Auth hot path cố ý KHÔNG cache quyền xuyên request (quyền phải tươi); chấp nhận một EntityGraph lớn (5 path) được khóa bởi `AuthQueryOptimizationContractTest`. Muốn tối ưu phải đổi contract test trước. | `docs/ponytail/2026-07-24-audit-senior-review.md` (A2 cập nhật) |
| S7 | Ứng dụng chạy bằng role Postgres non-owner, NOSUPERUSER, không BYPASSRLS; Flyway dùng owner riêng; V74 (grant), V75 (RLS ma trận cho mọi bảng có `tenant_id`, test `TenantRlsMatrixIntegrationTest`). Bảng legacy chưa `FORCE` RLS. | `docs/runbooks/postgres-runtime-role.md` |
| S8 | Prod: GraphQL introspection tắt cứng, cookie secure/strict, stacktrace never; CORS fail-closed khi `*` + credentials; xem bảng closure BE-014. | `docs/1606/03-security-vulnerabilities.md` (cuối file) |
| S9 | Signed upload URL quiz-media: HMAC-SHA256 trói tenant + storage key + MIME + size + expiry; key ký riêng (`SECRET_MEDIA_URL_SIGNING_KEY` >= 32 ký tự, khác JWT secret); adapter R2/S3 phải HEAD kiểm size/checksum khi commit. | `docs/runbooks/storage-signed-url.md`, `docs/superpowers/plans/2026-07-24-be-017-*.md` |
| S10 | Webhook: chữ ký provider + cửa sổ timestamp + durable inbox; lặp lại trả receipt đã ghi, không chạy lại hiệu ứng nghiệp vụ. | `docs/runbooks/webhook-replay.md` |

### 2.3 Dữ liệu, hiệu năng, hạ tầng

| # | Quyết định | Nguồn |
|---|---|---|
| D1 | Hybrid: JPA cho ghi/CRUD; projection + `JdbcClient` cho read model có biên; jOOQ chỉ pilot khi có benchmark; GraphQL selection-aware, `@BatchMapping` chỉ cho quan hệ thật; fan-out nặng đi qua outbox + worker có giới hạn; native image là tối ưu bộ nhớ/khởi động, không phải giải pháp latency DB. | `docs/superpowers/specs/2026-07-15-api-performance-scalability-design.md` |
| D2 | Postgres là nguồn sự thật; Redis 8.8 tách `redis-state` (AOF, noeviction: session/rate limit/điều phối) và `redis-cache` (allkeys-lfu, TTL bắt buộc, có thể mất). L1 Caffeine + L2 Redis, single-flight, invalidation bằng generation + Pub/Sub; rate limiter dùng `INCREX` với fallback Lua cho Redis 7.4. Rollback: `ERG_REDIS_CACHE_ENABLED=false`. | `docs/superpowers/specs/2026-07-16-redis-8-8-upgrade-design.md`, `docs/2026-07-16-redis-8-8-implementation-report.md` |
| D3 | Redis tắt mặc định; bật bằng profile `redis` + biến môi trường; URL thiếu thì fail ngay lúc khởi động (`@AssertTrue`). | `docs/ponytail/redis-enable-runbook.md` |
| D4 | Mọi migration Flyway append-only; không có down migration; rollback ứng dụng bằng facade tương thích + forward fix; chỉ restore backup cho DR. | `docs/runbooks/flyway-upgrade-rollback.md`, `docs/superpowers/plans/2026-07-24-be-012-*.md` |
| D5 | Idempotency: nhiều cơ chế per-command (V30, V66, V67, V76, V17); retry cùng key bị chặn bởi unique index phải được phân biệt với xung đột dữ liệu thật (BE-009); `STALE_WRITE` 409 cho optimistic lock. | `docs/superpowers/plans/2026-07-24-be-009-*.md` |
| D6 | JVM là mặc định cho đến khi có benchmark JVM vs Native cùng workload (cold start, RSS, RPS, p95/p99, CPU, GC, pool wait, error); Native smoke chỉ chứng minh đóng gói/Flyway/readiness. | `docs/performance/jvm-native-decision.md`, `docs/runbooks/native-release.md` |
| D7 | Deploy production: build image native Linux amd64 trên máy dev/CI, server chỉ pull tag bất biến (git SHA) và `compose up`; Nginx cân bằng nhiều replica backend qua DNS Docker; rollback = đổi lại tag. Bốn chế độ: dev (IDE + Compose dịch vụ phụ), test (native trong container), build, release. | `docs/superpowers/specs/2026-07-16-docker-native-deployment-design.md`, `ops/docker/README.md` |
| D8 | Ngân sách tài nguyên host 16 GB: >= 3 GiB OS, Postgres <= 6 GiB, app <= 2 GiB, worker <= 1 GiB, Redis 512 MiB noeviction, Nginx 256 MiB, monitoring <= 1,5 GiB; tổng Hikari + dự phòng không vượt `max_connections` Postgres. | `docs/superpowers/specs/2026-07-15-api-performance-scalability-design.md` mục 13 |
| D9 | Kubernetes: `helm upgrade --install`, digest ảnh cố định, tối thiểu 2 replica, PDB maxUnavailable 1, HPA (CPU 60%/mem 75%) hoặc KEDA (theo `hikaricp_connections_pending`), không bao giờ bật cả hai; template fail nếu `replicas x perPodMaxConnections > postgresMaxConnections - reserved` (mặc định 15 x 8 = 120 / 160). | `docs/runbooks/kubernetes-autoscaling-self-healing.md` |
| D10 | Cổng nghiệm thu hiệu năng: p95 <= 500 ms, p99 <= 1 s (read cacheable p95 < 300 ms, p99 < 800 ms), lỗi + 429/503 < 0,1%, không Hikari timeout, connection-acquire p95 <= 50 ms, CPU TB < 70%, RPO <= 15 phút, RTO <= 4 giờ, availability 99,5%/tháng (1 host, không phải HA). | `docs/superpowers/specs/2026-07-15-api-performance-scalability-design.md` mục 11, `docs/performance/2026-07-workload-model.md` |
| D11 | Loại bỏ nhiều caching/phân quyền "cũ": `@CacheEvict(allEntries=true)` còn rộng (74 chỗ trong 13 service, ghi nhận 07-16) là nợ đã biết; ưu tiên invalidation chính xác theo tenant/generation. | `docs/2026-07-16-api-performance-release-readiness-reaudit.md` |

### 2.4 Domain / nghiệp vụ

| # | Quyết định | Nguồn |
|---|---|---|
| N1 | Quiz: tách DRAFT / PUBLISHED bất biến; authoring đọc bằng GraphQL, ghi bằng REST; hỗ trợ cả grading `client-first` và `server-authoritative`; media chuyển từ base64 sang object storage; `version_label` tự động `vN`; GC media mồ côi mặc định 7 ngày. | `docs/0706/quiz-backend-plan.md` mục 0, 10 |
| N2 | Runtime quiz: BE trả package canonical bất biến (cache mạnh), không shuffle theo học sinh; FE shuffle xác định theo seed của attempt ở testing mode; training dùng answer key nhúng, testing thì không. | `docs/0706/quiz-fe-be-integration-report-2026-07-08.md` |
| N3 | Lịch giảng dạy: GraphQL-first, mutation cấp trên cùng (`createTeachingSchedule`, `previewTeachingSchedule`, `updateTeachingScheduleEvent`, `deleteTeachingScheduleEvent`, `deleteTeacherSchedule`, `publishTeachingScheduleBatch`, `cancelTeachingScheduleBatch`, `createRoom`, `importTeachingScheduleFromExcel`); xóa mềm sang `CANCELLED`; snapshot tên giáo viên; advisory lock + idempotency khi tạo; `updateTeachingScheduleEvent` chỉ sửa `roomId/roomName/note`. | `docs/1406/teaching-calendar-implementation-report.md`, `teaching-calendar-fe-backend-followup.md` |
| N4 | Giám sát đào tạo (kế hoạch): REST cho command/import/export, GraphQL cho view/dashboard; mặc định chốt: mỗi trường thuộc một tenant; một học sinh một lớp active mỗi năm học; điểm thực hành thang 0-10 + `max_score`; sổ đầu bài khóa sau khi PĐT duyệt hoặc 7 ngày; câu hỏi tạm archive khi campaign locked, purge payload sau 7 ngày; Google Sheets dùng service account; dashboard SLA 60 giây. | `docs/1307/school-training-operations-api-plan.md` mục 20 |
| N5 | Notification: Postgres giữ inbox/read state/device/outbox/audit/idempotency, FCM chỉ là kênh giao; payload chỉ chứa ID/deeplink; topic có namespace `t_{tenant}_...`. | `docs/1006/firebase-push-notification-be-plan.md`, `firebase-push-notification-report.md` |
| N6 | Ràng buộc dữ liệu: thêm 80 FK ở V11..V16 theo kiểu `NOT VALID` rồi `VALIDATE`; chưa có composite FK theo tenant, chưa normalize các mảng ID trong JSONB. | `docs/1006/database-relationship-integrity-completion-report.md` |

---

## 3. Runbook vận hành

### 3.1 Deploy và release

**Docker (một host)** - `docs/superpowers/specs/2026-07-16-docker-native-deployment-design.md`, `ops/docker/README.md`, `scripts/docker.ps1|sh`
1. Dev: `.\scripts\docker.ps1 dev` khởi động Postgres, Redis, Nginx; Spring Boot chạy từ IDE (cổng 1606).
2. Test: `.\scripts\docker.ps1 test 2 -Image erg-spring:local-native` chạy native trong container với 2 replica sau Nginx.
3. Build/release: `docker.ps1 build -Image <registry/image:tag>`, `release` (tag = git SHA, không dùng `latest`).
4. Server: đặt `ERG_APP_IMAGE` bằng tag bất biến, `docker compose pull`, tạo lại dịch vụ. PG/Redis phải healthy trước khi backend lên.
5. Rollback: chọn lại tag trước và tạo lại stack. Image/push hỏng không ảnh hưởng bản đang chạy.

**Native release** - `docs/runbooks/native-release.md`
1. Build trên Linux/CI với profile production và secret ngoài.
2. Kiểm tra Flyway đạt đúng version, `/ready` HTTP 200, Postgres healthy, image không chứa artifact test/Insight.
3. Native smoke là gate khởi động/health/tài nguyên, không phải bằng chứng throughput.

**Kubernetes (Helm)** - `docs/runbooks/kubernetes-autoscaling-self-healing.md`
1. Điều kiện: node group đa zone + Cluster Autoscaler, Metrics Server (hoặc KEDA + Prometheus), Secret có sẵn (chart không tạo secret), NetworkPolicy `externalCIDRs` hẹp.
2. `helm lint` -> `helm template` -> `helm upgrade --install ... --wait --timeout 10m` -> `kubectl rollout status`.
3. Quan sát p50/p95/p99, lỗi HTTP, CPU throttling, RSS, Hikari active/pending (phải về 0 sau scale-out), Postgres connections/locks, Redis hit/miss/eviction.
4. Kiểm thử harness kind: `load/scripts/kubernetes-platform-test.ps1` (render, đa node, xóa pod, rolling, drain node), sau đó ladder 100 -> 500 -> 1.000 -> 5.000 qua ingress.
5. Sự cố: Pods Pending (kiểm tra request/taint/topology/CA), HPA chạm max (dừng tăng tải, xem throttling + Hikari pending), crash loop (xem probe/migration; không bypass Flyway/RLS), mất Prometheus/KEDA (chuyển sang HPA qua Helm release).
6. Rollback: `helm -n erg history erg`, `helm rollback erg <revision> --wait`. Chỉ rollback ứng dụng/chart; schema dùng forward fix.

**Canary Modulith rollback** - `docs/runbooks/modulith-rollback.md`: tắt cờ canary của module, route về compatibility facade, so sánh error rate, p95/p99, lock wait, event lag; không rollback Flyway; consumer chấp nhận cả hai phiên bản event trong một cửa sổ deploy. (Lưu ý: cờ `erg.modules.*.new-handler-enabled` đã bị xóa 2026-08-09 - xem A7; runbook này chưa phản ánh.)

### 3.2 Database: Flyway, backup, restore, role

**Flyway upgrade gate** - `docs/runbooks/flyway-upgrade-rollback.md`
1. Backup Postgres (`pg_dump --format=custom --no-owner`) và verify.
2. Restore vào DB cô lập, chạy migration hiện hành.
3. Chạy `FlywayUpgradePathIntegrationTest` (migrate lên ranh giới release trước rồi nâng cấp tại chỗ).
4. Kiểm tra parity lịch sử Flyway, readiness, error rate, lock wait, event publication lag trước khi tăng canary.
5. Ghi commit SHA, engine/version, version nguồn/đích, checksum backup, thời gian restore, query kiểm tra.
- Rollback: không bao giờ down migration trên prod; tắt handler mới + forward repair; restore chỉ cho DR sau khi dừng writer, ghi recovery point, kiểm tra số dòng/Flyway history/idempotency key.
- Guard `MigrationVersionSequenceContractTest` (plan BE-012) chặn trùng version/khoảng trống/file undo.

**Backup/restore drill** - `ops/backup/restore-drill.md` (chạy hàng quý và sau khi đổi schema/hosting)
1. Base backup mã hóa off-host + giữ WAL. 2. Ghi image, version Postgres, version Flyway, checksum. 3. Restore vào host Postgres 17 trống, không writer. 4. Áp WAL tới recovery target. 5. Chạy ứng dụng với Redis rỗng. 6. Flyway validate, smoke tenant isolation, kiểm tra idempotency notification/outbox, một report query. 7. Ghi RPO, thời gian restore, thời gian ready. Chưa có bằng chứng drill hoàn tất (xem mục 6).

**Role runtime và RLS** - `docs/runbooks/postgres-runtime-role.md`
1. Hai credential: owner (chỉ Flyway: `SPRING_FLYWAY_USER/PASSWORD`) và runtime non-owner (`SPRING_DATASOURCE_USERNAME/PASSWORD`).
2. Volume mới: `ops/docker/postgres/init/02-runtime-role.sh` tạo role; DB đã có: cấp role + grant (V74) TRƯỚC khi đổi datasource; không xóa volume prod để chạy lại init.
3. V75 bật RLS cho mọi bảng có `tenant_id`; `TenantRlsMatrixIntegrationTest` fail nếu thêm bảng tenant mà thiếu RLS.

**Chẩn đoán Postgres** - `ops/postgres/README.md`: cần `pg_stat_statements`; script `top-queries.sql`, `explain-capture.sql`, `fk-index-audit.sql` chỉ chạy trên staging clone; không `EXPLAIN ANALYZE` lệnh ghi trên prod ngoài maintenance window; ghi version, `max_connections`, shared_buffers, work_mem, fingerprint image.

### 3.3 Reset / seed DB

**Reset + seed Postgres (dev)** - `docs/1006/reset-and-seed-postgres-runbook.md`, `scripts/reset-and-seed-postgres.ps1`
1. `.\scripts\reset-and-seed-postgres.ps1 -Confirmation I_UNDERSTAND_THIS_DELETES_DATA` (bắt buộc cụm xác nhận).
2. Đọc `.env` lấy datasource; Flyway `clean` schema `public`; `migrate` từ `src/main/resources/db/migration/postgres`.
3. Chạy `DatabaseReseedRunnerTest` (seed LMS/HocLieu; chỉ chạy khi `-Derg.reseed.devdb=true`), rồi `DatabaseRelationshipMigrationContractTest` + `DatabaseRelationshipConstraintIntegrationTest`.
4. Cờ: `-SkipSeed`, `-SkipVerify`. Không đổi `ddl-auto` sang `create-drop` (Flyway là nguồn sự thật).
- Lưu ý: tài liệu mô tả kết quả "Flyway version 16" - đã cũ (xem mục 5).

**Môi trường Postgres local + mirror** - `docs/2026-07-16-local-postgres-seed.md`
1. Local Docker là target mặc định (`jdbc:postgresql://localhost:5432/erg`, Redis local, Mongo auto-config tắt, Flyway bật).
2. Ngày 2026-07-16 DB local được thay bằng export data-only từ Aiven (schema dựng lại bằng migration nguồn tới V67 để tránh drift checksum V29 của Aiven); 77 bảng khớp số dòng (ví dụ: 13 user, 12 education unit, 179 lớp, 770 học sinh LMS, 35 câu hỏi, 11 quiz, 2 assignment, 0 attempt).
3. Backup nằm ở `backups/` (dump Aiven V64, SQL tương ứng, `local-aiven-mirror-v67-...sql`, bản local trước khi thay). Khôi phục bản V67: pipe SQL vào `docker exec -i erg-dev-postgres-1 psql -v ON_ERROR_STOP=1 -U erg -d erg`.
4. Fixture synthetic tùy chọn: `./scripts/seed-local-postgres.ps1` (5.000 user, 10 trường, 250 lớp, 4.500 học sinh, 10.000 assignment, 180.000 recipient/attempt, 1.000.000 activity); sẽ trộn dữ liệu synthetic vào mirror nên không dùng khi cần mirror sạch. SQL ở `load/fixtures/seed-postgres-scale.sql`.

**Migrate Redis 8.8 local** - `scripts/migrate-redis-8.8-local.ps1|sh`; thiết kế cho phép xóa volume Redis cũ (chỉ Redis, không bao giờ Postgres).

### 3.4 Event, outbox, cutover, recovery

**Modulith event recovery** - `docs/runbooks/modulith-event-recovery.md`, `outbox-recovery.md`
1. Registry JDBC là nguồn sự thật; giao hàng at-least-once; consumer giữ idempotency key (ví dụ `schedule-published:<batchId>`).
2. `ModuleEventRecoveryJob` chạy mỗi 30 giây: resubmit publication fail/incomplete cũ hơn 30 giây, batch 100, tối đa 16 in-flight; không có memory queue, không gọi listener trực tiếp. Tắt khi sự cố: `ERG_MODULE_EVENTS_RECOVERY_ENABLED=false` rồi khởi động lại.
3. Sự cố: kiểm tra lag/failed row/attempt/log -> xác định nguyên nhân (DB, provider, dữ liệu event, listener regression) -> provider lỗi thì tạm dừng recovery và sửa/giới hạn provider, KHÔNG tăng thread pool/batch -> lỗi retryable để recovery tự drain -> event sai vĩnh viễn thì dừng recovery, forward fix hoặc consumer có version -> kiểm tra business key trước khi replay thủ công; tuyệt đối không replay cùng hệ quả qua cả registry lẫn outbox cũ; không xóa row để "xanh" metric.
4. Retention: completion mode `delete` cho bảng nóng; lịch sử nằm ở bảng nghiệp vụ/audit; theo dõi bloat/index.
5. Ghi vào timeline: event id, listener, tenant, correlation id, attempt, kết quả.

**Cutover outbox cũ (V66)** - `docs/runbooks/outbox-cutover.md`
1. Mục tiêu tắt worker cũ ngày 2026-08-31, điều kiện: backlog = 0 liên tục trong một cửa sổ deploy; dashboard parity cho thấy mọi event nghiệp vụ mới thuộc registry; retry/dead, delivery provider, idempotency key khớp; worker không nhận loại nào ngoài 2 loại mail legacy.
2. Canary với `ERG_OUTBOX_WORKER_ENABLED=false`, quan sát một cửa sổ, rồi xóa worker/bảng bằng migration forward. Không xóa row V66 đang pending/processing.
3. Rollback = forward fix cấu hình: bật lại worker chỉ cho 2 loại mail legacy nếu thấy mail chưa gửi; không dùng để replay event Modulith.

**Notification dead-letter replay** - `docs/runbooks/notification-dead-letter-replay.md`
1. Xác nhận backlog dead (`notifications.outbox.dead`, `GET /api/v1/admin/notifications/stats`) và nguyên nhân đã retryable.
2. Không replay lỗi target không hợp lệ (`UNREGISTERED`/`NOT_FOUND`).
3. `POST /api/v1/admin/notifications/outbox/replay-dead?limit=<n>` (cần `notifications.manage`, tối đa 500, theo tenant của caller): đặt `status='retry'`, `attempt_count=0`, `next_attempt_at=now`, xóa lease; chỉ đổi trạng thái, không gửi - worker (hoặc `POST /outbox/run`) gửi ở lượt sau; replay hai lần không nhân đôi (lease `FOR UPDATE SKIP LOCKED`).
4. Kiểm tra stats sau replay; ghi tenant, số lượng, mã lỗi, kết quả; không sửa lịch sử delivery attempt.

**Webhook replay** - `docs/runbooks/webhook-replay.md`: ghi provider, webhook id, receipt status, kết quả chữ ký, tenant, correlation id; chỉ replay từ delivery log đã xác minh của provider qua endpoint ingress bình thường; không chèn event trực tiếp.

### 3.5 Bật Redis, observability, SLO

**Bật Redis phân tán** - `docs/ponytail/redis-enable-runbook.md`
1. `SPRING_PROFILES_ACTIVE=prod,redis`; đặt env trước: `SPRING_DATA_REDIS_URL` (state), `ERG_REDIS_CACHE_URL` (L2), `ERG_REDIS_CACHE_L2_NAMES=...`.
2. Cờ: `erg.redis.state.enabled`, `erg.graphql.rate-limit.redis-enabled`, `erg.cache.l2-enabled` + `erg.redis.cache.enabled` (profile `redis` bật cả bốn).
3. Xác nhận: `MANAGEMENT_HEALTH_REDIS_ENABLED=true`, `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`, `/actuator/health` có `redisStateHealth`/`redisCacheHealth`; `/actuator/prometheus` có metrics tag `state`/`cache`; thử 429 từ 2 instance phải cộng dồn.
4. Lưu ý: rate limit trở thành toàn fleet; `erg.graphql.rate-limit.fail-closed-on-redis-error=true` (mặc định) -> lỗi Redis trả 429; invalidation cache lan tỏa qua Pub/Sub.

**Observability/SLO** - `docs/runbooks/observability-slo.md`: dashboard chuẩn `ops/observability/grafana/erg-backend-slo.json`; metric thấp cardinality (không gắn tenant/user/request ID/raw query); so sánh p95 HTTP/GraphQL với Hikari pending, lock/IO Postgres, cache hit ratio, Modulith publication lag; không tăng thread/connection vô hạn; sampling trace production 10%, có thể nâng tạm bằng `MANAGEMENT_TRACING_SAMPLING_PROBABILITY`. Stack giám sát Docker: `ops/docker/compose.monitor.yml`.

### 3.6 Contract và CI

**Quy trình contract REST/GraphQL** - `docs/api-contract-workflow.md`, `docs/engineering/ci-gates.md`
1. Chạy BE với Postgres đã migrate; `node scripts/export-openapi-contract.cjs --url http://localhost:1606/v3/api-docs` rồi `--check`.
2. PR: snapshot + sha256 phải commit cùng thay đổi; `oasdiff` (`tufin/oasdiff:v1.15.1`) so với base, cảnh báo breaking cũng làm CI fail; GraphQL có checksum schema + AST compatibility.
3. Lỗi REST/GraphQL dùng mã ổn định: `VALIDATION_ERROR`, `ACCESS_DENIED`, `AUTHENTICATION_REQUIRED`, `INTERNAL_SERVER_ERROR` kèm request ID.
4. Test: Surefire (nhanh, bao gồm architecture/Modulith) vs Failsafe (đuôi `IT`/`ITCase`/`IntegrationTest`, cần Testcontainers); `@ApplicationModuleTest` + `Scenario` cho event; native chạy nightly/release; k6 chỉ trên môi trường benchmark.

**Benchmark JVM/Native** - `load/README.md`, `docs/superpowers/specs/2026-07-28-be-015-full-benchmark-platform-design.md`: validate bằng `-DryRun`, build image jvm/native, chạy smoke hoặc `-FullLoad`; cacheable-read gate p50 < 150 ms, p95 < 300 ms, p99 < 800 ms, lỗi < 0,1%; run chưa đủ thang 5.000 VU được báo là incomplete, không phải thành công; hạ tầng bị dọn trong `finally`.

---

## 4. Báo cáo / audit / performance

### 4.1 Số liệu build và test

| Mốc | Kết quả | Nguồn |
|---|---|---|
| 2026-07-20 baseline | `mvnw test`: 833 test, 1 fail (`LearningResourceLegacyApiRemovalTest`: route `/api/curriculum/tree`), 3 skip, 268,6 s, Flyway 71 migration, PG 17.10 | `docs/architecture/build-baseline.md` |
| 2026-07-16 JVM | 753 test, 0 fail/error/skip, Flyway 68 migration | `docs/2026-07-16-api-performance-implementation-status.md` |
| 2026-07-17 native | 767 test xanh; ảnh native digest `sha256:a02df2c4...`, 127.051.790 byte; REST assignments 40/40 | cùng file |
| 2026-07-24 | 76 migration V1..V76 (BE-012); sau đó V85 (attendance/gradebook), V86 (provider registry), V96, V97 được nhắc | `docs/superpowers/plans/2026-07-24-be-012-*.md`, các file architecture |
| Inventory API | 555 REST route, 44 field GraphQL; catalog `src/test/resources/performance/api-performance-catalog.yaml` đối chiếu runtime `RequestMappingHandlerMapping` | `docs/architecture/api-inventory.md` |

### 4.2 Kết quả benchmark và preflight

- Modulith event preflight 2026-07-21 (50 req/s, 2 phút, 0 lỗi): JVM 6.000 req p95 319,88 ms; Native 6.001 req p95 43,68 ms; startup JVM 64,5 s vs native 9,6 s; RSS native idle 219,2 MiB. Chưa tính overhead registry (thiếu baseline không-registry), gate p95 tăng <= 10% vẫn mở. (`docs/performance/modulith-event-overhead.md`, `docs/architecture/2026-07-21-modernization-dod-audit.md`)
- Benchmark Compose 2026-07-29 (`load/results/20260729T182820Z/comparison.md`, `...T190558Z/comparison.md`): ở 100 VU, REST và GraphQL đều PASS (JVM REST p95 24,25 ms; Native REST p95 67,29 ms, p99 366 ms); ở 500 VU REST FAIL (JVM p95 1.484 ms, 342 RPS; Native p95 2.515 ms, 265 RPS). Không run nào hoàn thành thang 5.000 VU. Runbook Kubernetes kết luận: 1 pod JVM 4-CPU đạt SLO ở 100 VU nhưng bão hòa ở REST 500 VU, Native cùng ranh giới; vì vậy scale ngang là trách nhiệm hạ tầng, không nới ngưỡng. (`docs/runbooks/kubernetes-autoscaling-self-healing.md`)
- Cache smoke native 2 VU/10 s: 0 lỗi nhưng p95 1,04 s (> 500 ms), chỉ để định vị workflow chậm. (`docs/2026-07-16-redis-8-8-implementation-report.md`)
- Query-plan audit 2026-07-22: khóa các index `idx_lms_assignments_read_class_status_due`, `idx_lms_attempts_read_student_updated`, `idx_tse_tenant_school_timerange`/`idx_tse_tenant_timerange`, `idx_lms_quiz_questions_question` (không còn `jsonb_array_elements_text`); chỉ là regression plan, không chứng minh latency ở 5.000 user. (`docs/performance/query-plan-audit-2026-07-22.md`)
- Workload model: 70% read cacheable, 20% command chuẩn, 10% webhook burst (100 VU, HTTP 202), outbox dispatch 16 worker/50 dispatch/s; chạy riêng từng loại mới đủ nghiệm thu. (`docs/performance/2026-07-workload-model.md`)

### 4.3 Audit chính

- **DoD audit modulith (2026-07-21)** - `docs/architecture/2026-07-21-modernization-dod-audit.md`: `mvnw verify` xanh; facade LMS/Calendar/Auth/Curriculum đã tách use case (ví dụ `AuthService` 789 -> 107 dòng, `CalendarReadService` -> 242 dòng, `CurriculumService` 704 -> 161 dòng); BE-004 RLS cho 43 bảng legacy qua V75; refresh-token rotation được chứng minh bằng `SELECT ... FOR UPDATE`; lỗi REST/GraphQL thống nhất envelope. Còn mở: so sánh JVM/Native production-equivalent, benchmark overhead registry, tải 100/500/1.000/5.000, CI native + k6.
- **Re-audit release-readiness (2026-07-16)** - `docs/2026-07-16-api-performance-release-readiness-reaudit.md`: kết luận chưa đủ bằng chứng 5.000 user. 9 chặn: (1) chưa chạy nghiệm thu tải; (2) Native chưa nghiệm thu capacity; (3) thiếu `pg_stat_statements`/EXPLAIN before-after; (4) 74 `@CacheEvict(allEntries=true)`; (5) API catalog chưa là inventory từng route (sau đó được bổ sung ở `api-inventory.md`); (6) breaking API chưa được ghi đủ; (7) store in-memory còn lại (Document/Cms/Catalog/ModuleWorkflow) chỉ bị chặn bằng profile; (8) reporting chưa có bảng aggregate; (9) backup/restore chưa được chứng minh.
- **Audit GraphQL/API (2026-07-18)** - `docs/2026-07-18-graphql-api-performance-audit-report.md`: P0 cần integration test guard ở đúng path `/api/v1/graphql`; P1: auth tạo chi phí DB gần như mỗi request, workspace over-fetch (chỉ `schoolOperations` dùng selection set), option/catalog query không giới hạn, `studentGroups` tải tới 10.000 row, offset `Page` kèm count, deadline GraphQL không hủy JDBC, cache key phân mảnh theo permission, `allEntries=true` gây cold-cache storm.
- **Audit senior (2026-07-24)** - `docs/ponytail/2026-07-24-audit-senior-review.md` (53.303 LOC, 94 `@Entity`, 77 repository, 50 controller, 74 service): điểm tốt - toàn bộ association LAZY, `open-in-view: false`, không SQL injection, token opaque 48 byte SHA-256, refresh rotation + replay detection, GraphQL guard (depth 8, complexity 500). Phát hiện: thiếu `default_batch_fetch_size`; EntityGraph auth đa collection (quyết định có chủ đích, xem S6); `GET /lms/quizzes/{quizId}/students` nạp toàn bộ attempt + 3 blob JSONB; R2 client chưa có timeout; resilience4j là dependency chết; CORS default có origin `http://` + credentials; upload media không auth + buffer toàn body; access token thực tế sống 8-12h; SVG trong allowlist ảnh; RLS chưa đủ + permissive fallback khi thiếu TenantContext; media signing dùng lại JWT secret. Thứ tự đề xuất tuần 1-3 + backlog.
- **Thẩm định 5k (2026-07-25)** - `docs/ponytail/2026-07-25-enterprise-scale-5k.md`: session lưu Postgres (`user_sessions`), Redis mặc định tắt (L2 và state) => cache chỉ Caffeine từng instance; E1 Hikari max 8/timeout 1000 ms là nút thắt số 1 (ước tính ~160 req/s/instance, hỏng từ ~180-200 request đồng thời), đề xuất 25/5000 ms (cần xác nhận capacity Postgres); E2 `/ready` luôn 200 kể cả DB chết; E3 thiếu retention cho outbox/attempt/audit/webhook inbox; E4-E7 truy vấn nặng; E8/E9 cache quyền và rate limit không lan truyền giữa instance khi tắt Redis; E10/E11 thiếu MDC correlation + business metrics. Điểm đã đạt enterprise: RLS, sharded assignment progress 64-way (V69), idempotency V76, cache degrade tốt. Báo cáo nêu "chưa sửa gì" cho phần đề xuất.
- **Closure bảo mật BE-014 (2026-07-24)** - `docs/1606/03-security-vulnerabilities.md`: 7 finding, không còn P0/P1 mở; #1, #3, #4, #5, #6 đóng + khóa bằng test (`ProductionConfigurationSafetyTest`, `CorsConfigSecurityTest`); #2 (rate limiter in-memory) bị phân loại stale vì đã có router Redis fail-closed; #7 chấp nhận rủi ro (guard biến nhạy cảm match quá rộng).
- **Audit bảo mật 2026-06-10** - `docs/1006/bao-cao-bao-mat-spring-boot.md`: 2 Critical, 8 High, 6 Medium, nhiều Low; đã fix 13/15 (xóa trusted-header auth, IDOR profile, `canAccessClassroom` OR->AND, `.anyRequest().denyAll()`...); chưa fix: C-2 (credential production nằm trong `.env`, cần xoay thủ công) và M-2 (route GET public có thể lộ draft).
- **Audit 2026-06-16** - `docs/1606/00..09`: nguyên nhân latency GraphQL 1-2 s là tra principal mỗi request + thiếu DataLoader + query tuần tự; progress file ghi đã làm: TTL `authPrincipal` 5s -> 30s, key cache, EntityGraph, `@Cacheable` read model, Caffeine rate limiter, timer `graphql.operation.duration`, `HibernateQueryCountFilter` (cảnh báo > 10 query). Backlog Phase 4 (Redis rate limit/cache, tracing, bật Flyway, projection thay entity User, DataLoader ngoài Calendar) hầu hết đã được xử lý ở các tài liệu tháng 7 (xem mục 5).

### 4.4 Báo cáo tính năng

- **GraphQL enterprise LMS/LCMS (06-09..14)** - `docs/0806/lms-lcms-graphql-security-report.md`: 6 workspace có read model projection + query-count proof (7 test entityLoadCount == 0), readiness tự chấm 80%; `remaining-report` liệt kê P0 (questionBank PostgreSQL proof, slow-query smoke, field-level matrix), P1 (Redis rate limit/cache, load 100 -> 5.000, attendance summary, announcement persistence), P2 (AOT/native smoke, gom placeholder, EXPLAIN toàn bộ). Quy tắc skill: `docs/0806/erg-spring-enterprise-skill/` (REST luôn `ApiResponse<T>`, GraphQL chỉ read-projection, DTO != projection, không trusted header, scope bắt buộc).
- **Firebase push** - BE đủ API (self/device/admin), sender HTTP v1, outbox worker, mã hóa token; phần chưa làm (06-10): Postgres test cho `SKIP LOCKED`, native smoke CI, topic sync Firebase thật, retention `notification_delivery_attempts`, rate limiter provider, hook event LMS thật. FE (`firebase-push-notification-fe-*`): FID-first, chưa bắt đầu. (`docs/1006/firebase-push-notification-*`)
- **Teaching calendar** - `docs/1406/teaching-calendar-backend-report.md` tự chấm 100% (8 mutation, 2 workspace query, 10 lớp test); migration `V17` rồi `V18` (không đảo thứ tự). Cảnh báo về mâu thuẫn Excel import ở mục 5.
- **Quiz FE/BE** - `docs/0706/quiz-fe-be-integration-report-2026-07-08.md`: runtime package, authoring, report GraphQL, cache `quizAssignmentReport`/`quizQuestionAnalytics` TTL 30 s có evict sau commit; còn lại: chạy E2E Postgres thật (seed demo, package training có answer key/testing không có, start/submit với auth+RLS thật).
- **Ràng buộc FK** - `docs/1006/database-relationship-integrity-completion-report.md`: 80 FK (core/RBAC/org 23, LMS 19, HocLieu 19, CRM/notification 19) validate hết; còn thiếu composite FK theo tenant và normalize JSONB.

---

## 5. Tài liệu có thể lỗi thời hoặc trùng lặp

| File | Lý do |
|---|---|
| `docs/REMAINING-GAPS-REPORT.md` (2026-06-15) | Danh sách 25 gap chốt ở thời điểm 06-15 (ví dụ "chưa từng chạy test-compile", rate limit/cache chỉ in-memory, student calendar chưa có). Đã bị vượt qua bởi Redis 8.8, re-audit 07-16, BE-xxx tháng 8. Chỉ dùng làm lịch sử. |
| `docs/1006/reset-and-seed-postgres-runbook.md` | Mô tả `V1..V16` và "Flyway schema version: 16"; số liệu seed tối thiểu cũ. Migration hiện đã tới ít nhất V97. Quy trình (xác nhận, clean, migrate, seed, verify) có thể vẫn dùng nhưng đầu ra mong đợi không còn đúng. |
| `docs/1006/database-relationship-integrity-audit.md` và `...completion-report.md` | Era V11..V16, chạy trên DB Aiven `defaultdb`; hai tài liệu cùng chủ đề (audit rồi báo cáo hoàn tất). Một phần "chưa normalize JSON" đã được xử lý bằng bảng `lms_quiz_questions` (07-16) - cần xác minh phần còn lại. |
| `docs/1006/bao-cao-bao-mat-spring-boot.md` | Audit 06-10 trước khi nhiều fix; **chứa chuỗi giống credential** (một số đã `REDACTED`, nhưng ít nhất một mật khẩu dạng văn bản rõ xuất hiện trong mục C-2). Cần scrub khỏi repo và coi credential đã lộ là phải xoay. Không sao chép nội dung đó. |
| `docs/1006/erg-full-api-postman-collection.json`, `docs/1006/erg-spring-local.postman_environment.json` | Trùng chức năng với `postman/erg-spring-full.postman_collection.json` và `postman/erg-spring-local.postman_environment.json` (sinh từ OpenAPI); bản trong `docs/1006` là bản cũ. |
| `docs/1606/00..09` (audit + progress) | Nhiều khuyến nghị bị đảo ngược: TTL cache auth 30 s (`09-implementation-progress.md`) mâu thuẫn quyết định 07-24 "không cache auth xuyên request" (S6); song song hóa bằng `CompletableFuture` đã bị gỡ ở re-audit 07-16 vì mất transaction/RLS context; rate limiter Caffeine -> Redis; `03-security-vulnerabilities.md` đã được cập nhật bảng closure nên phần đầu là lịch sử. |
| `docs/0806/*` (4 tài liệu enterprise GraphQL, 1844 + 1207 + 521 + 193 + 148 dòng) | Chồng lấn nặng (plan/report/remaining-plan/remaining-report/report đều lặp checkpoint); mốc 06-09..14; nhắc rate limit in-memory, Redis chưa có, GraphQL chưa production. Dùng `docs/2026-07-18-graphql-api-performance-audit-report.md` và `docs/architecture` làm hiện trạng. |
| `docs/0806/erg-spring-enterprise-skill/` | Bản copy skill (SKILL.md, evals, references) với "status guard after report item 27" cũ; có khả năng trùng skill toàn cục - cần quyết định nguồn chuẩn. |
| `docs/1406/teaching-calendar-backend-plan.md` (2964 dòng), `...implementation-report.md` | Kế hoạch chi tiết lúc codebase chưa có `Mutation`, `Room`, `lms.schedule.*`; hiện thực đã nằm trong module `calendar` (Modulith). `...backend-report.md` đánh dấu "DONE" cho Excel import (stub) và "100%" trong khi `docs/architecture/capability-stub-policy.md` quy định Excel import trả `CALENDAR_EXCEL_IMPORT_NOT_IMPLEMENTED` - số liệu readiness bị thổi phồng. |
| `docs/1107/auth-authorization-enterprise-plan.md` | Trước clean-break 2026-08-03 (BE-034/052/053/054/065); mô tả "Redis Spring Session" trong khi `docs/ponytail/2026-07-25-enterprise-scale-5k.md` nói session nằm Postgres và Redis autoconfig bị exclude (có thể cùng đúng: BFF là cờ riêng) - cần đối chiếu mã. |
| `docs/1307/school-training-operations-api-plan.md` | Kế hoạch 13/07 (21 mục, 13 PR đề xuất); chưa có bản đối chiếu file nào đã hiện thực; đối chiếu với `docs/architecture/organization-school-module-boundary.md` và BE-048/055. |
| `docs/0706/quiz-backend-plan.md`, `quiz-fe-integration-guide.md`, `quiz-fe-be-integration-report-2026-07-08.md` | Ba tài liệu cùng chủ đề quiz, kích thước lớn (587, 778, 1184 dòng); phần "Remaining Issues" và ledger FE đã cũ; guide 07-08 mô tả "điểm lệch spec" nên đọc cùng plan. |
| `docs/2026-07-16-api-performance-implementation-status.md` vs `...-release-readiness-reaudit.md` | Cùng ngày, số migration khác nhau (68/69) và số test khác; tài liệu sau (re-audit) là nhận định, tài liệu trước cập nhật "continuation 07-17". Đọc cả hai. |
| `docs/2026-07-16-local-postgres-seed.md` | Mô tả mirror V67, số dòng ngày 07-16; **có chuỗi kết nối Redis local kèm mật khẩu dev** (không sao chép). Số migration lạc hậu. |
| `docs/architecture/api-inventory.md` | Mâu thuẫn nội bộ: tiêu đề "REST route count: 555" nhưng văn bản vẫn nói "does not duplicate 522 rows". |
| `docs/architecture/spring-modulith-boundaries.md`, `generated-modules.md`, `module-dependency-graph.md` | Liệt kê 17 module, thiếu `audit` (module-catalog có 18); `module-dependency-graph.md` vừa nói "eighteen" vừa vẽ sơ đồ 17. |
| `docs/architecture/2026-07-20-modulith-baseline.md`, `build-baseline.md` | Snapshot một thời điểm (commit `0fe43c2`, 71 migration, 1 test fail dự kiến đã giải quyết bằng `docs/frontend/2026-07-17-curriculum-tree-api.md`/route `/api/v1/curriculum/tree`). Không phải trạng thái hiện hành. |
| `docs/runbooks/modulith-rollback.md` | Nhắc "canary handler flag" nhưng cờ `erg.modules.*.new-handler-enabled` đã bị xóa (`typed-configuration.md`); runbook chưa cập nhật. |
| `docs/runbooks/outbox-recovery.md` | Bước 5 viện dẫn `outbox-cutover.md "(when present)"` - file đã tồn tại. |
| `docs/ponytail/redis-enable-runbook.md` | Nằm trong thư mục audit nhưng là runbook; nên chuyển sang `docs/runbooks/` và đối chiếu với thiết kế Redis 8.8 (tách state/cache). |
| `docs/superpowers/plans/*.md` | Checkbox hầu như chưa tick (kể cả plan modulith 261 mục chưa tick trong khi DoD audit báo hoàn tất phần lớn); chỉ `2026-07-30-be-011-lms-attendance-gradebook-contracts.md` có 8 mục `[x]`. Trạng thái thật ở workbook task, không ở plan. Nhiều plan gắn với worktree/branch cũ (`codex/...`). |
| `docs/engineering/ci-gates.md` / `test-strategy.md` | Còn đúng nhưng chưa nhắc các job `portal-suites`, `release-load-gates` được thêm bởi BE-011/BE-018. |

---

## 6. Câu hỏi và việc còn dở được ghi trong docs

### 6.1 Gate hiệu năng/vận hành chưa đóng
1. Nghiệm thu tải 100/500/1.000/5.000 VU (steady 30 phút, burst nộp bài, soak 2 giờ 50%, cache warm/cold/Redis outage) chưa có kết quả đạt; 500 VU REST đang FAIL ở cả JVM và Native. (`docs/2026-07-16-api-performance-release-readiness-reaudit.md`, `load/results/20260729T182820Z/comparison.md`)
2. Quyết định JVM vs Native: cần benchmark cùng CPU/RAM, RSS, p99, heap, pool wait. (`docs/performance/jvm-native-decision.md`)
3. Overhead Modulith registry: chưa chạy baseline không-registry và không-bền; gate p95 tăng <= 10% mở. (`docs/performance/modulith-event-overhead.md`)
4. CI-hosted native build và k6 release gate chưa thay thế bằng test cục bộ. Windows `native:compile` thiếu Visual Studio 2022 Build Tools. (`docs/architecture/typed-configuration.md`, `2026-07-21-modernization-dod-audit.md`)
5. Backup/restore drill: chưa có RPO/RTO đo được; chưa có bản backup mã hóa off-host + WAL. (`ops/backup/restore-drill.md`, re-audit 07-16)
6. `pg_stat_statements` và EXPLAIN before/after trên workload đại diện chưa thu thập. (`ops/postgres/README.md`, query-plan audit)
7. Hikari max 8/timeout 1 s, `/ready` luôn 200, thiếu retention outbox/attempt/audit, MDC correlation, business metrics: đề xuất E1-E11 chưa được xác nhận áp dụng. (`docs/ponytail/2026-07-25-enterprise-scale-5k.md`)
8. `@CacheEvict(allEntries=true)` rộng (74 chỗ) chưa chuyển sang invalidation theo tenant/generation. (re-audit 07-16)
9. Redis hit/miss SLO và sustained-load evidence cho cache single-flight còn mở. (DoD audit)

### 6.2 Bảo mật
10. Xoay mọi credential từng nằm trong `.env`/tài liệu cũ (C-2 chưa fix thủ công) và scrub `docs/1006/bao-cao-bao-mat-spring-boot.md`. (`docs/1006/bao-cao-bao-mat-spring-boot.md`)
11. M-2: route GET public có thể lộ draft/unpublished - cần audit service layer. (cùng file)
12. BE-014 #2: xác minh bằng chứng tải phân tán (hạn 2026-08-10); #7: siết match biến nhạy cảm theo word boundary (hạn 2026-08-31). (`docs/1606/03-security-vulnerabilities.md`)
13. RLS: bảng legacy chưa `FORCE` và chưa có policy + proof riêng cho từng bảng; permissive fallback khi thiếu TenantContext (C5 audit). (`docs/runbooks/postgres-runtime-role.md`, audit 07-24)
14. Production upload: adapter R2/S3 phải áp dụng cùng contract xác minh, HEAD size/checksum khi commit, từ chối PUT hết hạn/khác tenant ở edge; local adapter không lưu byte. (`docs/runbooks/storage-signed-url.md`)
15. Dual-key rotation cho `SECRET_MEDIA_URL_SIGNING_KEY`; hiện còn fallback sang JWT secret. (cùng file)
16. Default `dev` profile và Flyway tắt mặc định được ghi là "production-safety finding" của Phase 4.3 chưa thay đổi trong baseline. (`docs/architecture/2026-07-20-modulith-baseline.md`)

### 6.3 Tính năng và dữ liệu
17. Outbox V66: tắt worker và xóa bảng bằng migration forward, mục tiêu 2026-08-31, cần backlog = 0 một cửa sổ deploy. (`docs/runbooks/outbox-cutover.md`)
18. Reporting: chưa có bảng aggregate canonical + incremental maintenance + reconciliation. (re-audit 07-16)
19. Firebase: topic sync thật, rate limiter provider (quota FCM), retention delivery attempts (V19 có SQL, thiếu job Java gọi `cleanup_stale_delivery_attempts()`), batch multicast, admin cancel/resend, wiring event LMS thật vào `LmsRuntimeService.submitAttempt()`, native smoke profile. (`docs/REMAINING-GAPS-REPORT.md`, `docs/1006/firebase-push-notification-report.md` - cần xác minh trạng thái hiện tại)
20. LMS GraphQL: `classWorkspace` attendance summary, `studentDashboard` announcement persistence, field-level security matrix đầy đủ, query-count proof PostgreSQL cho `questionBankWorkspace`. (`docs/0806/lms-lcms-graphql-security-remaining-report.md` - một phần có thể đã xong theo BE-011)
21. Calendar Excel import: route công bố nhưng trả `NOT_IMPLEMENTED`; sản phẩm cần quyết định thư viện parse và luồng persistence. (`docs/architecture/capability-stub-policy.md`)
22. Calendar: `updateTeachingScheduleEvent` chỉ sửa room/note; sửa thời gian/lớp/giáo viên cần yêu cầu nghiệp vụ mới (ảnh hưởng conflict detection); quyết định UX preview-trước-create là việc FE/product. (`docs/1406/teaching-calendar-fe-backend-followup.md`)
23. HocLieu legacy `/api/v1/hoclieu/**` đã bị gỡ hoàn toàn; chưa có ánh xạ thay thế - cần quyết định product/API. (`docs/2026-07-16-api-performance-release-readiness-reaudit.md`, `docs/frontend/api-performance-security-migration.md`)
24. Cải tiến contract sắp tới cần FE: báo cáo lớn chuyển sang job + polling; list không giới hạn sẽ yêu cầu page/size hoặc cursor; GraphQL vượt ngân sách complexity trả validation error; response workspace có thể bỏ section không chọn. (`docs/frontend/api-performance-security-migration.md`)
25. Process-local store (`DocumentService`, `CmsService`, `CatalogService`, `ModuleWorkflowService`) cần persistence Postgres/Redis hoặc giữ prototype-only. (`docs/architecture/capability-stub-policy.md`)
26. Các owner JSONB (`lms`, `crm`, `learningcontent`, `reporting`, `users`...) cần reader có version trước khi đổi shape; chỉ trích xuất tiện ích chung khi >= 2 owner có cơ chế giống nhau. (`docs/architecture/durable-jsonb-inventory.md`)
27. Calendar legacy reference-map còn mang JPA user (deprecated), mục tiêu gỡ tiếp theo. (`docs/architecture/user-account-boundary.md`)
28. Data integrity: composite FK theo tenant, bảng nối cho `lms_assignments.student_ids`, `lms_quizzes.question_ids/topic_ids`. (`docs/1006/database-relationship-integrity-completion-report.md`)
29. SaaS: không bật tính năng nào cho tới khi có tenant thứ hai được tài trợ, nhà cung cấp/billing/retention/residency được duyệt; kết quả hiện tại là no-go hợp lệ. (`docs/architecture/saas-productization-gate.md`)
30. Sự kiện bên ngoài (Kafka/AMQP): mở lại chỉ khi có consumer ngoài được duyệt. (`docs/architecture/external-event-decision.md`)
31. Quiz: chạy một lượt E2E Postgres có auth/RLS thật để xác nhận seed demo, package training/testing, start/submit, `assignmentReport` và `quizQuestionAnalytics`. (`docs/0706/quiz-fe-be-integration-report-2026-07-08.md`)

### 6.4 Câu hỏi mở cho đội
- Có dọn `docs/0806`, `docs/1006`, `docs/1606`, `docs/REMAINING-GAPS-REPORT.md` thành thư mục lưu trữ hay gộp vào `docs/second-brain`?
- Số migration hiện hành chính xác là bao nhiêu (tài liệu nhắc tới V97; cần kiểm `src/main/resources/db/migration/postgres`)?
- `docs/ponytail/*` nên giữ tên thư mục này hay chuyển audit vào `docs/audits/` và runbook vào `docs/runbooks/`?
- Quyết định E1 (Hikari 25/5 s) và các mục bật Redis có đã được áp dụng ở môi trường nào chưa? Báo cáo 07-25 nói "chưa sửa gì" cho phần đề xuất.
