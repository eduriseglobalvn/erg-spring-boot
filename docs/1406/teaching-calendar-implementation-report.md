# Bao cao trien khai tinh nang Lich giang day (Teaching Calendar)

**Ngay:** 2026-06-14
**Du an:** erg-spring
**Branch:** codex/lms-graphql-security-audit-plan
**Nguoi thuc hien:** Senior Engineer

---

## 1. Tong quan

Tinh nang Lich giang day cho phep quan tri vien LCMS tao lich bang (Excel-like bulk grid) cho nhieu lop, nhieu giao vien, lap nhieu tuan. Giao vien LMS co the xem lich ca nhan. He thong phat hien xung dot thoi gian (lop, giao vien, phong hoc) va ho tro idempotency, advisory lock, notification.

### Thong ke

| Thong so | Gia tri |
|----------|---------|
| Tong so file tao moi | 20 |
| Tong so file sua | 1 (ErgPolicyService) + 1 (schema.graphqls) |
| Database migration | 2 (V17 + V18) |
| Entity (JPA) | 4 |
| Repository | 4 |
| Service | 5 |
| GraphQL Controller | 3 |
| DTO (request/response) | 2 |
| Migration SQL | 2 |

---

## 2. Danh sach file theo nhom

### 2.1 Database Migrations

| STT | File | Mo ta |
|-----|------|-------|
| 1 | `src/main/resources/db/migration/postgres/V17__teaching_schedule_schema.sql` | Tao 4 bang: `teaching_schedule_batches`, `teaching_schedule_events`, `teaching_schedule_event_teachers`, `teaching_schedule_rooms`. Them index, foreign key (NOT VALID), seed permission va role_permission. |
| 2 | `src/main/resources/db/migration/postgres/V18__validate_teaching_schedule_foreign_keys.sql` | VALIDATE tat ca foreign key tu V17 (chuyen tu NOT VALID sang VALID). |

### 2.2 Entities (JPA Model)

| STT | File | Mo ta |
|-----|------|-------|
| 3 | `src/main/java/com/erg/ergspringboot/model/lms/schedule/TeachingScheduleBatch.java` | Entity cho bang `teaching_schedule_batches`. Luu mot lan tao lich: tenant, school, applyFrom, repeatWeeks, status (DRAFT/PUBLISHED/CANCELLED), idempotencyKey, payloadHash, nguoi tao/publish. |
| 4 | `src/main/java/com/erg/ergspringboot/model/lms/schedule/TeachingScheduleEvent.java` | Entity cho bang `teaching_schedule_events`. Mot buoi hoc cu the: class, subject, level, room, thoi gian (startAt/endAt), tiet (periodStart/periodCount), status, note. |
| 5 | `src/main/java/com/erg/ergspringboot/model/lms/schedule/TeachingScheduleEventTeacher.java` | Entity cho bang `teaching_schedule_event_teachers`. Join table event - giao vien: role (MAIN/ASSISTANT), displayNameSnapshot, colorKey. |
| 6 | `src/main/java/com/erg/ergspringboot/model/lms/schedule/TeachingScheduleRoom.java` | Entity cho bang `teaching_schedule_rooms`. Phong hoc: tenant, school, name, status (ACTIVE/INACTIVE), createdBy. |

### 2.3 Repositories

| STT | File | Mo ta |
|-----|------|-------|
| 7 | `src/main/java/com/erg/ergspringboot/repository/lms/schedule/TeachingScheduleBatchRepository.java` | JpaRepository. Query chinh: `findByTenantIdAndIdempotencyKey()` cho idempotency check. |
| 8 | `src/main/java/com/erg/ergspringboot/repository/lms/schedule/TeachingScheduleEventRepository.java` | JpaRepository. Cac query: `findByTenantIdAndSchoolIdInAndStartAtLessThan...` (LCMS workspace), `findTeacherCalendarEvents` (LMS ca nhan), `findTeacherTimeConflicts` (xung dot giao vien), `countByTenantIdAndAcademicClassId...` (xung dot lop), `countByTenantIdAndSchoolIdAndRoomName...` (xung dot phong), `updateStatusBatch` (bulk status update). |
| 9 | `src/main/java/com/erg/ergspringboot/repository/lms/schedule/TeachingScheduleEventTeacherRepository.java` | JpaRepository. Cac query: `findByTenantIdAndTeacherUserId()`, `findByEventIdIn()`, `findEventIdsByTeacher()` (dung cho deleteTeacherSchedule). |
| 10 | `src/main/java/com/erg/ergspringboot/repository/lms/schedule/TeachingScheduleRoomRepository.java` | JpaRepository. Cac query: `findBySchoolIdAndNameIgnoreCase()` (idempotent create room), `findByTenantIdAndSchoolIdAndStatus()` (catalog). |

### 2.4 DTOs (Request/Response)

| STT | File | Mo ta |
|-----|------|-------|
| 11 | `src/main/java/com/erg/ergspringboot/common/request/lms/TeachingScheduleRequests.java` | Input records: `TeachingCalendarWorkspaceInput`, `MyTeachingCalendarInput`, `TeachingScheduleBulkInput`, `TeachingScheduleBulkRowInput`, `TeachingScheduleEventUpdateInput`, `TeachingScheduleEventDeleteInput`, `DeleteTeacherScheduleInput`, `CreateRoomInput`. |
| 12 | `src/main/java/com/erg/ergspringboot/common/response/lms/TeachingScheduleResponses.java` | Output records (~20 types): workspace, event, filter options, lane summary, catalog, permissions, preview payload, bulk payload, batch payload, delete payload, conflict response, row result, room option. |

### 2.5 Services

| STT | File | Mo ta |
|-----|------|-------|
| 13 | `src/main/java/com/erg/ergspringboot/service/lms/schedule/TeachingScheduleExpansionService.java` | Mo rong (expand) input row thanh event instances. Tinh toan ngay tu `applyFrom`, `weekday`, `repeatWeeks`. Sinh title tu subjectName + className. Tao teacher assignments. |
| 14 | `src/main/java/com/erg/ergspringboot/service/lms/schedule/TeachingScheduleValidationService.java` | Validate tung dong input: class ton tai, teacher ton tai, subject ton tai, level thuoc subject, period count 1-10, startTime < endTime, weekday 1-7, khong trung teacher role. Tra ve errors/warnings theo dong (khong throw). |
| 15 | `src/main/java/com/erg/ergspringboot/service/lms/schedule/TeachingScheduleConflictService.java` | Phat hien xung dot 3 chieu: class overlap (ERROR), teacher overlap (ERROR, gom ca DB va trong candidate list), room overlap (WARNING). Ho tro `resolveConflictsByDeletion()` cho resolveMode=DELETE_CONFLICTING. |
| 16 | `src/main/java/com/erg/ergspringboot/service/lms/schedule/TeachingScheduleReadService.java` | Query workspace: `lcmsTeachingCalendarWorkspace()` (LCMS aggregate), `teacherTeachingCalendarWorkspace()` (LMS ca nhan). Xay dung filter options, lane summaries, catalog, permissions. Batch-load reference data tranh N+1. |
| 17 | `src/main/java/com/erg/ergspringboot/service/lms/schedule/TeachingScheduleCommandService.java` | Command handler: `preview()`, `create()` (kem idempotency + advisory lock + conflict resolution), `publishBatch()`, `cancelBatch()`, `updateEvent()`, `deleteEvent()`, `deleteTeacherSchedule()`, `createRoom()`. Tich hop notification khi publish. |

### 2.6 Controllers (GraphQL)

| STT | File | Mo ta |
|-----|------|-------|
| 18 | `src/main/java/com/erg/ergspringboot/controller/lcms/graphql/LcmsTeachingScheduleGraphQlController.java` | `@SchemaMapping(typeName = "LcmsQuery", field = "teachingCalendarWorkspace")`. Yeu cau permission `lms.schedule.read` tro len. Scope schoolIds tu input hoac managedEducationUnitIds. |
| 19 | `src/main/java/com/erg/ergspringboot/controller/lms/graphql/LmsTeachingScheduleGraphQlController.java` | `@SchemaMapping(typeName = "LmsQuery", field = "myTeachingCalendar")`. Yeu cau permission `lms.schedule.read_self` tro len. Tu dong lay `principal.userId()` lam teacherUserId. |
| 20 | `src/main/java/com/erg/ergspringboot/controller/lms/graphql/TeachingScheduleMutationGraphQlController.java` | `@MutationMapping` cho 7 mutation: `previewTeachingSchedule`, `createTeachingSchedule`, `updateTeachingScheduleEvent`, `deleteTeachingScheduleEvent`, `deleteTeacherSchedule`, `publishTeachingScheduleBatch`, `cancelTeachingScheduleBatch`, `createRoom`. |

### 2.7 Config (sua file co san)

| STT | File | Mo ta |
|-----|------|-------|
| 21 | `src/main/java/com/erg/ergspringboot/platform/security/ErgPolicyService.java` | Them `lms.schedule.read`, `lms.schedule.manage` vao role `lms.admin`. Them `lms.schedule.read_self` vao `lms.teacher` va `lms.student`. |
| 22 | `src/main/resources/graphql/schema.graphqls` | Them `type Mutation` (lan dau tien trong codebase). Them input types, output types, enums cho teaching calendar vao schema. Them `teachingCalendarWorkspace` vao `LcmsQuery`, `myTeachingCalendar` vao `LmsQuery`. |

---

## 3. Thu tu Migration (bat buoc)

```
V17__teaching_schedule_schema.sql   -->  V18__validate_teaching_schedule_foreign_keys.sql
```

- **V17**: Tao bang, index, foreign key (NOT VALID), seed du lieu permission.
- **V18**: VALIDATE cac foreign key. Bat buoc chay sau V17 vi VALIDATE chi co tac dung neu constraint da ton tai.

**Khong duoc chay V18 truoc V17.**

---

## 4. So do phu thuoc giua cac thanh phan

```
schema.graphqls (GraphQL schema)
       |
       v
TeachingScheduleRequests (input DTOs)
       |
       v
TeachingScheduleMutationGraphQlController  +  LcmsTeachingScheduleGraphQlController  +  LmsTeachingScheduleGraphQlController
       |                                              |                                          |
       v                                              v                                          v
TeachingScheduleCommandService                  TeachingScheduleReadService
       |                                              |
       |-- TeachingScheduleValidationService           |-- TeachingScheduleExpansionService.ReferenceData
       |-- TeachingScheduleExpansionService            |-- (truc tiep goi repository)
       |-- TeachingScheduleConflictService             |
       v                                              v
TeachingScheduleBatchRepository     TeachingScheduleEventRepository     TeachingScheduleEventTeacherRepository     TeachingScheduleRoomRepository
       |                                              |
       v                                              v
TeachingScheduleBatch (entity)              TeachingScheduleEvent (entity)     TeachingScheduleEventTeacher (entity)     TeachingScheduleRoom (entity)
       |                                              |
       v                                              v
V17__teaching_schedule_schema.sql  -->  V18__validate_teaching_schedule_foreign_keys.sql
       |
       v
ErgPolicyService (role-permission mapping)
```

### Phu thuoc runtime:

| Component | Phu thuoc vao |
|-----------|----------------|
| TeachingScheduleMutationGraphQlController | TeachingScheduleCommandService, GraphQlAccessSupport |
| LcmsTeachingScheduleGraphQlController | TeachingScheduleReadService, GraphQlAccessSupport |
| LmsTeachingScheduleGraphQlController | TeachingScheduleReadService, GraphQlAccessSupport |
| TeachingScheduleCommandService | TeachingScheduleBatchRepository, TeachingScheduleEventRepository, TeachingScheduleEventTeacherRepository, TeachingScheduleRoomRepository, TeachingScheduleExpansionService, TeachingScheduleValidationService, TeachingScheduleConflictService, NotificationCommandService, GraphQlAccessSupport, EntityManager, ClassroomRepository, SubjectRepository, LevelRepository, UserRepository, EducationUnitRepository |
| TeachingScheduleReadService | TeachingScheduleEventRepository, TeachingScheduleEventTeacherRepository, TeachingScheduleRoomRepository, ClassroomRepository, SubjectRepository, LevelRepository, EducationUnitRepository, UserRepository |
| TeachingScheduleExpansionService | Khong co phu thuoc repository (doc lap, xu ly in-memory) |
| TeachingScheduleValidationService | Khong co phu thuoc repository (doc lap, xu ly in-memory) |
| TeachingScheduleConflictService | TeachingScheduleEventRepository |

### Phu thuoc database:

| Bang | Phu thuoc vao bang khac |
|------|--------------------------|
| `teaching_schedule_batches` | `education_units(id)`, `users(id)` |
| `teaching_schedule_events` | `teaching_schedule_batches(id)`, `education_units(id)`, `academic_classes(id)`, `lms_subjects(id)`, `lms_levels(id)`, `users(id)` |
| `teaching_schedule_event_teachers` | `teaching_schedule_events(id)`, `users(id)` |
| `teaching_schedule_rooms` | `education_units(id)`, `users(id)` |

### Phu thuoc permission:

| Permission | Role duoc gan |
|------------|---------------|
| `lms.schedule.read_self` | `lms.teacher`, `lms.student` (qua ErgPolicyService), `lms.instructor`, `lms.learner` (qua seed SQL) |
| `lms.schedule.read` | `lms.admin` (qua ErgPolicyService + seed SQL) |
| `lms.schedule.manage` | `lms.admin` (qua ErgPolicyService + seed SQL) |

---

## 5. Verification Checklist

### 5.1 Database

- [ ] Kiem tra V17 da tao du 4 bang: `teaching_schedule_batches`, `teaching_schedule_events`, `teaching_schedule_event_teachers`, `teaching_schedule_rooms`
- [ ] Kiem tra index da duoc tao (17 index tong cong)
- [ ] Kiem tra unique constraint: `uq_tsr_school_name`, `uq_tset_event_teacher_role`, `idx_tsb_tenant_idempotency_key`
- [ ] Kiem tra check constraint: status, weekday, period_start, period_count, repeat_weeks, time_range
- [ ] Kiem tra foreign key (V17 NOT VALID -> V18 VALID)
- [ ] Kiem tra permission seed: `permission_groups` co `pg_lms_schedule`, `permissions` co 3 record moi
- [ ] Kiem tra role_permissions: `lms.admin` co 2 permission, `lms.instructor` co 1, `lms.learner` co 1
- [ ] Kiem tra V18 da VALIDATE tat ca FK thanh cong

### 5.2 API - LCMS Workspace (Query)

- [ ] `lcms.teachingCalendarWorkspace` tra ve workspace day du
- [ ] Loc theo schoolIds (neu input co) -> chi tra event cua cac truong do
- [ ] Khong co input schoolIds -> dung managedEducationUnitIds tu principal
- [ ] Filter teacherIds loc chinh xac
- [ ] Filter classIds, subjectIds, levelIds, statuses loc chinh xac
- [ ] Catalog tra ve danh sach day du (schools, classes, teachers, subjects, levels, rooms)
- [ ] FilterOptions tra ve count chinh xac
- [ ] LaneSummaries hoat dong dung voi moi viewMode (ALL/BY_SCHOOL/BY_TEACHER/BY_SUBJECT)
- [ ] Permissions phan anh dung quyen cua principal

### 5.3 API - LMS My Calendar (Query)

- [ ] `lms.myTeachingCalendar` tra ve workspace
- [ ] Tu dong lay userId tu principal (khong cho override)
- [ ] Chi tra event co teacher assignment cho userId do
- [ ] Filter schoolIds, classIds, subjectIds, levelIds hoat dong
- [ ] Permission `lms.schedule.read_self` duoc enforce

### 5.4 API - Mutations

- [ ] `previewTeachingSchedule`: tra ve preview payload, khong persist
- [ ] `createTeachingSchedule`: tao batch + events + assignments trong 1 transaction
- [ ] Idempotency: cung idempotencyKey + cung payload -> tra ve batch cu
- [ ] Idempotency: cung key + khac payload -> throw ConflictException
- [ ] Advisory lock: 2 request cung luc cho cung truong -> 1 thanh cong, 1 bi tu choi
- [ ] `publishTeachingScheduleBatch`: update batch + events status, gui notification
- [ ] `cancelTeachingScheduleBatch`: update batch + events -> CANCELLED
- [ ] `updateTeachingScheduleEvent`: update roomId, roomName, note
- [ ] `deleteTeachingScheduleEvent`: soft delete (CANCELLED)
- [ ] `deleteTeacherSchedule`: xoa toan bo lich giao vien trong khoang thoi gian + truong
- [ ] `createRoom`: tao phong moi hoac tra ve phong da co (idempotent)

### 5.5 Validation & Conflict

- [ ] Row validation: classIds empty -> CLASS_REQUIRED
- [ ] Row validation: class khong thuoc school -> CLASS_NOT_IN_SCHOOL
- [ ] Row validation: mainTeacherIds empty -> MAIN_TEACHER_REQUIRED
- [ ] Row validation: teacher khong ton tai -> TEACHER_NOT_FOUND
- [ ] Row validation: subject khong ton tai -> SUBJECT_NOT_FOUND
- [ ] Row validation: level khong thuoc subject -> LEVEL_NOT_IN_SUBJECT
- [ ] Row validation: assistant trung main -> DUPLICATE_TEACHER_ROLE
- [ ] Row validation: periodCount < 1 hoac > 10 -> INVALID_PERIOD_COUNT
- [ ] Row validation: startTime >= endTime -> INVALID_TIME_RANGE
- [ ] Row validation: weekday < 1 hoac > 7 -> INVALID_WEEKDAY
- [ ] Conflict: class overlap -> CLASS_TIME_CONFLICT (ERROR)
- [ ] Conflict: teacher overlap -> TEACHER_TIME_CONFLICT (ERROR)
- [ ] Conflict: room overlap -> ROOM_TIME_CONFLICT (WARNING)
- [ ] Conflict: trong candidate events -> phat hien cross-row conflict
- [ ] resolveMode=DELETE_CONFLICTING: cancel event cu, tao event moi
- [ ] resolveMode=REJECT (mac dinh): khong persist, tra ve conflict

### 5.6 Notification

- [ ] Khi publish batch -> gui notification cho tat ca teacher co event trong batch
- [ ] Notification co type = SCHEDULE_PUBLISHED, source = teaching_schedule
- [ ] Khong spam notification khi bi loi (try-catch)

### 5.7 Security

- [ ] `teachingCalendarWorkspace` yeu cau `lms.schedule.read` hoac cao hon
- [ ] `myTeachingCalendar` yeu cau `lms.schedule.read_self` hoac cao hon
- [ ] Tat ca mutation yeu cau `lms.schedule.manage` hoac `lms.manage`
- [ ] `assertEducationUnitScope` duoc goi cho mutation co schoolId
- [ ] `verifiedTenantId` duoc goi o tat ca endpoint
- [ ] `ErgPolicyService` da cap nhat role-permission mapping

### 5.8 Performance

- [ ] Batch load reference data (tranh N+1)
- [ ] Advisory lock prevent double creation
- [ ] Index day du cho range query (tenant, school, start_at, end_at)
- [ ] `updateStatusBatch` dung JPQL bulk update (khong load tung entity)

---

## 6. GraphQL Schema Summary

### Queries

```
LcmsQuery {
    teachingCalendarWorkspace(input: TeachingCalendarWorkspaceInput!): TeachingCalendarWorkspace!
}

LmsQuery {
    myTeachingCalendar(input: MyTeachingCalendarInput!): TeachingCalendarWorkspace!
}
```

### Mutations (lan dau tien trong codebase)

```
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

### Enums

- `TeachingScheduleTeacherRole`: MAIN, ASSISTANT
- `TeachingCalendarViewMode`: ALL, BY_SCHOOL, BY_TEACHER, BY_SUBJECT
- `TeachingScheduleStatus`: DRAFT, PUBLISHED, CANCELLED
- `TeachingScheduleResolveMode`: REJECT, DELETE_CONFLICTING

---

## 7. Luu y quan trong

1. **Mutation lan dau**: Day la lan dau tien codebase co `type Mutation` trong GraphQL. Tat ca mutation dung `@MutationMapping`.
2. **Transaction**: `create()` su dung `postgresTransactionManager` + advisory lock `pg_try_advisory_xact_lock()`.
3. **Soft delete**: Khong xoa vat ly. Tat ca xoa la chuyen status -> `CANCELLED`.
4. **Display name snapshot**: Luu ten giao vien tai thoi diem tao lich tranh JOIN khong can thiet.
5. **Color key**: BE tra color key (khong phai hex), FE map sang palette.
6. **Teacher personal calendar**: Query join `teaching_schedule_event_teachers` -> chi tra event co teacher assignment. Khong cho override teacherUserId.
7. **Idempotency**: `create()` co idempotency check. `createRoom()` idempotent nho unique constraint.
