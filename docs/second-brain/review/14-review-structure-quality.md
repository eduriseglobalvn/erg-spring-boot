# 14 — Review cấu trúc & chất lượng code (erg-spring)

> Phạm vi: `src/main/java/com/erg/ergspringboot/**` và `src/test/**`. Review chỉ đọc (read-only), không chạy build/test.
> Mọi đường dẫn bên dưới là tương đối với `src/main/java/com/erg/ergspringboot/` trừ khi ghi rõ `src/test/...` hoặc `src/main/resources/...`.
> Ngày review: 2026-11-03. Reviewer: senior reviewer (strict).

## 0. Xác minh số liệu của coordinator

| Số liệu | Kết quả xác minh |
|---|---|
| 27 `*Operations` (~2.300 dòng) | Đúng: 27 file, 2.328 dòng, 303 public method; đếm regex được ~218 dòng chỉ gọi thẳng 1 method khác (51 method một dòng `{ return x.y(..); }` + 167 câu lệnh delegate) — tức đa số là pass-through. |
| 77 broad catch | Đúng: 77 (`catch (Exception|RuntimeException|Throwable ...)`). Phân loại ở §4. |
| Controller không import repository | Đúng ở tầng controller, nhưng *module `internal`* import ngược vào `service.*`/`repository.*` legacy 95 lần (chỉ tính `lms`, `lcms`, `crm`, `auth`) — boundary chỉ là vỏ bọc. |
| Hằng số trùng (`FAILED_LOGIN_RESULTS`, `STATUSES`, `DEFAULT_STATUSES`) | Đúng; `DEFAULT_STATUSES = List.of("DRAFT","PUBLISHED")` xuất hiện 2 lần. Còn các bản trùng nguy hiểm hơn ở §2. |

## 1. Scorecard (1–10)

| Tiêu chí | Điểm | Lý do (một dòng) | Bằng chứng |
|---|---|---|---|
| Layering clarity | **4** | Một lệnh đi qua 3–4 lớp chỉ chuyển tiếp; có bean `@Deprecated` vẫn implement cùng port; field tên `legacy` lại trỏ vào implementation mới. | `lms/LmsOperations.java:33-56`, `lms/internal/runtime/LmsRuntimeUseCase.java:15-17,40`, `service/lms/LmsRuntimeService.java:16-49` |
| Duplication | **4** | Cùng một quy tắc (trạng thái attempt kết thúc, tính quyền hiệu lực, loại scope) được viết lại 3–16 lần và đã lệch nghĩa nhau. | `repository/lms/AssignmentRepository.java:216` (+13 bản SQL), `platform/security/ErgPrincipalService.java:336-340` vs `platform/security/OidcPrincipalBridgeFilter.java:137` |
| Nesting / readability | **5** | Lồng sâu ở mức chấp nhận được (~5), nhưng có method nhồi cả chục câu lệnh vào **một dòng** rất khó review. | `lms/internal/attempt/LmsAttemptUseCase.java:203-205,389-390` |
| Error handling | **6** | Đa số catch rethrow có ngữ cảnh; có vài chỗ "best effort" không thực sự best effort (catch trong transaction đã bị đánh dấu rollback-only). | `lms/internal/attempt/LmsAttemptUseCase.java:389`, `service/quiz/runtime/QuizAttemptGradingService.java:34,184` |
| Transaction safety | **6** | SMTP và FCM đi qua outbox (tốt), nhưng R2 HEAD/PUT và connector sync vẫn chạy trong `@Transactional`. | `service/quiz/media/MediaService.java:22,86,97`, `enterpriseconnectors/internal/EnterpriseConnectorService.java:186-199` |
| Performance | **5** | Mỗi lần start/submit bài đều xoá **toàn bộ** 6 cache (mọi tenant); có N+1 ở learning-outcomes; nhiều nơi cắt danh sách mà không báo cho client. | `lms/internal/attempt/LmsAttemptUseCase.java:33-34,390`, `learningoutcomes/internal/LearningOutcomesService.java:732,743` |
| Completeness | **5** | 13 controller `@Profile("prototype")`, Excel import ném `NotImplementedException`, publisher thông báo LMS không có caller, connector chỉ có adapter "REFERENCE". | `controller/calendar/rest/CalendarRestController.java:278`, `service/notifications/LmsNotificationEventPublisher.java:54` |
| Test coverage của code rủi ro | **5** | Có test cho idempotency/thời hạn, nhưng không có test nào bắt lỗi IDOR khi nộp bài, lệch ngữ nghĩa override quyền, hay đường thất bại của projection. | `src/test/.../lms/LmsAttemptPolicyContractTest.java:72-162`, `src/test/.../service/lms/LmsRuntimeServiceAttemptRulesTest.java:104-237` |

## 2. Bảng phát hiện (findings)

Thứ tự: theo mức độ nghiêm trọng. Effort: S < 0.5 ngày, M 0.5–2 ngày, L > 2 ngày.

| ID | Phát hiện | Mức | Bằng chứng | Ví dụ cụ thể | Cách sửa nhỏ nhất | Effort |
|---|---|---|---|---|---|---|
| SQ-01 | Nộp/xem attempt **không kiểm tra người sở hữu**: chỉ kiểm tra quyền truy cập lớp của assignment; nếu assignment không có `academicClassId` thì bỏ qua luôn. | Critical | `lms/internal/attempt/LmsAttemptUseCase.java:224,349-350`; `controller/lms/LmsController.java:176-187` | Học sinh A cùng lớp (có `lms.assignment.submit`) gọi `POST /attempts/{attemptIdCủaB}/submit` với đáp án bất kỳ → bài của B bị nộp và chấm. Assignment không gắn lớp → mọi user trong tenant làm được. | Trong `mutable()` bắt buộc `attempt.studentId` ∈ {`access.userId()`, Student.id ứng với authUserId}, trừ khi có quyền giáo viên/review rõ ràng. Thêm test âm. | S |
| SQ-02 | Tính "quyền hiệu lực" được viết **3 lần với ngữ nghĩa khác nhau**. | High | `auth/internal/login/AuthLoginUseCase.java:316-356`; `platform/security/ErgPrincipalService.java:247-280,336-340`; `platform/security/OidcPrincipalBridgeFilter.java:124-145` | Override có `grantType="allow"`: ErgPrincipal coi là grant; Login bỏ qua (UI không thấy quyền nhưng API cho phép). `grantType="revoke"`: ErgPrincipal coi là **deny**, OIDC coi là **grant**. OIDC còn bỏ qua `UserRoleAssignment`. Login không lowercase tên quyền. | Gom về một `EffectivePermissionResolver` (dùng logic của `ErgPrincipalService`), 3 nơi gọi chung; viết test bảng cho grant/allow/deny/null/hết hạn. | M |
| SQ-03 | Tập trạng thái "attempt đã xong" bị chép **14 lần trong JPQL + 2 lần trong Java**, và lệch với `LmsStatePolicy`. | High | `lms/LmsStatePolicy.java:15-18`; `repository/lms/AssignmentRepository.java:216,226,248,271,450,553`; `repository/lms/AttemptRepository.java:254,262,373,396`; `repository/lms/StudentRepository.java:91,111,144,148`; `service/elearning/readmodel/StudentDashboardReadService.java:438` | Bài `needs_review` (chờ chấm tay) vẫn bị tính vào giới hạn số lần làm (`COMPLETED_ATTEMPT_STATES` có `needs_review`) nhưng dashboard/progress lại coi là **chưa nộp** → học sinh thấy "còn phải làm" mà bấm làm lại thì gặp "attempt limit reached". | Đưa danh sách vào tham số query (`:completedStates` lấy từ `LmsStatePolicy`) và quyết định rõ `needs_review` thuộc nhóm nào; thêm test hợp đồng. | M |
| SQ-04 | Chấm điểm dự phòng khi attempt không có `quizVersionId`: **điểm = số câu đã trả lời**, đạt nếu ≥ 50%. | High | `lms/internal/attempt/LmsAttemptUseCase.java:202-204`; `lms/internal/attempt/LmsAttemptUseCase.java:236,265-268` | Quiz legacy chưa publish package: học sinh điền bừa mọi câu → 100%, `passed=true`, ghi vào `student_progress`. | Nếu không có version thì chuyển trạng thái `needs_review` (hoặc chặn submit) thay vì tự cho điểm; log/metric cho đường này. | S |
| SQ-05 | Projection "best effort" chạy **bên trong transaction của submit**, gọi service có `@Transactional`; nếu lỗi thì transaction bị đánh dấu rollback-only → catch + warn không cứu được, cả bài nộp fail lúc commit (`UnexpectedRollbackException`). | High | `lms/internal/attempt/LmsAttemptUseCase.java:172,210,389`; `service/quiz/runtime/QuizAttemptGradingService.java:34,184` | Lỗi constraint trên `student_progress` → học sinh nhận 500 dù bài đã được chấm trong bộ nhớ; client retry với cùng idempotency key thì receipt cũng đã bị rollback. | Chuyển projection sang `@TransactionalEventListener(AFTER_COMMIT)` hoặc `REQUIRES_NEW`; bỏ catch nuốt lỗi. | S |
| SQ-06 | Quiz package lấy theo tenant, **không kiểm tra assignment/lớp**; với package không phải `server-authoritative` thì đáp án được gửi về client. | High | `lms/internal/resource/LmsResourceQueryUseCase.java:23-35`; `service/quiz/runtime/QuizRuntimePackageService.java:96-102`; `controller/lms/LmsController.java:84-87` | User có `lms.quiz.read` (học sinh cần quyền này để tải đề) gọi `GET /quizzes/{bấtKỳ}/package` → nhận `answerKey` của bài thi chưa giao. Nhánh legacy trả nguyên `quiz.getSlides()` (`List<Object>`), không lọc đáp án. | Kiểm tra học sinh được giao quiz (giống `start()` dòng 94-97) trước khi trả package; chỉ trả `answerKey` khi policy offline cho phép. | M |
| SQ-07 | Gọi R2 (HEAD/PUT) **trong `@Transactional` của cả class**; endpoint upload đọc toàn bộ body vào `byte[]` rồi mới kiểm tra kích thước. | Medium | `service/quiz/media/MediaService.java:22,85-87,97`; `service/quiz/media/MediaStorageConfig.java:23-29`; `controller/lcms/LcmsMediaController.java:37-46` | R2 chậm (đến 10s timeout) → giữ connection DB 10s mỗi lần commit; body 500 MB vẫn bị buffer hết trước khi bị từ chối. | Bỏ `@Transactional` ở cấp class, chỉ bao phần ghi DB; giới hạn kích thước ở tầng servlet (`max-request-size`) hoặc kiểm tra `Content-Length` trước khi đọc. | S |
| SQ-08 | Provider connector `sync()/preview()` chạy **trong transaction**. | Medium | `enterpriseconnectors/internal/EnterpriseConnectorService.java:157-171,186-199,211-234` | Hiện chỉ có `ReferenceEnterpriseConnectorAdapter` (in-memory) nên chưa thấy hậu quả; adapter HTTP thật sẽ giữ khoá/kết nối DB suốt thời gian gọi ra ngoài. | Tách ba bước: ghi run (tx) → gọi provider (ngoài tx) → ghi kết quả (tx). | M |
| SQ-09 | Mỗi lần start/submit bài đều xoá **toàn bộ** 6 cache đọc (mọi tenant, mọi lớp). | Medium | `lms/internal/attempt/LmsAttemptUseCase.java:33-34,115,131,210,390`; tương tự `service/learningcontent/LearningContentService.java:184` (`allEntries = true`) | 500 học sinh nộp bài cùng lúc → cache dashboard/report của mọi trường bị xoá liên tục, hit-rate gần 0 đúng lúc tải cao nhất. | Evict theo key (tenant + class/assignment) hoặc dùng cache-generation theo tenant (`platform/cache/RedisCacheGenerationService.java` đã có sẵn). | M |
| SQ-10 | N+1 khi lọc quyền xem từng dòng; lọc **sau khi** đã lấy top 100. | Medium | `learningoutcomes/internal/LearningOutcomesService.java:406-408,728-746,610-612` | 100 attainment → tối đa 100 truy vấn `paths.findByTenantIdAndId`; nếu 80 dòng không được xem thì client chỉ nhận 20 dòng mà không biết là còn dữ liệu. | Nạp trước các `LearningPath` bằng `findByTenantIdAndIdIn`, rồi lọc trong bộ nhớ; đưa điều kiện scope vào query và trả về có phân trang. | S |
| SQ-11 | Danh sách lớn bị **cắt ngầm**, response không có thông tin phân trang. | Medium | `service/crm/CrmWorkspaceService.java:85-111`; `service/lms/LmsAttemptService.java:90-94`; `controller/lms/LmsController.java:192` | CRM workspace lấy tối đa 500 trường rồi trả về **toàn bộ** deal/activity/handover của các trường đó (không giới hạn); roster `quizStudents` dừng ở 500 dòng mà không báo. | Thêm `page/size` cho các endpoint đó, trả `hasMore`/`total`. | M |
| SQ-12 | Hai kiểu authorization song song: URL rule trong `SecurityConfig` (58 rule) và `@PreAuthorize` (72 file). CRM chỉ dùng URL rule; GraphQL chỉ yêu cầu `authenticated()`. | Medium | `platform/security/SecurityConfig.java:131,164-169`; `controller/crm/CrmWorkspaceController.java:29-110` | Đổi đường dẫn CRM hoặc thêm method HTTP mới mà quên sửa `SecurityConfig` thì không có lớp bảo vệ thứ hai; `approveHandover` dùng chung quyền `crm.deal.manage` với người tạo → người tạo tự duyệt handover của mình (`CrmWorkspaceService.java:248-268`). | Thêm annotation method-level cho CRM (giống các module `lms/authorization/*`) và quyền riêng `crm.handover.approve`; chặn requester tự duyệt. | S |
| SQ-13 | Quy tắc scope đơn vị giáo dục bị lặp lại và lệch nhau giữa authorization và session bootstrap. | Medium | `platform/security/ErgPrincipalService.java:323` (`CENTER`, `SCHOOL`, phân biệt hoa thường) vs `service/sessions/SessionService.java:209-212` (`SCHOOL`,`CENTER`,`SYSTEM`,`ORGANIZATION`, đã uppercase) | Assignment scope `ORGANIZATION`/`school` → UI bootstrap hiện đơn vị đó, nhưng request sau đó bị 403. | Dùng chung `AccessScopeAssembler` cho cả hai nơi. | S |
| SQ-14 | Đăng nhập: rate limiter và failure enforcement là dependency **tuỳ chọn** (setter `required=false`), fail-open; nhánh fallback ghi lần đăng nhập thất bại trong cùng transaction rồi throw → bị rollback; `blockUserIfFailedLoginThresholdReached` tính xong nhưng không làm gì. | Medium | `auth/internal/login/AuthLoginUseCase.java:115-139,145,285-314` | Profile/test thiếu bean `LoginFailureEnforcementService` → không có khoá tài khoản, không có bản ghi `FAILED`, mà không có cảnh báo gì. | Inject qua constructor (bắt buộc), xoá nhánh fallback và method chết; fail lúc khởi động nếu thiếu. | S |
| SQ-15 | Kiến thức theo từng loại câu hỏi bị chia ra ít nhất 3 nơi phải sửa cùng lúc. | Medium | `service/quiz/publish/QuizCompileService.java` (`strip*Answers`, `extractAnswerKey`, `matchingPairs`...); `service/quiz/runtime/GradingEngine.java:237-270`; `src/test/.../service/quiz/publish/QuestionKindRegistryTest.java` | Thêm loại "hotspot đa điểm" mà quên `stripHotspotAnswers` → lộ đáp án; quên `answerAlias` → chấm 0. `answerAlias("pairs")` ánh xạ `matchingOrder` thành `item→item` (`GradingEngine.java:247-254`) — **cần xác nhận** đây có đúng là định dạng đáp án mong muốn không. | Mỗi loại câu hỏi là một strategy (`strip`, `extractKey`, `normalizeAnswer`, `grade`), được đăng ký trong `QuestionKindRegistry`. | L |
| SQ-16 | Publisher thông báo LMS (giao bài, sắp hết hạn, quá hạn, đã nộp, điểm thấp) **không có caller nào** ngoài test. | Medium | `service/notifications/LmsNotificationEventPublisher.java:54,109,164,220,285` (grep: chỉ được tham chiếu từ `src/test/.../LmsNotificationEventPublisherTest.java`) | Giáo viên giao bài → học sinh không nhận push; nếu sau này có nối vào thì mọi lỗi cũng chỉ được `log.warn` (dòng 89-92). | Phát qua Modulith event `AFTER_COMMIT` từ `LmsAssignmentCommandService`/`LmsAttemptUseCase`, hoặc xoá đi. | M |
| SQ-17 | Modulith chỉ kiểm tra các package có annotation; `service/`, `repository/`, `model/`, `controller/` nằm ngoài mọi module, nên module "internal" vẫn phụ thuộc trực tiếp vào code legacy mà không bị bắt. | Medium | `src/test/.../architecture/ModulithVerificationTest.java` (`detection-strategy=explicitly-annotated`, `modules.verify()`); `lms/internal/attempt/LmsAttemptUseCase.java:10-11`; `service/organization/EducationUnitService.java:4` (import `organization.internal`) | `LmsAttemptUseCase` dùng 8 service legacy `service.lms.*`/`service.quiz.runtime.*`; verify vẫn pass. Có ratchet baseline (`ArchitectureRatchetTest.java:61,74`) nên số lượng không tăng, nhưng cũng không giảm. | Đưa `service/lms` + `repository/lms` vào `lms/internal` theo từng lát; thêm rule ArchUnit "`*.internal..` không được phụ thuộc `..service..`" cho module đã migrate. | L |
| SQ-18 | Code chết và nhánh gây hiểu nhầm trong use case nóng. | Low | `lms/internal/attempt/LmsAttemptUseCase.java:402-410` (`checkVersion` không được gọi); `platform/sync/LmsSyncOperationService.java:111-115` (`abandon` chạy trong cùng tx sắp rollback, nên vô tác dụng; mẫu catch→abandon→rethrow lặp lại 9 lần); `service/lms/LmsRuntimeService.java:16-49` (bean deprecated) | Người đọc tưởng có kiểm tra optimistic-lock/hoàn tác receipt nhưng thực tế không có. | Xoá `checkVersion`; bỏ `abandon` (hoặc dùng `REQUIRES_NEW` có chủ đích); xoá `LmsRuntimeService` + constructor `@Deprecated`. | S |
| SQ-19 | `Map<String,Object>` làm domain model / payload API. | Low→Medium | 25 entity (`model/lms/Attempt.java:68,72,125`, `model/crm/CrmSchoolDeal.java:80,84`, `model/crm/CrmHandover.java:48`, `model/lms/Question.java:105-113`), 16 controller, 9 DTO (`common/dto/lms/LmsRuntimeDtos.java:44-61,109,259-262`) | `policySnapshot` đọc lại qua `toString()`+parse, lỗi parse bị nuốt → `deadlineAt=null` → rơi về `assignment.dueAt` (`LmsAttemptUseCase.java:362-377`): giáo viên đổi hạn nộp thì bài đang làm bị đổi luật. Doanh thu CRM tính từ `revenueLines` dạng map. 13 controller prototype trả `List<Map<String,Object>>` nên OpenAPI không có schema. | Dùng record + `@JdbcTypeCode(JSON)` cho `policySnapshot`, `revenueLines`, `checklist`; parse lỗi thì throw. | M |
| SQ-20 | Class lớn gộp nhiều việc không liên quan. | Low | Xem §3 | — | Tách theo việc (§3). | L |

### Mục ít rủi ro đã kiểm tra (không ghi thành finding)
- SMTP: `TransactionalAccountMailSender` (`@Primary`) chỉ ghi outbox; gửi thật ở `service/outbox/TransactionalOutboxWorker.java:62-80`, nằm ngoài transaction, có retry/dead letter. Lời gọi ở `service/auth/AccountVerificationService.java:110` do đó an toàn.
- FCM: `service/notifications/NotificationOutboxWorker.java:84-106` claim/record trong các transaction ngắn, `send()` nằm ngoài transaction.
- Advisory lock lịch: lỗi thì trả `false` → `ConflictException` (fail-closed, `calendar/internal/bulk/CalendarBulkCreateUseCase.java:150-152,286-297`); chỉ là thông báo lỗi gây hiểu nhầm.

## 3. Class lớn: gộp việc gì, tách thế nào

| Class (dòng) | Đang làm những việc | Tách theo việc |
|---|---|---|
| `learningoutcomes/internal/LearningOutcomesService.java` (846) | Learning path + node (139-271), competency (282-336), attainment (337-412), đánh giá hoàn thành (413-497), cấp/thu hồi/xác minh chứng chỉ (498-612), idempotency receipt + lock (613+), kiểm tra quyền xem (728-747). | `LearningPathCommands`, `CompetencyCatalog`, `AttainmentRecorder`, `CompletionEvaluator`, `CertificateService`; dùng chung `OutcomeVisibilityPolicy` + `OutcomeReceiptStore`. |
| `service/quiz/publish/QuizCompileService.java` (691) | Dựng runtime quiz, merge media/setting, **xoá đáp án** theo 6 loại câu hỏi, **trích answer key** theo 7 loại, tính điểm câu hỏi. | Một `QuestionKindCompiler` cho mỗi loại (strip + extract); service chính chỉ điều phối (xem SQ-15). |
| `service/learningcontent/LearningContentService.java` (664) | CRUD content item (100-314), CRUD asset (315-400), gắn asset (401), placement/reorder theo topic (434-490), tạo bài giảng Google Slides (186-246), idempotency. | `ContentItemCommands`, `ContentAssetCommands`, `TopicPlacementService`, `GoogleSlidesLectureFactory`. |
| `auth/internal/login/AuthLoginUseCase.java` (601) | Kiểm tra mật khẩu, trạng thái tài khoản, rate-limit, ghi lần thử, **tính quyền hiệu lực** (316-356), suy ra portal, thay thế session theo thiết bị/IP, sự kiện bảo mật, cấp token. | `CredentialVerifier`, `LoginAttemptRecorder`, `EffectivePermissionResolver` (dùng chung, SQ-02), `SessionIssuer` (gồm chính sách thay thế session). |
| `service/elearning/readmodel/StudentDashboardReadService.java` (514) | Kiểm tra quyền/scope, profile, class membership, assignment slice, đếm (có fallback legacy 216-230), điểm, thông báo, ánh xạ card. | Giữ một query service; chuyển `isPendingAction`/trạng thái sang `LmsStatePolicy`; xoá nhánh count legacy. |
| `service/lms/LmsAssignmentCommandService.java` (511) | CRUD assignment + recipients + resources **và** CRUD student group. | `AssignmentCommands` và `StudentGroupCommands`. |
| `service/sessions/SessionService.java` (493) | Session context, bootstrap, **lắp ráp scope đơn vị** (195-260, trùng với principal). | Chuyển phần scope vào `AccessScopeAssembler` (SQ-13). |
| `lms/internal/attempt/LmsAttemptUseCase.java` (418) | Không lớn nhất nhưng dày nhất: start/submit/idempotency/deadline/chấm/projection/cache/metrics, có dòng dài >1.000 ký tự (389). | `AttemptDeadlinePolicy` (315-348, 362-377), `AttemptProgressProjector` (389, chạy after-commit), `AttemptCacheInvalidator` (390). |

`repository/lms/AssignmentRepository.java` (557) chủ yếu là JPQL read model; vấn đề chính của nó là chép lại danh sách trạng thái (SQ-03), không phải trộn nhiều việc.

## 4. Phân loại 77 broad catch

| Nhóm | Số | Ví dụ |
|---|---|---|
| Rethrow/dịch lỗi có ngữ cảnh (ổn) | 40 | `config/persistence/TenantAwareJpaTransactionManager.java:63`, `service/quiz/publish/QuizPackageSigner.java:63,78,87,105`, `service/outbox/OutboxPayloadCipher.java:28,42,55`, 9 chỗ catch→`abandon`→rethrow (`LmsAttemptUseCase.java:135,216`, `QuizDraftService.java:92,126,155`, `LearningContentService.java:287,394`, `LmsAcademicRecordUseCase.java:130,190`, `LmsAssignmentCommandService.java:190`) |
| Chuyển thành kết quả/trạng thái thất bại (ổn) | 7 | `service/outbox/TransactionalOutboxWorker.java:77`, `service/reporting/ReportWorker.java:119`, `notifications/delivery/NotificationDeliveryAdapterRegistry.java:65`, `FirebaseNotificationDeliveryAdapter.java:51`, `platform/concurrency/ErgReadTaskExecutor.java:69`, rate limiter fail-closed `service/auth/AuthLoginRateLimiter.java:84`, `platform/graphql/GraphQlRateLimiter.java:87` (có cấu hình) |
| Log rồi chạy tiếp | 9 | `LmsAttemptUseCase.java:389` (**nguy hiểm**, SQ-05), `LmsNotificationEventPublisher.java:89,144,199,261,331` (mất thông báo, chỉ có `warn`), `platform/events/ModuleEventRecoveryJob.java:76,84`, `CalendarBulkCreateUseCase.java:293` |
| Nuốt lỗi im lặng (trả `null`/mặc định, không log) | 21 | Chấp nhận được: health/metrics (`RedisCacheHealthIndicator.java:22`, `RedisStateHealthIndicator.java:22`, `RedisMetricsBinder.java:49`, `PlatformHealthController.java:122,135`), cache degrade (`CacheFailurePolicy.java:33`, `RedisCacheGenerationService.java:29,42`, `ErgTieredCache.java:228`, `RedisCacheInvalidationSubscriber.java:32`), parse phản hồi FCM (`FirebaseHttpV1PushSender.java:147,165`), `GraphQlGuardFilter.java:201` (→400). **Đáng lo**: `LmsAttemptUseCase.java:367` (deadline snapshot hỏng → âm thầm dùng `dueAt` hiện tại), `common/dto/lms/LmsRuntimeDtos.java:187` và `lcms/LcmsQuizBundleResponse.java:137` (ngày sai → `null` trong response), `R2MediaStorageAdapter.java:72`/`LocalMediaStorageAdapter.java:98` (mọi lỗi storage, kể cả mất mạng, đều thành "chữ ký không hợp lệ", tức 400 thay vì 5xx), `TrustedClientIpResolver.java:57` (IP = null → rate limit theo IP bị vô hiệu), `ReportWorker.java:167` (timezone sai → UTC), `AttemptEventRecorder.java:90` |

Các chỗ nguy hiểm, theo thứ tự: `LmsAttemptUseCase.java:389` → `LmsNotificationEventPublisher.java:89..331` (nếu được nối vào) → `R2MediaStorageAdapter.java:72` → `TrustedClientIpResolver.java:57` → `LmsAttemptUseCase.java:367`.

## 5. Completeness

- `@Profile("prototype")`: **13 controller** (`controller/ai/AiContentController.java:17`, `catalog/CourseController`, `catalog/ElearningController`, `cms/PageMenuController`, `cms/PostController`, `crawler/CrawlerController`, `crawler/RssBlacklistController`, `documents/DocumentController`, `engagement/EngagementController`, `operations/AnalyticsOperationsController`, `platform/MigrationPlatformController`, `seo/SeoController`, `trending/TrendingBotController`) và **5 service** (`service/catalog/CatalogService`, `service/cms/CmsService`, `service/documents/DocumentService`, `service/support/ModuleWorkflowService`, `service/calendar/CalendarExcelImportService`). 4 service đầu là kho dữ liệu in-memory, được chặn bởi `platform/persistence/ProcessLocalStatePolicy.java:12-20` (Java mặc định `true`, yml mặc định `false` — `src/main/resources/application.yml:265-266`).
- `NotImplementedException`: 3 điểm ném ra — Excel import lịch (`controller/calendar/rest/CalendarRestController.java:278`, `controller/calendar/graphql/CalendarMutationGraphQlController.java:164`, `service/calendar/CalendarExcelImportService.java:86`) và `ProcessLocalStatePolicy.java:18`.
- Đang là stub hoặc chỉ in-memory: AI content (generate/refine/keys), CMS posts/pages/menus, catalog course/e-learning, documents, engagement/reviews/recruitment, SEO, crawler/RSS, trending bot, analytics ops, migration platform, Excel import lịch, enterprise connector (chỉ có `ReferenceEnterpriseConnectorAdapter` mô phỏng, `enterpriseconnectors/internal/ReferenceEnterpriseConnectorAdapter.java:21`), thông báo LMS (SQ-16).
- Rủi ro hợp đồng: `SecurityConfig.java:119-128` vẫn `permitAll` cho `/api/v1/posts/**`, `/api/v1/courses/**`, `/api/v1/recruitment/apply`... trong khi controller của chúng chỉ tồn tại ở profile prototype → ở production các URL này trả 404, nhưng rule public vẫn còn đó chờ controller tiếp theo dùng lại đường dẫn.

## 6. Test cho code rủi ro — chỗ còn thiếu

| Khu vực | Đã có | Thiếu test sẽ fail khi có regression |
|---|---|---|
| Quyền sở hữu attempt | Deadline, idempotency, limit (`LmsAttemptPolicyContractTest.java:143,162`; `LmsRuntimeServiceAttemptRulesTest.java:117,214`) | Học sinh khác nộp/đọc attempt của người khác; assignment không gắn lớp (SQ-01). |
| Override quyền | `UserServiceTest`, `UserControllerContractTest` (chỉ CRUD) | Bảng grant/allow/deny/null/hết hạn cho cả Login, ErgPrincipal, OIDC (SQ-02). |
| Trạng thái attempt | `LmsStatePolicyTest`, `AttemptRepositoryReadModelContractTest.java:99` (so khớp chuỗi SQL) | `needs_review` nhất quán giữa limit, dashboard, progress (SQ-03). |
| Chấm điểm | `GradingEngineTest` (16 test) | Chấm khi không có version (SQ-04); `matchingOrder`; lộ đáp án khi thêm loại câu hỏi mới (SQ-15). |
| Ghi dữ liệu + projection | Kiểm tra cache bị xoá sau khi nộp (`LmsRuntimeServiceAttemptRulesTest.java:237`) | Projection thất bại không được làm hỏng submit (SQ-05) — cần test tích hợp với transaction thật. |
| CRM | 3 file test tham chiếu `CrmWorkspaceService` | Requester tự duyệt handover; route CRM mới không có rule trong `SecurityConfig` (SQ-12). |
| Workers | Outbox/notification worker (3–4 file) | `ReportWorker` chỉ có 1 file; không có test nào kiểm chứng `ModuleEventRecoveryJob` thực sự resubmit được. |
| Media | `MediaService` (3 file) | Body vượt giới hạn bị chặn trước khi buffer; storage lỗi phải ra 5xx chứ không phải 400. |

## 7. Ba call chain (đầu → cuối)

### 7.1 LCMS media commit / upload
```
PUT/POST controller/lcms/LcmsMediaController.java:37 / :68
 → lcms/LcmsOperations.java:38-39          [pass-through, 1 dòng]
 → lcms/LcmsCommandPort.java:28-29         [interface]
 → lcms/internal/command/LcmsCommandUseCase.java:66-67   [pass-through, 1 dòng]
 → service/quiz/media/MediaService.java:85-87 (storeSignedUpload) [pass-through, 1 dòng]
   / MediaService.java:89-113 (commit)     [logic thật; @Transactional ở class, dòng 22]
 → service/quiz/media/R2MediaStorageAdapter.java:83-95 (headObject; gọi S3 trong tx)
 → repository/quiz/media/MediaAssetRepository (save)
```
Số hop chỉ chuyển tiếp: **3** với upload (Operations, UseCase, `MediaService.storeSignedUpload`), **2** với commit (+1 interface). Logic thật nằm ở 2 nơi: controller (kiểm tra kích thước) và MediaService (`MAX_IMAGE_BYTES` phải được lấy vòng qua `LcmsCommandUseCase.java:65`).

### 7.2 LMS nộp bài (attempt submit)
```
POST controller/lms/LmsController.java:176-187  (@LmsSubmit: chỉ kiểm tra permission)
 → lms/LmsOperations.java:54-56            [pass-through; field tên `legacy` thực ra là implementation @Primary mới]
 → lms/LmsRuntimePort.java:18              [interface; có 2 implementation: LmsRuntimeUseCase @Primary + LmsRuntimeService @Deprecated]
 → lms/internal/runtime/LmsRuntimeUseCase.java:40  [pass-through, 1 dòng]
 → lms/internal/attempt/LmsAttemptUseCase.java:172-220  [logic thật: idempotency, deadline, chấm, lưu, event, projection, cache]
     ├ service/lms/LmsAttemptService.java  [wrapper repository, 11 method dạng `return repository.x(..)`]
     ├ platform/sync/LmsSyncOperationService.java:47,98,111 (receipt, cùng tx)
     ├ service/quiz/runtime/QuizAttemptGradingService.java (grading + projectProgress, @Transactional)
     │   → service/quiz/runtime/GradingEngine.java
     └ service/quiz/runtime/AttemptEventRecorder.java
 → repository/lms/AttemptRepository
```
Số hop chỉ chuyển tiếp: **3** (LmsOperations, LmsRuntimeUseCase, LmsAttemptService) + 1 interface + 1 bean deprecated song song. Kiểm tra quyền tách làm 2 nơi (annotation ở controller, kiểm tra lớp ở use case) và đều không kiểm tra người sở hữu (SQ-01).

### 7.3 CRM duyệt handover (ghi)
```
POST controller/crm/CrmWorkspaceController.java:97-102  (không có annotation; quyền lấy từ SecurityConfig.java:168-169 'crm.deal.manage')
 → crm/CrmOperations.java (approveHandover)  [pass-through]
 → service/crm/CrmWorkspaceService.java:248-269  [logic thật; @Transactional]
     ├ requireSchool (276-283): tenant qua EducationUnit + scope đơn vị
     ├ checkVersion: optimistic lock thủ công
     └ ghi 3 aggregate: handover, profile (lead=active), deal (stage=active)
 → repository/crm/{CrmHandover,CrmSchoolProfile,CrmSchoolDeal}Repository
```
Số hop chỉ chuyển tiếp: **1** (CrmOperations). Chuỗi này gọn nhất, nhưng quy tắc phân quyền nằm ở một nơi khác hẳn (theo URL), và các bảng profile/deal/activity không có `tenantId` riêng: tenant chỉ được suy ra qua `EducationUnit` (`CrmWorkspaceService.java:272-283`). Vì vậy `profile(id)` (dòng 271-274) tra theo id mà **không** lọc tenant, và an toàn của cả chuỗi phụ thuộc hoàn toàn vào việc mọi đường đều gọi `requireSchool`.

## 8. Thứ tự ưu tiên đề xuất
1. SQ-01 (S): kiểm tra người sở hữu attempt + test âm — làm ngay.
2. SQ-05, SQ-04, SQ-14 (S mỗi mục): sửa transaction/fallback trong submit và login.
3. SQ-02, SQ-03, SQ-06 (M): gom quy tắc quyền và trạng thái về một nơi; khoá package theo assignment.
4. SQ-07, SQ-09, SQ-10, SQ-11 (S–M): hiệu năng và gọi ra ngoài khi đang trong transaction.
5. SQ-15, SQ-17 (L): refactor cấu trúc (strategy theo loại câu hỏi, đưa legacy vào module).
