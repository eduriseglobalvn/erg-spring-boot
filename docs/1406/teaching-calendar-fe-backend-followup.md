# Báo cáo FE -> BE: Teaching Calendar

**Ngày cập nhật:** 2026-06-15  
**Nguồn đã đọc:** `D:\ERG\erg-spring\docs\1406\teaching-calendar-fe-integration-guide.md`  
**Phạm vi FE tích hợp:** `D:\ERG\erg-system`  
**Phạm vi BE xử lý:** `D:\ERG\erg-spring`

## Tóm tắt trạng thái

Các điểm thiếu hoặc lệch contract trong báo cáo FE đã được xử lý trực tiếp ở BE cho module teaching calendar:

- Đã thống nhất nullable cho `colorKey` trong GraphQL schema.
- Đã bổ sung GraphQL query danh sách và chi tiết batch lịch dạy cho LCMS.
- Đã siết tenant-safety khi publish/cancel/update/delete lịch.
- Đã chuẩn hóa REST import Excel dạng multipart gồm `file` và `metadata`.
- Đã điều chỉnh REST batch list/detail dùng DTO rõ ràng thay vì `Map`.
- Đã cập nhật test liên quan và chạy xanh bộ test calendar mục tiêu.

## Các mục đã xử lý ở BE

### 1. Nullable `colorKey`

**Trạng thái:** Đã làm.

Schema trước đó khai báo một số field `colorKey` là non-null trong khi dữ liệu runtime và ví dụ trong guide cho phép `null`.

Đã cập nhật:

- `TeachingCalendarLaneSummary.colorKey: String`
- `TeachingCalendarTeacherOption.colorKey: String`

Lý do: FE cần hiển thị an toàn khi lane/filter/catalog không có màu cố định.

### 2. GraphQL query cho batch list/detail

**Trạng thái:** Đã làm.

Đã bổ sung query trong namespace `lcms`:

```graphql
teachingScheduleBatches(input: TeachingScheduleBatchListInput): TeachingScheduleBatchPage!
teachingScheduleBatch(input: TeachingScheduleBatchDetailInput!): TeachingScheduleBatchDetail!
```

Đã thêm input/response tương ứng:

- `TeachingScheduleBatchListInput`
- `TeachingScheduleBatchDetailInput`
- `TeachingScheduleBatchPage`
- `TeachingScheduleBatchSummary`
- `TeachingScheduleBatchDetail`

REST batch endpoints vẫn được giữ, nhưng FE có thể dùng GraphQL đồng bộ với publish/cancel mutation.

### 3. Tenant safety cho publish/cancel batch

**Trạng thái:** Đã làm.

`publishTeachingScheduleBatch(batchId)` và `cancelTeachingScheduleBatch(batchId)` không nhận `tenantId` từ client. BE hiện resolve tenant từ principal/header, sau đó truy vấn batch theo `tenantId + batchId`.

Đã cập nhật service/repository để tránh thao tác nhầm dữ liệu tenant khác:

- `CalendarBatchRepository.findByTenantIdAndId(...)`
- `CalendarEventRepository.findByTenantIdAndBatchId(...)`
- `CalendarCommandService.publishBatch(tenantId, batchId, userId)`
- `CalendarCommandService.cancelBatch(tenantId, batchId, userId)`

### 4. Tenant safety cho update/delete event

**Trạng thái:** Đã làm.

`updateTeachingScheduleEvent` và `deleteTeachingScheduleEvent` hiện kiểm tra event thuộc đúng tenant đã resolve trước khi cập nhật/xóa.

Ngoài ra `updateTeachingScheduleEvent` đã trả về `CalendarEventPayload.event` không-null, đúng với GraphQL schema.

### 5. REST import Excel multipart contract

**Trạng thái:** Đã làm.

Endpoint REST import Excel hiện nhận multipart rõ ràng:

- `file`: file Excel upload.
- `metadata`: chuỗi JSON parse thành `CalendarExcelImportRequest`.

BE cũng đã thêm kiểm tra scope trường học cho REST import, tương tự các flow calendar khác.

### 6. REST batch list/detail response

**Trạng thái:** Đã làm.

REST:

- `GET /api/v1/calendar/batches`
- `GET /api/v1/calendar/batches/{batchId}`

hiện dùng DTO từ `CalendarReadService` thay vì trả `Map` tạm. Response này đồng bộ với GraphQL batch page/detail.

### 7. Mapping `viewMode`

**Trạng thái:** Đã làm.

`CalendarReadService` đã xử lý các mode FE đang dùng:

- `ALL`
- `BY_SCHOOL`
- `BY_TEACHER`
- `BY_SUBJECT`
- `BY_CLASS`

Các alias cũ như `SCHOOL`, `TEACHER`, `SUBJECT`, `CLASS` vẫn được hỗ trợ.

## Các mục cần xác nhận thêm nhưng không chặn tích hợp API hiện tại

### 1. Mutation namespace

**Trạng thái:** Giữ nguyên theo guide.

Các mutation calendar vẫn là top-level mutation:

- `createTeachingSchedule`
- `previewTeachingSchedule`
- `updateTeachingScheduleEvent`
- `deleteTeachingScheduleEvent`
- `deleteTeacherSchedule`
- `publishTeachingScheduleBatch`
- `cancelTeachingScheduleBatch`
- `createRoom`
- `importTeachingScheduleFromExcel`

Authorization dựa vào token/principal và portal/tenant context, không đặt mutation dưới namespace `lcms` hoặc `lms`.

### 2. Phạm vi edit event

**Trạng thái:** Chưa mở rộng ngoài contract hiện tại.

`updateTeachingScheduleEvent` hiện vẫn chỉ hỗ trợ các field đã có trong contract:

- `roomId`
- `roomName`
- `note`

Nếu sản phẩm cần sửa thời gian, lớp, môn, level hoặc phân công giáo viên ngay trên event đã publish, cần bổ sung yêu cầu nghiệp vụ riêng vì có thể ảnh hưởng conflict detection, audit và batch integrity.

### 3. Room option nullable

**Trạng thái:** Chưa đổi.

Schema vẫn giữ:

```graphql
type TeachingCalendarRoomOption { id: String label: String! schoolId: String }
type TeachingScheduleRoomOption { id: String! label: String! schoolId: String! }
```

Lý do: catalog có thể cần biểu diễn room/ad-hoc room chưa chuẩn hóa ID. `createRoom` vẫn trả room đã lưu nên `id` non-null.

### 4. Student calendar screen

**Trạng thái:** FE/product follow-up.

BE guide có `lms.myStudentCalendar`. Đây không phải thiếu BE API trong đợt này, mà là việc FE cần wire vào màn hình học viên nếu sản phẩm cần.

### 5. Preview conflict UX

**Trạng thái:** FE/product follow-up.

BE đã có `previewTeachingSchedule` và `createTeachingSchedule(resolveMode: REJECT)`. Flow UX có bắt buộc preview trước create hay không là quyết định sản phẩm/FE.

## Kiểm thử

Đã chạy thành công:

```powershell
.\mvnw.cmd "-Dtest=CalendarCommandServiceTest,CalendarMutationGraphQlControllerTest,CalendarLcmsQueryGraphQlControllerTest,CalendarReadServiceTest" test
```

Kết quả:

- `Tests run: 124`
- `Failures: 0`
- `Errors: 0`
- `BUILD SUCCESS`

Compile main source cũng đã chạy thành công:

```powershell
.\mvnw.cmd -DskipTests compile
```

## Ghi chú môi trường test

Khi chạy thêm `TeachingScheduleSecurityTest`, Spring context hiện bị chặn bởi migration PostgreSQL chạy trên H2:

- File: `src/main/resources/db/migration/postgres/V11__relationship_integrity_seed_cleanup.sql`
- Nguyên nhân: H2 không hỗ trợ cú pháp PostgreSQL `DELETE ... USING`.

Đây là lỗi tương thích môi trường test/migration, không phải lỗi contract teaching calendar vừa xử lý. Nếu muốn chạy full security test trên H2, cần tách migration theo vendor hoặc chỉnh cấu hình Flyway test để không chạy migration PostgreSQL-only trên H2.
