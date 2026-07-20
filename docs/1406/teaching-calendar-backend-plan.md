# Kế hoạch BE lịch giảng dạy LCMS/LMS GraphQL-first — Bản chi tiết (v2)

Ngày lập kế hoạch: 2026-06-14
Ngày cập nhật: 2026-06-14 (sau khi quét toàn bộ source code)
Người lập: Senior Engineer Review

Phạm vi: `erg-spring` backend cho màn hình lịch giảng dạy của LCMS và LMS trong `erg-system`.

---

## 0. Kết quả quét source code — Hiện trạng thực tế

### 0.1 Những gì ĐÃ CÓ trong codebase

| Layer | Status |
|-------|--------|
| GraphQL schema | `src/main/resources/graphql/schema.graphqls` — có `Query` với `lms`, `lcms`, `elearning`, `crm` namespace. **Chưa có `Mutation`.** |
| GraphQL controller pattern | `@SchemaMapping(typeName = "LmsQuery", field = "classWorkspace")` — đã có 5 controller theo pattern này. **Chưa có mutation controller nào.** |
| GraphQL security | `GraphQlAccessSupport` (`currentPrincipal()`, `assertPermission()`, `verifiedTenantId()`, `assertEducationUnitScope()`, `assertClassScope()`) — hoạt động tốt. |
| GraphQL guard | `GraphQlGuardFilter` (rate limit, depth limit, alias limit, introspection guard) — đã có. |
| SecurityConfig | `/api/v1/graphql` yêu cầu `authenticated()`. Không cần sửa gì thêm. |
| WorkShift entity | `model/organization/WorkShift.java` — entity `work_shifts` với `userId`, `schoolId`, `room`, `teachingSubject`, `startTime`, `endTime`, `type`, `status`. **Không dùng cho lịch giảng dạy vì thiếu quan hệ class/subject/level/teacher role.** |
| Notification system | `NotificationCommandService.enqueue(NotificationCommand)` — đã hoàn thiện, hỗ trợ userId list + topic + idempotency. Test đã dùng event type `"SCHEDULE_PUBLISHED"` và source type `"schedule"`. |
| Notification outbox | `NotificationOutboxWorker` scheduled worker — đã có. |
| Classroom entity | `model/lms/Classroom.java` → table `academic_classes`: `id`, `tenantId`, `schoolId`, `name`, `grade`, `academicYear`, `status`, `homeroomTeacherId`. |
| Subject entity | `model/lms/Subject.java` → table `lms_subjects`: `id`, `tenantId`, `name`, `code`, `status`, `scope` (jsonb). |
| Level entity | `model/lms/Level.java` → table `lms_levels`: `id`, `tenantId`, `subjectId`, `name`, `code`, `order`, `status`. |
| User entity | `model/users/User.java` — có `workShifts` (Set<WorkShift>). Cần thêm `teachingScheduleEventTeachers` nếu muốn navigation 2 chiều. |
| Student entity | Tồn tại trong `model/lms/` — có `authUserId` map với `User.id`. |
| Room entity | **KHÔNG CÓ.** `WorkShift.room` là String thường. Không có bảng `rooms`. |
| Response pattern | Container class (`LmsClassWorkspaceResponses`) chứa các Java `record` bên trong. Không dùng Lombok cho DTO. |
| BaseEntity | `@MappedSuperclass` với `id` (String 36, UUID v7), `createdAt`, `updatedAt` — tự động generate id qua `IdGenerator.newId()`. |
| Permissions hiện có | `*`, `profile.self`, `lms.read`, `lms.manage`, `lms.class.read`, `lms.unit.read`, `lms.assignment.read_self`, `lms.assignment.submit`, `lms.scope.read`, v.v... **Không có `lms.schedule.*`.** |
| Roles hiện có | `system.super_admin`, `lms.admin`, `lms.instructor`, `lms.learner`, `seo.staff`, `seo.lead`, `seo.manager`, `center.manager`, `school.manager`, `executive.director`. |
| ErgPolicyService | Có `ROLE_PERMISSIONS` map — cần cập nhật để thêm schedule permissions cho `lms.admin`, `lms.teacher`, `lms.student`. |
| Migration version | Đến `V16__validate_relationship_foreign_keys.sql`. Migration mới sẽ là **`V17`** (tạo schema + seed) + **`V18`** (validate FK). |
| Test pattern | Mockito `@ExtendWith(MockitoExtension.class)`, manual controller construction, `SecurityContextHolder` mock, `ErgPrincipal` builder với `attributes` map. |
| Java/Spring version | Java 25, Spring Boot 4.0.6. |

### 0.2 Những gì CHƯA CÓ — cần xây dựng từ đầu

- ❌ `type Mutation` trong GraphQL schema
- ❌ Mutation controller pattern (chưa có tiền lệ trong codebase)
- ❌ `teaching_schedule_*` tables
- ❌ `TeachingSchedule*` entities
- ❌ `TeachingSchedule*` repositories + projections
- ❌ Schedule read/command/expansion/conflict services
- ❌ Schedule permissions (`lms.schedule.*`)
- ❌ Notification wiring cho schedule events
- ❌ Room entity/table (có thể để V2)
- ❌ DataLoader / batch loading (chưa có trong codebase)

---

## 1. Kết luận kiến trúc

Calendar đi theo hướng **GraphQL-first**, không phải REST-first.

Lý do:
- Màn hình calendar cần lấy một workspace trọn vẹn trong một request: events, bộ lọc, danh sách trường/lớp/giáo viên/môn/level, summary, permission của user hiện tại.
- `erg-spring` đã có GraphQL workspace pattern: `LmsQuery`, `LcmsQuery`, `GraphQlAccessSupport`, `schema.graphqls`, guard/rate-limit cho `/api/v1/graphql`.
- FE đã có `graphQlRequest` trong `erg-system`, các màn LMS/LCMS khác đang dùng GraphQL read model.
- Calendar cần filter theo khoảng ngày và role/scope; nếu chia thành nhiều REST call thì FE dễ bị hardcode filter, khó bảo mật và khó đồng bộ.

REST chỉ giữ vai trò phụ cho phase sau nếu cần import/export file lớn, download audit report, hoặc webhook tích hợp ngoài hệ thống.

---

## 2. Domain model chi tiết

Backend là source of truth. FE filter chỉ là UX, không phải security boundary.

### 2.1 `teaching_schedule_batches` — Lưu một lần tạo lịch từ LCMS

```sql
CREATE TABLE teaching_schedule_batches (
    id                VARCHAR(36) PRIMARY KEY,
    tenant_id         VARCHAR(36) NOT NULL,
    school_id         VARCHAR(36) NOT NULL,
    academic_year     VARCHAR(20),
    apply_from        DATE NOT NULL,
    repeat_weeks      INT NOT NULL CHECK (repeat_weeks BETWEEN 1 AND 52),
    status            VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                      CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED')),
    source            VARCHAR(30) NOT NULL DEFAULT 'LCMS_BULK_GRID',
    idempotency_key   VARCHAR(255),
    payload_hash      VARCHAR(64),
    note              TEXT,
    created_by         VARCHAR(36),
    published_by       VARCHAR(36),
    published_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes
CREATE INDEX idx_tsb_tenant_school ON teaching_schedule_batches(tenant_id, school_id);
CREATE UNIQUE INDEX idx_tsb_tenant_idempotency ON teaching_schedule_batches(tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
```

Ý nghĩa:
- Dùng để audit ai tạo lịch, lúc nào.
- Dùng để publish/cancel cả loạt (cả batch).
- Dùng để idempotency khi user bấm tạo nhiều lần.
- `payload_hash` lưu hash SHA-256 của toàn bộ input rows để so sánh idempotency.
- `source` mặc định `LCMS_BULK_GRID` để mở rộng sau này (import Excel, API external...).

### 2.2 `teaching_schedule_events` — Một buổi học cụ thể

```sql
CREATE TABLE teaching_schedule_events (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    batch_id            VARCHAR(36) NOT NULL,
    school_id            VARCHAR(36) NOT NULL,
    academic_class_id   VARCHAR(36) NOT NULL,
    subject_id          VARCHAR(36) NOT NULL,
    level_id            VARCHAR(36),
    room_id             VARCHAR(36),
    room_name           VARCHAR(255),
    title               VARCHAR(500),
    start_at            TIMESTAMPTZ NOT NULL,
    end_at              TIMESTAMPTZ NOT NULL,
    weekday             SMALLINT NOT NULL CHECK (weekday BETWEEN 1 AND 7),
    session             VARCHAR(20),
    period_start        SMALLINT NOT NULL CHECK (period_start >= 1),
    period_count        SMALLINT NOT NULL CHECK (period_count BETWEEN 1 AND 10),
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                        CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED')),
    note                TEXT,
    created_by           VARCHAR(36),
    updated_by           VARCHAR(36),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Critical indexes for range queries
CREATE INDEX idx_tse_tenant_timerange ON teaching_schedule_events(tenant_id, start_at, end_at);
CREATE INDEX idx_tse_tenant_school_timerange ON teaching_schedule_events(tenant_id, school_id, start_at, end_at);
CREATE INDEX idx_tse_tenant_class_timerange ON teaching_schedule_events(tenant_id, academic_class_id, start_at, end_at);
CREATE INDEX idx_tse_tenant_subject_timerange ON teaching_schedule_events(tenant_id, subject_id, start_at, end_at);
CREATE INDEX idx_tse_batch ON teaching_schedule_events(batch_id);
CREATE INDEX idx_tse_tenant_status ON teaching_schedule_events(tenant_id, status);

-- Constraints
ALTER TABLE teaching_schedule_events ADD CONSTRAINT chk_tse_time_range CHECK (start_at < end_at);
```

Ý nghĩa:
- Đây là canonical class session. Một dòng Excel có thể sinh ra nhiều event nếu có nhiều lớp hoặc lặp nhiều tuần.
- `start_at` và `end_at` là `TIMESTAMPTZ` để lưu giờ tuyệt đối (có timezone).
- `weekday` = ISO weekday (1=Monday, 7=Sunday).
- `session` = buổi sáng/chiều/tối (`MORNING`, `AFTERNOON`, `EVENING`). Đây chỉ là metadata hiển thị, không dùng để filter logic.
- Event **không lưu teacher chính trong chính table này** vì có nhiều giáo viên — dùng join table.

### 2.3 `teaching_schedule_event_teachers` — Join table phân giải lịch cá nhân

```sql
CREATE TABLE teaching_schedule_event_teachers (
    id                    VARCHAR(36) PRIMARY KEY,
    tenant_id             VARCHAR(36) NOT NULL,
    event_id              VARCHAR(36) NOT NULL,
    teacher_user_id       VARCHAR(36) NOT NULL,
    role                  VARCHAR(20) NOT NULL CHECK (role IN ('MAIN', 'ASSISTANT')),
    display_name_snapshot VARCHAR(255),
    color_key             VARCHAR(100),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Critical indexes
CREATE UNIQUE INDEX idx_tset_event_teacher_role
    ON teaching_schedule_event_teachers(event_id, teacher_user_id, role);
CREATE INDEX idx_tset_teacher_timerange
    ON teaching_schedule_event_teachers(tenant_id, teacher_user_id);
CREATE INDEX idx_tset_event ON teaching_schedule_event_teachers(event_id);
```

Ý nghĩa:
- Đây là bảng **quyết định user nào được thấy lịch nào**.
- Giáo viên chính và trợ giảng đều có lịch cá nhân.
- `display_name_snapshot`: lưu tên hiển thị tại thời điểm tạo lịch, tránh JOIN không cần thiết khi query calendar.
- `color_key`: có thể để null, BE sẽ tự tính theo rule (xem section 7.4).

**Query pattern cho LMS teacher** (quan trọng nhất):
```sql
SELECT e.*, et.role as teacher_role, et.display_name_snapshot
FROM teaching_schedule_events e
JOIN teaching_schedule_event_teachers et ON e.id = et.event_id
WHERE e.tenant_id = ?
  AND et.teacher_user_id = ?
  AND e.start_at < ?
  AND e.end_at > ?
  AND e.status = 'PUBLISHED'
ORDER BY e.start_at;
```

Index `idx_tset_teacher_timerange` cần được mở rộng thành composite với `start_at` nếu query chậm. Xem xét tạo index phụ:
```sql
-- Nếu cần thiết sau khi test performance:
CREATE INDEX idx_tset_teacher_tenant_range
    ON teaching_schedule_event_teachers(tenant_id, teacher_user_id);
-- Join sẽ dùng index này + idx_tse_tenant_timerange trên events table
```

### 2.4 `teaching_schedule_conflicts` (OPTIONAL — V2)

V1 không tạo bảng này. Validation đồng bộ trả về ngay trong preview/create.

Nếu muốn audit conflict hoặc cho override có lý do, thêm bảng sau V1:

```sql
CREATE TABLE teaching_schedule_conflicts (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL,
    batch_id        VARCHAR(36),
    event_id        VARCHAR(36),
    conflict_type   VARCHAR(30) NOT NULL CHECK (conflict_type IN ('TEACHER_TIME', 'CLASS_TIME', 'ROOM_TIME')),
    severity        VARCHAR(20) NOT NULL DEFAULT 'ERROR' CHECK (severity IN ('ERROR', 'WARNING', 'OVERRIDDEN')),
    message         TEXT,
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

Khuyến nghị V1: không tạo bảng này trừ khi product cần lưu lịch sử override conflict.

### 2.5 Về Room (phòng học)

**Hiện tại codebase không có Room entity.** `WorkShift.room` là String thường.

Chiến lược cho V1:
- `roomId` và `roomName` trong `teaching_schedule_events` đều là **optional**.
- `roomId` = null, `roomName` = text tự do từ LCMS input.
- Room conflict detection: nếu `roomName` không rỗng, so sánh text (case-insensitive, trim). Đây là `WARNING` chứ không phải `ERROR` vì text room không chuẩn hóa.

**Tuy nhiên**, có yêu cầu thực tế: khi admin tạo lịch trên bảng Excel-like, nếu phòng chưa có trong danh sách, admin cần tạo phòng ngay tại chỗ mà không cần rời khỏi màn hình.

#### `teaching_schedule_rooms` — Bảng phòng học (V1)

```sql
CREATE TABLE teaching_schedule_rooms (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL,
    school_id       VARCHAR(36) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_by      VARCHAR(36),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_tsr_school_name UNIQUE (school_id, name)
);

CREATE INDEX idx_tsr_tenant_school ON teaching_schedule_rooms(tenant_id, school_id);
CREATE INDEX idx_tsr_tenant_school_status ON teaching_schedule_rooms(tenant_id, school_id, status);

-- FK Column Index
CREATE INDEX idx_tsr_created_by ON teaching_schedule_rooms(created_by);

-- FK
ALTER TABLE teaching_schedule_rooms
    ADD CONSTRAINT fk_tsr_school
        FOREIGN KEY (school_id) REFERENCES education_units(id)
        ON DELETE RESTRICT
        NOT VALID,
    ADD CONSTRAINT fk_tsr_created_by
        FOREIGN KEY (created_by) REFERENCES users(id)
        ON DELETE SET NULL
        NOT VALID;
```

#### JPA Entity

```java
@Entity
@Table(name = "teaching_schedule_rooms")
@Data @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TeachingScheduleRoom extends BaseEntity {
    @Column(name = "tenant_id", length = 36, nullable = false)
    private String tenantId;

    @Column(name = "school_id", length = 36, nullable = false)
    private String schoolId;

    @Column(length = 255, nullable = false)
    private String name;

    @Column(length = 20, nullable = false)
    private String status;  // ACTIVE, INACTIVE

    @Column(name = "created_by", length = 36)
    private String createdBy;
}
```

#### createRoom Mutation — Flow chi tiết

**Mục đích:** Cho phép admin tạo phòng ngay trên bảng tạo lịch mà không cần rời màn hình. Sau khi tạo, FE nhận được `id` + `label` và đưa vào dropdown chọn phòng.

**Input:** `schoolId` (trường hiện tại), `roomName` (tên phòng muốn tạo).

**Pipeline:**

1. Authorize: principal phải có `lms.schedule.manage`, `lms.manage`, hoặc `*`.
2. Validate `schoolId` thuộc scope (`managedEducationUnitIds`).
3. Validate `roomName` không rỗng, trim, length ≤ 255.
4. Check unique: `roomRepository.existsBySchoolIdAndNameIgnoreCase(schoolId, roomName)`.
   - Nếu đã tồn tại → trả về room hiện có (idempotent — không throw lỗi).
   - Nếu chưa tồn tại → tạo mới với `status = ACTIVE`.
5. Trả về `TeachingScheduleRoomOption { id, label, schoolId }`.

**Lưu ý về idempotency:**
- Nếu 2 admin cùng tạo phòng "Phòng A101" cho cùng trường, người thứ 2 sẽ nhận lại phòng đã có.
- Không cần `idempotencyKey` vì unique constraint `(school_id, name)` tự xử lý.
- Nếu transaction conflict (race condition), catch `DataIntegrityViolationException` và query lại phòng đã tồn tại.

**Catalog:** `TeachingScheduleCatalog.rooms` sẽ query từ `teaching_schedule_rooms` thay vì parse text từ `teaching_schedule_events.room_name`.

---

## 3. GraphQL schema chi tiết

### 3.1 Mutation type — LƯU Ý QUAN TRỌNG

**Đây là lần đầu tiên codebase có `type Mutation`.** Hiện tại `schema.graphqls` chỉ có `type Query`.

Cách triển khai Mutation trong Spring GraphQL:
- Sử dụng `@MutationMapping` annotation (cách recommended của Spring GraphQL).
- HOẶC `@SchemaMapping(typeName = "Mutation", field = "createTeachingSchedule")`.
- **Khuyến nghị dùng `@MutationMapping`** vì rõ ràng hơn và đúng chuẩn Spring GraphQL.

GraphQL controller mẫu cho mutation:
```java
@Controller
public class TeachingScheduleMutationGraphQlController {

    private final GraphQlAccessSupport accessSupport;
    private final TeachingScheduleCommandService commandService;

    // constructor...

    @MutationMapping
    public TeachingSchedulePreviewPayload previewTeachingSchedule(
            @Argument TeachingScheduleBulkInput input) {
        // ...
    }

    @MutationMapping
    public TeachingScheduleBulkPayload createTeachingSchedule(
            @Argument TeachingScheduleBulkInput input) {
        // ...
    }
}
```

Tất cả guard/rate-limit/depth-limit hiện tại trong `GraphQlGuardFilter` sẽ áp dụng cho mutation tự động vì cùng đi qua `/api/v1/graphql`.

### 3.2 Schema đầy đủ

Thêm vào `schema.graphqls`:

```graphql
type Mutation {
  previewTeachingSchedule(input: TeachingScheduleBulkInput!): TeachingSchedulePreviewPayload!
  createTeachingSchedule(input: TeachingScheduleBulkInput!): TeachingScheduleBulkPayload!
  updateTeachingScheduleEvent(input: TeachingScheduleEventUpdateInput!): TeachingScheduleEventPayload!
  deleteTeachingScheduleEvent(input: TeachingScheduleEventDeleteInput!): TeachingScheduleDeletePayload!
  deleteTeacherSchedule(input: DeleteTeacherScheduleInput!): TeachingScheduleDeletePayload!
  publishTeachingScheduleBatch(batchId: String!): TeachingScheduleBatchPayload!
  cancelTeachingScheduleBatch(batchId: String!): TeachingScheduleBatchPayload!
  createRoom(input: CreateRoomInput!): TeachingScheduleRoomOption!
}
```

Thêm vào `LcmsQuery`:
```graphql
  teachingCalendarWorkspace(input: TeachingCalendarWorkspaceInput!): TeachingCalendarWorkspace!
```

Thêm vào `LmsQuery`:
```graphql
  myTeachingCalendar(input: MyTeachingCalendarInput!): TeachingCalendarWorkspace!
```

### 3.3 Input types

```graphql
input TeachingCalendarWorkspaceInput {
  tenantId: String
  from: String!
  to: String!
  viewMode: TeachingCalendarViewMode! = ALL
  schoolIds: [String!]
  teacherIds: [String!]
  classIds: [String!]
  subjectIds: [String!]
  levelIds: [String!]
  statuses: [TeachingScheduleStatus!]
}

input MyTeachingCalendarInput {
  tenantId: String
  from: String!
  to: String!
  viewMode: TeachingCalendarViewMode! = ALL
  schoolIds: [String!]
  classIds: [String!]
  subjectIds: [String!]
  levelIds: [String!]
  statuses: [TeachingScheduleStatus!]
}

input TeachingScheduleBulkInput {
  tenantId: String
  idempotencyKey: String
  schoolId: String!
  academicYear: String
  applyFrom: String!
  repeatWeeks: Int!
  note: String
  overrideConflicts: Boolean
  resolveMode: TeachingScheduleResolveMode
  rows: [TeachingScheduleBulkRowInput!]!
}

enum TeachingScheduleResolveMode {
  REJECT
  DELETE_CONFLICTING
}

input TeachingScheduleBulkRowInput {
  clientRowId: String!
  classIds: [String!]!
  weekday: Int!
  session: String
  periodStart: Int!
  periodCount: Int!
  startTime: String!
  endTime: String!
  subjectId: String!
  levelId: String
  roomId: String
  roomName: String
  mainTeacherIds: [String!]!
  assistantTeacherIds: [String!]
  note: String
}

input TeachingScheduleEventUpdateInput {
  tenantId: String
  eventId: String!
  roomId: String
  roomName: String
  note: String
}

input TeachingScheduleEventDeleteInput {
  tenantId: String
  eventId: String!
}

input DeleteTeacherScheduleInput {
  tenantId: String
  teacherUserId: String!
  schoolIds: [String!]
  dateFrom: String
  dateTo: String
}

# Sửa để cho phép tạo phòng từ bảng tạo lịch
input CreateRoomInput {
  tenantId: String
  schoolId: String!
  roomName: String!
}

enum TeachingCalendarViewMode {
  ALL
  BY_SCHOOL
  BY_TEACHER
  BY_SUBJECT
}

enum TeachingScheduleStatus {
  DRAFT
  PUBLISHED
  CANCELLED
}
```

### 3.4 Output types

```graphql
type TeachingCalendarWorkspace {
  tenantId: String!
  from: String!
  to: String!
  viewMode: TeachingCalendarViewMode!
  events: [TeachingCalendarEvent!]!
  filterOptions: TeachingCalendarFilterOptions!
  laneSummaries: [TeachingCalendarLaneSummary!]!
  catalog: TeachingScheduleCatalog!
  permissions: TeachingCalendarPermissions!
  generatedAt: String!
}

type TeachingCalendarEvent {
  id: String!
  batchId: String
  title: String!
  startAt: String!
  endAt: String!
  date: String!
  startTime: String!
  endTime: String!
  schoolId: String!
  schoolName: String!
  classId: String!
  className: String!
  subjectId: String
  subjectName: String
  levelId: String
  levelName: String
  roomId: String
  roomName: String
  periodStart: Int!
  periodCount: Int!
  teachers: [TeachingScheduleTeacherAssignment!]!
  status: TeachingScheduleStatus!
  note: String
  colorGroup: TeachingCalendarColorGroup!
}

type TeachingScheduleTeacherAssignment {
  userId: String!
  displayName: String!
  role: TeachingScheduleTeacherRole!
  colorKey: String
}

enum TeachingScheduleTeacherRole {
  MAIN
  ASSISTANT
}

type TeachingCalendarFilterOptions {
  schools: [TeachingCalendarFilterOption!]!
  classes: [TeachingCalendarFilterOption!]!
  teachers: [TeachingCalendarFilterOption!]!
  subjects: [TeachingCalendarFilterOption!]!
  levels: [TeachingCalendarFilterOption!]!
}

type TeachingCalendarFilterOption {
  id: String!
  label: String!
  count: Int!
  colorKey: String
}

type TeachingCalendarLaneSummary {
  id: String!
  label: String!
  count: Int!
  colorKey: String!
}

type TeachingCalendarColorGroup {
  key: String!
  label: String!
  kind: String!
}

type TeachingScheduleCatalog {
  schools: [TeachingCalendarFilterOption!]!
  classes: [TeachingCalendarClassOption!]!
  teachers: [TeachingCalendarTeacherOption!]!
  assistantTeachers: [TeachingCalendarTeacherOption!]!
  subjects: [TeachingCalendarSubjectOption!]!
  levels: [TeachingCalendarLevelOption!]!
  rooms: [TeachingCalendarRoomOption!]!
}

type TeachingCalendarClassOption {
  id: String!
  schoolId: String!
  label: String!
  grade: String
  academicYear: String
}

type TeachingCalendarTeacherOption {
  id: String!
  label: String!
  colorKey: String!
}

type TeachingCalendarSubjectOption {
  id: String!
  label: String!
}

type TeachingCalendarLevelOption {
  id: String!
  subjectId: String!
  label: String!
  sortOrder: Int
}

type TeachingCalendarRoomOption {
  id: String
  label: String!
  schoolId: String
}

# Payload trả về khi tạo phòng khẩn cấp
type TeachingScheduleRoomOption {
  id: String!
  label: String!
  schoolId: String!
}

type TeachingCalendarPermissions {
  canCreate: Boolean!
  canUpdate: Boolean!
  canDelete: Boolean!
  canPublish: Boolean!
  canViewAggregate: Boolean!
}

# Mutation payloads

type TeachingSchedulePreviewPayload {
  valid: Boolean!
  previewEventCount: Int!
  teacherScheduleCount: Int!
  totalPeriodCount: Int!
  rowResults: [TeachingScheduleRowResult!]!
  conflicts: [TeachingScheduleConflict!]!
}

type TeachingScheduleBulkPayload {
  batchId: String!
  createdEventCount: Int!
  teacherScheduleCount: Int!
  totalPeriodCount: Int!
  rowResults: [TeachingScheduleRowResult!]!
  conflicts: [TeachingScheduleConflict!]!
}

type TeachingScheduleEventPayload {
  event: TeachingCalendarEvent!
}

type TeachingScheduleDeletePayload {
  eventId: String!
  deleted: Boolean!
}

type TeachingScheduleBatchPayload {
  batchId: String!
  status: TeachingScheduleStatus!
  affectedEventCount: Int!
}

type TeachingScheduleRowResult {
  clientRowId: String!
  valid: Boolean!
  eventCount: Int!
  teacherScheduleCount: Int!
  errors: [TeachingScheduleRowMessage!]!
  warnings: [TeachingScheduleRowMessage!]!
}

type TeachingScheduleRowMessage {
  code: String!
  field: String
  message: String!
}

type TeachingScheduleConflict {
  code: String!
  severity: String!
  clientRowId: String
  existingEventId: String
  message: String!
}
```

### 3.5 Mối quan hệ giữa catalog, filterOptions, và laneSummaries

- **catalog**: Danh sách **tất cả** các lựa chọn có sẵn trong hệ thống (tất cả trường, lớp, giáo viên, môn, level, phòng) — dùng để FE hiển thị dropdown/combobox khi tạo lịch hoặc filter. Catalog không thay đổi theo date range.
- **filterOptions**: Chỉ các lựa chọn **có event trong khoảng ngày đang xem** — dùng để FE render filter bar với count. Ví dụ: "THCS Bình An (15)" nghĩa là có 15 event của trường này trong khoảng.
- **laneSummaries**: Tổng hợp theo chiều dọc của calendar (lane = cột trong calendar view). Tùy theo `viewMode`:
  - `BY_TEACHER`: mỗi lane là một giáo viên, `count` = số event của giáo viên đó.
  - `BY_SCHOOL`: mỗi lane là một trường.
  - `BY_SUBJECT`: mỗi lane là một môn học.
  - `ALL`: không có lane (trả về 1 lane duy nhất "Tất cả").

---

## 4. Flow tạo lịch bảng Excel-like — Chi tiết pipeline

### 4.1 Quy tắc input

LCMS user tạo lịch trên bảng:
- Chọn trường một lần ở header (`schoolId`).
- Chọn ngày bắt đầu áp dụng (`applyFrom`).
- Chọn số tuần lặp (`repeatWeeks`).
- Nhập nhiều dòng lịch (`rows`).
- Mỗi dòng có thể chọn nhiều lớp (`classIds`).
- Mỗi dòng có nhiều giáo viên chính (`mainTeacherIds`) và nhiều trợ giảng (`assistantTeacherIds`).

### 4.2 Giải thích "phân giải ra lịch từng cá nhân"

Ví dụ cụ thể:

**Input:**
- Trường: `sch_binh_an`
- Ngày áp dụng: `2026-06-15` (Thứ 2)
- Số tuần lặp: `4`
- Dòng 1:
  - Lớp: `6A1`, `6A2`
  - Thứ: 3 (Tuesday)
  - Tiết: 1-2
  - Giờ: 07:00-08:30
  - Giáo viên chính: `teacher_a`, `teacher_b`
  - Trợ giảng: `teacher_c`

**Tính toán ngày:**
- `applyFrom = 2026-06-15` (Monday)
- `weekday = 3` → Thứ 3 đầu tiên sau (hoặc bằng) ngày áp dụng: `2026-06-16`
- `repeatWeeks = 4` → các ngày: `2026-06-16`, `2026-06-23`, `2026-06-30`, `2026-07-07`

**Tạo event:**
- 2 lớp × 4 tuần = **8 events**
- Mỗi event: `startAt = 2026-06-16T07:00:00+07:00`, `endAt = 2026-06-16T08:30:00+07:00`

**Tạo teacher assignments:**
- 8 events × 3 giáo viên = **24 `teaching_schedule_event_teachers` records**

**Kết quả:**
- `teacher_a` vào LMS thấy 8 event của mình.
- `teacher_b` vào LMS thấy 8 event của mình.
- `teacher_c` vào LMS thấy 8 event với role `ASSISTANT`.
- Giáo viên khác không thấy các event này.

### 4.3 Pipeline xử lý chi tiết

`createTeachingSchedule(input)` chạy theo pipeline sau:

#### Step 1: Normalize input
```
- Trim tất cả string fields
- verifiedTenantId(principal, input.tenantId)
- Parse applyFrom (ISO date string → LocalDate)
- Parse startTime/endTime (HH:mm → LocalTime)
- Distinct và filter blank trong classIds, mainTeacherIds, assistantTeacherIds
- Normalize weekday: 1-7
- Sinh idempotency_key nếu client không gửi: UUID v7
- Tính payload_hash = SHA-256(toJson(rows))
```

#### Step 2: Authorize
```
- principal phải có permission: "lms.schedule.manage" hoặc "lms.manage" hoặc "*"
- assertEducationUnitScope(principal, input.schoolId)
- Nếu không pass, throw ForbiddenException NGAY LẬP TỨC (không collect vào row errors)
```

#### Step 3: Load reference data (batch — 1 query mỗi loại)
```
- School: educationUnitRepository.findById(schoolId)
  → nếu không tồn tại → throw BadRequestException

- Classes: classroomRepository.findByTenantIdAndSchoolIdAndIdIn(tenantId, schoolId, allClassIds)
  → map Id → Classroom để validate + lấy name/grade

- Users/Teachers: userRepository.findByTenantIdAndIdIn(tenantId, allTeacherUserIds)
  → map Id → User để lấy displayName

- Subjects: subjectRepository.findByTenantIdAndIdIn(tenantId, allSubjectIds)
  → map Id → Subject để validate + lấy name

- Levels: levelRepository.findByTenantIdAndSubjectIdIn(tenantId, subjectIds)
  → map Id → Level để validate + lấy name + subjectId

- Rooms (nếu có roomId): không có room repository trong V1
  → bỏ qua, chỉ dùng roomName text
```

#### Step 4: Validate từng row (trả về errors/warnings, không throw)
```
Với mỗi row:
  1. classIds không được rỗng → code: CLASS_REQUIRED
  2. Tất cả classIds phải thuộc schoolId → code: CLASS_NOT_IN_SCHOOL
  3. mainTeacherIds không được rỗng → code: MAIN_TEACHER_REQUIRED
  4. Tất cả teacherIds phải tồn tại trong DB → code: TEACHER_NOT_FOUND
  5. subjectId tồn tại và active → code: SUBJECT_NOT_FOUND
  6. levelId (nếu có) thuộc subjectId → code: LEVEL_NOT_IN_SUBJECT
  7. assistantTeacherIds không trùng mainTeacherIds → code: DUPLICATE_TEACHER_ROLE
  8. periodCount từ 1-10 → code: INVALID_PERIOD_COUNT
  9. periodStart >= 1 → code: INVALID_PERIOD_START
  10. startTime < endTime → code: INVALID_TIME_RANGE
  11. repeatWeeks từ 1-52 → code: INVALID_REPEAT_WEEKS
  12. weekday từ 1-7 → code: INVALID_WEEKDAY
  13. applyFrom là ngày hợp lệ → code: INVALID_APPLY_DATE
  14. startTime/endTime parse được (HH:mm) → code: INVALID_TIME_FORMAT
```

#### Step 5: Expand (sinh event instances)
```
Với mỗi row valid:
  allDates = calculateDates(applyFrom, weekday, repeatWeeks)
  → List<LocalDate>

  Với mỗi classId trong row.classIds:
    Với mỗi date trong allDates:
      startAt = LocalDateTime.of(date, startTime).atZone(VIETNAM_ZONE)
      endAt = LocalDateTime.of(date, endTime).atZone(VIETNAM_ZONE)

      Tạo event (chưa persist):
        - tenantId, batchId, schoolId
        - academicClassId = classId
        - subjectId, levelId
        - roomId, roomName
        - title = generateTitle(subjectName, className)
        - startAt, endAt, weekday, session
        - periodStart, periodCount
        - status = PUBLISHED (hoặc DRAFT nếu input yêu cầu)
        - note = row.note
        - createdBy = principal.userId()

      Với mỗi teacherId trong row.mainTeacherIds:
        Tạo teacher assignment:
          - eventId (gán sau khi persist)
          - teacherUserId
          - role = MAIN
          - displayNameSnapshot = userMap.get(teacherId).displayName
          - colorKey = null (để BE tính sau)

      Với mỗi teacherId trong row.assistantTeacherIds:
        Tương tự với role = ASSISTANT
```

#### Step 6: Conflict detection
```
Sau khi expand và có danh sách event dự kiến:

Với mỗi event dự kiến:
  1. Class conflict: query DB xem có event nào cùng class, overlap thời gian không
     → Query: SELECT COUNT(*) FROM teaching_schedule_events
       WHERE tenant_id = ? AND academic_class_id = ?
       AND start_at < ? AND end_at > ? AND status != 'CANCELLED'
     → Nếu có → conflict code: CLASS_TIME_CONFLICT, severity: ERROR

  2. Teacher conflict: với mỗi teacher assignment
     → Query: SELECT e.* FROM teaching_schedule_events e
       JOIN teaching_schedule_event_teachers et ON e.id = et.event_id
       WHERE e.tenant_id = ? AND et.teacher_user_id = ?
       AND e.start_at < ? AND e.end_at > ? AND e.status != 'CANCELLED'
     → Nếu có → conflict code: TEACHER_TIME_CONFLICT, severity: ERROR

  3. Room conflict (chỉ nếu roomName không rỗng):
     → Query: SELECT COUNT(*) FROM teaching_schedule_events
       WHERE tenant_id = ? AND school_id = ?
       AND LOWER(room_name) = LOWER(?) AND start_at < ? AND end_at > ?
       AND status != 'CANCELLED'
     → Nếu có → conflict code: ROOM_TIME_CONFLICT, severity: WARNING

Đồng thời kiểm tra conflict GIỮA CÁC ROW trong cùng batch:
  - Cùng class, cùng thời gian ở 2 row khác nhau → conflict
  - Cùng teacher, cùng thời gian ở 2 row khác nhau → conflict
```

#### Step 7: Persist (một transaction)
```
@Transactional("postgresTransactionManager")
public TeachingScheduleBulkPayload createTeachingSchedule(input, principal) {

    // 1. Idempotency check
    if (input.idempotencyKey != null) {
        existing = batchRepository.findByTenantIdAndIdempotencyKey(tenantId, key)
        if (existing.isPresent()) {
            if (existing.payloadHash.equals(currentHash)) {
                // Trả về kết quả cũ (không tạo mới)
                return buildPayloadFromExistingBatch(existing)
            } else {
                throw new ConflictException("Same idempotency key but different payload")
            }
        }
    }

    // 2. Save batch
    batch = new TeachingScheduleBatch(...)
    batchRepository.save(batch)

    // 3. Save events (batch insert)
    List<TeachingScheduleEvent> events = expandedEvents
    eventRepository.saveAll(events)  // JPA batch insert

    // 4. Save teacher assignments (batch insert)
    List<TeachingScheduleEventTeacher> assignments = expandedAssignments
    // Gán eventId cho từng assignment
    teacherRepository.saveAll(assignments)

    // 5. Build và return payload
    return buildPayload(batch, events, assignments, rowResults, conflicts)
}
```

**Lưu ý về performance batch insert:**
- Với 600 events + 1800 assignments, `saveAll()` của JPA có thể chậm.
- Nếu performance không đạt, chuyển sang `JdbcTemplate.batchUpdate()` cho phần insert events và assignments.
- Thiết lập `spring.jpa.properties.hibernate.jdbc.batch_size=50` và `spring.jpa.properties.hibernate.order_inserts=true`.

#### Step 8: Return payload
```
Trả về:
  - batchId
  - createdEventCount
  - teacherScheduleCount
  - totalPeriodCount = sum(periodCount của tất cả events)
  - rowResults: kết quả từng row (valid, eventCount, teacherScheduleCount, errors, warnings)
  - conflicts: danh sách conflict (nếu overrideConflicts=false và có ERROR → không persist)
```

### 4.4 `previewTeachingSchedule(input)` pipeline

Chạy y hệt pipeline trên nhưng **không persist**. Step 7 được thay thế bằng:
- Trả về `TeachingSchedulePreviewPayload` với `valid`, `previewEventCount`, `teacherScheduleCount`, `totalPeriodCount`, `rowResults`, `conflicts`.
- Không gọi repository save.
- Không check idempotency.

---

## 5. Conflict và validation — Chi tiết bổ sung

### 5.1 Severity levels

| Severity | Hành vi mặc định | overrideConflicts=false | overrideConflicts=true |
|----------|-----------------|------------------------|------------------------|
| `ERROR` | Chặn tạo lịch | Chặn | **Vẫn chặn** |
| `WARNING` | Cảnh báo, vẫn cho tạo | Chặn | Cho phép, trả warning |

Lý do không cho override ERROR: không thể cho phép 1 giáo viên dạy 2 lớp cùng giờ, hoặc 1 lớp học 2 môn cùng giờ. Đây là ràng buộc nghiệp vụ cứng.

### 5.2 `resolveMode` — Logic xử lý khi giáo viên bị trùng lịch (QUAN TRỌNG)

**Vấn đề thực tế:** GV A đang có lịch vào khung giờ 12:45-14:45 ở bất kỳ trường nào đó. Admin vẫn cố tình tạo lịch thêm cho GV A vào cùng khung giờ ở 1 trường khác (hoặc cùng trường). BE cần xử lý tình huống này theo 2 hướng.

**Hai lựa chọn cho Admin:**

```
┌─────────────────────────────────────────────────────────────┐
│ Input: resolveMode = REJECT (mặc định)                      │
│                                                             │
│ GV A đã có lịch 12:45-14:45 Thứ 3 → CONFLICT               │
│ BE trả về conflict trong preview/create payload             │
│ Admin thấy conflict → quyết định không tạo nữa               │
│ → Không tạo lịch mới                                        │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ Input: resolveMode = DELETE_CONFLICTING                      │
│                                                             │
│ GV A đã có lịch 12:45-14:45 Thứ 3 ở trường X                │
│ Admin tạo lịch mới cho GV A cùng khung giờ ở trường Y        │
│ BE:                                                         │
│   1. Phát hiện conflict GV A (TEACHER_TIME_CONFLICT)        │
│   2. resolveMode = DELETE_CONFLICTING                        │
│   3. → XÓA toàn bộ event cũ của GV A bị conflict            │
│      (cancel event cũ: status → CANCELLED)                   │
│   4. → TẠO event mới cho GV A                                │
│   5. → Trả về payload với danh sách event đã bị cancel      │
│         trong conflicts[].severity = "RESOLVED"             │
└─────────────────────────────────────────────────────────────┘
```

**Pipeline với `resolveMode`:**

```
Step 1: Validate + Expand (như cũ)
Step 2: Detect conflicts (như cũ)
Step 3: Xử lý conflict theo resolveMode:

  if resolveMode == REJECT (mặc định):
      if có bất kỳ ERROR conflict nào:
          → KHÔNG persist
          → Trả về payload với valid=false, conflicts chứa danh sách conflict

  if resolveMode == DELETE_CONFLICTING:
      if conflict severity == ERROR (TEACHER_TIME_CONFLICT):
          → Load các event cũ gây conflict
          → Cancel từng event cũ (status → CANCELLED)
          → Lưu vào danh sách `resolvedConflicts` với severity="RESOLVED"
          → Tiếp tục tạo event mới
      if conflict severity == ERROR (CLASS_TIME_CONFLICT):
          → KHÔNG resolve (không cho xóa lịch lớp)
          → Vẫn chặn tạo, trả về conflict
      if conflict severity == WARNING (ROOM_TIME_CONFLICT):
          → Tạo event mới, trả warning
```

**Payload response khi `resolveMode = DELETE_CONFLICTING`:**

```json
{
  "data": {
    "createTeachingSchedule": {
      "batchId": "batch-abc123",
      "createdEventCount": 8,
      "teacherScheduleCount": 24,
      "totalPeriodCount": 16,
      "rowResults": [...],
      "conflicts": [
        {
          "code": "TEACHER_TIME_CONFLICT",
          "severity": "RESOLVED",
          "clientRowId": "row-1",
          "existingEventId": "event-old-001",
          "message": "Đã hủy lịch cũ của GV Nguyễn Văn A (12:45-14:45, Thứ 3, THCS Bình An) và thay bằng lịch mới"
        },
        {
          "code": "TEACHER_TIME_CONFLICT",
          "severity": "RESOLVED",
          "clientRowId": "row-1",
          "existingEventId": "event-old-002",
          "message": "Đã hủy lịch cũ của GV Trần Thị B (12:45-14:45, Thứ 3, THCS Cát Lái) và thay bằng lịch mới"
        }
      ]
    }
  }
}
```

**Quy tắc bảo mật khi DELETE_CONFLICTING:**
- Chỉ được cancel event của giáo viên trong scope trường mình quản lý (`managedEducationUnitIds`).
- Nếu event cũ thuộc trường NGOÀI scope → không được cancel → vẫn chặn tạo, trả về conflict `FORBIDDEN_CROSS_SCHOOL`.
- Nếu event cũ thuộc trường trong scope → được cancel và tạo mới.
- **KHÔNG tự động notify** giáo viên bị cancel lịch cũ ở bước này (tránh spam). Chỉ notify cho lịch mới sau khi publish.

### 5.3 Trợ giảng nhiều hơn 1 — Xác nhận

`assistantTeacherIds: [String!]` trong `TeachingScheduleBulkRowInput` — **đã là array, hỗ trợ nhiều trợ giảng ngay từ đầu.**

Ví dụ:
- Dòng 1: `mainTeacherIds: ["teacher_a"]`, `assistantTeacherIds: ["teacher_c", "teacher_d", "teacher_e"]`
- Kết quả: `teacher_a` role `MAIN`, `teacher_c`/`teacher_d`/`teacher_e` role `ASSISTANT` — tất cả cùng 1 event.

Validate: `assistantTeacherIds` không được chứa bất kỳ ID nào trong `mainTeacherIds` (code: `DUPLICATE_TEACHER_ROLE`).

### 5.4 Concurrent batch creation

Hai admin cùng tạo lịch cho một trường có thể gây conflict không phát hiện được nếu cả 2 cùng query conflict detection trước khi persist.

Giải pháp V1:
- Sử dụng `@Transactional(isolation = Isolation.REPEATABLE_READ)` cho toàn bộ transaction create.
- Hoặc sử dụng **advisory lock** của Postgres trên `(tenant_id, school_id)`:
  ```java
  @Query(value = "SELECT pg_try_advisory_xact_lock(hashtext(?1 || ':' || ?2))", nativeQuery = true)
  boolean tryAcquireSchoolScheduleLock(String tenantId, String schoolId);
  ```
- Nếu không acquire được lock → trả về lỗi "Another schedule creation is in progress for this school".

### 5.5 Idempotency — Chi tiết

`idempotencyKey` có thể do FE tạo (UUID) hoặc BE tự sinh từ hash của input.

Rule:
- Key mới: tạo batch mới.
- Key cũ + payload hash giống: trả về kết quả batch cũ (không tạo mới).
- Key cũ + payload hash khác: throw `ConflictException` ("An schedule with this idempotency key already exists with different data").

`payload_hash` = SHA-256 của canonical JSON của `rows` array (đã sort key để đảm bảo deterministic).

---

### 5.6 `deleteTeacherSchedule` — API xóa toàn bộ lịch giáo viên

**Mục đích:** Cho phép Admin xóa toàn bộ lịch của một giáo viên trong phạm vi trường hoặc danh sách trường, trong khoảng thời gian. Dùng trong các tình huống:
- Giáo viên nghỉ dài hạn, cần hủy toàn bộ lịch.
- Admin muốn xóa sạch lịch cũ trước khi tạo lịch mới.
- Dọn dẹp lịch bị sai.

**Input:**

```graphql
input DeleteTeacherScheduleInput {
  tenantId: String
  teacherUserId: String!          # BẮT BUỘC: giáo viên cần xóa lịch
  schoolIds: [String!]            # Optional: chỉ xóa trong các trường này. Nếu null → xóa TẤT CẢ trường trong scope
  dateFrom: String                # Optional: xóa từ ngày này. Nếu null → từ hiện tại
  dateTo: String                  # Optional: xóa đến ngày này. Nếu null → không giới hạn trên
}
```

**Ứng xử theo input:**

| schoolIds | dateFrom | dateTo | Hành vi |
|-----------|----------|--------|---------|
| `["sch_A"]` | `"2026-06-15"` | `"2026-07-15"` | Xóa lịch GV ở trường A, từ 15/06 đến 15/07 |
| `["sch_A", "sch_B"]` | `null` | `null` | Xóa TẤT CẢ lịch GV ở trường A và B (từ hiện tại trở đi) |
| `null` | `"2026-06-15"` | `null` | Xóa lịch GV ở TẤT CẢ trường trong scope, từ 15/06 trở đi |
| `["sch_A"]` | `null` | `null` | Xóa TẤT CẢ lịch tương lai của GV ở trường A |

**Pipeline:**

```
Step 1: Authorize
  - principal phải có "lms.schedule.manage", "lms.manage", hoặc "*"
  - Nếu schoolIds được cung cấp: assertEducationUnitScope cho từng trường
  - Nếu schoolIds null: dùng managedEducationUnitIds từ principal

Step 2: Validate teacher tồn tại
  - userRepository.findById(teacherUserId) → nếu không tồn tại → throw BadRequestException

Step 3: Query danh sách event cần xóa
  SELECT et.event_id
  FROM teaching_schedule_event_teachers et
  JOIN teaching_schedule_events e ON et.event_id = e.id
  WHERE et.tenant_id = ?
    AND et.teacher_user_id = ?
    AND e.school_id = ANY(?)
    AND e.status = 'PUBLISHED'
    AND (? IS NULL OR e.start_at >= ?)   -- dateFrom
    AND (? IS NULL OR e.start_at < ?)    -- dateTo
  → Lấy danh sách eventId bị ảnh hưởng

Step 4: Cancel events (soft delete)
  UPDATE teaching_schedule_events
  SET status = 'CANCELLED', updated_at = now(), updated_by = ?
  WHERE id IN (:eventIds)

Step 5: Emit notification (tùy chọn)
  - Nếu số event > 0: gửi notification "TEACHER_SCHEDULE_CANCELLED"
  - Chỉ gửi cho teacherUserId bị xóa lịch

Step 6: Trả về payload
  {
    deleted: true,
    affectedEventCount: số event đã cancel,
    affectedTeachers: [{
      userId: teacherUserId,
      displayName: "...",
      cancelledEventCount: N
    }]
  }
```

**Bảo mật:**
- Admin trường A không thể xóa lịch của giáo viên ở trường B (nếu B không trong `managedEducationUnitIds`).
- `schoolIds = null` → tự động filter theo `managedEducationUnitIds`.
- KHÔNG cho phép teacher tự gọi API này (teacher không có `lms.schedule.manage`).

**So sánh với các API xóa khác:**

| API | Phạm vi | Ai dùng |
|-----|---------|---------|
| `deleteTeachingScheduleEvent` | 1 event cụ thể | Admin |
| `cancelTeachingScheduleBatch` | Toàn bộ batch (tất cả event của 1 lần tạo) | Admin |
| **`deleteTeacherSchedule`** | **Toàn bộ lịch của 1 GV trong N trường, khoảng thời gian** | Admin |

---

## 6. Read model và filter behavior

### 6.1 LCMS (`lcms.teachingCalendarWorkspace`)

**Authorization:**
- Permission: `lms.schedule.read`, `lms.schedule.manage`, `lms.manage`, hoặc `*`.
- Scope: `assertEducationUnitScope(principal, input.schoolIds[0])` — chỉ xem được trường trong scope quản lý.
- Nếu input có `schoolIds` thì filter theo đó, ngược lại dùng `managedEducationUnitIds` từ principal.

**Query logic:**
```sql
SELECT e.*, c.name as class_name, s.name as school_name,
       sub.name as subject_name, l.name as level_name
FROM teaching_schedule_events e
JOIN academic_classes c ON e.academic_class_id = c.id
JOIN education_units s ON e.school_id = s.id
LEFT JOIN lms_subjects sub ON e.subject_id = sub.id
LEFT JOIN lms_levels l ON e.level_id = l.id
WHERE e.tenant_id = ?
  AND e.school_id = ANY(?)
  AND e.start_at < ?  -- to
  AND e.end_at > ?    -- from
  AND e.status = ANY(?)
  AND (? IS NULL OR e.school_id = ANY(?))
  AND (? IS NULL OR e.subject_id = ANY(?))
  AND (? IS NULL OR e.academic_class_id = ANY(?))
  AND (? IS NULL OR e.level_id = ANY(?))
ORDER BY e.start_at
```

**Modes:**
- `ALL`: filter list = trường, event color = theo trường (`school:{schoolId}`)
- `BY_SCHOOL`: filter list = giáo viên, event color = theo giáo viên (`teacher:{userId}`)
- `BY_TEACHER`: filter list = trường, event color = theo trường (`school:{schoolId}`)

### 6.2 LMS teacher (`lms.myTeachingCalendar`)

**Authorization:**
- Permission: `lms.schedule.read_self`, `lms.schedule.read`, `lms.manage`, hoặc `*`.
- **TỰ ĐỘNG** lấy `principal.userId()` làm `teacherId` — input không có và không cần `teacherIds`.
- Không cần `assertEducationUnitScope` vì teacher chỉ xem lịch của chính mình.
- Các filter `schoolIds`, `classIds`, `subjectIds`, `levelIds` là optional filter UX, không phải security.

**Query logic:**
```sql
SELECT e.*, et.role as teacher_role, et.display_name_snapshot,
       c.name as class_name, s.name as school_name,
       sub.name as subject_name, l.name as level_name
FROM teaching_schedule_events e
JOIN teaching_schedule_event_teachers et ON e.id = et.event_id
JOIN academic_classes c ON e.academic_class_id = c.id
JOIN education_units s ON e.school_id = s.id
LEFT JOIN lms_subjects sub ON e.subject_id = sub.id
LEFT JOIN lms_levels l ON e.level_id = l.id
WHERE e.tenant_id = ?
  AND et.teacher_user_id = ?
  AND e.start_at < ?  -- to
  AND e.end_at > ?    -- from
  AND e.status = ANY(?)
  AND (? IS NULL OR e.school_id = ANY(?))
  AND (? IS NULL OR e.academic_class_id = ANY(?))
  AND (? IS NULL OR e.subject_id = ANY(?))
  AND (? IS NULL OR e.level_id = ANY(?))
ORDER BY e.start_at
```

**Modes:**
- `ALL`: filter list = trường, event color = theo trường (`school:{schoolId}`)
- `BY_SCHOOL`: filter list = lớp, event color = theo lớp (`class:{classId}`)
- `BY_SUBJECT`: filter list = môn, event color = theo môn (`subject:{subjectId}`)

### 6.3 LMS student calendar

Nếu học viên cũng cần xem lịch:
- Thêm field `myStudentCalendar` vào `LmsQuery` (hoặc dùng chung `myTeachingCalendar` với logic phân biệt account type).
- Map `principal.userId()` → `Student.authUserId` → lấy `academicClassId` của student.
- Chỉ trả event của lớp đó (`e.academic_class_id = studentClassId AND e.status = 'PUBLISHED'`).
- Permission: `lms.schedule.read_self` (giống teacher).

### 6.4 Color key — Quy tắc cố định

BE không trả mã màu hex, nhưng trả `colorKey` ổn định để FE map sang palette.

Rule sinh `colorKey`:
- Theo trường: `school:{schoolId}`
- Theo lớp: `class:{classId}`
- Theo giáo viên: `teacher:{teacherUserId}`
- Theo môn: `subject:{subjectId}`

`ColorGroup.kind` cho FE biết loại color key: `"SCHOOL"`, `"CLASS"`, `"TEACHER"`, `"SUBJECT"`.

### 6.5 Response title generation

`TeachingCalendarEvent.title` sinh theo rule:
1. Nếu có subjectName và className: `"{subjectName} - {className}"`
2. Nếu thiếu subject: dùng className
3. Nếu thiếu class: dùng `"Lịch giảng dạy"`
4. Nếu có note thì append `" - {note}"`

---

## 7. Permission seed và SecurityConfig

### 7.1 Permissions mới cần seed

Thêm trong `V17__teaching_schedule_schema.sql`:

```sql
-- Permission group
INSERT INTO permission_groups (id, name, description, sort_order) VALUES
    ('pg_lms_schedule', 'LMS Schedule', 'Teaching schedule permissions', 30)
ON CONFLICT DO NOTHING;

-- Permissions
INSERT INTO permissions (id, name, group_name, label, description) VALUES
    ('perm_lms_schedule_read_self', 'lms.schedule.read_self', 'lms', 'View own schedule', 'View teaching schedule assigned to the current user'),
    ('perm_lms_schedule_read', 'lms.schedule.read', 'lms', 'View aggregate schedule', 'View teaching schedule for managed schools'),
    ('perm_lms_schedule_manage', 'lms.schedule.manage', 'lms', 'Manage teaching schedule', 'Create, update, delete, and publish teaching schedules')
ON CONFLICT DO NOTHING;

-- Gán permission cho roles hiện có
-- lms.admin → schedule.read + schedule.manage
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'lms.admin' AND p.name IN ('lms.schedule.read', 'lms.schedule.manage')
AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- lms.instructor → schedule.read_self
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'lms.instructor' AND p.name = 'lms.schedule.read_self'
AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- lms.learner → schedule.read_self
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'lms.learner' AND p.name = 'lms.schedule.read_self'
AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- system.super_admin đã có * nên không cần thêm
```

### 7.2 Cập nhật ErgPolicyService

Thêm vào `ROLE_PERMISSIONS` map trong `ErgPolicyService.java`:

```java
// Trong Map.ofEntries(...) thêm:
Map.entry("lms.admin", List.of("lms.*", "lms.read", "lms.manage",
    "lms.schedule.read", "lms.schedule.manage")),
// lms.teacher: thêm "lms.schedule.read_self" vào list hiện có
Map.entry("lms.teacher", List.of(
    "lms.unit.read", "lms.class.read", "lms.student.read",
    "lms.quiz.*", "lms.assignment.*", "lms.grade.read", "lms.report.read",
    "lms.discussion.*", "lms.announcement.read", "lms.document.read",
    "lms.dashboard.read", "lms.schedule.read_self"  // <-- THÊM DÒNG NÀY
)),
// lms.student: thêm "lms.schedule.read_self" vào list hiện có
Map.entry("lms.student", List.of("profile.self", "lms.assignment.read_self",
    "lms.assignment.submit", "lms.quiz.read", "lms.report.read",
    "lms.schedule.read_self"  // <-- THÊM DÒNG NÀY
)),
```

### 7.3 SecurityConfig

**Không cần sửa `SecurityConfig.java`.** `/api/v1/graphql` đã được authenticated. Permission chi tiết được enforce trong từng controller method qua `GraphQlAccessSupport.assertPermission()`.

---

## 8. Java implementation plan — Chi tiết từng class

### 8.1 Package structure

```
com.erg.ergspringboot
├── model/lms/schedule/
│   ├── TeachingScheduleBatch.java          (JPA entity → teaching_schedule_batches)
│   ├── TeachingScheduleEvent.java          (JPA entity → teaching_schedule_events)
│   └── TeachingScheduleEventTeacher.java   (JPA entity → teaching_schedule_event_teachers)
│
├── repository/lms/schedule/
│   ├── TeachingScheduleBatchRepository.java
│   ├── TeachingScheduleEventRepository.java
│   ├── TeachingScheduleEventTeacherRepository.java
│   └── projection/
│       ├── TeachingCalendarEventProjection.java
│       ├── TeachingCalendarFilterOptionProjection.java
│       ├── TeachingCalendarLaneSummaryProjection.java
│       └── TeachingScheduleConflictProjection.java
│
├── service/lms/schedule/
│   ├── TeachingScheduleReadService.java
│   ├── TeachingScheduleCommandService.java
│   ├── TeachingScheduleExpansionService.java
│   ├── TeachingScheduleValidationService.java
│   ├── TeachingScheduleConflictService.java
│   └── TeachingScheduleCatalogService.java
│
├── controller/lcms/graphql/
│   └── LcmsTeachingScheduleGraphQlController.java    (@SchemaMapping typeName="LcmsQuery")
│
├── controller/lms/graphql/
│   ├── LmsTeachingScheduleGraphQlController.java     (@SchemaMapping typeName="LmsQuery")
│   └── TeachingScheduleMutationGraphQlController.java (@MutationMapping)
│
├── common/request/lms/
│   └── TeachingScheduleRequests.java                 (input records)
│
└── common/response/lms/
    └── TeachingScheduleResponses.java                (output records)
```

### 8.2 Entity classes

#### TeachingScheduleBatch.java

```java
@Entity
@Table(name = "teaching_schedule_batches")
@Data @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TeachingScheduleBatch extends BaseEntity {
    @Column(name = "tenant_id", length = 36, nullable = false)
    private String tenantId;

    @Column(name = "school_id", length = 36, nullable = false)
    private String schoolId;

    @Column(name = "academic_year", length = 20)
    private String academicYear;

    @Column(name = "apply_from", nullable = false)
    private LocalDate applyFrom;

    @Column(name = "repeat_weeks", nullable = false)
    private Integer repeatWeeks;

    @Column(length = 20, nullable = false)
    private String status;  // DRAFT, PUBLISHED, CANCELLED

    @Column(length = 30, nullable = false)
    private String source;  // LCMS_BULK_GRID

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Column(name = "payload_hash", length = 64)
    private String payloadHash;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "published_by", length = 36)
    private String publishedBy;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;
}
```

#### TeachingScheduleEvent.java

```java
@Entity
@Table(name = "teaching_schedule_events")
@Data @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TeachingScheduleEvent extends BaseEntity {
    @Column(name = "tenant_id", length = 36, nullable = false)
    private String tenantId;

    @Column(name = "batch_id", length = 36, nullable = false)
    private String batchId;

    @Column(name = "school_id", length = 36, nullable = false)
    private String schoolId;

    @Column(name = "academic_class_id", length = 36, nullable = false)
    private String academicClassId;

    @Column(name = "subject_id", length = 36, nullable = false)
    private String subjectId;

    @Column(name = "level_id", length = 36)
    private String levelId;

    @Column(name = "room_id", length = 36)
    private String roomId;

    @Column(name = "room_name", length = 255)
    private String roomName;

    @Column(length = 500)
    private String title;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private OffsetDateTime endAt;

    @Column(nullable = false)
    private Short weekday;  // 1-7

    @Column(length = 20)
    private String session;  // MORNING, AFTERNOON, EVENING

    @Column(name = "period_start", nullable = false)
    private Short periodStart;

    @Column(name = "period_count", nullable = false)
    private Short periodCount;

    @Column(length = 20, nullable = false)
    private String status;  // DRAFT, PUBLISHED, CANCELLED

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "updated_by", length = 36)
    private String updatedBy;
}
```

#### TeachingScheduleEventTeacher.java

```java
@Entity
@Table(name = "teaching_schedule_event_teachers")
@Data @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TeachingScheduleEventTeacher extends BaseEntity {
    @Column(name = "tenant_id", length = 36, nullable = false)
    private String tenantId;

    @Column(name = "event_id", length = 36, nullable = false)
    private String eventId;

    @Column(name = "teacher_user_id", length = 36, nullable = false)
    private String teacherUserId;

    @Column(length = 20, nullable = false)
    private String role;  // MAIN, ASSISTANT

    @Column(name = "display_name_snapshot", length = 255)
    private String displayNameSnapshot;

    @Column(name = "color_key", length = 100)
    private String colorKey;
}
```

### 8.3 Repository interfaces

#### TeachingScheduleEventRepository — Queries quan trọng

```java
public interface TeachingScheduleEventRepository
        extends JpaRepository<TeachingScheduleEvent, String> {

    // LCMS aggregate query
    @Query("""
        SELECT e FROM TeachingScheduleEvent e
        WHERE e.tenantId = :tenantId
          AND e.schoolId IN :schoolIds
          AND e.startAt < :to
          AND e.endAt > :from
          AND e.status IN :statuses
        ORDER BY e.startAt
        """)
    List<TeachingScheduleEvent> findLcmsCalendarEvents(
            @Param("tenantId") String tenantId,
            @Param("schoolIds") List<String> schoolIds,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("statuses") List<String> statuses);

    // LMS teacher query - join với event_teachers
    @Query("""
        SELECT e, et.role as teacherRole
        FROM TeachingScheduleEvent e
        JOIN TeachingScheduleEventTeacher et ON e.id = et.eventId
        WHERE e.tenantId = :tenantId
          AND et.teacherUserId = :teacherUserId
          AND e.startAt < :to
          AND e.endAt > :from
          AND e.status IN :statuses
        ORDER BY e.startAt
        """)
    List<Object[]> findTeacherCalendarEvents(
            @Param("tenantId") String tenantId,
            @Param("teacherUserId") String teacherUserId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("statuses") List<String> statuses);

    // Conflict: class overlap
    @Query("""
        SELECT COUNT(e) FROM TeachingScheduleEvent e
        WHERE e.tenantId = :tenantId
          AND e.academicClassId = :classId
          AND e.startAt < :endAt
          AND e.endAt > :startAt
          AND e.status <> 'CANCELLED'
        """)
    long countClassTimeConflicts(
            @Param("tenantId") String tenantId,
            @Param("classId") String classId,
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt);

    // Conflict: teacher overlap (cần join)
    @Query("""
        SELECT e FROM TeachingScheduleEvent e
        JOIN TeachingScheduleEventTeacher et ON e.id = et.eventId
        WHERE e.tenantId = :tenantId
          AND et.teacherUserId = :teacherUserId
          AND e.startAt < :endAt
          AND e.endAt > :startAt
          AND e.status <> 'CANCELLED'
        """)
    List<TeachingScheduleEvent> findTeacherTimeConflicts(
            @Param("tenantId") String tenantId,
            @Param("teacherUserId") String teacherUserId,
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt);

    // Conflict: room overlap
    @Query("""
        SELECT COUNT(e) FROM TeachingScheduleEvent e
        WHERE e.tenantId = :tenantId
          AND e.schoolId = :schoolId
          AND LOWER(e.roomName) = LOWER(:roomName)
          AND e.startAt < :endAt
          AND e.endAt > :startAt
          AND e.status <> 'CANCELLED'
        """)
    long countRoomTimeConflicts(
            @Param("tenantId") String tenantId,
            @Param("schoolId") String schoolId,
            @Param("roomName") String roomName,
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt);

    // Filter options: distinct schools với events trong range
    @Query("""
        SELECT DISTINCT e.schoolId as id, s.name as label, COUNT(e) as cnt
        FROM TeachingScheduleEvent e
        JOIN EducationUnit s ON e.schoolId = s.id
        WHERE e.tenantId = :tenantId
          AND e.startAt < :to AND e.endAt > :from
          AND e.status IN :statuses
        GROUP BY e.schoolId, s.name
        ORDER BY s.name
        """)
    List<TeachingCalendarFilterOptionProjection> findSchoolFilterOptions(...);

    // Catalog queries (không phụ thuộc date range)
    // ...tương tự cho classes, subjects, levels, teachers...
}
```

### 8.4 Service classes — Chi tiết

#### TeachingScheduleExpansionService

```java
@Service
public class TeachingScheduleExpansionService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /**
     * Tính danh sách ngày từ applyFrom, weekday, repeatWeeks.
     *
     * @param applyFrom   ngày bắt đầu áp dụng (Monday of the week)
     * @param weekday     ISO weekday (1=Monday, 7=Sunday)
     * @param repeatWeeks số tuần lặp
     * @return danh sách LocalDate, sorted ascending
     *
     * Ví dụ: applyFrom=2026-06-15(Mon), weekday=3(Tue), repeatWeeks=4
     * → [2026-06-16, 2026-06-23, 2026-06-30, 2026-07-07]
     */
    public List<LocalDate> calculateDates(LocalDate applyFrom, int weekday, int repeatWeeks) {
        // Tìm ngày đầu tiên >= applyFrom có weekday khớp
        LocalDate firstDate = applyFrom;
        while (firstDate.getDayOfWeek().getValue() != weekday) {
            firstDate = firstDate.plusDays(1);
        }

        List<LocalDate> dates = new ArrayList<>();
        for (int i = 0; i < repeatWeeks; i++) {
            dates.add(firstDate.plusWeeks(i));
        }
        return dates;
    }

    /**
     * Expand một row input thành danh sách TeachingScheduleEvent (chưa persist).
     *
     * @param row       input row từ LCMS
     * @param batchId   batch đã tạo
     * @param tenantId  tenant
     * @param schoolId  trường
     * @param createdBy userId của người tạo
     * @param applyFrom ngày áp dụng
     * @param repeatWeeks số tuần lặp
     * @param refData   reference data (classMap, subjectMap, levelMap, userMap)
     * @return danh sách event + teacher assignments
     */
    public ExpansionResult expand(
            TeachingScheduleBulkRowInput row,
            String batchId,
            String tenantId,
            String schoolId,
            String createdBy,
            LocalDate applyFrom,
            int repeatWeeks,
            ReferenceData refData
    ) {
        List<LocalDate> dates = calculateDates(applyFrom, row.weekday(), repeatWeeks);
        LocalTime startTime = LocalTime.parse(row.startTime());
        LocalTime endTime = LocalTime.parse(row.endTime());

        List<TeachingScheduleEvent> events = new ArrayList<>();
        List<TeachingScheduleEventTeacher> assignments = new ArrayList<>();

        for (String classId : row.classIds()) {
            Classroom cls = refData.classMap().get(classId);
            Subject subject = refData.subjectMap().get(row.subjectId());
            String className = cls != null ? cls.getName() : classId;
            String subjectName = subject != null ? subject.getName() : "";
            String title = buildTitle(subjectName, className);

            for (LocalDate date : dates) {
                OffsetDateTime startAt = LocalDateTime.of(date, startTime).atZone(VIETNAM_ZONE).toOffsetDateTime();
                OffsetDateTime endAt = LocalDateTime.of(date, endTime).atZone(VIETNAM_ZONE).toOffsetDateTime();

                TeachingScheduleEvent event = new TeachingScheduleEvent();
                event.setTenantId(tenantId);
                event.setBatchId(batchId);
                event.setSchoolId(schoolId);
                event.setAcademicClassId(classId);
                event.setSubjectId(row.subjectId());
                event.setLevelId(nullToNull(row.levelId()));
                event.setRoomId(nullToNull(row.roomId()));
                event.setRoomName(nullToNull(row.roomName()));
                event.setTitle(title);
                event.setStartAt(startAt);
                event.setEndAt(endAt);
                event.setWeekday((short) row.weekday());
                event.setSession(nullToNull(row.session()));
                event.setPeriodStart((short) row.periodStart());
                event.setPeriodCount((short) row.periodCount());
                event.setStatus("PUBLISHED");
                event.setNote(nullToNull(row.note()));
                event.setCreatedBy(createdBy);
                events.add(event);

                // Main teachers
                for (String teacherId : row.mainTeacherIds()) {
                    assignments.add(buildAssignment(tenantId, event.getId(), teacherId,
                            "MAIN", refData));
                }
                // Assistant teachers
                if (row.assistantTeacherIds() != null) {
                    for (String teacherId : row.assistantTeacherIds()) {
                        assignments.add(buildAssignment(tenantId, event.getId(), teacherId,
                                "ASSISTANT", refData));
                    }
                }
            }
        }

        return new ExpansionResult(events, assignments);
    }

    private String buildTitle(String subjectName, String className) {
        if (!subjectName.isBlank() && !className.isBlank()) {
            return subjectName + " - " + className;
        }
        if (!className.isBlank()) {
            return className;
        }
        return "Lịch giảng dạy";
    }
}

record ExpansionResult(List<TeachingScheduleEvent> events,
                       List<TeachingScheduleEventTeacher> assignments) {}

record ReferenceData(Map<String, Classroom> classMap,
                     Map<String, Subject> subjectMap,
                     Map<String, Level> levelMap,
                     Map<String, User> userMap,
                     Map<String, EducationUnit> schoolMap) {}
```

#### TeachingScheduleValidationService

```java
@Service
public class TeachingScheduleValidationService {

    /**
     * Validate từng row. Trả về danh sách RowValidationResult.
     * Không throw exception — tất cả lỗi được collect vào RowValidationResult.errors.
     */
    public List<RowValidationResult> validateRows(
            TeachingScheduleBulkInput input,
            ReferenceData refData
    ) {
        List<RowValidationResult> results = new ArrayList<>();

        for (TeachingScheduleBulkRowInput row : input.rows()) {
            List<TeachingScheduleRowMessage> errors = new ArrayList<>();
            List<TeachingScheduleRowMessage> warnings = new ArrayList<>();

            // 1. ClassIds không rỗng
            if (row.classIds() == null || row.classIds().isEmpty()) {
                errors.add(msg("CLASS_REQUIRED", "classIds", "Ít nhất một lớp học là bắt buộc"));
            } else {
                // 2. Class thuộc school
                for (String classId : row.classIds()) {
                    Classroom cls = refData.classMap().get(classId);
                    if (cls == null) {
                        errors.add(msg("CLASS_NOT_FOUND", "classIds", "Lớp không tồn tại: " + classId));
                    } else if (!input.schoolId().equals(cls.getSchoolId())) {
                        errors.add(msg("CLASS_NOT_IN_SCHOOL", "classIds",
                                "Lớp " + cls.getName() + " không thuộc trường đã chọn"));
                    }
                }
            }

            // 3. MainTeacherIds không rỗng
            if (row.mainTeacherIds() == null || row.mainTeacherIds().isEmpty()) {
                errors.add(msg("MAIN_TEACHER_REQUIRED", "mainTeacherIds",
                        "Ít nhất một giáo viên chính là bắt buộc"));
            } else {
                for (String teacherId : row.mainTeacherIds()) {
                    if (!refData.userMap().containsKey(teacherId)) {
                        errors.add(msg("TEACHER_NOT_FOUND", "mainTeacherIds",
                                "Giáo viên không tồn tại: " + teacherId));
                    }
                }
            }

            // 4. Subject tồn tại
            if (!refData.subjectMap().containsKey(row.subjectId())) {
                errors.add(msg("SUBJECT_NOT_FOUND", "subjectId",
                        "Môn học không tồn tại: " + row.subjectId()));
            }

            // 5. Level thuộc subject
            if (row.levelId() != null && !row.levelId().isBlank()) {
                Level level = refData.levelMap().get(row.levelId());
                if (level == null) {
                    errors.add(msg("LEVEL_NOT_FOUND", "levelId",
                            "Level không tồn tại: " + row.levelId()));
                } else if (!level.getSubjectId().equals(row.subjectId())) {
                    errors.add(msg("LEVEL_NOT_IN_SUBJECT", "levelId",
                            "Level không thuộc môn học đã chọn"));
                }
            }

            // 6. Assistant không trùng main
            if (row.assistantTeacherIds() != null && row.mainTeacherIds() != null) {
                Set<String> mainSet = new HashSet<>(row.mainTeacherIds());
                for (String assistantId : row.assistantTeacherIds()) {
                    if (mainSet.contains(assistantId)) {
                        errors.add(msg("DUPLICATE_TEACHER_ROLE", "assistantTeacherIds",
                                "Giáo viên không thể vừa là chính vừa là trợ giảng"));
                    }
                }
            }

            // 7. Period validation
            if (row.periodCount() < 1 || row.periodCount() > 10) {
                errors.add(msg("INVALID_PERIOD_COUNT", "periodCount",
                        "Số tiết phải từ 1 đến 10"));
            }
            if (row.periodStart() < 1) {
                errors.add(msg("INVALID_PERIOD_START", "periodStart",
                        "Tiết bắt đầu phải >= 1"));
            }

            // 8. Time validation
            try {
                LocalTime start = LocalTime.parse(row.startTime());
                LocalTime end = LocalTime.parse(row.endTime());
                if (!start.isBefore(end)) {
                    errors.add(msg("INVALID_TIME_RANGE", "startTime",
                            "Giờ bắt đầu phải trước giờ kết thúc"));
                }
            } catch (DateTimeParseException e) {
                errors.add(msg("INVALID_TIME_FORMAT", "startTime",
                        "Định dạng giờ không hợp lệ (HH:mm)"));
            }

            // 9. Weekday validation
            if (row.weekday() < 1 || row.weekday() > 7) {
                errors.add(msg("INVALID_WEEKDAY", "weekday", "Thứ trong tuần phải từ 1-7"));
            }

            results.add(new RowValidationResult(
                    row.clientRowId(),
                    errors.isEmpty(),
                    errors,
                    warnings
            ));
        }

        return results;
    }

    private TeachingScheduleRowMessage msg(String code, String field, String message) {
        return new TeachingScheduleRowMessage(code, field, message);
    }
}

record RowValidationResult(String clientRowId, boolean valid,
                           List<TeachingScheduleRowMessage> errors,
                           List<TeachingScheduleRowMessage> warnings) {}
```

### 8.5 GraphQL controllers

#### LcmsTeachingScheduleGraphQlController.java

```java
@Controller
public class LcmsTeachingScheduleGraphQlController {

    private final GraphQlAccessSupport accessSupport;
    private final TeachingScheduleReadService readService;

    // constructor

    @SchemaMapping(typeName = "LcmsQuery", field = "teachingCalendarWorkspace")
    public TeachingCalendarWorkspaceResponse teachingCalendarWorkspace(
            @Argument TeachingCalendarWorkspaceInput input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal,
                "lms.schedule.read", "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, input.tenantId());

        // Scope: nếu input có schoolIds thì assert, ngược lại dùng managed schools
        List<String> scopeSchoolIds = resolveSchoolScope(principal, input.schoolIds());

        return readService.lcmsTeachingCalendarWorkspace(
                tenantId,
                scopeSchoolIds,
                input.from(),
                input.to(),
                input.viewMode(),
                input.teacherIds(),
                input.classIds(),
                input.subjectIds(),
                input.levelIds(),
                input.statuses(),
                principal
        );
    }
}
```

#### LmsTeachingScheduleGraphQlController.java

```java
@Controller
public class LmsTeachingScheduleGraphQlController {

    private final GraphQlAccessSupport accessSupport;
    private final TeachingScheduleReadService readService;

    // constructor

    @SchemaMapping(typeName = "LmsQuery", field = "myTeachingCalendar")
    public TeachingCalendarWorkspaceResponse myTeachingCalendar(
            @Argument MyTeachingCalendarInput input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal,
                "lms.schedule.read_self", "lms.schedule.read",
                "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, input.tenantId());

        // QUAN TRỌNG: tự động lấy userId từ principal, không cho phép override
        String teacherUserId = principal.userId();

        return readService.teacherTeachingCalendarWorkspace(
                tenantId,
                teacherUserId,
                input.from(),
                input.to(),
                input.viewMode(),
                input.schoolIds(),
                input.classIds(),
                input.subjectIds(),
                input.levelIds(),
                input.statuses(),
                principal
        );
    }
}
```

#### TeachingScheduleMutationGraphQlController.java

```java
@Controller
public class TeachingScheduleMutationGraphQlController {

    private final GraphQlAccessSupport accessSupport;
    private final TeachingScheduleCommandService commandService;
    private final TeachingScheduleValidationService validationService;
    private final TeachingScheduleExpansionService expansionService;
    private final TeachingScheduleConflictService conflictService;

    // constructor

    @MutationMapping
    public TeachingSchedulePreviewPayload previewTeachingSchedule(
            @Argument TeachingScheduleBulkInput input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal,
                "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, input.tenantId());
        accessSupport.assertEducationUnitScope(principal, input.schoolId());

        return commandService.preview(tenantId, input, principal.userId());
    }

    @MutationMapping
    public TeachingScheduleBulkPayload createTeachingSchedule(
            @Argument TeachingScheduleBulkInput input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal,
                "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, input.tenantId());
        accessSupport.assertEducationUnitScope(principal, input.schoolId());

        return commandService.create(tenantId, input, principal.userId());
    }

    @MutationMapping
    public TeachingScheduleEventPayload updateTeachingScheduleEvent(
            @Argument TeachingScheduleEventUpdateInput input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal,
                "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, input.tenantId());
        // TODO: assert event belongs to principal's managed schools

        return commandService.updateEvent(tenantId, input, principal.userId());
    }

    @MutationMapping
    public TeachingScheduleDeletePayload deleteTeachingScheduleEvent(
            @Argument TeachingScheduleEventDeleteInput input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal,
                "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, input.tenantId());
        // TODO: assert event belongs to principal's managed schools

        return commandService.deleteEvent(tenantId, input, principal.userId());
    }

    @MutationMapping
    public TeachingScheduleBatchPayload publishTeachingScheduleBatch(
            @Argument String batchId) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal,
                "lms.schedule.manage", "lms.manage", "*");
        return commandService.publishBatch(batchId, principal);
    }

    @MutationMapping
    public TeachingScheduleBatchPayload cancelTeachingScheduleBatch(
            @Argument String batchId) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal,
                "lms.schedule.manage", "lms.manage", "*");
        return commandService.cancelBatch(batchId, principal);
    }

    @MutationMapping
    public TeachingScheduleDeletePayload deleteTeacherSchedule(
            @Argument DeleteTeacherScheduleInput input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal,
                "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, input.tenantId());

        // Xác định scope trường: input.schoolIds hoặc managedEducationUnitIds
        List<String> scopeSchoolIds = resolveSchoolIds(principal, input.schoolIds());

        return commandService.deleteTeacherSchedule(
                tenantId, input.teacherUserId(), scopeSchoolIds,
                input.dateFrom(), input.dateTo(), principal.userId());
    }

    @MutationMapping
    public TeachingScheduleRoomOption createRoom(
            @Argument CreateRoomInput input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal,
                "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, input.tenantId());
        accessSupport.assertEducationUnitScope(principal, input.schoolId());

        return commandService.createRoom(tenantId, input.schoolId(), input.roomName(), principal.userId());
    }
}
```

### 8.7 Service classes mới

#### TeachingScheduleRoomService

```java
@Service
@Transactional("postgresTransactionManager")
public class TeachingScheduleRoomService {

    @Transactional
    public TeachingScheduleRoomOption createRoom(
            String tenantId, String schoolId, String roomName, String createdBy) {
        String normalized = roomName.trim();
        if (normalized.isBlank() || normalized.length() > 255) {
            throw new BadRequestException("Tên phòng không hợp lệ");
        }

        TeachingScheduleRoom existing = roomRepository
                .findBySchoolIdAndNameIgnoreCase(schoolId, normalized)
                .orElse(null);

        if (existing != null) {
            return new TeachingScheduleRoomOption(existing.getId(), existing.getName(), existing.getSchoolId());
        }

        try {
            TeachingScheduleRoom room = new TeachingScheduleRoom();
            room.setTenantId(tenantId);
            room.setSchoolId(schoolId);
            room.setName(normalized);
            room.setStatus("ACTIVE");
            room.setCreatedBy(createdBy);
            roomRepository.save(room);

            return new TeachingScheduleRoomOption(room.getId(), room.getName(), room.getSchoolId());
        } catch (DataIntegrityViolationException e) {
            // Race condition — room được tạo giữa lúc check và insert
            TeachingScheduleRoom concurrent = roomRepository
                    .findBySchoolIdAndNameIgnoreCase(schoolId, normalized)
                    .orElseThrow(() -> new IllegalStateException("Room creation failed unexpectedly"));
            return new TeachingScheduleRoomOption(concurrent.getId(), concurrent.getName(), concurrent.getSchoolId());
        }
    }
}
```

#### TeachingScheduleDeleteService

```java
@Service
@Transactional("postgresTransactionManager")
public class TeachingScheduleDeleteService {

    /**
     * Xóa toàn bộ lịch của một giáo viên trong scope trường và khoảng thời gian.
     * Soft delete: set status = 'CANCELLED'.
     *
     * @return số event đã cancel + danh sách teacher bị ảnh hưởng
     */
    public TeachingScheduleDeletePayload deleteTeacherSchedule(
            String tenantId,
            String teacherUserId,
            List<String> schoolIds,
            String dateFrom,
            String dateTo,
            String cancelledBy
    ) {
        // Validate teacher tồn tại
        userRepository.findById(teacherUserId)
                .orElseThrow(() -> new BadRequestException("Giáo viên không tồn tại: " + teacherUserId));

        OffsetDateTime from = dateFrom != null
                ? OffsetDateTime.parse(dateFrom + "T00:00:00+07:00")
                : OffsetDateTime.now();
        OffsetDateTime to = dateTo != null
                ? OffsetDateTime.parse(dateTo + "T23:59:59+07:00")
                : from.plusYears(5);  // Không giới hạn trên

        // Query event IDs bị ảnh hưởng
        List<String> eventIds = eventTeacherRepository.findEventIdsByTeacher(
                tenantId, teacherUserId, schoolIds, from, to, "PUBLISHED");

        if (eventIds.isEmpty()) {
            return new TeachingScheduleDeletePayload(null, true, 0);
        }

        // Soft delete events
        OffsetDateTime now = OffsetDateTime.now();
        eventRepository.updateStatusBatch(eventIds, "CANCELLED", cancelledBy, now);

        return new TeachingScheduleDeletePayload(
                eventIds.getFirst(), true, eventIds.size());
    }
}
```

### 8.6 Transaction boundaries

```java
@Service
@Transactional(readOnly = true)
public class TeachingScheduleReadService {
    // Tất cả phương thức trong đây đều read-only
    // LCMS workspace query
    // LMS teacher calendar query
    // Catalog query
    // Filter options query
}

@Service
public class TeachingScheduleCommandService {

    @Transactional("postgresTransactionManager")
    public TeachingScheduleBulkPayload create(String tenantId, TeachingScheduleBulkInput input, String userId) {
        // Idempotency check + Batch insert + Events insert + Teacher assignments insert
        // TẤT CẢ TRONG 1 TRANSACTION
    }

    @Transactional("postgresTransactionManager")
    public TeachingScheduleBatchPayload publishBatch(String batchId, ErgPrincipal principal) {
        // Update batch status + update all events status
        // + emit notification
    }

    @Transactional("postgresTransactionManager")
    public TeachingScheduleEventPayload updateEvent(String tenantId, TeachingScheduleEventUpdateInput input, String userId) {
        // Update single event (chỉ note, room)
    }

    @Transactional("postgresTransactionManager")
    public TeachingScheduleDeletePayload deleteEvent(String tenantId, TeachingScheduleEventDeleteInput input, String userId) {
        // Soft delete event (status → CANCELLED)
    }
}
```

---

## 9. Notification integration — Chi tiết

### 9.1 Event types

| Event Type | Khi nào emit | Đối tượng nhận |
|------------|-------------|----------------|
| `TEACHER_SCHEDULE_PUBLISHED` | Batch được publish | Tất cả giáo viên trong batch |
| `TEACHER_SCHEDULE_UPDATED` | Event được update | Giáo viên của event đó |
| `TEACHER_SCHEDULE_CANCELLED` | Batch hoặc event bị cancel | Tất cả giáo viên bị ảnh hưởng |

### 9.2 Wire vào CommandService

```java
// Trong TeachingScheduleCommandService, sau khi publish batch thành công:

private void emitSchedulePublishedNotification(
        TeachingScheduleBatch batch,
        Set<String> affectedTeacherUserIds,
        String actorUserId
) {
    NotificationCommand command = new NotificationCommand(
            batch.getTenantId(),          // tenantId
            actorUserId,                   // createdBy
            "schedule_publish_" + batch.getId(),  // idempotencyKey
            "TEACHER_SCHEDULE_PUBLISHED",  // eventType
            "schedule",                    // sourceType
            batch.getId(),                 // sourceId
            "Lịch giảng dạy mới",          // title
            "Bạn có lịch giảng dạy mới từ " + batch.getApplyFrom(),  // bodyPreview
            "/lms/schedule?from=" + batch.getApplyFrom(),  // actionUrl (deep link)
            "normal",                      // priority
            "lms",                         // portal
            new ArrayList<>(affectedTeacherUserIds),  // userIds
            null,                          // topicName (gửi trực tiếp cho user)
            Map.of(                        // data
                    "kind", "schedule",
                    "batchId", batch.getId(),
                    "schoolId", batch.getSchoolId(),
                    "eventCount", String.valueOf(affectedTeacherUserIds.size())
            )
    );
    notificationCommandService.enqueue(command);
}
```

### 9.3 Deep link format

```
# LMS teacher calendar (default view)
/lms/schedule

# LMS teacher calendar với ngày cụ thể
/lms/schedule?from=2026-06-16&to=2026-07-13

# LMS teacher calendar filter theo trường
/lms/schedule?schoolId=sch_binh_an&from=2026-06-16

# LCMS aggregate calendar
/lcms/schedule?schoolId=sch_binh_an&from=2026-06-16
```

### 9.4 Notification preference

Giáo viên có thể tắt/mở notification cho `TEACHER_SCHEDULE_PUBLISHED` qua `NotificationPreferenceEntity` (đã có sẵn). Không cần code thêm — hệ thống `NotificationOutboxWorker` sẽ tự xử lý.

---

## 10. Migration V17 — SQL đầy đủ

```sql
-- V17__teaching_schedule_schema.sql

-- ============================================================================
-- 1. Teaching Schedule Batches
-- ============================================================================
CREATE TABLE teaching_schedule_batches (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL,
    school_id       VARCHAR(36) NOT NULL,
    academic_year   VARCHAR(20),
    apply_from      DATE NOT NULL,
    repeat_weeks    INT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    source          VARCHAR(30) NOT NULL DEFAULT 'LCMS_BULK_GRID',
    idempotency_key VARCHAR(255),
    payload_hash    VARCHAR(64),
    note            TEXT,
    created_by      VARCHAR(36),
    published_by    VARCHAR(36),
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_tsb_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED')),
    CONSTRAINT chk_tsb_repeat_weeks CHECK (repeat_weeks BETWEEN 1 AND 52)
);

CREATE INDEX idx_tsb_tenant_school ON teaching_schedule_batches(tenant_id, school_id);
CREATE INDEX idx_tsb_tenant_status ON teaching_schedule_batches(tenant_id, status);
CREATE UNIQUE INDEX idx_tsb_tenant_idempotency_key
    ON teaching_schedule_batches(tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- ============================================================================
-- 2. Teaching Schedule Events
-- ============================================================================
CREATE TABLE teaching_schedule_events (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    batch_id            VARCHAR(36) NOT NULL,
    school_id            VARCHAR(36) NOT NULL,
    academic_class_id   VARCHAR(36) NOT NULL,
    subject_id          VARCHAR(36) NOT NULL,
    level_id            VARCHAR(36),
    room_id             VARCHAR(36),
    room_name           VARCHAR(255),
    title               VARCHAR(500),
    start_at            TIMESTAMPTZ NOT NULL,
    end_at              TIMESTAMPTZ NOT NULL,
    weekday             SMALLINT NOT NULL,
    session             VARCHAR(20),
    period_start        SMALLINT NOT NULL DEFAULT 1,
    period_count        SMALLINT NOT NULL DEFAULT 1,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    note                TEXT,
    created_by          VARCHAR(36),
    updated_by          VARCHAR(36),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_tse_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED')),
    CONSTRAINT chk_tse_weekday CHECK (weekday BETWEEN 1 AND 7),
    CONSTRAINT chk_tse_period_start CHECK (period_start >= 1),
    CONSTRAINT chk_tse_period_count CHECK (period_count BETWEEN 1 AND 10),
    CONSTRAINT chk_tse_time_range CHECK (start_at < end_at)
);

-- Range query indexes (quan trọng nhất)
CREATE INDEX idx_tse_tenant_timerange ON teaching_schedule_events(tenant_id, start_at, end_at);
CREATE INDEX idx_tse_tenant_school_timerange ON teaching_schedule_events(tenant_id, school_id, start_at, end_at);
CREATE INDEX idx_tse_tenant_class_timerange ON teaching_schedule_events(tenant_id, academic_class_id, start_at, end_at);
CREATE INDEX idx_tse_tenant_subject_timerange ON teaching_schedule_events(tenant_id, subject_id, start_at, end_at);
CREATE INDEX idx_tse_batch ON teaching_schedule_events(batch_id);
CREATE INDEX idx_tse_tenant_status ON teaching_schedule_events(tenant_id, status);
CREATE INDEX idx_tse_school_room ON teaching_schedule_events(school_id, room_name) WHERE room_name IS NOT NULL;

-- ============================================================================
-- 3. Teaching Schedule Event Teachers (join table)
-- ============================================================================
CREATE TABLE teaching_schedule_event_teachers (
    id                      VARCHAR(36) PRIMARY KEY,
    tenant_id               VARCHAR(36) NOT NULL,
    event_id                VARCHAR(36) NOT NULL,
    teacher_user_id         VARCHAR(36) NOT NULL,
    role                    VARCHAR(20) NOT NULL,
    display_name_snapshot   VARCHAR(255),
    color_key               VARCHAR(100),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_tset_role CHECK (role IN ('MAIN', 'ASSISTANT')),
    CONSTRAINT uq_tset_event_teacher_role UNIQUE (event_id, teacher_user_id, role)
);

CREATE INDEX idx_tset_teacher ON teaching_schedule_event_teachers(tenant_id, teacher_user_id);
CREATE INDEX idx_tset_event ON teaching_schedule_event_teachers(event_id);

-- ============================================================================
-- 4. FK Column Indexes (MUST create BEFORE adding FKs — same pattern as V12/V13)
-- ============================================================================
CREATE INDEX idx_tsb_school ON teaching_schedule_batches(school_id);
CREATE INDEX idx_tsb_created_by ON teaching_schedule_batches(created_by);
CREATE INDEX idx_tsb_published_by ON teaching_schedule_batches(published_by);

CREATE INDEX idx_tse_school ON teaching_schedule_events(school_id);
CREATE INDEX idx_tse_created_by ON teaching_schedule_events(created_by);
CREATE INDEX idx_tse_updated_by ON teaching_schedule_events(updated_by);

CREATE INDEX idx_tset_teacher_user ON teaching_schedule_event_teachers(teacher_user_id);

-- ============================================================================
-- 5. Foreign Keys (NOT VALID — same pattern as V12/V13)
-- ============================================================================
-- Giải thích ON DELETE strategy:
--   RESTRICT  = không cho xóa dữ liệu cha khi còn dữ liệu con (school, class, subject, level, batch)
--   CASCADE   = xóa cha thì xóa luôn con (batch → events, event → event_teachers)
--   SET NULL  = xóa cha thì set FK về NULL (user references: created_by, updated_by)

-- 5a. teaching_schedule_batches FKs -------------------------------------------
ALTER TABLE teaching_schedule_batches
    ADD CONSTRAINT fk_tsb_school
        FOREIGN KEY (school_id) REFERENCES education_units(id)
        ON DELETE RESTRICT
        NOT VALID,
    ADD CONSTRAINT fk_tsb_created_by
        FOREIGN KEY (created_by) REFERENCES users(id)
        ON DELETE SET NULL
        NOT VALID,
    ADD CONSTRAINT fk_tsb_published_by
        FOREIGN KEY (published_by) REFERENCES users(id)
        ON DELETE SET NULL
        NOT VALID;

-- 5b. teaching_schedule_events FKs --------------------------------------------
ALTER TABLE teaching_schedule_events
    ADD CONSTRAINT fk_tse_batch
        FOREIGN KEY (batch_id) REFERENCES teaching_schedule_batches(id)
        ON DELETE CASCADE
        NOT VALID,
    ADD CONSTRAINT fk_tse_school
        FOREIGN KEY (school_id) REFERENCES education_units(id)
        ON DELETE RESTRICT
        NOT VALID,
    ADD CONSTRAINT fk_tse_class
        FOREIGN KEY (academic_class_id) REFERENCES academic_classes(id)
        ON DELETE RESTRICT
        NOT VALID,
    ADD CONSTRAINT fk_tse_subject
        FOREIGN KEY (subject_id) REFERENCES lms_subjects(id)
        ON DELETE RESTRICT
        NOT VALID,
    ADD CONSTRAINT fk_tse_level
        FOREIGN KEY (level_id) REFERENCES lms_levels(id)
        ON DELETE RESTRICT
        NOT VALID,
    ADD CONSTRAINT fk_tse_created_by
        FOREIGN KEY (created_by) REFERENCES users(id)
        ON DELETE SET NULL
        NOT VALID,
    ADD CONSTRAINT fk_tse_updated_by
        FOREIGN KEY (updated_by) REFERENCES users(id)
        ON DELETE SET NULL
        NOT VALID;

-- 5c. teaching_schedule_event_teachers FKs ------------------------------------
ALTER TABLE teaching_schedule_event_teachers
    ADD CONSTRAINT fk_tset_event
        FOREIGN KEY (event_id) REFERENCES teaching_schedule_events(id)
        ON DELETE CASCADE
        NOT VALID,
    ADD CONSTRAINT fk_tset_teacher_user
        FOREIGN KEY (teacher_user_id) REFERENCES users(id)
        ON DELETE CASCADE
        NOT VALID;

-- ============================================================================
-- 6. Seed Schedule Permissions
-- ============================================================================

-- Insert permission group if not exists
INSERT INTO permission_groups (id, name, description, sort_order)
SELECT 'pg_lms_schedule', 'LMS Schedule', 'Teaching schedule permissions', 30
WHERE NOT EXISTS (SELECT 1 FROM permission_groups WHERE id = 'pg_lms_schedule');

-- Insert permissions
INSERT INTO permissions (id, name, group_name, label, description)
SELECT 'perm_lms_schedule_read_self', 'lms.schedule.read_self', 'lms',
       'View own schedule',
       'View teaching schedule assigned to the current user'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'lms.schedule.read_self');

INSERT INTO permissions (id, name, group_name, label, description)
SELECT 'perm_lms_schedule_read', 'lms.schedule.read', 'lms',
       'View aggregate schedule',
       'View teaching schedule across managed education units'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'lms.schedule.read');

INSERT INTO permissions (id, name, group_name, label, description)
SELECT 'perm_lms_schedule_manage', 'lms.schedule.manage', 'lms',
       'Manage teaching schedule',
       'Create, update, delete, and publish teaching schedules'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'lms.schedule.manage');

-- Assign to lms.admin
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'lms.admin'
  AND p.name IN ('lms.schedule.read', 'lms.schedule.manage')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- Assign to lms.instructor
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'lms.instructor'
  AND p.name = 'lms.schedule.read_self'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- Assign to lms.learner (student)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'lms.learner'
  AND p.name = 'lms.schedule.read_self'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
```

### 10.1 Relationship Diagram (Entity Relationship)

```
┌─────────────────────────────────┐
│      education_units            │  ← ĐÃ CÓ (schools/centers)
│  PK: id                         │
└─────────────────────────────────┘
          │                              RESTRICT
          │  fk_tsb_school ─────────────────────────────────────┐
          │  fk_tse_school ──────────────────────────────┐      │
          ▼                                              │      │
┌─────────────────────────────────┐                      │      │
│ teaching_schedule_batches       │  (MỚI)              │      │
│  PK: id                         │                      │      │
│  FK: school_id → edu_units      │                      │      │
│  FK: created_by → users         │                      │      │
│  FK: published_by → users       │                      │      │
└─────────────────────────────────┘                      │      │
          │  CASCADE (batch → events)                    │      │
          │  fk_tse_batch                                │      │
          ▼                                              │      │
┌─────────────────────────────────┐                      │      │
│ teaching_schedule_events        │  (MỚI)              │      │
│  PK: id                         │                      │      │
│  FK: batch_id → ts_batches      │  ← CASCADE           │      │
│  FK: school_id → edu_units      │  ← RESTRICT ────────┘      │
│  FK: academic_class_id →        │  ← RESTRICT                │
│       academic_classes          │                             │
│  FK: subject_id → lms_subjects  │  ← RESTRICT                │
│  FK: level_id → lms_levels      │  ← RESTRICT                │
│  FK: created_by → users         │  ← SET NULL                │
│  FK: updated_by → users         │  ← SET NULL                │
└─────────────────────────────────┘                      │      │
          │  CASCADE (event → teachers)                 │      │
          │  fk_tset_event                              │      │
          ▼                                              │      │
┌─────────────────────────────────┐                      │      │
│ teaching_schedule_event_teachers│  (MỚI)              │      │
│  PK: id                         │                      │      │
│  FK: event_id → ts_events       │  ← CASCADE           │      │
│  FK: teacher_user_id → users    │  ← CASCADE           │──────┘
│  UK: (event_id, teacher_user_id,│                      │
│       role)                     │                      │
└─────────────────────────────────┘

┌─────────────────────────────────┐     ┌─────────────────────────────────┐
│         users                   │ ĐÃ CÓ│       academic_classes          │ ĐÃ CÓ
│  PK: id                         │     │  PK: id                         │
└─────────────────────────────────┘     │  FK: school_id → edu_units      │
          ▲                             │  FK: homeroom_teacher_id→users   │
          │ SET NULL                    └─────────────────────────────────┘
          │ (created_by, updated_by)              ▲
          │                                       │ RESTRICT
          │ CASCADE                               │ fk_tse_class
          │ (teacher_user_id)
          │                             ┌─────────────────────────────────┐
          │                             │         lms_subjects            │ ĐÃ CÓ
          │                             │  PK: id                         │
          │                             └─────────────────────────────────┘
          │                                       ▲
          │                                       │ RESTRICT
          │                                       │ fk_tse_subject
          │                             ┌─────────────────────────────────┐
          │                             │         lms_levels              │ ĐÃ CÓ
          │                             │  PK: id                         │
          │                             │  FK: subject_id → lms_subjects  │
          │                             └─────────────────────────────────┘
          │                                       ▲
          │                                       │ RESTRICT
          │                                       │ fk_tse_level
```

### 10.2 ON DELETE Strategy — Decision Rationale

| FK | ON DELETE | Lý do |
|----|-----------|-------|
| `fk_tsb_school` (batches → edu_units) | **RESTRICT** | Không cho xóa trường khi còn lịch giảng dạy. Bảo vệ dữ liệu cốt lõi. |
| `fk_tse_school` (events → edu_units) | **RESTRICT** | Tương tự, không cho xóa trường khi còn event. |
| `fk_tse_class` (events → academic_classes) | **RESTRICT** | Không cho xóa lớp khi đang có lịch dạy. |
| `fk_tse_subject` (events → lms_subjects) | **RESTRICT** | Không cho xóa môn học khi đang được dùng trong lịch. |
| `fk_tse_level` (events → lms_levels) | **RESTRICT** | Không cho xóa level khi đang được dùng trong lịch. |
| `fk_tse_batch` (events → batches) | **CASCADE** | Xóa batch → xóa tất cả events. Parent-child relationship. |
| `fk_tset_event` (event_teachers → events) | **CASCADE** | Xóa event → xóa tất cả teacher assignments. Parent-child. |
| `fk_tset_teacher_user` (event_teachers → users) | **CASCADE** | Xóa user → xóa assignment của user đó (không làm hỏng event). User có thể bị xóa khỏi hệ thống. |
| `fk_tsb_created_by` (batches → users) | **SET NULL** | Xóa user → giữ lại batch, set created_by = NULL. Audit trail vẫn còn. |
| `fk_tsb_published_by` (batches → users) | **SET NULL** | Tương tự, giữ lại thông tin batch. |
| `fk_tse_created_by` (events → users) | **SET NULL** | Xóa user → giữ lại event, set created_by = NULL. |
| `fk_tse_updated_by` (events → users) | **SET NULL** | Tương tự. |

### 10.3 V18 Migration — Validate FKs

Sau khi data migration hoàn tất (V17), cần thêm migration validate FK (theo đúng pattern V16):

```sql
-- V18__validate_teaching_schedule_foreign_keys.sql

-- teaching_schedule_batches FKs
ALTER TABLE teaching_schedule_batches VALIDATE CONSTRAINT fk_tsb_school;
ALTER TABLE teaching_schedule_batches VALIDATE CONSTRAINT fk_tsb_created_by;
ALTER TABLE teaching_schedule_batches VALIDATE CONSTRAINT fk_tsb_published_by;

-- teaching_schedule_events FKs
ALTER TABLE teaching_schedule_events VALIDATE CONSTRAINT fk_tse_batch;
ALTER TABLE teaching_schedule_events VALIDATE CONSTRAINT fk_tse_school;
ALTER TABLE teaching_schedule_events VALIDATE CONSTRAINT fk_tse_class;
ALTER TABLE teaching_schedule_events VALIDATE CONSTRAINT fk_tse_subject;
ALTER TABLE teaching_schedule_events VALIDATE CONSTRAINT fk_tse_level;
ALTER TABLE teaching_schedule_events VALIDATE CONSTRAINT fk_tse_created_by;
ALTER TABLE teaching_schedule_events VALIDATE CONSTRAINT fk_tse_updated_by;

-- teaching_schedule_event_teachers FKs
ALTER TABLE teaching_schedule_event_teachers VALIDATE CONSTRAINT fk_tset_event;
ALTER TABLE teaching_schedule_event_teachers VALIDATE CONSTRAINT fk_tset_teacher_user;
```

---

## 11. Error handling — GraphQL specifics

### 11.1 Exception hierarchy

```
GraphQL request
├── Authentication error → GraphQLError (extensions.code = "UNAUTHORIZED")
│   Throw: throw new ForbiddenException(...)
│   → GlobalExceptionHandler maps to GraphQLError
│
├── Permission error → GraphQLError (extensions.code = "FORBIDDEN")
│   Throw: throw new ForbiddenException(...)
│
├── Input structure error → GraphQLError (extensions.code = "BAD_REQUEST")
│   Throw: throw new BadRequestException(...)
│
└── Row-level validation error: KHÔNG throw
    → Trả về trong TeachingScheduleRowResult.errors[]
    → GraphQL response data vẫn chứa payload với valid=false
```

### 11.2 GraphQL response structure khi có lỗi

```json
{
  "data": {
    "createTeachingSchedule": {
      "batchId": null,
      "createdEventCount": 0,
      "teacherScheduleCount": 0,
      "totalPeriodCount": 0,
      "rowResults": [
        {
          "clientRowId": "row-1",
          "valid": false,
          "eventCount": 0,
          "teacherScheduleCount": 0,
          "errors": [
            {
              "code": "CLASS_NOT_IN_SCHOOL",
              "field": "classIds",
              "message": "Lớp 6A3 không thuộc trường đã chọn"
            }
          ],
          "warnings": []
        },
        {
          "clientRowId": "row-2",
          "valid": true,
          "eventCount": 8,
          "teacherScheduleCount": 24,
          "errors": [],
          "warnings": []
        }
      ],
      "conflicts": []
    }
  },
  "errors": []  // ← Không có errors vì row-level không throw
}
```

Trường hợp authentication error:
```json
{
  "data": {
    "createTeachingSchedule": null
  },
  "errors": [
    {
      "message": "GraphQL field permission is missing.",
      "path": ["createTeachingSchedule"],
      "extensions": {
        "classification": "FORBIDDEN"
      }
    }
  ]
}
```

### 11.3 Error codes đầy đủ

| Code | Field | HTTP Status | Description |
|------|-------|-------------|-------------|
| `UNAUTHORIZED` | — | 401 | Thiếu hoặc sai token |
| `FORBIDDEN` | — | 403 | Thiếu permission |
| `TENANT_MISMATCH` | tenantId | 403 | Tenant không khớp |
| `SCHOOL_NOT_IN_SCOPE` | schoolId | 403 | Trường không trong scope quản lý |
| `CLASS_REQUIRED` | classIds | 400 | Thiếu lớp học |
| `CLASS_NOT_FOUND` | classIds | 400 | Lớp không tồn tại |
| `CLASS_NOT_IN_SCHOOL` | classIds | 400 | Lớp không thuộc trường |
| `MAIN_TEACHER_REQUIRED` | mainTeacherIds | 400 | Thiếu giáo viên chính |
| `TEACHER_NOT_FOUND` | mainTeacherIds | 400 | Giáo viên không tồn tại |
| `SUBJECT_NOT_FOUND` | subjectId | 400 | Môn học không tồn tại |
| `LEVEL_NOT_FOUND` | levelId | 400 | Level không tồn tại |
| `LEVEL_NOT_IN_SUBJECT` | levelId | 400 | Level không thuộc môn |
| `DUPLICATE_TEACHER_ROLE` | assistantTeacherIds | 400 | Trùng giáo viên chính/trợ giảng |
| `INVALID_PERIOD_COUNT` | periodCount | 400 | Số tiết không hợp lệ |
| `INVALID_PERIOD_START` | periodStart | 400 | Tiết bắt đầu không hợp lệ |
| `INVALID_TIME_RANGE` | startTime | 400 | Giờ bắt đầu >= giờ kết thúc |
| `INVALID_TIME_FORMAT` | startTime | 400 | Sai định dạng giờ |
| `INVALID_WEEKDAY` | weekday | 400 | Thứ không hợp lệ |
| `INVALID_REPEAT_WEEKS` | repeatWeeks | 400 | Số tuần lặp không hợp lệ |
| `INVALID_APPLY_DATE` | applyFrom | 400 | Ngày áp dụng không hợp lệ |
| `TEACHER_TIME_CONFLICT` | — | 409 | Giáo viên trùng giờ |
| `CLASS_TIME_CONFLICT` | — | 409 | Lớp trùng giờ |
| `ROOM_TIME_CONFLICT` | — | 409 | Phòng trùng giờ |
| `IDEMPOTENCY_CONFLICT` | idempotencyKey | 409 | Key đã tồn tại với data khác |

---

## 12. Performance

### 12.1 Range query limit

- `from` và `to` bắt buộc.
- Max range mặc định **92 ngày** (~3 tháng) cho workspace. Nếu vượt quá, BE tự động cắt xuống 92 ngày và trả về `WARNING` trong extensions.
- Nếu cần năm học đầy đủ thì dùng endpoint export riêng (phase sau).

### 12.2 Index strategy

Các index đã được thiết kế để cover tất cả query patterns:

| Query Pattern | Index Used |
|---------------|-----------|
| Teacher calendar (`teacher_user_id` + date range) | `idx_tset_teacher` + `idx_tse_tenant_timerange` |
| LCMS aggregate (`school_id` + date range) | `idx_tse_tenant_school_timerange` |
| Filter by class | `idx_tse_tenant_class_timerange` |
| Filter by subject | `idx_tse_tenant_subject_timerange` |
| Batch lookup | `idx_tse_batch` |
| Idempotency check | `idx_tsb_tenant_idempotency_key` |

### 12.3 Batch loading reference data

Để tránh N+1 khi query calendar events:
- Load tất cả events trong range trước (1 query).
- Extract tất cả `schoolId`, `classId`, `subjectId`, `levelId` từ events.
- Load schools, classes, subjects, levels trong 4 query riêng biệt (batch).
- Map vào events trong memory.

Không sử dụng JPA `@ManyToOne` fetch eager cho các reference data này — sẽ gây N+1.

### 12.4 Pagination

Workspace query không paginate events (trả về tối đa 1000 events trong range, nếu quá thì cắt và báo trong `extensions`). Filter options và catalog thì luôn trả về toàn bộ (số lượng trường/lớp/môn giới hạn).

---

## 13. Test plan — Mở rộng

### 13.1 Unit tests

**TeachingScheduleExpansionServiceTest:**
- 1 row, 1 class, 1 week, 1 main teacher → 1 event + 1 assignment
- 1 row, 3 classes, 4 weeks, 2 main + 1 assistant → 12 events + 36 assignments
- `periodCount=2`, `periodStart=3` → period range 3-4
- `repeatWeeks=0` → bị reject (throw IllegalArgumentException)
- `startTime >= endTime` → bị reject
- `weekday` mapping đúng: Monday=1, Sunday=7
- `applyFrom` + `weekday` + `repeatWeeks` tính đúng danh sách ngày
- Cross-month expansion: `applyFrom=2026-01-29(Thu)`, `weekday=5(Fri)`, `repeatWeeks=4` → [Jan 30, Feb 6, Feb 13, Feb 20]

**TeachingScheduleValidationServiceTest:**
- ClassIds rỗng → `CLASS_REQUIRED`
- Class không thuộc school → `CLASS_NOT_IN_SCHOOL`
- MainTeacherIds rỗng → `MAIN_TEACHER_REQUIRED`
- Subject không tồn tại → `SUBJECT_NOT_FOUND`
- Level không thuộc subject → `LEVEL_NOT_IN_SUBJECT`
- Assistant trùng main → `DUPLICATE_TEACHER_ROLE`
- periodCount=0 → `INVALID_PERIOD_COUNT`
- periodCount=11 → `INVALID_PERIOD_COUNT`
- startTime="08:00", endTime="08:00" → `INVALID_TIME_RANGE`
- startTime="abc" → `INVALID_TIME_FORMAT`
- weekday=0 → `INVALID_WEEKDAY`
- weekday=8 → `INVALID_WEEKDAY`

**TeachingScheduleConflictServiceTest:**
- Teacher A trùng giờ ở event khác → conflict
- Teacher A không trùng nếu khác ngày → không conflict
- Teacher A không trùng nếu event cũ đã CANCELLED → không conflict
- Class trùng giờ → conflict
- Room trùng giờ → warning (không phải error)
- 2 rows trong cùng batch xung đột → conflict
- Conflict giữa main teacher và assistant teacher → không conflict (khác role, cùng event)

### 13.2 Integration tests (Testcontainers Postgres)

- Create batch persist đúng rows/events/teachers
- Idempotency same key same payload → trả kết quả cũ, không tạo mới
- Idempotency same key different payload → throw `IDEMPOTENCY_CONFLICT`
- Query teacher A không trả event của teacher B
- Query LCMS scope school A không trả school B
- Query filter subject/class/school đúng count
- Concurrent creation with advisory lock → lần 2 bị từ chối
- Range query với index hoạt động (verify query plan)
- Soft delete: cancel event → status = CANCELLED, query không trả về (khi filter status=PUBLISHED)

### 13.3 GraphQL tests

- `lcms.teachingCalendarWorkspace` trả events + filterOptions + catalog + laneSummaries + permissions
- `lms.myTeachingCalendar` lấy principal user, không cần teacherId
- `lms.myTeachingCalendar` với student account → chỉ thấy event của lớp mình
- `previewTeachingSchedule` trả row-level errors không persist
- `createTeachingSchedule` persist và trả counts
- `publishTeachingScheduleBatch` cập nhật status và emit notification
- `cancelTeachingScheduleBatch` cập nhật status
- Permission missing → `FORBIDDEN`
- School scope violation → `FORBIDDEN`

### 13.4 FE contract tests

Sau khi BE xong, FE cần test:
- Map `TeachingCalendarEvent` sang `CenterUpCalendarEvent` component
- LCMS filter modes đúng:
  - `ALL` → filter list: schools, event color: school
  - `BY_SCHOOL` → filter list: teachers, event color: teacher
  - `BY_TEACHER` → filter list: schools, event color: school
- LMS filter modes đúng:
  - `ALL` → filter list: schools, event color: school
  - `BY_SCHOOL` → filter list: classes, event color: class
  - `BY_SUBJECT` → filter list: subjects, event color: subject
- Event detail hiển thị đủ: ngày, giờ, số tiết, môn, lớp, phòng, giáo viên, trường
- Notification deep link mở đúng màn hình calendar
- Notification hiển thị đúng title/body

---

## 14. Acceptance criteria

BE được xem là hoàn thành khi:

- [ ] LCMS tạo lịch từ bảng nhiều dòng qua GraphQL mutation.
- [ ] BE phân giải đúng thành event theo lớp/ngày/tuần và teacher assignment theo từng cá nhân.
- [ ] Teacher A vào LMS chỉ thấy lịch có `teacher_user_id = A`.
- [ ] Student chỉ thấy lịch lớp của mình nếu mở student calendar.
- [ ] LCMS xem aggregate theo scope, không đọc được ngoài school scope.
- [ ] Preview trả lỗi theo từng row để FE highlight cell.
- [ ] Conflict giáo viên/lớp/phòng được chặn đúng.
- [ ] Query calendar thẳng không N+1 và có index.
- [ ] Permissions được seed và resolver enforce bằng `GraphQlAccessSupport`.
- [ ] Notification được gửi khi publish batch (đúng userId, đúng deep link).
- [ ] Idempotency hoạt động: bấm 2 lần không tạo trùng.
- [ ] Unit tests + Integration tests + GraphQL tests pass toàn bộ.

---

## 15. Thứ tự implementation — Cập nhật chi tiết

### Phase 1: Foundation (Migration + Entities)
1. Viết `V17__teaching_schedule_schema.sql` với đầy đủ tables, indexes, FK indexes, FKs (NOT VALID), permission seed.
2. Viết `V18__validate_teaching_schedule_foreign_keys.sql` validate tất cả constraint.
3. Chạy migration, verify trên local Postgres.
4. Tạo 3 entity classes (`TeachingScheduleBatch`, `TeachingScheduleEvent`, `TeachingScheduleEventTeacher`).
5. Tạo 3 repository interfaces với các query methods.
6. Tạo projection records cho calendar read model.

### Phase 2: Business Logic
6. Tạo request/response records (`TeachingScheduleRequests`, `TeachingScheduleResponses`).
7. Implement `TeachingScheduleExpansionService` + unit tests.
8. Implement `TeachingScheduleValidationService` + unit tests.
9. Implement `TeachingScheduleConflictService` + unit tests.
10. Implement `TeachingScheduleCatalogService` (catalog + filter options).

### Phase 3: Command & Read
11. Implement `TeachingScheduleCommandService` (create/preview/publish/cancel/update/delete).
12. Implement `TeachingScheduleReadService` (LCMS workspace + LMS teacher calendar).
13. Wire notification vào `TeachingScheduleCommandService.publishBatch()`.

### Phase 4: GraphQL API
14. Cập nhật `schema.graphqls` (thêm Mutation type, thêm field vào LcmsQuery và LmsQuery).
15. Implement `LcmsTeachingScheduleGraphQlController`.
16. Implement `LmsTeachingScheduleGraphQlController`.
17. Implement `TeachingScheduleMutationGraphQlController`.
18. Cập nhật `ErgPolicyService.ROLE_PERMISSIONS`.

### Phase 5: Testing
19. Viết unit tests cho từng service.
20. Viết integration tests với Testcontainers Postgres.
21. Viết GraphQL controller tests.
22. Viết GraphQL integration tests (full schema test).

### Phase 6: FE Integration
23. Cập nhật FE query layer thay mock calendar.
24. Playwright verify LCMS/LMS calendar flows.
25. Kiểm tra notification hiển thị đúng.

---

## 16. Rủi ro và mitigation

| Rủi ro | Impact | Mitigation |
|--------|--------|------------|
| Mutation chưa có tiền lệ trong codebase | Medium | Viết POC mutation controller trước, verify Spring GraphQL `@MutationMapping` hoạt động với guard/rate-limit hiện tại |
| Performance batch insert 600+ events | Medium | Thiết lập JDBC batch size, benchmark với 1000 events, fallback sang JdbcTemplate nếu cần |
| Room conflict detection không chính xác | Low | V1 chỉ dùng text comparison + WARNING severity, không block |
| Dữ liệu tham chiếu không tồn tại khi tạo FK | Medium | FK tạo với NOT VALID (V17), validate sau (V18). Nếu seed data chưa có user/school, constraint vẫn được tạo nhưng chưa validate dữ liệu cũ. |
| FE calendar component chưa hỗ trợ GraphQL | Medium | FE đã có `graphQlRequest`, calendar component `centerup-calendar-workspace` đã có sẵn |
| Notification rate limit cho batch lớn | Low | Notification đã có rate limit per user, outbox xử lý async |

---

## 17. Ghi chú

- Plan này thay thế hoàn toàn bản v1 trước đó.
- Mọi thông tin đã được verify với source code thực tế tại thời điểm 2026-06-14.
- Codebase hiện tại có đầy đủ infrastructure (GraphQL guard, security, notification, repository pattern) — chỉ cần xây dựng domain logic mới.
- REST không phải interface chính cho calendar V1. Nếu cần REST endpoint cho mobile app hoặc webhook, sẽ thêm ở phase sau.
