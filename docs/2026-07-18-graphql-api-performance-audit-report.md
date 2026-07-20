# GraphQL/API performance audit – 2026-07-18

## Phạm vi và mức độ chắc chắn

Đã quét các GraphQL controller, read-service, repository/query, cache catalog, security filter, Hikari/Redis configuration và các migration/index hiện có. Tôi cũng kiểm tra PostgreSQL local: container đang healthy, có 84 bảng nhưng các bảng nghiệp vụ hiện có `n_live_tup = 0`. Vì vậy các kết luận về cấu trúc là có bằng chứng từ source; chưa thể kết luận p95/p99 dưới 1 giây hoặc khả năng 5.000 concurrent cho tới khi seed data và chạy benchmark HTTP có authentication.

## Các vấn đề phát hiện

### P0 – GraphQL query guard chưa được chứng minh ở đúng HTTP path

`application.yml` đặt GraphQL path là `${SPRING_GRAPHQL_PATH:/api/v1/graphql}`, còn `GraphQlGuardFilter` nhận path qua `@Value("${spring.graphql.http.path:/graphql}")`. Trong cấu hình Spring hiện tại hai giá trị thường sẽ khớp, nhưng contract test mới khởi tạo filter thủ công. Cần có integration test gửi request thật tới `/api/v1/graphql` và xác nhận filter thực sự chặn depth, complexity, alias, body-size và rate-limit; đồng thời test `/graphql` không vô tình là endpoint thứ hai. Đây là điểm cần khóa bằng test, không nên chỉ suy luận từ constructor injection.

### P1 – Authentication tạo DB cost cho gần như mọi request

Bearer request đi qua `ErgAuthenticationFilter` → `ErgPrincipalService.fromBearerToken` → `AuthSessionRepository.findPrincipalByAccessTokenHashAndTenantId`. Entity graph tải session, user, roles, permissions, overrides và scopes; active role assignments còn có thể phát sinh query thứ hai. Với Hikari mặc định chỉ 8 connection (local dev 10), 5.000 request đồng thời có thể xếp hàng ở auth trước khi GraphQL resolver chạy. Cache authorization allow dài hạn là không an toàn vì role revoke phải có hiệu lực ngay.

Hướng xử lý: benchmark riêng auth-hit/auth-miss; giảm entity graph bằng projection tối thiểu; cân nhắc signed claims + deny/revocation check Redis; hoặc principal cache ngắn hạn có `authzVersion`/fail-closed. Không tăng pool mù quáng.

### P1 – GraphQL workspace còn over-fetch theo selection set

Chỉ `schoolOperations` dùng `DataFetchingFieldSelectionSet`. Các workspace còn lại luôn dựng toàn bộ response, kể cả khi FE chỉ chọn một nhánh: question bank, quiz workspace, class, assignment progress, teacher homework, student dashboard, calendar, school directory, access management và reports. Hệ quả là query SQL, mapping, allocation và JSON serialization tăng theo phần FE không sử dụng.

Ưu tiên truyền selection set xuống read-service và chia các phần nặng thành lazy field/data fetcher hoặc query projection: question-bank/quiz options, class score/risk, report rows, calendar catalog và dashboard announcements.

### P1 – Option/catalog queries có nguy cơ trả unbounded data

`LcmsWorkspaceReadService.questionBankWorkspace` luôn gọi subject/level/topic assessment options dạng `List` không có Pageable/hard cap; quiz workspace cũng làm tương tự. `accessManagementWorkspace` tải toàn bộ role options và scope options của tenant. Đây là rủi ro tăng theo tenant, dù page question/user đã giới hạn 50.

Đề xuất: cap rõ ràng + trả `hasMore`, hoặc endpoint option search/pagination riêng; không nhúng hàng nghìn option vào mỗi workspace response.

### P1 – Student groups tải tối đa 10.000 member rows vào một response/cache entry

`LmsReadModelService.studentGroups` giới hạn 200 group nhưng gọi `findByTenantIdAndGroupIdIn(..., PageRequest.of(0, 10_000))`, sau đó đưa toàn bộ student ID vào response và cache. Với group lớn, đây là memory/serialization/cache bandwidth hotspot.

Đề xuất response mặc định chỉ có member count + preview giới hạn; member list phải phân trang theo group. Nếu cần giữ payload cũ, thêm field additive và rollout FE theo feature flag.

### P1 – Offset `Page` kéo theo count query và chậm ở trang sâu

Nhiều workspace dùng Spring Data `Page`: assignment progress, teacher homework, class workspace, question bank, quiz workspace, calendar batch, school directory và assignment report. Mỗi request thường có data query + count query; `OFFSET` sâu làm PostgreSQL phải scan/bỏ qua nhiều rows.

Đề xuất cursor/keyset theo `(updated_at,id)` hoặc `(created_at,id)`, giữ offset chỉ cho trang nông tương thích FE; cân nhắc `Slice` khi UI không cần total count.

### P1 – Deadline GraphQL không hủy JDBC đang chạy

`GraphQlDeadlineInstrumentation` chỉ kiểm tra trước/ sau data fetch và sau `CompletionStage`. Nó không cancel blocking JDBC. Vì vậy timeout 5 giây có thể trả lỗi cho client nhưng connection/SQL vẫn tiếp tục chạy và tiếp tục chiếm pool.

Đề xuất đặt PostgreSQL `statement_timeout`/`lock_timeout` theo transaction hoặc per-request budget, xác nhận driver cancel behavior, và đo số query còn chạy sau GraphQL timeout. Cần tránh đặt timeout thấp hơn các report hợp lệ mà không có async job.

### P1 – Cache key GraphQL bị phân mảnh theo user/permission

Calendar cache keys chứa `principal.userId()`, `principal.permissions()` và `principal.deniedPermissions()`. Cùng một calendar data của nhiều giáo viên sẽ tạo nhiều key khác nhau; list permission còn làm key dài và làm hit-rate thấp. Đây là lý do cache có thể tồn tại nhưng không giảm DB load tương ứng.

Tách dữ liệu công khai/tenant-scoped khỏi permission envelope; cache data theo normalized scope/generation, sau đó tính permission response riêng. Với dữ liệu user-specific, đo cardinality và hit-rate trước khi giữ cache.

### P1 – Cache invalidation rộng gây cold-cache storm

Source có khoảng 53 `@CacheEvict(allEntries = true)`. Một mutation curriculum/calendar/quiz/school/authorization có thể làm nhiều request đồng thời miss cache và cùng rebuild workspace. Generation/coordination đã có nhưng cần xác nhận mọi mutation dùng exact tenant/generation invalidation thay vì xóa toàn bộ.

Đề xuất single-flight per key, jitter TTL, stale-while-revalidate cho read-only data, và metric `load-coalesced`, `eviction-count`, `hit-ratio` theo cache.

### P1 – Có N+1 thật trong xử lý xung đột lịch

`CalendarConflictService.resolveConflictsByDeletion` lặp từng conflict và gọi `eventRepository.findById(existingEventId)`. Với batch có N conflict, đây là N round-trip DB. Đây là command path nhưng có thể làm thao tác import/delete schedule chậm và tranh connection với GraphQL.

Đề xuất gom toàn bộ IDs rồi `findByTenantIdAndIdIn`, map trong memory và giữ kiểm tra tenant/school scope.

### P1 – Báo cáo có đường gọi bỏ qua cache do self-invocation

`QuizReportService.assignmentReport(tenantId, assignmentId)` gọi overload 4 tham số trong cùng bean; `questionAnalytics(tenantId, quizId)` gọi `questionAnalyticsByVersion` cùng bean. Spring proxy không intercept self-invocation, nên `@Cacheable` không chạy trên đường gọi đó. GraphQL assignmentReport 4-arg hiện đi qua proxy, nhưng `ReportWorker` gọi overload 2-arg và sẽ query lại.

Đề xuất tách cached loader thành bean riêng hoặc inject self-proxy; test bằng query-count chứng minh hit thứ hai không chạm DB.

### P2 – Calendar workspace là aggregate nặng dù đã batch SQL

Mỗi calendar workspace vừa tải events/teachers vừa dựng reference maps, filter/lane summaries, permissions và `buildCatalog`. Catalog có nhiều query option (school, class, subject, level, room, teacher) và có thể chạy cho mỗi key user/date/filter. Đây không phải N+1 theo từng event, nhưng là fan-out cố định lớn và response bytes lớn.

Đề xuất tách `calendarCatalog` cache theo tenant/scope + generation, không gắn principal permission list; selection-set gating cho catalog/lane/filters; giới hạn 31 ngày và option count đã có nhưng cần metric response bytes.

### P2 – GraphQL guard phân tích query bằng text scan mỗi request

`GraphQlGuardFilter` đọc toàn bộ body, parse JSON, rồi scan query nhiều lần để depth/alias/introspection/variables; GraphQL Java sau đó lại parse/validate/complexity. Đây là CPU overhead cố định và logic alias/depth text-based có thể lệch grammar GraphQL.

Giữ guard để chống request quá lớn nhưng benchmark near-limit query; ưu tiên dùng AST/instrumentation làm nguồn chuẩn và chỉ dùng filter cho body-size/rate-limit/sensitive variable.

### P2 – Read executor hiện gần như dead code

`ErgReadTaskExecutor` mặc định 4 threads/queue 32 nhưng `CalendarReadService` và `LmsReadModelService` chỉ inject, không có call `submit` trong read path. Đây chưa phải lỗi chậm trực tiếp; ngược lại, bật parallel fan-out mà không thiết kế lại transaction/RLS sẽ làm quá tải Hikari. Cần hoặc loại bỏ dependency, hoặc triển khai có giới hạn sau khi đo pool.

### P2 – Search `%term%` thiếu trigram coverage

Đã có trigram index cho quiz title/question stem. Chưa thấy tương đương cho các search phổ biến trên user email/full name/phone, education-unit name/slug và permission label/description. Khi tenant lớn, các truy vấn leading wildcard sẽ sequential scan.

Chỉ thêm `pg_trgm` index sau khi lấy `EXPLAIN (ANALYZE, BUFFERS)` với data đại diện; index phải bao gồm tenant predicate nếu phù hợp.

## Đánh giá hiện trạng

## Các hạng mục đã sửa trong lượt triển khai này

- `CalendarConflictService.resolveConflictsByDeletion` đã chuyển từ `findById` trong vòng lặp sang `findByTenantIdAndIdIn` một lần, vẫn giữ kiểm tra tenant/school scope.
- Hai convenience entrypoint của `QuizReportService` đã được gắn cache key tenant-scoped, nên đường gọi từ worker không còn bỏ qua cache ở outer method.
- Question-bank subject/level/topic options, access-management role options và scope options đã có Pageable hard cap 500 rows.
- Đã cập nhật unit test và query-count integration test; query-count question bank vẫn xanh sau thay đổi.

- Query-count integration suite hiện đã xanh sau khi sửa constructor `AuthLoginRateLimiter` bằng `@Autowired`.
- Local PostgreSQL/Redis containers healthy nhưng DB nghiệp vụ đang rỗng; chưa có benchmark đại diện cho cây tổ chức, curriculum tree, question bank, report, quiz và student dashboard.
- Các warm benchmark trước đó không đủ để khẳng định latency end-to-end vì cần bao gồm HTTP filter + bearer authentication + serialization.
- Không thể cam kết mọi GraphQL API dưới 1 giây hoặc 5.000 concurrent nếu chưa có k6/Gatling với p50/p95/p99, error rate, DB pool wait, Redis hit ratio, CPU, heap và response bytes.

## Thứ tự triển khai đề xuất

1. Tạo seed dataset theo tenant với quy mô đại diện; thêm authenticated HTTP benchmark và query-count bao gồm auth.
2. Sửa N+1 calendar conflict và self-invocation cache; thêm regression tests.
3. Bounded option/member APIs và response-byte/selection-set instrumentation.
4. Tối ưu calendar catalog/cache key và giảm broad invalidation.
5. Statement timeout/cancellation theo request budget.
6. Keyset pagination cho các màn hình cần trang sâu.
7. EXPLAIN-driven indexes cho search; sau đó load test 500 → 2.000 → 5.000 concurrent và đo scale ceiling của một server.

## Tiêu chí nghiệm thu

- Mỗi GraphQL operation hot có p95/p99 đo qua HTTP, có auth, warm/cold cache và payload thực.
- Không có query count tăng theo số item/group/event; command calendar conflict là một batch query.
- Cache hit ratio, stampede/coalescing và invalidation được quan sát bằng metric.
- DB pool wait, active connections, `pg_stat_statements`, slow query log và Redis latency được thu trong cùng bài test.
- Payload thay đổi phải additive và có tài liệu FE; không xóa business logic hiện tại.
