# 02 — LCMS, CALENDAR, REPORTING, ANALYTICS

> Quy ước: `B` = `src/main/java/com/erg/ergspringboot`; `T` = `src/test/java/com/erg/ergspringboot`; `R` = `src/main/resources`.
> Mọi trích dẫn có dạng `đường-dẫn:dòng` tính từ gốc repo. Chỉ ghi điều đã đọc trong code; chỗ chưa chắc ghi "nghi ngờ".
> Phạm vi đã đọc: toàn bộ file main thuộc 4 package segment `lcms`, `calendar`, `reporting`, `analytics` (+ `service/lcms`, `service/calendar`, `service/reporting`, `controller/*`, `model/*`, `repository/*`, `common/request|response`), các GraphQL module liên quan, migration V17/V20/V22/V67/V69/V91. Test: đã liệt kê toàn bộ tên test method (không đọc hết thân các file test dài > 900 dòng).
> Logic nằm NGOÀI slice và KHÔNG được đọc ở đây: `QuizAuthoringService`, `QuizDraftService`, `QuizPublishService`, `QuestionBankAuthoringService`, `MediaService`, `PortableQuizBundleService`, `QuizReportService`, `LmsOperationalOperations`, `SchoolOperations`, `SchoolQueryOperations`, `ModuleWorkflowService`, `NotificationCommandService`. Slice này chủ yếu là lớp facade/port/controller bọc quanh chúng.

---

## 1. Phạm vi và trách nhiệm

- **LCMS** (quản trị nội dung/đào tạo): lớp facade `LcmsOperations`/`LcmsQueryOperations` bọc các service quiz (soạn câu hỏi, soạn quiz/draft, publish, media, xuất bundle offline), read-model GraphQL (question bank, quiz workspace, access management, báo cáo) và các lệnh quản trị trường/lớp/học sinh/môn/ghi danh (controller mỏng, logic ở service ngoài slice).
- **CALENDAR**: lịch giảng dạy ("teaching schedule"): tạo lô lịch hàng loạt theo lưới (bulk grid), preview, phát hiện xung đột lớp/giáo viên/phòng, vòng đời batch DRAFT → PUBLISHED/CANCELLED, sửa/huỷ từng buổi, đọc workspace cho LCMS/giáo viên/học sinh, phát sự kiện `CalendarBatchPublished` để gửi thông báo.
- **REPORTING**: báo cáo tiến độ bài tập (assignment progress) đọc đồng bộ (phân trang) và xuất bất đồng bộ (job queue `report_jobs`, JSON/CSV), cùng worker đối soát bảng tổng hợp `assignment_progress_aggregates`.
- **ANALYTICS**: trong code Java của slice chỉ có (a) controller prototype `AnalyticsOperationsController` (stub, chỉ chạy profile `prototype`) và (b) 3 POJO `Event`, `Visit`, `FirebaseSyncEvent` (không phải entity JPA). Analytics "thật" của quiz (độ khó câu hỏi) nằm trong `QuizReportService` (ngoài slice) và chỉ được expose qua `LcmsReportGraphQlController`.

---

## 2. Class/entity chính và vai trò

### 2.1 LCMS

| Class | Đường dẫn | Vai trò |
|---|---|---|
| `LcmsOperations` | `B/lcms/LcmsOperations.java` | Facade lệnh (public API module), chỉ uỷ quyền sang `LcmsCommandPort` |
| `LcmsCommandPort` / `LcmsCommandUseCase` | `B/lcms/LcmsCommandPort.java`, `B/lcms/internal/command/LcmsCommandUseCase.java` | Cổng lệnh; `@Primary` impl uỷ quyền 6 service quiz (`:53-91`) |
| `LcmsQueryOperations` / `LcmsReadPort` / `LcmsReadModelUseCase` | `B/lcms/LcmsQueryOperations.java`, `B/lcms/internal/readmodel/LcmsReadModelUseCase.java` | Facade đọc: quiz workspace, draft, versions, question bank, access mgmt, analytics, assignment report |
| `LcmsQuizBundleResponse`, `LcmsQuizPackageManifest`, `LcmsQuizPackageUpdateFeedResponse`, `LcmsQuizBundleRevokeRequest` | `B/lcms/*.java` | Hợp đồng bundle offline đã ký (xem §3.6) |
| `LcmsCommandCompatibility`, `LcmsReadCompatibility` | `B/lcms/*Compatibility.java` | `@Deprecated`, seam dựng use case cho test cũ |
| Annotation phân quyền `Lcms*` (22 file) | `B/lcms/authorization/` | Mỗi annotation = `@PreAuthorize("@methodAuthorization.isAllowedAny(...)")` (bảng ở §4) |
| `LcmsQuizWorkspaceReadService` | `B/service/lcms/readmodel/LcmsQuizWorkspaceReadService.java` | Read-model quiz workspace/draft/versions (cache) |
| `LcmsWorkspaceReadService` | `B/service/lcms/readmodel/LcmsWorkspaceReadService.java` | Read-model question bank + access management (cache) |
| `LcmsWorkspaceResponses` | `B/common/response/lcms/LcmsWorkspaceResponses.java` | DTO record cho question bank / access management |
| Controllers REST | `B/controller/lcms/*.java` | `LcmsQuizAuthoringController`, `LcmsQuestionBankAuthoringController`, `LcmsMediaController`, `LcmsQuizBundleController`, `LmsOperationalCommandController`, `SchoolCommandController` |
| Controllers GraphQL | `B/controller/lcms/graphql/*.java` | `LcmsQuizWorkspaceGraphQlController`, `LcmsWorkspaceGraphQlController`, `LcmsReportGraphQlController`, `LcmsSchoolOperationsGraphQlController`, `LcmsSchoolOperationsMutationGraphQlController` |

### 2.2 CALENDAR

| Class | Đường dẫn | Vai trò |
|---|---|---|
| `CalendarOperations` | `B/calendar/CalendarOperations.java` | Facade đọc+lệnh (public API module) |
| `CalendarCommandPort` / `CalendarCommandUseCase` | `B/calendar/CalendarCommandPort.java`, `B/calendar/internal/command/CalendarCommandUseCase.java` | Ghép 5 use case lệnh; mọi lệnh ghi có `@CacheEvict("calendarWorkspace", allEntries=true)` (`:38-44`) |
| `CalendarBulkPreviewUseCase` | `B/calendar/internal/bulk/` | Validate + expand + detect conflict, không ghi DB |
| `CalendarBulkCreateUseCase` | `B/calendar/internal/bulk/` | Tạo batch DRAFT nguyên tử + idempotency + advisory lock |
| `CalendarBulkReferenceDataUseCase` | `B/calendar/internal/bulk/` | Nạp snapshot lớp/môn/level/GV/phòng/trường cho bulk |
| `CalendarBatchLifecycleUseCase` | `B/calendar/internal/batch/` | publish/cancel batch |
| `CalendarEventMutationUseCase` | `B/calendar/internal/mutation/` | sửa/huỷ 1 buổi, huỷ lịch theo giáo viên |
| `CalendarRoomCommandUseCase` | `B/calendar/internal/room/` | tạo phòng (idempotent theo tên) |
| `CalendarReadUseCase` (+`CalendarWorkspaceQueryUseCase`, `CalendarFullCatalogQueryUseCase`, `CalendarWorkspaceAssembler`, `CalendarEventResponseAssembler`, `CalendarReferenceMapsQueryUseCase`, `CalendarPermissionsQueryUseCase`, `CalendarCatalogQueryUseCase`, `CalendarScheduleBatchQueryUseCase`, `CalendarEventDetailQueryUseCase`) | `B/calendar/internal/readmodel/` | Đọc workspace/catalog/batch/event; `@Primary`, `readOnly` tx (`CalendarReadUseCase.java:38-41`) |
| `CalendarPublishedBatchProjection(+Adapter)` | `B/calendar/CalendarPublishedBatchProjection.java`, `.../CalendarPublishedBatchProjectionAdapter.java` | Projection phi-JPA cho module notifications |
| `CalendarBatchPublished` | `B/calendar/CalendarBatchPublished.java` | Sự kiện module (at-least-once) |
| `CalendarScopeService` | `B/service/calendar/CalendarScopeService.java` | Điểm duy nhất assert phạm vi trường/tenant cho event/batch |
| `CalendarValidationService`, `CalendarExpansionService`, `CalendarConflictService`, `CalendarBulkInputNormalizer` | `B/service/calendar/` | Luật validate / sinh buổi / xung đột / chuẩn hoá input |
| `CalendarCommandService`, `CalendarReadService`, `CalendarExcelImportService` | `B/service/calendar/` | `@Deprecated` adapter tương thích; Excel import chỉ là stub profile `prototype` |
| `CalendarDataLoaderRegistry` | `B/service/calendar/CalendarDataLoaderRegistry.java` | 2 bean DataLoader — không thấy nơi dùng (xem §9) |
| Entity `CalendarBatch` / `CalendarEvent` / `CalendarEventTeacher` / `CalendarRoom` / `CalendarConflict` | `B/model/calendar/` | Bảng `teaching_schedule_batches/events/event_teachers/rooms/conflicts` |
| Repository | `B/repository/calendar/` | `CalendarBatch/Event/EventTeacher/Room/ConflictRepository` |
| Controller | `B/controller/calendar/rest/CalendarRestController.java`, `B/controller/calendar/graphql/{CalendarLcmsQuery,CalendarLmsQuery,CalendarMutation}GraphQlController.java` | REST + GraphQL |
| Annotation | `B/calendar/authorization/Calendar{Read,SelfRead,Create,Update,Delete,Publish,Import}.java` | Chính sách typed |
| DTO | `B/common/request/calendar/CalendarRequests.java`, `B/common/response/calendar/CalendarResponses.java` | Request/response record |
| `CalendarBatchPublishedConsumer` | `B/notifications/internal/CalendarBatchPublishedConsumer.java` | Consumer của module notifications |

### 2.3 REPORTING / ANALYTICS

| Class | Đường dẫn | Vai trò |
|---|---|---|
| `ReportingOperations` | `B/reporting/ReportingOperations.java` | Facade: kiểm tra phạm vi assignment/lớp rồi enqueue/đọc báo cáo |
| `ReportJobPort`/`ReportJobUseCase` | `B/reporting/internal/ReportJobUseCase.java` | Enqueue idempotent + đọc trạng thái job |
| `ReportingAnalyticsService` | `B/reporting/internal/ReportingAnalyticsService.java` | Báo cáo đồng bộ + định nghĩa KPI |
| `AssignmentScopeLookup(+Adapter)` | `B/reporting/AssignmentScopeLookup.java`, `.../internal/AssignmentScopeLookupAdapter.java` | Cổng hẹp lấy `(tenantId, academicClassId)` của assignment |
| `AssignmentProgressFilter`, `ReportExportOptions`, `ReportingResponses` | `B/reporting/*.java` | Filter/option/response |
| `ReportJob` | `B/model/reporting/ReportJob.java` | Entity `report_jobs` |
| `ReportWorker` | `B/service/reporting/ReportWorker.java` | Worker định kỳ sinh báo cáo |
| `ReportAggregateReconciliationWorker` + `PostgresReportAggregateReconciliationStore` | `B/service/reporting/` | Worker đối soát bảng aggregate |
| `ReportJobService` | `B/service/reporting/ReportJobService.java` | `@Deprecated` adapter |
| `ReportJobController` | `B/controller/reporting/ReportJobController.java` | REST `/api/v1/reports/jobs` |
| `AnalyticsOperationsController` | `B/controller/operations/AnalyticsOperationsController.java` | Prototype stub `/api/v1/insight|audit|operations` |
| `Event`, `Visit`, `FirebaseSyncEvent` | `B/model/analytics/` | POJO Lombok "migrated from erg-go", không `@Entity` |
| `QuestionAnalyticsProjection` | `B/repository/quiz/runtime/projection/QuestionAnalyticsProjection.java` | Projection native query (độ khó câu hỏi: answered/correct/partial/needsManual/avgPoints) |

---

## 3. Nghiệp vụ và luồng xử lý

### 3.1 CALENDAR — tạo lịch hàng loạt (preview → create)

Đầu vào: `CalendarBulkCreateRequest` (schoolId, applyFrom, repeatWeeks, rows[], overrideConflicts, resolveMode, idempotencyKey) — `B/common/request/calendar/CalendarRequests.java:54-83`.

**Preview** (`B/calendar/internal/bulk/CalendarBulkPreviewUseCase.java:67-125`):
1. Chuẩn hoá (`CalendarBulkInputNormalizer.normalize`, `:76`): tối đa 200 dòng, 52 tuần, 20 lớp/dòng, 20 GV/vai trò, note 2000 ký tự, roomName 255 (`B/service/calendar/CalendarBulkInputNormalizer.java:16-21`); trim/khử trùng ID (`:64-72`); đổi `resolveMode` sang UPPER (`:42`); `startTime/endTime` ép về `LocalTime.toString()` (`:92-99`).
2. Nạp reference data theo trường (`CalendarBulkReferenceDataUseCase.load`, `:75-134`): lớp của trường, ≤500 môn ACTIVE, level theo môn, user theo tenant, phòng ACTIVE của trường (phòng không active → `IllegalArgumentException`, `:117-127`).
3. Validate từng dòng (`B/service/calendar/CalendarValidationService.java:19-127`) — mã lỗi: `CLASS_REQUIRED`, `CLASS_NOT_FOUND`, `CLASS_NOT_IN_SCHOOL`, `MAIN_TEACHER_REQUIRED`, `TEACHER_NOT_FOUND`, `SUBJECT_NOT_FOUND`, `LEVEL_NOT_FOUND`, `LEVEL_NOT_IN_SUBJECT`, `DUPLICATE_TEACHER_ROLE`, `INVALID_PERIOD_COUNT` (1..10), `INVALID_PERIOD_START` (≥1), `INVALID_TIME_RANGE`, `INVALID_TIME_FORMAT`, `INVALID_WEEKDAY` (1..7).
4. Sinh buổi cho dòng hợp lệ (`CalendarExpansionService.expand`, `B/service/calendar/CalendarExpansionService.java:69-128`): với mỗi lớp × mỗi tuần; ngày đầu = ngày ≥ `applyFrom` rơi đúng thứ (`:48-67`, weekday ISO 1=Mon..7=Sun); giờ theo múi `Asia/Ho_Chi_Minh` (`:28`, `:94-95`); status khởi tạo `DRAFT` (`:114`); title = `"<môn> - <lớp>"` hoặc `"Unknown Class"` (`:130-138`); GV MAIN/ASSISTANT kèm `displayNameSnapshot` (`:160-186`).
5. Nếu tất cả dòng hợp lệ và có buổi → `detectConflicts` (xem §3.2). `valid = allValid && không có conflict ERROR` (`:120-121`). Preview chạy trong transaction nhưng không ghi gì.

**Create** (`B/calendar/internal/bulk/CalendarBulkCreateUseCase.java:122-231`, `@Transactional("postgresTransactionManager")`):
1. Chuẩn hoá + tính `payloadHash` = SHA-256 của JSON chuẩn tắc (key sort) (`:133-134`, `:233-244`).
2. **Idempotency** (`:135-148`): nếu `idempotencyKey` đã tồn tại theo `(tenant, key)` → nếu hash khác → `ConflictException`; nếu giống → trả lại payload dựng từ batch cũ (rowResults/conflicts rỗng). Unique index `idx_tsb_tenant_idempotency_key (tenant_id, idempotency_key) WHERE idempotency_key IS NOT NULL` (`R/db/migration/postgres/V17__teaching_schedule_schema.sql`).
3. **Advisory lock** `pg_try_advisory_xact_lock(hash(tenant,school))` không chờ; không lấy được → `ConflictException("Another bulk schedule operation ...")` (`:150-152`, `:286-298`).
4. Validate (như preview); có dòng lỗi → trả `CalendarBulkPayload(batchId=null, 0,0,0, rowResults, [])` (`:155-160`) — HTTP 200, không ném lỗi.
5. Expand + `detectConflicts` (`:162-181`).
6. Có conflict `ERROR`: nếu `resolveMode == DELETE_CONFLICTING` → `resolveConflictsByDeletion` (chỉ xử lý `TEACHER_TIME_CONFLICT`, chỉ buổi thuộc trường trong phạm vi quản lý) rồi `updateStatusBatch(... CANCELLED ...)` cho các buổi cũ (`:185-192`); còn conflict chưa giải quyết → trả payload không tạo gì (`:197-199`); mode khác (REJECT/null) → trả payload không tạo gì (`:200-202`).
7. Có conflict `WARNING` (phòng) và `overrideConflicts=false` → trả payload không tạo (`:205-208`).
8. Tạo `CalendarBatch` status `DRAFT`, source `LCMS_BULK_GRID` (`:210-223`), lưu conflict còn lại vào `teaching_schedule_conflicts` (`:224`), `saveAll` event + teacher assignment (`:225-226`). Trả `batchId`, số event, số assignment, tổng số tiết.

### 3.2 CALENDAR — phát hiện xung đột (`B/service/calendar/CalendarConflictService.java:50-204`)
- Cửa sổ truy vấn = [min(start), max(end)] của toàn bộ buổi ứng viên (`:88-95`).
- So với DB (mọi status ≠ `CANCELLED`, tức gồm cả DRAFT của batch khác): lớp (`CLASS_TIME_CONFLICT`, ERROR, `:137-143`), giáo viên toàn tenant (`TEACHER_TIME_CONFLICT`, ERROR, `:145-155`), phòng cùng trường theo **roomName** không phân biệt hoa thường (`ROOM_TIME_CONFLICT`, WARNING, `:157-169`).
- So giữa các ứng viên với nhau theo cặp (`addPairwiseConflicts`, `:173-196`).
- Overlap = `a.start < b.end && a.end > b.start` (liền kề không tính, `:279-285`).
- Khử trùng theo `(code, clientRowId, existingEventId)` (`:198-203`).
- `persistConflicts` lưu `eventId = clientRowId` (`:298`) — tên cột `event_id` thực chứa clientRowId.
- `markConflictsResolved` đánh `resolved=true` khi publish/cancel batch (`:313-326`).

### 3.3 CALENDAR — state machine batch/event

```
Batch:  DRAFT --publish--> PUBLISHED
        DRAFT/PUBLISHED --cancel--> CANCELLED   (cancel lại → lỗi "already cancelled")
Event:  DRAFT | PUBLISHED --(delete event / cancel batch / delete teacher schedule / DELETE_CONFLICTING)--> CANCELLED
```
CHECK constraint trên DB: status ∈ {DRAFT, PUBLISHED, CANCELLED} (V17).

- **Publish** (`B/calendar/internal/batch/CalendarBatchLifecycleUseCase.java:58-83`): chỉ khi batch `DRAFT` (`IllegalStateException` nếu không, `:61-64`); batch phải có event (`:66-69`); set `PUBLISHED`, `publishedBy/At`; `updateStatusBatch` đặt PUBLISHED cho **mọi** event của batch (`:66`, `:77-78`); `markConflictsResolved`; `publishEvent(CalendarBatchPublished("schedule-published:"+batchId, ..., schemaVersion 1))` (`:80-81`).
- **Cancel** (`:85-101`): cấm khi đã CANCELLED; set batch + mọi event = CANCELLED; không phát sự kiện.
- **Sửa buổi** (`B/calendar/internal/mutation/CalendarEventMutationUseCase.java:45-56`): chỉ cập nhật `roomId`, `roomName`, `note` khi khác null; không đổi giờ/GV/lớp.
- **Huỷ buổi** (`:58-66`): đặt CANCELLED (không xoá vật lý).
- **Huỷ lịch theo GV** (`:68-93`): lấy event DRAFT + PUBLISHED của GV trong khoảng ngày (múi VN, `[from 00:00, to+1 00:00)`) thuộc `schoolIds` được phép → CANCELLED.
- **Tạo phòng** (`B/calendar/internal/room/CalendarRoomCommandUseCase.java:32-55`): tên trim, rỗng → `IllegalArgumentException`; đã có (cùng school, tên không phân biệt hoa thường) → trả phòng cũ; ngược lại tạo ACTIVE. Unique `uq_tsr_school_name (school_id, name)` (V17).

### 3.4 CALENDAR — đọc workspace
- **LCMS workspace** (`B/calendar/internal/readmodel/CalendarWorkspaceQueryUseCase.java:69-86`): trạng thái mặc định `DRAFT,PUBLISHED` (`:33`, `:76`); lọc GV/lớp/môn/level bằng cờ `*Filtered` + sentinel `__none__` (`:34`, `:166-173`).
- **Giáo viên** (`:88-107`) và **học sinh** (`:109-133`): luôn chỉ `PUBLISHED`, **bỏ qua tham số `statuses`** (`:96`, `:125`). Học sinh: tìm `Student` theo `authUserId` (`:113`), phải có `academicClassId` (`:116-120`), lỗi dùng `IllegalArgumentException`.
- Giới hạn: khoảng ngày 1..31 (`:35`, `:195`), mỗi list filter ≤100 giá trị (`:36`, `:199-207`), `from/to` phải là ISO date (`BadRequestException`, `:188-193`).
- Response gồm: events, filterOptions (đếm theo trường/lớp/GV/môn/level, `CalendarWorkspaceAssembler.java:27-63`), laneSummaries theo `viewMode` (`:65-81`: CLASS/BY_CLASS, SUBJECT, TEACHER, mặc định theo trường), catalog (`CalendarFullCatalogQueryUseCase.java:69-152`, tối đa 500 option/loại, vượt → `BadRequestException`, `:204-208`), permissions, `generatedAt`.
- `colorGroup` suy từ `abs(subjectId.hashCode()) % 8` trong bảng 8 màu (`CalendarEventResponseAssembler.java:22-23`, `:82-86`).
- Cache `calendarWorkspace`, TTL 30s, tối đa 64 mục (`B/config/PlatformCacheDescriptorContributor.java:34`); key gồm tham số + `principal.userId()`, `permissions()`, `deniedPermissions()` (`CalendarReadUseCase.java:155-157`).

### 3.5 CALENDAR — thông báo khi publish
`CalendarBatchPublishedConsumer.on` (`B/notifications/internal/CalendarBatchPublishedConsumer.java:26-43`, `@ApplicationModuleListener`): đọc projection `findPublishedBatch` (batch không còn → `IllegalStateException`, để retry), nếu không có GV nào → bỏ qua; ngược lại `notifications.enqueue(NotificationCommand type "SCHEDULE_PUBLISHED", aggregate "teaching_schedule", deep link "/calendar", priority "normal", channel "lms", recipients = teacherIds distinct)`, khoá idempotency downstream = `eventId` ("schedule-published:"+batchId). Nội dung thông báo viết tiếng Việt không dấu và dùng **schoolId** thay tên trường (`:37-39`).

### 3.6 LCMS — luồng quiz authoring/publish/bundle
- Create quiz: `POST /api/v1/lcms/quizzes` idempotent theo `X-Idempotency-Key` (`B/controller/lcms/LcmsQuizAuthoringController.java:45-54`, javadoc `:31-35`). Draft autosave dùng optimistic `docVersion` (409 `STALE_DRAFT`, theo javadoc `:32-34`; logic nằm trong `QuizDraftService`, ngoài slice).
- Draft/slide/reorder cùng nhận idempotency key bằng `body.withIdempotencyKey(key)` (`:65-66`, `:78-79`, `:91-92`, `:104-105`).
- Validate (`:108-116`) → Publish (`:118-132`, quyền `lms.quiz.publish`) → Fork version (`:134-143`) → Delete quiz (`:145-153`). Logic state/tính điểm/ký bundle ở service ngoài slice.
- Question bank CRUD: `B/controller/lcms/LcmsQuestionBankAuthoringController.java:29-70`; xoá = "archive" (`:58-70`).
- **Media** hash-first: `POST /media/presign` rồi client `PUT .../upload/quiz-media/{tenantId}/{prefix}/{filename}` (URL ký, `@ReviewedPublicEndpoint`, giới hạn kích thước `lcms.maxMediaUploadBytes()`, URL ký sai/hết hạn → 400) rồi `POST /media/{mediaId}/commit` (`B/controller/lcms/LcmsMediaController.java:35-80`).
- **Bundle offline** (`B/controller/lcms/LcmsQuizBundleController.java`): export bundle active/version (`:42-62`), manifest (`:64-73`), download = 302 sang `downloadUrl` nếu `portableReady` (`:75-92`), update feed theo `channel/currentVersion/runtimeVersion` (`:94-107`), revoke với `reason` bắt buộc ≤256 (`:109-118`; `LcmsQuizBundleRevokeRequest.java`). Phản hồi cache `private, max-age=300, must-revalidate` + ETag/`If-None-Match` → 304 (`:124-136`). `LcmsQuizBundleResponse.fromLegacy` ép từ map cũ, thiếu field số → `IllegalArgumentException` (`B/lcms/LcmsQuizBundleResponse.java:69-114`), `releaseChannel` mặc định `stable`.
- Theo javadoc: đáp án chỉ nhúng cho gói client-first; gói server-authoritative bỏ đáp án (`LcmsQuizBundleController.java:28-32`) — logic không nằm trong slice.

### 3.7 LCMS — read-model
- `quizWorkspace` (`B/service/lcms/readmodel/LcmsQuizWorkspaceReadService.java:100-155`): phân trang (mặc định 20, tối đa 50, `:43-44`, `:257-266`), chuẩn hoá `search/status/kind` về lowercase (`:268-271`), có/không search chọn 2 query (`:172-203`), option môn/level/topic ACTIVE; số câu = số `questionIds` không rỗng (`:216`). Cache `lcmsQuizWorkspace` TTL 30s tối đa 128 (`B/config/LmsCacheDescriptorContributor.java:40`).
- `quizDraft` (`:157-170`): lấy draft mới nhất theo `updatedAt`, trả settings/playerTemplate/tree dạng chuỗi JSON.
- `quizVersions` (`:74-85`): >100 phiên bản → `BadRequestException` (không cắt âm thầm).
- `questionBankWorkspace` (`B/service/lcms/readmodel/LcmsWorkspaceReadService.java:79-153`): `includeQuestions` mặc định true; tối đa 500 option mỗi loại (`:45`, `:269-285`); đếm usage câu hỏi theo batch (`:352-364`); danh sách quiz option chọn 1 trong 4 query theo (search,status) (`:186-233`); nhãn "Toan cong ty", thời lượng = `max(5, n × (2|3))` phút (test=2, khác=3, `:466-469`), `sourceMode` = manual (test) | auto-random (`:471-473`).
- `accessManagementWorkspace` (`:235-267`): phân trang user + role option + scope option; chỉ kèm effective permissions nếu controller cho phép (xem §4).
- `quizQuestionAnalytics`, `assignmentReport` → `QuizReportService` (ngoài slice) qua `LcmsReadModelUseCase.java:41-42`.

### 3.8 LCMS — vận hành trường/lớp/học sinh (chỉ lớp adapter)
- REST: `B/controller/lcms/SchoolCommandController.java` (`/api/v1/lcms/schools`: tạo/sửa(PUT,PATCH)/archive/restore trường, tạo năm học, activate/close năm học (cần `VersionRequest`), sync grades mặc định, tạo/sửa grade) và `B/controller/lcms/LmsOperationalCommandController.java` (`/api/v1/lcms`: lớp, học sinh, import học sinh `import|bulk`, môn, ghi danh đơn/bulk, header `X-Idempotency-Key`).
- GraphQL: `LcmsSchoolOperationsMutationGraphQlController` (`:38-156`) tạo `UserAccessContext` từ principal (`:164-174`), `version` bắt buộc là số nguyên không âm (`:192-203`).
- Đọc: `schoolDirectory`, `schoolOperations` (`LcmsSchoolOperationsGraphQlController.java:27-61`); `schoolDirectory` coi là "all scope" nếu principal wildcard, scopes chứa `*` hoặc có `curriculum.manage` (`:34-37`); `schoolOperations` chỉ nạp các field GraphQL được chọn (`selectionSet`, `:53-60`) và trả cờ quyền create/update/archive/class.manage.
- **Logic nghiệp vụ thật (validate, state, idempotency, version) nằm trong `LmsOperationalOperations`/`SchoolOperations` — chưa đọc.**

### 3.9 REPORTING — job xuất báo cáo
1. `POST /api/v1/reports/jobs/assignment-progress` (`B/controller/reporting/ReportJobController.java:43-58`): header `X-Idempotency-Key` ưu tiên hơn body (`:51-52`); dựng filter (`:122-134`: status ∈ {submitted,in_progress,needs_review,review_required}, `submittedFrom ≤ submittedTo`, timezone hợp lệ, page ≥0, size 1..100).
2. `ReportingOperations.enqueueAssignmentProgress` (`B/reporting/ReportingOperations.java:62-77`): assignment phải thuộc tenant, user phải `canAccessClass(classId của assignment)`, `filter.classId` (nếu có) phải bằng lớp của assignment — vi phạm → `ForbiddenException`.
3. `ReportJobUseCase.enqueueAssignmentProgress` (`B/reporting/internal/ReportJobUseCase.java:38-74`): format ∈ {json,csv} (mặc định json, `ReportExportOptions.java:11`); key tự sinh nếu rỗng = `ASSIGNMENT_PROGRESS:<assignment>:<user>:<epochSecond>` (`:46-49`); nếu `(tenant,key)` đã có: cùng request → trả job cũ, khác request → `ConflictException` (`:51-57`, `sameRequest :105-114`); job mới: `status=queued`, `progress=0`, `availableAt=now`, `expiresAt=now+1 ngày` (`:67-72`).
4. **Worker** (`B/service/reporting/ReportWorker.java`, `@Scheduled` mặc định 5s, bật mặc định `erg.reporting.worker.enabled`): mỗi vòng lấy tối đa 2 job (`BATCH_SIZE=2`) bằng `FOR UPDATE SKIP LOCKED` (`B/repository/reporting/ReportJobRepository.java:15-23`) gồm `queued & available_at ≤ now` **hoặc** `processing & lease_until < now`; set `processing`, lease 10 phút, `attempts+1` (`:49-72`). Xử lý (`:74-123`): chạy dưới `TenantContext` của job, đọc từng trang 100 dòng qua `QuizReportService`, tối đa 100 trang = 10.000 dòng; vượt → lỗi "report exceeds 10,000 result rows" (`:92-100`); kết quả (đếm tổng + `students`) lưu vào `result_json`; CSV thì `content` = CSV và bỏ `students` (`:113-117`, header `:178`).
5. Lỗi (`:119-121`, `transition :199-210`): `RuntimeException` → nếu `attempts ≥ 5` thì `failed` (đặt `completed_at`), ngược lại quay về `queued` với `available_at = now + min(300, 2^min(8,attempts))` giây; `last_error` cắt 1000 ký tự.
6. Trạng thái: `queued → processing → completed | failed` (CHECK `ck_report_jobs_status`, V67). Poll `GET /{jobId}`; tải `GET /{jobId}/download` (`:86-105`): chưa `completed` → 409; quá `expires_at` → 404; CSV trả `text/csv` attachment `assignment-progress-<assignmentId>.csv`, JSON trả `result`.

### 3.10 REPORTING — báo cáo đồng bộ và đối soát aggregate
- `GET /api/v1/reports/jobs/assignments/{assignmentId}/progress` (`ReportJobController.java:67-84`) → `ReportingAnalyticsService.assignmentProgress` (`B/reporting/internal/ReportingAnalyticsService.java:44-69`): kiểm tra tenant + lớp (2 lần cho `filter.classId`), gọi `QuizReportService.assignmentReport`, kèm `KpiDefinition` (totalRecipients, submittedCount, inProgressCount, needsReviewCount, notStartedCount = tổng − submitted − inProgress − needsReview, averagePercent; nguồn `student_progress + assignment_progress_aggregates`, `:20-34`).
- Đối soát: trigger `trg_student_progress_assignment_aggregate` cập nhật delta aggregate và `INSERT ... ON CONFLICT` vào `report_reconciliation_queue` (V69). `ReportAggregateReconciliationWorker` (`B/service/reporting/ReportAggregateReconciliationWorker.java:24-44`, mặc định 5s, batch 10) claim bằng `FOR UPDATE SKIP LOCKED` + lease 2 phút (`PostgresReportAggregateReconciliationStore.java:17-43`), gọi hàm SQL `erg_reconcile_assignment_progress(tenant, aggregate)`, rồi xoá dòng queue **chỉ khi** `source_updated_at` còn khớp (`:45-57`) — cập nhật mới đến trong lúc đối soát được giữ lại.

### 3.11 ANALYTICS — prototype
`AnalyticsOperationsController` (`B/controller/operations/AnalyticsOperationsController.java:17-19`, `@Profile("prototype")`) chuyển mọi route `/api/v1/insight/*`, `/api/v1/audit/logs`, `/api/v1/operations/configs|firewall|system-status` sang `ModuleWorkflowService` (generic). `firewallCheck` luôn trả `blocked=false`; `insightSession/user-journey` trả `events=[]` cố định. Không có phân quyền method-level trong file này.

---

## 4. Quy tắc nghiệp vụ, validation, phân quyền

### 4.1 Permission key theo annotation

| Annotation (file trong `B/…/authorization/`) | Quyền chấp nhận (`isAllowedAny`) |
|---|---|
| `CalendarRead` | `lms.schedule.read`, `lms.schedule.manage`, `lms.manage`, `*` |
| `CalendarSelfRead` | `lms.schedule.read_self`, `lms.schedule.read`, `lms.schedule.manage`, `lms.manage`, `*` |
| `CalendarCreate` / `Update` / `Delete` / `Publish` / `Import` | `lms.schedule.{create|update|delete|publish|import}` + `lms.schedule.manage`, `lms.manage`, `*` |
| `ReportingRead` / `LcmsReportRead` | `lms.report.read`, `lms.grade.read`, `lms.manage`, `*` |
| `LcmsQuizCreate` | `lms.exercise.create`, `lms.quiz.create`, `*` |
| `LcmsQuizUpdate` | `lms.exercise.update`, `lms.quiz.create`, `*` |
| `LcmsQuizDelete` | `lms.exercise.delete`, `lms.quiz.create`, `*` |
| `LcmsQuizPublish` | `lms.quiz.publish`, `*` (cũng dùng cho **revoke bundle**) |
| `LcmsQuizBundleRead` | `lms.quiz.read`, `*` |
| `LcmsMediaWrite` | `lms.quiz.create`, `*` |
| `LcmsQuestionCreate/Update/Delete` | `lms.exercise.{create|update|delete}`, `lms.quiz.create`, `*` |
| `LcmsQuizWorkspaceRead` | `lms.exercise.read`, `lms.quiz.read`, `lms.exercise.create`, `lms.quiz.create`, `lms.manage`, `*` |
| `LcmsWorkspaceRead` | `lms.exercise.read`, `lms.quiz.read`, `content.manage`, `*` |
| `LcmsScopeRead` | `lms.scope.read`, `*` |
| `LcmsSchoolRead` | `lms.school.read`, `lms.school.update`, `lms.manage`, `*` |
| `LcmsSchoolCreate/Update/Archive` | `lms.school.create` / `lms.school.update`+`lms.class.manage` / `lms.school.archive` (+`*`) |
| `LcmsClassManage` | `lms.class.manage`, `*` |
| `LcmsStudentManage` | `lms.student.manage`, `lms.class.manage`, `*` |
| `LcmsSubjectManage` | `lms.subject.manage`, `curriculum.manage`, `lms.class.manage`, `*` |
| `LcmsEnrollmentManage` | `lms.enrollment.manage`, `lms.class.manage`, `*` |

GraphQL controller lặp lại quyền bằng `accessSupport.assertPermission(...)` (kiểm tra 2 lớp), ví dụ `B/controller/calendar/graphql/CalendarLcmsQueryGraphQlController.java:50`, `B/controller/lcms/graphql/LcmsReportGraphQlController.java:33`. Test `CalendarAuthorizationPolicyContractTest` khẳng định REST và GraphQL dùng cùng policy typed.

### 4.2 Tenant và phạm vi (scope)
- Tenant luôn lấy từ principal; `tenantId` client gửi khác principal → `ForbiddenException` (REST: `CalendarRestController.java:331-337`; GraphQL: `accessSupport.verifiedTenantId`).
- Calendar dùng `CalendarScopeService`:
  - `requireManagedSchools` (`B/service/calendar/CalendarScopeService.java:46-59`): principal wildcard → trả danh sách yêu cầu đã chuẩn hoá (có thể rỗng); ngược lại rỗng → danh sách trường quản lý; có yêu cầu → phải ⊆ managed, nếu `effective` rỗng hoặc không thuộc managed → `ForbiddenException`.
  - `requireEvent` / `requireBatch` (`:65-81`): tìm theo tenant + id (thiếu → `IllegalArgumentException`), rồi `assertEducationUnitScope(school của resource)`.
- Áp dụng: workspace LCMS, danh sách batch (`resolveScopedSchoolFilter`, REST `:315-329`, GraphQL `:89-103`: trường chỉ định phải trong managed; không chỉ định + wildcard → toàn tenant; không wildcard và quản lý >1 trường → `BadRequestException`), chi tiết batch, xem/sửa/huỷ event, publish/cancel batch, preview/create (`requireManagedSchool`), tạo phòng, huỷ lịch theo GV.
- `GET /api/v1/calendar/rooms` (`CalendarRestController.java:221-229`) không gọi kiểm tra phạm vi trường (chỉ tenant).
- "My calendar" (REST `:84-103`, GraphQL `CalendarLmsQueryGraphQlController.java:39-78`): userId lấy từ principal, không nhận từ client; thiếu userId → `ForbiddenException`.
- Reporting: assignment phải cùng tenant và `access.canAccessClass(classId)`; `GET /{jobId}` và `/download` chỉ lọc theo tenant (`ReportingOperations.java:79-81`, `ReportJobUseCase.java:76-81`) — **không kiểm tra người yêu cầu hay lớp** (xem §9). `report_jobs` bật RLS theo `app.tenant_id` (V67).
- LCMS GraphQL: `quizDraft`/`quizVersions`/`quizQuestionAnalytics`/`assignmentReport` luôn dùng tenant từ principal (truyền `null` cho verifiedTenantId); `quizWorkspace`/`questionBankWorkspace`/`accessManagementWorkspace` cho phép `input.tenantId` nhưng phải khớp.
- `accessManagementWorkspace`: effective permissions chỉ trả nếu principal có `*`/`rbac.permission.read`/`users.manage` và **không** bị deny bởi `*`, `rbac.permission.read`, `users.manage` (`LcmsWorkspaceGraphQlController.java:68-82`).
- Quyền của lớp Calendar permissions response: `canCreate=canUpdate=canDelete=canPublish=canManage`, `canViewAggregate=canRead` (`CalendarPermissionsQueryUseCase.java:21-27`), chỉ xét `lms.schedule.manage`/`lms.schedule.read`/`*` (không xét các quyền chi tiết).

### 4.3 Luật khác
- Lịch: GV chính bắt buộc ≥1; một GV không thể vừa chính vừa trợ giảng trong một dòng; số tiết 1..10 (cũng CHECK DB); weekday 1..7; level phải thuộc môn; lớp phải thuộc trường (§3.1).
- Một lô tạo trong một transaction, khoá advisory theo `(tenant, school)` — các request song song cùng trường bị từ chối (409), không xếp hàng.
- Xung đột `ERROR` (lớp, GV) chặn tạo; `WARNING` (phòng) chặn trừ khi `overrideConflicts`.
- Report: kích thước trang 1..100; tối đa 10.000 dòng/job; job hết hạn sau 1 ngày; idempotency theo `(tenant,key)` (UNIQUE `uq_report_jobs_tenant_idempotency`, V67).

---

## 5. API

### 5.1 REST

| Method | Path | Controller:dòng | Quyền | Mô tả |
|---|---|---|---|---|
| GET | `/api/v1/calendar/workspace` | `CalendarRestController.java:61` | CalendarRead | Workspace LCMS (from,to bắt buộc) |
| GET | `/api/v1/calendar/my-calendar` | `:84` | CalendarSelfRead | Lịch của GV hiện tại |
| GET | `/api/v1/calendar/events/{eventId}` | `:105` | CalendarRead | Chi tiết buổi |
| GET | `/api/v1/calendar/batches` | `:115` | CalendarRead | Danh sách batch (page/size) |
| GET | `/api/v1/calendar/batches/{batchId}` | `:132` | CalendarRead | Chi tiết batch |
| POST | `/api/v1/calendar/preview` | `:144` | CalendarCreate | Preview bulk |
| POST | `/api/v1/calendar` | `:154` | CalendarCreate | Tạo batch DRAFT |
| PUT | `/api/v1/calendar/events/{eventId}` | `:164` | CalendarUpdate | Sửa phòng/ghi chú |
| DELETE | `/api/v1/calendar/events/{eventId}` | `:177` | CalendarDelete | Huỷ buổi |
| POST | `/api/v1/calendar/teachers/delete-bulk` | `:190` | CalendarDelete | Huỷ lịch theo GV/khoảng ngày |
| POST | `/api/v1/calendar/batches/{batchId}/publish` | `:201` | CalendarPublish | Publish |
| POST | `/api/v1/calendar/batches/{batchId}/cancel` | `:211` | CalendarDelete | Cancel batch |
| GET/POST | `/api/v1/calendar/rooms` | `:221` / `:231` | CalendarRead / CalendarCreate | Phòng |
| POST | `/api/v1/calendar/import-excel` | `:266` | CalendarImport | Stub: luôn ném `NotImplementedException` (`CALENDAR_EXCEL_IMPORT_NOT_IMPLEMENTED`) |
| POST | `/api/v1/reports/jobs/assignment-progress` | `ReportJobController.java:43` | ReportingRead | Enqueue job |
| GET | `/api/v1/reports/jobs/{jobId}` | `:60` | ReportingRead | Trạng thái |
| GET | `/api/v1/reports/jobs/assignments/{assignmentId}/progress` | `:67` | ReportingRead | Báo cáo đồng bộ |
| GET | `/api/v1/reports/jobs/{jobId}/download` | `:86` | ReportingRead | Tải JSON/CSV |
| POST | `/api/v1/lcms/quizzes` | `LcmsQuizAuthoringController.java:45` | LcmsQuizCreate | Tạo quiz (idempotent) |
| PUT/PATCH | `/api/v1/lcms/quizzes/{quizId}/draft` | `:56` / `:69` | LcmsQuizUpdate | Lưu draft |
| POST | `/api/v1/lcms/quizzes/{quizId}/slides` | `:82` | LcmsQuizUpdate | Upsert slide |
| POST | `/api/v1/lcms/quizzes/{quizId}/reorder` | `:95` | LcmsQuizUpdate | Sắp xếp slide |
| POST | `/api/v1/lcms/quizzes/{quizId}/validate` | `:108` | LcmsQuizUpdate | Validate trước publish |
| POST | `/api/v1/lcms/quizzes/{quizId}/publish` | `:118` | LcmsQuizPublish | Publish |
| POST | `/api/v1/lcms/quizzes/{quizId}/versions/{versionId}/fork` | `:134` | LcmsQuizCreate | Fork version |
| PATCH | `/api/v1/lcms/quizzes/{quizId}` | `:147` | LcmsQuizUpdate | Đổi tên / loại (`title`, `kind`) |
| DELETE | `/api/v1/lcms/quizzes/{quizId}` | `:158` | LcmsQuizDelete | Xoá quiz |
| POST/PUT/DELETE | `/api/v1/lcms/questions[/{id}]` | `LcmsQuestionBankAuthoringController.java:29,43,58` | LcmsQuestion* | Tạo/sửa/archive câu hỏi |
| POST | `/api/v1/lcms/media/presign` | `LcmsMediaController.java:56` | LcmsMediaWrite | Presign |
| PUT | `/api/v1/lcms/media/upload/quiz-media/{tenantId}/{prefix}/{filename}` | `:35` | `@ReviewedPublicEndpoint` (URL ký) | Upload local |
| POST | `/api/v1/lcms/media/{mediaId}/commit` | `:66` | LcmsMediaWrite | Commit |
| GET | `/api/v1/lcms/quizzes/{quizId}/bundle` | `LcmsQuizBundleController.java:42` | LcmsQuizBundleRead | Bundle active |
| GET | `/api/v1/lcms/quiz-versions/{id}/bundle` | `:53` | LcmsQuizBundleRead | Bundle theo version |
| GET | `/api/v1/lcms/quiz-packages/{id}/manifest` / `/download` / `/updates` | `:64` / `:75` / `:94` | LcmsQuizBundleRead | Manifest / redirect tải / update feed |
| POST | `/api/v1/lcms/quiz-packages/{id}/revoke` | `:109` | LcmsQuizPublish | Thu hồi gói |
| POST | `/api/v1/lcms/schools`, PUT/PATCH/DELETE `/{id}`, POST `/{id}/restore`, `/{id}/academic-years[/{yearId}/activate|close]`, `/{id}/grades[/sync-defaults]`, PATCH `/{id}/grades/{gradeId}` | `SchoolCommandController.java:35-109` | LcmsSchool*/LcmsClassManage | Quản trị trường/năm học/khối |
| POST/PUT/PATCH/DELETE | `/api/v1/lcms/schools/{id}/classes[/{classId}]`, `/students[/{id}]`, `/students/import|bulk`, `/subjects[/{id}]`, `/enrollments`, `/enrollments/bulk` | `LmsOperationalCommandController.java:38-118` | LcmsClassManage/StudentManage/SubjectManage/EnrollmentManage | Lớp/học sinh/môn/ghi danh; `GET /subjects` luôn 404 (`:91-92`) |
| * | `/api/v1/insight/*`, `/api/v1/audit/logs[/{id}]`, `/api/v1/operations/*` | `AnalyticsOperationsController.java` | (không có) | Prototype, chỉ profile `prototype` |

### 5.2 GraphQL

| Operation | Resolver:dòng | Quyền |
|---|---|---|
| `lcms.teachingCalendarWorkspace(input)` | `CalendarLcmsQueryGraphQlController.java:46` | lms.schedule.read/manage |
| `lcms.teachingScheduleBatches(input)` / `teachingScheduleBatch(input)` | `:62` / `:76` | như trên |
| `lms.myTeachingCalendar(input)` / `myStudentCalendar(input)` | `CalendarLmsQueryGraphQlController.java:39` / `:60` | lms.schedule.read_self… |
| Mutation `previewTeachingSchedule`, `createTeachingSchedule`, `updateTeachingScheduleEvent`, `deleteTeachingScheduleEvent`, `deleteTeacherSchedule`, `publishTeachingScheduleBatch`, `cancelTeachingScheduleBatch`, `createRoom`, `importTeachingScheduleFromExcel` | `CalendarMutationGraphQlController.java:58-167` | tương ứng REST; schema khai báo ở `R/graphql/modules/40-mutation-social.graphqls:8-16` |
| `lcms.quizWorkspace`, `quizDraft`, `quizVersions` | `LcmsQuizWorkspaceGraphQlController.java:33-69` | LcmsQuizWorkspaceRead |
| `lcms.questionBankWorkspace`, `accessManagementWorkspace` | `LcmsWorkspaceGraphQlController.java:29` / `:50` | LcmsWorkspaceRead / LcmsScopeRead |
| `lcms.quizQuestionAnalytics(quizVersionId)`, `assignmentReport(assignmentId,page,size)` | `LcmsReportGraphQlController.java:29` / `:40` | LcmsReportRead |
| `lcms.schoolDirectory`, `lcms.schoolOperations` | `LcmsSchoolOperationsGraphQlController.java:27` / `:41` | LcmsSchoolRead |
| Mutation `createSchoolClass`, `updateSchoolClass`, `archiveSchoolClass`, `createSchoolStudent`, `updateSchoolStudent`, `archiveSchoolStudent`, `importSchoolStudents`, `createSchoolSubject`, `updateSchoolSubject`, `archiveSchoolSubject`, `enrollSchoolStudent`, `bulkEnrollSchoolStudents` | `LcmsSchoolOperationsMutationGraphQlController.java:38-156` | LcmsClass/Student/Subject/EnrollmentManage; schema `R/graphql/modules/45-school-operations-mutations.graphqls` |

Schema calendar: `R/graphql/modules/50-calendar.graphqls` (enum `TeachingScheduleStatus {DRAFT PUBLISHED CANCELLED}`, `TeachingScheduleResolveMode {REJECT DELETE_CONFLICTING}`, `TeachingCalendarViewMode {ALL BY_SCHOOL BY_TEACHER BY_SUBJECT}`); khai báo field query ở `00-shell.graphqls:8-45`; schema tổng `R/graphql/schema.graphqls`. Lưu ý enum `viewMode` GraphQL không có `BY_CLASS` dù `laneSummaries` có xử lý `CLASS/BY_CLASS` (`CalendarWorkspaceAssembler.java:73`), REST nhận chuỗi tự do.

---

## 6. Dữ liệu

### Calendar (migration V17, V18 FK validate, V20, V21 seed, V22 index)
- `teaching_schedule_batches` (entity `CalendarBatch`): `tenant_id`, `school_id`, `academic_year`, `apply_from`, `repeat_weeks` (CHECK 1..52), `status` (CHECK DRAFT/PUBLISHED/CANCELLED), `source` (mặc định `LCMS_BULK_GRID`), `idempotency_key`, `payload_hash`, `note`, `created_by`, `published_by/at`. UNIQUE partial `(tenant_id, idempotency_key)`; index `(tenant_id, school_id, status, created_at desc)` (V22).
- `teaching_schedule_events` (`CalendarEvent`): `batch_id` (FK CASCADE khi xoá batch), `school_id`, `academic_class_id`, `subject_id`, `level_id`, `room_id`, `room_name`, `title`, `start_at/end_at` (CHECK `start_at<end_at`), `weekday` (1..7), `session`, `period_start`, `period_count` (1..10), `status`, `note`. Index theo (tenant, school/class/subject/level, status, time) và index room theo `lower(room_name)` cho status ≠ CANCELLED (V22). `CalendarEvent` có `@ManyToOne CalendarRoom` read-only (`B/model/calendar/CalendarEvent.java:48-50`).
- `teaching_schedule_event_teachers` (`CalendarEventTeacher`): role ∈ {MAIN, ASSISTANT}, UNIQUE `(event_id, teacher_user_id, role)`, `display_name_snapshot`, `color_key`.
- `teaching_schedule_rooms` (`CalendarRoom`): UNIQUE `(school_id, name)` (phân biệt hoa thường ở DB), status ACTIVE/INACTIVE.
- `teaching_schedule_conflicts` (`CalendarConflict`): conflict_type CHECK 3 loại, severity CHECK ERROR/WARNING, `resolved`, `resolved_by/at`. Không có FK/unique tới event.
- Không có exclusion constraint chống chồng giờ ở DB: chống trùng chỉ bằng code + advisory lock theo `(tenant, school)`.

### Reporting
- `report_jobs` (V67 + V91, entity `ReportJob`): trạng thái queued/processing/completed/failed, `progress 0..100`, `attempts`, `available_at`, `lease_until`, `request_json`/`result_json` (jsonb), `format` (json|csv, CHECK `ck_report_jobs_format`), `expires_at` (+ index `idx_report_jobs_expiry` partial). Index nhặt job `idx_report_jobs_pickup`. RLS bật + force (policy `report_jobs_tenant_isolation`, cho phép khi `app.maintenance_mode='true'`).
- `assignment_progress_aggregates`, `report_reconciliation_queue` + hàm `erg_apply_assignment_progress_delta`, `erg_reconcile_assignment_progress`, trigger `trg_student_progress_assignment_aggregate` trên `student_progress` (V69); RLS tương tự.

### Analytics
- Không tìm thấy bảng/migration cho `Event`, `Visit`, `FirebaseSyncEvent` (tìm bằng từ khoá `analytics|insight` trong `R/db`); 3 class là POJO.

### LCMS
- Bảng quiz/question (V23, V24, V32…) thuộc slice quiz — chỉ được dùng qua repository ở `service/lcms/readmodel`.

---

## 7. Tích hợp ngoài và sự kiện

- **Spring Modulith**: `@ApplicationModule` ở `B/calendar/package-info.java`, `B/lcms/package-info.java`, `B/reporting/package-info.java`. `CalendarBatchPublished` được publish bằng `ApplicationEventPublisher` trong transaction publish (`CalendarBatchLifecycleUseCase.java:80-81`) và consume bằng `@ApplicationModuleListener` (`CalendarBatchPublishedConsumer.java:26`) → event publication registry (giao hàng at-least-once theo javadoc `CalendarBatchPublished.java:6`). Test `CalendarPublishedScenarioTest` xác nhận chỉ 1 notification idempotent.
- **Notifications**: `NotificationCommandService.enqueue(NotificationCommand)` — type `SCHEDULE_PUBLISHED`, deep link `/calendar` (chi tiết kênh email/push nằm ngoài slice).
- **Cache** (Redis/Caffeine theo `*CacheDescriptorContributor`, chưa đọc backend): `calendarWorkspace` (30s/64), `lcmsQuizWorkspace` & `lcmsQuestionBankWorkspace` (30s/128). Evict: calendar mọi lệnh ghi `allEntries` (`CalendarCommandUseCase.java:38-44`); LCMS evict từ `QuestionBankAuthoringService`, `QuizAuthoringService`, `QuizDraftService`, `QuizPublishService:480-481`, `QuestionBankDemoBootstrapSeedService:58`. Lưu ý key của cache LCMS không chứa principal (chỉ tenant + tham số).
- **Scheduler**: `ReportWorker` và `ReportAggregateReconciliationWorker` (`@Scheduled fixedDelay` 5s; tắt bằng `erg.reporting.worker.enabled=false` / `erg.reporting.reconciliation.enabled=false`).
- **PostgreSQL**: advisory lock, `FOR UPDATE SKIP LOCKED`, RLS, trigger/function.
- **Media/Storage**: URL upload ký (chữ ký + tenant + checksum + hạn), xử lý ở `MediaService` (ngoài slice); test `LcmsMediaControllerUploadTest`.
- **Email/push/AI**: slice này không có tích hợp trực tiếp.
- **Excel import lịch**: chưa triển khai (REST/GraphQL ném `NotImplementedException`; `CalendarExcelImportService` profile `prototype`).

---

## 8. Test hiện có

Kiểm chứng được (theo tên test, đã liệt kê):
- **Calendar**: `CalendarCommandServiceTest` (986 dòng): preview, create, lock không lấy được, idempotency (cùng/khác payload), warning cần override, `REJECT`, `DELETE_CONFLICTING` (giải quyết đủ/còn lại), publish (+event durable), publish batch rỗng, cancel, sửa/huỷ event, huỷ theo GV, phòng. `CalendarConflictServiceTest` (1114 dòng) rất đủ (class/teacher/room, trong cùng batch, liền kề, tenant khác…). `CalendarExpansionServiceTest`, `CalendarValidationServiceTest`, `CalendarBulkInputNormalizerTest`, `CalendarReadServiceTest` (workspace/filter/lane/catalog ≤500/permissions/colour), 3 test GraphQL controller (quyền, deny, tenant spoofing, scope trường, wildcard), `CalendarAuthorizationPolicyContractTest`, nhiều test kiến trúc `Calendar*ArchitectureTest` (ràng buộc phụ thuộc use case), repository test (`CalendarEventRepositoryTest`, `CalendarEventTeacherRepositoryTest`), `CalendarBatchPublishedConsumerTest`, `CalendarPublishedScenarioTest`.
- **Reporting**: `ReportJobUseCaseTest` (replay cùng request, đổi format cùng key → conflict), `ReportingOperationsTest` (assignment ngoài tenant bị chặn trước enqueue), `ReportWorkerTest` (sinh báo cáo hoàn tất; report quá lớn không được đánh dấu hoàn tất cắt cụt), `ReportAggregateReconciliationWorkerTest`, `ReportingAnalyticsServiceTest` (KPI), `ReportJobMigrationContractTest`, `ReportingExportMigrationContractTest`.
- **LCMS**: `LcmsOperationsTest`, `LcmsQuizBundleBoundaryTest`, `LcmsQuizBundleResponseTest`, `LcmsReadGraphQlBoundaryTest`, `LcmsReportGraphQlBoundaryTest`, `LmsOperationalCommandBoundaryTest`, `LcmsSchoolOperationsMutationGraphQlControllerTest`, `LcmsQuizAuthoringControllerTest`, `LcmsQuestionBankAuthoringControllerTest`, `LcmsWorkspaceGraphQlControllerTest`, `LcmsWorkspaceReadServiceTest`, `LcmsQuizWorkspaceReadServiceTest` (chỉ test chặn version history không giới hạn), `QuestionBankWorkspaceQueryCountIntegrationTest`, `LcmsMediaControllerUploadTest`.

Khoảng trống rõ ràng (không thấy test tương ứng trong danh sách):
- Không có test cho `ReportJobController` (REST: validation filter, download expired/CSV/409), không test `ReportJobUseCase` expiry/đọc job của user khác.
- Không có test đa-giao dịch cho hành vi "DELETE_CONFLICTING nhưng vẫn trả conflict còn lại" xem commit hay rollback (test hiện chỉ mock — `shouldReturnRemainingConflictsWhenResolutionIncomplete`).
- Không có test cho `CalendarRestController` (REST) — chỉ GraphQL controller được test; không test `deleteTeacherSchedule` với `dateFrom/dateTo` null.
- Không có test publish batch có event đã CANCELLED riêng lẻ.
- Không có test cho `LcmsQuizBundleController` hành vi ETag/304/redirect (chỉ test ranh giới + response mapping).
- Không có test cho `LcmsQuizWorkspaceReadService.quizWorkspace`/`quizDraft`, `LcmsSchoolOperationsGraphQlController`, `SchoolCommandController`.
- Không có test cho `AnalyticsOperationsController` (prototype).
- Không có test tích hợp Postgres cho `ReportJobRepository.findDueForUpdate` (SKIP LOCKED) ngoài test hợp đồng migration.

---

## 9. Rủi ro, nợ kỹ thuật, nghi ngờ bug

1. **Publish có thể "hồi sinh" buổi đã huỷ** — `CalendarBatchLifecycleUseCase.java:66,77-78` lấy *mọi* event của batch (không lọc status) và `updateStatusBatch` (`B/repository/calendar/CalendarEventRepository.java:155-163`, không điều kiện status) đặt tất cả thành PUBLISHED. Kịch bản: buổi bị huỷ bởi `deleteEvent` hoặc bị `DELETE_CONFLICTING` của batch khác (`CalendarBulkCreateUseCase.java:185-192`) rồi batch DRAFT chứa nó được publish → buổi CANCELLED trở lại PUBLISHED, có thể chồng giờ GV/lớp. Chưa có test. Publish cũng không chạy lại `detectConflicts`.
2. **`DELETE_CONFLICTING` có thể huỷ buổi cũ nhưng không tạo batch mới và vẫn commit** — `CalendarBulkCreateUseCase.java:185-199` và `:205-208` thực hiện `updateStatusBatch(... CANCELLED ...)` rồi `return` payload (không ném exception) khi còn conflict chưa giải quyết hoặc còn WARNING chưa override ⇒ transaction commit với các buổi cũ đã bị huỷ mà lịch mới không được tạo. Nghi ngờ (cần test tích hợp xác nhận).
3. **`deleteTeacherSchedule` NPE khi thiếu ngày** — `CalendarEventMutationUseCase.java:76-77` gọi `LocalDate.parse(dateFrom/dateTo)`; schema GraphQL cho `dateFrom/dateTo` là nullable (`50-calendar.graphqls:12`) và `DeleteTeacherScheduleRequest` (`CalendarRequests.java:111-117`) không có `@NotBlank` ⇒ giá trị null gây `NullPointerException` (500). Ngày sai định dạng ném `DateTimeParseException`, không phải `BadRequestException`.
4. **Wildcard + không truyền schoolIds** — `CalendarScopeService.requireManagedSchools` (`:47-49`) trả danh sách rỗng cho wildcard; `findLcmsWorkspaceEvents` dùng `e.schoolId IN :schoolIds` (`CalendarEventRepository.java:33`) với collection rỗng ⇒ nghi ngờ trả rỗng hoặc lỗi SQL tuỳ Hibernate (chưa xác minh; test controller dùng mock). Tương tự `resolveManagedSchoolIds` (`CalendarBulkCreateUseCase.java:300-305`) có thể rỗng cho wildcard làm `resolveConflictsByDeletion` bỏ qua mọi buổi (`CalendarConflictService.java:270`) — nghi ngờ.
5. **Sửa buổi không kiểm tra trạng thái/phòng** — `CalendarEventMutationUseCase.java:45-56`: cho sửa cả buổi CANCELLED, `roomId` không được kiểm tra thuộc phòng ACTIVE của trường (bulk có kiểm, `CalendarBulkReferenceDataUseCase.java:117-127`), đổi `roomName` không chạy lại phát hiện xung đột phòng. `findEvent` dùng `findById` rồi mới so tenant (`:95-103`) — lộ khác biệt 403/404 giữa tenant.
6. **Xung đột phòng so theo tên, không theo `roomId`** (`CalendarConflictService.java:157-166`, `roomKey :234-236`); chỉ là WARNING. Nhiều phòng trùng tên (khác chữ hoa) coi là một.
7. **Phạm vi giờ lệch**: `CalendarBulkInputNormalizer.parseTime` trả `LocalTime.toString()` (có thể `HH:mm:ss` nếu có giây, `:92-99`), trong khi `CalendarExpansionService` parse bằng `"HH:mm"` (`:83-84`) ⇒ nghi ngờ lỗi parse khi người dùng gửi giờ có giây.
8. **Quyền cho Calendar UI không khớp API** — `CalendarPermissionsQueryUseCase.java:22-27` chỉ bật cờ khi có `lms.schedule.manage`/`*`; user có riêng `lms.schedule.create` được API cho phép nhưng nhận `canCreate=false`.
9. **Job báo cáo không gắn với người yêu cầu** — `ReportingOperations.status` (`:79-81`) và `/download` chỉ lọc `tenant`; bất kỳ user có `ReportingRead` trong tenant biết `jobId` đều đọc được kết quả (có tên học sinh, điểm) của lớp mà họ không quản lý. Nghi ngờ rủi ro quyền (RLS chỉ theo tenant).
10. **Retry job không phân biệt lỗi vĩnh viễn** — `ReportWorker.java:119-121` thử lại tối đa 5 lần cả với "report exceeds 10,000 rows" hay "unsupported report type"; lỗi cuối `failed` với `progress=0`. Kết quả toàn bộ (≤10.000 dòng) lưu trong `result_json`; `expires_at` chỉ được kiểm tra khi tải — không thấy job dọn dẹp/purge job hết hạn trong slice (nghi ngờ tăng dung lượng bảng).
11. **CSV chưa chống formula injection** — `ReportWorker.csvCell` (`:193-197`) chỉ escape dấu `"`; `studentName` do người dùng nhập có thể bắt đầu bằng `=`/`+`/`-`/`@` (nghi ngờ khi mở bằng Excel). Chỉ đóng gói `text/csv`, không BOM.
12. **Reconciliation worker không ghi lỗi** — `ReportAggregateReconciliationWorker.java:36-39` / store `:45-57`: exception trong `reconcile` làm vòng lặp dừng, task còn `processing` tới khi lease 2 phút hết; `attempts` tăng nhưng không có trần/`last_error`/dead-letter ở code Java (cột `last_error` tồn tại trong trigger V69).
13. **Cache LCMS không theo principal** — `lcmsQuizWorkspace`/`lcmsQuestionBankWorkspace` (key chỉ tenant+tham số) có thể trả cùng dữ liệu cho mọi user cùng tenant; hiện không có lọc theo user nên có vẻ có chủ ý. TTL 30s nên dữ liệu có thể cũ tới 30s sau thay đổi ngoài các đường evict đã liệt kê.
14. **Chuỗi cứng không i18n** — "Toan cong ty", "Academic team", "phut" trong `LcmsWorkspaceReadService.java:419,438,459,461,468`; nội dung notification không dấu và dùng schoolId (`CalendarBatchPublishedConsumer.java:37-39`); thông báo lỗi validate lịch tiếng Việt không dấu.
15. **Không nhất quán version khi archive** — REST `DELETE .../classes|students|subjects/{id}` nhận `version` tuỳ chọn (`LmsOperationalCommandController.java:53,72,102`), GraphQL archive bắt buộc `version` (`LcmsSchoolOperationsMutationGraphQlController.java:61-66,192-203`). Mutation GraphQL `updateSchoolSubject`, `archiveSchoolSubject`, `enrollSchoolStudent`, `bulkEnrollSchoolStudents` không gọi `assertEducationUnitScope` (`:120-156`) trong khi class/student làm — nghi ngờ, cần xem `LmsOperationalOperations` để biết scope có được kiểm tra bên trong không.
16. **Nợ kỹ thuật cấu trúc**: nhiều lớp `@Deprecated` tương thích (`CalendarCommandService`, `CalendarReadService` với `ReferenceMaps` nhân đôi, `ReportJobService`, `Lcms*Compatibility`, `CalendarReferenceMaps` chứa entity JPA), constructor kép `ObjectProvider` + instance (`CalendarBulkCreateUseCase.java:98-120` v.v.) chỉ để phục vụ test cũ; `CalendarDataLoaderRegistry` đăng ký 2 DataLoader không có nơi dùng (grep không thấy tham chiếu) và truy vấn `findByEventIdIn`/`findByBatchIdIn` không lọc tenant (`:63-64`, `:98-99`) — dead code nhưng nguy hiểm nếu được nối vào sau.
17. **Phòng**: `findBySchoolIdAndNameIgnoreCase(schoolId, name)` không lọc tenant (`CalendarRoomRepository.java:11`, dùng ở `CalendarRoomCommandUseCase.java:41`); phụ thuộc schoolId duy nhất toàn hệ thống.
18. **Hiệu năng**: mỗi lần mở workspace luôn nạp full catalog (≤500 mục/loại, `CalendarWorkspaceQueryUseCase.java:151-152`); phát hiện xung đột nạp toàn bộ buổi lớp/phòng trong cửa sổ min–max (tới 52 tuần, `CalendarConflictService.java:88-114`); `ReportWorker.updateProgress` mở 1 transaction mỗi trang.
19. **Advisory lock** dùng `tenantId.hashCode()*31 + schoolId.hashCode()` (`CalendarBulkCreateUseCase.java:288`) — có thể va chạm giữa các cặp khác nhau (chỉ gây 409 giả); không bảo vệ xung đột giáo viên giữa nhiều trường chạy song song.
20. **Prototype controller** `AnalyticsOperationsController` không có `@PreAuthorize`; an toàn nhờ `@Profile("prototype")` — cần đảm bảo profile này không bật ở production (chưa kiểm tra cấu hình).
21. **`GET /api/v1/lcms/subjects` luôn 404** (`LmsOperationalCommandController.java:90-92`) — placeholder cố ý "fail-closed".

---

## 10. Câu hỏi còn mở

1. Hành vi commit/rollback thực tế của `DELETE_CONFLICTING` khi còn conflict hoặc WARNING (mục 9.2) có phải ý định thiết kế không?
2. Có chủ đích cho publish đặt lại các buổi CANCELLED thành PUBLISHED không (mục 9.1)? Có quy tắc "buổi huỷ riêng lẻ phải được loại khỏi publish" ở nơi khác không?
3. Principal wildcard (`*`) kỳ vọng nhìn thấy toàn bộ trường khi không truyền `schoolIds` (workspace LCMS)? Hiện code trả `IN ()` rỗng — cần kiểm tra thực nghiệm trên Postgres.
4. `GET /reports/jobs/{jobId}` có cần giới hạn theo `requested_by`/lớp? Ai có `lms.report.read` trong tenant được phép xem mọi job?
5. Ai/cái gì dọn `report_jobs` hết hạn (`expires_at`)? Có job retention ở ngoài slice không?
6. `LmsOperationalOperations`/`SchoolOperations` thực thi quy tắc nghiệp vụ nào (trạng thái lớp/học sinh, version, idempotency, scope theo trường/lớp)? Cần tài liệu hoá ở module riêng.
7. Backend cache thực tế (Caffeine/Redis) và cơ chế evict đa-instance cho `calendarWorkspace`/`lcms*Workspace`.
8. `ModuleWorkflowService` + profile `prototype`: các endpoint `/insight`, `/audit`, `/operations` có được dùng ở môi trường thật không? Dữ liệu analytics (`Visit`, `Event`) lưu ở đâu?
9. `CalendarDataLoaderRegistry` còn dùng không (GraphQL schema hiện trả `events` phẳng, không field resolver lồng)?
10. Excel import lịch: kế hoạch triển khai (hiện stub), và có cần endpoint REST `import-excel` trong contract công bố không?
11. `QuizReportService` (`assignmentReport`, `questionAnalyticsByVersion`) tính "needs_review"/"not started" như thế nào — KPI `notStartedCount` suy ra ở đâu (code KPI chỉ mô tả, tính toán ở service ngoài slice)?
