# Báo cáo enterprise LMS/LCMS GraphQL, security, projection và chịu tải

Ngày cập nhật: 2026-06-09 19:26 ICT

Tài liệu liên quan:

- Plan: `docs/0806/lms-lcms-graphql-security-enterprise-plan.md`
- Skill/rules: `docs/0806/erg-spring-enterprise-skill/`

## 0. Checkpoint kiem tra lai ngay 2026-06-09 20:55 ICT

Ket luan sau khi doi chieu lai plan/report: chua con thieu phan "lap ke hoach" lon nao ve LMS/LCMS/Elearning GraphQL, security, projection, DTO/response, N+1, Java 25/GraalVM va 5.000 concurrency. Tai lieu da co du khung enterprise. Tuy nhien source moi dat muc first-slice/read-model proof cho mot so workspace, chua dat production-ready enterprise.

Da xong:

- Endpoint GraphQL chung `POST /api/v1/graphql` va namespace `lms`, `lcms`, `crm`, `elearning`.
- GraphQL guard/operation guard/execution guard/deadline va in-memory rate limit.
- Rule DTO vs Projection da chot: DTO la public contract, Projection la DB/read-model contract, khong expose projection truc tiep.
- First implementation/proof cho `learningResourceLibrary`, `classWorkspace`, `teacherHomeworkWorkspace`, `assignmentProgressWorkspace`, `studentDashboard`, `accessManagementWorkspace`.
- `questionBankWorkspace` da co first projection va batch usage projection, nhung PostgreSQL proof van la blocker.

Con lai:

- `lcms.questionBankWorkspace`: PostgreSQL/Testcontainers query-count proof, `EXPLAIN ANALYZE`, index review cho JSONB/lateral usage query.
- `lms.assignmentProgressWorkspace`: recipient roster, missing/late students, latest/best attempt per recipient projections.
- `lms.classWorkspace`: attendance summary/projection va PostgreSQL EXPLAIN/index.
- `lms.teacherHomeworkWorkspace`: class options, pending-review detail, overdue detail projections.
- `elearning.studentDashboard`: announcement projection.
- Field-level security matrix sau hon cho tung workspace va sensitive fields.
- Redis/gateway distributed rate limit, distributed cache/stampede guard, slow-query smoke, load/soak 100 -> 500 -> 1.000 -> 5.000 concurrency, AOT/native smoke.

## 1. Tóm tắt điều hành

Milestone hiện tại đã chuyển từ kế hoạch sang nền source chạy được cho phần GraphQL/read-model đầu tiên của `erg-spring`.

Đính chính trạng thái source hiện tại:

- `lms.classWorkspace(input)` không còn chỉ là class info + roster; source đã có thêm assignment summary page, `scoreSummary` first implementation bằng `ClassScoreSummaryProjection`, `riskSummary` first implementation bằng `ClassRiskSummaryProjection` và query-count integration proof đầu tiên bằng Hibernate statistics. `scoreSummary` là nullable field và chỉ include khi principal có `lms.grade.read`, `lms.manage` hoặc wildcard; `riskSummary` chỉ include khi principal có `lms.progress.read`, `lms.grade.read`, `lms.manage` hoặc wildcard. Vẫn thiếu attendance summary, EXPLAIN/index evidence, load/soak proof và native smoke.
- `lms.teacherHomeworkWorkspace(input)` đã là first-slice read-only, có summary aggregate projection cho active/overdue/pending-review counts và có query-count integration proof đầu tiên bằng `LmsTeacherHomeworkWorkspaceQueryCountIntegrationTest`.
- `elearning.studentDashboard(input)` đã là first-slice read-only theo self-scope, có assignment projection slice/counters/next actions, score summary projection và query-count integration proof đầu tiên bằng Hibernate statistics; vẫn thiếu announcement projection, PostgreSQL EXPLAIN/index evidence, load/soak proof và native smoke.
- `lcms.questionBankWorkspace(input)` và `lcms.accessManagementWorkspace(input)` đã vượt qua shell rỗng: có projection-backed first implementation. `accessManagementWorkspace` đã có query-count integration proof đầu tiên; `questionBankWorkspace` vẫn chưa có DB/query-count proof.
- Các bằng chứng hiện có chủ yếu là contract/unit test và bounded read-model shape; chúng chưa thay thế được SQL query-count, load, native hay slow-query smoke.

Đã có trong source hiện tại:

- Đã spawn đủ 4 worker mới để xử lý song song: HocLieu library, GraphQL guard, LMS class workspace, audit/report.
- Đã có endpoint GraphQL chung `POST /api/v1/graphql`.
- Schema root đã có namespace `lms`, `lcms`, `crm`, `elearning`.
- GraphQL guard có request-level guard: POST-only, JSON-only, bắt buộc `query`, bắt buộc `operationName`, giới hạn query length, depth heuristic, alias heuristic, chặn introspection mặc định, chặn sensitive variables.
- GraphQL execution guard có runtime instrumentation cho max depth, max complexity và execution deadline.
- GraphQL rate limit đã có in-memory implementation theo tenant/user/operationName.
- JDBC/JPA timeout đã có cấu hình nền: `hibernate.jdbc.timeout` và `jakarta.persistence.query.timeout`.
- `lms.learningResourceLibrary(input)` đã có đủ shape đọc chính: taxonomy tree, resource cards, resource counts, progress, recent opened.
- `lms.classWorkspace(input)` đã có slice đầu tiên mở rộng: class info + roster học sinh có pagination + assignment summary page có pagination + score summary aggregate projection có permission gate.
- DTO vẫn là public contract; projection/read-model là chuẩn bắt buộc cho read-heavy screens.
- Focused suite tổng hợp đã xanh ở một mốc xác minh chính: `Tests run: 56, Failures: 0, Errors: 0, Skipped: 0`; các đợt bổ sung sau đó vẫn tiếp tục xanh ở mốc 34 tests cho những slice mới.

Chưa được coi là production-ready enterprise cho mục tiêu 5.000 concurrency vì còn thiếu:

- Query-count integration tests cho tất cả workspace nặng để chứng minh không N+1 ở mức JPA/DB thật. Hiện đã có first proof cho `classWorkspace`, `studentDashboard`, `teacherHomeworkWorkspace` và `accessManagementWorkspace`; các workspace còn lại vẫn cần.
- Load/soak tests 100 -> 500 -> 1.000 -> 5.000 concurrency.
- Redis/gateway distributed rate limit cho multi-instance.
- AOT/native smoke cho GraalVM.
- Các workspace nặng tiếp theo đã có first-slice an toàn cho `teacherHomeworkWorkspace`, `assignmentProgressWorkspace`, `elearning.studentDashboard`, `lcms.questionBankWorkspace`, `lcms.accessManagementWorkspace`; LCMS và student dashboard đã có thêm projection-backed data thật hơn shell ban đầu. `assignmentProgressWorkspace` đã tách thành GraphQL workspace riêng ở first slice nhưng vẫn thiếu recipient/missing-late/latest-best projections và production gates.
- `classWorkspace` đã có class info + roster + assignment summary page + score summary + risk summary first implementation và query-count integration proof đầu tiên; vẫn chưa có attendance summary, EXPLAIN/index evidence, load proof và native smoke.

## 2. Kết quả 4 worker

Worker A: `lms.learningResourceLibrary`

- Hoàn thiện projection/read-model shape cho taxonomy, resource counts, progress và recent opened.
- Resource count của taxonomy tôn trọng thêm filter `gradeId`, `categoryId`, `sectionId`, `topicId`.
- Resolver truyền `principal.userId()` và `academicYear` xuống read service.
- `academicYear` lấy từ input, fallback từ principal attributes: `currentAcademicYear`, `academicYear`, `schoolYear`.
- Focused tests của worker: `BUILD SUCCESS`, `Tests run: 26`, `Failures: 0`, `Errors: 0`.

Worker B: GraphQL guard enterprise

- Thêm `GraphQlDeadlineInstrumentation`.
- `GraphQlExecutionGuardConfiguration` chain đủ depth, complexity và deadline.
- Thêm tests cho deadline trước/sau data fetcher và disabled timeout.
- Focused tests của worker: `BUILD SUCCESS`, `Tests run: 21`, `Failures: 0`, `Errors: 0`.

Worker C: `lms.classWorkspace`

- Siết scope trước khi đọc DB.
- Nếu principal chỉ có `managedEducationUnitIds` thì request phải gửi `schoolId` thuộc scope.
- Nếu principal có `managedClassIds` đúng class thì được đọc class đó, và response scope được kiểm tra lại theo class/school.
- Thêm repository contract test cho roster projection.
- Worker bị chặn lúc đầu bởi compile blocker ở HocLieu, sau đó blocker đã được sửa ở luồng chính và focused suite tổng hợp đã xanh.

Worker D: audit/report readiness

- Cập nhật audit test để phản ánh runtime guards hiện tại.
- Ghi nhận risks còn lại: in-memory rate limiter, thiếu query-count integration test, thiếu load/native evidence, `classWorkspace` đã có assignment summary page nhưng vẫn chưa chạm ngưỡng enterprise.
- Worker bị chặn lúc đầu bởi compile blocker ở HocLieu, sau đó blocker đã được sửa ở luồng chính.

## 3. GraphQL platform hiện tại

Files chính:

- `src/main/resources/graphql/schema.graphqls`
- `src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlShellController.java`
- `src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlRequestContext.java`
- `src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlRequestContextFactory.java`
- `src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlContextInterceptor.java`
- `src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlGuardFilter.java`
- `src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlRateLimiter.java`
- `src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlExecutionGuardConfiguration.java`
- `src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlDeadlineInstrumentation.java`
- `src/main/java/com/erg/ergspringboot/platform/graphql/ErgGraphQlProperties.java`

Đã tốt:

- `X-Tenant-ID` và `X-Portal` chỉ là requested context, không phải nguồn sự thật.
- Tenant thực tế lấy từ `ErgPrincipal`.
- Portal request phải thuộc danh sách portal principal được access.
- GraphQL context không lưu raw bearer token, chỉ lưu `authorizationPresent`.
- `/api/v1/graphql` yêu cầu authenticated principal ở route-level.
- Resolver/service chịu trách nhiệm field-level permission/scope.
- Introspection và GraphiQL mặc định tắt.

Rủi ro còn lại:

- `GraphQlDeadlineInstrumentation` chỉ abort trước/sau data fetcher hoặc khi chuyển field; nó không hủy được JDBC call đang treo giữa chừng.
- `hibernate.jdbc.timeout` và `jakarta.persistence.query.timeout` đã có trong config, nhưng vẫn cần integration test/DB smoke để chứng minh driver và database áp dụng đúng cho query thật.
- Rate limit in-memory chỉ đúng cho single instance/dev/staging nhỏ; production multi-node cần Redis/token bucket hoặc gateway limiter.
- Complexity guard dùng built-in GraphQL Java, cần tune theo operation khi schema lớn hơn.

## 4. `lms.learningResourceLibrary(input)`

Files chính:

- `src/main/java/com/erg/ergspringboot/platform/graphql/LmsLearningResourceGraphQlController.java`
- `src/main/java/com/erg/ergspringboot/service/hoclieu/readmodel/HocLieuLibraryReadService.java`
- `src/main/java/com/erg/ergspringboot/common/response/hoclieu/HocLieuLibraryResponses.java`
- `src/main/java/com/erg/ergspringboot/repository/hoclieu/ResourceRecordRepository.java`
- `src/main/java/com/erg/ergspringboot/repository/hoclieu/TaxonomyNodeRepository.java`
- `src/main/java/com/erg/ergspringboot/repository/hoclieu/HocLieuProgressStateRepository.java`
- `src/main/java/com/erg/ergspringboot/repository/hoclieu/projection/*`

Đã có:

- Resource cards qua `HocLieuResourceCardProjection`.
- Taxonomy tree qua `HocLieuTaxonomyNodeProjection`.
- Child/resource counts qua `HocLieuTaxonomyCountProjection`.
- Progress qua `HocLieuResourceProgressProjection`.
- Recent opened qua `HocLieuRecentResourceProjection`.
- Default page size 20, hard max 50.
- Progress limit 50, recent limit 10.
- Resolver truyền userId + academicYear để progress/recent không còn luôn rỗng.
- Public response là DTO, không expose entity/projection.

Security đã có:

- Permission cho phép: `lms.resource.read`, `hoclieu.resource.read`, `hoclieu.manage`, `*`.
- Denied permission thắng granted permission.
- Tenant spoof bị reject.
- Empty managed education-unit scope bị deny khi request scope cụ thể.
- Unmanaged education-unit scope bị deny.

Còn thiếu:

- Integration test với DB thật để validate JPQL aggregate/projection trên Postgres/H2.
- Query-count test bằng Hibernate statistics hoặc datasource proxy.
- `EXPLAIN ANALYZE` cho filter phổ biến.

## 5. `lms.classWorkspace(input)`

Files chính:

- `src/main/java/com/erg/ergspringboot/platform/graphql/LmsClassWorkspaceGraphQlController.java`
- `src/main/java/com/erg/ergspringboot/service/lms/readmodel/LmsReadModelService.java`
- `src/main/java/com/erg/ergspringboot/common/response/lms/LmsClassWorkspaceResponses.java`
- `src/main/java/com/erg/ergspringboot/repository/lms/ClassroomRepository.java`
- `src/main/java/com/erg/ergspringboot/repository/lms/StudentRepository.java`
- `src/main/java/com/erg/ergspringboot/repository/lms/AssignmentRepository.java`
- `src/main/java/com/erg/ergspringboot/repository/lms/projection/ClassWorkspaceProjection.java`
- `src/main/java/com/erg/ergspringboot/repository/lms/projection/StudentRosterProjection.java`
- `src/main/java/com/erg/ergspringboot/repository/lms/projection/AssignmentCardProjection.java`

Đã có:

- Class info projection.
- Student roster projection có pagination.
- Assignment summary page có pagination.
- Student page hard max 50.
- Assignment page hard max 50.
- Tenant, class, school và status bound ở repository query.
- Scope check trước khi gọi read-model.
- Response scope check sau khi đọc class.
- Denied permission thắng granted permission.
- Repository contract test chứng minh roster dùng projection, không query full `Student`.

Còn thiếu:

- Score summary.
- Attendance summary.
- Risk flags.
- Query-count integration test cho lớp lớn.
- Product decision về concealment: ngoài scope trả 403 hay 404.

## 6. Security/RBAC/scope audit

Đã được khóa bằng test/source:

- No token vào GraphQL trả 401 với `AUTHENTICATION_REQUIRED`.
- Wrong portal trả 403.
- Tenant header mismatch principal bị reject.
- Missing verified principal bị reject.
- Denied permission override thắng granted permission.
- Empty managed scope deny-by-default khi request scope cụ thể.
- Header tenant/scope không được coi là authoritative.
- Access log có hướng mask sensitive keys.

Test liên quan:

- `AccessControlSecurityTest`
- `GraphQlSecurityGuardContractTest`
- `GraphQlEnterpriseSecurityAuditTest`
- `SecurityHardeningTest`
- `PermissionAuthorizationManagerTest`
- `ApiAccessLogFilterTest`
- `LmsLearningResourceGraphQlControllerTest`
- `LmsClassWorkspaceGraphQlControllerTest`

Rủi ro còn lại:

- GraphQL là một route chung nên route-level permission không đủ; mỗi field/workspace mới phải có service-level permission/scope tests riêng.
- Effective permissions, raw quiz answers, credentials, private notes cần field-level permission matrix riêng.
- Student self-scope cho `elearning.studentDashboard` đã có service/controller test, assignment projection slice, score summary projection và query-count integration proof đầu tiên; vẫn cần announcement projection, PostgreSQL EXPLAIN/index evidence, load/soak và native smoke.
- LCMS access-management đã có field-level gate cho effective permissions ở controller; vẫn cần matrix test sâu hơn cho effective permissions, denied overrides và từng field nhạy cảm.

## 7. DTO, Projection và N+1

Kết luận enterprise:

- DTO là hợp đồng public cho REST/GraphQL.
- Projection là hợp đồng đọc DB/read-model cho màn hình nặng.
- Read-model service map projection sang DTO.
- Controller/resolver không trả projection trực tiếp.
- GraphQL resolver không gọi repository trong loop.
- DataLoader chỉ dùng sau khi base page đã bounded bằng projection.
- Không dùng `FetchType.EAGER` để chữa N+1.

Đã có tiến triển:

- HocLieu resource library dùng projection cho cards/taxonomy/count/progress/recent.
- LMS class workspace dùng projection cho class info và roster.
- Auth/user repository đã có pageable user list projection + role-name batch query.
- Migration `V9__lms_hoclieu_read_model_indexes.sql` đã thêm index đọc cho LMS/HocLieu.

Thiếu bằng chứng:

- Chưa có query-count integration tests cho từng workspace.
- Chưa có benchmark DB hoặc `EXPLAIN ANALYZE`.
- Chưa có tests chứng minh query count không tăng tuyến tính theo nested rows.

Trạng thái query-count proof:

- Hiện có repository/read-model contract tests chứng minh dùng projection, paging và scope-bound query.
- Chưa có test đo số câu SQL thật bằng Hibernate statistics/datasource proxy trên dataset nhiều row.
- Vì vậy chưa được coi là đã chứng minh hết N+1; mới đạt mức giảm rủi ro qua thiết kế projection.

### 7.1 Cập nhật bổ sung sau attachment: Projection là enterprise gate

Đã cập nhật plan với phụ lục mới: `## 10. Phụ lục bổ sung: chuẩn Projection cho DTO, GraphQL và 5.000 concurrency`.

Kết luận bổ sung:

- DTO là public API contract, không phải công cụ tối ưu DB.
- Projection là DB/read-model contract, bắt buộc cho read-heavy LMS/LCMS/HocLieu/Elearning.
- GraphQL resolver phải gọi read-model service đã dùng projection/batch query; không gọi repository theo từng nested field.
- Virtual Threads giúp giảm áp lực OS thread, nhưng không tự chữa N+1, không tăng số DB connection an toàn và không giảm heap allocation nếu vẫn load entity graph lớn.
- Mọi workspace nặng phải có projection, paging, index và query-count integration test trước khi đưa vào load test 5.000 concurrency.

Projection đã được plan hóa theo màn hình:

- LMS: homework list, assignment progress, class workspace, score sheet, attendance, reports, resources.
- LCMS: overview, question bank, access management.
- Elearning: student dashboard, assignment/score/next actions.

Acceptance mới:

- Query có `tenantId` và scope filter ngay trong SQL/JPQL.
- Query có `Pageable`, `LIMIT` hoặc hard max.
- Aggregate tính ở DB, không load collection rồi count trong Java.
- Projection không chứa password/token/raw credential/raw quiz answer/private note nếu không có permission field-level.
- Query count không tăng tuyến tính theo số row/nested row.
- Controller/resolver chỉ trả DTO, không expose projection.

## 8. Chịu tải 5.000 concurrency

Định hướng đúng:

- Virtual Threads phù hợp để giữ Spring MVC synchronous dễ maintain.
- 5.000 concurrency không có nghĩa là 5.000 DB connections.
- Hikari phải bounded theo năng lực DB và số instance.
- Projection + paging + cache + timeout mới là lớp bảo vệ chính.

Đã có foundation:

- `spring.threads.virtual.enabled` là env flag, mặc định false.
- Hikari pool knobs trong `application.yml`.
- JPA/Hibernate timeout config: `hibernate.jdbc.timeout=${SPRING_JPA_HIBERNATE_JDBC_TIMEOUT_SECONDS:5}` và `jakarta.persistence.query.timeout=${SPRING_JPA_QUERY_TIMEOUT_MS:5000}`.
- Actuator/prometheus enabled.
- Resilience4j dependency.
- GraphQL guard knobs.
- In-memory rate limit.
- Log masking config.

Còn thiếu:

- Load test 100/500/1.000 trước khi lên 5.000.
- Metrics p95/p99 theo endpoint/operationName.
- Hikari active/idle/pending/timeout dashboard.
- Redis/distributed cache và stampede guard cho hot read path.
- Distributed rate limit hoặc gateway rate limit.
- Read replica/routing datasource strategy cho read-heavy traffic.
- Async REST job cho report/export nặng.

## 9. Java 25, Spring Boot 4 và GraalVM native readiness

Đã tốt:

- Record DTO/projection đang được dùng nhiều hơn.
- Constructor injection phổ biến.
- `native-maven-plugin` đã có contract test.
- GraphQL schema nằm trong resources rõ ràng.
- `spring.jpa.open-in-view=false` đúng hướng.

Cần tiếp tục:

- Chạy Spring AOT smoke.
- Chạy native compile smoke trong CI/staging khi bắt đầu native phase.
- Kiểm tra reflection/resource hints cho GraphQL schema, Jackson DTO, validation, Hibernate/JPA, PostgreSQL driver, BouncyCastle.
- Giảm `Map<String,Object>` public contract ở các controller/service placeholder.
- Mockito đang cảnh báo dynamic agent trên Java mới; test build enterprise nên cấu hình Mockito agent rõ ràng.
- Lombok cảnh báo Unsafe deprecated trên Java 25; cần theo dõi version hoặc giảm Lombok ở domain critical nếu native phase gặp vấn đề.

## 10. Verification đã chạy

Focused suite tổng hợp đã chạy sau khi tích hợp 4 worker và sửa compile/test blockers:

```powershell
& "C:\Users\VuongTran\.m2\wrapper\dists\apache-maven-3.9.15\0226a00282e400185496f3b60ec5a3f029cbdc6893912937d4876d57695224e1\bin\mvn.cmd" "-Dtest=HocLieuLibraryReadServiceTest,LmsReadModelServiceTest,LmsLearningResourceGraphQlControllerTest,LmsClassWorkspaceGraphQlControllerTest,GraphQlEnterpriseSecurityAuditTest,GraphQlGuardFilterTest,GraphQlRequestContextFactoryTest,StudentRepositoryReadModelContractTest,ClassroomRepositoryReadModelContractTest,GraphQlDeadlineInstrumentationTest,GraphQlExecutionGuardConfigurationTest,GraphQlEndpointConfigurationTest,OpsReadinessConfigurationTest,GraphQlShellContractTest" test
```

Kết quả:

- `BUILD SUCCESS`
- `Tests run: 56`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 0`

Các blocker đã sửa trong luồng chính:

- `GraphQlDeadlineInstrumentation`: record accessor `disabled()` bị trùng method tự định nghĩa; đổi thành `isDisabled()`.
- `HocLieuLibraryReadService`: `taxonomyTree(...)` thiếu tham số `topicId`; đã thêm vào signature và call site.
- `LmsLearningResourceGraphQlControllerTest`: cập nhật constructor input mới có `academicYear`.
- `LmsReadModelServiceTest`: chỉnh total của `PageImpl` theo page 2/size 50.
- `LmsClassWorkspaceGraphQlController`: response scope cho managed class không bị chặn nhầm bởi school scope.

Worker H verification sau khi cập nhật plan/report/skill:

```powershell
& "C:\Users\VuongTran\.m2\wrapper\dists\apache-maven-3.9.15\0226a00282e400185496f3b60ec5a3f029cbdc6893912937d4876d57695224e1\bin\mvn.cmd" "-Dtest=GraphQlEnterpriseSecurityAuditTest" test
```

Kết quả:

- `BUILD SUCCESS`
- `Tests run: 4`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 0`

Audit test hiện pin thêm cấu hình persistence timeout: `SPRING_JPA_HIBERNATE_JDBC_TIMEOUT_SECONDS` và `SPRING_JPA_QUERY_TIMEOUT_MS`.

## 11. Rủi ro còn lại theo mức ưu tiên

P0:

- Thêm query-count integration tests cho `learningResourceLibrary` và `classWorkspace`.
- Thêm integration/smoke test chứng minh JDBC/JPA timeout thật sự abort slow query; nếu dùng PostgreSQL production thì cân nhắc thêm `statement_timeout` ở datasource/session.
- Viết field-level security matrix cho workspace GraphQL tiếp theo.

P1:

- Thay in-memory limiter bằng distributed limiter cho production multi-instance.
- Hoàn thiện `classWorkspace` với score/attendance/risk projections.
- Thêm Redis cache/stampede guard.
- Chạy load test và ghi p95/p99/Hikari metrics.

P2:

- Native/AOT smoke.
- Giảm `Map<String,Object>` ở public contract.
- Permission registry/alias nên quản trị bằng seed/config rõ hơn.

## 12. Hướng tiếp theo

1. Thêm query-count integration test cho `learningResourceLibrary`.
2. Thêm query-count integration test cho `classWorkspace`.
3. Bổ sung JDBC/JPA query timeout và test config.
4. Hoàn thiện `classWorkspace` phần score/attendance/risk.
5. Hoàn thiện `teacherHomeworkWorkspace` bằng projection DB thật cho counters, pending review, overdue và class options; giữ first-slice read-only hiện có.
6. Hoàn thiện `elearning.studentDashboard` bằng announcement projection, PostgreSQL EXPLAIN/index evidence và load/native gates; assignment slice, next actions, score summary và query-count integration proof đầu tiên đã có first implementation.
7. Hoàn thiện `lcms.questionBankWorkspace` và `lcms.accessManagementWorkspace` bằng DB integration/query-count proof, index/EXPLAIN, field-level tests; question/user/role/scope/effective-permission projections đã có first implementation.
8. Thêm load test và dashboard vận hành.
9. Thêm AOT/native smoke.

## 13. Kết luận mới nhất

Milestone GraphQL/read-model/security đầu tiên đã đạt trạng thái source compile và focused tests xanh. `learningResourceLibrary` đã đủ shape đọc chính, `classWorkspace` đã có slice đầu tiên, GraphQL guard đã có depth/complexity/deadline/rate-limit cơ bản, và report/plan/skill đã được cập nhật theo hướng enterprise.

Tuy vậy, mục tiêu enterprise đầy đủ vẫn chưa hoàn tất. Phần còn lại không còn là blocker compile của milestone này, mà là các gate sản xuất thật: query-count proof, load proof, distributed rate-limit/cache, native smoke và hoàn thiện projection DB/counters/options cho các workspace LMS/LCMS/Elearning nặng.

## 14. Cập nhật kiểm chứng mới nhất - 2026-06-09 13:37 ICT

Sau khi kiểm tra lại current worktree, report cần điều chỉnh so với trạng thái cũ:

- `lms.classWorkspace(input)` hiện không chỉ có class info + roster. Source đã có thêm assignment summary page trong DTO, schema, resolver/service và tests.
- `ClassWorkspaceInput` đã có `assignmentStatus`, `assignmentPage`, `assignmentSize`.
- `ClassWorkspace` GraphQL type đã có `assignments: AssignmentSummaryPage!`.
- `LmsReadModelService.classWorkspace(...)` dùng 3 read projection/page calls cố định: class projection, student roster page, assignment card page.
- `HocLieuLibraryReadServiceTest` đã có bounded-call contract cho taxonomy/cards/count/progress/recent với `verifyNoMoreInteractions`.
- Các bằng chứng này vẫn là unit/contract-level proof, chưa phải SQL query-count integration proof bằng Hibernate statistics/datasource proxy.

Focused suite mới đã chạy lại bằng Maven:

```powershell
& "C:\Users\VuongTran\.m2\wrapper\dists\apache-maven-3.9.15\0226a00282e400185496f3b60ec5a3f029cbdc6893912937d4876d57695224e1\bin\mvn.cmd" "-Dtest=HocLieuLibraryReadServiceTest,LmsReadModelServiceTest,LmsLearningResourceGraphQlControllerTest,LmsClassWorkspaceGraphQlControllerTest,GraphQlEnterpriseSecurityAuditTest,GraphQlGuardFilterTest,GraphQlRequestContextFactoryTest,StudentRepositoryReadModelContractTest,ClassroomRepositoryReadModelContractTest,GraphQlDeadlineInstrumentationTest,GraphQlExecutionGuardConfigurationTest,GraphQlEndpointConfigurationTest,OpsReadinessConfigurationTest,GraphQlShellContractTest" test
```

Kết quả:

- `BUILD SUCCESS`
- `Tests run: 56`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 0`

Warning cần đưa vào native/test-build readiness:

- Lombok vẫn phát sinh cảnh báo `sun.misc.Unsafe::objectFieldOffset` bị deprecated trên Java 25.
- Mockito inline mock maker đang self-attach dynamic agent; JDK tương lai sẽ không cho dynamic agent loading mặc định, nên test build enterprise nên cấu hình Mockito agent rõ ràng.

Điều chỉnh remaining gaps:

- `classWorkspace` đã có assignment summary page, score summary first implementation, risk summary first implementation và query-count integration proof đầu tiên, nhưng vẫn thiếu attendance summary, EXPLAIN/index evidence và load/native gates.
- `teacherHomeworkWorkspace` đã có first-slice read-only và summary aggregate projection first implementation; `studentDashboard`, `questionBankWorkspace`, `accessManagementWorkspace` đã có projection-backed first implementation. `studentDashboard` đã có query-count integration proof đầu tiên; các workspace còn lại vẫn thiếu một phần counters/options/report data và query-count integration proof.
- Chưa có SQL query-count integration proof thật cho toàn bộ workspace nặng; hiện mới có first proof cho `classWorkspace` và `studentDashboard`.
- Chưa có slow-query DB timeout smoke.
- Chưa có load/soak test 100 -> 500 -> 1.000 -> 5.000 concurrency.
- Chưa có Redis/distributed limiter/cache và AOT/native smoke.

## 15. Cập nhật kiểm chứng mới nhất - 2026-06-09 16:27 ICT

Sau lượt triển khai song song tiếp theo và tích hợp trong luồng chính:

- Đã thêm `GraphQlAccessSupport` để gom logic trust boundary dùng chung cho GraphQL resolver: principal hiện tại, tenant verification, deny override, permission check, class/school scope, academic year fallback.
- `lms.teacherHomeworkWorkspace(input)` đã có first slice read-only: permission `lms.assignment.read`, tenant lấy từ principal, chống tenant spoof, teacherId lấy từ `ErgPrincipal.userId()`, dùng `AssignmentCardProjection` và summary aggregate projection cho counters, không mutation, không raw answers.
- `elearning.studentDashboard(input)` đã có first slice read-only: root namespace `ElearningQuery`, student self-scope ở service, chống tenant spoof, chống query student khác, có assignment projection slice/counters, không đụng attempt package/sync/submit REST runtime.
- `lcms.questionBankWorkspace(input)` đã có projection-backed first implementation: root namespace `LcmsQuery`, permission `lms.quiz.read` hoặc `hoclieu.manage`, pagination/hard max, question page/options/quiz options, không raw answers/private data.
- `lcms.accessManagementWorkspace(input)` đã có projection-backed first implementation: permission `lms.scope.read`, user rows, role/scope options, `effectivePermissionsIncluded` chỉ bật khi principal có `rbac.permission.read` hoặc `users.manage`, deny override vẫn thắng.

Focused suite mới đã chạy:

```powershell
& "C:\Users\VuongTran\.m2\wrapper\dists\apache-maven-3.9.15\0226a00282e400185496f3b60ec5a3f029cbdc6893912937d4876d57695224e1\bin\mvn.cmd" "-Dtest=GraphQlShellContractTest,LmsHomeworkWorkspaceGraphQlControllerTest,StudentDashboardGraphQlControllerTest,StudentDashboardReadServiceTest,LcmsWorkspaceGraphQlControllerTest,LcmsWorkspaceReadServiceTest,LmsLearningResourceGraphQlControllerTest,LmsClassWorkspaceGraphQlControllerTest" test
```

Kết quả:

- `BUILD SUCCESS`
- `Tests run: 34`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 0`

Trạng thái sau cập nhật này:

- GraphQL root hiện có `lms`, `lcms`, `crm`, `elearning`.
- `lms` đã có `learningResourceLibrary`, `classWorkspace`, `teacherHomeworkWorkspace`.
- `elearning` đã có `studentDashboard`.
- `lcms` đã có `questionBankWorkspace`, `accessManagementWorkspace`.
- Các workspace mới sau `classWorkspace` hiện là first-slice an toàn, trong đó LCMS và student dashboard đã có projection-backed data thật hơn shell ban đầu. `studentDashboard` đã có first query-count integration proof. Chưa được xem là hoàn chỉnh enterprise vì chưa có đủ PostgreSQL index/EXPLAIN, load proof và cache/distributed limiter.

## 16. Cập nhật bổ sung từ attachment projection/concurrency - 2026-06-09

Attachment bổ sung đã được đưa vào plan ở các phần `3.2.1`, `3.2.2` và `10.6`. Kết luận chốt:

- DTO là đủ cho public contract, nhưng không đủ cho hiệu năng nếu phía sau vẫn load full entity rồi map.
- Projection áp dụng được và phải áp dụng cho read-heavy LMS/LCMS/HocLieu/Elearning.
- Virtual Threads giúp giữ mô hình Spring MVC synchronous dễ bảo trì, nhưng không tăng năng lực DB và không chữa N+1.
- Hikari pool không scale theo 5.000 concurrent user; projection, paging, query timeout, Redis/stampede guard và read replica strategy mới là lớp chịu tải chính.
- Mỗi workspace GraphQL nặng phải có projection DB thật, index/EXPLAIN, query-count integration proof và security field-level proof trước khi đưa vào load test 5.000 concurrency.

## 17. Cập nhật kiểm chứng mới nhất - 2026-06-09 16:53 ICT

Sau khi chạy lại focused suite rộng với Maven ngoài sandbox:

```powershell
& "C:\Users\VuongTran\.m2\wrapper\dists\apache-maven-3.9.15\0226a00282e400185496f3b60ec5a3f029cbdc6893912937d4876d57695224e1\bin\mvn.cmd" "-Dtest=HocLieuLibraryReadServiceTest,LmsReadModelServiceTest,LmsLearningResourceGraphQlControllerTest,LmsClassWorkspaceGraphQlControllerTest,LmsHomeworkWorkspaceGraphQlControllerTest,StudentDashboardGraphQlControllerTest,StudentDashboardReadServiceTest,LcmsWorkspaceGraphQlControllerTest,LcmsWorkspaceReadServiceTest,GraphQlEnterpriseSecurityAuditTest,GraphQlGuardFilterTest,GraphQlRequestContextFactoryTest,StudentRepositoryReadModelContractTest,ClassroomRepositoryReadModelContractTest,GraphQlDeadlineInstrumentationTest,GraphQlExecutionGuardConfigurationTest,GraphQlEndpointConfigurationTest,OpsReadinessConfigurationTest,GraphQlShellContractTest" test
```

Kết quả:

- `BUILD SUCCESS`
- `Tests run: 80`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 0`

Nhắc lại những warning còn tồn tại cho native/test-build readiness:

- Mockito inline mock maker vẫn self-attach agent động; build native/test tương lai nên cấu hình agent rõ.
- Lombok từng cảnh báo `sun.misc.Unsafe::objectFieldOffset` trên Java 25 ở lượt recompile trước, cần tiếp tục theo dõi/cập nhật khi làm native/test-build hardening.

Ý nghĩa verification:

- `teacherHomeworkWorkspace` vẫn được xem là first-slice read-only an toàn, nhưng đã có thêm summary aggregate projection cho counters.
- `studentDashboard` đã tiến hơn shell ban đầu: có `StudentDashboardAssignmentProjection`, assignment slice, active assignment counter, pending action counter, `StudentDashboardScoreSummaryProjection` và `StudentDashboardQueryCountIntegrationTest`. Vẫn thiếu announcement projection, PostgreSQL EXPLAIN/index evidence và load/native proof.
- `questionBankWorkspace` đã tiến hơn shell ban đầu: có question page projection, subject/level/topic option projections, quiz option projection và batch `QuestionUsageCountProjection`. Vẫn chưa có DB integration/query-count proof thật; native JSONB/lateral usage query cần EXPLAIN/index review.
- `accessManagementWorkspace` đã tiến hơn shell ban đầu: có user row projection, role/scope options và batch effective permissions khi field-level gate cho phép. Vẫn cần DB integration/query-count proof và field-level matrix đầy đủ hơn.
- Baseline hiện tại xác nhận source compile và suite có thể chạy xanh trên Java 25, nhưng chưa thay thế được query-count integration proof, slow-query smoke, load/soak test và native smoke.

## 18. Cập nhật plan theo attachment projection/concurrency - 2026-06-09 17:20 ICT

Đã cập nhật `lms-lcms-graphql-security-enterprise-plan.md` thêm mục `15. Cập nhật bổ sung từ attachment: Projection, Virtual Threads và 5.000 concurrency`.

Nội dung chốt thêm:

- DTO là public contract; Projection là DB/read-model contract. DTO không thay thế Projection cho màn hình nặng.
- GraphQL v1 vẫn là read-projection-only; resolver phải gọi read-model service đã dùng projection/page/aggregate query.
- Virtual Threads giúp giữ code synchronous dễ bảo trì, nhưng không tăng năng lực DB, không chữa N+1 và không cho phép scale Hikari theo 5.000 concurrent users.
- `studentDashboard` hiện đã có score summary projection và DB query-count integration proof đầu tiên; gap còn lại là announcement projection, PostgreSQL EXPLAIN/index evidence và load/native gates.
- `questionBankWorkspace` hiện đã có usage count batch projection; gap còn lại là DB integration/query-count proof, EXPLAIN/index review cho native JSONB/lateral query và load/native gates.
- Mọi workspace LMS/LCMS/HocLieu/Elearning phải có query-count integration test trước khi đưa vào load test 5.000 concurrency.

## 19. Cập nhật bounded-call proof - 2026-06-09 17:24 ICT

Đã bổ sung thêm contract tests nhỏ để tăng bằng chứng chống N+1 ở mức service/read-model:

- `StudentDashboardReadServiceTest` verify rõ `studentDashboard` chỉ gọi một slice projection, hai counter projection và một score summary projection; không phát sinh repository interaction sau khi map DTO.
- `LcmsWorkspaceReadServiceTest` verify `questionBankWorkspace` bỏ qua `countQuestionUsageByQuestionIds` khi question page rỗng, tránh usage-count query thừa.

Focused suite đã chạy:

```powershell
& "C:\Users\VuongTran\.m2\wrapper\dists\apache-maven-3.9.15\0226a00282e400185496f3b60ec5a3f029cbdc6893912937d4876d57695224e1\bin\mvn.cmd" "-Dtest=StudentDashboardReadServiceTest,LcmsWorkspaceReadServiceTest" test
```

Kết quả:

- `BUILD SUCCESS`
- `Tests run: 10`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 0`

Ý nghĩa: đây vẫn là bounded repository-interaction proof, chưa phải SQL query-count integration proof bằng Hibernate statistics/datasource proxy. Gate SQL thật, EXPLAIN, load/soak và native smoke vẫn còn mở.

## 20. Trạng thái sau mục 19 - 2026-06-09 17:32 ICT

Mục 19 đã tăng bằng chứng bounded-call ở service/read-model, nhưng chưa đóng các gate enterprise còn lại.

Điều chỉnh wording để tránh stale status:

- `studentDashboard` không còn thiếu score summary ở mức first implementation. Source đã có `StudentDashboardScoreSummaryProjection` và `StudentDashboardQueryCountIntegrationTest`; phần còn thiếu là `AnnouncementCardProjection`, PostgreSQL EXPLAIN/index evidence, load/soak proof và native smoke.
- `questionBankWorkspace` không còn là usage placeholder. Source đã có batch `QuestionUsageCountProjection`/`countQuestionUsageByQuestionIds`; phần còn thiếu là DB integration/query-count proof, `EXPLAIN`/index review cho native JSONB/lateral query và load/native gates.
- `classWorkspace` không còn thiếu score summary/risk summary ở mức first implementation và đã có query-count integration proof đầu tiên. Source đã có `ClassScoreSummaryProjection`, `ClassRiskSummaryProjection`, nullable `scoreSummary`/`riskSummary` trong DTO/schema, field-level gates và `LmsClassWorkspaceQueryCountIntegrationTest`; phần còn thiếu là attendance summary, EXPLAIN/index evidence, load/soak proof và native smoke.

Remaining gates chưa complete:

- SQL query-count integration tests bằng Hibernate statistics hoặc datasource proxy cho `learningResourceLibrary`, `classWorkspace`, `teacherHomeworkWorkspace`, `studentDashboard`, `questionBankWorkspace` và `accessManagementWorkspace`.
- Slow-query/JDBC timeout smoke với DB thật.
- `EXPLAIN`/index review cho các query projection/report nặng, đặc biệt native JSONB/lateral usage query của question bank.
- Load/soak 100 -> 500 -> 1.000 -> 5.000 concurrency với p95/p99, Hikari active/pending/timeout, DB slow query, GC và cache metrics.
- Redis/gateway distributed rate limit và cache/stampede guard cho production multi-instance.
- AOT/native smoke cho GraphQL schema, DTO/projection records, validation, JPA và PostgreSQL driver.
- Field-level permission matrix sâu hơn cho effective permissions, raw/sensitive fields và các workspace GraphQL mới.
## 21. Cap nhat teacher homework summary va focused suite - 2026-06-09 17:43 ICT

Da bo sung first implementation cho `lms.teacherHomeworkWorkspace.summary` bang `TeacherHomeworkSummaryProjection`:

- Them `TeacherHomeworkSummaryProjection`.
- `AssignmentRepository.summarizeTeacherHomeworkWorkspace(...)` gom `activeAssignmentCount`, `overdueAssignmentCount`, `pendingReviewAssignmentCount` bang mot aggregate projection query.
- `TeacherHomeworkWorkspaceResponse` va GraphQL schema co them `summary`.
- `LmsReadModelService.teacherHomeworkWorkspace(...)` tra summary + assignment page projection.
- Contract tests kiem tra teacher homework assignment page projection/countQuery va summary aggregate query khong load full entity/join fetch.
- GraphQL/controller/security tests duoc bo sung cho tenant spoofing, missing permission va effective permission gate.
- Ops/logging tests duoc bo sung cho Hikari/actuator/native plugin posture va sensitive query masking.

Focused suite da chay:

```powershell
& "C:\Users\VuongTran\.m2\wrapper\dists\apache-maven-3.9.15\0226a00282e400185496f3b60ec5a3f029cbdc6893912937d4876d57695224e1\bin\mvn.cmd" "-Dtest=LmsReadModelServiceTest,AssignmentRepositoryReadModelContractTest,LcmsWorkspaceGraphQlControllerTest,StudentDashboardGraphQlControllerTest,OpsReadinessConfigurationTest,ApiAccessLogFilterTest,LmsHomeworkWorkspaceGraphQlControllerTest" test
```

Ket qua:

- `BUILD SUCCESS`
- `Tests run: 31`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 0`

Gioi han can ghi ro: summary nay la first implementation va bounded/contract proof. Focused suite 31 tests xanh xac nhan slice hien tai, nhung no chua thay the SQL query-count integration proof bang Hibernate statistics/datasource proxy, chua co EXPLAIN/index evidence va chua dong load/native gates. `teacherHomeworkWorkspace` van con can class options, overdue/pending-review detail lists, DB query-count proof va load/native verification.

## 22. Cập nhật theo attachment Projection/Virtual Threads - 2026-06-09 17:48 ICT

Đã đọc thêm attachment về Java hiện đại, Spring Boot, 5.000 concurrent requests, N+1, Hikari, cache stampede, circuit breaker và GC. Nội dung đã được đưa vào plan tại mục `15.6 Quyết định cuối cùng: Projection áp dụng được hay DTO là đủ?`.

Kết luận chốt:

- Projection áp dụng được và phải áp dụng cho read-heavy LMS/LCMS/HocLieu/Elearning.
- DTO là đủ cho public contract, nhưng không đủ cho hiệu năng nếu phía sau vẫn load full entity/lazy collection rồi map.
- GraphQL v1 phải là read-projection-only: resolver -> access guard -> read-model service -> repository projection/page/aggregate query -> mapper -> DTO.
- Virtual Threads giúp app chịu request concurrency tốt hơn, nhưng không chữa N+1, không tăng DB connection an toàn và không thay thế paging/projection/query-timeout/cache-stampede guard.
- `classWorkspace.scoreSummary` đã có first implementation bằng `ClassScoreSummaryProjection`, được gate bởi `lms.grade.read`/`lms.manage`. `classWorkspace.riskSummary` đã có first implementation bằng `ClassRiskSummaryProjection`, được gate bởi `lms.progress.read`/`lms.grade.read`/`lms.manage`. Query-count integration proof đầu tiên đã có cho slice này. Gap còn lại là attendance summary, EXPLAIN/index evidence, load/soak proof và native smoke.
- Mỗi workspace nặng phải có repository contract test, SQL query-count integration proof, scope/permission proof và EXPLAIN/index review trước khi đưa vào load test 5.000 concurrency.

## 23. Cập nhật classWorkspace risk summary - 2026-06-09 18:19 ICT

Đã bổ sung first implementation cho `lms.classWorkspace.riskSummary` bằng dữ liệu thật từ `Student`, `Assignment` và `Attempt`:

- Thêm `ClassRiskSummaryProjection`.
- Thêm `StudentRepository.summarizeClassWorkspaceRisks(tenantId, academicClassId, lowScoreThreshold)`.
- Query aggregate tính `missingOverdueStudentCount`, `lowScoreStudentCount`, `inactiveStudentCount` ở DB, không load full student list để count trong Java.
- `LmsReadModelService.classWorkspace(...)` map projection sang `ClassRiskSummaryResponse`; DTO/schema có nullable `riskSummary`.
- `LmsClassWorkspaceGraphQlController` chỉ include `riskSummary` khi principal có `lms.progress.read`, `lms.grade.read`, `lms.manage` hoặc wildcard.
- `scoreSummary` vẫn gate bằng `lms.grade.read`/`lms.manage`; role chỉ có `lms.progress.read` có thể xem risk summary mà không tự động xem score summary.
- Sửa thêm lỗi context startup: `GraphQlRateLimiter` là `@Component` nhưng Spring Boot 4 context đang cố instantiate bằng default constructor. Đã thêm constructor injection rõ ràng bằng `@Autowired` để context smoke và các security smoke có thể khởi động GraphQL guard/rate limiter.

Focused suite đã chạy:

```powershell
& "C:\Users\VuongTran\.m2\wrapper\dists\apache-maven-3.9.15\0226a00282e400185496f3b60ec5a3f029cbdc6893912937d4876d57695224e1\bin\mvn.cmd" "-Dtest=LmsReadModelServiceTest,LmsClassWorkspaceGraphQlControllerTest,StudentRepositoryReadModelContractTest,GraphQlShellContractTest" test
```

Kết quả:

- `BUILD SUCCESS`
- `Tests run: 24`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 0`

Context smoke đã chạy sau khi sửa rate limiter:

```powershell
& "C:\Users\VuongTran\.m2\wrapper\dists\apache-maven-3.9.15\0226a00282e400185496f3b60ec5a3f029cbdc6893912937d4876d57695224e1\bin\mvn.cmd" "-Dtest=ErgSpringApplicationTests" test
```

Kết quả:

- `BUILD SUCCESS`
- `Tests run: 1`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 0`

Ý nghĩa thêm: Spring context khởi động được với H2/Flyway, GraphQL schema inspection báo không có unmapped fields, và Spring Data/Hibernate không fail khi parse repository beans hiện tại.

GraphQL guard/rate limiter focused suite đã chạy:

```powershell
& "C:\Users\VuongTran\.m2\wrapper\dists\apache-maven-3.9.15\0226a00282e400185496f3b60ec5a3f029cbdc6893912937d4876d57695224e1\bin\mvn.cmd" "-Dtest=GraphQlGuardFilterTest,GraphQlSecurityGuardContractTest" test
```

Kết quả:

- `BUILD SUCCESS`
- `Tests run: 14`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 0`

Giới hạn cần ghi rõ: đây là first implementation + contract/unit proof. Nó chưa thay thế SQL query-count integration proof bằng Hibernate statistics/datasource proxy, chưa có EXPLAIN/index evidence, chưa có load/soak proof và chưa có native smoke. Attendance summary vẫn chưa triển khai vì source hiện chưa có attendance/session model thật; không nên tạo placeholder để tự đánh dấu hoàn thành.

## 24. Cập nhật query-count integration proof cho classWorkspace - 2026-06-09 18:53 ICT

Đã bổ sung `LmsClassWorkspaceQueryCountIntegrationTest` để nâng bằng chứng từ contract/unit proof lên integration proof đầu tiên cho `lms.classWorkspace`.

Test làm các việc sau:

- Chạy với `@SpringBootTest`, profile `test`, H2/Flyway và Hibernate statistics.
- Seed một lớp thật với 12 học sinh, 5 assignment và nhiều attempt.
- Gọi `LmsReadModelService.classWorkspace(...)` với `includeScoreSummary=true` và `includeRiskSummary=true`.
- Assert response có roster page, assignment page, `scoreSummary` và `riskSummary`.
- Assert `statistics.getPrepareStatementCount() <= 8`, tức query count bounded, không tăng theo số học sinh/assignment/attempt nested trong dataset.
- Assert `statistics.getEntityLoadCount() == 0`, tức read path projection không materialize entity graph.

Focused suite đã chạy:

```powershell
& "C:\Users\VuongTran\.m2\wrapper\dists\apache-maven-3.9.15\0226a00282e400185496f3b60ec5a3f029cbdc6893912937d4876d57695224e1\bin\mvn.cmd" "-Dtest=LmsClassWorkspaceQueryCountIntegrationTest,LmsReadModelServiceTest,LmsClassWorkspaceGraphQlControllerTest,StudentRepositoryReadModelContractTest" test
```

Kết quả:

- `BUILD SUCCESS`
- `Tests run: 22`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 0`

Ý nghĩa: `classWorkspace` hiện đã có bằng chứng query-count integration đầu tiên ở mức H2/Flyway/Hibernate statistics. Đây chưa phải bằng chứng sản xuất đầy đủ: vẫn cần EXPLAIN/index review trên PostgreSQL thật, dataset lớn hơn, p95/p99 load/soak, native smoke và attendance model/projection thật.

## 25. Cap nhat query-count integration proof cho studentDashboard - 2026-06-09 19:03 ICT

Da bo sung va xac nhan `StudentDashboardQueryCountIntegrationTest` cho `elearning.studentDashboard`.

Test lam cac viec sau:

- Chay voi `@SpringBootTest`, profile `test`, H2/Flyway va Hibernate statistics.
- Seed mot student that, mot class, 12 assignments va nhieu attempts.
- Goi `StudentDashboardReadService.studentDashboard(...)` bang student self-scope.
- Assert response co profile dung student, assignment slice bounded o 8 item, next actions bounded, active assignment count la 12 va score summary khac null.
- Assert `statistics.getPrepareStatementCount() <= 6`, tuc query count bounded theo read-model/profile/projection/aggregate, khong tang theo 12 assignments/attempts.
- Assert `statistics.getEntityLoadCount() <= 2`. Hai entity load duoc chap nhan hien tai la profile `User` va `Student`; assignment/attempt graph khong bi materialize.

Focused suite da chay:

```powershell
& "C:\Users\VuongTran\.m2\wrapper\dists\apache-maven-3.9.15\0226a00282e400185496f3b60ec5a3f029cbdc6893912937d4876d57695224e1\bin\mvn.cmd" "-Dtest=StudentDashboardQueryCountIntegrationTest,StudentDashboardReadServiceTest,StudentDashboardGraphQlControllerTest,StudentDashboardReadServiceSecurityTest" test
```

Ket qua:

- `BUILD SUCCESS`
- `Tests run: 12`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 0`

Y nghia: `studentDashboard` hien da co first query-count integration proof o muc H2/Flyway/Hibernate statistics, khong con duoc ghi la thieu query-count proof dau tien. Tuy nhien day chua phai bang chung production day du: van can `AnnouncementCardProjection`, PostgreSQL EXPLAIN/index review, dataset lon hon, load/soak p95/p99, distributed cache/rate-limit va native smoke.

## 26. Cap nhat query-count integration proof va bugfix aggregate cho teacherHomeworkWorkspace - 2026-06-09 19:20 ICT

Da bo sung `LmsTeacherHomeworkWorkspaceQueryCountIntegrationTest` cho `lms.teacherHomeworkWorkspace`.

Trong qua trinh viet integration proof, test phat hien bug that trong `AssignmentRepository.summarizeTeacherHomeworkWorkspace(...)`: active/overdue counters dung `sum(case...)` tren query co `left join Attempt`, nen assignment co nhieu attempts bi dem lap theo so dong attempt. Da sua thanh `count(distinct case when ... then assignment.id else null end)` cho active va overdue counters. Pending-review counter da dung `count(distinct ...)` tu truoc.

Test moi lam cac viec sau:

- Chay voi `@SpringBootTest`, profile `test`, H2/Flyway va Hibernate statistics.
- Seed 4 classes, 16 assignments va nhieu attempts cho cung teacher.
- Goi `LmsReadModelService.teacherHomeworkWorkspace(...)`.
- Assert assignment page size 10 tren total 16.
- Assert summary dung sau bugfix: active `12`, overdue active `3`, pending-review `8`.
- Assert `statistics.getPrepareStatementCount() <= 4`, tuc bounded theo assignment page/count va summary aggregate.
- Assert `statistics.getEntityLoadCount() == 0`, tuc read path projection khong materialize assignment/attempt/class graph.

Focused suite da chay:

```powershell
& "C:\Users\VuongTran\.m2\wrapper\dists\apache-maven-3.9.15\0226a00282e400185496f3b60ec5a3f029cbdc6893912937d4876d57695224e1\bin\mvn.cmd" "-Dtest=LmsTeacherHomeworkWorkspaceQueryCountIntegrationTest,LmsReadModelServiceTest,LmsHomeworkWorkspaceGraphQlControllerTest,AssignmentRepositoryReadModelContractTest" test
```

Ket qua:

- `BUILD SUCCESS`
- `Tests run: 17`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 0`

Y nghia: `teacherHomeworkWorkspace` hien da co first SQL query-count integration proof o muc H2/Flyway/Hibernate statistics, khong con duoc ghi la thieu query-count proof dau tien. Tuy nhien day van chua phai production evidence day du: van can class options, pending-review/overdue detail projections, PostgreSQL EXPLAIN/index review, load/soak p95/p99, distributed cache/rate-limit va native smoke.

## 27. Cap nhat query-count integration proof cho accessManagementWorkspace - 2026-06-09 19:26 ICT

Da bo sung `AccessManagementWorkspaceQueryCountIntegrationTest` cho `lcms.accessManagementWorkspace`.

Test moi lam cac viec sau:

- Chay voi `@SpringBootTest`, profile `test`, H2/Flyway va Hibernate statistics.
- Seed 12 users trong cung tenant, 2 roles, 2 permissions test, 2 education units, user scopes va permission overrides.
- Goi `LcmsWorkspaceReadService.accessManagementWorkspace(...)` voi `includeEffectivePermissions=true`.
- Assert user page size 10 tren total 12.
- Assert role options, scope options va effective permissions duoc map ra DTO.
- Assert `statistics.getPrepareStatementCount() <= 7`, tuc bounded theo user page/count, role options, scope options, role permission batch va override permission batch.
- Assert `statistics.getEntityLoadCount() == 0`, tuc read path projection khong materialize user/role/scope/permission graph.

Focused suite da chay:

```powershell
& "C:\Users\VuongTran\.m2\wrapper\dists\apache-maven-3.9.15\0226a00282e400185496f3b60ec5a3f029cbdc6893912937d4876d57695224e1\bin\mvn.cmd" "-Dtest=AccessManagementWorkspaceQueryCountIntegrationTest,LcmsWorkspaceReadServiceTest,LcmsWorkspaceGraphQlControllerTest" test
```

Ket qua:

- `BUILD SUCCESS`
- `Tests run: 14`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 0`

Y nghia: `accessManagementWorkspace` hien da co first SQL query-count integration proof o muc H2/Flyway/Hibernate statistics. Tuy nhien day van chua phai production evidence day du: van can field-level permission matrix day du hon cho effective permissions/denied override, PostgreSQL EXPLAIN/index review, load/soak p95/p99, distributed cache/rate-limit va native smoke.

## 28. Cap nhat theo pasted text: chot Projection vs DTO va 5.000 concurrency - 2026-06-09

Da doc them pasted text ve Java 21/25, Spring Boot hien dai, Virtual Threads, N+1, HikariCP, Redis/cache stampede, Resilience4j, GC/ZGC va muc tieu 5.000 concurrent requests. Da cap nhat plan chinh them muc `17. Chot bo sung sau pasted text: Projection la enterprise read contract, DTO khong du`.

Ket luan kien truc:

- DTO la public API contract cho REST/GraphQL: response shape, validation, compatibility va client contract.
- Projection la DB/read-model contract: cot nao duoc select, aggregate nao tinh o DB, paging/scope/index nao bat buoc.
- DTO khong du cho man hinh nang neu implementation van load full Entity/lazy collection roi map sang DTO.
- Projection khong duoc expose truc tiep ra controller/resolver; read-model service map Projection -> DTO.
- GraphQL v1 phai giu read-projection-only; resolver khong goi repository theo tung nested field.
- Virtual Threads giup tang kha nang phuc vu request blocking o tang app, nhung khong chua N+1, khong lam DB nhanh hon va khong cho phep scale Hikari theo 5.000 concurrent users.

Rule moi duoc ghi vao plan:

- REST command/write dung Request DTO + Response DTO + transaction/audit/idempotency.
- REST current/scope/classes nhe co the DTO-only neu query da bounded theo principal/scope.
- Detail nho co the Entity -> DTO hoac `@EntityGraph` co review.
- List/table/workspace/report/dashboard bat buoc dung Projection/Page/Aggregate Projection -> DTO.
- Count/summary/metric bat buoc tinh o DB bang aggregate projection.
- DataLoader chi dung sau khi base row set da bounded bang projection.
- `FetchType.EAGER` khong duoc dung de chua N+1; `hibernate.default_batch_fetch_size` chi la safety net.

Acceptance gate moi cho moi workspace nang:

- Co public DTO/GraphQL type typed.
- Co base projection, aggregate projection va batch query bounded.
- Co tenant/school/class/user scope filter tai repository/service.
- Co field-level security test neu co field nhay cam.
- Co repository contract test, SQL query-count integration proof va `entityLoadCount` expectation.
- Co PostgreSQL EXPLAIN/index evidence cho hot query/native SQL/JSONB/lateral.
- Co pagination hard limit, query timeout, cache/rate-limit policy neu la hot path.
- Co native/AOT smoke khi vao phase GraalVM native image.

Canh bao con mo:

- Hikari pool phai sizing theo DB capacity, so app instance va query latency, khong theo so concurrent user.
- Cache Redis/Caffeine phai co key gom tenant/user/scope/operation/filter/page, TTL jitter va stampede guard; cache khong thay permission check.
- Resilience4j timeout/circuit breaker/bulkhead can ap dung cho external calls; Virtual Threads khong duoc dung de che dependency bi treo.
- Workspace chua co query-count proof bounded va PostgreSQL EXPLAIN/index review khong duoc dua vao load test 5.000 concurrency nhu production contract.

## 29. Cap nhat questionBankWorkspace query-count gate: H2 skip, PostgreSQL proof van bat buoc - 2026-06-09

Da kiem tra `QuestionBankWorkspaceQueryCountIntegrationTest` va `QuizRepository.countQuestionUsageByQuestionIds(...)`.

Phat hien ky thuat:

- `questionBankWorkspace` da co `QuestionUsageCountProjection` va batch usage query.
- Usage query production dang dung PostgreSQL native SQL:
  - `jsonb_array_elements_text(...)`
  - `join lateral`
  - cast `'[]'::jsonb`
- H2 trong profile `test` khong ho tro shape SQL nay, nen khong the dung H2 de chung minh query-count production cho usage count.

Da cap nhat test:

- `QuestionBankWorkspaceQueryCountIntegrationTest` khong bi vo gia tri; test van giu seed/query/assert query-count.
- Them assumption kiem tra DB product name la PostgreSQL truoc khi chay.
- Tren H2, test se skip voi ly do ro: H2 khong the prove PostgreSQL JSONB/lateral gate.
- Tren PostgreSQL/Testcontainers/staging DB, test se chay that va moi duoc tinh la query-count proof cho `questionBankWorkspace`.

Ket luan enterprise:

- Khong duoc ghi `questionBankWorkspace` da co SQL query-count proof production.
- Trang thai dung: projection-backed first implementation da co, H2 gate duoc chuyen thanh Postgres-only skip de tranh fail sai dialect, nhung PostgreSQL query-count proof, EXPLAIN/index review va load/native gates van mo.
- Neu usage query tro thanh hot path, nen can nhac normalized read model/join table `quiz_question_usage` hoac `quiz_questions` de:
  - query portable hon;
  - index ro theo `tenant_id, question_id`;
  - giam phu thuoc vao JSONB/lateral cho workspace read nang;
  - de prove query-count/load bang H2 va PostgreSQL hon.

## 30. Cap nhat query-count integration proof cho learningResourceLibrary - 2026-06-09

Da bo sung `HocLieuLibraryQueryCountIntegrationTest` cho `lms.learningResourceLibrary` / `HocLieuLibraryReadService`.

Test moi lam cac viec sau:

- Chay voi `@SpringBootTest`, profile `test`, H2/Flyway va Hibernate statistics.
- Seed 6 taxonomy nodes gom subject/category/section, 16 resources va 8 progress/recent-opened rows.
- Goi `HocLieuLibraryReadService.learningResourceLibrary(...)` voi user/school/academicYear de kich hoat resource cards, taxonomy tree, child counts, resource counts, progress va recent opened.
- Assert resource page size 10 tren total 16.
- Assert taxonomy tree co resource count, progress va recent opened duoc map ra DTO.
- Assert `statistics.getPrepareStatementCount() <= 9`, tuc query count bounded theo page/count/taxonomy/aggregate/progress/recent widgets, khong tang theo 16 resources hay 8 progress rows.
- Assert `statistics.getEntityLoadCount() == 0`, tuc read path projection khong materialize full HocLieu entity graph.

Focused suite da chay:

```powershell
& "C:\Users\VuongTran\.m2\wrapper\dists\apache-maven-3.9.15\0226a00282e400185496f3b60ec5a3f029cbdc6893912937d4876d57695224e1\bin\mvn.cmd" "-Dtest=HocLieuLibraryQueryCountIntegrationTest,HocLieuLibraryReadServiceTest" test
```

Ket qua:

- `BUILD SUCCESS`
- `Tests run: 5`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 0`

Y nghia: `learningResourceLibrary` hien da co first SQL query-count integration proof o muc H2/Flyway/Hibernate statistics. Day van chua phai production evidence day du: van can PostgreSQL EXPLAIN/index review, cache hit/stampede guard proof, load/soak p95/p99, distributed rate-limit/cache va native smoke.

Warning tiep tuc can xu ly trong native/test-build readiness:

- Caffeine cache metrics dang bao nhieu cache khong `recordStats()`, nen dashboard cache hit ratio chua day du cho load proof.
- Mockito inline mock maker van self-attach Java agent dong; Java/JDK tuong lai va native/test phase can cau hinh agent ro.
- Lombok van goi `sun.misc.Unsafe::objectFieldOffset`, can theo doi/cap nhat khi di vao Java 25/GraalVM native hardening.

## 31. Cap nhat field-level security matrix cho accessManagementWorkspace - 2026-06-09

Da bo sung test `accessManagementWorkspaceDeniedWildcardWinsOverWildcardGrantBeforeServiceCall` trong `LcmsWorkspaceGraphQlControllerTest`.

Y nghia security:

- Neu principal co grant wildcard `*` nhung cung co denied permission wildcard `*`, deny phai thang.
- Truong hop nay khong chi an `effectivePermissions`; no phai bi chan truoc khi goi read service vi field-level permission `lms.scope.read`/`*` khong con hop le khi bi denied wildcard.
- Test assert `ForbiddenException` va `verifyNoInteractions(readService)`.

Focused suite da chay:

```powershell
& "C:\Users\VuongTran\.m2\wrapper\dists\apache-maven-3.9.15\0226a00282e400185496f3b60ec5a3f029cbdc6893912937d4876d57695224e1\bin\mvn.cmd" "-Dtest=QuestionBankWorkspaceQueryCountIntegrationTest,LcmsWorkspaceReadServiceTest,LcmsWorkspaceGraphQlControllerTest" test
```

Ket qua:

- `BUILD SUCCESS`
- `Tests run: 15`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 1`

Ghi chu: `Skipped: 1` la `QuestionBankWorkspaceQueryCountIntegrationTest` tren H2 do Postgres-only JSONB/lateral usage query. Day khong dong PostgreSQL production proof cho `questionBankWorkspace`.

## 32. Cap nhat cache observability cho load/soak proof - 2026-06-09

Da xu ly warning trong focused test output: nhieu Caffeine cache khong `recordStats()`, nen Micrometer chi expose `cache.size` va khong co hit/miss/request metrics. Day la blocker cho load proof vi khong the do cache hit ratio/cache stampede behavior.

Thay doi source:

- `CacheConfig` them `.recordStats()` vao tat ca Caffeine cache builder.
- Them `CacheConfigTest` de verify tung cache enterprise record hit/miss stats qua native Caffeine `CacheStats`.
- Cap nhat `OpsReadinessConfigurationTest` de bat buoc source giu `.recordStats()` va cac cache names quan trong.

Focused suite da chay:

```powershell
& "C:\Users\VuongTran\.m2\wrapper\dists\apache-maven-3.9.15\0226a00282e400185496f3b60ec5a3f029cbdc6893912937d4876d57695224e1\bin\mvn.cmd" "-Dtest=CacheConfigTest,OpsReadinessConfigurationTest" test
```

Ket qua:

- `BUILD SUCCESS`
- `Tests run: 5`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 0`

Y nghia:

- Caffeine cache metrics co the phuc vu Prometheus/load dashboard tot hon: request/hit/miss khong con bi mat o local cache layer.
- Day chua dong Redis/distributed cache/rate-limit gate. Production multi-instance van can Redis/gateway limiter, distributed stampede guard/single-flight policy, cache key review theo tenant/user/scope/operation/filter/page va load test that.

## 33. Cap nhat plan theo pasted text: bo sung Projection operation standard - 2026-06-09

Da doc them pasted text ve Java hien dai, Spring Boot, Virtual Threads, N+1, Hikari, Redis/cache stampede, Resilience4j, GC/ZGC va muc tieu 5.000 concurrency. Plan chinh da duoc bo sung muc `18. Bo sung sau attachment: chuan van hanh Projection trong source`.

Noi dung moi da chot:

- DTO la public API contract; Projection la DB/read-model contract. DTO khong du neu source van load full Entity/lazy relation roi map.
- Projection ap dung duoc va phai ap dung cho read-heavy GraphQL/workspace/list/report cua LMS/LCMS/HocLieu/Elearning.
- Uu tien record/class constructor projection cho read-model moi vi ro shape, immutable, de test va than thien voi GraalVM native image.
- Interface projection chi nen dung cho query nho/ro; khong dung open projection/SpEL projection cho hot path.
- Native SQL projection nhu JSONB/lateral bat buoc co PostgreSQL integration test, EXPLAIN ANALYZE, index review va plan normalize/read table neu thanh hot path.
- EntityGraph chi dung cho detail nho theo id, khong phai cach chua N+1 cho workspace/list/report.

Projection checklist da them vao plan:

- Query phai filter `tenantId` va scope authoritative ngay trong SQL/JPQL.
- List/table/card phai co `Pageable`, default size va hard max.
- Summary/counter phai tinh bang aggregate projection trong DB, khong dem collection trong Java.
- Query-count test phai dung dataset co nested rows de bat N+1.
- Read path projection nen co `entityLoadCount == 0` neu khong can profile entity nho.
- Cache key phai gom tenant/user/scope/operation/filter/page va co TTL jitter/single-flight/distributed lock neu la hot path.
- Native phase phai smoke GraphQL schema/resources, Jackson DTO, projection records, validation, JPA va PostgreSQL driver.

Bo sung projection theo man hinh:

- `lms.assignmentProgressWorkspace`: first slice da co `AssignmentDetailProjection`, `AssignmentProgressProjection`, `AttemptSummaryProjection`; tiep theo can `RecipientProgressProjection`, `AttemptScoreProjection`, `MissingLateStudentProjection`.
- `lms.classWorkspace`: can `ClassAttendanceSummaryProjection`, `AttendanceSessionSummaryProjection`, `StudentRiskProjection` neu drill-down.
- `lms.teacherHomeworkWorkspace`: can `ClassOptionProjection`, `PendingReviewProjection`, `OverdueStudentProjection`.
- `elearning.studentDashboard`: can `AnnouncementCardProjection`, `StudentNextActionProjection` neu next action vuot assignment slice.
- `lcms.questionBankWorkspace`: can normalized `QuestionUsageProjection`/read table neu JSONB/lateral usage query thanh hot path.
- `lcms.accessManagementWorkspace`: can `EffectivePermissionProjection`, `DeniedPermissionProjection`, `AccessWarningProjection`.
- `lms.learningResourceLibrary`: can `ResourceAccessProjection`, `ResourceRecommendationProjection` neu them recommendation.

Ket luan: Virtual Threads la nen tang concurrency o app layer, nhung projection/paging/query-timeout/cache-stampede guard moi la lop bao ve DB/Hikari/heap. Workspace nao chua co bounded query-count proof va EXPLAIN/index review thi chua duoc coi la production contract cho muc tieu 5.000 concurrency.

## 34. Hoan thien first slice `lms.assignmentProgressWorkspace` - 2026-06-09 20:40 ICT

Da bo sung GraphQL read-projection-only query `lms.assignmentProgressWorkspace(input)` vao schema va backend.

Thay doi source chinh:

- Them GraphQL field/input/types cho `assignmentProgressWorkspace`.
- Them `AssignmentDetailProjection`.
- Them `AssignmentRepository.findAssignmentDetail(...)` chi select field can hien thi, khong select full `Assignment`.
- Dung lai `AssignmentProgressProjection` cho summary aggregate.
- Dung `AttemptSummaryProjection` va `AttemptRepository.findAssignmentAttemptSummaries(...)` cho attempts page.
- Them DTO public trong `LmsHomeworkWorkspaceResponses`: `AssignmentProgressWorkspaceResponse`, `AssignmentDetailResponse`, `AssignmentProgressSummaryResponse`, `AssignmentAttemptSummaryResponse`.
- `LmsReadModelService.assignmentProgressWorkspace(...)` map projection -> DTO, clamp percent va page size hard max 50.
- `LmsHomeworkWorkspaceGraphQlController.assignmentProgressWorkspace(...)` enforce permission, tenant principal, owner/class scope.

Security/N+1 rule da enforce:

- Permission chap nhan: `lms.assignment.read`, `lms.progress.read`, `lms.manage` hoac wildcard.
- Tenant spoof bi chan truoc service call.
- Denied permission thang wildcard/grant.
- Neu principal khong phai `assignedBy` cua assignment thi bat buoc co class scope trong `managedClassIds` hoac wildcard.
- Response khong tra raw answers, attempt events, client payload, package hash, teacher note, studentIds JSON.
- Resolver khong query nested field theo loop; read-model service dung detail projection + summary aggregate + page projection.

Focused suite da chay:

```powershell
& "C:\Users\VuongTran\.m2\wrapper\dists\apache-maven-3.9.15\0226a00282e400185496f3b60ec5a3f029cbdc6893912937d4876d57695224e1\bin\mvn.cmd" "-Dtest=LmsAssignmentProgressWorkspaceQueryCountIntegrationTest,LmsReadModelServiceTest,LmsHomeworkWorkspaceGraphQlControllerTest,AttemptRepositoryReadModelContractTest,AssignmentRepositoryReadModelContractTest,GraphQlEndpointConfigurationTest" test
```

Ket qua:

- `BUILD SUCCESS`
- `Tests run: 30`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 0`

Bang chung query-count:

- `LmsAssignmentProgressWorkspaceQueryCountIntegrationTest` seed 1 assignment va 40 attempts.
- Assert page attempts 20/40 va counters `submitted=13`, `inProgress=14`, `needsReview=13`.
- Assert `prepareStatementCount <= 4`, gom assignment detail projection, summary aggregate, attempt page va count.
- Assert `entityLoadCount == 0`, tuc read path projection khong materialize assignment/attempt entity graph.

Gap con lai cua workspace nay:

- Chua co recipient roster/missing-late student projections.
- Chua co latest/best attempt per recipient projection.
- Chua co PostgreSQL EXPLAIN/index evidence rieng cho hot path.
- Chua co load/soak va native smoke.
- Day la first implementation + first SQL query-count proof, chua phai production-ready full gate.

## 35. Cap nhat remaining-work P0 sau 4 agent song song - 2026-06-10 09:30 ICT

Da doc va thuc hien cac muc P0 chinh trong `docs/0806/lms-lcms-graphql-security-remaining-work-plan.md`.

Trang thai dieu phoi:

- Da spawn 4 worker song song cho cac slice rieng: GraphQL controller package boundary, `assignmentProgressWorkspace`, `studentDashboard` announcement contract/port, va `questionBankWorkspace` PostgreSQL proof.
- Ca 4 worker da hoan thanh va da duoc close sau khi lay ket qua.
- Luong chinh da ghep, sua conflict/bean wiring va chay lai verification tap trung.

Thay doi source chinh:

- GraphQL package boundary da duoc chuan hoa theo domain-first controller layout:
  - GraphQL shell chung nam trong `src/main/java/com/erg/ergspringboot/controller/platform/graphql`.
  - LMS GraphQL controllers nam trong `controller/lms/graphql`.
  - LCMS GraphQL controller nam trong `controller/lcms/graphql`.
  - Elearning GraphQL controller nam trong `controller/elearning/graphql`.
  - `platform/graphql` chi con infra/support: guard, context, deadline instrumentation, execution guard, rate limiter, request context va access support.
  - `rg` khong con import business controller cu theo `platform.graphql.*Controller`.
- `lms.assignmentProgressWorkspace` da duoc mo rong qua projection/DTO:
  - Public response co `recipients`, `missingLateStudents`, va nested `AssignmentAttemptScore`.
  - Them `RecipientProgressProjection`, `MissingLateStudentProjection`, `AttemptScoreProjection`.
  - `AssignmentRepository` co query recipient roster va missing/late students.
  - `AttemptRepository` co query latest/best attempt score theo recipient.
  - `LmsReadModelService.assignmentProgressWorkspace(...)` gom projection page + latest/best score map theo bounded recipient ids, khong expose raw answers/events/client state/package hash/private note.
- `lms.teacherHomeworkWorkspace` da co detail projection lists:
  - Response co `classOptions`, `pendingReviews`, `overdueStudents`.
  - Them `ClassOptionProjection`, `PendingReviewProjection`, `OverdueStudentProjection`.
  - Them `ClassroomRepository.findTeacherClassOptions(...)`, `AttemptRepository.findTeacherPendingReviews(...)`, `StudentRepository.findTeacherOverdueStudents(...)`.
  - Query-count integration test verify bounded projection queries cho cards, summary, options va detail pages.
- `elearning.studentDashboard` da co announcement public contract va read port:
  - Response co `announcements`.
  - Them `StudentDashboardAnnouncementCardResponse`, `AnnouncementCardProjection`, `StudentDashboardAnnouncementReadPort`.
  - Them fallback `NoPersistentStudentDashboardAnnouncementReadPort` bang `@ConditionalOnMissingBean`, tra empty list khi chua co persistence that.
  - Khong tao table/repository gia vi `model.lms.Announcement` hien chi la plain model, chua co `@Entity` va chua co Flyway table.
- `lcms.questionBankWorkspace` da co PostgreSQL-capable proof test:
  - Them Testcontainers Postgres dependency/BOM trong `pom.xml`.
  - Them `PostgreSqlTestDatabase` de dung `ERG_TEST_POSTGRES_URL`/system properties neu co staging DB, hoac Testcontainers neu Docker available.
  - `QuestionBankWorkspaceQueryCountIntegrationTest` assert PostgreSQL, bounded query count, `entityLoadCount == 0`, va EXPLAIN cho JSONB/lateral usage query.
  - Test duoc guard bang `@EnabledIf("postgresProofDatabaseAvailable")` de local H2 khong tao false-positive.
- GraphQL schema da cap nhat cho:
  - `StudentDashboard.announcements`.
  - `TeacherHomeworkWorkspace.classOptions`, `pendingReviews`, `overdueStudents`.
  - `AssignmentProgressWorkspace.recipients`, `missingLateStudents`, `AssignmentAttemptScore`.

Verification da chay:

```powershell
.\mvnw.cmd -DskipTests compile
```

Ket qua:

- `BUILD SUCCESS`.

Focused suite da chay:

```powershell
.\mvnw.cmd "-Dtest=com.erg.ergspringboot.controller.platform.graphql.GraphQlShellContractTest,com.erg.ergspringboot.controller.lms.graphql.LmsClassWorkspaceGraphQlControllerTest,com.erg.ergspringboot.controller.lms.graphql.LmsHomeworkWorkspaceGraphQlControllerTest,com.erg.ergspringboot.controller.lms.graphql.LmsLearningResourceGraphQlControllerTest,com.erg.ergspringboot.controller.lcms.graphql.LcmsWorkspaceGraphQlControllerTest,com.erg.ergspringboot.controller.elearning.graphql.StudentDashboardGraphQlControllerTest,com.erg.ergspringboot.service.lms.readmodel.LmsReadModelServiceTest,com.erg.ergspringboot.service.lms.readmodel.LmsAssignmentProgressWorkspaceQueryCountIntegrationTest,com.erg.ergspringboot.service.lms.readmodel.LmsTeacherHomeworkWorkspaceQueryCountIntegrationTest,com.erg.ergspringboot.service.elearning.readmodel.StudentDashboardReadServiceTest,com.erg.ergspringboot.service.elearning.readmodel.StudentDashboardQueryCountIntegrationTest,com.erg.ergspringboot.service.lcms.readmodel.QuestionBankWorkspaceQueryCountIntegrationTest,com.erg.ergspringboot.repository.lms.AssignmentRepositoryReadModelContractTest,com.erg.ergspringboot.repository.lms.AttemptRepositoryReadModelContractTest,com.erg.ergspringboot.repository.lms.ClassroomRepositoryReadModelContractTest,com.erg.ergspringboot.repository.lms.StudentRepositoryReadModelContractTest" test
```

Ket qua:

- `BUILD SUCCESS`.
- `Tests run: 73`.
- `Failures: 0`.
- `Errors: 0`.
- `Skipped: 1`.

Ghi chu ve skip:

- `Skipped: 1` la `QuestionBankWorkspaceQueryCountIntegrationTest`.
- Ly do: moi truong hien tai khong co Docker daemon/Testcontainers usable va khong set external PostgreSQL URL.
- Vi vay chua duoc ghi la PostgreSQL production proof da dong. Test chi dang san sang chay that khi co Docker hoac set `ERG_TEST_POSTGRES_URL`, `ERG_TEST_POSTGRES_USERNAME`, `ERG_TEST_POSTGRES_PASSWORD`.

Can chay de dong blocker PostgreSQL proof:

```powershell
.\mvnw.cmd "-Dtest=com.erg.ergspringboot.service.lcms.readmodel.QuestionBankWorkspaceQueryCountIntegrationTest" test
```

Hoac chay voi staging PostgreSQL:

```powershell
$env:ERG_TEST_POSTGRES_URL="jdbc:postgresql://<host>:<port>/<db>"
$env:ERG_TEST_POSTGRES_USERNAME="<username>"
$env:ERG_TEST_POSTGRES_PASSWORD="<password>"
.\mvnw.cmd "-Dtest=com.erg.ergspringboot.service.lcms.readmodel.QuestionBankWorkspaceQueryCountIntegrationTest" test
```

Gioi han va gap con lai:

- `questionBankWorkspace`: chua co no-skip PostgreSQL/Testcontainers result tren may nay, nen chua co EXPLAIN/index evidence production.
- `studentDashboard.announcements`: moi co contract/port/fallback, chua co persistence/repository projection that vi thieu `Announcement` entity/table/migration.
- `assignmentProgressWorkspace`: class-recipient path da co recipient/missing-late/latest-best bounded projections; targeted `student_ids` JSONB recipient mode van can PostgreSQL-native query va EXPLAIN/index review.
- `classWorkspace`: attendance summary/projection van chua lam vi chua tim thay attendance entity/table ro trong source.
- P1/P2 van mo: Redis/gateway distributed rate limit, distributed cache/stampede guard, slow-query/JDBC timeout smoke tren DB that, load/soak 100 -> 500 -> 1.000 -> 5.000 concurrency, va GraalVM native/AOT smoke.

Ket luan checkpoint:

- P0 package boundary da dong o muc source/test local.
- `assignmentProgressWorkspace` va `teacherHomeworkWorkspace` da tien len mot slice projection day du hon, co bounded query-count proof local.
- `studentDashboard` da co announcement API contract an toan, nhung chua co DB-backed implementation.
- `questionBankWorkspace` da co PostgreSQL proof harness, nhung blocker production van con mo cho den khi co no-skip PostgreSQL run va EXPLAIN/index evidence.

## 36. Chuyen GraphQL controller sang domain-first layout - 2026-06-10 10:02 ICT

Ly do dieu chinh:

- `controller/graphql/<domain>` lam lap domain vi trong `controller` da co cac package domain san co.
- Ownership/review theo source hien tai hop hon khi GraphQL adapter nam gan REST controller cua domain.
- Shell namespace chung duoc dat vao `controller/platform/graphql` vi `GraphQlShellController` la platform API adapter, khong thuoc rieng LMS/LCMS/Elearning.

Layout sau refactor:

- `src/main/java/com/erg/ergspringboot/controller/platform/graphql/GraphQlShellController.java`
- `src/main/java/com/erg/ergspringboot/controller/lms/graphql/LmsClassWorkspaceGraphQlController.java`
- `src/main/java/com/erg/ergspringboot/controller/lms/graphql/LmsHomeworkWorkspaceGraphQlController.java`
- `src/main/java/com/erg/ergspringboot/controller/lms/graphql/LmsLearningResourceGraphQlController.java`
- `src/main/java/com/erg/ergspringboot/controller/lcms/graphql/LcmsWorkspaceGraphQlController.java`
- `src/main/java/com/erg/ergspringboot/controller/elearning/graphql/StudentDashboardGraphQlController.java`

Da check service/repository:

- Khong co package `service/graphql` hoac `repository/graphql`.
- Read-model service van dung domain-first layout: `service/lms/readmodel`, `service/lcms/readmodel`, `service/elearning/readmodel`, `service/hoclieu/readmodel`.
- Repository/projection van dung domain-first layout: `repository/lms`, `repository/lms/projection`, `repository/hoclieu/projection`, `repository/authorization/projection`, `repository/organization/projection`.
- Khong tim thay business GraphQL controller/resolver trong `platform/graphql`; package nay chi con infra/support.

Verification sau refactor:

```powershell
.\mvnw.cmd -DskipTests compile
```

Ket qua:

- `BUILD SUCCESS`.

Focused suite da chay voi package moi:

```powershell
.\mvnw.cmd "-Dtest=com.erg.ergspringboot.controller.platform.graphql.GraphQlShellContractTest,com.erg.ergspringboot.controller.lms.graphql.LmsClassWorkspaceGraphQlControllerTest,com.erg.ergspringboot.controller.lms.graphql.LmsHomeworkWorkspaceGraphQlControllerTest,com.erg.ergspringboot.controller.lms.graphql.LmsLearningResourceGraphQlControllerTest,com.erg.ergspringboot.controller.lcms.graphql.LcmsWorkspaceGraphQlControllerTest,com.erg.ergspringboot.controller.elearning.graphql.StudentDashboardGraphQlControllerTest,com.erg.ergspringboot.platform.security.GraphQlEnterpriseSecurityAuditTest,com.erg.ergspringboot.service.lms.readmodel.LmsReadModelServiceTest,com.erg.ergspringboot.service.lms.readmodel.LmsAssignmentProgressWorkspaceQueryCountIntegrationTest,com.erg.ergspringboot.service.lms.readmodel.LmsTeacherHomeworkWorkspaceQueryCountIntegrationTest,com.erg.ergspringboot.service.elearning.readmodel.StudentDashboardReadServiceTest,com.erg.ergspringboot.service.elearning.readmodel.StudentDashboardQueryCountIntegrationTest,com.erg.ergspringboot.service.lcms.readmodel.QuestionBankWorkspaceQueryCountIntegrationTest,com.erg.ergspringboot.repository.lms.AssignmentRepositoryReadModelContractTest,com.erg.ergspringboot.repository.lms.AttemptRepositoryReadModelContractTest,com.erg.ergspringboot.repository.lms.ClassroomRepositoryReadModelContractTest,com.erg.ergspringboot.repository.lms.StudentRepositoryReadModelContractTest" test
```

Ket qua:

- `BUILD SUCCESS`.
- `Tests run: 77`.
- `Failures: 0`.
- `Errors: 0`.
- `Skipped: 1`.

Ghi chu: skip van la `QuestionBankWorkspaceQueryCountIntegrationTest` do moi truong hien tai khong co Docker/Testcontainers usable hoac external PostgreSQL URL, khong phai loi do package refactor.
