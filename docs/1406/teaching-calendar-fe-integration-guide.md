# Teaching Calendar — FE Integration Guide

**Version:** 1.0  
**Date:** 2026-06-15  
**GraphQL Endpoint:** `POST /api/v1/graphql`  
**REST Endpoint:** `POST /api/v1/calendar/*` (file upload, admin convenience)  
**Authentication:** Bearer token trong `Authorization` header

---

## Table of Contents

1. [Overview & Architecture](#1-overview--architecture)
2. [Quick Start — GraphQL Queries](#2-quick-start--graphql-queries)
3. [LCMS Workspace — `lcms.teachingCalendarWorkspace`](#3-lcms-workspace)
4. [LMS Teacher Calendar — `lms.myTeachingCalendar`](#4-lms-teacher-calendar)
5. [LMS Student Calendar — `lms.myStudentCalendar`](#5-lms-student-calendar)
6. [Mutations — Create/Update/Delete](#6-mutations)
7. [Bulk Create Flow — Excel-like Grid](#7-bulk-create-flow)
8. [Conflict Detection & Resolution](#8-conflict-detection--resolution)
9. [Catalog, Filters & Lanes](#9-catalog-filters--lanes)
10. [Permissions & Authorization](#10-permissions--authorization)
11. [REST Endpoints (Supplemental)](#11-rest-endpoints)
12. [Error Handling](#12-error-handling)
13. [Appendix: Full Schema](#13-appendix-full-schema)

---

## 1. Overview & Architecture

### 1.1 Who sees what

| Portal | Query | User | Scope |
|--------|-------|------|-------|
| **LCMS** | `lcms.teachingCalendarWorkspace` | Admin/Manager | Aggregate view across managed schools |
| **LMS** | `lms.myTeachingCalendar` | Teacher | Self-scoped — only own events |
| **LMS** | `lms.myStudentCalendar` | Student | Self-scoped — only class events |

### 1.2 Key concepts

| Concept | Description |
|---------|-------------|
| **Batch** | Một lần tạo lịch từ LCMS. Chứa N rows, mỗi row expand ra nhiều events. |
| **Event** | Một buổi học cụ thể: `classId + subjectId + startAt/endAt`. |
| **Teacher Assignment** | Liên kết giữa event và teacher (MAIN hoặc ASSISTANT). |
| **Room** | Phòng học. Có thể tạo ad-hoc từ màn hình tạo lịch. |
| **Weekday** | ISO 1=Monday, 7=Sunday. |
| **ViewMode** | `ALL`, `BY_SCHOOL`, `BY_TEACHER`, `BY_SUBJECT` — ảnh hưởng cách grouping lanes. |

### 1.3 Standard headers

```
Authorization: Bearer <token>
Content-Type: application/json
X-Tenant-ID: <tenant-id>       # Optional, used for tenant override
X-Portal: lcms|lms              # Optional, used for portal verification
```

---

## 2. Quick Start — GraphQL Queries

### 2.1 Single GraphQL request pattern

Tất cả GraphQL request đều qua `POST /api/v1/graphql`:

```
POST /api/v1/graphql
Authorization: Bearer eyJhbGci...
Content-Type: application/json

{
  "query": "query { lcms { teachingCalendarWorkspace(input: { from: \"2026-06-15\", to: \"2026-06-21\" }) { tenantId events { id title } } } }"
}
```

### 2.2 Response envelope

```json
{
  "data": {
    "lcms": {
      "teachingCalendarWorkspace": { ... }
    }
  },
  "errors": [
    {
      "message": "Authentication is required",
      "locations": [{ "line": 1, "column": 1 }],
      "extensions": {
        "code": "AUTHENTICATION_REQUIRED",
        "classification": "FORBIDDEN"
      }
    }
  ]
}
```

> **Lưu ý:** GraphQL luôn trả về HTTP 200. Lỗi authentication/permission nằm trong `errors[]`. Lỗi 401 HTTP chỉ xảy ra khi không có token hợp lệ.

### 2.3 Get started with these 3 queries

```graphql
# 1. LCMS — xem tất cả lịch (admin)
query LCMS_CALENDAR {
  lcms {
    teachingCalendarWorkspace(input: {
      from: "2026-06-15", to: "2026-06-21"
    }) {
      tenantId from to viewMode
      events { id title startAt endAt className teachers { userId displayName role } }
      filterOptions { schools { id label count } teachers { id label count } }
      laneSummaries { id label count colorKey }
      catalog { schools { id label } classes { id label schoolId } teachers { id label } rooms { id label schoolId } }
      permissions { canCreate canUpdate canDelete canPublish canViewAggregate }
    }
  }
}

# 2. LMS Teacher — xem lịch cá nhân
query MY_CALENDAR {
  lms {
    myTeachingCalendar(input: {
      from: "2026-06-15", to: "2026-06-21"
    }) {
      # Same TeachingCalendarWorkspace shape as above
      events { id title startAt endAt className teachers { userId displayName role } }
    }
  }
}

# 3. LMS Student — xem lịch lớp
query MY_STUDENT_CALENDAR {
  lms {
    myStudentCalendar(input: {
      from: "2026-06-15", to: "2026-06-21"
    }) {
      # Same TeachingCalendarWorkspace shape
      events { id title startAt endAt className teachers { userId displayName role } }
    }
  }
}
```

---

## 3. LCMS Workspace

### 3.1 Query

```graphql
query LCMS_CALENDAR_WORKSPACE($input: TeachingCalendarWorkspaceInput!) {
  lcms {
    teachingCalendarWorkspace(input: $input) {
      tenantId
      from
      to
      viewMode
      events { ...CalendarEventFields }
      filterOptions { ...FilterOptionFields }
      laneSummaries { ...LaneSummaryFields }
      catalog { ...CatalogFields }
      permissions { canCreate canUpdate canDelete canPublish canViewAggregate }
      generatedAt
    }
  }
}
```

### 3.2 Input

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `tenantId` | `String` | No | Tenant ID (lấy từ header nếu không gửi) |
| `from` | `String` | **Yes** | Ngày bắt đầu (ISO: `yyyy-MM-dd`) |
| `to` | `String` | **Yes** | Ngày kết thúc (ISO: `yyyy-MM-dd`) |
| `viewMode` | `TeachingCalendarViewMode` | No | `ALL` (default), `BY_SCHOOL`, `BY_TEACHER`, `BY_SUBJECT` |
| `schoolIds` | `[String!]` | No | Filter theo trường. Nếu null → dùng managed schools từ token |
| `teacherIds` | `[String!]` | No | Filter theo giáo viên |
| `classIds` | `[String!]` | No | Filter theo lớp |
| `subjectIds` | `[String!]` | No | Filter theo môn |
| `levelIds` | `[String!]` | No | Filter theo level |
| `statuses` | `[TeachingScheduleStatus!]` | No | `DRAFT`, `PUBLISHED`, `CANCELLED`. Mặc định: `[DRAFT, PUBLISHED]` |

### 3.3 Response — Full shape

```json
{
  "data": {
    "lcms": {
      "teachingCalendarWorkspace": {
        "tenantId": "erg",
        "from": "2026-06-15",
        "to": "2026-06-21",
        "viewMode": "BY_SCHOOL",
        "events": [
          {
            "id": "evt_abc123",
            "batchId": "batch_def456",
            "title": "Toán - Lớp 6A1",
            "startAt": "2026-06-16T07:00:00+07:00",
            "endAt": "2026-06-16T08:30:00+07:00",
            "date": "2026-06-16",
            "startTime": "07:00",
            "endTime": "08:30",
            "schoolId": "sch_binh_an",
            "schoolName": "THCS Bình An",
            "classId": "cls_6a1",
            "className": "6A1",
            "subjectId": "sub_math",
            "subjectName": "Toán",
            "levelId": "lvl_basic",
            "levelName": "Cơ bản",
            "roomId": null,
            "roomName": "Phòng 101",
            "periodStart": 1,
            "periodCount": 2,
            "teachers": [
              { "userId": "teacher_a", "displayName": "Nguyễn Văn A", "role": "MAIN", "colorKey": null },
              { "userId": "teacher_c", "displayName": "Trần Thị C", "role": "ASSISTANT", "colorKey": null }
            ],
            "status": "PUBLISHED",
            "note": null,
            "colorGroup": { "key": "blue", "label": null, "kind": "subject" }
          }
        ],
        "filterOptions": {
          "schools": [{ "id": "sch_binh_an", "label": "THCS Bình An", "count": 15, "colorKey": null }],
          "classes": [{ "id": "cls_6a1", "label": "6A1", "count": 8, "colorKey": null }],
          "teachers": [{ "id": "teacher_a", "label": "Nguyễn Văn A", "count": 8, "colorKey": "blue" }],
          "subjects": [{ "id": "sub_math", "label": "Toán", "count": 4, "colorKey": null }],
          "levels": [{ "id": "lvl_basic", "label": "Cơ bản", "count": 4, "colorKey": null }]
        },
        "laneSummaries": [
          { "id": "sch_binh_an", "label": "THCS Bình An", "count": 15, "colorKey": null },
          { "id": "sch_cat_lai", "label": "THCS Cát Lái", "count": 10, "colorKey": null }
        ],
        "catalog": {
          "schools": [{ "id": "sch_binh_an", "label": "THCS Bình An", "count": 0, "colorKey": null }],
          "classes": [{ "id": "cls_6a1", "schoolId": "sch_binh_an", "label": "6A1", "grade": "6", "academicYear": "2025-2026" }],
          "teachers": [{ "id": "teacher_a", "label": "Nguyễn Văn A", "colorKey": "blue" }],
          "assistantTeachers": [{ "id": "teacher_c", "label": "Trần Thị C", "colorKey": "green" }],
          "subjects": [{ "id": "sub_math", "label": "Toán" }],
          "levels": [{ "id": "lvl_basic", "subjectId": "sub_math", "label": "Cơ bản", "sortOrder": 1 }],
          "rooms": [{ "id": "room_001", "label": "Phòng 101", "schoolId": "sch_binh_an" }]
        },
        "permissions": {
          "canCreate": true,
          "canUpdate": true,
          "canDelete": true,
          "canPublish": true,
          "canViewAggregate": true
        },
        "generatedAt": "2026-06-15T10:30:00+07:00"
      }
    }
  }
}
```

### 3.4 Events — Field reference

| Field | Type | Notes |
|-------|------|-------|
| `id` | `String!` | Event ID |
| `batchId` | `String` | Batch ID (nếu event được tạo từ bulk) |
| `title` | `String!` | `"{subjectName} - {className}"` — Generated by BE |
| `startAt` | `String!` | ISO 8601 with timezone |
| `endAt` | `String!` | ISO 8601 with timezone |
| `date` | `String!` | `yyyy-MM-dd` (convenience field) |
| `startTime` | `String!` | `HH:mm` (convenience field) |
| `endTime` | `String!` | `HH:mm` (convenience field) |
| `schoolId` | `String!` | |
| `schoolName` | `String!` | Resolved from education_units |
| `classId` | `String!` | academic_class_id |
| `className` | `String!` | Resolved from academic_classes |
| `subjectId` | `String` | |
| `subjectName` | `String` | Resolved from lms_subjects |
| `levelId` | `String` | |
| `levelName` | `String` | Resolved from lms_levels |
| `roomId` | `String` | Nullable |
| `roomName` | `String` | Nullable |
| `periodStart` | `Int!` | Tiết bắt đầu |
| `periodCount` | `Int!` | Số tiết |
| `teachers` | `[TeachingScheduleTeacherAssignment!]!` | Always non-null list |
| `status` | `TeachingScheduleStatus!` | `DRAFT` / `PUBLISHED` / `CANCELLED` |
| `note` | `String` | |
| `colorGroup` | `TeachingCalendarColorGroup!` | `key` = color key để FE map sang palette |

---

## 4. LMS Teacher Calendar

### 4.1 Query

```graphql
query MY_CALENDAR($input: MyTeachingCalendarInput!) {
  lms {
    myTeachingCalendar(input: $input) {
      tenantId
      from
      to
      viewMode
      events { ...CalendarEventFields }
      filterOptions { ...FilterOptionFields }
      laneSummaries { ...LaneSummaryFields }
      catalog { ...CatalogFields }
      permissions { canCreate canUpdate canDelete canPublish canViewAggregate }
      generatedAt
    }
  }
}
```

### 4.2 Key differences from LCMS

| Aspect | LCMS | LMS Teacher |
|--------|------|-------------|
| **Scope** | Managed schools | **Self only** — `principal.userId()` được dùng làm teacher filter |
| **schoolIds** | Có thể chọn | Optional UX filter, không phải security |
| **teacherIds** | Có thể chọn | **Không có** — luôn là chính user |
| **TeacherIds input** | `teacherIds: [String!]` | Not available |
| **Permission** | `lms.schedule.read` | `lms.schedule.read_self` |

### 4.3 Input

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `tenantId` | `String` | No | |
| `from` | `String` | **Yes** | |
| `to` | `String` | **Yes** | |
| `viewMode` | `TeachingCalendarViewMode` | No | |
| `schoolIds` | `[String!]` | No | UX filter — không ảnh hưởng security |
| `classIds` | `[String!]` | No | UX filter |
| `subjectIds` | `[String!]` | No | UX filter |
| `levelIds` | `[String!]` | No | UX filter |
| `statuses` | `[TeachingScheduleStatus!]` | No | |

> **Security note:** Teacher không thể xem lịch của teacher khác. API tự động lấy `userId` từ token.

---

## 5. LMS Student Calendar

### 5.1 Query

```graphql
query MY_STUDENT_CALENDAR($input: MyStudentCalendarInput!) {
  lms {
    myStudentCalendar(input: $input) {
      tenantId
      from
      to
      viewMode
      events { ...CalendarEventFields }
      filterOptions { ...FilterOptionFields }
      laneSummaries { ...LaneSummaryFields }
      catalog { ...CatalogFields }
      permissions { canCreate canUpdate canDelete canPublish canViewAggregate }
      generatedAt
    }
  }
}
```

### 5.2 Key differences

| Aspect | LCMS | LMS Teacher | LMS Student |
|--------|------|-------------|-------------|
| **Scope** | Managed schools | Self (teacher events) | **Self (class events)** |
| **Class filter** | User picks | UX filter | **Auto — only student's class** |
| **schoolIds input** | Available | Available | **Not available** |
| **classIds input** | Available | Available | **Not available** |
| **Mapping** | — | `principal.userId` → teacherId | `principal.userId` → `Student.authUserId` → `academicClassId` |

### 5.3 How student → class mapping works

```
principal.userId (from token)
    → StudentRepository.findFirstByTenantIdAndAuthUserId(tenantId, userId)
    → Student.academicClassId (e.g., "cls_6a1")
    → Chỉ query events có academic_class_id = "cls_6a1"
```

### 5.4 Input

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `tenantId` | `String` | No | |
| `from` | `String` | **Yes** | |
| `to` | `String` | **Yes** | |
| `viewMode` | `TeachingCalendarViewMode` | No | |
| `subjectIds` | `[String!]` | No | UX filter |
| `levelIds` | `[String!]` | No | UX filter |
| `statuses` | `[TeachingScheduleStatus!]` | No | |

> **Lưu ý:** Student không thấy `schoolIds` hay `classIds` filter vì họ chỉ có 1 class. Nếu student chưa được assign class → API trả về lỗi.

---

## 6. Mutations

### 6.1 All mutations at a glance

| Mutation | Input | Output | Permission |
|----------|-------|--------|------------|
| `previewTeachingSchedule` | `TeachingScheduleBulkInput!` | `TeachingSchedulePreviewPayload!` | `lms.schedule.manage` |
| `createTeachingSchedule` | `TeachingScheduleBulkInput!` | `TeachingScheduleBulkPayload!` | `lms.schedule.manage` |
| `updateTeachingScheduleEvent` | `TeachingScheduleEventUpdateInput!` | `TeachingScheduleEventPayload!` | `lms.schedule.manage` |
| `deleteTeachingScheduleEvent` | `TeachingScheduleEventDeleteInput!` | `TeachingScheduleDeletePayload!` | `lms.schedule.manage` |
| `deleteTeacherSchedule` | `DeleteTeacherScheduleInput!` | `TeachingScheduleDeletePayload!` | `lms.schedule.manage` |
| `publishTeachingScheduleBatch` | `batchId: String!` | `TeachingScheduleBatchPayload!` | `lms.schedule.manage` |
| `cancelTeachingScheduleBatch` | `batchId: String!` | `TeachingScheduleBatchPayload!` | `lms.schedule.manage` |
| `createRoom` | `CreateRoomInput!` | `TeachingScheduleRoomOption!` | `lms.schedule.manage` |
| `importTeachingScheduleFromExcel` | `TeachingScheduleExcelImportInput!` | `TeachingSchedulePreviewPayload!` | `lms.schedule.manage` |

### 6.2 `previewTeachingSchedule`

Trả về preview **không persist** — dùng để show conflict trước khi tạo thật.

```graphql
mutation PREVIEW($input: TeachingScheduleBulkInput!) {
  previewTeachingSchedule(input: $input) {
    valid
    previewEventCount
    teacherScheduleCount
    totalPeriodCount
    rowResults {
      clientRowId
      valid
      eventCount
      teacherScheduleCount
      errors { code field message }
      warnings { code field message }
    }
    conflicts {
      code
      severity
      clientRowId
      existingEventId
      message
    }
  }
}
```

### 6.3 `createTeachingSchedule` — THE MAIN MUTATION

```graphql
mutation CREATE($input: TeachingScheduleBulkInput!) {
  createTeachingSchedule(input: $input) {
    batchId
    createdEventCount
    teacherScheduleCount
    totalPeriodCount
    rowResults {
      clientRowId
      valid
      eventCount
      teacherScheduleCount
      errors { code field message }
      warnings { code field message }
    }
    conflicts {
      code
      severity
      clientRowId
      existingEventId
      message
    }
  }
}
```

**Input detail:**

```json
{
  "input": {
    "tenantId": "erg",
    "idempotencyKey": "uuid-v7-or-client-generated",
    "schoolId": "sch_binh_an",
    "academicYear": "2025-2026",
    "applyFrom": "2026-06-15",
    "repeatWeeks": 4,
    "note": "Học kỳ 1",
    "overrideConflicts": false,
    "resolveMode": "REJECT",
    "rows": [
      {
        "clientRowId": "row-1",
        "classIds": ["cls_6a1", "cls_6a2"],
        "weekday": 3,
        "session": "MORNING",
        "periodStart": 1,
        "periodCount": 2,
        "startTime": "07:00",
        "endTime": "08:30",
        "subjectId": "sub_math",
        "levelId": "lvl_basic",
        "roomId": null,
        "roomName": "Phòng 101",
        "mainTeacherIds": ["teacher_a", "teacher_b"],
        "assistantTeacherIds": ["teacher_c"]
      }
    ]
  }
}
```

**Expansion logic (what happens per row):**

| Row field | → Expands to |
|-----------|-------------|
| `classIds: ["6A1", "6A2"]` × `repeatWeeks: 4` | **8 events** (2 classes × 4 weeks) |
| `mainTeacherIds: ["A", "B"]` × `assistantTeacherIds: ["C"]` | **24 teacher assignments** (8 events × 3 teachers) |

**Idempotency:**

| Scenario | Behavior |
|----------|----------|
| Same `idempotencyKey` + same payload | Trả về batch cũ, không tạo mới |
| Same `idempotencyKey` + khác payload | **Throw error** — `ConflictException` |
| No `idempotencyKey` | Tạo mới bình thường |

**Advisory lock:** Nếu 2 admin cùng tạo lịch cho 1 trường, cái thứ 2 sẽ nhận lỗi "Another bulk schedule operation is in progress for this school."

### 6.4 Other mutations

**`updateTeachingScheduleEvent`** — Chỉ update `roomId`, `roomName`, `note`:

```graphql
mutation UPDATE_EVENT($input: TeachingScheduleEventUpdateInput!) {
  updateTeachingScheduleEvent(input: {
    tenantId: "erg",
    eventId: "evt_abc123",
    roomId: "room_002",
    roomName: "Phòng 102",
    note: "Đã đổi phòng"
  }) {
    event { id title startAt roomName }
  }
}
```

**`deleteTeachingScheduleEvent`** — Cancel 1 event (soft delete):

```graphql
mutation DELETE_EVENT($input: TeachingScheduleEventDeleteInput!) {
  deleteTeachingScheduleEvent(input: { tenantId: "erg", eventId: "evt_abc123" }) {
    eventId
    deleted
    affectedEventCount
  }
}
```

**`deleteTeacherSchedule`** — Bulk cancel by teacher + optional scope:

```graphql
mutation DELETE_TEACHER_SCHEDULE($input: DeleteTeacherScheduleInput!) {
  deleteTeacherSchedule(input: {
    tenantId: "erg",
    teacherUserId: "teacher_a",
    schoolIds: ["sch_binh_an"],
    dateFrom: "2026-06-15",
    dateTo: "2026-07-15"
  }) {
    eventId
    deleted
    affectedEventCount
  }
}
```

| schoolIds | dateFrom | dateTo | Behavior |
|-----------|----------|--------|----------|
| `["sch_A"]` | `"2026-06-15"` | `"2026-07-15"` | Xóa lịch GV ở trường A, 15/06 → 15/07 |
| `null` | `"2026-06-15"` | `null` | Xóa lịch GV ở TẤT CẢ trường trong scope, từ 15/06 |
| `["sch_A"]` | `null` | `null` | Xóa TẤT CẢ lịch tương lai của GV ở trường A |

**`publishTeachingScheduleBatch`:**

```graphql
mutation PUBLISH_BATCH {
  publishTeachingScheduleBatch(batchId: "batch_def456") {
    batchId
    status
    affectedEventCount
  }
}
```

Khi publish:
- Batch status → `PUBLISHED`
- Tất cả events trong batch → `PUBLISHED`
- **Gửi notification** `SCHEDULE_PUBLISHED` tới tất cả teachers có event trong batch

**`cancelTeachingScheduleBatch`:**

```graphql
mutation CANCEL_BATCH {
  cancelTeachingScheduleBatch(batchId: "batch_def456") {
    batchId
    status
    affectedEventCount
  }
}
```

**`createRoom`** — Tạo phòng ad-hoc từ màn hình tạo lịch:

```graphql
mutation CREATE_ROOM($input: CreateRoomInput!) {
  createRoom(input: { schoolId: "sch_binh_an", roomName: "Phòng 101" }) {
    id
    label
    schoolId
  }
}
```

Idempotent: Nếu phòng đã tồn tại → trả về phòng cũ, không throw lỗi.

**`importTeachingScheduleFromExcel`** — Stub (chưa implement Excel parsing):

```graphql
mutation IMPORT_EXCEL($input: TeachingScheduleExcelImportInput!) {
  importTeachingScheduleFromExcel(input: {
    schoolId: "sch_binh_an",
    applyFrom: "2026-06-15",
    repeatWeeks: 4
  }) {
    valid
    previewEventCount
    teacherScheduleCount
    totalPeriodCount
    rowResults { clientRowId valid errors { code field message } }
    conflicts { code severity message }
  }
}
```

> ⚠️ Mutation này hiện throw `UnsupportedOperationException`. Dùng `createTeachingSchedule` với `TeachingScheduleBulkInput` để tạo lịch.

---

## 7. Bulk Create Flow

### 7.1 Recommended FE flow

```
[User fills grid] 
    ↓
[Preview] → previewTeachingSchedule(input)
    ↓
[Show conflicts to user]
    ↓
[User decides: override? resolve?]
    ↓
[Create] → createTeachingSchedule(input with resolveMode)
    ↓
[Show result]
```

### 7.2 `clientRowId` pattern

Mỗi row trong input phải có `clientRowId` (do FE tự sinh, UUID là đủ). BE trả về kết quả theo `clientRowId` để FE map ngược lại UI:

```
Input:
  rows: [
    { clientRowId: "row-1", classIds: ["6A1"], ... },
    { clientRowId: "row-2", classIds: ["6A2"], ... }
  ]

Output rowResults:
  [
    { clientRowId: "row-1", valid: true, eventCount: 4, ... },
    { clientRowId: "row-2", valid: false, eventCount: 0, errors: [...] }
  ]
```

### 7.3 Weekday ↔ Date mapping

```
applyFrom = 2026-06-15 (Monday)
weekday = 3 (Tuesday)

calculateDates(2026-06-15, 3, 4):
  → [2026-06-16, 2026-06-23, 2026-06-30, 2026-07-07]
```

| weekday | ISO value | Vietnamese |
|---------|-----------|------------|
| Monday | 1 | Thứ Hai |
| Tuesday | 2 | Thứ Ba |
| Wednesday | 3 | Thứ Tư |
| Thursday | 4 | Thứ Năm |
| Friday | 5 | Thứ Sáu |
| Saturday | 6 | Thứ Bảy |
| Sunday | 7 | Chủ Nhật |

### 7.4 Time handling

- `startTime` / `endTime` format: `HH:mm` (24h)
- All times stored in `Asia/Ho_Chi_Minh` timezone (+07:00)
- BE trả về ISO 8601: `2026-06-16T07:00:00+07:00`
- FE chỉ cần show theo format: `HH:mm` (convenience field `startTime`/`endTime` dùng cho việc này)

### 7.5 Title generation

```
title = subjectName + " - " + className
  Ví dụ: "Toán - Lớp 6A1"

Nếu không có subject: dùng className
Nếu không có class: dùng "Unknown Class"
```

---

## 8. Conflict Detection & Resolution

### 8.1 Conflict types

| Code | Severity | Condition | Action |
|------|----------|-----------|--------|
| `CLASS_TIME_CONFLICT` | `ERROR` | Cùng lớp, cùng thời gian | **Blocks creation** |
| `TEACHER_TIME_CONFLICT` | `ERROR` | Cùng teacher, cùng thời gian | **Blocks creation** (unless resolved) |
| `ROOM_TIME_CONFLICT` | `WARNING` | Cùng phòng, cùng thời gian | Blocks if `overrideConflicts=false`, warns if `true` |

### 8.2 Preview response with conflicts

```json
{
  "data": {
    "previewTeachingSchedule": {
      "valid": false,
      "previewEventCount": 8,
      "teacherScheduleCount": 24,
      "totalPeriodCount": 16,
      "rowResults": [...],
      "conflicts": [
        {
          "code": "TEACHER_TIME_CONFLICT",
          "severity": "ERROR",
          "clientRowId": "row-1",
          "existingEventId": "evt_existing_001",
          "message": "Giao vien da co lich trung gio"
        },
        {
          "code": "ROOM_TIME_CONFLICT",
          "severity": "WARNING",
          "clientRowId": "row-1",
          "existingEventId": "",
          "message": "Phong hoc da co lich su dung: Phòng 101"
        }
      ]
    }
  }
}
```

### 8.3 Resolve mode

**Flow detail:**

```
1. Preview → thấy TEACHER_TIME_CONFLICT
2. User chọn "Override — hủy lịch cũ của GV này"
3. Gọi createTeachingSchedule với resolveMode = "DELETE_CONFLICTING"
4. BE: cancel event cũ (status = CANCELLED) + tạo event mới
5. Response: conflicts[] chứa conflict đã resolve
```

**Response với DELETE_CONFLICTING:**

```json
{
  "data": {
    "createTeachingSchedule": {
      "batchId": "batch_abc123",
      "createdEventCount": 8,
      "conflicts": [
        {
          "code": "TEACHER_TIME_CONFLICT",
          "severity": "RESOLVED",
          "clientRowId": "row-1",
          "existingEventId": "evt_existing_001",
          "message": "Đã hủy lịch cũ và thay bằng lịch mới"
        }
      ]
    }
  }
}
```

| resolveMode | Behavior |
|-------------|----------|
| `REJECT` (default) | Có ERROR conflict → không tạo, trả về lỗi |
| `DELETE_CONFLICTING` | TEACHER conflict → cancel event cũ + tạo mới |

> **Security:** Chỉ cancel được event trong scope trường admin quản lý. Event ngoài scope → vẫn block.

---

## 9. Catalog, Filters & Lanes

### 9.1 Three related but distinct concepts

```
┌─────────────────────────────────────────────────────────────┐
│ catalog = TẤT CẢ options có sẵn (không phụ thuộc date range) │
│   → Dùng cho dropdown/combobox khi tạo lịch                   │
├─────────────────────────────────────────────────────────────┤
│ filterOptions = Chỉ options CÓ EVENT trong khoảng ngày       │
│   → Dùng cho filter bar (có count badge)                     │
├─────────────────────────────────────────────────────────────┤
│ laneSummaries = Group events theo chiều dọc                  │
│   → Dùng cho calendar lane view                              │
└─────────────────────────────────────────────────────────────┘
```

### 9.2 Catalog fields

| Field | Contains | Use case |
|-------|----------|----------|
| `schools` | `[FilterOption]` | School dropdown |
| `classes` | `[ClassOption]` | Class dropdown (có `schoolId` để group) |
| `teachers` | `[TeacherOption]` | Main teacher dropdown |
| `assistantTeachers` | `[TeacherOption]` | Assistant teacher dropdown |
| `subjects` | `[SubjectOption]` | Subject dropdown |
| `levels` | `[LevelOption]` | Level dropdown (có `subjectId` để cascade) |
| `rooms` | `[RoomOption]` | Room dropdown |

### 9.3 Lane summaries by viewMode

| viewMode | Lane grouping | Count is | colorKey |
|----------|---------------|----------|----------|
| `ALL` | Single lane "Tất cả" | Total events | null |
| `BY_SCHOOL` | Per school | Events/school | null |
| `BY_TEACHER` | Per teacher | Events/teacher | Teacher's colorKey |
| `BY_SUBJECT` | Per subject | Events/subject | null |

### 9.4 Color group

`colorGroup` trên mỗi event:

```json
{
  "key": "blue",
  "label": null,
  "kind": "subject"
}
```

- **`kind`**: `"school"`, `"class"`, `"subject"`, `"teacher"` — cho FE biết palette nào dùng
- **`key`**: string ổn định — FE map sang màu cố định
- Color keys pool: `["blue", "green", "orange", "purple", "red", "teal", "pink", "indigo"]`
- Key được sinh từ `subjectId.hashCode() % 8` (ổn định với cùng subjectId)
- **Không dùng mã hex** — chỉ dùng colorKey, FE tự map

### 9.5 Permissions

```json
{
  "canViewAggregate": true,   // Có thể xem LCMS aggregate
  "canCreate": true,          // Có thể tạo lịch
  "canUpdate": true,          // Có thể sửa lịch
  "canDelete": true,          // Có thể xóa lịch
  "canPublish": true          // Có thể publish batch
}
```

Dùng để show/hide các nút trên UI:

| Permission | Show when |
|------------|-----------|
| `canCreate` | "Tạo lịch" button |
| `canPublish` | "Publish" button |
| `canDelete` | Delete/cancel actions |

---

## 10. Permissions & Authorization

### 10.1 Required permissions by operation

| Operation | Permission |
|-----------|------------|
| View LCMS workspace | `lms.schedule.read` |
| View LMS teacher calendar | `lms.schedule.read_self` |
| View LMS student calendar | `lms.schedule.read_self` |
| Create/Update/Delete/Publish | `lms.schedule.manage` |

### 10.2 Role mapping

| Role | Permissions |
|------|-------------|
| `system.super_admin` | `*` (có quyền làm tất cả) |
| `lms.admin` | `lms.schedule.read` + `lms.schedule.manage` |
| `lms.instructor` | `lms.schedule.read_self` |
| `lms.learner` | `lms.schedule.read_self` |

### 10.3 Scope enforcement

```
LCMS: schoolIds phải nằm trong managedEducationUnitIds của token
LMS Teacher: luôn self-scoped (không thể xem teacher khác)
LMS Student: luôn self-scoped (chỉ thấy class của mình)

Mutation (create/update/delete):
  schoolId phải nằm trong managedEducationUnitIds
```

### 10.4 Error messages

| Error | HTTP Status (GraphQL errors[]) | Message |
|-------|--------------------------------|---------|
| No token | 401 | `AUTHENTICATION_REQUIRED` |
| Missing permission | 200 (error in response) | Contains "permission" |
| Wrong tenant | 200 (error in response) | Contains "tenant" |
| Wrong portal | 200 (error in response) | Contains "portal" |
| Out of scope | 200 (error in response) | Contains "education unit" |
| Not found | 200 (error in response) | "not found" |

---

## 11. REST Endpoints

REST endpoints exist as convenience, mainly for admin operations and file upload.

### 11.1 Available endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/calendar/workspace` | LCMS workspace (query params same as GraphQL) |
| `GET` | `/api/v1/calendar/my-calendar` | Teacher calendar |
| `GET` | `/api/v1/calendar/events/{eventId}` | Event detail |
| `GET` | `/api/v1/calendar/batches` | Batch list |
| `GET` | `/api/v1/calendar/batches/{batchId}` | Batch detail |
| `GET` | `/api/v1/calendar/rooms` | Room list |
| `POST` | `/api/v1/calendar` | Create schedule |
| `POST` | `/api/v1/calendar/preview` | Preview schedule |
| `PUT` | `/api/v1/calendar/events/{eventId}` | Update event |
| `DELETE` | `/api/v1/calendar/events/{eventId}` | Delete event |
| `POST` | `/api/v1/calendar/teachers/delete-bulk` | Delete teacher schedule |
| `POST` | `/api/v1/calendar/batches/{batchId}/publish` | Publish batch |
| `POST` | `/api/v1/calendar/batches/{batchId}/cancel` | Cancel batch |
| `POST` | `/api/v1/calendar/rooms` | Create room |
| `POST` | `/api/v1/calendar/import-excel` | Import Excel (stub) |

### 11.2 REST response envelope

```json
{
  "meta": {
    "code": 200,
    "message": "Calendar workspace loaded",
    "requestId": "req_abc123",
    "path": "/api/v1/calendar/workspace"
  },
  "data": { ... }  // Same shape as GraphQL TeachingCalendarWorkspace
}
```

> **Recommendation:** Dùng GraphQL cho tất cả calendar operations. REST chỉ dùng cho Excel import (file upload).

---

## 12. Error Handling

### 12.1 Validation errors (row-level)

```json
{
  "data": {
    "createTeachingSchedule": {
      "batchId": null,
      "createdEventCount": 0,
      "rowResults": [
        {
          "clientRowId": "row-1",
          "valid": false,
          "eventCount": 0,
          "errors": [
            { "code": "CLASS_NOT_IN_SCHOOL", "field": "classIds", "message": "Lớp 6A3 không thuộc trường đã chọn" },
            { "code": "INVALID_TIME_RANGE", "field": "startTime", "message": "Giờ bắt đầu phải trước giờ kết thúc" }
          ],
          "warnings": []
        }
      ],
      "conflicts": []
    }
  }
}
```

### 12.2 All validation codes

| Code | Severity | Description |
|------|----------|-------------|
| `CLASS_REQUIRED` | error | classIds không được rỗng |
| `CLASS_NOT_FOUND` | error | ClassId không tồn tại |
| `CLASS_NOT_IN_SCHOOL` | error | Class không thuộc school đã chọn |
| `MAIN_TEACHER_REQUIRED` | error | mainTeacherIds không được rỗng |
| `TEACHER_NOT_FOUND` | error | TeacherId không tồn tại |
| `SUBJECT_NOT_FOUND` | error | SubjectId không tồn tại |
| `LEVEL_NOT_FOUND` | error | LevelId không tồn tại |
| `LEVEL_NOT_IN_SUBJECT` | error | Level không thuộc subject đã chọn |
| `DUPLICATE_TEACHER_ROLE` | error | Teacher vừa là MAIN vừa là ASSISTANT |
| `INVALID_PERIOD_COUNT` | error | periodCount không trong [1,10] |
| `INVALID_PERIOD_START` | error | periodStart < 1 |
| `INVALID_TIME_RANGE` | error | startTime không trước endTime |
| `INVALID_TIME_FORMAT` | error | Không parse được HH:mm |
| `INVALID_WEEKDAY` | error | weekday không trong [1,7] |

### 12.3 Application errors (blocking)

| Scenario | Error |
|----------|-------|
| Room name blank | `IllegalArgumentException` |
| Batch not found | `IllegalArgumentException` |
| Wrong status for publish | `IllegalStateException` |
| Duplicate idempotency key, different payload | `ConflictException` |
| Advisory lock failed | `ConflictException` |

---

## 13. Appendix: Full Schema

### 13.1 GraphQL types reference

```
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

type TeachingCalendarFilterOptions { schools: [TeachingCalendarFilterOption!]! classes: [TeachingCalendarFilterOption!]! teachers: [TeachingCalendarFilterOption!]! subjects: [TeachingCalendarFilterOption!]! levels: [TeachingCalendarFilterOption!]! }
type TeachingCalendarFilterOption { id: String! label: String! count: Int! colorKey: String }
type TeachingCalendarLaneSummary { id: String! label: String! count: Int! colorKey: String! }
type TeachingCalendarColorGroup { key: String! label: String! kind: String! }
type TeachingCalendarPermissions { canCreate: Boolean! canUpdate: Boolean! canDelete: Boolean! canPublish: Boolean! canViewAggregate: Boolean! }

# Catalog
type TeachingScheduleCatalog { schools: [TeachingCalendarFilterOption!]! classes: [TeachingCalendarClassOption!]! teachers: [TeachingCalendarTeacherOption!]! assistantTeachers: [TeachingCalendarTeacherOption!]! subjects: [TeachingCalendarSubjectOption!]! levels: [TeachingCalendarLevelOption!]! rooms: [TeachingCalendarRoomOption!]! }
type TeachingCalendarClassOption { id: String! schoolId: String! label: String! grade: String academicYear: String }
type TeachingCalendarTeacherOption { id: String! label: String! colorKey: String! }
type TeachingCalendarSubjectOption { id: String! label: String! }
type TeachingCalendarLevelOption { id: String! subjectId: String! label: String! sortOrder: Int }
type TeachingCalendarRoomOption { id: String label: String! schoolId: String }
type TeachingScheduleRoomOption { id: String! label: String! schoolId: String! }

# Mutation payloads
type TeachingSchedulePreviewPayload { valid: Boolean! previewEventCount: Int! teacherScheduleCount: Int! totalPeriodCount: Int! rowResults: [TeachingScheduleRowResult!]! conflicts: [TeachingScheduleConflict!]! }
type TeachingScheduleBulkPayload { batchId: String! createdEventCount: Int! teacherScheduleCount: Int! totalPeriodCount: Int! rowResults: [TeachingScheduleRowResult!]! conflicts: [TeachingScheduleConflict!]! }
type TeachingScheduleEventPayload { event: TeachingCalendarEvent! }
type TeachingScheduleDeletePayload { eventId: String! deleted: Boolean! affectedEventCount: Int! }
type TeachingScheduleBatchPayload { batchId: String! status: TeachingScheduleStatus! affectedEventCount: Int! }
type TeachingScheduleRowResult { clientRowId: String! valid: Boolean! eventCount: Int! teacherScheduleCount: Int! errors: [TeachingScheduleRowMessage!]! warnings: [TeachingScheduleRowMessage!]! }
type TeachingScheduleRowMessage { code: String! field: String message: String! }
type TeachingScheduleConflict { code: String! severity: String! clientRowId: String existingEventId: String message: String! }

# Enums
enum TeachingScheduleTeacherRole { MAIN ASSISTANT }
enum TeachingCalendarViewMode { ALL BY_SCHOOL BY_TEACHER BY_SUBJECT }
enum TeachingScheduleStatus { DRAFT PUBLISHED CANCELLED }
enum TeachingScheduleResolveMode { REJECT DELETE_CONFLICTING }
```

### 13.2 Input types reference

```graphql
input TeachingCalendarWorkspaceInput { tenantId: String, from: String!, to: String!, viewMode: TeachingCalendarViewMode, schoolIds: [String!], teacherIds: [String!], classIds: [String!], subjectIds: [String!], levelIds: [String!], statuses: [TeachingScheduleStatus!] }
input MyTeachingCalendarInput { tenantId: String, from: String!, to: String!, viewMode: TeachingCalendarViewMode, schoolIds: [String!], classIds: [String!], subjectIds: [String!], levelIds: [String!], statuses: [TeachingScheduleStatus!] }
input MyStudentCalendarInput { tenantId: String, from: String!, to: String!, viewMode: TeachingCalendarViewMode, subjectIds: [String!], levelIds: [String!], statuses: [TeachingScheduleStatus!] }
input TeachingScheduleBulkInput { tenantId: String, idempotencyKey: String, schoolId: String!, academicYear: String, applyFrom: String!, repeatWeeks: Int!, note: String, overrideConflicts: Boolean, resolveMode: TeachingScheduleResolveMode, rows: [TeachingScheduleBulkRowInput!]! }
input TeachingScheduleBulkRowInput { clientRowId: String!, classIds: [String!]!, weekday: Int!, session: String, periodStart: Int!, periodCount: Int!, startTime: String!, endTime: String!, subjectId: String!, levelId: String, roomId: String, roomName: String, mainTeacherIds: [String!]!, assistantTeacherIds: [String!], note: String }
input TeachingScheduleEventUpdateInput { tenantId: String, eventId: String!, roomId: String, roomName: String, note: String }
input TeachingScheduleEventDeleteInput { tenantId: String, eventId: String! }
input DeleteTeacherScheduleInput { tenantId: String, teacherUserId: String!, schoolIds: [String!], dateFrom: String, dateTo: String }
input CreateRoomInput { tenantId: String, schoolId: String!, roomName: String! }
input TeachingScheduleExcelImportInput { tenantId: String, schoolId: String!, academicYear: String, applyFrom: String!, repeatWeeks: Int!, note: String, overrideConflicts: Boolean, resolveMode: TeachingScheduleResolveMode }
```

---

## Quick Reference Card

### FE cần làm gì?

1. **Gọi GraphQL workspace** khi vào màn calendar — `lcms.teachingCalendarWorkspace` hoặc `lms.myTeachingCalendar`/`lms.myStudentCalendar`
2. **Show events** trên timeline — dùng `startAt`/`endAt`, `date`, `startTime`/`endTime`
3. **Show filter bar** — dùng `filterOptions` (counts)
4. **Show dropdowns** — dùng `catalog` (full options)
5. **Show lanes** — dùng `laneSummaries` (group by school/teacher/subject)
6. **Render màu** — dùng `colorGroup.key` map sang palette, `colorGroup.kind` để biết palette nào
7. **Tạo lịch** — `createTeachingSchedule(input)` with `clientRowId` pattern
8. **Conflict** — check `conflicts[]` trong response, show cho user
9. **Publish/Cancel** — dùng `publishTeachingScheduleBatch(batchId)` / `cancelTeachingScheduleBatch(batchId)`
10. **Phòng** — dùng `createRoom(input)` ad-hoc khi tạo lịch

### Không cần làm

- ❌ Không tự tính toán expand rows (BE làm)
- ❌ Không tự query reference data cho dropdown (catalog có sẵn trong workspace)
- ❌ Không cần tính color (BE trả colorGroup)
- ❌ Không cần phân quyền ở FE (BE enforce, FE chỉ dùng permissions để show/hide UI)
