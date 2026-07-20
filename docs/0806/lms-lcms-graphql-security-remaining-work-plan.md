# Kế hoạch xử lý các phần còn lại cho LMS/LCMS/Elearning GraphQL enterprise

Ngày lập: 2026-06-09 21:05 ICT

Tài liệu gốc:

- `docs/0806/lms-lcms-graphql-security-enterprise-plan.md`
- `docs/0806/lms-lcms-graphql-security-enterprise-report.md`

## 1. Mục tiêu

File này chỉ theo dõi các việc **chưa làm xong** sau khi đã có nền GraphQL/read-model/projection đầu tiên trong `erg-spring`.

Mục tiêu cuối cùng:

- GraphQL v1 chỉ dùng cho read-projection-heavy workspace.
- REST tiếp tục giữ auth/current/scope/command/file/import/export/quiz runtime.
- DTO là public API contract.
- Projection là DB/read-model contract.
- Không expose Projection trực tiếp ra REST/GraphQL response.
- Không có N+1 ở các workspace nặng.
- Có bằng chứng PostgreSQL, query-count, EXPLAIN/index, security matrix, load/soak và native smoke trước khi production enterprise.

## 2. Trạng thái hiện tại

Đã có first implementation/proof:

- `POST /api/v1/graphql` endpoint chung.
- Namespace GraphQL: `lms`, `lcms`, `crm`, `elearning`.
- GraphQL guard nền: POST/JSON/operationName/query length/depth/alias/introspection/sensitive variables.
- Execution guard: depth, complexity, deadline.
- In-memory GraphQL rate limit.
- `lms.learningResourceLibrary`.
- `lms.classWorkspace`.
- `lms.teacherHomeworkWorkspace`.
- `lms.assignmentProgressWorkspace`.
- `elearning.studentDashboard`.
- `lcms.accessManagementWorkspace`.
- `lcms.questionBankWorkspace` first projection-backed implementation, nhưng chưa có PostgreSQL proof.

Chưa được coi là production-ready vì còn thiếu:

- PostgreSQL/Testcontainers proof cho các native/heavy query.
- EXPLAIN ANALYZE/index review.
- Field-level security matrix đầy đủ.
- Distributed Redis/gateway rate limit.
- Distributed cache/stampede guard.
- Slow-query/JDBC timeout smoke trên DB thật.
- Load/soak đến 5.000 concurrency.
- GraalVM native/AOT smoke.

## 3. P0 - Bắt buộc trước khi bật GraphQL production

### 3.0 Chuẩn hóa cấu trúc package GraphQL

Nguyên tắc chốt để làm ngày mai:

- Code GraphQL mới phải bám theo cấu trúc Spring hiện tại của `erg-spring`: `controller`, `service`, `repository`, `common/response`, `platform`.
- Không tổ chức GraphQL như một module tách biệt nằm hết trong `platform/graphql`.
- `platform/graphql` chỉ chứa hạ tầng chung cho GraphQL.
- Business resolver/controller của LMS/LCMS/Elearning phải nằm trong `controller`, giống cách REST controller hiện tại đang được tổ chức.
- Read logic vẫn nằm ở `service/*/readmodel`.
- DB access/projection vẫn nằm ở `repository/*/projection`.
- DTO public contract vẫn nằm ở `common/response/*`.

Hiện trạng:

- `platform/graphql` đang chứa đúng các thành phần platform/infra như:
  - `GraphQlGuardFilter`;
  - `GraphQlContextInterceptor`;
  - `GraphQlExecutionGuardConfiguration`;
  - `GraphQlDeadlineInstrumentation`;
  - `GraphQlRateLimiter`;
  - `GraphQlRequestContext`;
  - `GraphQlAccessSupport`.
- Tuy nhiên `platform/graphql` cũng đang chứa business GraphQL resolver/controller theo domain:
  - `LmsClassWorkspaceGraphQlController`;
  - `LmsHomeworkWorkspaceGraphQlController`;
  - `LmsLearningResourceGraphQlController`;
  - `LcmsWorkspaceGraphQlController`;
  - `StudentDashboardGraphQlController`.

Đánh giá:

- Về Spring Boot/Spring GraphQL, dùng `@Controller` với `@QueryMapping` và `@SchemaMapping` là đúng. GraphQL resolver không cần là `@RestController`.
- Về kiến trúc enterprise của source hiện tại, business resolver không nên nằm trong `platform`. `platform` chỉ nên là lớp hạ tầng dùng chung, không chứa logic/scope theo màn hình LMS/LCMS/Elearning.
- Nếu để nguyên, package `platform/graphql` sẽ phình thành nơi chứa cả infra, security, namespace shell và business API adapter. Sau này khó bảo trì, khó phân quyền ownership, khó review sensitive field và khó native/AOT hint theo domain.

Cấu trúc mục tiêu:

```text
com.erg.ergspringboot
├── platform
│   └── graphql
│       ├── ErgGraphQlProperties
│       ├── GraphQlGuardFilter
│       ├── GraphQlContextInterceptor
│       ├── GraphQlExecutionGuardConfiguration
│       ├── GraphQlDeadlineInstrumentation
│       ├── GraphQlRateLimiter
│       ├── GraphQlRequestContext
│       ├── GraphQlRequestContextFactory
│       └── GraphQlAccessSupport
├── controller
│   └── graphql
│       ├── GraphQlShellController
│       ├── lms
│       │   ├── LmsClassWorkspaceGraphQlController
│       │   ├── LmsHomeworkWorkspaceGraphQlController
│       │   └── LmsLearningResourceGraphQlController
│       ├── lcms
│       │   └── LcmsWorkspaceGraphQlController
│       └── elearning
│           └── StudentDashboardGraphQlController
└── service
    ├── lms/readmodel
    ├── lcms/readmodel
    └── elearning/readmodel
```

Hoặc nhìn theo cấu trúc project hiện tại:

```text
src/main/java/com/erg/ergspringboot
├── controller
│   ├── auth
│   ├── hoclieu
│   ├── lms
│   ├── sessions
│   └── graphql
│       ├── GraphQlShellController.java
│       ├── lms
│       ├── lcms
│       └── elearning
├── service
│   ├── lms/readmodel
│   ├── lcms/readmodel
│   └── elearning/readmodel
├── repository
│   ├── lms/projection
│   ├── hoclieu/projection
│   ├── organization/projection
│   └── authorization/projection
├── common/response
│   ├── lms
│   ├── lcms
│   └── elearning
└── platform
    └── graphql
        ├── guard/filter/context/instrumentation
        └── access/rate-limit/request-context
```

Quy ước:

- `platform/graphql`: endpoint guard, context, instrumentation, rate limit, access helper dùng chung.
- `controller/<domain>/graphql`: GraphQL API adapter/resolver theo ownership domain, tương đương controller layer của REST.
- `controller/platform/graphql`: GraphQL shell/namespace chung không thuộc riêng business domain nào.
- `service/*/readmodel`: gom projection, permission field gate cấp use-case và mapping projection -> DTO.
- `repository/*/projection`: projection contract với DB.
- `common/response/*`: public DTO/GraphQL response contract.

Việc cần làm:

- Move `GraphQlShellController` sang `controller/platform/graphql`.
- Move resolver LMS sang `controller/lms/graphql`.
- Move resolver LCMS sang `controller/lcms/graphql`.
- Move resolver Elearning sang `controller/elearning/graphql`.
- Giữ `GraphQlAccessSupport` ở `platform/graphql` nếu nó vẫn là helper chung; nếu logic scope phình to thì tách tiếp thành domain policy service như `LmsGraphQlAccessPolicy`.
- Update imports trong tests.
- Không đổi schema hoặc public GraphQL contract trong refactor này.

Acceptance criteria:

- App context vẫn start.
- Focused GraphQL tests vẫn xanh.
- `platform/graphql` không còn business resolver theo màn hình.
- Resolver chỉ làm adapter mỏng: lấy argument/context, gọi read-model service, trả DTO.
- Không phát sinh thay đổi contract schema ngoài ý muốn.

### 3.1 `lcms.questionBankWorkspace`

Vấn đề:

- Đã có `QuestionUsageCountProjection`.
- H2 không chứng minh được query JSONB/lateral.
- Chưa có PostgreSQL/Testcontainers query-count proof.
- Chưa có EXPLAIN/index evidence.

Việc cần làm:

- Thêm PostgreSQL/Testcontainers integration test cho `questionBankWorkspace`.
- Seed dataset đủ lớn: nhiều subject/level/topic/question/quiz/resource usage.
- Assert query count bounded bằng Hibernate statistics hoặc datasource proxy.
- Chạy `EXPLAIN ANALYZE` cho query usage JSONB/lateral.
- Review index cho:
  - tenant/school/subject/level/topic filters;
  - quiz/question relation;
  - JSONB usage field nếu vẫn giữ JSONB;
  - publish/status filters.
- Nếu usage query thành hot path, thiết kế bảng normalize/read summary như `question_usage_summary`.

Acceptance criteria:

- Có test PostgreSQL xanh, không skip.
- Query-count bounded và `entityLoadCount == 0` cho question/quiz usage graph.
- Có ghi chú EXPLAIN/index trong report.
- Có quyết định rõ: giữ JSONB/lateral hay normalize.

### 3.2 `lms.assignmentProgressWorkspace`

Vấn đề:

- First slice đã có assignment detail, progress summary, attempts page.
- Chưa có recipient roster.
- Chưa có missing/late students.
- Chưa có latest/best attempt per recipient.

Việc cần làm:

- Thêm `RecipientProgressProjection`.
- Thêm `MissingLateStudentProjection`.
- Thêm `AttemptScoreProjection` hoặc projection riêng cho latest/best attempt per recipient.
- Bổ sung DTO public tương ứng trong response.
- Bổ sung permission/scope gate cho từng field nhạy cảm.
- Không trả raw answers, raw events, client state, package hash, private teacher note.
- Thêm query-count integration test với dataset nhiều student/recipient/attempt.
- Thêm PostgreSQL EXPLAIN/index review.

Acceptance criteria:

- Resolver chỉ gọi read-model service, không nested repository per field.
- Query count bounded khi 1 assignment có nhiều recipient và nhiều attempts.
- Teacher chỉ xem assignment do mình giao hoặc lớp mình được access.
- Denied permission override luôn thắng wildcard/grant.
- Không lộ raw quiz data/sensitive fields.

### 3.3 `lms.classWorkspace`

Vấn đề:

- Đã có class info, roster, assignment summary, score summary, risk summary.
- Chưa có attendance summary/projection.
- Chưa có PostgreSQL EXPLAIN/index proof.

Việc cần làm:

- Thêm `ClassAttendanceSummaryProjection`.
- Thêm `AttendanceSessionSummaryProjection` nếu màn hình cần drill-down theo buổi.
- Tách field-level gate:
  - attendance read;
  - grade read;
  - progress/risk read.
- Review query plan cho student/assignment/attempt/score joins.
- Bổ sung index theo tenant/class/student/assignment/status/due date.

Acceptance criteria:

- Attendance summary không load attendance sessions trong Java loop.
- Score/risk/attendance fields bị ẩn đúng theo quyền.
- Query-count test vẫn bounded sau khi thêm attendance.
- Có EXPLAIN/index evidence.

### 3.4 `lms.teacherHomeworkWorkspace`

Vấn đề:

- Đã có assignment cards và counters.
- Chưa có class options.
- Chưa có pending-review detail.
- Chưa có overdue detail.

Việc cần làm:

- Thêm `ClassOptionProjection`.
- Thêm `PendingReviewProjection`.
- Thêm `OverdueStudentProjection`.
- Bổ sung filter theo teacher scope/current school/class.
- Bổ sung pagination cho detail lists.
- Bổ sung query-count test cho nhiều class/assignment/student/attempt.

Acceptance criteria:

- Không dùng current scope từ GraphQL để thay REST current; GraphQL chỉ nhận scope đã được BE xác thực.
- Query count bounded.
- Overdue/pending-review không tính bằng Java loop qua entity graph.
- Teacher không xem được lớp ngoài managed scope.

### 3.5 `elearning.studentDashboard`

Vấn đề:

- Đã có assignment slice/counters/next actions/score summary.
- Chưa có announcement projection.
- Chưa có PostgreSQL EXPLAIN/index proof.

Việc cần làm:

- Thêm `AnnouncementCardProjection`.
- Chỉ trả announcement theo tenant/student/school/class/access scope.
- Không trả internal publish note hoặc targeting rule nhạy cảm.
- Bổ sung query-count test khi có nhiều announcements/assignments.
- Bổ sung EXPLAIN/index review cho dashboard hot path.

Acceptance criteria:

- Student chỉ xem dashboard của chính mình.
- Attempt package/sync/submit vẫn đi REST.
- Dashboard query không load assignment/attempt graph.

### 3.6 Field-level security matrix

Vấn đề:

- Đã có một số gate theo workspace.
- Chưa đủ matrix cho mọi sensitive field.

Việc cần làm:

- Lập bảng permission cho từng GraphQL field:
  - score;
  - risk;
  - attendance;
  - effective permissions;
  - raw/private notes;
  - quiz attempt summaries;
  - resource progress;
  - announcements targeting.
- Thêm test:
  - no token -> 401;
  - wrong portal -> 403;
  - wrong tenant -> 403;
  - school/class out of scope -> 403 hoặc data rỗng theo contract;
  - denied permission override thắng wildcard/grant;
  - student self-scope only;
  - user không có RBAC permission không thấy effective permissions.

Acceptance criteria:

- Mỗi sensitive field có ít nhất một positive test và một negative test.
- Không dựa vào frontend route guard.
- Không tin `X-Managed-*` hoặc header test-only ở production.

## 4. P1 - Chịu tải và vận hành multi-instance

### 4.1 Distributed rate limit

Việc cần làm:

- Thay hoặc bọc in-memory GraphQL rate limiter bằng Redis/gateway limiter cho production.
- Key gồm tenantId, userId, portal, operationName.
- Có profile/test mode riêng cho in-memory.
- Có metric rejected/allowed/throttled.

Acceptance criteria:

- Multi-instance không bypass rate limit.
- Có test hoặc staging proof cho cùng user bắn qua nhiều app instance.

### 4.2 Distributed cache và stampede guard

Việc cần làm:

- Xác định workspace được cache:
  - resource library taxonomy/counts;
  - dashboard summary;
  - question bank options;
  - class/homework counters nếu ổn định ngắn hạn.
- Cache key bắt buộc gồm tenant/user/scope/operation/filter/page.
- TTL ngắn, TTL jitter, single-flight hoặc distributed lock cho hot key.
- Không cache data chưa enforce tenant/scope.

Acceptance criteria:

- Không rò data giữa tenant/user/scope.
- Có cache hit/miss metrics.
- Có proof không stampede khi nhiều request cùng key.

### 4.3 Slow-query/JDBC timeout smoke

Việc cần làm:

- Tạo smoke test hoặc staging script với DB thật để chứng minh timeout abort query.
- Ghi rõ config:
  - `hibernate.jdbc.timeout`;
  - `jakarta.persistence.query.timeout`;
  - datasource/socket/query timeout nếu có.
- Log query timeout với operationName/requestId/tenantId đã mask sensitive data.

Acceptance criteria:

- Slow query bị abort đúng thời gian.
- Không giữ Hikari connection treo quá lâu.
- Log đủ trace nhưng không lộ variable nhạy cảm.

### 4.4 Load/soak test đến 5.000 concurrency

Việc cần làm:

- Chuẩn bị kịch bản theo bậc:
  - 100 concurrent;
  - 500 concurrent;
  - 1.000 concurrent;
  - 5.000 concurrent.
- Kịch bản gồm:
  - session/current REST;
  - LMS class workspace;
  - homework workspace;
  - assignment progress;
  - student dashboard;
  - question bank;
  - resource library.
- Thu metric:
  - p50/p95/p99 latency;
  - error rate;
  - Hikari active/pending/timeout;
  - DB CPU/IO/slow query;
  - JVM heap/GC pause;
  - cache hit/miss;
  - rate-limit reject.

Acceptance criteria:

- `sessions/current` p95 < 300ms.
- Cached dashboard/resource query p95 < 800ms.
- Class/assignment workspace p95 < 1s với pagination.
- Không có Hikari starvation.
- Không có N+1 phát sinh trong run.

## 5. P2 - Java 25, Spring Boot 4 và GraalVM native readiness

### 5.1 AOT/native smoke

Việc cần làm:

- Build AOT/native smoke tối thiểu cho:
  - GraphQL schema resources;
  - Jackson DTO records;
  - projection records;
  - Bean Validation;
  - JPA repositories;
  - PostgreSQL driver;
  - security filters;
  - cache config.
- Kiểm tra reflective access của GraphQL/Jackson/JPA.
- Ghi native hints nếu cần.

Acceptance criteria:

- Native build smoke chạy được app context.
- GraphQL schema load được.
- Focused GraphQL query smoke pass.
- Không cần mở reflection quá rộng cho package nội bộ.

### 5.2 Test-build hardening cho Java 25

Việc cần làm:

- Xử lý/ghi nhận Mockito inline mock maker self-attach.
- Theo dõi Lombok warning `sun.misc.Unsafe::objectFieldOffset`.
- Đảm bảo Flyway/H2/PostgreSQL test matrix không che lỗi production SQL.

Acceptance criteria:

- Test logs không có warning blocker chưa giải thích.
- Native/test profile tách rõ khỏi production.

## 6. P2 - Dọn placeholder/in-memory để đạt enterprise production

Vấn đề:

- Một số module còn placeholder/in-memory hoặc chưa có persistence/service typed.

Việc cần làm:

- Audit lại:
  - `ModuleWorkflowService`;
  - `CmsService`;
  - `DocumentService`;
  - LMS admin/workspace placeholder endpoints;
  - user/access list path còn `findAll().stream()`;
  - `HocLieuService.studioBootstrap()` và `resources()` pagination.
- Thay bằng repository/pageable/service typed.
- Viết contract test cho response DTO, pagination, permission/scope.

Acceptance criteria:

- Không còn endpoint enterprise production nào trả placeholder/accepted/status available nếu chưa có business implementation.
- Không có list endpoint dùng `findAll().stream()` cho bảng lớn.
- Pagination envelope nhất quán.

## 7. Thứ tự triển khai đề xuất

1. Đóng `questionBankWorkspace` PostgreSQL proof vì đây là blocker rõ nhất.
2. Hoàn thiện `assignmentProgressWorkspace` recipient/missing-late/latest-best.
3. Hoàn thiện `classWorkspace` attendance summary.
4. Hoàn thiện `teacherHomeworkWorkspace` class options/pending/overdue detail.
5. Hoàn thiện `studentDashboard` announcement projection.
6. Viết field-level security matrix đầy đủ cho toàn bộ workspace.
7. Thêm Redis/gateway rate limit và distributed cache/stampede guard.
8. Chạy slow-query smoke và PostgreSQL EXPLAIN/index review.
9. Chạy load/soak theo bậc đến 5.000 concurrency.
10. Chạy AOT/native smoke và xử lý native hints.
11. Dọn placeholder/in-memory modules trước khi chốt production.

## 8. Definition of Done chung

Một workspace chỉ được coi là enterprise-ready khi có đủ:

- Public DTO/GraphQL type rõ ràng.
- Repository projection/read-model query rõ ràng.
- Permission/scope enforced ở backend.
- Sensitive fields được gate hoặc loại bỏ.
- Pagination/default size/hard max.
- Query-count proof bounded.
- PostgreSQL EXPLAIN/index evidence.
- Không N+1.
- Không load full entity graph cho màn hình nặng.
- Cache/rate-limit policy nếu là hot path.
- Load/soak evidence.
- Native/AOT smoke nếu đi GraalVM.
