# Ke hoach enterprise cho LMS/LCMS, GraphQL, security va kha nang chiu tai

Ngay cap nhat: 2026-06-09 19:26 ICT

Pham vi audit: `erg-spring` Spring Boot 4.0.6, Java 25, REST hien huu, security/RBAC/scope, DTO/response, LMS/LCMS/HocLieu data contracts, N+1, native image/GraalVM readiness va muc tieu van hanh doanh nghiep.

## 0.0 Checkpoint kiem tra lai ngay 2026-06-09 20:55 ICT

Trang thai hien tai: ke hoach da duoc cap nhat du cac nhom chinh ve GraphQL read-projection-only, DTO/response, security/RBAC/scope, projection, N+1, cache/rate-limit, Java 25/GraalVM native va muc tieu 5.000 concurrency. Tuy nhien source chua duoc xem la enterprise production-ready; moi dat muc first-slice/read-model proof cho mot so workspace nang.

Da xong o muc first implementation/proof:

- `POST /api/v1/graphql` endpoint chung va namespace `lms`, `lcms`, `crm`, `elearning`.
- Guard GraphQL nen: POST/JSON/operationName/query length/depth/alias/introspection/sensitive variables, execution depth/complexity/deadline va in-memory rate limit.
- `lms.learningResourceLibrary` co projection/read-model shape va query-count proof dau tien.
- `lms.classWorkspace` co class info, roster page, assignment summary, score summary, risk summary va query-count proof dau tien.
- `lms.teacherHomeworkWorkspace` co assignment cards, active/overdue/pending-review counters va query-count proof dau tien.
- `lms.assignmentProgressWorkspace` co first slice rieng: assignment detail, progress summary, attempts page, permission/scope gate va query-count proof dau tien.
- `elearning.studentDashboard` co assignment slice/counters/next actions, score summary va query-count proof dau tien.
- `lcms.accessManagementWorkspace` co user rows, role/scope options, effective permission batch projection va query-count proof dau tien.
- `lcms.questionBankWorkspace` co projection-backed first implementation va batch `QuestionUsageCountProjection`, nhung proof DB PostgreSQL van chua dong.
- Rule da chot: DTO la public API contract; Projection la DB/read-model contract; resolver/service khong expose projection truc tiep.

Con lai bat buoc lam tiep:

- P0: `questionBankWorkspace` can PostgreSQL/Testcontainers query-count proof, `EXPLAIN ANALYZE`, index review cho JSONB/lateral usage query va phuong an normalize neu thanh hot path.
- P0: `assignmentProgressWorkspace` can `RecipientProgressProjection`, `MissingLateStudentProjection`, latest/best attempt per recipient projection.
- P0: `classWorkspace` can attendance summary/projection va PostgreSQL EXPLAIN/index cho cac query join attempt/student/assignment.
- P0: `teacherHomeworkWorkspace` can class options, pending-review detail va overdue detail projections.
- P0: `studentDashboard` can announcement projection.
- P0: field-level permission matrix sau hon cho raw answers, private note, denied permissions, effective permissions va tung workspace GraphQL.
- P1: distributed Redis/gateway rate limit, distributed cache/stampede guard, cache key review theo tenant/user/scope/operation/filter/page.
- P1: slow-query/JDBC timeout smoke tren DB that; load/soak 100 -> 500 -> 1.000 -> 5.000 concurrency kem p95/p99, Hikari active/pending/timeout, DB slow query, GC/cache metrics.
- P2: AOT/native smoke cho GraphQL schema/resources, Jackson DTO records, projection records, validation, JPA va PostgreSQL driver.
- P2: thay cac placeholder/in-memory module bang persistence/service typed truoc khi go production enterprise.

## 0. Cap nhat moi nhat: projection proof va query-count proof chua dong enterprise gates

Sau muc 19 trong report, source/test da co them bang chung bounded repository interaction o muc service/read-model:

- `StudentDashboardReadServiceTest` verify `studentDashboard` chi goi mot slice projection, hai counter projection va mot score summary projection; khong phat sinh repository interaction sau khi map DTO.
- `StudentDashboardQueryCountIntegrationTest` verify `studentDashboard` tren dataset 12 assignments/attempts bang H2/Flyway/Hibernate statistics: `prepareStatementCount <= 6`, `entityLoadCount <= 2`. Day la query-count integration proof dau tien cho student dashboard; 2 entity load hien tai la profile `User` va `Student`, khong phai assignment/attempt graph.
- `LcmsWorkspaceReadServiceTest` verify `questionBankWorkspace` bo qua usage-count query khi question page rong.

Ket luan trang thai moi nhat:

- `studentDashboard` da co assignment slice/counters/next actions, `StudentDashboardScoreSummaryProjection` va SQL query-count integration proof dau tien bang Hibernate statistics; khong duoc ghi la con thieu score summary hay con thieu query-count proof dau tien nua. Gap con lai la announcement projection, PostgreSQL EXPLAIN/index evidence, load/soak proof va native smoke.
- `questionBankWorkspace` da co batch `QuestionUsageCountProjection`; khong duoc ghi la usage placeholder nua. Gap con lai la DB integration/query-count proof, `EXPLAIN`/index review cho native JSONB/lateral usage query, load/soak proof va native smoke.
- `classWorkspace` da co class info, roster page, assignment summary page, `ClassScoreSummaryProjection` va `ClassRiskSummaryProjection` first implementation co field-level gate; da co query-count integration proof dau tien bang Hibernate statistics tren dataset nhieu student/assignment/attempt. Gap con lai la attendance summary, EXPLAIN/index review, load/soak proof va native smoke.
- GraphQL guard/rate limiter startup da duoc harden: `GraphQlRateLimiter` dung constructor injection ro rang de Spring Boot 4 context khong fail vi default-constructor instantiation.

Remaining gates chua complete va van chan production GraphQL:

- Query-count integration tests bang Hibernate statistics hoac datasource proxy cho tung workspace nang, chay tren dataset nhieu row/nested row. Da co first proof cho `learningResourceLibrary`, `classWorkspace`, `studentDashboard`, `teacherHomeworkWorkspace` va `accessManagementWorkspace`; `questionBankWorkspace` van can PostgreSQL proof vi usage query dung JSONB/lateral.
- Slow-query/JDBC timeout smoke voi DB that de chung minh timeout thuc su abort query.
- `EXPLAIN ANALYZE`/index review cho projection/report query nang, dac biet question usage native JSONB/lateral.
- Load/soak 100 -> 500 -> 1.000 -> 5.000 concurrency kem p95/p99, Hikari active/pending/timeout, DB slow query, GC va cache metrics.
- Redis/gateway distributed rate limit, Redis/cache stampede guard va production multi-instance policy.
- Caffeine local cache da bat `recordStats()` va co `CacheConfigTest`, nen local cache hit/miss observability khong con la gap; Redis/distributed stampede guard van mo.
- AOT/native smoke cho GraphQL schema/resources, Jackson DTO, projection records, validation, JPA va PostgreSQL driver.
- Field-level permission matrix cho effective permissions, denied overrides, raw answers/private/sensitive fields va tung workspace GraphQL moi.

## 1. Ket luan ngan gon

Backend da co nen tot de di len enterprise:

- `ApiResponse<T>` thong nhat response REST.
- `GlobalExceptionHandler` da gom validation/API/unexpected error ve cung response shape.
- `PageRequestDto` da co default size va hard max 100.
- `SecurityConfig` da phan quyen theo route kha chi tiet.
- `GET /api/v1/sessions/current` da dung huong: tra ve user, permissions, current scope, manageable education units/classes.
- `HocLieuService` co read model/cache tot hon phan LMS admin.
- `spring.jpa.open-in-view=false`, Flyway migration, Hikari, cache Caffeine, native plugin da co trong `pom.xml`.
- Phase 0 security hardening da bat dau co code/test: trusted header auth default off, scope authoritative tu principal, empty scope deny-by-default, session/tenant spoof rejection, permission authorization dung tenant tu principal va portal theo path.
- DTO/response convention da ro hon: Java `requestId`, wire `request_id`.
- Projection/read-model skeleton da co cho LMS/HocLieu, gom assignment progress aggregate projection de giam N+1/broad read.
- Ops foundation da co actuator/prometheus, Resilience4j dependency, virtual-thread flag, Hikari tunables va log masking.
- GraphQL platform shell da bat dau co: `spring-boot-starter-graphql`, single endpoint `/api/v1/graphql`, schema root namespace `lms/lcms/crm/elearning`, GraphiQL off mac dinh, operationName guard, introspection off mac dinh va explicit authenticated route.
- GraphQL rate limiter da co in-memory first implementation va da qua context smoke sau khi bo sung constructor injection ro rang.
- Read-model da tien them: LMS class workspace projection path va users/access-management pageable projection path da co test contract.

Cap nhat trang thai source hien tai de tranh nham "da trien khai enterprise" voi "da co first-slice/shell":

- `lms.learningResourceLibrary(input)` da co read-model shape chinh: taxonomy, resource cards, counts, progress va recent opened; da co `HocLieuLibraryQueryCountIntegrationTest` chung minh query count bounded o muc H2/Flyway/Hibernate statistics (`prepareStatementCount <= 9`, `entityLoadCount == 0`). Van can PostgreSQL EXPLAIN/index, cache stampede guard, load/soak va native gates.
- `lms.classWorkspace(input)` da co class info, roster page, assignment summary page, nullable `scoreSummary` bang `ClassScoreSummaryProjection` va nullable `riskSummary` bang `ClassRiskSummaryProjection`. `scoreSummary` chi duoc include khi user co `lms.grade.read`, `lms.manage` hoac wildcard; `riskSummary` chi duoc include khi user co `lms.progress.read`, `lms.grade.read`, `lms.manage` hoac wildcard. Da co `LmsClassWorkspaceQueryCountIntegrationTest` de chung minh query count bounded o muc H2/Flyway/Hibernate statistics cho slice nay. Van thieu attendance summary, EXPLAIN/index proof va load/native gates.
- `lms.teacherHomeworkWorkspace(input)` da co first-slice read-only dua tren teacherId tu principal, `AssignmentCardProjection` va `TeacherHomeworkSummaryProjection` cho active/overdue/pending-review counters; van chua co class options va pending-review/overdue detail lists day du.
- `elearning.studentDashboard(input)` da co first-slice read-only theo student self-scope, `StudentDashboardAssignmentProjection` cho assignment slice/counters/next actions, `StudentDashboardScoreSummaryProjection` cho score summary va `StudentDashboardQueryCountIntegrationTest` chung minh query count bounded o muc H2/Flyway/Hibernate statistics. Van thieu announcement projection, PostgreSQL EXPLAIN/index proof va load/native gates.
- `lcms.questionBankWorkspace(input)` da co projection-backed first implementation: question page, subject/level/topic options, quiz options va batch `QuestionUsageCountProjection`; van chua co DB integration/query-count proof va EXPLAIN cho native JSONB usage query.
- `lcms.accessManagementWorkspace(input)` da co projection-backed first implementation: user rows, role options, scope options, effective-permission batch khi du permission va `AccessManagementWorkspaceQueryCountIntegrationTest` chung minh query count bounded o muc H2/Flyway/Hibernate statistics. Van can field-level matrix day du hon, PostgreSQL EXPLAIN/index proof va load/native gates.
- Chua co query-count integration proof cho tat ca workspace, load/soak evidence, Redis/distributed rate limit/cache, slow-query timeout smoke hay AOT/native smoke.

Nhung chua nen dua GraphQL vao production neu chua hoan thanh cac gate con lai:

1. Query-count integration tests moi co first proof cho `learningResourceLibrary`, `classWorkspace`, `studentDashboard`, `teacherHomeworkWorkspace`, `assignmentProgressWorkspace` va `accessManagementWorkspace`; `questionBankWorkspace` van can PostgreSQL proof vi H2 khong ho tro JSONB/lateral usage query. `assignmentProgressWorkspace` van can recipient/missing-late/latest-best projections va production gates.
2. Field-level permission va scope tests phai duoc viet rieng cho tung workspace GraphQL moi; route-level `/api/v1/graphql` khong du.
3. JDBC/JPA timeout da co config, nhung chua co integration/slow-query smoke de chung minh driver/database abort query that.
4. Read-model services cho cac workspace nang da co first-slice an toan; `lcms.questionBankWorkspace`, `lcms.accessManagementWorkspace` va `elearning.studentDashboard` da bat dau co projection-backed data that hon shell ban dau. `studentDashboard` da co first query-count integration proof, nhung cac workspace nay van thieu SQL/EXPLAIN evidence, load proof va toan bo counters/options/report data hoan chinh.
5. `LmsRuntimeService` van con broad read/in-memory filter o mot so path ngoai `assignmentProgress`.
6. Nhieu endpoint LMS admin/workspace trong `LmsController` van la placeholder/accepted/status available. Khong duoc xem la contract production.
7. Chua co load tests/Redis distributed cache/distributed rate limit/read replica strategy/native image smoke.

Huong chot:

- REST van la nen cho auth/current/scope/command/file/import/export/quiz runtime.
- GraphQL chi lam read-projection cho man hinh nang.
- DTO la API contract, nhung Projection la bat buoc cho read-model/man hinh nang de khong load thua entity va khong day Hibernate vao N+1.
- Chi mo `POST /api/v1/graphql` cho production sau khi trust boundary/scope authoritative tiep tuc duoc giu, pagination hard limit, projection/batch repository, GraphQL guard, persistence timeout smoke va security/query-count tests deu pass.

## 2. Chuan kien truc muc tieu

### 2.1 REST giu vai tro nen

Bat buoc giu REST cho:

- Auth/login/refresh/logout/session/current.
- Current scope nhe: truong, trung tam, lop giao vien duoc access.
- Commands co audit/idempotency: tao/sua assignment, diem, diem danh, phan quyen, import hoc sinh, upload/download/launch asset, quiz attempt sync/submit.
- File, import/export, async job, webhook.
- Quiz runtime vi can idempotency, offline sync, submit flow va security rieng.

Endpoint current duy nhat:

```http
GET /api/v1/sessions/current
```

Can giu response gom:

- user profile summary.
- roles, granted permissions, denied permissions.
- current scope.
- manageable education units.
- manageable classes.
- tenant/session/portal context neu FE can.

### 2.2 GraphQL chi la read-projection

Endpoint duy nhat:

```http
POST /api/v1/graphql
```

Root schema:

```graphql
type Query {
  lms: LmsQuery!
  lcms: LcmsQuery!
  crm: CrmQuery!
  elearning: ElearningQuery!
}
```

Khong nen tao:

```http
/api/v1/lms/graphql
/api/v1/lcms/graphql
/api/v1/crm/graphql
```

Neu gateway/product policy bat buoc URL rieng, gateway rewrite ve `/api/v1/graphql` va set `X-Portal`.

Resolver khong duplicate business logic. Resolver chi:

- doc GraphQL context.
- goi read-model service.
- enforce permission/scope o service layer.
- khong goi truc tiep method runtime dang co broad read/N+1.

## 3. Hien trang source da kiem tra

### 3.1 Security

File quan trong:

- `platform/security/ErgAuthenticationFilter.java`
- `platform/security/UserAccessContext.java`
- `platform/security/SecurityConfig.java`
- `platform/security/ErgPolicyService.java`
- `platform/security/ErgPrincipalService.java`
- `service/sessions/SessionService.java`

Nhan xet:

- `SecurityConfig` da co route-level permission kha day du, nhung GraphQL se chi co mot route nen phai them field-level permission trong resolver/service.
- `ErgPolicyService` co deny override, wildcard, alias/role expansion. Huong nay tot, can chuyen permission registry tu hard-code sang seed/config ro rang hon khi lon.
- `ErgPrincipalService` da doc Bearer token -> session -> user/role/permission, dung huong.
- `SessionService.currentBootstrap()` da tinh manageable units/classes tu access scope/teacher classes, dung huong.
- Risk lon nhat la fallback header principal trong `ErgAuthenticationFilter` va header-managed scope trong `UserAccessContext`.

### 3.2 DTO/response/request

File quan trong:

- `common/response/ApiResponse.java`
- `common/response/PageResponse.java`
- `common/dto/PageRequestDto.java`
- `common/dto/lms/LmsRuntimeDtos.java`
- `common/request/...`
- `exception/GlobalExceptionHandler.java`

Nhan xet:

- Record DTO la dung huong cho Java 25/native image.
- `ApiResponse<T>` da tot cho REST. Quy uoc chot: Java component/accessor la `requestId`; wire field giu `request_id` bang `@JsonProperty("request_id")`.
- `Map<String, Object>` dang dung kha nhieu o controller/service placeholder. Enterprise nen chi cho phep o vung metadata/plugin/dynamic payload co schema ro, con contract public phai la record DTO typed.
- `Object answer` trong quiz runtime co the chap nhan vi answer shape dong, nhung can validate JSON size, field whitelist, depth, va khong luu raw secret/client token.
- `PageRequestDto` la contract REST zero-based: missing/negative `page` -> 0, missing/non-positive `size` -> 20, max `size` -> 100. GraphQL nen default 20-50 va hard max 200 cho mot so workspace co ly do ro.

### 3.2.1 DTO và Projection phải đi cùng nhau

Kết luận kiến trúc, cập nhật sau khi đọc thêm phân tích về Java 21/25, Spring Boot hiện đại, Virtual Threads, N+1 và 5.000 concurrency:

- DTO không thay thế Projection.
- Projection cũng không thay thế DTO.
- DTO là contract public để FE/GraphQL/REST đọc được, ổn định và version được.
- Projection là contract đọc DB/read-model để chỉ select đúng cột/summary/aggregate cần thiết.
- Mapper/read-model service chuyển Projection sang DTO.
- Với màn hình nặng, dùng DTO mà vẫn load full entity thì chưa đạt chuẩn enterprise.
- Với GraphQL, Projection là lớp chống N+1 chính; DataLoader chỉ là lớp batch bổ sung cho nested field còn lại.

Áp dụng:

| Use case | Nen dung |
|---|---|
| Command create/update/delete | Request DTO + Response DTO |
| Detail nho theo id | Entity -> DTO, neu relation it va co guard |
| List paginated don gian | Projection hoac lightweight Entity -> DTO |
| Dashboard/workspace/report | Projection + aggregate query + DTO |
| GraphQL resolver | Projection + batch read/DataLoader + DTO |
| Count/summary/metric | Projection bat buoc |

Contract boundary:

- DTO owns public field names, compatibility, validation shape, and client-generation stability.
- Projection owns query shape/read performance and is never the public REST/GraphQL response type.
- Read-model service or mapper converts Projection -> DTO; controllers/resolvers return DTO only.

Projection uu tien:

- Record projection cho query ro rang, native-image friendly.
- Interface projection cho Spring Data query don gian.
- JPQL constructor expression khi can custom join/aggregate.
- Native SQL projection khi query report/aggregate phuc tap va can `EXPLAIN ANALYZE`.

Vi du:

```java
public record AssignmentSummaryProjection(
        String assignmentId,
        String title,
        String status,
        Long submittedCount,
        Long inProgressCount,
        Double averageScore
) {
}
```

Sau do map sang public DTO:

```java
public record AssignmentSummaryResponse(
        String id,
        String title,
        String status,
        long submittedCount,
        long inProgressCount,
        double averageScore
) {
}
```

Rule:

- Khong expose Projection truc tiep ra controller neu no gan voi DB/internal naming.
- Projection dat o repository/readmodel package; DTO dat o `common/response/<domain>`.
- Projection khong chua sensitive field neu query khong can.
- Projection cua GraphQL phai duoc thiet ke theo field group/read model, khong theo tung nested resolver.

Quyết định áp dụng Projection cho `erg-spring`:

- Áp dụng được và nên áp dụng ngay cho LMS/LCMS/HocLieu read-heavy.
- Không dùng Projection cho command write như tạo assignment, submit quiz, import học sinh, upload file.
- Không expose Projection trực tiếp ra REST/GraphQL public response.
- Không tạo projection theo từng field GraphQL nhỏ lẻ vì sẽ kéo hệ thống về N+1; tạo projection theo workspace/card/summary.
- Không dùng `FetchType.EAGER` để chữa N+1. Nếu cần relation nhỏ cho detail read thì dùng `@EntityGraph`; nếu là list/workspace thì dùng Projection + batch query.
- `hibernate.default_batch_fetch_size=50-100` chỉ là safety net cho lazy relation còn sót lại, không phải phương án chính.

Các Projection skeleton đã bắt đầu có trong source và phải được phát triển tiếp:

- LMS: `AssignmentCardProjection`, `AttemptSummaryProjection`, `ClassroomSummaryProjection`, `ClassWorkspaceProjection`, `QuizSummaryProjection`, `StudentRosterProjection`.
- HocLieu: `HocLieuResourceCardProjection`, `ResourceSummaryProjection`.
- Organization/access: `UserAccessScopeProjection`.

Các Projection còn thiếu cho enterprise rollout:

- `AssignmentProgressSummaryProjection`: tổng submitted/inProgress/needsReview/missing/late/averageScore theo assignment.
- `AttemptScoreProjection`: latest/best score theo assignmentId + studentId, không load raw answers.
- `AttendanceSessionSummaryProjection`: trạng thái điểm danh theo buổi/lớp/tuần.
- `ScoreSheetColumnProjection`: cột bài tập/quiz/điểm trong bảng điểm.
- `ClassRiskSummaryProjection`: risk summary cấp lớp hiện đã có first implementation cho missing overdue/low score/inactive student counts.
- `StudentRiskProjection`: risk flags theo từng student/class nếu FE cần drill-down chi tiết sau này.
- `QuestionUsageProjection`: usage count cho question/quiz LCMS. Source hiện đã có first implementation bằng `QuestionUsageCountProjection`/`QuizRepository.countQuestionUsageByQuestionIds`; cần DB integration test, EXPLAIN và index review vì query đang dùng native JSONB/lateral.
- `StudentScoreSummaryProjection`: source hiện đã có first implementation bằng `StudentDashboardScoreSummaryProjection`/`AttemptRepository.summarizeStudentDashboardScores`; cần query-count integration proof và kiểm tra index theo `tenantId + studentId + status`.
- `StudentNextActionProjection`: nếu next actions cần vượt khỏi assignment slice hiện tại.
- `AnnouncementCardProjection`: announcement/news cho student dashboard.
- `AssignmentProgressSummaryProjection`: tổng submitted/inProgress/needsReview/missing/late/averageScore theo assignment vẫn cần hoàn thiện cho homework/progress workspace.

Các projection đã bắt đầu có thêm sau lượt triển khai song song:

- `QuestionBankQuestionProjection`, `QuestionBankOptionProjection`, `QuestionBankQuizOptionProjection`.
- `UserAccessRowProjection`, `UserEffectivePermissionProjection`, `RoleOptionProjection`, `ScopeOptionProjection`.
- `StudentDashboardAssignmentProjection`.

Checklist bắt buộc khi thêm projection:

- Query có `tenantId` trong điều kiện lọc.
- Query có scope/class/school filter ở DB, không filter scope lớn trong Java.
- Query có `Pageable` hoặc hard limit.
- Query không select full entity nếu response chỉ cần row/card/summary.
- Query không giữ DB connection lâu vì tính count/summary trong Java.
- Projection không chứa password/token/raw credential/raw answer/private note nếu field đó không cần.
- Aggregate query trả count/avg/max/min bằng projection, không đếm trong Java sau khi load entity.
- Có index composite tương ứng với `WHERE + ORDER BY`.
- Có test repository hoặc read-model chứng minh paging, security scope và query shape.
- Với GraphQL workspace, có query-count test để query count không tăng theo số nested row.

### 3.2.2 Kết luận bổ sung: Projection có áp dụng được không?

Có. Với `erg-spring`, Projection không chỉ áp dụng được mà phải trở thành chuẩn bắt buộc cho read-heavy LMS/LCMS/HocLieu/Elearning. DTO là đủ cho contract public, nhưng không đủ để bảo đảm hiệu năng DB/JPA. Nếu controller/resolver trả DTO nhưng service vẫn load full entity, lazy relation hoặc collection lớn rồi mới map sang DTO thì hệ thống vẫn có nguy cơ N+1, tốn heap, tốn connection time và không đạt mục tiêu 5.000 concurrency.

Quyết định enterprise:

- DTO là lớp hợp đồng bên ngoài: REST response, GraphQL type, validation shape, backward compatibility.
- Projection là lớp hợp đồng đọc dữ liệu bên trong: cột nào được select, aggregate nào tính ở DB, query nào có paging/scope/index.
- Read-model service là nơi ghép nhiều projection nhỏ thành DTO workspace lớn.
- Repository projection phải lọc tenant/school/class/user scope ngay trong SQL/JPQL, không đọc rộng rồi filter bằng Java.
- GraphQL resolver không được tự gọi repository theo từng field; resolver gọi read-model service đã batch/projection sẵn.
- DataLoader được dùng cho nested field còn lại, nhưng không thay thế projection base query.
- Virtual Threads giúp request thread không cạn nhanh, nhưng không chữa query sai shape. Nếu projection chưa bounded, 5.000 concurrent chỉ làm DB pool queue nhanh hơn.
- Hikari pool không được tăng theo số user concurrent; projection/paging/cache/query timeout mới là cách giảm thời gian giữ connection.
- Redis/cache chỉ được bật sau khi cache key đã bao gồm tenant/scope/user cần thiết và có stampede guard; cache không thay thế permission check.

Bảng quyết định:

| Tình huống | Cách làm chuẩn |
|---|---|
| Command/write: create/update/delete/submit/import/upload | Request DTO + Response DTO + service transaction; không dùng Projection làm contract |
| Detail nhỏ theo id, relation ít và cố định | Entity hoặc `@EntityGraph` có chủ đích -> DTO |
| List paginated đơn giản | Interface/record Projection -> DTO |
| Dashboard/workspace/report/GraphQL read | Page Projection + aggregate Projection + batch query -> Workspace DTO |
| Count/summary/metric | Aggregate Projection bắt buộc, không load collection rồi count trong Java |
| GraphQL nested field có dữ liệu phụ | Base Projection trước, sau đó DataLoader/batch `IN (...)` nếu thật sự cần |

Quy tắc chống N+1:

- Không dùng `FetchType.EAGER` để "chữa" N+1; cách này dễ tạo object graph lớn và phá query plan.
- Dùng `@EntityGraph` cho detail read nhỏ, ví dụ user detail kèm role/permission, không dùng cho workspace lớn.
- Dùng `hibernate.default_batch_fetch_size=50-100` như safety net cho lazy relation còn sót lại, không xem là phương án chính.
- Với mỗi workspace phải có query-count test: số query không được tăng tuyến tính theo số row/nested row.
- Với GraphQL phải test một query có nhiều nested selection để bảo đảm không phát sinh repository call trong loop.

Chuẩn thiết kế Projection:

- `*RowProjection`: row trong table/list.
- `*CardProjection`: card nhẹ cho dashboard.
- `*SummaryProjection`: count/avg/max/min/status.
- `*OptionProjection`: picker/dropdown.
- `*DetailProjection`: detail có giới hạn cột và relation.
- `*WorkspaceProjection`: chỉ dùng khi một query trả đúng shape workspace, không chứa dữ liệu nhạy cảm.

Các màn hình bắt buộc dùng Projection:

- LMS homework list/progress/class workspace/score sheet/attendance/report/resource library.
- LCMS overview/question bank/access management/resource studio.
- Elearning student dashboard/assignment summary/score summary/next actions.
- Any screen có page size, nested cards, summary counters, progress counts hoặc cần join nhiều bảng.

Acceptance criteria cho Projection:

- Query có `tenantId` và scope filter.
- Query có paging/hard limit.
- Query không select raw answer/password/token/credential/private note nếu không cần.
- Query có index tương ứng với `WHERE + ORDER BY`.
- Service map Projection -> DTO, không expose Projection ra public API.
- Test repository/read-model chứng minh paging, scope deny và query-count bounded.
- Với native image/GraalVM, ưu tiên record projection/record DTO vì ít reflection và dễ maintain hơn dynamic `Map<String,Object>`.

### 3.2.3 Bổ sung từ phân tích Java 25/Spring Boot hiện đại: Projection là lớp giảm tải DB, không phải chỉ là kiểu trả dữ liệu

Sau khi đọc thêm phân tích về Java 21/25, Spring Boot hiện đại, Virtual Threads, N+1 và mục tiêu 5.000 concurrent requests, quyết định kiến trúc cần chốt rõ hơn:

- Virtual Threads giúp ứng dụng giữ được mô hình code synchronous/blocking dễ đọc mà không bị nghẽn ở số lượng OS thread.
- Virtual Threads không làm database nhanh hơn, không tăng số connection an toàn của HikariCP, không tự chữa N+1 và không giảm số cột/entity Hibernate phải materialize.
- Vì vậy bottleneck ở 5.000 concurrency sẽ chuyển từ web thread sang DB connection pool, query count, lock, cache miss, heap allocation và downstream service.
- Projection là cơ chế chính để giảm thời gian giữ DB connection: query ít cột hơn, aggregate ngay trong DB, paging rõ ràng và không dựng object graph lớn.
- DTO chỉ bảo vệ contract public; Projection mới bảo vệ read path nội bộ khỏi broad entity load, lazy traversal và memory churn.

Chuẩn enterprise cho read-heavy endpoint:

```text
GraphQL/REST Controller
  -> permission/scope guard
  -> Read-model service
  -> Repository projection query / aggregate projection query
  -> Mapper
  -> Public DTO
```

Không dùng flow này cho command/write:

```text
Command Controller
  -> Request DTO validation
  -> Domain service transaction
  -> Entity write/audit/idempotency
  -> Response DTO
```

Lý do Projection bắt buộc ở 5.000 concurrency:

| Rủi ro chịu tải | Projection xử lý thế nào |
|---|---|
| DB pool exhaustion | Giảm thời gian giữ connection bằng query ngắn, select đúng cột, paging cứng |
| N+1 query | Dùng page projection + batch/aggregate projection thay vì lazy relation trong loop |
| Heap/GC pressure | Không materialize full entity graph, giảm object allocation |
| Cache stampede | Cache theo read-model/projection DTO nhỏ; dễ lock/sync cache theo key |
| GraphQL nested field explosion | Base projection theo workspace, không resolver từng field gọi repository |
| Read replica routing | `@Transactional(readOnly = true)` + projection query dễ route sang replica hơn command flow |
| GraalVM native image | Record DTO/record projection rõ shape, ít dynamic reflection hơn `Map<String,Object>` |

Quy tắc áp dụng từ attachment:

- Bật `spring.threads.virtual.enabled=true` là nền tốt, nhưng phải đi cùng Hikari max pool có kiểm soát; không set pool theo số request concurrent.
- Với read-heavy GraphQL, mỗi query workspace phải có page size mặc định, hard max và query-count test.
- `hibernate.default_batch_fetch_size=50-100` chỉ là lưới an toàn cho lazy relation còn sót, không thay thế projection.
- `@EntityGraph` chỉ dùng cho detail nhỏ có relation cố định; không dùng để kéo workspace/dashboard/report lớn.
- Cache Redis/Caffeine chỉ cache kết quả read-model đã được scope/permission đúng; cache key phải gồm tenant, user/scope, operation/filter/page.
- Với cache dữ liệu nóng, cần stampede guard: `sync=true`, distributed lock hoặc single-flight theo key nếu dùng Redis.
- Downstream/file/import/export/report phải có timeout, circuit breaker và bulkhead; không để GraphQL giữ request dài.
- Query report/export không chạy trong GraphQL; chỉ tạo async REST job.

Checklist performance bắt buộc trước khi mở production cho một màn hình nặng:

- Có projection query cho base page/card/list.
- Có aggregate projection cho counter/summary.
- Không có repository call trong loop theo từng row/nested field.
- Có paging/hard limit ở input và ở repository.
- Có tenant + school/class/user scope trong `WHERE`.
- Có index khớp `tenant_id + scope columns + status + updated_at/order column`.
- Có query-count test chứng minh số query không tăng tuyến tính theo số row.
- Có test security chứng minh user ngoài scope không nhìn thấy row/count.
- Có metric p95/p99 theo operationName/API path.
- Có load test tăng dần 100 -> 500 -> 1.000 -> 5.000 concurrency trên cụm thật hoặc môi trường tương đương.

### 3.2.4 Chuẩn đặt tên Projection và mapping cho các màn hình LMS/LCMS

Projection phải được thiết kế theo read purpose, không theo entity và không theo từng field GraphQL nhỏ lẻ:

- `*RowProjection`: dòng table/list có paging.
- `*CardProjection`: card nhẹ cho dashboard/library.
- `*SummaryProjection`: count/avg/max/min/status counters.
- `*OptionProjection`: picker/dropdown/search option.
- `*DetailProjection`: detail nhỏ, giới hạn cột rõ.
- `*WorkspaceProjection`: chỉ dùng khi một query trả đúng phần khung của workspace.

Mapping chuẩn:

```text
repository/<domain>/projection/*Projection
service/<domain>/readmodel/*ReadService
common/response/<domain>/*Response
platform/graphql/*Controller
```

Không để controller/resolver trả projection trực tiếp. Projection là hợp đồng nội bộ với database/read-model, DTO/Response mới là hợp đồng public với FE/GraphQL/REST.

Projection cần bổ sung theo từng query GraphQL đã chốt:

| Query | Projection cần có |
|---|---|
| `lms.learningResourceLibrary` | `HocLieuResourceCardProjection`, `HocLieuTaxonomyNodeProjection`, `HocLieuTaxonomyCountProjection`, `HocLieuResourceProgressProjection`, `HocLieuRecentResourceProjection` |
| `lms.classWorkspace` | `ClassWorkspaceProjection`, `StudentRosterProjection`, `ClassAssignmentSummaryProjection`, `ClassScoreSummaryProjection`, `ClassRiskSummaryProjection`, `ClassAttendanceSummaryProjection`, `StudentRiskProjection` |
| `lms.teacherHomeworkWorkspace` | `AssignmentCardProjection`, `AssignmentProgressSummaryProjection`, `PendingReviewProjection`, `OverdueStudentProjection`, `ClassroomSummaryProjection` |
| `lms.assignmentProgressWorkspace` | First slice da co `AssignmentDetailProjection`, `AssignmentProgressProjection`, `AttemptSummaryProjection`; tiep theo can `RecipientProgressProjection`, `AttemptScoreProjection`, `MissingLateStudentProjection` |
| `lcms.questionBankWorkspace` | `QuestionRowProjection`, `QuizOptionProjection`, `QuestionUsageProjection`, `TaxonomyOptionProjection` |
| `lcms.accessManagementWorkspace` | `UserAccessRowProjection`, `UserRoleNameProjection`, `UserAccessScopeProjection`, `EffectivePermissionProjection` |
| `elearning.studentDashboard` | `StudentDashboardProfileProjection`, `StudentAssignmentCardProjection`, `StudentScoreSummaryProjection`, `StudentNextActionProjection` |

Ưu tiên triển khai Projection theo thứ tự:

1. Hoàn thiện `lms.learningResourceLibrary` vì source đã có read-model và projection đầu tiên.
2. `lms.classWorkspace` vì đây là màn hình dễ phát sinh N+1 nhất: class -> students -> assignments -> attempts -> attendance.
3. `lms.teacherHomeworkWorkspace` vì cần nhiều aggregate counters.
4. `elearning.studentDashboard` vì user scope là cá nhân, dễ khóa bảo mật trước khi mở rộng.
5. `lcms.questionBankWorkspace` vì question/quiz usage dễ nặng và cần index tốt.
6. `lcms.accessManagementWorkspace` vì dữ liệu nhạy cảm, cần field-level permission rất rõ.

### 3.2.5 Chiến lược chịu tải khi dùng Projection với Virtual Threads

Mục tiêu không phải để một instance đơn lẻ mở 5.000 DB connections. Mục tiêu đúng là nhận được nhiều concurrent requests, nhưng giới hạn tài nguyên đắt tiền bằng bounded pool, timeout, cache và backpressure.

Khuyến nghị cấu hình/triển khai:

- Virtual threads: bật cho request handling để giảm chi phí thread và giữ code blocking dễ maintain.
- HikariCP: giữ pool theo năng lực DB, theo dõi `active`, `idle`, `pending`, `timeout`; không tăng pool theo cảm tính.
- Read-only transaction: mọi read-model service dùng `@Transactional(readOnly = true)` để chuẩn bị cho read replica routing.
- Projection query: mọi GraphQL read-heavy query phải có `Pageable` hoặc hard limit.
- Cache: cache taxonomy/options/static config và dashboard summary ngắn hạn; cache key luôn có tenant/scope/user/filter.
- Stampede guard: dùng single-flight/sync cache cho key nóng như taxonomy tree, resource library summary, class workspace summary.
- Resilience4j: bulkhead/circuit breaker cho service ngoài, file storage, export engine, AI/import worker nếu có.
- GC/JVM: khi chạy JVM mode, đánh giá ZGC/Shenandoah cho latency; khi native image, đo lại heap/RSS/startup/p95 vì profile sẽ khác.
- Observability: bắt buộc metric theo endpoint/operationName/tenant bucket, DB query time, Hikari pending, cache hit ratio, error code.

Gate trước production:

- p95 `sessions/current` < 300ms.
- p95 cached resource/dashboard query < 800ms.
- p95 class workspace < 1s với paging.
- Hikari pending không tăng kéo dài trong test 1.000 concurrency.
- Không có query nào vượt hard max page size.
- Không có GraphQL query chạy report/export đồng bộ.
- Không có raw password/token/refresh token/raw credential/raw answer trong projection/DTO.

### 3.3 LMS runtime/read

File quan trong:

- `service/lms/LmsRuntimeService.java`
- `controller/lms/LmsController.java`
- `repository/lms/AssignmentRepository.java`
- `repository/lms/AttemptRepository.java`
- `repository/lms/ClassroomRepository.java`
- `repository/lms/StudentRepository.java`

Nhan xet:

- Quiz runtime endpoints co gia tri production hon phan admin/workspace.
- `listStudentAssignments()` va `listStudentScores()` doc assignments theo tenant roi filter in memory.
- `quizStudents()` doc attempts theo quiz roi lookup assignment/class trong loop.
- `ensureAttemptAccess()` lookup assignment moi attempt.
- `classAllowed()` lookup classroom trong filter loop.
- `educationUnitItems()` va `classItems()` doc all classes trong tenant roi filter.

Ket luan:

- Khong dung `LmsRuntimeService` lam GraphQL resolver foundation.
- Tao read-model service rieng, repository query theo scope/page/batch.

### 3.4 HocLieu/LCMS

File quan trong:

- `service/hoclieu/HocLieuService.java`
- `repository/hoclieu/*`
- `common/response/hoclieu/HocLieuLibraryResponses.java`

Nhan xet:

- HocLieu da co typed response, cache, taxonomy/resource/progress tach tuong doi tot.
- `libraryBootstrap()` doc taxonomy va resources de lap tree. Tot cho data vua, can chia cache taxonomy va page resource khi data lon.
- `studioBootstrap()` doc tat ca resources, co nguy co nang khi studio lon.
- `resources()` chua co pagination trong response hien tai.

Ket luan:

- GraphQL query dau tien nen di tu HocLieu vi shape da gan read model.
- Truoc production can them pagination/hard max va tach cache theo static taxonomy vs user progress.

### 3.5 Config/observability/load

File quan trong:

- `pom.xml`
- `application.yml`
- `config/CacheConfig.java`
- `config/persistence/PostgresJpaConfig.java`
- `platform/web/ApiAccessLogFilter.java`

Nhan xet:

- Java 25, Spring Boot 4.0.6, GraalVM native plugin da co.
- Hikari default `maximum-pool-size=25` co the phu hop mot instance, nhung 5.000 concurrency phai tinh theo RPS, DB CPU, query latency va so instance. Khong nen tang pool qua lon de "chiu tai"; can cache, pagination, async export, query bounded.
- Redis dependency co trong pom/config nhung cache hien tai dung Caffeine local. Neu scale multi-instance, session/current/resource cache can co strategy invalidation hoac Redis cache cho read-model hot path.
- `ApiAccessLogFilter` log full query string. Can mask token/password/secret va GraphQL variables.
- `management.endpoints.web.exposure.include=health,info`; can them metrics/prometheus trong enterprise.
- Dev config include stacktrace/message; prod da tat. Tot.

### 3.6 Virtual Threads va cac cong cu JVM/Spring hien dai

Noi dung bo sung tu phan phan tich them:

- Java 21+ den Java 25 co the dung Virtual Threads de giu code synchronous MVC nhung tang kha nang xu ly concurrent blocking I/O.
- Trong Spring Boot co the bat:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

Nhung can hieu dung:

- Virtual Threads giai quyet ap luc OS thread/context switching o tang app.
- Virtual Threads khong lam DB chiu duoc 5.000 connections.
- Neu 5.000 request cung cho DB connection, bottleneck se chuyen sang Hikari/DB.
- Vi vay bat Virtual Threads phai di kem:
  - Hikari pool bounded.
  - query timeout.
  - pagination/projection.
  - cache.
  - rate limit/bulkhead.
  - async export/job cho tac vu nang.

Khuyen nghi:

- Thu nghiem Virtual Threads o staging voi workload REST/GraphQL that.
- Khong bat production neu chua co load test va metrics Hikari/DB.
- Neu dung JPA/Hibernate, van can kiem soat transaction ngan, query bounded va no N+1.
- Long-lived connection nhu WebSocket/SSE can danh gia rieng; khong gop chung voi REST CRUD/request-response.

## 4. Lo trinh uu tien bat buoc

### Phase 0 - Khoa trust boundary truoc moi viec

Muc tieu: public client khong the tu cap role, permission, managed scope.

Can lam:

1. Them config:

```yaml
erg:
  security:
    allow-header-auth: false
    trust-forwarded-principal: false
```

2. Sua `ErgAuthenticationFilter`:

- Production chi chap nhan Bearer token hoac cookie token duoc server verify.
- Header auth chi bat duoc trong `test` hoac profile noi bo.
- Neu dung gateway header, header phai duoc gateway strip tu public request va ky/noi bo hoa.
- Khong chap nhan `X-Roles`, `X-Permissions`, `X-Denied-Permissions` tu client public.

3. Sua `UserAccessContext`:

- `managedEducationUnitIds`, `managedClassIds` chi lay tu `ErgPrincipal.attributes()` hoac service/database.
- Header/request/body chi la requested current scope.
- Empty managed scope = khong co access, tru khi principal co global scope `*` hoac permission/role global.
- Bo fallback `X-EducationUnit-ID` vao managed scope.

4. Them regression tests:

- Khong token + `X-User-ID`/`X-Permissions:*` o prod -> 401.
- Token user A + spoof `X-Managed-EducationUnit-IDs` school B -> van 403.
- Deny permission override thang wildcard grant.
- Wrong `X-Portal` -> 403.
- Teacher chi thay lop/truong minh quan ly.

Acceptance criteria:

- Moi route protected chi pass khi principal duoc verify.
- Khong mot test nao can `X-Permissions:*` trong profile production.
- `UserAccessContext.canAccess*` deny-by-default khi scope rong.

### Phase 1 - Chuan hoa DTO/response/request

Muc tieu: API contract de doc, de version, de generate client, de native image.

Rule:

- Controller public tra `ApiResponse<T>` voi `T` la record typed.
- List co pagination tra `PageResponse<T>` voi `items`, `page`, `size`, `totalItems`, `totalPages`, `hasNext`, `hasPrevious`; response typed co `items + meta` chi dung nhu compatibility adapter da document.
- Request body phai la record request typed + validation annotation.
- Chi dung `Map<String,Object>` cho:
  - metadata dynamic da co whitelist.
  - quiz answer/client event co validation size/depth.
  - internal admin workflow placeholder trong giai do chua production.
- Khong return entity JPA truc tiep ra controller.
- Khong dua password hash, token hash, raw credential, denied internal notes ra response.

Can sua theo tung module:

- LMS admin/workspace: thay placeholder `Map<String,Object>` bang DTO that khi implement.
- Notifications/Documents/CMS placeholder: tach ro "prototype surface" va "production surface".
- User list: thay `findAll().stream()` bang repository pageable/filter.
- HocLieu resources/studio bootstrap: them `PageRequestDto` hoac filter input typed.

Response convention:

```json
{
  "statusCode": 200,
  "message": "success",
  "data": {},
  "errors": null,
  "timestamp": "...",
  "path": "/api/v1/...",
  "request_id": "..."
}
```

Khuyen nghi code style:

- Trong Java bat buoc dung `requestId`; wire REST hien tai giu `request_id` bang `@JsonProperty("request_id")`.
- Incoming REST pagination la zero-based; `PageRequestDto` normalize page/size va moi endpoint list moi khong duoc tao pagination envelope thu hai.
- Error `errors` nen la object co `code`, `message`, `details` nhat quan hon thay vi luc string luc list. Neu chua doi ngay, ghi la v1 compatibility.

### Phase 2 - Hoan thien REST nen cho LMS/current/scope

Bat buoc co that:

```http
GET /api/v1/sessions/current
PUT /api/v1/lms/scopes/current
GET /api/v1/lms/classes?schoolId=...
GET /api/v1/education-units
```

REST command giu rieng:

- `POST /api/v1/lms/assignments`
- update assignment.
- save attendance.
- save score adjustment/manual override.
- import students/preview/commit.
- quiz attempt start/sync/save/submit.
- upload/launch/download HocLieu assets.
- export reports async job.
- user/role/access writes.

Khong dua command quan trong vao GraphQL trong v1.

### Phase 3 - Them GraphQL shell

Dependency:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-graphql</artifactId>
</dependency>
```

Config:

```yaml
spring:
  graphql:
    path: /api/v1/graphql
    graphiql:
      enabled: false
```

GraphQL context gom:

- Bearer/session principal da verify.
- `X-Portal`.
- `X-Tenant-ID`.
- `X-Request-ID`.
- `ErgPrincipal`.
- `UserAccessContext` authoritative.
- operationName.

Guard production:

- Bat buoc `operationName`.
- Tat GraphiQL.
- Introspection chi dev/admin/internal.
- Max depth 6-8.
- Max complexity theo operation.
- Default page size 20-50.
- Hard max 200.
- Timeout 3-5s cho GraphQL read.
- Rate limit theo tenant/user/operation.
- Mask variables nhay cam trong log.
- Khong cho report export trong GraphQL.

### Phase 4 - Tao read-model services chong N+1

Tao service rieng:

- `LmsLearningResourceReadService`
- `LmsClassWorkspaceReadService`
- `LmsHomeworkWorkspaceReadService`
- `LmsScoreSheetReadService`
- `LmsAttendanceReadService`
- `LmsReportReadService`
- `LcmsQuestionBankReadService`
- `LcmsAccessManagementReadService`
- `ElearningStudentDashboardReadService`

Rule:

- Read-model service chi read, `@Transactional(readOnly = true)`.
- Moi input co tenant/scope/page/filter.
- Moi query co hard limit.
- Moi heavy query dung Projection/aggregate projection truoc khi map sang DTO.
- Moi nested data dung batch query hoac DataLoader.
- Khong lookup repository trong loop neu loop co the > 20 item.
- Khong doc all tenant roi filter trong Java.
- Khong return raw answer/audit/private field neu permission field-level khong cho phep.

Projection design:

- `*RowProjection`: row trong table/list.
- `*SummaryProjection`: count/avg/max/min/status summary.
- `*CardProjection`: card lightweight cho FE.
- `*OptionProjection`: picker/dropdown.
- `*DetailProjection`: detail co cot can thiet nhung khong load relation khong can.

Vi du package nen dung:

```text
repository/lms/projection
repository/hoclieu/projection
repository/authorization/projection
common/response/lms
common/response/hoclieu
```

DTO vs Projection:

- Projection khong phai public contract.
- DTO public khong phu thuoc truc tiep vao entity.
- Read-model service assemble nhieu projection thanh mot workspace DTO.

EntityGraph/BatchSize:

- Dung `@EntityGraph` cho detail read can mot vai relation ro rang, vi du user detail kem roles/permissions.
- Khong dung `FetchType.EAGER` mac dinh cho relation lon.
- Co the cau hinh `hibernate.default_batch_fetch_size=50-100` de giam N+1 lazy loading con sot lai.
- Batch fetch chi la safety net, khong thay the projection/batch query cho workspace nang.

Repository can them:

Assignments:

- `findByTenantIdAndAcademicClassIdInAndStatusInOrderByCreatedAtDesc`
- `findByTenantIdAndAssignedByAndStatusInOrderByCreatedAtDesc`
- `findByTenantIdAndIdIn`
- projection summary count theo class/status/due.

Attempts:

- `findByTenantIdAndAssignmentIdInOrderByUpdatedAtDesc`
- `findByTenantIdAndAssignmentIdInAndStudentIdInOrderByUpdatedAtDesc`
- summary projection: assignmentId, status, count, avgScore, maxScore.
- latest/best attempt grouped by assignmentId/studentId.

Classes:

- `findByTenantIdAndIdIn`
- `findByTenantIdAndSchoolIdInAndStatusInOrderByNameAsc`

Students:

- `findByTenantIdAndAcademicClassIdOrderByFullNameAsc(Pageable)`
- `findByTenantIdAndAcademicClassIdInOrderByFullNameAsc(Pageable)`
- search by class/status/name/code.

HocLieu:

- resources pageable theo tenant/status/visibility/subject/category/section/topic.
- projection cards khong load metadata nang neu khong can.
- progress grouped by lesson/resource/user/school/year.

Authorization/users:

- user pageable/filter by tenant/status/role/search.
- effective permission batch by userIds.
- scope options query bounded.

Projection can tao som:

- `ClassOptionProjection(id, name, schoolId, grade, academicYear)`.
- `StudentRowProjection(id, fullName, studentCode, status, academicClassId)`.
- `AssignmentCardProjection(id, academicClassId, quizId, subjectId, status, dueAt, recipientMode, createdAt)`.
- `AssignmentProgressSummaryProjection(assignmentId, submittedCount, inProgressCount, needsReviewCount, averageScore)`.
- `AttemptScoreProjection(assignmentId, studentId, score, maxScore, percent, status, updatedAt)`.
- `HocLieuResourceCardProjection(id, title, subjectId, categoryId, sectionId, topicId, status, visibility, updatedAt)`.
- `UserAccessRowProjection(userId, fullName, email, status, roles, scopeCount)`.
- `EffectivePermissionProjection(userId, permission, source, denied, expiresAt)`.

### Phase 5 - GraphQL query contract theo thu tu trien khai

1. `lms.learningResourceLibrary(input)`

Du lieu:

- taxonomy tree.
- resource cards paged.
- progress by lesson/resource.
- recent opened.
- scope summary.

Dung:

- HocLieu read model.
- REST asset launch/download/progress write van giu.

2. `lms.classWorkspace(classId, range, page)`

Du lieu:

- class info.
- students page.
- assignment summaries.
- latest/best scores.
- attendance summary.
- risk flags.

Khong tra:

- raw answers mac dinh.
- private notes.
- full audit.

3. `lms.teacherHomeworkWorkspace(scope, filters, page)`

Du lieu:

- assignments cua giao vien.
- class summaries.
- completion counters.
- pending review.
- overdue/missing list.

4. `elearning.studentDashboard`

Du lieu:

- profile summary.
- assignments.
- scores.
- announcements.
- next actions.

REST van giu:

- attempt package/start/sync/submit.

5. `lcms.questionBankWorkspace(filters, page)`

Du lieu:

- subjects/levels/topics.
- questions page.
- quiz summaries.
- usage metrics.

6. `lcms.accessManagementWorkspace(filters, page)`

Du lieu:

- users page.
- role groups.
- scope options.
- effective access summary.
- warnings.

Field nhay cam:

- effective permissions can `rbac.permission.read`.
- save access dung REST command.

## 5. Ke hoach man hinh LMS/LCMS

| Man hinh | Du lieu can | API nen dung |
|---|---|---|
| Teacher shell/sidebar | user, permissions, schools, classes, current scope | REST `GET /api/v1/sessions/current`, `GET /api/v1/lms/classes` |
| Homework list | assignments, class summary, due status, completion counts | GraphQL `lms.teacherHomeworkWorkspace` |
| Assign homework | classes, students/groups, exercise/quiz picker | GraphQL read + REST `POST /api/v1/lms/assignments` |
| Homework progress | assignment detail, recipients, attempts summary, missing/late | GraphQL `lms.assignmentProgressWorkspace` hoac trong class/homework workspace |
| Exercise bank | subjects, levels, topics, questions/quizzes, usage | GraphQL `lcms.questionBankWorkspace`; CRUD REST |
| Class management | class info, students page, assignments, progress, risk flags | GraphQL `lms.classWorkspace` |
| Score sheet | students, assignment columns, best/latest scores, overrides | GraphQL read + REST save score |
| Attendance | sessions/week grid, students, attendance states, summary | GraphQL read + REST save attendance |
| Calendar | schedule, class sessions, event status | REST range query, optional GraphQL khi merge workspace |
| Class log | sessions, lesson log, attendance/notes snapshot | REST read/write, GraphQL neu gom report |
| Resources | taxonomy, resources, progress, recent opened | GraphQL `lms.learningResourceLibrary`; REST launch/download |
| Reports | progress, score, attendance, trends | GraphQL summary; REST async export |
| Notifications | list, unread count, detail, mark read | REST `/api/v1/notifications` |
| Account/login logs | profile, sessions, revoke, password | REST `/api/v1/users/me...` |
| LCMS overview | counts/resources/questions/import jobs | GraphQL `lcms.overview` |
| Schools/Centers | list/detail/tree/write | REST list/write; GraphQL aggregate neu can |
| Import students | preview/map/commit/credentials | REST only |
| Users/Permissions | users page, roles, scopes, effective access | GraphQL read + REST save |
| Quiz editor | project/slides/import/publish/autosave | REST project load/save; GraphQL picker/bootstrap |
| Learning resources studio | taxonomy, resource cards, assets, presets | GraphQL read hoac REST bootstrap paged; commands REST |
| Public disclosure | docs/PDF/metadata/publish/delete | REST only |
| Settings | tenant flags/portal config | REST den khi requirement on dinh |

## 6. Security/RBAC/scope enterprise checklist

### Authentication

- Bearer token duoc hash va match session active.
- Session revoked/expired/user inactive -> 401.
- Refresh token khong dung de access protected route.
- Header auth mac dinh off.
- Cookie token neu dung phai HttpOnly, Secure production, SameSite phu hop portal.
- Public endpoints duoc liet ke ro, khong dung `anyRequest().permitAll()` cho API moi.

### Authorization

- Route permission o `SecurityConfig` chi la lop ngoai.
- Service permission/scope la bat buoc.
- GraphQL phai field-level permission.
- Deny override thang wildcard.
- Global access chi khi role/permission/scope co `*` ro rang.
- Portal mismatch -> 403.
- Tenant mismatch -> 403 hoac 404 theo chinh sach concealment.

### Scope

- Managed scope authoritative tu DB/session/principal.
- Requested scope tu header/body/input phai validate.
- Teacher chi xem school/class duoc gan.
- Student chi xem self dashboard/attempt.
- LCMS admin xem scope theo role.
- Effective permissions la sensitive.

### Data protection

- Khong log Authorization, cookie, password, token, secret, API key, GraphQL variables nhay cam.
- Khong return password hash/token hash/session raw token.
- Credential import hoc sinh phai co expiry/download-once/masking/audit.
- Raw quiz answers chi field co permission ro.
- Audit events/private notes can permission rieng.

## 7. N+1 va performance checklist

Khong chap nhan:

- `findAll().stream().filter()` cho endpoint co du lieu lon.
- `findByTenantIdOrderBy...` roi filter in-memory cho workspace.
- repository call trong loop item/page.
- GraphQL nested resolver tu goi repository tung node.
- response khong page/hard limit.

Bat buoc:

- Pageable o repository.
- Projection DTO cho summary/card.
- Batch query `IN (...)` theo ids.
- DataLoader neu nested GraphQL can resolve theo relation.
- `@EntityGraph` chi dung cho detail/read nho co relation ro rang.
- `hibernate.default_batch_fetch_size` lam lop bao ve bo sung, khong duoc xem la fix chinh cho workspace.
- Query count test cho moi workspace.
- Index composite dung theo filter/order.
- Cache taxonomy/static config rieng voi user progress.
- Export/report nang chay async REST job, khong chay trong GraphQL.

Trang thai hien tai sau vong worker moi:

- `lms.learningResourceLibrary` da dung projection cho resource cards, taxonomy, resource counts, progress va recent opened.
- `lms.classWorkspace` da dung projection cho class info va roster page.
- Da co repository/read-model contract tests de pin projection/paging/scope.
- Chua co query-count integration proof bang Hibernate statistics/datasource proxy, nen chua duoc xem la da khoa het N+1 cho production.

Index can bo sung:

```sql
CREATE INDEX IF NOT EXISTS idx_lms_assignments_tenant_class_status_due
  ON lms_assignments(tenant_id, academic_class_id, status, due_at);

CREATE INDEX IF NOT EXISTS idx_lms_assignments_tenant_teacher_created
  ON lms_assignments(tenant_id, assigned_by, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_lms_attempts_tenant_assignment_student_updated
  ON lms_attempts(tenant_id, assignment_id, student_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_lms_attempts_tenant_quiz_status_updated
  ON lms_attempts(tenant_id, quiz_id, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_academic_classes_tenant_school_status_name
  ON academic_classes(tenant_id, school_id, status, name);

CREATE INDEX IF NOT EXISTS idx_lms_students_tenant_class_status_name
  ON lms_students(tenant_id, academic_class_id, status, full_name);

CREATE INDEX IF NOT EXISTS idx_hoclieu_resources_tenant_status_visibility_updated
  ON hoclieu_resources(tenant_id, status, visibility, updated_at DESC);
```

## 8. Chiu tai 5.000 concurrency

5.000 concurrency khong co nghia la 5.000 DB connections. Virtual Threads co the giup app nhan va cho blocking I/O tot hon OS threads, nhung DB connection pool van la tai nguyen huu han. Kien truc dung:

- Stateless app instances sau load balancer.
- Hikari pool moi instance nho va co tinh toan, thuong 20-40 tuy DB.
- Redis/distributed cache cho session/current/read hot path neu chay multi-instance.
- Caffeine local chi tot cho mot instance hoac cache tolerate stale.
- GraphQL query bounded, timeout ngan.
- Heavy reports -> async job queue + object storage.
- Upload/download/asset stream khong di qua memory lon.
- Backpressure/rate limit theo tenant/user/operation.
- Circuit breaker/timeouts/bulkhead cho external AI/storage/third-party.
- DB read query p95 muc tieu duoi 200-400ms cho read model.

Virtual Threads rollout:

- Bat `spring.threads.virtual.enabled=true` o staging truoc.
- Do Hikari pending/active connections, DB CPU, query latency, heap allocation.
- Neu DB pool cho lau, fix query/cache/pool/read replica; khong tang pool vo toi va.
- Long-lived WebSocket/SSE can duoc do rieng voi REST/GraphQL.

Hikari/DB:

- Pool size theo DB capacity va so instance, khong theo so concurrent users.
- Them connection timeout ngan hop ly de fail fast khi qua tai.
- `hibernate.jdbc.timeout` va `jakarta.persistence.query.timeout` phai bat trong `application.yml`; hien config da co, nhung can slow-query smoke/integration test de xac nhan driver va DB ap dung dung.
- Neu dung PostgreSQL production, can quyet dinh them `statement_timeout` theo session/user/datasource de chan query treo sau tang JDBC/JPA.
- Can read replica/read-write split cho read-heavy LMS/LCMS neu traffic lon.
- `@Transactional(readOnly = true)` giup routing read replica neu sau nay them routing datasource.

Cache stampede:

- Redis cache cho multi-instance hot read.
- Dung single-flight/cache lock hoac `@Cacheable(sync = true)` cho local hot cache neu phu hop.
- TTL nen co jitter neu Redis de tranh het han dong loat.

Resilience:

- Them Resilience4j cho AI/storage/external APIs.
- Bulkhead theo resource de external slow khong giu het request capacity.
- Circuit breaker fallback phai an toan, khong tra data sai scope.

GC/JVM:

- Tren JVM deployment co the xem xet ZGC/Shenandoah cho latency thap.
- Native image co memory/latency profile khac, can benchmark rieng thay vi ap dung y nguyen JVM flags.

Target:

- `/api/v1/sessions/current` p95 < 300ms.
- cached resource/dashboard p95 < 800ms.
- class workspace p95 < 1s voi pagination.
- GraphQL timeout 3-5s.
- Export report tra job id < 300ms.
- No N+1: query count bounded theo screen, khong tang theo so row nested.

Can them test/load:

- Query-count integration tests cho `learningResourceLibrary` va `classWorkspace` truoc load test.
- Slow-query/JPA timeout smoke de chung minh timeout that su abort DB query.
- k6/Gatling scenario login/session/current/classes.
- GraphQL resource library 100/500/1000 concurrent virtual users.
- class workspace voi class 50/100/200 hoc sinh.
- assignment progress voi 1.000 attempts.
- soak test 30-60 phut.
- DB explain analyze cho query workspace.

### 8.1 Bổ sung từ phân tích Java 21/25, Spring Boot và Virtual Threads

Virtual Threads là hướng đúng cho Java 25/Spring Boot nếu hệ thống vẫn muốn giữ code synchronous MVC dễ bảo trì:

- Request blocking I/O như DB/API ngoài có thể unmount khỏi carrier thread, giảm áp lực OS thread.
- Code controller/service vẫn rõ ràng, không phải chuyển toàn bộ sang WebFlux/reactive chain.
- 5.000 concurrent request không còn bị giới hạn chủ yếu bởi số OS thread.

Nhưng enterprise gate phải hiểu đúng bottleneck:

- Virtual Threads không làm PostgreSQL/MySQL nhận được 5.000 connection hiệu quả.
- Hikari pool vẫn phải bounded; nếu pool 25 thì request thứ 26 trở đi chờ connection.
- Khi cache miss đồng loạt, traffic sẽ dồn xuống DB và tạo cache stampede.
- Khi external storage/AI/mail/API chậm, virtual thread vẫn giữ object/request context trong memory; cần timeout, bulkhead và circuit breaker.
- GC pressure tăng nếu GraphQL/read-model trả object graph lớn hoặc load full entity.

Quy tắc khi bật Virtual Threads:

- Chỉ bật production sau khi staging load test với traffic thật: session/current, classes, resource library, class workspace, assignment progress.
- Mỗi endpoint read-heavy phải có Projection + pagination trước khi đưa vào workload 5.000 concurrency.
- Mọi external call phải có timeout và Resilience4j policy.
- Hikari `maximumPoolSize` không scale theo số user concurrent; scale theo DB capacity, số app instance và latency query.
- Cache hot path phải có cơ chế chống stampede: Redis lock/single-flight hoặc `@Cacheable(sync = true)` cho local cache phù hợp.
- Report/export/import nặng không chạy trong request/GraphQL; trả job id qua REST async.

Thông số theo dõi khi load test:

- Hikari active/idle/pending/timeout.
- DB CPU, lock wait, slow query, index scan vs sequential scan.
- JVM heap allocation, GC pause, thread pinning nếu có synchronized/native/blocking region.
- HTTP p95/p99 latency theo endpoint/GraphQL operationName.
- Cache hit ratio, miss burst, Redis latency.
- Số query SQL trên mỗi request workspace.

GC/JVM:

- Với JVM deployment, cân nhắc ZGC hoặc Shenandoah cho latency thấp khi heap lớn.
- Với GraalVM native image, không áp dụng nguyên xi JVM tuning; benchmark riêng vì native có profile memory/latency khác.
- Ưu tiên giảm object allocation từ gốc bằng Projection/DTO nhỏ, không chỉ dựa vào GC.

### 8.2 Bổ sung từ attachment: điều kiện chịu tải khi dùng Java 25/Spring Boot

Virtual Threads là hướng phù hợp cho Spring MVC synchronous nếu muốn code dễ đọc, dễ debug và dễ bảo trì. Nhưng Virtual Threads không phải "thuốc chữa bách bệnh" cho 5.000 concurrency. Nó giúp giảm áp lực OS thread/context switching, còn bottleneck sẽ chuyển sang DB connection pool, query latency, cache, external service, heap allocation và GC.

Các quyết định phải đưa vào rollout:

- Bật `spring.threads.virtual.enabled=true` theo phase, bắt đầu ở staging/load test.
- Hikari pool phải bounded theo năng lực DB và số instance, không set theo số user concurrent.
- Mọi read-heavy endpoint/GraphQL query phải có Projection + pagination + query-count test trước khi load test 5.000 concurrency.
- Không để GraphQL/report/export query chạy lâu trong request thread; report/export dùng REST async job.
- Dùng Redis/distributed cache cho hot read path multi-instance; Caffeine chỉ dùng local hoặc dữ liệu chấp nhận stale.
- Chống cache stampede bằng single-flight/cache lock hoặc `@Cacheable(sync = true)` khi phù hợp, TTL nên có jitter.
- External call phải có timeout, retry có giới hạn, circuit breaker và bulkhead bằng Resilience4j.
- Mọi query workspace phải có `EXPLAIN ANALYZE` hoặc test repository chứng minh dùng index đúng với `WHERE + ORDER BY`.
- Native image/GraalVM phải benchmark riêng vì memory/latency profile khác JVM; không copy y nguyên tuning ZGC/Shenandoah.

N+1 strategy chuẩn:

- Ưu tiên Projection/aggregate Projection để DB chỉ trả đúng cột và summary cần.
- Dùng `@EntityGraph` cho detail nhỏ có relation cố định.
- Dùng `hibernate.default_batch_fetch_size=50-100` như safety net, không thay thế projection.
- Dùng DataLoader cho GraphQL nested relation còn lại sau khi base page đã được projection.
- Không gọi repository trong loop, không load child collection rồi count/sort/filter trong Java cho màn hình workspace.

Điều kiện acceptance trước production:

- Hikari active/pending/timeout được expose qua Prometheus.
- Metrics theo GraphQL `operationName`, endpoint REST, tenant và status.
- p95/p99 đo riêng cho session/current, resource library, class workspace, homework workspace.
- Query count không tăng theo số học sinh/bài tập/nested cards trong test.
- Heap allocation không tăng do load entity graph lớn; response DTO/projection nhỏ và bounded.
- Load test có scenario 100/500/1000 virtual users trước, sau đó mới lên 5.000 concurrent theo từng cụm app instance.

## 9. GraalVM native image va Spring Boot 4/Java 25

Rule de native-friendly:

- Uu tien Java records, constructor injection.
- Han che reflection/dynamic class loading.
- Khong dung proxy magic/SpEL phuc tap trong hot path neu khong can.
- Jackson DTO typed tot hon `Map<String,Object>` tran lan.

## 10. Phụ lục bổ sung: chuẩn Projection cho DTO, GraphQL và 5.000 concurrency

Phần này chốt lại câu hỏi "Projection có áp dụng được không hay DTO là đủ?". Kết luận enterprise: DTO là bắt buộc nhưng không đủ cho read-heavy. Projection áp dụng được và phải áp dụng cho các màn hình LMS/LCMS/HocLieu/Elearning nặng.

### 10.1 Ranh giới trách nhiệm

| Lớp | Trách nhiệm | Không được làm |
|---|---|---|
| Request DTO | Nhận input từ REST/GraphQL, validate shape, default page/filter | Không chứa entity hoặc projection nội bộ |
| Response DTO | Hợp đồng public cho FE, REST, GraphQL, versioning và backward compatibility | Không expose field DB nội bộ, raw token, password, raw answer, credential |
| Projection | Hợp đồng đọc DB/read-model: select cột nào, aggregate nào, join/scope/index nào | Không trả trực tiếp từ controller/resolver |
| Read-model service | Ghép nhiều projection thành workspace DTO, enforce scope, paging, batch query | Không gọi repository trong loop theo từng row |
| Domain service command | Transaction write, audit, idempotency, rule nghiệp vụ | Không dùng GraphQL mutation cho v1 nếu command cần audit/file/import/export |

DTO trả cho FE chỉ giải quyết contract. Nếu phía sau DTO vẫn load full entity, lazy collection hoặc entity graph lớn rồi mới map DTO, hệ thống vẫn có N+1, tốn heap, giữ DB connection lâu và không đạt mục tiêu 5.000 concurrency. Projection giải quyết phần đọc dữ liệu từ gốc: ít cột hơn, aggregate ở DB, paging cứng, query count bounded.

### 10.2 Quy tắc chọn Entity, EntityGraph, Projection, DataLoader

| Trường hợp | Cách làm chuẩn |
|---|---|
| Command/write: create, update, delete, submit, import, upload | Request DTO + service transaction + Response DTO; không dùng projection làm public contract |
| Detail nhỏ theo id, relation ít và cố định | Entity hoặc `@EntityGraph` có chủ đích rồi map sang DTO |
| List paginated đơn giản | Interface/record projection rồi map sang DTO |
| Dashboard/workspace/report/GraphQL read | Page projection + aggregate projection + batch `IN (...)` query rồi map sang workspace DTO |
| Count/summary/metric | Aggregate projection bắt buộc, không load collection rồi count trong Java |
| GraphQL nested field còn lại | Base projection trước, sau đó DataLoader hoặc batch query; không gọi repository theo từng nested field |

Không dùng `FetchType.EAGER` để chữa N+1. `@EntityGraph` chỉ dùng cho detail read nhỏ, không dùng cho workspace/dashboard/report. `hibernate.default_batch_fetch_size=50-100` chỉ là safety net cho lazy relation còn sót lại, không thay thế projection.

### 10.3 Chuẩn đặt tên và package

Package projection nên giữ gần repository/read model:

```text
repository/lms/projection
repository/hoclieu/projection
repository/lcms/projection
repository/authorization/projection
repository/organization/projection
```

Package DTO public:

```text
common/request/<domain>
common/response/<domain>
platform/graphql/<domain dto nếu thật sự cần>
```

Tên projection:

- `*RowProjection`: row cho table/list.
- `*CardProjection`: card nhẹ cho dashboard/workspace.
- `*SummaryProjection`: count, avg, max, min, status summary.
- `*OptionProjection`: picker/dropdown.
- `*DetailProjection`: detail có giới hạn cột và relation.
- `*WorkspaceProjection`: chỉ dùng khi một query trả đúng shape workspace và đã được review security.

Ưu tiên record projection hoặc JPQL constructor expression cho query rõ shape và native-image friendly. Interface projection dùng được cho Spring Data query đơn giản. Native SQL projection chỉ dùng khi report/aggregate phức tạp cần `EXPLAIN ANALYZE` và kiểm soát index sát hơn.

### 10.4 Projection bắt buộc theo màn hình

| Màn hình | Projection cần có | Ghi chú security/N+1 |
|---|---|---|
| LMS homework list | `AssignmentCardProjection`, `AssignmentProgressSummaryProjection`, `ClassOptionProjection` | Filter tenant/teacher/school/class ở DB; không loop từng assignment để count attempts |
| LMS assignment progress | `AssignmentProgressSummaryProjection`, `AttemptScoreProjection`, `StudentRosterProjection` | Không trả raw answers mặc định; latest/best score dùng aggregate/batch query |
| LMS class workspace | `ClassWorkspaceProjection`, `StudentRosterProjection`, `AssignmentCardProjection`, `ClassScoreSummaryProjection`, `ClassRiskSummaryProjection`, `AttendanceSessionSummaryProjection`, `StudentRiskProjection` | Query count phải bounded theo page, không tăng theo số học sinh/bài tập nested |
| LMS score sheet | `ScoreSheetColumnProjection`, `AttemptScoreProjection`, `StudentRosterProjection` | Manual override dùng REST command; GraphQL chỉ đọc |
| LMS attendance | `AttendanceSessionSummaryProjection`, `StudentRosterProjection` | Save attendance qua REST command; summary tính ở DB |
| LMS reports | `ClassProgressSummaryProjection`, `AttendanceTrendProjection`, `AssignmentTrendProjection` | GraphQL chỉ summary; export dùng REST async job |
| LMS resources/HocLieu | `HocLieuResourceCardProjection`, `HocLieuTaxonomyNodeProjection`, `HocLieuTaxonomyCountProjection`, `HocLieuResourceProgressProjection`, `HocLieuRecentResourceProjection` | Đã bắt đầu có; cần query-count integration proof |
| LCMS overview | `LcmsOverviewMetricProjection`, `ImportJobSummaryProjection`, `ResourceUsageSummaryProjection` | Không scan full table trong Java |
| LCMS question bank | `QuestionRowProjection`, `QuizSummaryProjection`, `TopicOptionProjection`, `QuestionUsageProjection` | CRUD/editor vẫn REST; GraphQL chỉ picker/list/summary |
| LCMS access management | `UserAccessRowProjection`, `RoleOptionProjection`, `ScopeOptionProjection`, `EffectivePermissionProjection` | `EffectivePermissionProjection` chỉ trả khi có `rbac.permission.read` hoặc `users.manage` |
| Elearning student dashboard | `StudentAssignmentCardProjection`, `StudentScoreSummaryProjection`, `StudentNextActionProjection`, `AnnouncementCardProjection` | Student scope chỉ chính mình; attempt package/sync/submit vẫn REST |

### 10.5 Checklist bắt buộc khi thêm projection

- Query có `tenantId` trong `WHERE`.
- Query lọc school/class/user scope ngay trong SQL/JPQL, không đọc rộng rồi filter bằng Java.
- Query có `Pageable`, `LIMIT` hoặc hard max rõ ràng.
- Query có index tương ứng với `WHERE + ORDER BY`.
- Projection không chứa password, token, raw credential, raw quiz answer, private note nếu không có permission field-level rõ ràng.
- Aggregate tính ở DB bằng `count`, `avg`, `max`, `min`, `case when`, không load collection rồi tính trong Java.
- Read-model service map projection sang DTO, controller/resolver chỉ trả DTO.
- GraphQL resolver không gọi repository trong loop hoặc theo từng field nhỏ.
- Có repository/read-model contract test cho paging, tenant/scope và permission.
- Có query-count integration test bằng Hibernate statistics hoặc datasource proxy trước khi load test.

Acceptance query-count:

| Query | Số call repository/SQL mục tiêu |
|---|---|
| `lms.learningResourceLibrary` | Bounded theo taxonomy/cards/progress/recent; không tăng tuyến tính theo số resource nested |
| `lms.classWorkspace` | 1 class projection + 1 roster page projection + 1 assignment page projection + các summary batch cố định |
| `lms.teacherHomeworkWorkspace` | 1 assignment page projection + summary counts batch + class option batch |
| `lcms.questionBankWorkspace` | 1 question page projection + option taxonomy batch + usage summary batch |
| `lcms.accessManagementWorkspace` | 1 user page projection + role/scope/effective permission batch có permission gate |

Nếu query count tăng theo số row hoặc nested row, endpoint đó chưa đạt chuẩn enterprise và không được đưa vào load test 5.000 concurrency.

### 10.6 Liên hệ với Virtual Threads và chịu tải

Virtual Threads giúp Spring MVC synchronous xử lý nhiều request blocking I/O hơn mà không cần chuyển toàn bộ code sang reactive. Nhưng Virtual Threads không tăng năng lực DB, không giảm số SQL, không tự ngăn N+1 và không giảm object allocation nếu service vẫn load entity graph lớn.

Diễn giải từ attachment bổ sung:

- 5.000 concurrent không có nghĩa là mở 5.000 DB connection. Request có thể chạy trên Virtual Threads, nhưng DB vẫn bị giới hạn bởi Hikari pool, latency query, lock, CPU và I/O.
- Projection là lớp giảm áp lực đầu tiên: select ít cột hơn, aggregate tại DB, giảm heap allocation, giảm thời gian giữ connection và giảm nguy cơ GC pause.
- `@EntityGraph` chỉ phù hợp detail nhỏ; dùng cho dashboard/list lớn sẽ dễ biến thành join rộng hoặc duplicate row.
- `hibernate.default_batch_fetch_size` chỉ giảm hậu quả lazy loading còn sót, không phải kiến trúc chính cho LMS/LCMS workspace.
- Cache Redis/Caffeine phải có TTL, key theo tenant/scope/user và chống stampede; nếu cache miss đồng loạt, projection/query timeout vẫn phải bảo vệ DB.
- External calls phải có timeout, circuit breaker và bulkhead; Virtual Threads không được dùng để che việc dependency bị treo.
- Load test phải đo đồng thời p95/p99, Hikari active/pending/timeout, DB slow query, GC pause, cache hit ratio và query-count theo màn hình.

Vì vậy rollout 5.000 concurrency phải đi theo thứ tự:

1. Chuẩn hóa DTO/response và scope security.
2. Tách read-model service cho từng workspace.
3. Dùng projection + aggregate projection + paging cho read-heavy.
4. Thêm query-count integration test.
5. Thêm index và `EXPLAIN ANALYZE`.
6. Bật/tune Virtual Threads ở staging.
7. Load test 100 -> 500 -> 1.000 -> 5.000 concurrent.

Hikari pool không được scale theo số concurrent request. Pool phải scale theo DB capacity, số app instance và query latency. Projection giúp giảm thời gian giữ connection, từ đó mới làm Virtual Threads có ý nghĩa ở tải cao.
- Neu dung native image, check:
  - Spring AOT.
  - Hibernate/JPA entity hints.
  - BouncyCastle compatibility.
  - PostgreSQL driver.
  - GraphQL schema/resource inclusion.
  - Validation/Jackson hints.
- Tranh runtime scanning tuy tien o production.
- Khong dua secret/config dynamic vao static initializer.
- Test `mvn -Pnative native:compile` trong CI rieng khi bat dau native rollout.

### 9.1 Acceptance bổ sung cho worker 4: ops/load/native readiness

Trạng thái cấu hình nền cần được giữ bằng test cấu hình trước khi mở GraphQL/read-heavy production:

- `spring.threads.virtual.enabled` phải là biến môi trường, mặc định `false`; chỉ bật staging/prod sau khi có load test.
- Hikari phải có pool-name, `maximum-pool-size`, `minimum-idle`, `connection-timeout`, `validation-timeout`, `idle-timeout`, `max-lifetime`, `auto-commit=false` và được expose qua Micrometer/Prometheus.
- Actuator phải expose tối thiểu `health,info,prometheus`; health probes bật để phục vụ Kubernetes/liveness/readiness.
- Metrics phải có tag `application` và `environment`; production cần bổ sung tag operationName cho GraphQL và route/status cho HTTP.
- GraphQL ops knobs phải có trong config: `require-operation-name`, `introspection-enabled`, `max-query-length`, `max-depth`, `max-complexity`, `execution-timeout`, rate limit theo user/tenant/operation.
- Log access phải mask query/header/body sensitive keys như password, token, secret, api_key, authorization, cookie; GraphQL variables nhạy cảm không được log raw.
- `native-maven-plugin` phải tồn tại trong `pom.xml`; native compile chưa là gate production cho giai đoạn đầu, nhưng phải có smoke job riêng khi bắt đầu native rollout.

Các tiêu chí chưa được xem là hoàn thành nếu chỉ có config:

- Depth/alias/sensitive-variable guard đã có thể enforce ở filter, nhưng complexity thật, execution timeout thật và rate-limit thật vẫn phải được enforce trong GraphQL instrumentation/filter/rate limiter, không chỉ khai báo YAML.
- Load test phải có kết quả p95/p99, Hikari active/pending/timeout, DB CPU/slow query, cache hit ratio và query-count theo màn hình.
- Native smoke phải chạy trên CI/staging với profile riêng và kiểm tra serialization DTO/projection/schema resources.

## 10. Observability va operations

Can them:

- Micrometer + Prometheus endpoint.
- Request metrics: path/operation/status/latency.
- GraphQL metrics theo operationName.
- DB query count/slow query log o test/staging.
- Structured JSON logging production.
- Trace ID/request ID propagate qua REST/GraphQL/job.
- Audit log cho command nhay cam: role, permission, user, assignment, attendance, score, import, export.
- Mask log:
  - Authorization
  - Cookie
  - password
  - token
  - secret
  - apiKey
  - GraphQL variables sensitive.

`ApiAccessLogFilter` can sua:

- Khong log full query string neu co sensitive param.
- Lay userId/tenantId tu principal truoc header.
- Them duration bucket/operationName.

## 11. Test plan

### Contract tests

- Moi REST command co success/validation/forbidden/not found.
- Moi GraphQL query co schema contract va partial error handling.
- DTO response khong leak field nhay cam.

### Security tests

- No token -> 401.
- Spoof header auth prod -> 401.
- Wrong portal -> 403.
- Tenant mismatch -> 403/404.
- Teacher school A khong query class/student school B.
- Student chi query own dashboard/attempt.
- User khong co `rbac.permission.read` khong thay effective permissions.
- Deny override thang wildcard.

### GraphQL guard tests

- Missing operationName -> reject.
- Depth qua gioi han -> reject.
- Complexity qua gioi han -> reject.
- Page size qua max -> clamp/reject theo rule.
- Timeout -> error masked.
- Introspection off production.
- Variables nhay cam khong log.

### N+1/query count tests

- Dung Hibernate statistics hoac datasource proxy de dem SQL that trong integration test.
- Test can chay tren dataset co nhieu nested rows, khong chi mock repository.
- `lms.classWorkspace` voi 50/100/200 students co query count bounded.
- `lms.teacherHomeworkWorkspace` query count khong tang theo assignments nested.
- `lms.learningResourceLibrary` tach taxonomy/progress/resources.
- `lcms.accessManagementWorkspace` batch effective permissions.
- Neu query count tang theo so row, bat buoc them projection/batch query truoc khi load test.

### Native/build tests

- `mvn test`.
- `mvn -DskipTests package`.
- Spring AOT smoke.
- Native compile/staging smoke khi bat dau native phase.

## 12. Definition of Done cho GraphQL v1

Chua duoc rollout neu thieu mot trong cac dieu kien:

- Header auth off mac dinh production.
- Managed scope authoritative.
- Empty scope deny-by-default.
- GraphQL context dung principal/session.
- Field-level permission co test.
- Pagination/timeout/depth/complexity/rate limit.
- Read-model services khong co repository call trong loop.
- Query count tests pass.
- Sensitive log masking.
- Introspection/GraphiQL off production.
- FE da chuyen tung screen tu mock sang real contract theo feature flag.

## 13. Thu tu thuc hien de it risk

1. Security hardening: header auth config, authoritative scope, deny-by-default.
2. Test security regression.
3. DTO/request cleanup cho REST current/classes/HocLieu resources.
4. Repository batch/page/index migration.
5. Read-model service HocLieu resource library.
6. GraphQL shell + guards.
7. `lms.learningResourceLibrary`.
8. `lms.classWorkspace`.
9. `lms.teacherHomeworkWorkspace`.
10. `elearning.studentDashboard`.
11. `lcms.questionBankWorkspace`.
12. `lcms.accessManagementWorkspace`.
13. Load test + FE screen rollout.

## 14. Ghi chu ve source hien tai can theo doi

- Tests hien tai con dung `X-Permissions:*` va `X-Managed-*` de bootstrap request. Sau khi khoa production header auth, can tach test profile/internal helper ro rang.
- `ModuleWorkflowService`, `CmsService`, `DocumentService` dang dung in-memory map/placeholder. Khong dua vao enterprise production neu chua thay bang persistence/service typed.
- `UserService.listUsers()` dang `findAll().stream()`; can pageable repository truoc khi access management GraphQL.
- `HocLieuService.studioBootstrap()` va `resources()` can pagination.
- `LmsRuntimeService` giu cho runtime REST, nhung read workspace moi phai dung service moi.

## 15. Cập nhật bổ sung từ attachment: Projection, Virtual Threads và 5.000 concurrency

Kết luận sau khi đọc thêm attachment: Projection áp dụng được, cần áp dụng, và DTO một mình không đủ cho các màn hình nặng. DTO là hợp đồng public để FE/REST/GraphQL ổn định field name, versioning và validation. Projection là hợp đồng đọc DB/read-model để cắt số cột, cắt entity materialization, gom aggregate tại DB và chặn N+1 ngay từ query shape. Với chuẩn enterprise, mọi GraphQL workspace nặng phải đi theo chuỗi:

```text
GraphQL input/context
  -> access/scope guard
  -> read-model service
  -> repository projection/page/aggregate query
  -> mapper Projection -> DTO
  -> GraphQL/REST response DTO
```

Không trả Projection trực tiếp ra public contract, nhưng cũng không được load full Entity rồi map DTO cho dashboard/list/report lớn.

### 15.1 Quy tắc chọn DTO, Projection, EntityGraph và batch fetch

| Tình huống | Chuẩn enterprise |
|---|---|
| Command create/update/delete | Request DTO + Response DTO; transaction/audit/idempotency qua REST |
| Detail nhỏ theo id | Entity -> DTO hoặc `@EntityGraph` có kiểm soát, có permission/scope guard |
| List/table paginated | Projection/page query -> DTO |
| Dashboard/workspace/report | Projection + aggregate projection + batch query -> DTO |
| GraphQL nested list | Base projection phải bounded trước; chỉ dùng DataLoader cho phần nested còn lại |
| Relation lazy còn sót | `hibernate.default_batch_fetch_size=50-100` là safety net, không phải fix chính |

Không dùng `FetchType.EAGER` để chữa N+1. `@EntityGraph` chỉ phù hợp detail nhỏ; với workspace LMS/LCMS/HocLieu/Elearning phải ưu tiên projection theo row/card/summary/option.

### 15.2 Trạng thái projection hiện tại cần ghi đúng

- `learningResourceLibrary`: đã có projection cho resource card, taxonomy, count, progress, recent opened và query-count integration proof đầu tiên bằng `HocLieuLibraryQueryCountIntegrationTest`; còn cần PostgreSQL EXPLAIN/index review, cache stampede guard, load/soak và native gates.
- `classWorkspace`: đã có class info, roster page, assignment summary page, score summary first implementation bằng `ClassScoreSummaryProjection`, risk summary first implementation bằng `ClassRiskSummaryProjection` và query-count integration proof đầu tiên bằng `LmsClassWorkspaceQueryCountIntegrationTest`; `scoreSummary` là nullable field được gate bởi `lms.grade.read`/`lms.manage`, `riskSummary` là nullable field được gate bởi `lms.progress.read`/`lms.grade.read`/`lms.manage`. Còn thiếu attendance summary, EXPLAIN/index evidence và load/native gates.
- `teacherHomeworkWorkspace`: đã có first slice bằng `AssignmentCardProjection`, summary aggregate projection `TeacherHomeworkSummaryProjection` cho active/overdue/pending-review counts và query-count integration proof đầu tiên bằng `LmsTeacherHomeworkWorkspaceQueryCountIntegrationTest`; focused suite mới đã xanh `17/17`. Còn thiếu class options, pending-review/overdue detail lists, EXPLAIN/index evidence và load/native gates.
- `studentDashboard`: đã có assignment slice/counters/next actions bằng `StudentDashboardAssignmentProjection`, score summary bằng `StudentDashboardScoreSummaryProjection` và query-count integration proof đầu tiên bằng `StudentDashboardQueryCountIntegrationTest`; còn thiếu announcement projection, PostgreSQL EXPLAIN/index evidence và load/native gates.
- `questionBankWorkspace`: đã có question page/options/quiz options và batch usage count bằng `QuestionUsageCountProjection`; cần DB integration test, query-count proof và EXPLAIN vì query usage dùng native JSONB/lateral.
- `accessManagementWorkspace`: đã có user row, role/scope options, effective permission batch khi đủ quyền và query-count integration proof đầu tiên bằng `AccessManagementWorkspaceQueryCountIntegrationTest`; cần field-level matrix đầy đủ, PostgreSQL EXPLAIN/index evidence và load/native gates.

### 15.3 Gate N+1 bắt buộc trước load test

Mỗi workspace phải có integration test đếm SQL thật bằng Hibernate statistics hoặc datasource proxy trên dataset có nhiều nested rows. Test mock repository chỉ chứng minh service gọi bounded method, chưa chứng minh Hibernate/JDBC không sinh N+1.

Acceptance tối thiểu:

- Query count không tăng tuyến tính theo số học sinh, assignment, question hoặc permission nested.
- Không có repository call trong loop của resolver/read-model service.
- Không load toàn bộ tenant rồi filter bằng Java.
- Aggregate count/avg/max/min/status phải tính ở DB.
- Projection query có `tenantId`, scope filter, `Pageable`/limit và index tương ứng.
- Raw answer, token, password, credential, private note không có trong projection mặc định.

### 15.4 Virtual Threads không thay thế thiết kế dữ liệu

Virtual Threads phù hợp với Java 25/Spring MVC vì giữ code synchronous dễ đọc và giảm áp lực OS thread/context switching. Nhưng Virtual Threads không làm DB chịu được 5.000 connection, không tăng Hikari pool an toàn, không chữa N+1 và không giảm object allocation nếu vẫn load entity graph lớn.

Vì vậy 5.000 concurrency phải hiểu là năng lực phục vụ request đồng thời ở tầng app, còn DB connection vẫn là tài nguyên hữu hạn:

- Hikari pool scale theo DB capacity, số app instance và query latency, không scale theo số user concurrent.
- Projection/paging/query timeout giảm thời gian giữ connection.
- Cache Redis/Caffeine chỉ bật sau khi cache key bao gồm tenant/scope/user đúng và có stampede guard.
- Hot read path cần TTL jitter, single-flight hoặc distributed lock khi dùng Redis.
- External service cần timeout, circuit breaker và bulkhead; Virtual Threads không được dùng để che dependency treo.
- Load test phải đo p95/p99, Hikari active/pending/timeout, DB slow query, GC pause, heap allocation, cache hit ratio và query-count theo `operationName`.

### 15.5 Rollout projection cho các màn hình còn thiếu

P0 trước production GraphQL:

- `classWorkspace`: giữ `ClassScoreSummaryProjection`, `ClassRiskSummaryProjection` và query-count integration proof hiện có; bổ sung EXPLAIN/index cho các query join attempt/student/assignment; tiếp tục thêm `AttendanceSessionSummaryProjection` khi có attendance model thật.
- `teacherHomeworkWorkspace`: giữ `TeacherHomeworkSummaryProjection` và `LmsTeacherHomeworkWorkspaceQueryCountIntegrationTest` hiện có; bổ sung pending-review/overdue detail projections, class option projection, EXPLAIN/index review và load/native verification.
- `studentDashboard`: thêm `AnnouncementCardProjection`; giữ `StudentDashboardQueryCountIntegrationTest` hiện có và bổ sung PostgreSQL EXPLAIN/index review cho query theo `tenantId + studentId + status/dueAt`.
- `questionBankWorkspace`: viết DB integration test cho `QuestionUsageCountProjection`, bổ sung index/EXPLAIN cho JSONB/lateral query hoặc cân nhắc bảng join normalized nếu usage query thành hot path.
- `accessManagementWorkspace`: giữ `AccessManagementWorkspaceQueryCountIntegrationTest` hiện có; viết field-level permission matrix cho effective permissions, roles, scopes và bổ sung PostgreSQL EXPLAIN/index review.

P1 chịu tải:

- Query timeout smoke với DB thật.
- Redis/distributed rate limiter và cache stampede guard.
- Load test 100 -> 500 -> 1.000 -> 5.000 concurrency, ghi p95/p99/Hikari/DB/cache/GC.
- Native/AOT smoke với GraphQL schema, Jackson DTO, validation, JPA, PostgreSQL driver và projection records.

### 15.6 Quyết định cuối cùng: Projection áp dụng được hay DTO là đủ?

Projection áp dụng được và phải áp dụng cho các read path nặng. DTO là đủ cho hợp đồng public, validation, versioning và response shape; DTO không đủ để bảo đảm hiệu năng nếu implementation vẫn load full entity, lazy collection hoặc entity graph lớn rồi mới map.

Chuẩn áp dụng trong `erg-spring`:

- REST command/write: dùng Request DTO + Response DTO + transaction/audit/idempotency; không cần Projection.
- REST current/scope/classes nhẹ: DTO là đủ nếu query đã bounded theo principal/scope và không load broad graph.
- Detail nhỏ theo id: Entity -> DTO hoặc `@EntityGraph` có kiểm soát, chỉ sau permission/scope guard.
- List/table/workspace/report/dashboard: bắt buộc Projection/Page/Aggregate Projection -> DTO.
- GraphQL v1: read-projection-only; resolver không gọi repository theo từng nested field, không trả Projection trực tiếp, không load entity rồi map cho workspace nặng.

Projection acceptance gate:

- Mỗi Projection có tên theo mục đích đọc: `*RowProjection`, `*CardProjection`, `*SummaryProjection`, `*OptionProjection`, `*DetailProjection`.
- Query luôn có `tenantId`, scope filter và `Pageable` hoặc hard limit.
- Summary/count/avg/max/min/status tính trong DB bằng aggregate projection, không count collection trong Java.
- Không chứa password, token, credential, raw quiz answer, private note hoặc denied permission field trong projection mặc định.
- Read-model service map Projection -> DTO; controller/resolver chỉ trả DTO.
- Có repository contract test để chứng minh query dùng projection.
- Có integration query-count test để chứng minh SQL count bounded trên dataset nhiều row/nested row.
- Có EXPLAIN/index review trước load test nếu query là hot path hoặc dùng native SQL/JSONB/lateral.

Tác động tới 5.000 concurrency:

- Virtual Threads giúp app xử lý nhiều request blocking hơn, nhưng không tăng số connection DB an toàn.
- Projection giảm thời gian giữ Hikari connection, giảm heap allocation, giảm Hibernate dirty-check/object graph overhead và giảm GC pressure.
- Nếu query count tăng theo số học sinh/bài tập/câu hỏi/quyền nested, endpoint đó chưa được đưa vào load test 5.000 concurrency.
- Hikari pool phải sizing theo DB capacity và số instance, không theo số concurrent user; projection, paging, timeout, Redis/cache stampede guard và read replica strategy mới là lớp chịu tải chính.

## 16. Cap nhat Projection sau attachment va `StudentDashboardQueryCountIntegrationTest` - 2026-06-09 19:03 ICT

Da doc them attachment ve Java 21/25, Spring Boot hien dai, Virtual Threads, N+1, Hikari, Redis/cache stampede, Resilience4j, GC va muc tieu 5.000 concurrent requests. Ket luan bo sung cho plan:

- Projection ap dung duoc va phai ap dung cho read-heavy LMS/LCMS/HocLieu/Elearning. DTO la public contract; Projection la DB/read-model contract.
- Virtual Threads giup tang kha nang phuc vu request blocking o tang app, nhung khong chua N+1, khong lam DB nhanh hon va khong cho phep scale Hikari theo so concurrent user.
- Hikari pool la tai nguyen bounded theo DB capacity va so app instance. Projection, paging, query timeout, cache stampede guard va read replica strategy moi la lop giam thoi gian giu connection.
- Cache Redis/Caffeine chi duoc bat cho read-model da enforce tenant/scope/user; cache key phai gom tenant, principal/scope, operationName/filter/page va co TTL jitter/single-flight/distributed lock khi la hot path.
- Resilience4j timeout/circuit breaker/bulkhead can ap dung cho external calls; Virtual Threads khong duoc dung de che dependency treo.
- ZGC/Shenandoah hoac native image can benchmark rieng. Projection/DTO nho giup giam allocation truoc khi toi uu GC.

Trang thai projection/query-count moi nhat:

| Workspace | Trang thai projection | Query-count proof | Gap con lai |
|---|---|---|---|
| `lms.classWorkspace` | `ClassWorkspaceProjection`, `StudentRosterProjection`, `AssignmentCardProjection`, `ClassScoreSummaryProjection`, `ClassRiskSummaryProjection` | Co `LmsClassWorkspaceQueryCountIntegrationTest`, `prepareStatementCount <= 8`, `entityLoadCount == 0` | Attendance summary, PostgreSQL EXPLAIN/index, load/soak, native smoke |
| `elearning.studentDashboard` | `StudentDashboardAssignmentProjection`, `StudentDashboardScoreSummaryProjection` | Co `StudentDashboardQueryCountIntegrationTest`, `prepareStatementCount <= 6`, `entityLoadCount <= 2` | `AnnouncementCardProjection`, PostgreSQL EXPLAIN/index, load/soak, native smoke |
| `lms.teacherHomeworkWorkspace` | `AssignmentCardProjection`, `TeacherHomeworkSummaryProjection` | Co `LmsTeacherHomeworkWorkspaceQueryCountIntegrationTest`, `prepareStatementCount <= 4`, `entityLoadCount == 0` | Class options, pending-review/overdue detail projections, EXPLAIN/index, load/native |
| `lcms.questionBankWorkspace` | Question/options/quiz projections va `QuestionUsageCountProjection` | Chua co DB query-count proof | EXPLAIN/index cho JSONB/lateral, co the normalized join neu usage la hot path |
| `lcms.accessManagementWorkspace` | User row, role/scope options, effective permission batch projection | Co `AccessManagementWorkspaceQueryCountIntegrationTest`, `prepareStatementCount <= 7`, `entityLoadCount == 0` | Field-level matrix, denied override proof, EXPLAIN/index, load/native |
| `lms.learningResourceLibrary` | Resource card, taxonomy/count/progress/recent projections | Co `HocLieuLibraryQueryCountIntegrationTest`, `prepareStatementCount <= 9`, `entityLoadCount == 0` | PostgreSQL EXPLAIN/index, cache stampede guard, load/soak, native smoke |

Rule chot cho Projection:

- Repository projection query phai filter `tenantId` va scope ngay trong SQL/JPQL, khong doc rong roi filter bang Java.
- Page/card/list/summary/report/read workspace bat buoc dung Projection hoac aggregate Projection truoc khi map sang DTO.
- Controller/resolver khong expose Projection truc tiep; DTO/response record la hop dong public duy nhat.
- Khong dung `FetchType.EAGER` de chua N+1; `@EntityGraph` chi dung cho detail read nho va co review.
- `hibernate.default_batch_fetch_size=50-100` chi la safety net cho lazy relation con sot.
- GraphQL resolver khong goi repository theo tung nested field; neu nested data con lai bat buoc thi dung DataLoader/batch `IN (...)` sau base projection.
- Projection khong chua password, token, credential, raw quiz answer, private note hoac denied permission fields.
- Workspace chua co query-count proof bounded khong duoc dua vao load test 5.000 concurrency nhu mot contract production.

Cap nhat question bank gate:

- `QuestionBankWorkspaceQueryCountIntegrationTest` la Postgres-only gate ve ban chat vi `QuizRepository.countQuestionUsageByQuestionIds(...)` dung PostgreSQL JSONB/lateral.
- Tren H2, test phai skip co ly do ro thay vi fail sai dialect hoac ghi nhan proof gia.
- Query-count proof cua `lcms.questionBankWorkspace` chi duoc tinh khi test chay tren PostgreSQL/Testcontainers/staging DB va co `prepareStatementCount` bounded.
- Neu `QuestionUsageCountProjection` thanh hot path, uu tien normalized join/read table cho quiz-question usage de co index `tenant_id, question_id`, EXPLAIN ro va portability tot hon.

## 17. Chot bo sung sau pasted text: Projection la enterprise read contract, DTO khong du

Da doc them pasted text ve Java hien dai, Spring Boot, Virtual Threads, N+1, HikariCP, Redis/cache stampede, Resilience4j, GC va muc tieu 5.000 concurrent requests. Ket luan can ghi vao plan nhu mot rule kien truc bat buoc:

- DTO la hop dong public API: REST/GraphQL response shape, validation, compatibility va client contract.
- Projection la hop dong doc du lieu noi bo: query select cot nao, aggregate nao tinh o DB, paging/scope/index nao bat buoc.
- DTO khong thay the Projection. Neu service load full Entity/lazy collection roi map sang DTO thi van co N+1, ton heap, ton thoi gian giu Hikari connection va khong dat chuan 5.000 concurrency.
- Projection khong thay the DTO. Projection khong duoc expose truc tiep ra controller/resolver vi no gan voi schema DB/internal naming va co the lam lo sensitive/internal fields.
- Read-model service la lop ghep: `Repository Projection -> mapper/read-model -> Response DTO`.

### 17.1 Chon Projection, DTO, EntityGraph, Batch Fetch theo dung tinh huong

| Tinh huong | Chuan ap dung |
|---|---|
| REST command/write: create/update/delete/submit/import/upload | Request DTO + Response DTO + service transaction/audit/idempotency; khong dung Projection lam public contract |
| REST current/scope/classes nhe | DTO la du neu repository da bounded theo principal/tenant/scope va khong load broad graph |
| Detail nho theo id | Entity -> DTO hoac `@EntityGraph` co chu dich, chi sau permission/scope guard |
| List/table paginated | Row/Card Projection + `Pageable`/hard limit -> DTO |
| Dashboard/workspace/report/GraphQL read | Base page Projection + aggregate Projection + batch `IN (...)` query -> Workspace DTO |
| Count/summary/metric | Aggregate Projection bat buoc; khong load collection roi count trong Java |
| Nested GraphQL field con lai | DataLoader/batch query chi sau khi base row set da bounded bang Projection |

Khong dung `FetchType.EAGER` de chua N+1. `@EntityGraph` chi duoc dung cho detail read nho, relation co dinh va co review query plan. `hibernate.default_batch_fetch_size=50-100` chi la safety net cho lazy relation con sot, khong phai giai phap chinh cho workspace nang.

### 17.2 Rule Projection cho LMS/LCMS/HocLieu/Elearning

- Projection phai dat ten theo muc dich doc, khong theo entity thuan: `*RowProjection`, `*CardProjection`, `*SummaryProjection`, `*OptionProjection`, `*DetailProjection`, `*WorkspaceProjection`.
- Repository projection query phai filter `tenantId`, school/class/user scope va permission-relevant condition ngay trong SQL/JPQL.
- Khong doc rong roi filter bang Java, dac biet voi tenant, school, class, student, role, permission.
- Query list/workspace phai co `Pageable`, default page size 20-50 va hard max 200.
- Aggregate/counter/status phai tinh trong DB bang projection query; khong lap qua entity/collection de dem trong Java.
- Projection mac dinh khong chua password, token, credential, raw quiz answer, private note, denied permission field, session secret, signed URL hoac PII khong can cho man hinh.
- Neu field nhay cam can hien thi, phai co field-level gate tai service/read-model va test security rieng.
- GraphQL resolver khong duoc goi repository theo tung nested field. Resolver chi doc context, check portal/operation guard, roi goi read-model service.

### 17.3 Projection va 5.000 concurrency

Virtual Threads la nen tot cho Spring MVC synchronous vi giam ap luc OS thread va giu code de maintain. Nhung Virtual Threads khong lam cac viec sau:

- khong lam DB nhanh hon;
- khong cho phep mo 5.000 Hikari connections;
- khong chua N+1;
- khong giam object graph/heap allocation neu van load full Entity;
- khong bao ve downstream service bi treo.

Vi vay muc tieu 5.000 concurrent requests phai duoc hieu la kha nang phuc vu o tang app sau khi read path da bounded. Thu tu enterprise dung la:

1. Security/scope authoritative o service layer.
2. DTO/response typed, khong leak entity/internal fields.
3. Projection/page/aggregate query cho moi read-heavy workspace.
4. Query-count integration proof tren dataset co nested rows.
5. PostgreSQL EXPLAIN/index review cho hot path va native SQL/JSONB/lateral.
6. Query timeout, transaction read-only va Hikari metrics.
7. Cache Redis/Caffeine co key theo tenant/user/scope/operation/filter/page, TTL jitter va stampede guard.
8. Resilience4j timeout/circuit breaker/bulkhead cho external calls.
9. Load/soak 100 -> 500 -> 1.000 -> 5.000 voi p95/p99, Hikari active/pending/timeout, DB slow query, GC/heap, cache hit ratio.
10. Native/AOT smoke cho GraphQL schema/resources, Jackson DTO, projection records, validation, JPA va PostgreSQL driver.

Hikari pool phai sizing theo DB capacity, so app instance va query latency, khong theo so concurrent user. Projection giam thoi gian giu connection, giam entity materialization, giam heap allocation va lam Virtual Threads co y nghia trong thuc te.

### 17.4 Acceptance gate moi cho moi workspace nang

Mot workspace LMS/LCMS/HocLieu/Elearning chi duoc coi la dat chuan enterprise khi co du:

- public DTO/GraphQL type typed va version-friendly;
- projection query cho base list/card/page;
- aggregate projection cho counters/summary;
- permission/scope filter tai repository/service, khong tin FE/header;
- field-level security test cho field nhay cam;
- repository contract test chung minh dung projection, khong broad entity load;
- SQL query-count integration test voi Hibernate statistics hoac datasource proxy;
- `entityLoadCount == 0` cho read path projection neu workspace khong can profile entity nho;
- PostgreSQL EXPLAIN/index evidence cho hot query;
- pagination hard limit va query timeout;
- cache/rate-limit policy neu la hot path;
- native/AOT smoke khi vao phase native image.

Workspace chua co query-count proof bounded va EXPLAIN/index review khong duoc dua vao load test 5.000 concurrency nhu mot production contract.

## 18. Bo sung sau attachment: chuan van hanh Projection trong source

Attachment ve Java 21/25, Spring Boot hien dai, Virtual Threads va 5.000 concurrency khong chi la goi y toi uu. No la ly do phai bien Projection thanh mot chuan kien truc bat buoc trong `erg-spring`: ung dung co the nhan nhieu request hon bang Virtual Threads, nhung DB/Hikari/heap van se sap neu moi man hinh nang con load Entity rong, lazy relation hoac nested repository call.

### 18.1 Kieu Projection nen dung

| Kieu Projection | Khi nao dung | Rule enterprise |
|---|---|---|
| Interface projection dong | JPQL/Spring Data query don gian, field it, mapping ro | Chi dung closed projection; khong dung SpEL/open projection cho hot path vi kho do query shape va native image/reflection |
| Record/class constructor projection | Query quan trong, native-image friendly, DTO mapping on dinh | Uu tien cho read-model moi vi shape ro, immutable, de test va de AOT review |
| Aggregate projection | Count/sum/avg/min/max/status counters | Bat buoc cho summary/dashboard/report; khong dem collection trong Java |
| Native SQL projection | PostgreSQL-specific query nhu JSONB/lateral/report nang | Phai co PostgreSQL integration test, EXPLAIN ANALYZE, index review va fallback/normalization plan |
| Database view/materialized view/read table | Hot path co join/aggregate lap lai nhieu lan | Dung khi EXPLAIN cho thay query runtime/lock/CPU cao; update bang job/event/outbox neu can |
| EntityGraph | Detail nho theo id, relation co dinh | Khong dung cho workspace/list/report; phai co permission guard truoc va query-count test neu relation co the lon |

Quy tac chot:

- Uu tien record/class projection cho query moi can on dinh lau dai va native image.
- Interface projection chap nhan cho query nho, nhung khong de no thanh public API contract.
- Khong dung open projection/SpEL projection trong hot path GraphQL vi kho kiem soat SQL, kho AOT va de sinh hidden cost.
- Khong tra projection truc tiep ra controller/resolver. Public contract van la DTO/response record.

### 18.2 Projection implementation checklist

Moi repository projection query moi phai tra loi du cac cau hoi sau truoc khi merge:

1. Query co filter `tenantId` va scope authoritative ngay trong SQL/JPQL chua?
2. Query co `Pageable`, default size va hard max neu tra list/table/card chua?
3. Query co chi select cac cot man hinh can khong, hay van select Entity?
4. Query co aggregate trong DB thay vi load collection ve Java khong?
5. Query co index tuong ung theo thu tu filter/sort/join khong?
6. Query-count test co dataset nhieu nested rows de bat N+1 khong?
7. `entityLoadCount` co bang 0 voi read path projection khong?
8. Field nhay cam co bi lo qua projection/DTO/cache/log khong?
9. Cache key neu co da gom tenant/user/scope/operation/filter/page chua?
10. Query co phu hop native image: record/constructor ro, it reflection/dynamic map khong?

### 18.3 Bo sung projection theo man hinh

| Workspace/query | Projection phai uu tien them | Ly do |
|---|---|---|
| `lms.assignmentProgressWorkspace` | `AssignmentDetailProjection`, `RecipientProgressProjection`, `AttemptScoreProjection`, `MissingLateStudentProjection`, `AssignmentProgressSummaryProjection` | Tach khoi REST progress runtime, khong tra raw answers, khong query per student/attempt |
| `lms.classWorkspace` | `ClassAttendanceSummaryProjection`, `AttendanceSessionSummaryProjection`, `StudentRiskProjection` neu can drill-down | Hoan thien attendance/risk ma khong load attendance sessions/students trong loop |
| `lms.teacherHomeworkWorkspace` | `ClassOptionProjection`, `PendingReviewProjection`, `OverdueStudentProjection` | Them picker/detail widgets nhung giu query bounded |
| `elearning.studentDashboard` | `AnnouncementCardProjection`, `StudentNextActionProjection` neu next action vuot assignment slice | Bo sung announcement/action ma van self-scope theo student |
| `lcms.questionBankWorkspace` | Normalized `QuestionUsageProjection` hoac read table `question_usage_summary` neu JSONB/lateral la hot path | JSONB/lateral can Postgres proof; neu tai cao nen co bang read/index `tenant_id, question_id` |
| `lcms.accessManagementWorkspace` | `EffectivePermissionProjection`, `DeniedPermissionProjection`, `AccessWarningProjection` | Hoan thien field-level security/deny override va canh bao scope |
| `lms.learningResourceLibrary` | `ResourceAccessProjection`, `ResourceRecommendationProjection` neu them recommendation | Cache/read-model phai scope-aware, khong de resource global leak school/role |

### 18.4 Quan he voi Hikari, cache, Virtual Threads va native image

- Virtual Threads giam ap luc OS thread, nhung khong duoc tinh la bang chung performance neu SQL count con tang theo row.
- Projection giam thoi gian giu Hikari connection. Day la dieu kien truoc khi load test 1.000/5.000 concurrency.
- Hikari `maximumPoolSize` phai tinh theo DB capacity, so instance, latency va RPS. Khong tang pool de che query xau.
- Caffeine/Redis chi cache ket qua read-model da enforce permission/scope. Cache khong thay permission check.
- Hot cache phai co TTL jitter va single-flight/distributed lock de chong stampede.
- Native image uu tien record DTO/record projection, tranh `Map<String,Object>` va dynamic reflection o public/read-heavy path.
- Sau khi bat native phase, moi GraphQL workspace phai co smoke test serialize/deserialize DTO, load schema resource, validation va repository projection.

### 18.5 Definition of done cho Projection enterprise

Mot projection/read-model moi chi duoc coi la enterprise-ready khi:

- Co DTO public typed rieng va mapper tu projection sang DTO.
- Co permission/scope guard tai service/read-model, khong tin FE/header.
- Co repository query bounded theo tenant/scope/page.
- Co query-count integration test va repository contract test.
- Co PostgreSQL EXPLAIN/index evidence neu la hot path/native SQL.
- Co sensitive-field test neu lien quan user/role/score/raw answer/credential.
- Co cache/rate-limit policy neu duoc goi thuong xuyen.
- Co native/AOT smoke trong phase native rollout.

## 19. Cap nhat 2026-06-09 20:40 ICT: first slice `lms.assignmentProgressWorkspace`

Da bo sung first implementation cho GraphQL read-projection-only query `lms.assignmentProgressWorkspace(input)`.

Contract hien tai:

- Input: `tenantId`, `assignmentId`, `attemptStatus`, `page`, `size`.
- Output: assignment detail, progress summary, attempts page.
- DTO public: `AssignmentProgressWorkspaceResponse`, `AssignmentDetailResponse`, `AssignmentProgressSummaryResponse`, `AssignmentAttemptSummaryResponse`.
- Projection/read-model: `AssignmentDetailProjection`, `AssignmentProgressProjection`, `AttemptSummaryProjection`.
- Repository shape: assignment detail projection + summary aggregate + paged attempt summary projection.
- Khong tra raw answers, attempt events, client payload, package hash, teacher note hoac `studentIds` JSON.

Security rule:

- Permission: `lms.assignment.read` hoac `lms.progress.read` hoac `lms.manage` hoac wildcard.
- Tenant luon verify theo principal, client khong duoc spoof `tenantId`.
- Deny override thang grant/wildcard.
- Assignment owner (`assignedBy == principal.userId`) duoc xem.
- Neu khong phai owner thi principal phai co wildcard hoac `managedClassIds` chua `assignment.academicClassId`.

Evidence da co:

- `LmsHomeworkWorkspaceGraphQlControllerTest`: tenant spoof, missing permission, denied permission, owner scope, response class-scope denial.
- `LmsReadModelServiceTest`: service map projection -> DTO, clamp page size 50, khong nested lookup.
- `AssignmentRepositoryReadModelContractTest`: assignment detail projection khong select entity/teacherNote/studentIds.
- `AttemptRepositoryReadModelContractTest`: attempt summary page projection khong select raw answers/events/client/packageHash.
- `LmsAssignmentProgressWorkspaceQueryCountIntegrationTest`: first SQL query-count proof bang H2/Flyway/Hibernate statistics, `prepareStatementCount <= 4`, `entityLoadCount == 0`.

Gap con lai:

- Chua co recipient roster projection.
- Chua co missing/late student projection.
- Chua co latest/best attempt per recipient projection.
- Chua co PostgreSQL EXPLAIN/index evidence, load/soak va native smoke.
