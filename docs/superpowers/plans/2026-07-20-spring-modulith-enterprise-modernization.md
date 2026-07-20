# Spring Modulith Enterprise Modernization Implementation Plan

> For agentic workers: REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Chuyển erg-spring thành modular monolith có Spring Modulith làm lớp kiểm soát boundary, loại bỏ prototype giả production, chuẩn hóa API và security, giữ transactional outbox/CQRS-lite, đồng thời nâng khả năng bảo trì, scale ngang, xử lý đồng thời và Native Image.

**Architecture:** Giữ một ứng dụng Spring Boot duy nhất, chia theo business module trực tiếp dưới com.erg.ergspringboot. Mỗi module có public API nhỏ ở package gốc và implementation trong internal/; bên trong dùng vertical slice theo use case thay vì tiếp tục phình các package controller/service/repository/model toàn cục. Sử dụng đầy đủ các capability phù hợp của Spring Modulith: explicit modules/named interfaces, dependency verification, module tests và Scenario, event publication registry, application module listeners, event externalization, runtime verification, module observability, documentation và module-aware Flyway. Module call đồng bộ vẫn dùng facade cho invariant/strong consistency; durable module events dùng cho side effect, projection và integration.

**Tech Stack:** Baseline Spring Boot 4.0.6; target platform Spring Boot 4.1.x + Spring Modulith 2.1.x, Java 25, Spring MVC, Spring GraphQL, Spring Security, Spring Data JPA, Spring Modulith JDBC Event Publication Registry, PostgreSQL, Flyway, Redis/Caffeine, Testcontainers, ArchUnit, Micrometer/Prometheus, GraalVM Native Build Tools, k6.

---

## 0. Nguyên tắc triển khai bắt buộc

1. Không dùng git reset --hard, git checkout -- hoặc xóa thay đổi đang có trong worktree.
2. Worktree hiện có nhiều thay đổi của người dùng; mỗi phase phải được triển khai trên branch riêng sau khi tạo checkpoint rõ ràng.
3. Không rename bảng hoặc xóa route/database column trong cùng commit với việc đổi package. Dùng expand–migrate–contract.
4. Spring Modulith Event Publication Registry là durable module-event mechanism mục tiêu. Custom outbox hiện tại chỉ tồn tại trong giai đoạn chuyển đổi; không được để một business event đi đồng thời qua cả hai cơ chế.
5. Không tăng connection pool, thread pool hoặc số worker chỉ để làm benchmark đạt số đẹp. Mọi thay đổi concurrency phải có số liệu DB wait, queue wait, CPU, heap và p95/p99.
6. Một module chỉ được công bố public facade, public event hoặc public DTO được ghi rõ. Các class trong internal/ không được module khác import.
7. Module call đồng bộ dùng facade; event dùng cho side effect, projection, notification và tích hợp không yêu cầu kết quả ngay.
8. Native Image là một build target phải được kiểm thử thật, không được coi plugin trong pom.xml là bằng chứng build thành công.
9. Không tạo executor/thread pool theo từng module. Chỉ có execution lanes dùng chung theo workload: request, module-event I/O và CPU-bound; giới hạn đồng thời nằm ở DB pool/provider semaphore/Event Publication Registry, không dùng queue RAM vô hạn.
10. Event payload chỉ chứa ID, version, tenant, actor/correlation metadata và dữ liệu bất biến cần thiết; không nhét JPA entity, collection lớn, binary hoặc full API response vào event.
11. Delivery semantics là at-least-once. Mọi listener/external consumer phải idempotent; không tuyên bố exactly-once nếu chưa có unique business key và test retry/crash.

## 1. Trạng thái hiện tại và mục tiêu

### 1.1. Các sự thật phải giữ trong kế hoạch

- Project có Spring Boot 4.0.6, Java 25, JPA, PostgreSQL, Flyway, MongoDB, Redis, GraphQL, Security, Mail, Actuator và native Maven plugin trong pom.xml.
- Có 592 production Java files, khoảng 44.000 production LOC và 181 test files.
- Test baseline lần audit gần nhất có 833 tests, 1 failure: route legacy /api/curriculum/tree vẫn tồn tại trong CurriculumController trong khi test yêu cầu loại bỏ.
- Nền tảng tốt cần giữ: open-in-view=false, query timeout, JDBC batching, 71 Flyway migrations, Testcontainers PostgreSQL, projections/read models, cache isolation, security deny-all fallback và transactional outbox.
- Rủi ro P0: các service in-memory/prototype, webhook public thiếu provider verification, build chưa xanh và UserAccessContext mặc định system/erg khi thiếu principal.
- Rủi ro P1: global horizontal layers đã tạo UserService, LmsReadModelService, LmsRuntimeService, AuthorizationService, CalendarReadService quá lớn; controller có nơi gọi repository/entity trực tiếp; nhiều API public dùng Map<String,Object>; route prefix chưa thống nhất.
- Cấu hình concurrency hiện có datasource pool mặc định 8, read executor 4/4 với queue 32, outbox worker poll 50 mỗi 5 giây và notification worker poll 200 mỗi 10 giây.

### 1.2. Trạng thái mục tiêu

~~~text
com.erg.ergspringboot/
├── ErgSpringApplication.java
├── auth/
├── users/
├── authorization/
├── organization/
├── school/
├── calendar/
├── curriculum/
├── learningcontent/
├── lms/
├── lcms/
├── notifications/
├── reporting/
├── crm/
├── documents/
├── platform/
└── shared/
~~~

Mỗi business module có dạng:

~~~text
<module>/
├── package-info.java
├── <Module>Operations.java
├── <BusinessEvent>.java
└── internal/
    ├── <use-case-1>/
    ├── <use-case-2>/
    ├── readmodel/
    └── persistence/
~~~

platform/ chỉ chứa kỹ thuật dùng chung: persistence bootstrap, security plumbing, cache, Redis, storage, observability, native image và concurrency. shared/ chỉ chứa kernel rất nhỏ như tenant ID, correlation ID, error contract và validation primitive; không đưa business entity vào đây.

## 2. Module ownership và dependency contract

| Module | Sở hữu | Không được sở hữu thay module khác |
|---|---|---|
| auth | login, refresh, recovery, session identity | role/permission policy nghiệp vụ |
| users | profile, account lifecycle, employee/user data | quyết định authorization cuối cùng |
| authorization | role, permission, scoped access policy | user profile persistence |
| organization | tenant, education unit, organization hierarchy | LMS attempt/grade |
| school | school operational core, classes, teachers | auth token/session |
| calendar | schedule, room, conflict, calendar projection | user permission persistence |
| curriculum | curriculum tree và curriculum lifecycle | content asset storage |
| learningcontent | canonical learning resources/assets/links | LMS attempt state |
| lms | assignment, recipient, attempt, quiz runtime, classroom/resource runtime | user account lifecycle |
| lcms | authoring, question bank, media, publication | runtime attempt state |
| notifications | message, delivery attempt, provider outbox | quyết định quyền truy cập |
| reporting | projections, report jobs, export/read model | mutate operational aggregate |
| crm | CRM workflow, referral, school deal/activity | LMS grade/attempt |
| documents | document metadata, ownership, lifecycle | generic shared persistence |

Dependency rule:

~~~text
module public facade/event
        ↑
module internal implementation
        ↑
platform adapters/configuration
~~~

Một module không import internal của module khác. Các module không truy cập JPA entity/repository của nhau. Nếu cần dữ liệu, dùng public facade, public query DTO hoặc event/projection.

---

## 3. Phase 0 — Baseline, branch và build contract

### Task 0.1: Tạo checkpoint không phá worktree

**Files:**
- Read-only: toàn bộ worktree hiện tại
- Create: docs/architecture/2026-07-20-modulith-baseline.md

- [ ] Ghi lại git status --short, git diff --stat, commit hiện tại và Java/Maven version vào baseline.
- [ ] Ghi rõ các file đang modified/untracked thuộc thay đổi trước đó; không stage hoặc commit chúng trong phase này.
- [ ] Khi bắt đầu implementation, tạo branch codex/spring-modulith-modernization từ commit đã được người dùng chấp nhận.
- [ ] Lưu output của .\mvnw.cmd -version và java -version vào baseline.
- [ ] Ghi service profile, datasource URL đã redacted, database pool, read executor và outbox settings đang dùng trong test.

**Verification:**

~~~powershell
git status --short
git diff --stat
.\mvnw.cmd -version
java -version
~~~

Expected: không có lệnh destructive; baseline cho thấy chính xác thay đổi trước khi migration bắt đầu.

### Task 0.2: Khóa build baseline

**Files:**
- Modify: src/test/java/com/erg/ergspringboot/controller/LearningResourceLegacyApiRemovalTest.java chỉ sau khi quyết định canonical route ở Task 1.2
- Create: docs/architecture/build-baseline.md

- [ ] Chạy .\mvnw.cmd test trên checkpoint và lưu tổng số test, failure, elapsed time.
- [ ] Không sửa test để che failure /api/curriculum/tree; failure phải được giải quyết bằng quyết định API ở Task 1.2.
- [ ] Chạy riêng native contract, security contract, outbox và query-count để ghi baseline từng nhóm.
- [ ] Ghi các suite cần tách sang Failsafe ở phase CI, không di chuyển test ngay trong baseline.

**Verification:**

~~~powershell
.\mvnw.cmd test
.\mvnw.cmd -Dtest=LearningResourceLegacyApiRemovalTest test
.\mvnw.cmd -Dtest=NativeImageBuildContractTest test
~~~

Expected: baseline ghi rõ failure hiện có; sau Task 1.2, full build phải xanh.

---

## 4. Phase 1 — API inventory và production surface

### Task 1.1: Tạo route inventory có owner và trạng thái

**Files:**
- Create: docs/architecture/api-inventory.md
- Create: src/test/java/com/erg/ergspringboot/architecture/RouteInventoryContractTest.java
- Read: src/main/java/com/erg/ergspringboot/controller/
- Read: src/main/java/com/erg/ergspringboot/platform/security/SecurityConfig.java

- [ ] Liệt kê tất cả Spring MVC mappings từ controller/ và GraphQL operation từ src/main/resources/graphql/schema.graphqls.
- [ ] Với mỗi route ghi: module owner, canonical/legacy/prototype, authentication, permission, tenant scope, idempotency requirement, persistence source và replacement route.
- [ ] Đánh dấu các controller đang dùng ModuleWorkflowService, CmsService, CatalogService hoặc DocumentService.
- [ ] Đánh dấu route đang permitAll, route có Map<String,Object>, route trả JPA entity và route không có @Valid.
- [ ] Test inventory phải fail nếu thêm route production không có owner record hoặc canonical status.

### Task 1.2: Chốt canonical API version và xử lý curriculum route

**Files:**
- Modify: src/main/java/com/erg/ergspringboot/controller/curriculum/CurriculumController.java
- Modify: src/test/java/com/erg/ergspringboot/controller/LearningResourceLegacyApiRemovalTest.java
- Modify: src/main/java/com/erg/ergspringboot/platform/security/SecurityConfig.java
- Create: src/test/java/com/erg/ergspringboot/controller/curriculum/CurriculumCanonicalRouteTest.java
- Modify: docs/architecture/api-inventory.md

- [ ] Chọn /api/v1/... làm canonical prefix cho learning resource/curriculum vì security contract hiện đã bảo vệ /api/v1 và test hiện tại yêu cầu loại bỏ /api/curriculum/tree.
- [ ] Di chuyển handler cần giữ sang canonical route /api/v1/curriculum/tree; cập nhật frontend inventory để tất cả caller dùng route này.
- [ ] Nếu cần backward compatibility, tạo route legacy riêng có @Deprecated, metric usage và ngày kết thúc được ghi trong inventory; không giữ alias vô thời hạn.
- [ ] Cập nhật SecurityConfig để permission matcher bao phủ canonical route và không để route cũ bypass policy.
- [ ] Test phải chứng minh route canonical trả đúng status/body và route legacy bị 404 sau khi hết deprecation window.

**Verification:**

~~~powershell
.\mvnw.cmd -Dtest=LearningResourceLegacyApiRemovalTest,CurriculumCanonicalRouteTest test
~~~

### Task 1.3: Chấm dứt API giả production

**Files:**
- Read/Modify: src/main/java/com/erg/ergspringboot/service/support/ModuleWorkflowService.java
- Read/Modify: src/main/java/com/erg/ergspringboot/service/cms/CmsService.java
- Read/Modify: src/main/java/com/erg/ergspringboot/service/catalog/CatalogService.java
- Read/Modify: src/main/java/com/erg/ergspringboot/service/documents/DocumentService.java
- Modify: các controller đang inject bốn service trên
- Create: src/test/java/com/erg/ergspringboot/architecture/ProductionPrototypeExposureTest.java

- [ ] Với mỗi endpoint prototype, quyết định một trong ba trạng thái: persistent thật, chỉ bật dưới profile prototype, hoặc xóa khỏi production surface.
- [ ] Không trả queued, healthy, saved, completed nếu chưa có persistent state/job thật.
- [ ] Nếu giữ prototype, đặt controller/bean dưới @Profile("prototype") và không để production profile đăng ký controller.
- [ ] Nếu implement thật, tạo entity, migration, repository, tenant ownership, audit event, typed request/response và idempotency key trước khi mở route.
- [ ] Test phải khởi động production profile và chứng minh prototype controller không tồn tại trong RequestMappingHandlerMapping.

---

## 5. Phase 2 — Thêm Spring Modulith không làm thay đổi behavior

### Task 2.1: Nâng platform theo change set độc lập và pin Modulith 2.1.x

**Files:**
- Modify: pom.xml
- Create: src/test/java/com/erg/ergspringboot/architecture/SpringModulithDependencyContractTest.java
- Create: docs/architecture/spring-platform-upgrade-report.md

- [ ] Làm xanh baseline trên Spring Boot 4.0.6 trước; không vừa sửa behavior vừa nâng platform.
- [ ] Trong commit platform riêng, nâng Spring Boot lên release 4.1.x đã được dependency matrix của project xác nhận và import org.springframework.modulith:spring-modulith-bom 2.1.x cùng release train.
- [ ] Thêm spring-modulith-starter-core ở compile scope.
- [ ] Thêm spring-modulith-starter-test ở test scope.
- [ ] Thêm spring-modulith-starter-jdbc để dùng Event Publication Registry bằng JDBC, tránh đưa lifecycle event qua Hibernate/JPA.
- [ ] Thêm spring-modulith-starter-insight ở runtime scope cho module actuator, module spans và event metrics.
- [ ] Chỉ thêm broker externalization artifact đúng broker được chọn; không kéo đồng thời Kafka, AMQP và JMS.
- [ ] Contract test kiểm tra Spring Boot/Modulith thuộc cùng release train, starter-test có test scope và không có test artifact trong production image.
- [ ] Chạy full unit/integration/native contract trước khi bắt đầu package move; nếu dependency chưa tương thích, giữ Boot 4.0.7 + Modulith 2.0.7 làm fallback release và không dùng 2.1-only outbox externalization cho tới platform release tiếp theo.

**Verification:**

~~~powershell
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd dependency:tree -Dincludes=org.springframework.modulith
~~~

Expected: không có Modulith version conflict, full verify xanh và platform upgrade nằm trong commit/release độc lập.

### Task 2.2: Khai báo detection strategy chỉ cho module đã migrate

**Files:**
- Create: src/test/java/com/erg/ergspringboot/architecture/ModulithVerificationTest.java
- Create: src/test/resources/application-modulith-test.yml
- Create: src/main/java/com/erg/ergspringboot/calendar/package-info.java

- [ ] Dùng spring.modulith.detection-strategy=explicitly-annotated trong test để legacy global packages chưa bị coi nhầm là business modules.
- [ ] Annotate từng package module đã migrate bằng @ApplicationModule.
- [ ] ModulithVerificationTest phải gọi ApplicationModules.of(ErgSpringApplication.class).verify().
- [ ] Test đầu tiên dùng calendar làm pilot vì có boundary rõ và controller hiện đang truy cập repository trực tiếp.
- [ ] Không bật runtime verification ở production cho tới khi danh sách module ổn định; verification CI là bắt buộc trước.

package-info.java của module pilot phải có annotation @ApplicationModule cho package com.erg.ergspringboot.calendar.

### Task 2.3: Định nghĩa public API/internal convention

**Files:**
- Create: docs/architecture/spring-modulith-boundaries.md
- Create: src/main/java/com/erg/ergspringboot/calendar/CalendarOperations.java
- Create: src/main/java/com/erg/ergspringboot/calendar/CalendarEventCreated.java
- Create: src/test/java/com/erg/ergspringboot/architecture/ModulePublicApiContractTest.java

- [ ] Public type của module nằm ở package gốc; implementation nằm trong internal/.
- [ ] Module khác chỉ import CalendarOperations, CalendarEventCreated hoặc DTO public đã ghi trong boundary document.
- [ ] Không dùng entity/JPA repository ở package gốc làm public API.
- [ ] Không đặt shared business model ở com.erg.ergspringboot.shared để né boundary.
- [ ] Test phải fail nếu một module import package .internal. của module khác.

### Task 2.4: Named interfaces và allowed dependencies

**Files:**
- Modify: package-info.java của từng business module
- Create: package-info.java cho public event/API package cần named interface
- Modify: src/test/java/com/erg/ergspringboot/architecture/ModulithVerificationTest.java
- Create: docs/architecture/module-dependency-matrix.md

- [ ] Mỗi module khai báo allowedDependencies theo dependency matrix; không dựa vào convention miệng.
- [ ] Public facade/event type nằm ở module root hoặc named interface rõ ràng bằng @NamedInterface.
- [ ] Không mở toàn bộ module bằng OPEN chỉ để verification xanh.
- [ ] Không tạo cycle users ↔ authorization: users gọi AuthorizationOperations cho quyết định quyền; authorization nhận user identity/scope qua public value object hoặc event, không gọi users.internal.
- [ ] Modulith verification phải fail khi module import type không thuộc named interface được công bố.

### Task 2.5: Runtime verification, documentation, observability và module-aware Flyway

**Files:**
- Modify: src/main/resources/application.yml
- Modify: src/main/java/com/erg/ergspringboot/config/persistence/FlywayConfig.java
- Modify: src/main/resources/db/migration/postgres/
- Create: src/test/java/com/erg/ergspringboot/architecture/ModulithDocumentationTest.java
- Create: src/test/java/com/erg/ergspringboot/config/ModuleFlywayMigrationTest.java
- Create: docs/architecture/generated-modules.md

- [ ] Bật spring.modulith.runtime.verification-enabled=true ở staging ngay khi tất cả package đã annotate; bật production sau một release staging xanh. Đây là startup check, không nằm trên request hot path.
- [ ] Dùng spring-modulith-starter-insight với trace sampling production; expose module actuator chỉ trên management network, không public Internet.
- [ ] Sinh module diagram và Application Module Canvas trong CI từ Spring Modulith Documenter.
- [ ] Freeze V1–V71 thành root history; migration mới đặt theo module owner dưới db/migration/postgres/<module> và dùng module-scoped history table.
- [ ] Bật spring.modulith.runtime.flyway-enabled chỉ sau khi migration contract test chứng minh existing Flyway checksums/history không đổi và Native Image đóng gói đủ root/module resources.
- [ ] Module integration test chỉ chạy root migrations và migrations của module/dependencies được bootstrap.

---

## 6. Phase 3 — Architecture guardrails

### Task 3.1: Cấm controller truy cập persistence trực tiếp

**Files:**
- Create: src/test/java/com/erg/ergspringboot/architecture/ControllerBoundaryArchTest.java
- Modify: src/main/java/com/erg/ergspringboot/controller/calendar/rest/CalendarRestController.java
- Modify: src/main/java/com/erg/ergspringboot/controller/notifications/NotificationAdminController.java
- Modify: src/main/java/com/erg/ergspringboot/controller/reporting/ReportJobController.java
- Modify: src/main/java/com/erg/ergspringboot/controller/learningcontent/LearningContentController.java

- [ ] Viết ArchUnit rule cho migrated modules: controller không depend ..repository.., ..model.. JPA entity hoặc EntityManager.
- [ ] Di chuyển query/tenant/class scope check từ ReportJobController vào application service.
- [ ] Di chuyển direct repository call trong CalendarRestController vào calendar use case/query handler.
- [ ] Di chuyển outbox counts/admin command khỏi NotificationAdminController vào notification application facade.
- [ ] Đổi LearningContentController trả typed response/projection, không trả JPA entity.
- [ ] Legacy controllers được đưa vào allowlist có tên và owner cụ thể; allowlist phải giảm sau mỗi module migration.

### Task 3.2: Cấm API public dùng Map tùy tiện

**Files:**
- Create: src/test/java/com/erg/ergspringboot/architecture/PublicApiTypeContractTest.java
- Modify: public controller request/response classes được route inventory đánh dấu
- Create: các record request/response theo từng module

- [ ] Dùng records/classes typed cho request/response.
- [ ] Map<String,Object> chỉ được phép ở adapter thật sự dynamic và phải có schema/validator riêng.
- [ ] Thêm @Valid cho input body và validate enum/status/size/tenant scope.
- [ ] Public response không expose lazy JPA association.
- [ ] Contract test fail nếu controller public có method return entity hoặc parameter Map ngoài allowlist có schema.

### Task 3.3: Enforce dependency direction

**Files:**
- Create: src/test/java/com/erg/ergspringboot/architecture/DependencyDirectionArchTest.java
- Modify: migrated module packages
- Create: docs/architecture/dependency-rules.md

- [ ] domain hoặc business rule không import platform.persistence, org.hibernate, org.springframework.data.
- [ ] api không import repository/entity.
- [ ] infrastructure có thể implement application contract nhưng không được trở thành public module API.
- [ ] platform không import business module.
- [ ] shared/kernel không import business module.
- [ ] Không dùng field injection trong business module; constructor injection là bắt buộc.
- [ ] Không dùng @Autowired(required=false) cho dependency nghiệp vụ; optional integration phải được biểu diễn bằng profile/conditional adapter rõ ràng.

---

## 7. Phase 4 — Security và production safety P0/P1

### Task 4.1: Webhook provider verification và replay protection

**Files:**
- Modify: src/main/java/com/erg/ergspringboot/platform/security/SecurityConfig.java
- Modify: src/main/java/com/erg/ergspringboot/controller/bot/WebhookController.java
- Create: src/main/java/com/erg/ergspringboot/platform/webhook/WebhookSignatureVerifier.java
- Create: src/main/java/com/erg/ergspringboot/platform/webhook/DiscordEd25519SignatureVerifier.java
- Create: src/main/java/com/erg/ergspringboot/platform/webhook/TelegramSecretTokenVerifier.java
- Create: src/main/java/com/erg/ergspringboot/platform/webhook/WebhookReplayGuard.java
- Create: typed provider payload records dưới module webhook
- Create: src/test/java/com/erg/ergspringboot/platform/webhook/WebhookSecurityTest.java

- [ ] Loại bỏ blanket /webhooks/** permitAll; chỉ permit exact provider paths cần public.
- [ ] Verify Discord bằng raw body, timestamp và Ed25519 header.
- [ ] Verify Telegram bằng secret token header được cấu hình riêng.
- [ ] Giới hạn body size trước parse JSON.
- [ ] Dùng timestamp tolerance và nonce/idempotency key lưu durable hoặc Redis có TTL để chặn replay.
- [ ] Parse typed payload sau khi verify; không nhận arbitrary Map<String,Object> vào domain.
- [ ] Controller chỉ enqueue durable job/outbox và trả HTTP 202 Accepted; không chạy workflow dài trong request thread.
- [ ] Metric có webhook.accepted, webhook.rejected.signature, webhook.replayed và webhook.queue_lag.

### Task 4.2: Chuyển UserAccessContext sang fail-closed

**Files:**
- Modify: src/main/java/com/erg/ergspringboot/platform/security/UserAccessContext.java
- Create: src/main/java/com/erg/ergspringboot/platform/security/SystemActorContext.java
- Create: src/main/java/com/erg/ergspringboot/platform/security/AuthenticationRequiredException.java
- Modify: scheduled workers, outbox workers và event listeners đang gọi UserAccessContext
- Create: src/test/java/com/erg/ergspringboot/platform/security/UserAccessContextFailClosedTest.java

- [ ] UserAccessContext.from() không còn mặc định userId="system" và tenantId="erg" khi HTTP request thiếu principal.
- [ ] Request user context thiếu principal phải throw AuthenticationRequiredException.
- [ ] Scheduled/outbox/system task dùng SystemActorContext có actor type, tenant scope và reason rõ ràng.
- [ ] Không cho system actor vô tình dùng route user-facing.
- [ ] Test chứng minh request không có authentication bị từ chối và system worker vẫn chạy bằng context riêng.

### Task 4.3: Production configuration fail-safe

**Files:**
- Modify: src/main/resources/application.yml
- Create: src/main/resources/application-local.yml
- Create: src/main/resources/application-test.yml
- Modify: ops/docker/.env.production.example
- Modify: ops/docker/compose.yml
- Create: src/test/java/com/erg/ergspringboot/config/ProductionConfigurationSafetyTest.java

- [ ] Bỏ default profile dev khỏi common config; profile phải được chọn ở deployment.
- [ ] Common config dùng safe defaults: Flyway không bị vô tình tắt trong production, process-local state tắt, stacktrace không expose, error message không expose, docs/Scalar tắt mặc định production.
- [ ] Local developer convenience chỉ nằm trong application-local.yml và .env.dev.example.
- [ ] Dùng @ConfigurationProperties + @Validated cho nhóm config quan trọng thay vì dàn trải @Value.
- [ ] Thiếu secret, datasource hoặc signing key production phải fail startup.
- [ ] Test profile production phải assert docs, stacktrace và process-local behavior theo policy.

---

## 8. Phase 5 — API contract và typed boundary

### Task 5.1: Chuẩn hóa response/status/version

**Files:**
- Review/Modify: src/main/java/com/erg/ergspringboot/common/response/ApiResponse.java
- Modify: src/main/java/com/erg/ergspringboot/controller/
- Create: src/test/java/com/erg/ergspringboot/controller/ApiHttpStatusContractTest.java
- Modify: src/main/resources/graphql/schema.graphqls

- [ ] Chỉ giữ một canonical prefix /api/v1 cho REST production.
- [ ] HTTP status là source of truth; không đặt status 201 trong body khi HTTP status là 200.
- [ ] Create trả 201, accepted job trả 202, delete thành công trả 204 hoặc response contract đã chốt.
- [ ] Deprecation header và metric được thêm cho route cũ trong thời gian chuyển đổi.
- [ ] Error response có error code ổn định, correlation ID và field validation errors; không trả exception message nội bộ.
- [ ] GraphQL mutation/query dùng typed input/output và permission ở application boundary.

### Task 5.2: Tách canonical read/write và projection

**Files:**
- Modify: src/main/java/com/erg/ergspringboot/service/lms/readmodel/LmsReadModelService.java
- Modify: src/main/java/com/erg/ergspringboot/service/lms/LmsRuntimeService.java
- Create: src/main/java/com/erg/ergspringboot/lms/internal/readmodel/
- Create: src/test/java/com/erg/ergspringboot/lms/ReadModelContractTest.java

- [ ] Tách read query từ mutation service theo use case.
- [ ] Query dùng projection/DTO thay vì load toàn aggregate khi chỉ cần đọc.
- [ ] Mỗi projection có owner, refresh/reconciliation policy và version.
- [ ] Read model stale behavior được mô tả rõ; không giả vờ synchronous nếu cập nhật qua event.
- [ ] Query-count threshold trở thành assertion fail, không chỉ log warning.

---

## 9. Phase 6 — Calendar pilot migration

Calendar là pilot vì có boundary nghiệp vụ tương đối rõ, có read/command service riêng và controller hiện đang vượt ranh giới repository.

### Task 6.1: Tạo module facade và vertical slices

**Files:**
- Create: src/main/java/com/erg/ergspringboot/calendar/package-info.java
- Create: src/main/java/com/erg/ergspringboot/calendar/CalendarOperations.java
- Create: src/main/java/com/erg/ergspringboot/calendar/internal/createevent/
- Create: src/main/java/com/erg/ergspringboot/calendar/internal/updateevent/
- Create: src/main/java/com/erg/ergspringboot/calendar/internal/cancelevent/
- Create: src/main/java/com/erg/ergspringboot/calendar/internal/listevents/
- Create: src/main/java/com/erg/ergspringboot/calendar/internal/persistence/

- [ ] Chuyển từng use case từ CalendarCommandService/CalendarReadService vào slice riêng.
- [ ] CalendarOperations chỉ expose command/query cần module khác gọi; không expose repository.
- [ ] Application service kiểm tra tenant/class scope; controller không tự kiểm tra scope.
- [ ] Persistence adapter trả typed projection/domain result, không trả entity ra API.
- [ ] Tất cả command có idempotency behavior và transaction boundary rõ.

### Task 6.2: Di chuyển controller theo strangler pattern

**Files:**
- Modify: src/main/java/com/erg/ergspringboot/controller/calendar/rest/CalendarRestController.java
- Modify: src/main/java/com/erg/ergspringboot/controller/calendar/rest/CalendarRestController.java thành adapter delegate trong deprecation window
- Modify: calendar repository/model package references
- Modify: src/main/java/com/erg/ergspringboot/config/persistence/PostgresJpaConfig.java

- [ ] Controller cũ chỉ delegate vào CalendarOperations trong thời gian chuyển đổi.
- [ ] Không để controller cũ inject repository trực tiếp.
- [ ] Cập nhật @EnableJpaRepositories và PersistenceManagedTypesScanner khi repository/entity đổi package.
- [ ] Chạy module tests trước khi xóa adapter cũ.
- [ ] Xóa adapter cũ chỉ sau khi route inventory và frontend contract xác nhận không còn caller.

### Task 6.3: Kiểm thử module

**Files:**
- Create: src/test/java/com/erg/ergspringboot/calendar/CalendarModuleIntegrationTest.java
- Create: src/test/java/com/erg/ergspringboot/calendar/CalendarModuleArchitectureTest.java
- Move/add: calendar repository/controller/service tests vào package module mới

- [ ] Dùng @ApplicationModuleTest hoặc @ModuleSlicing cho module integration test.
- [ ] Kiểm tra create/update/cancel/list, tenant isolation, class scope, optimistic locking và idempotency.
- [ ] Test module không khởi động các module không liên quan.
- [ ] Test architecture không cho calendar import users.internal, authorization.internal hoặc persistence của module khác.

---

## 10. Phase 7 — Identity và authorization migration

### Task 7.1: Tách UserService theo command/query

**Files:**
- Modify: src/main/java/com/erg/ergspringboot/service/users/UserService.java
- Create: src/main/java/com/erg/ergspringboot/users/package-info.java
- Create: src/main/java/com/erg/ergspringboot/users/UserOperations.java
- Create: src/main/java/com/erg/ergspringboot/users/internal/create/
- Create: src/main/java/com/erg/ergspringboot/users/internal/profile/
- Create: src/main/java/com/erg/ergspringboot/users/internal/password/
- Create: src/main/java/com/erg/ergspringboot/users/internal/session/
- Create: src/main/java/com/erg/ergspringboot/users/internal/roleassignment/
- Create: src/main/java/com/erg/ergspringboot/users/internal/query/

- [ ] Tách logic profile, provision account, session, password, role/permission, audit và invite thành use case riêng.
- [ ] Giữ transaction nhỏ; gửi email qua outbox, không gọi SMTP trong transaction.
- [ ] Public API của users không expose JPA User entity.
- [ ] Các query list/detail dùng projection có tenant predicate bắt buộc.
- [ ] Giữ compatibility facade trong một release, sau đó xóa UserService monolith.

### Task 7.2: Tách Auth và Authorization

**Files:**
- Modify: src/main/java/com/erg/ergspringboot/service/auth/AuthService.java
- Modify: src/main/java/com/erg/ergspringboot/service/authorization/AuthorizationService.java
- Create: src/main/java/com/erg/ergspringboot/auth/
- Create: src/main/java/com/erg/ergspringboot/authorization/
- Modify: src/main/java/com/erg/ergspringboot/platform/security/PermissionAuthorizationManager.java
- Modify: src/main/java/com/erg/ergspringboot/platform/security/ErgPolicyService.java

- [ ] Auth sở hữu login, refresh, recovery và session identity.
- [ ] Authorization sở hữu role, permission, scoped policy và access decision.
- [ ] PermissionAuthorizationManager chỉ là web adapter gọi authorization facade, không chứa policy nghiệp vụ mới.
- [ ] Các policy version/security event hiện có phải tiếp tục được phát hành idempotently.
- [ ] Test permission matrix, tenant scope, education-unit/class scope và refresh-token replay trước/sau migration.

---

## 11. Phase 8 — LMS/LCMS migration theo hot path

### Task 8.1: Tách LMS runtime thành assignment, attempt, classroom và resource

**Files:**
- Modify: src/main/java/com/erg/ergspringboot/service/lms/LmsRuntimeService.java
- Modify: src/main/java/com/erg/ergspringboot/service/lms/readmodel/LmsReadModelService.java
- Create: src/main/java/com/erg/ergspringboot/lms/package-info.java
- Create: src/main/java/com/erg/ergspringboot/lms/LmsOperations.java
- Create: src/main/java/com/erg/ergspringboot/lms/internal/assignment/
- Create: src/main/java/com/erg/ergspringboot/lms/internal/attempt/
- Create: src/main/java/com/erg/ergspringboot/lms/internal/classroom/
- Create: src/main/java/com/erg/ergspringboot/lms/internal/resource/
- Create: src/main/java/com/erg/ergspringboot/lms/internal/readmodel/

- [ ] Giữ invariants của Attempt trong aggregate/domain rule: start, submit, evaluate, status transition và idempotency.
- [ ] Command path không load các collection không cần thiết.
- [ ] Read path dùng projection/index đã có; không gọi command service để đọc.
- [ ] Ghi AttemptSubmitted/AssignmentPublished với event version, tenant, correlation ID và causation ID.
- [ ] Bảo đảm concurrent submit cho cùng attempt chỉ có một kết quả hợp lệ bằng database constraint/optimistic lock.
- [ ] Chạy load test load/k6/erg-5k-submission-burst.js sau mỗi thay đổi hot path.

### Task 8.2: Tách LCMS authoring/media/publishing

**Files:**
- Modify: quiz/lcms controllers and services under src/main/java/com/erg/ergspringboot/controller/quiz/, controller/lcms/, service/quiz/
- Create: src/main/java/com/erg/ergspringboot/lcms/package-info.java
- Create: src/main/java/com/erg/ergspringboot/lcms/internal/authoring/
- Create: src/main/java/com/erg/ergspringboot/lcms/internal/questionbank/
- Create: src/main/java/com/erg/ergspringboot/lcms/internal/media/
- Create: src/main/java/com/erg/ergspringboot/lcms/internal/publishing/
- Modify: src/main/java/com/erg/ergspringboot/service/quiz/media/MediaService.java

- [ ] Tách authoring draft, question bank, media và publish state machine.
- [ ] Media transaction chỉ tạo/update PENDING record; đóng transaction trước storageAdapter.presignUpload().
- [ ] Storage failure chuyển record sang failed/cleanup state bằng transaction ngắn.
- [ ] Publish operation có optimistic version và audit event.
- [ ] Không trả storage provider response trực tiếp ra public API.
- [ ] Native hints cho storage resource/client được kiểm tra sau khi module di chuyển.

---

## 12. Phase 9 — Notifications, outbox và event policy

### Task 9.1: Dùng Spring Modulith Event Publication Registry làm durable event core

**Files:**
- Modify: pom.xml
- Modify: src/main/resources/application.yml
- Create: src/main/resources/db/migration/postgres/notifications/V1__spring_modulith_event_publication.sql
- Create: src/main/java/com/erg/ergspringboot/platform/events/ModuleEventMetadata.java
- Create: src/main/java/com/erg/ergspringboot/platform/events/ModuleEventRecoveryJob.java
- Create: src/test/java/com/erg/ergspringboot/platform/events/ModulithEventRegistryIntegrationTest.java
- Modify/remove after cutover: src/main/java/com/erg/ergspringboot/service/outbox/TransactionalOutboxWorker.java
- Modify/remove after cutover: src/main/java/com/erg/ergspringboot/model/outbox/OutboxEvent.java
- Modify/remove after cutover: src/main/java/com/erg/ergspringboot/repository/outbox/OutboxEventRepository.java

- [ ] Dùng spring-modulith-starter-jdbc: application transaction lưu aggregate và Event Publication Registry record trong cùng PostgreSQL transaction.
- [ ] Đặt spring.modulith.events.registry-trigger-annotation thành @ApplicationModuleListener để registry không bắt mọi @TransactionalEventListener ngoài policy.
- [ ] Business event là immutable record có eventId, eventVersion, tenantId, actorId, aggregateId, aggregateVersion, occurredAt, correlationId và causationId.
- [ ] Event payload nhỏ hơn 2 KiB ở hot path, chỉ chứa ID/version và dữ liệu bất biến cần cho consumer; consumer query projection riêng khi cần dữ liệu lớn.
- [ ] Dùng completion mode delete cho hot publication table; business audit giữ ở audit table riêng. Theo dõi table bloat, autovacuum và index completion/status.
- [ ] Recovery job dùng FailedEventPublications/IncompleteEventPublications với batch size 100, maximum in-flight 16 và minimum age 30 giây; không bật republish-on-restart trong deployment nhiều instance.
- [ ] Mọi listener có unique idempotency key theo listenerName + eventId hoặc business key; test crash sau side effect nhưng trước completion phải không tạo logical duplicate.
- [ ] Custom outbox V66 tiếp tục chạy cho event cũ trong migration window nhưng event type mới chỉ đi Event Publication Registry. Sau khi backlog cũ bằng 0 và parity test xanh, disable worker rồi xóa code/table ở migration contract release sau.

### Task 9.2: Event execution model không sinh thread/queue rác

**Files:**
- Create: src/main/java/com/erg/ergspringboot/platform/concurrency/ModuleEventTaskExecutorConfig.java
- Modify: src/main/java/com/erg/ergspringboot/platform/concurrency/ErgReadTaskExecutor.java
- Modify: src/main/resources/application.yml
- Create: src/test/java/com/erg/ergspringboot/platform/events/ModuleEventConcurrencyTest.java
- Create: src/test/java/com/erg/ergspringboot/platform/concurrency/ExecutorResourceContractTest.java
- Modify: application module listeners under users, authorization, lms, notifications và reporting

- [ ] Dùng tối đa hai execution lanes dùng chung: module-event-io cho blocking I/O và module-event-cpu cho CPU-bound projection; không tạo executor theo module/listener.
- [ ] module-event-io có bounded concurrency 16, queue 0 hoặc rất nhỏ và rejection có metric; Event Publication Registry là durable backlog, không dùng RAM queue lớn.
- [ ] module-event-cpu có parallelism bằng số CPU được cấp cho container, queue bounded 32 và không chạy blocking I/O.
- [ ] Với Java 25, benchmark lane I/O bằng virtual threads có semaphore giới hạn per resource; nếu dùng platform threads, giữ fixed bounded pool. Không dùng common ForkJoinPool và không gọi Thread constructor trong business code.
- [ ] Event object mang context metadata; không copy SecurityContext/tenant ThreadLocal mù quáng sang listener. Listener lấy actor/tenant từ event và xác lập context trong scope có finally cleanup.
- [ ] Listener phải timeout, retryable error được để registry resubmit, non-retryable error đi failed/DLQ; không block HTTP request thread.
- [ ] Kiểm thử nhiều event đồng thời, rejection, listener timeout, process restart và duplicate delivery.

### Task 9.3: Externalization và broker boundary

**Files:**
- Modify: pom.xml chỉ thêm broker artifact đã chọn
- Create: src/main/java/com/erg/ergspringboot/platform/events/external/ExternalizedEventConfiguration.java
- Create: src/test/java/com/erg/ergspringboot/platform/events/ExternalizedEventContractTest.java
- Modify: module public event records cần externalize
- Modify: ops/docker/compose.yml nếu broker được bật trong môi trường integration

- [ ] Chỉ externalize business event có consumer ngoài process; không externalize mọi internal event.
- [ ] Đánh dấu event externalized bằng @Externalized với target/topic và routing key ổn định, versioned.
- [ ] Nếu yêu cầu broker outbox đa instance/order-preserving, dùng Spring Modulith 2.1 Namastack outbox mode; không tự xây thêm dispatcher song song.
- [ ] Nếu chỉ dùng internal module listeners, không thêm Kafka/AMQP dependency để tránh tài nguyên và vận hành không cần thiết.
- [ ] Event externalization phải có schema compatibility, DLQ, retry, correlation ID, tenant classification và consumer idempotency.
- [ ] Test producer publish, broker unavailable, retry/resubmit, schema version cũ/mới và duplicate consumer.

### Task 9.4: Bounded worker concurrency cho legacy provider delivery trong cutover

**Files:**
- Modify: src/main/java/com/erg/ergspringboot/service/notifications/NotificationOutboxWorker.java trong migration window
- Modify: src/main/java/com/erg/ergspringboot/service/outbox/TransactionalOutboxWorker.java trong migration window
- Create: src/test/java/com/erg/ergspringboot/service/outbox/OutboxConcurrencyTest.java
- Create: docs/runbooks/outbox-cutover.md

- [ ] Claim dùng FOR UPDATE SKIP LOCKED; dispatch external call ngoài transaction; transition trong transaction ngắn.
- [ ] Giới hạn concurrency theo provider và rate limit để không tạo retry storm.
- [ ] Dùng lease để nhiều instance claim song song mà không double-send trong cùng lease.
- [ ] Khi queue đầy, worker backpressure và metric rejection; không tạo unbounded thread hoặc queue.
- [ ] Test nhiều worker/instance giả lập cùng claim batch và assert mỗi idempotency key chỉ có một logical success.
- [ ] Xóa worker legacy sau khi Event Publication Registry parity dashboard chứng minh mọi event mới đã qua listener/registry.

### Task 9.5: Quy tắc dùng Spring Modulith event

**Files:**
- Create: docs/architecture/event-policy.md
- Create: docs/architecture/event-catalog.md
- Create: src/test/java/com/erg/ergspringboot/architecture/EventBoundaryContractTest.java
- Create: src/test/java/com/erg/ergspringboot/platform/events/ScenarioEventFlowTest.java

- [ ] Direct module facade cho nghiệp vụ cần kết quả đồng bộ/strong consistency.
- [ ] In-process ApplicationEvent + @ApplicationModuleListener + Event Publication Registry cho side effect cùng process nhưng không cần caller chờ.
- [ ] Externalized event + Modulith externalization/outbox cho broker hoặc consumer ngoài process.
- [ ] Mọi event có schema version, metadata, retention policy và consumer idempotency.
- [ ] Không phát event cho mỗi field mutation nếu consumer không cần; event phải biểu diễn business consequence.
- [ ] Dùng Spring Modulith Scenario để test stimulus → async listener → state/event outcome với timeout, không dùng sleep cố định.
- [ ] Không gọi external provider trong transaction command; listener thực hiện sau commit và có retry/recovery.

---

## 13. Phase 10 — Reporting và CQRS-lite

### Task 10.1: Tách reporting khỏi operational domain

**Files:**
- Modify: src/main/java/com/erg/ergspringboot/controller/reporting/ReportJobController.java
- Modify: src/main/java/com/erg/ergspringboot/service/reporting/
- Create: src/main/java/com/erg/ergspringboot/reporting/package-info.java
- Create: src/main/java/com/erg/ergspringboot/reporting/ReportingOperations.java
- Create: src/main/java/com/erg/ergspringboot/reporting/internal/generate/
- Create: src/main/java/com/erg/ergspringboot/reporting/internal/export/
- Create: src/main/java/com/erg/ergspringboot/reporting/internal/projection/
- Create: src/test/java/com/erg/ergspringboot/reporting/ReportingModuleIntegrationTest.java

- [ ] ReportJobController chỉ gọi reporting facade; không load Assignment entity để tự kiểm tra tenant/class scope.
- [ ] Report job có durable status, idempotency key, retry và cancellation state.
- [ ] Projection/read model không được mutate operational aggregate.
- [ ] Export stream/batch không giữ transaction dài.
- [ ] Reconciliation worker có watermark, retry và metric lag.
- [ ] Test báo cáo trong trạng thái projection stale phải trả trạng thái rõ ràng, không trả dữ liệu giả đã hoàn tất.

---

## 14. Phase 11 — Concurrency và performance engineering

### Task 11.1: Xây workload model và SLO gate

**Files:**
- Create: docs/performance/2026-07-workload-model.md
- Modify: load/README.md
- Modify: load/k6/erg-5k-steady.js
- Modify: load/k6/erg-5k-submission-burst.js
- Create: load/k6/webhook-burst.js
- Create: load/k6/outbox-dispatch.js

- [ ] Ghi workload theo route: RPS, concurrency, read/write ratio, payload size, tenant distribution, DB rows và expected p95/p99.
- [ ] Dùng initial release gates: error rate < 1%, p95 read < 300 ms cho workload cacheable, p95 write < 800 ms cho command chuẩn, DB pool wait < 5% request time; product có thể siết các gate sau khi có production telemetry.
- [ ] Mỗi k6 script có threshold, setup/teardown và dataset seed reproducible.
- [ ] Test burst submit, steady read, webhook burst và outbox dispatch riêng; không trộn để che bottleneck.

### Task 11.2: Tối ưu database/connections theo capacity

**Files:**
- Modify: src/main/resources/application.yml
- Modify: ops/postgres/top-queries.sql
- Modify: ops/postgres/explain-capture.sql
- Modify: ops/postgres/accepted-workload.sql
- Create: docs/performance/db-pool-capacity.md

- [ ] Tính pool per instance từ PostgreSQL max_connections, reserve cho migration/admin/replication và số instance dự kiến.
- [ ] Không tăng DB__MAX_OPEN_CONNS nếu chưa có evidence connection wait và DB CPU/IO còn đủ.
- [ ] Kiểm tra index/plan cho assignment/attempt/calendar/outbox/report queries bằng EXPLAIN (ANALYZE, BUFFERS) trên dataset scale.
- [ ] Đo lock wait, deadlock, slow query, connection wait và transaction duration.
- [ ] Dùng projection/read replica/cache cho read-heavy flow thay vì mở thêm JDBC connection vô hạn.

### Task 11.3: Virtual thread benchmark có kiểm soát

**Files:**
- Modify: src/main/resources/application.yml
- Modify: src/main/java/com/erg/ergspringboot/platform/concurrency/ErgReadTaskExecutor.java nếu benchmark chứng minh cần đổi model
- Create: src/test/java/com/erg/ergspringboot/platform/concurrency/VirtualThreadLoadContractTest.java
- Create: docs/performance/virtual-thread-benchmark.md

- [ ] Chạy cùng workload với SPRING_THREADS_VIRTUAL_ENABLED=false và true.
- [ ] Đo RPS, p95/p99, CPU, heap, DB pool wait, queue rejection, pinned thread và error rate.
- [ ] Giữ bounded executor cho CPU-heavy task; không dùng virtual thread để làm CPU-bound computation nhanh hơn.
- [ ] Nếu bật virtual thread production, giới hạn database bằng connection pool/semaphore thay vì tạo pool virtual thread.
- [ ] Chỉ merge config mặc định mới sau khi benchmark đạt SLO mà không tăng DB saturation hoặc memory risk.

### Task 11.4: Cache và backpressure

**Files:**
- Modify: src/main/java/com/erg/ergspringboot/config/CacheConfig.java
- Modify: src/main/java/com/erg/ergspringboot/platform/concurrency/ErgReadTaskExecutor.java
- Modify: src/main/resources/application.yml
- Create: src/test/java/com/erg/ergspringboot/platform/cache/CacheStampedeTest.java
- Create: src/test/java/com/erg/ergspringboot/platform/concurrency/ReadExecutorBackpressureTest.java

- [ ] Cache key luôn gồm tenant và authorization-relevant scope.
- [ ] Có TTL/jitter, size limit và negative-cache policy cho hot lookup.
- [ ] Cache miss fan-out phải có single-flight hoặc bounded semaphore.
- [ ] Executor queue bounded; rejection trả lỗi retryable/429 phù hợp thay vì block vô hạn.
- [ ] Redis L2 chỉ bật cho named caches đã benchmark; không cache JPA entity trực tiếp.

### Task 11.5: Đo overhead và throughput của Modulith events

**Files:**
- Create: load/k6/modulith-events.js
- Create: src/test/java/com/erg/ergspringboot/platform/events/EventPublicationThroughputTest.java
- Create: docs/performance/modulith-event-overhead.md
- Modify: src/main/resources/application.yml

- [ ] Benchmark cùng command ở ba chế độ: direct facade không event, event listener không durable registry và Event Publication Registry đầy đủ.
- [ ] Đo event rows/transaction, commit latency, DB WAL/IO, publication lag, listener throughput, retry rate và request p95/p99.
- [ ] Release gate: event registry không làm p95 command tăng quá 10% trên workload chuẩn; nếu vượt, giảm số listener/payload hoặc chuyển consumer không quan trọng sang projection batch, không tăng thread vô hạn.
- [ ] Event publication table phải có index theo listener/status/publication date và completion cleanup; kiểm tra autovacuum sau sustained load.
- [ ] Event backlog phải chịu được restart và multi-instance; test không mất event khi crash giữa commit và listener completion.
- [ ] Chỉ mở event cho business consequence có consumer thật; event không có consumer bị architecture test từ chối.

---

## 15. Phase 12 — Native Image và AOT

### Task 12.1: Tạo native build profile rõ ràng

**Files:**
- Modify: pom.xml
- Modify: ops/native/README.md
- Modify: ops/docker/compose.native.yml
- Modify: src/test/java/com/erg/ergspringboot/platform/nativeimage/NativeImageBuildContractTest.java

- [ ] Tạo Maven profile native sử dụng org.graalvm.buildtools:native-maven-plugin và Spring Boot build-image/native compile path.
- [ ] Document Linux/CI command build native và smoke run; Windows developer command chỉ là wrapper/documented limitation.
- [ ] Native image phải dùng production profile, Flyway migration resource directory và externalized secrets.
- [ ] Không đưa spring-modulith-starter-test vào runtime artifact.
- [ ] Contract test kiểm tra profile, native plugin, migration resources, GraphQL schema và smoke command tồn tại.

### Task 12.2: Runtime hints cho module mới

**Files:**
- Modify: src/main/java/com/erg/ergspringboot/platform/nativeimage/
- Modify: src/main/java/com/erg/ergspringboot/config/persistence/FlywayConfig.java
- Modify: src/main/resources/graphql/schema.graphqls
- Create: runtime hints cạnh module cần reflection/resource/serialization
- Create: src/test/java/com/erg/ergspringboot/platform/nativeimage/ModuleNativeRuntimeHintsTest.java

- [ ] Ưu tiên records/typed DTO và static bean definitions.
- [ ] Đăng ký reflection hints cho polymorphic Jackson/GraphQL/JPA types chỉ khi AOT trace chứng minh cần.
- [ ] Đăng ký resource pattern cho GraphQL schema, Flyway migration và template/config resource.
- [ ] Không dùng Class.forName, dynamic bean registration hoặc runtime classpath mutation trong module business.
- [ ] Chạy native smoke với health, login/refresh, canonical curriculum read, LMS read, one command và outbox worker disabled/enabled theo profile.

### Task 12.3: JVM-vs-native performance decision

**Files:**
- Create: docs/performance/jvm-native-decision.md
- Modify: ops/native/README.md

- [ ] So sánh cold start, RSS memory, steady-state throughput, p95/p99 và container startup của JVM và native.
- [ ] Dùng native cho workload scale-to-zero/startup/memory-sensitive khi benchmark tốt hơn.
- [ ] Giữ JVM cho long-running CPU-heavy workload nếu JIT throughput tốt hơn.
- [ ] Không kết luận native là nhanh hơn chỉ vì startup nhanh hơn.

---

## 16. Phase 13 — Test, CI/CD và architecture governance

### Task 13.1: Tách unit/integration test feedback loop

**Files:**
- Modify: pom.xml
- Modify: src/test/java/ test tags/packages
- Modify: src/test/java/ để test integration dùng hậu tố IT và được Maven Failsafe chạy ở phase integration-test/verify
- Create: docs/engineering/test-strategy.md

- [ ] Unit/domain/module architecture tests chạy nhanh ở Surefire.
- [ ] PostgreSQL Testcontainers, GraphQL full context và external integration chạy qua Failsafe/integration phase.
- [ ] Không tắt integration test để làm build xanh.
- [ ] CI chạy test cho PR và full verify trước merge/release.
- [ ] Test module dùng @ApplicationModuleTest để chỉ bootstrap module cần thiết.

### Task 13.2: CI gates

**Files:**
- Modify/create: CI workflow đang được repository sử dụng
- Modify: pom.xml
- Create: docs/engineering/ci-gates.md

- [ ] Gate 1: compile và unit tests.
- [ ] Gate 2: Spring Modulith verification và ArchUnit.
- [ ] Gate 3: integration/Testcontainers.
- [ ] Gate 4: security contract, dependency vulnerability scan và secret scan.
- [ ] Gate 5: API inventory/canonical route contract.
- [ ] Gate 6: native build + smoke theo nightly/release vì native compile tốn tài nguyên hơn.
- [ ] Gate 7: k6 smoke/threshold trên môi trường benchmark ổn định, không chạy load test nặng trên PR thông thường.

### Task 13.3: Documentation và ownership

**Files:**
- Create: docs/architecture/module-catalog.md
- Create: docs/architecture/module-dependency-graph.md
- Create: docs/architecture/event-catalog.md
- Create: docs/runbooks/outbox-recovery.md
- Create: docs/runbooks/webhook-replay.md
- Create: docs/runbooks/native-release.md

- [ ] Mỗi module ghi owner team, public API, emitted/consumed events, database tables, SLO và on-call runbook.
- [ ] Sinh PlantUML/Asciidoctor module diagram từ Spring Modulith trong test/doc job.
- [ ] Event catalog có version, payload classification, PII policy, retention và retry/DLQ behavior.
- [ ] Runbook outbox có claim stuck lease, retry failed, dead-letter inspection và idempotency verification.

---

## 17. Phase 14 — Rollout và rollback

### Task 14.1: Strangler migration order

Thứ tự triển khai:

~~~text
1. Build/API/security baseline
2. Modulith verification + Calendar pilot
3. Users/Auth/Authorization
4. Reporting read model
5. LMS assignment/attempt hot path
6. LCMS authoring/media/publishing
7. Notifications/outbox consolidation
8. Prototype decision: persistent implementation, profile-gate hoặc removal
9. Native/performance production gate
~~~

- [ ] Mỗi module migration tạo một compatibility facade và test contract trước khi đổi caller.
- [ ] Mỗi module có một migration commit nhỏ: package/facade, controller adapter, persistence scan, tests, old-package removal.
- [ ] Không trộn security policy rewrite, database schema rewrite và package move trong một commit lớn.
- [ ] Sau mỗi module, chạy full architecture test và module integration test.

### Task 14.2: Canary và rollback

**Files:**
- Modify: deployment manifests dưới ops/docker/
- Create: docs/runbooks/modulith-rollback.md
- Modify: src/main/resources/application.yml với flag erg.modules.<module>.new-handler-enabled cho từng module đang canary

- [ ] Route canonical và adapter cũ có thể bật/tắt độc lập trong deprecation window.
- [ ] Database migration backward-compatible với version cũ trong ít nhất một deployment window.
- [ ] Outbox event consumer phải chấp nhận event version cũ và mới trong rollout.
- [ ] Canary theo metric error rate, p95/p99, DB lock wait, outbox lag và module-specific business failure.
- [ ] Rollback application không rollback Flyway destructive migration; dùng compatibility path và forward fix.

---

## 18. Definition of Done toàn chương trình

Chỉ coi modernization hoàn tất khi tất cả điều kiện sau đạt:

- [ ] .\mvnw.cmd verify xanh trên CI với cả unit và integration tests.
- [ ] Không còn production endpoint giả trả success mà không có persistent state.
- [ ] Webhook public được provider verification, timestamp/replay protection, body limit và durable enqueue.
- [ ] Thiếu principal không còn tự động biến thành user system/tenant erg.
- [ ] Canonical REST prefix và deprecation policy được ghi trong API inventory; route legacy test xanh.
- [ ] Spring Modulith verification không có cycle hoặc internal-package violation.
- [ ] Named interfaces và allowedDependencies được khai báo cho mọi migrated module; module graph và Application Module Canvas được sinh trong CI.
- [ ] @ApplicationModuleTest và Scenario cover các module có asynchronous/transactional event flow; test không dùng sleep cố định.
- [ ] Runtime module verification chạy xanh ở staging/production startup và module-aware Flyway chỉ chạy sau khi root migration history parity xanh.
- [ ] Controller không truy cập repository/entity trực tiếp trong migrated modules.
- [ ] Public API không trả JPA entity và không dùng Map<String,Object> ngoài allowlist có schema.
- [ ] UserService, AuthorizationService, LmsRuntimeService và LmsReadModelService không còn là điểm chứa nhiều use case không liên quan.
- [ ] Event Publication Registry là owner của durable module events; custom outbox cũ chỉ còn event cũ trong cutover và đã có ngày tắt/cleanup.
- [ ] Event listener/external consumer at-least-once, idempotent, có retry/resubmit/DLQ và event lag/processing metrics.
- [ ] Không có executor theo module; module-event lanes bounded, không common pool, không unbounded RAM queue và không ThreadLocal context leak.
- [ ] Concurrent submit/outbox/webhook tests chứng minh idempotency và không double-process logical operation.
- [ ] Load gates đạt trên workload đã ghi; connection pool/queue không bị tăng vô hạn để đạt số liệu.
- [ ] JVM và Native Image đã được benchmark bằng cùng workload; production target được chọn bằng số liệu.
- [ ] Module catalog, dependency graph, event catalog, ownership và runbooks được cập nhật.

## 19. Các mốc phát hành đề xuất

| Mốc | Nội dung | Điều kiện phát hành |
|---|---|---|
| Release A | Build xanh, route inventory, webhook hardening, fail-closed context | Không còn P0 security/build failure |
| Release B | Boot/Modulith platform upgrade, Modulith core/Insight/test, Calendar pilot, ArchUnit boundary | Full verify và native contract xanh; module verification xanh |
| Release C | Users/Auth/Authorization split, typed API | Security matrix và API contract xanh |
| Release D | LMS/LCMS hot path, read model, concurrency gates | 5k burst/steady load đạt threshold và không tăng duplicate submit |
| Release E | Event Publication Registry, Scenario tests, module-aware Flyway, reporting và prototype removal/profile isolation | Event parity, retry/resubmit/DLQ và module migration observable |
| Release F | Event externalization/broker nếu có consumer ngoài process, native build, CI governance, documentation/runbooks | JVM/native decision có benchmark; event schema/retry/rollback contract xanh |

## 20. Lệnh kiểm tra chuẩn sau mỗi task

~~~powershell
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd -Dtest=*Architecture*Test test
.\mvnw.cmd -Dtest=*Security*Test test
.\mvnw.cmd -Dtest=*ContractTest test
.\mvnw.cmd test
.\mvnw.cmd verify
~~~

Khi chạy integration/native/load, dùng các lệnh tương ứng trong runbook, không thay bằng cách tắt test:

~~~powershell
.\mvnw.cmd verify -Pintegration
.\mvnw.cmd -Pnative -DskipTests native:compile
docker compose -f ops/docker/compose.native.yml up --build
~~~

Nếu một task làm full build fail, dừng ở checkpoint, ghi failure vào task và sửa nguyên nhân trước khi chuyển task tiếp theo. Không đánh dấu hoàn tất dựa trên compile đơn thuần nếu task liên quan runtime, transaction, security, concurrency hoặc native image.

## 21. Ước lượng chương trình và tổ chức team

Đây là working estimate cho source hiện tại, không phải cam kết lịch trước khi đo velocity của hai release đầu:

| Nhân lực | Thời gian dự kiến | Cách tổ chức |
|---|---:|---|
| 2 backend engineers | 5–7 tháng | Một người migration module, một người giữ build/security/performance; WIP tối đa 2 module |
| 3–4 backend engineers + 1 QA/SDET | 3–5 tháng | Hai module stream, một platform/security stream, QA tự động hóa contract/load |
| 5–6 backend engineers + platform/DBA part-time | 10–16 tuần | Ba module stream nhưng vẫn tuần tự ở schema/outbox/hot path để tránh conflict |

Vai trò tối thiểu:

- Một technical owner quyết định module boundary, canonical API và event policy.
- Mỗi module có một owner end-to-end, không chia người chỉ làm controller/service/repository.
- Một engineer chịu trách nhiệm architecture tests, CI, Native Image và dependency upgrades.
- QA/SDET sở hữu contract, concurrency, integration và k6 regression suite.
- DBA/platform engineer review index, connection budget, online migration, container resource và rollout.

Không chạy quá ba module migration đồng thời. Calendar pilot phải hoàn tất và tạo template trước khi mở migration users/auth/LMS song song.

## 22. Risk register và biện pháp giảm thiểu

| Rủi ro | Mức | Dấu hiệu sớm | Biện pháp |
|---|---|---|---|
| Package move phá JPA scan | Cao | missing repository/entity khi startup | Contract test cho PostgresJpaConfig, migrate một module mỗi commit |
| Spring Modulith bị dùng như event bus cho mọi call | Cao | event table tăng nhanh, flow khó trace | Direct facade cho sync path, event policy test, một durable outbox |
| Big-bang rewrite | Rất cao | branch sống nhiều tuần, conflict liên tục | Strangler facade, release theo module, compatibility adapter |
| Duplicate durable event | Rất cao | Modulith registry và custom outbox cùng gửi | Event catalog có owner duy nhất, parity dashboard, cutover flag và idempotency key bắt buộc |
| Security regression khi đổi route | Rất cao | route canonical không match permission | Route inventory + SecurityConfig contract + denyAll fallback |
| Tenant data leak | Rất cao | query thiếu tenant predicate/cache key | Tenant isolation integration test, RLS test, cache key contract |
| Virtual thread làm DB nghẽn | Cao | connection wait/p99 tăng dù thread tăng | Benchmark A/B, connection budget, backpressure |
| Native image build xanh nhưng runtime lỗi | Cao | reflection/resource error ở endpoint hiếm | Runtime hint test + production-profile native smoke |
| Outbox backlog dưới burst | Cao | lag/pending/lease age tăng | SKIP LOCKED, bounded parallel dispatch, provider rate limit, DLQ runbook |
| Prototype tiếp tục lộ production | Cao | route trả success nhưng mất state sau restart | ProductionPrototypeExposureTest và profile-gate |
| Shared package trở thành dumping ground | Trung bình | business entity/service được chuyển vào shared | Shared-kernel ArchUnit rule và owner review |
| Test feedback quá chậm | Trung bình | dev bỏ chạy full suite | Surefire/Failsafe split, module slicing, CI full verify bắt buộc |

## 23. Kết quả tự review plan

Bản plan ban đầu chưa đủ vì mới dùng Spring Modulith chủ yếu cho module verification/test và giữ custom outbox làm trung tâm. Bản hiện tại đã sửa bốn điểm:

1. Spring Modulith là runtime event backbone cho module events, với Event Publication Registry bằng JDBC, listener lifecycle, failed/incomplete resubmission và Scenario test.
2. Custom outbox không bị chạy song song vô thời hạn; nó có migration window, parity dashboard, idempotency và cutover/cleanup rõ ràng.
3. Module-aware Flyway, runtime verification, Insight/observability và generated module documentation đã được đưa thành deliverable bắt buộc.
4. Concurrency có execution lanes dùng chung, bounded admission, no-common-pool/no-unbounded-queue, event payload nhỏ và benchmark overhead trước khi mở rộng listeners.

Plan không yêu cầu phát event cho mọi method. Đó là chủ đích kỹ thuật: synchronous facade giữ invariant/strong consistency và response path ngắn; Spring Modulith events xử lý side effect, projection và integration có lifecycle/retry/observability. Dùng toàn bộ capability phù hợp của Modulith không đồng nghĩa bật mọi broker hoặc biến mọi lời gọi thành asynchronous event.
