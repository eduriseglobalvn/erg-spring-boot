# Kế hoạch Backend API — Quản lý trường & Giám sát đào tạo

**Dự án:** `erg-spring`  
**Ngày:** 13/07/2026  
**Phạm vi:** LCMS phòng đào tạo, LMS giáo viên, dữ liệu trường/khối/lớp/người học, sổ đầu bài, đánh giá, điểm thực hành, kiểm tra và câu hỏi tạm.  
**Nguyên tắc transport:** REST cho command/create/update/delete/import/export; GraphQL cho query/view/workspace/dashboard/drill-down.

---

## 1. Mục tiêu

Backend phải hỗ trợ một luồng nghiệp vụ thống nhất:

1. Tạo và cấu hình trường theo loại hình.
2. Tự khởi tạo khối mặc định theo loại trường.
3. Tạo/nhập lớp và người học theo Excel, Google Sheets hoặc nhập tay hàng loạt.
4. Duyệt người học theo trường → khối → lớp → hồ sơ.
5. Đăng ký môn cho trường, lớp và từng người học.
6. Giáo viên báo giảng qua sổ đầu bài; phòng đào tạo xem, phản hồi và đánh giá.
7. Giáo viên tự đánh giá theo tuần; phòng đào tạo tổng hợp trên dashboard.
8. Ghi nhận điểm thực hành theo chủ đề và kết quả kiểm tra.
9. Phòng đào tạo tạo sẵn đề; giáo viên có thể tạo đề tạm khi được cấp quyền.
10. Giáo viên chỉ được bổ sung câu hỏi qua file import đã kiểm tra.
11. Câu hỏi tạm hết thời hạn được gỡ khỏi authoring bank sau khi toàn bộ bài làm đã khóa, nhưng kết quả và snapshot phục vụ chấm/đối soát phải được giữ.
12. Dashboard trả về biểu đồ sóng, phân loại điểm, mức độ báo giảng, sức khỏe lớp và hỗ trợ drill-down.

Không tạo microservice mới trong giai đoạn này. Tiếp tục triển khai trong monolith Spring Boot hiện tại theo module/package rõ ràng và giữ PostgreSQL/Flyway.

---

## 2. Hiện trạng `erg-spring` đã kiểm tra

### 2.1. Thành phần có thể tái sử dụng

| Năng lực | Hiện trạng | Hướng tận dụng |
|---|---|---|
| Đơn vị/trường | `education_units`, `EducationUnit`, `EducationUnitRepository` | Là nguồn định danh duy nhất của trường. Không tạo bảng `schools` cạnh tranh. |
| Lớp | `academic_classes`, `Classroom` | Mở rộng academic year/version/source; không tạo bảng lớp mới. |
| Người học | `lms_students`, `Student` | Giữ là hồ sơ người học; bổ sung lịch sử xếp lớp và ghi danh môn bằng bảng liên kết. |
| Môn học | `lms_subjects` và taxonomy canonical `curriculum_subjects` | Dùng curriculum làm danh mục canonical; school offering liên kết đến subject canonical. |
| Bài tập/kiểm tra | `lms_assignments`, `assignment_resources`, `assignment_recipients` | Dùng làm cửa sổ giao đề cho lớp/nhóm/cá nhân. |
| Quiz/câu hỏi | `lms_questions`, `lms_quizzes`, `quiz_versions`, `quiz_version_questions`, `quiz_packages` | Tận dụng toàn bộ authoring → publish → immutable version/package. |
| Kết quả | `lms_attempts`, `attempt_answers`, `student_progress` | Là nguồn điểm kiểm tra và tiến độ. |
| Lịch dạy | `teaching_schedule_batches`, calendar events/teacher assignments | Là nguồn lịch dự kiến để đối chiếu giáo viên có phải báo giảng hay không. |
| GraphQL | `/api/v1/graphql`, `LcmsQuery`, resolver/read-model/projection pattern | Mở rộng LCMS workspace/dashboard bằng projection, không đọc entity rộng. |
| Bảo mật | `GraphQlAccessSupport`, `ErgPrincipal`, `UserAccessContext`, tenant GUC/RLS | Dùng tenant + education unit + class scope cho cả REST và GraphQL. |
| Import Excel | `CalendarRestController` + `CalendarExcelImportService` | Tái sử dụng pattern multipart → parse → preview → confirm; không dùng GraphQL upload. |
| API envelope | `ApiResponse<T>`, `PageResponse<T>`, typed exceptions | Áp dụng cho toàn bộ REST mới. |
| Hiệu năng | Caffeine, Micrometer, tracing, GraphQL depth/complexity/deadline | Tái sử dụng và bổ sung metric theo operation. |

### 2.2. Khoảng trống hiện tại

- `EducationUnitController` mới chỉ có GET list, chưa có command CRUD trường.
- `EducationUnitService.listEducationUnits()` chưa nhận tenant/scope và cache bằng key `'all'`; không được tái sử dụng nguyên trạng cho LCMS đa tenant.
- `education_units` chưa có `tenant_id` và thông tin vận hành trường chi tiết.
- Chưa có academic year, khối theo năm học, lịch sử xếp lớp người học.
- Chưa có school subject offering và student subject enrollment chuẩn hóa.
- Chưa có sổ đầu bài, báo giảng, attendance theo tiết, đánh giá tuần, đánh giá của phòng đào tạo.
- Chưa có điểm thực hành theo chủ đề ngoài quiz attempt.
- Chưa có permission request/grant riêng cho giáo viên bổ sung câu hỏi.
- Chưa có vòng đời câu hỏi tạm, expiry/purge job và audit tương ứng.
- GraphQL LCMS hiện có question bank, access management, calendar, quiz/report; chưa có school operations và training dashboard.
- Một số command calendar vẫn tồn tại dưới GraphQL Mutation. Không mở rộng pattern này cho domain mới; command mới chỉ đi REST.

---

## 3. Quyết định kiến trúc

### 3.1. Phân chia REST và GraphQL

**REST dùng cho:**

- Tạo, sửa, archive, restore.
- Kích hoạt/đóng năm học.
- Tạo khối/lớp, chuyển lớp, ghi danh môn.
- Preview/confirm import Excel, Google Sheets, manual grid.
- Submit/return/approve sổ đầu bài và đánh giá.
- Ghi điểm thực hành.
- Tạo quiz/campaign, cấp/revoke quyền câu hỏi.
- Upload/download/export.
- Các action có idempotency, optimistic lock, confirmation hoặc audit quan trọng.

**GraphQL dùng cho:**

- Danh sách trường, cây trường/khối/lớp, directory người học.
- Detail/profile read.
- Workspace sổ đầu bài, đánh giá, điểm thực hành.
- Dashboard và mọi biểu đồ.
- Drill-down dưới từng biểu đồ.
- Danh sách đợt kiểm tra, request quyền và trạng thái import.

**Ngoại lệ hợp lệ:** response từ POST preview/confirm trả ngay kết quả preview/commit. Đây vẫn là kết quả của command, không phải REST read endpoint thay GraphQL.

### 3.2. CQRS nhẹ trong cùng ứng dụng

- Command service thao tác entity/repository trong transaction.
- Read-model service dùng projection + aggregate projection + bounded batch query.
- Resolver chỉ xác thực context/scope rồi gọi read-model service.
- REST controller chỉ map request/response và gọi command service.
- DTO public không được là JPA entity hoặc repository projection.
- Không dùng event sourcing. Dùng audit log + outbox cho invalidation/aggregation bất đồng bộ.

### 3.3. Nguồn dữ liệu chuẩn

- `education_units`: identity của trường/trung tâm/đại học.
- `school_operational_profiles`: profile vận hành 1–1 của education unit.
- `academic_classes`: lớp hiện hữu.
- `lms_students`: hồ sơ người học hiện hữu.
- `curriculum_subjects`: danh mục môn canonical.
- Quiz/assignment/attempt hiện hữu: kiểm tra và điểm kiểm tra.
- Calendar event: lịch phải dạy.
- Class log: dữ liệu thực tế giáo viên đã dạy.

---

## 4. Mô hình dữ liệu đề xuất

Tên migration bắt đầu từ version kế tiếp sau `V49`. Nếu branch khác đã chiếm version, đổi số nhưng giữ thứ tự phụ thuộc.

### 4.1. V50 — School operational core

#### Mở rộng `education_units`

- `tenant_id varchar(36)`; backfill tenant hiện tại, validate rồi chuyển `NOT NULL`.
- `version bigint NOT NULL DEFAULT 0` cho optimistic locking.
- unique `(tenant_id, slug)` khi slug khác null.
- index `(tenant_id, type, status, name)`.

Không đưa địa chỉ/hiệu trưởng/năm học trực tiếp vào `education_units`; bảng này tiếp tục là identity/hierarchy dùng chung CRM/LMS/LCMS.

#### `school_operational_profiles`

| Cột | Ghi chú |
|---|---|
| `school_id` | PK, FK `education_units.id` |
| `tenant_id` | bắt buộc |
| `school_type` | `PRIMARY`, `SECONDARY`, `HIGH_SCHOOL`, `UNIVERSITY`, `TRAINING_CENTER` |
| `address`, `district` | thông tin vận hành |
| `principal_name` | người phụ trách |
| `contact_phone`, `contact_email` | liên hệ |
| `onboarding_status` | `DRAFT`, `ONBOARDING`, `ACTIVE`, `PAUSED`, `ARCHIVED` |
| `current_academic_year_id` | nullable FK |
| `version` | optimistic lock |
| `created_by`, `updated_by`, timestamps | audit |

Unique `(tenant_id, school_id)`.

#### `school_academic_years`

- `id`, `tenant_id`, `school_id`.
- `start_year int`, `end_year int` với check `end_year = start_year + 1`.
- `status`: `PLANNED`, `CURRENT`, `CLOSED`, `ARCHIVED`.
- `starts_on`, `ends_on`, `version`, audit columns.
- Unique `(tenant_id, school_id, start_year)`.
- Partial unique một `CURRENT` cho mỗi `(tenant_id, school_id)`.

#### `school_grades`

- `id`, `tenant_id`, `school_id`, `academic_year_id`.
- `grade_code`, `label`, `sort_order`, `status`, `source`.
- Unique `(tenant_id, school_id, academic_year_id, grade_code)`.

Khi tạo trường hoặc đổi loại trường:

- Tiểu học: 1–5.
- THCS: 6–9.
- THPT: 10–12.
- Đại học/trung tâm: không tạo khối.
- Không tự xóa khối đã có dữ liệu. Nếu đổi loại hình xung đột, trả `409 SCHOOL_TYPE_CONFLICT` và yêu cầu migration plan.

### 4.2. V51 — Class, student membership và môn học

#### Mở rộng `academic_classes`

- `academic_year_id` FK.
- `school_grade_id` nullable FK; null cho đại học/trung tâm.
- `source_type`: `MANUAL`, `EXCEL`, `GOOGLE_SHEETS`, `API`.
- `source_import_job_id` nullable.
- `version bigint`.
- Unique `(tenant_id, school_id, academic_year_id, lower(name))`.
- Index `(tenant_id, school_id, academic_year_id, grade, status)`.

Giữ `grade` hiện tại làm snapshot/compatibility trong giai đoạn chuyển đổi.

#### `student_class_enrollments`

- `id`, `tenant_id`, `school_id`, `academic_year_id`.
- `student_id`, `class_id`.
- `enrolled_at`, `ended_at`, `status`: `ACTIVE`, `TRANSFERRED`, `WITHDRAWN`.
- `source_type`, `source_import_job_id`, audit columns.
- Partial unique một enrollment `ACTIVE` cho mỗi `(tenant_id, academic_year_id, student_id)`.

`lms_students.academic_class_id` tiếp tục là current-class denormalized field để tương thích và đọc nhanh; command service phải cập nhật cùng transaction với enrollment history.

#### `school_subject_offerings`

- `id`, `tenant_id`, `school_id`, `academic_year_id`, `subject_id`.
- `display_name`, `status`, `starts_on`, `ends_on`.
- Unique `(tenant_id, school_id, academic_year_id, subject_id)`.

#### `class_subject_offerings`

- `id`, `tenant_id`, `school_subject_offering_id`, `class_id`.
- `main_teacher_id`, `status`, audit columns.
- Unique `(school_subject_offering_id, class_id)`.

#### `student_subject_enrollments`

- `id`, `tenant_id`, `school_id`, `academic_year_id`.
- `student_id`, `school_subject_offering_id`, `class_id` nullable.
- `source_type`: `CLASS_BULK`, `INDIVIDUAL`, `IMPORT`, `TRANSFER`.
- `source_id`, `status`, `enrolled_at`, `ended_at`, audit columns.
- Partial unique active `(tenant_id, student_id, school_subject_offering_id)`.

Khi gán môn cho cả lớp, backend tạo explicit student enrollment idempotently. Nếu một học sinh có môn riêng thì row `INDIVIDUAL` không bị xóa khi thay đổi môn của lớp.

### 4.3. V52 — Import pipeline và audit

#### `data_import_jobs`

- `id`, `tenant_id`, `school_id`, `academic_year_id`.
- `import_type`: `CLASS`, `STUDENT`, `CLASS_AND_STUDENT`, `SUBJECT_ENROLLMENT`, `QUESTION`.
- `source_type`: `EXCEL`, `GOOGLE_SHEETS`, `MANUAL_GRID`.
- `status`: `UPLOADED`, `PARSING`, `PREVIEW_READY`, `INVALID`, `CONFIRMING`, `COMPLETED`, `PARTIAL`, `FAILED`, `CANCELLED`.
- `file_name`, `object_key`, `sheet_id`, `sheet_range`.
- `mapping_json`, `summary_json`, `payload_hash`, `idempotency_key`.
- `created_by`, `confirmed_by`, timestamps, `expires_at`.

#### `data_import_rows`

- `id`, `job_id`, `row_number`.
- `raw_payload jsonb`, `normalized_payload jsonb`.
- `validation_status`, `error_codes jsonb`, `warning_codes jsonb`.
- `resolved_entity_ids jsonb`.
- Index `(job_id, validation_status, row_number)`.

#### `school_operation_audit_events`

- append-only: tenant, school, actor, action, aggregate type/id, before/after JSON, request id, idempotency key, timestamp.
- Không lưu raw answer, token, credential Google hoặc nội dung file đầy đủ.

### 4.4. V53 — Sổ đầu bài, đánh giá và thực hành

#### `class_log_entries`

- `id`, `tenant_id`, `school_id`, `academic_year_id`, `class_id`.
- `calendar_event_id` nullable; unique khi khác null.
- `teaching_date`, `period_start`, `period_count`.
- `subject_id`, `topic_id` nullable, `topic_title_snapshot`.
- `teacher_user_id`.
- `lesson_objectives`, `actual_content`, `homework`, `teacher_reflection`, `support_notes`.
- `present_count`, `absent_count`, `late_count`.
- `teacher_self_score numeric(3,2)` check 0–5.
- `status`: `DRAFT`, `SUBMITTED`, `LATE`, `RETURNED`, `APPROVED`, `LOCKED`.
- `submitted_at`, `reviewed_by`, `reviewed_at`, `review_note`, `version`.
- Index `(tenant_id, school_id, teaching_date, status)`.
- Index `(tenant_id, class_id, teaching_date)`.
- Index `(tenant_id, teacher_user_id, teaching_date)`.

#### `class_log_attendance`

- `class_log_id`, `student_id`, `tenant_id`.
- `status`: `PRESENT`, `ABSENT`, `LATE`, `EXCUSED`.
- `note`, `recorded_at`.
- PK `(class_log_id, student_id)`.

#### `teacher_weekly_evaluations`

- `id`, `tenant_id`, `school_id`, `teacher_user_id`.
- `week_start`, `week_end`, `class_id` nullable.
- `self_score`, `completion_percent`, `wins`, `issues`, `support_request`, `next_week_plan`.
- `status`: `DRAFT`, `SUBMITTED`, `REVIEWED`, `RETURNED`, `LOCKED`.
- review/audit/version columns.
- Unique `(tenant_id, school_id, teacher_user_id, class_id, week_start)` với null-safe strategy.

#### Rubric đánh giá phòng đào tạo

`training_evaluation_templates`:

- scope school/global, name, target type `CLASS`/`TEACHER`, version, status.

`training_evaluation_criteria`:

- template, code, label, description, weight, max score, sort order.

`training_evaluations`:

- school, class/teacher, week range, evaluator, source class log coverage, status, total score, classification, comment.

`training_evaluation_scores`:

- evaluation, criterion, score, evidence note.

#### Điểm thực hành

`practice_assessments`:

- `id`, tenant/school/year/class/subject/topic, title, assessed_on, max_score, teacher, status, optional `assignment_id`/`quiz_id`.

`practice_scores`:

- assessment, student, score, completion status, feedback, graded_by, graded_at, version.
- Unique `(assessment_id, student_id)`.

Nếu hoạt động thực hành chạy bằng quiz thì lấy điểm từ attempt và lưu link; không copy raw answer.

### 4.5. V54 — Quyền câu hỏi và vòng đời nội dung tạm

#### `question_authoring_requests`

- requester teacher, tenant, school, subject, purpose.
- requested question count, requested from/to.
- `import_only = true` mặc định.
- `status`: `PENDING`, `APPROVED`, `REJECTED`, `REVOKED`, `EXPIRED`.
- reviewer, decision note, timestamps, version.

#### `question_authoring_grants`

- request id, teacher, school, subject.
- valid from/to, max questions, used questions.
- `import_only`, status, revoked fields.
- Index active grant `(tenant_id, teacher_user_id, school_id, subject_id, valid_to)`.

#### Mở rộng `lms_questions`

- `owner_user_id`, `source_type`, `source_import_job_id`.
- `permission_grant_id` nullable.
- `temporary boolean`, `expires_at`.
- `purge_status`: `NONE`, `PENDING`, `ARCHIVED`, `PURGED`, `FAILED`.
- `purged_at`.

#### `assessment_campaigns`

- wrapper nghiệp vụ quanh quiz/assignment hiện hữu.
- school/year, title, subject, owner, source `TRAINING`/`TEACHER`.
- `quiz_id`, `assignment_id`.
- `opens_at`, `closes_at`, `auto_submit`, `temporary_questions`, `purge_after_close`.
- status `DRAFT`, `SCHEDULED`, `OPEN`, `CLOSED`, `LOCKED`, `PURGED`, `CANCELLED`.
- version/audit.

Không xóa `quiz_version_questions`, package snapshot, attempt, attempt answer hoặc result. “Xóa câu hỏi sau kiểm tra” được triển khai là:

1. Hết giờ và assignment đóng.
2. Tất cả recipient ở trạng thái terminal hoặc đã auto-submit.
3. Quiz version/package snapshot đã tồn tại và kiểm tra hash thành công.
4. Archive câu hỏi tạm khỏi authoring bank.
5. Purge payload nguồn theo retention policy; giữ minimal audit metadata.
6. Giữ immutable version snapshot và kết quả để chấm, phúc khảo và audit.

### 4.6. V55 — Read model, index và RLS

Giai đoạn đầu query trực tiếp bằng aggregate projection có index. Nếu EXPLAIN/load test không đạt, bổ sung các read table:

- `training_daily_metrics`.
- `training_weekly_metrics`.
- `class_training_metrics`.
- `teacher_reporting_metrics`.

Metrics được cập nhật qua outbox/event sau:

- class log submit/return/approve.
- weekly evaluation submit/review.
- practice score batch save.
- quiz attempt submit/grade.
- enrollment/class transfer.

Mọi bảng mới có `tenant_id NOT NULL`, RLS policy theo `app.current_tenant_id`, composite index bắt đầu bằng tenant và scope chính.

---

## 5. REST Command API

Base path mới: `/api/v1/lcms`.

Tất cả response dùng `ApiResponse<T>`. Request body dùng Java record + Jakarta validation. Create/confirm/bulk command hỗ trợ `X-Idempotency-Key`. Update/archive dùng `If-Match` hoặc `version` bắt buộc.

### 5.1. Trường và năm học

| Method | Endpoint | Permission | Mục đích |
|---|---|---|---|
| POST | `/schools` | `lms.school.create` | Tạo education unit + operational profile + academic year + default grades trong một transaction. ID do backend cấp. |
| PUT | `/schools/{schoolId}` | `lms.school.update` | Cập nhật toàn bộ profile có version. |
| PATCH | `/schools/{schoolId}` | `lms.school.update` | Cập nhật một phần profile. |
| DELETE | `/schools/{schoolId}` | `lms.school.archive` | Soft archive; 409 nếu còn lớp/năm học active mà không có confirmation policy. |
| POST | `/schools/{schoolId}/restore` | `lms.school.archive` | Restore trường. |
| POST | `/schools/{schoolId}/academic-years` | `lms.school.update` | Tạo năm học. `endYear` backend luôn tính `startYear + 1`. |
| POST | `/schools/{schoolId}/academic-years/{yearId}/activate` | `lms.school.update` | Kích hoạt và đóng current cũ theo policy. |
| POST | `/schools/{schoolId}/academic-years/{yearId}/close` | `lms.school.update` | Đóng năm học sau validation. |
| POST | `/schools/{schoolId}/grades/sync-defaults` | `lms.school.update` | Tạo khối mặc định còn thiếu theo school type. |
| POST | `/schools/{schoolId}/grades` | `lms.class.manage` | Thêm khối tùy chỉnh nếu loại trường cho phép. |
| PATCH | `/schools/{schoolId}/grades/{gradeId}` | `lms.class.manage` | Đổi label/status/sort order. |

`CreateSchoolRequest` tối thiểu:

```json
{
  "name": "THPT Lê Quý Đôn",
  "schoolType": "HIGH_SCHOOL",
  "academicYearStart": 2026,
  "address": "...",
  "district": "...",
  "status": "ONBOARDING"
}
```

Không nhận `id`, `code` hoặc `academicYearEnd` từ client.

### 5.2. Lớp và người học

| Method | Endpoint | Permission | Mục đích |
|---|---|---|---|
| POST | `/schools/{schoolId}/classes` | `lms.class.manage` | Tạo lớp tay. |
| PUT/PATCH | `/schools/{schoolId}/classes/{classId}` | `lms.class.manage` | Sửa lớp/version. |
| DELETE | `/schools/{schoolId}/classes/{classId}` | `lms.class.manage` | Archive lớp; không hard-delete. |
| POST | `/schools/{schoolId}/students` | `lms.student.manage` | Tạo một người học. |
| POST | `/schools/{schoolId}/students/bulk` | `lms.student.manage` | Nhập tay hàng loạt nhỏ; vẫn đi preview/confirm nếu >100 rows. |
| PUT/PATCH | `/schools/{schoolId}/students/{studentId}` | `lms.student.manage` | Sửa hồ sơ. |
| DELETE | `/schools/{schoolId}/students/{studentId}` | `lms.student.archive` | Ngừng học/soft archive. |
| POST | `/schools/{schoolId}/students/{studentId}/transfer-class` | `lms.student.manage` | Kết thúc enrollment cũ, tạo enrollment mới, cập nhật current class. |
| POST | `/schools/{schoolId}/classes/{classId}/students` | `lms.student.manage` | Gán danh sách student vào lớp idempotently. |
| DELETE | `/schools/{schoolId}/classes/{classId}/students/{studentId}` | `lms.student.manage` | Rút khỏi lớp, giữ history. |

### 5.3. Môn học và ghi danh

| Method | Endpoint | Permission | Mục đích |
|---|---|---|---|
| POST | `/schools/{schoolId}/subject-offerings` | `lms.subject.manage` | Đăng ký môn cho năm học. |
| PATCH | `/schools/{schoolId}/subject-offerings/{offeringId}` | `lms.subject.manage` | Sửa trạng thái/thời gian. |
| DELETE | `/schools/{schoolId}/subject-offerings/{offeringId}` | `lms.subject.manage` | Archive nếu không còn enrollment active. |
| POST | `/schools/{schoolId}/subject-enrollments/classes` | `lms.subject.enroll` | Gán một/nhiều môn cho một/nhiều lớp. |
| POST | `/schools/{schoolId}/subject-enrollments/students` | `lms.subject.enroll` | Gán một/nhiều môn cho danh sách người học. |
| DELETE | `/schools/{schoolId}/subject-enrollments/{enrollmentId}` | `lms.subject.enroll` | Kết thúc một enrollment cụ thể. |

Bulk request trả `affectedStudents`, `created`, `alreadyExists`, `ended`, `warnings`.

### 5.4. Import Excel, Google Sheets và manual grid

| Method | Endpoint | Permission | Mục đích |
|---|---|---|---|
| POST multipart | `/schools/{schoolId}/imports/file/preview` | `lms.school.import` | Upload `.xlsx/.xls/.csv`, parse và trả preview. |
| POST | `/schools/{schoolId}/imports/google-sheets/preview` | `lms.school.import` | Đọc sheet/range và tạo preview job. |
| POST | `/schools/{schoolId}/imports/manual/preview` | `lms.school.import` | Preview grid JSON nhập tay hàng loạt. |
| POST | `/schools/{schoolId}/imports/{jobId}/confirm` | `lms.school.import` | Ghi DB sau xác nhận. |
| POST | `/schools/{schoolId}/imports/{jobId}/cancel` | `lms.school.import` | Hủy job. |
| GET | `/schools/import-templates/{type}` | `lms.school.import` | Download template; binary/download là REST. |

Preview phải tự nhận diện các cột trường/khối/lớp/người học, trả mapping, rows hợp lệ/lỗi/cảnh báo và dự kiến số entity sẽ tạo. Confirm không parse lại nguồn; dùng normalized rows đã hash trong job để chống file thay đổi giữa preview và confirm.

### 5.5. Sổ đầu bài và đánh giá

| Method | Endpoint | Permission | Mục đích |
|---|---|---|---|
| POST | `/class-logs` | `lms.class_log.write_self` hoặc `manage` | Tạo draft từ calendar event/class. |
| PUT/PATCH | `/class-logs/{logId}` | `lms.class_log.write_self` | Lưu draft, version bắt buộc. |
| POST | `/class-logs/{logId}/submit` | `lms.class_log.submit_self` | Giáo viên báo giảng. |
| POST | `/class-logs/{logId}/return` | `lms.class_log.review` | PĐT trả lại kèm lý do. |
| POST | `/class-logs/{logId}/approve` | `lms.class_log.review` | PĐT duyệt/lock theo policy. |
| PUT | `/class-logs/{logId}/attendance` | `lms.attendance.write` | Upsert attendance theo học sinh. |
| POST | `/weekly-evaluations` | `lms.weekly_evaluation.write_self` | Tạo/lưu tự đánh giá tuần. |
| POST | `/weekly-evaluations/{id}/submit` | `lms.weekly_evaluation.write_self` | Submit tuần. |
| POST | `/weekly-evaluations/{id}/review` | `lms.training_evaluation.manage` | PĐT review. |
| POST | `/training-evaluations` | `lms.training_evaluation.manage` | Đánh giá lớp/giáo viên theo rubric. |
| PUT | `/training-evaluations/{id}` | `lms.training_evaluation.manage` | Sửa draft/version. |
| POST | `/training-evaluations/{id}/finalize` | `lms.training_evaluation.manage` | Chốt đánh giá. |

### 5.6. Điểm thực hành

| Method | Endpoint | Permission | Mục đích |
|---|---|---|---|
| POST | `/practice-assessments` | `lms.practice_score.write` | Tạo lần đánh giá theo chủ đề. |
| PUT/PATCH | `/practice-assessments/{id}` | `lms.practice_score.write` | Sửa metadata trước khi lock. |
| PUT | `/practice-assessments/{id}/scores` | `lms.practice_score.write` | Upsert hàng loạt điểm học sinh. |
| POST | `/practice-assessments/{id}/finalize` | `lms.practice_score.finalize` | Chốt điểm. |
| POST | `/practice-assessments/{id}/reopen` | `lms.practice_score.reopen` | Mở lại có reason + audit. |

### 5.7. Kiểm tra, quyền và câu hỏi tạm

Tận dụng các endpoint quiz hiện có `/api/v1/lcms/quizzes`, draft, publish và question bank. Bổ sung orchestration sau:

| Method | Endpoint | Permission | Mục đích |
|---|---|---|---|
| POST | `/question-authoring-requests` | `lms.question.request` | Giáo viên xin quyền theo school/subject/time/count. |
| POST | `/question-authoring-requests/{id}/approve` | `lms.question.permission.review` | Tạo grant giới hạn. |
| POST | `/question-authoring-requests/{id}/reject` | `lms.question.permission.review` | Từ chối kèm lý do. |
| POST | `/question-authoring-grants/{id}/revoke` | `lms.question.permission.review` | Thu hồi quyền ngay. |
| POST multipart | `/question-imports/file/preview` | `lms.question.import` | Import câu hỏi bằng file, validate grant. |
| POST | `/question-imports/{jobId}/confirm` | `lms.question.import` | Ghi câu hỏi với grant/source/expiry. |
| POST | `/assessment-campaigns` | `lms.assessment.manage` | Tạo campaign và liên kết quiz/assignment. |
| PATCH | `/assessment-campaigns/{id}` | owner hoặc `manage` | Sửa draft/window trước khi mở. |
| POST | `/assessment-campaigns/{id}/schedule` | `lms.assessment.manage` | Publish quiz/version/package và tạo recipients. |
| POST | `/assessment-campaigns/{id}/close` | `lms.assessment.manage` | Auto-submit/lock campaign. |
| POST | `/assessment-campaigns/{id}/cancel` | `lms.assessment.manage` | Cancel có audit. |

Trong flow kiểm tra theo trường, **mọi câu hỏi mới đều bắt buộc đi qua file import**, kể cả câu hỏi do phòng đào tạo bổ sung. Endpoint tạo câu hỏi thủ công hiện có chỉ tiếp tục phục vụ authoring trung tâm/legacy ngoài flow này và phải được permission tách biệt. `QuestionBankAuthoringService` và `QuestionImportService` phải kiểm tra:

- PĐT/admin trong school assessment flow: bắt buộc import file, không bypass preview/confirm.
- Giáo viên: bắt buộc active grant, đúng school, subject, thời gian, số lượng và `import_only=true`.
- Mọi câu hỏi tạo qua grant mang `temporary=true`, `permission_grant_id`, `expires_at`.

File câu hỏi tối thiểu có các cột:

```text
external_key, question_type, stem, subject_code, level_code, topic_code,
difficulty, choice_a, choice_b, choice_c, choice_d, correct_answer,
max_points, explanation, tags
```

- `external_key` unique trong job để FE/BE đối chiếu lỗi.
- `question_type` phải tồn tại trong `question_types` hiện có.
- Với matching/sequence/drag-drop, dùng sheet phụ hoặc cột JSON có schema version; backend validate depth/size.
- `correct_answer` không xuất lại trong GraphQL workspace thông thường.
- File thiếu subject/level/topic hoặc placement không hợp canonical curriculum bị `422` tại preview.

---

## 6. GraphQL Read API

Không thêm field vào `Query` root ngoài namespace hiện tại. Mở rộng `LcmsQuery`.

### 6.1. Các query đề xuất

```graphql
extend type LcmsQuery {
  schoolDirectory(input: SchoolDirectoryInput): SchoolDirectoryWorkspace!
  schoolOperations(input: SchoolOperationsInput!): SchoolOperationsWorkspace!
  schoolStudentDirectory(input: SchoolStudentDirectoryInput!): SchoolStudentDirectoryWorkspace!
  trainingMonitoringDashboard(input: TrainingMonitoringDashboardInput!): TrainingMonitoringDashboard!
  trainingMetricDrilldown(input: TrainingMetricDrilldownInput!): TrainingMetricDrilldown!
  classLogWorkspace(input: ClassLogWorkspaceInput!): ClassLogWorkspace!
  classEvaluationWorkspace(input: ClassEvaluationWorkspaceInput!): ClassEvaluationWorkspace!
  practiceTopicWorkspace(input: PracticeTopicWorkspaceInput!): PracticeTopicWorkspace!
  assessmentGovernanceWorkspace(input: AssessmentGovernanceInput!): AssessmentGovernanceWorkspace!
  schoolImportWorkspace(input: SchoolImportWorkspaceInput!): SchoolImportWorkspace!
}
```

### 6.2. Scope input dùng chung

```graphql
input SchoolScopeInput {
  tenantId: String
  schoolId: String!
  academicYearId: String
  gradeId: String
  classId: String
  subjectId: String
}

input DateRangeInput {
  from: String!
  to: String!
}
```

Resolver phải gọi:

1. `currentPrincipal()`.
2. `assertPermission(...)`.
3. `verifiedTenantId(...)`.
4. `assertEducationUnitScope(schoolId)`.
5. `assertClassScope(classId, schoolId)` khi có class.
6. Gọi read-model service.

### 6.3. School directory và student directory

`schoolDirectory` trả:

- paged schools.
- status/type/year filters.
- aggregate total schools, active, onboarding, students, classes.

`schoolOperations` trả:

- school profile.
- current/planned years.
- grades/classes tree.
- subject offerings.
- setup completeness và warnings.

`schoolStudentDirectory` input:

- `scope`, `search`, `status`, `subjectId`, `page`, `size`, `sort`.

Response:

- scope breadcrumb.
- grade/class tree với counts.
- summary total, assigned subjects, at risk, inactive.
- paged student rows.
- filter options.

Không load full student entity rồi group trong Java. Dùng:

- `StudentDirectoryRowProjection` paged.
- `StudentScopeCountProjection` group by grade/class/status.
- `StudentSubjectCountProjection` batch theo student IDs của page.

### 6.4. Training monitoring dashboard

Input:

```graphql
input TrainingMonitoringDashboardInput {
  scope: SchoolScopeInput!
  range: DateRangeInput!
  weekMode: TrainingWeekMode = ISO_WEEK
}
```

Response tối thiểu:

```graphql
type TrainingMonitoringDashboard {
  scope: TrainingScope!
  generatedAt: String!
  summary: TrainingSummary!
  weeklyWave: [TrainingWavePoint!]!
  scoreDistribution: [ScoreDistributionBucket!]!
  classHealth: [ClassHealthRow!]!
  classLogCoverage: ClassLogCoverage!
  alerts: [TrainingAlert!]!
}
```

`summary`:

- average student score.
- class log completion percent.
- teachers submitted weekly evaluation/total teachers.
- classes needing attention.
- practice completion percent.

`weeklyWave`:

- week start/end.
- student average normalized 0–100 và raw 0–10.
- teacher self-evaluation normalized 0–100 và raw 0–5.
- class log coverage percent.
- practice average/completion.

`scoreDistribution`:

- bucket `GOOD`, `PASS`, `REINFORCE`, `SUPPORT`.
- student count, percent, previous-period delta.

`classHealth`:

- class, grade, teacher.
- average test score, practice score, log coverage, teacher evaluation.
- status `GOOD`, `WATCH`, `CRITICAL` và reason codes.

### 6.5. Drill-down dưới biểu đồ

```graphql
enum TrainingMetric {
  STUDENT_SCORE
  TEACHER_SELF_EVALUATION
  CLASS_LOG_COVERAGE
  PRACTICE_SCORE
  CLASS_HEALTH
}

input TrainingMetricDrilldownInput {
  scope: SchoolScopeInput!
  range: DateRangeInput!
  metric: TrainingMetric!
  bucket: String
  weekStart: String
  search: String
  page: Int
  size: Int
}
```

Response gồm summary, paged rows và export token/filter snapshot. Drawer mở mới gọi query này; dashboard không trả hàng nghìn row chi tiết.

### 6.6. Sổ đầu bài, đánh giá và thực hành

`classLogWorkspace`:

- status/date/teacher/class filters.
- summary expected sessions, submitted, late, missing, returned.
- paged rows.
- detail có attendance chỉ load khi FE query field detail hoặc gọi query với `logId`.

`classEvaluationWorkspace`:

- class summary page.
- rubric results.
- teacher weekly evaluation coverage.
- score/test/practice/log evidence references.

`practiceTopicWorkspace`:

- topic/class/subject filters.
- topic aggregates.
- paged assessment rows.
- student support drill-down.

### 6.7. Assessment governance

`assessmentGovernanceWorkspace` trả:

- campaign summary/status page.
- active/open/closed counts.
- pending permission count.
- temporary question purge count/failure count.
- permission request page.
- import job status page.

Không trả raw answer, question correct answer hoặc attempt answer mặc định. Các field nhạy cảm phải có permission riêng và chỉ xuất hiện trong query chuyên biệt.

---

## 7. Ánh xạ màn hình FE sang API

| Màn hình | GraphQL load | REST command |
|---|---|---|
| Danh sách trường | `lcms.schoolDirectory` | create/update/archive school |
| Thiết lập trường | `lcms.schoolOperations` | update profile/year/grade/class/offering |
| Người học theo khối/lớp | `lcms.schoolStudentDirectory` | create/update/archive/transfer/import/enroll subject |
| Giám sát đào tạo | `lcms.trainingMonitoringDashboard` | không command trực tiếp |
| Drawer chi tiết chart | `lcms.trainingMetricDrilldown` | export qua REST nếu cần file |
| Sổ đầu bài | `lcms.classLogWorkspace` | draft/submit/return/approve/attendance |
| Đánh giá lớp/GV | `lcms.classEvaluationWorkspace` | create/finalize training evaluation |
| Thực hành theo chủ đề | `lcms.practiceTopicWorkspace` | create assessment/save/finalize scores |
| Kiểm tra & câu hỏi | `lcms.assessmentGovernanceWorkspace` + quiz workspace hiện có | permission/import/campaign command |
| Nhập dữ liệu | `lcms.schoolImportWorkspace` | preview/confirm/cancel/import template |

---

## 8. Import pipeline chi tiết

### 8.1. Excel/CSV

1. Kiểm tra MIME, extension, magic bytes và giới hạn dung lượng.
2. Virus scan/object storage nếu production policy yêu cầu.
3. Tạo import job và hash file.
4. Parse streaming; không load workbook lớn toàn bộ vào heap.
5. Detect header aliases có dấu/không dấu.
6. Chuẩn hóa whitespace, phone/date/class/grade.
7. Resolve existing school/year/grade/class/student/subject bằng batch query.
8. Ghi preview rows và summary.
9. FE review.
10. Confirm theo chunk trong transaction boundary rõ ràng.
11. Ghi audit, outbox và cache invalidation.

### 8.2. Google Sheets

Request nhận `spreadsheetId`, `sheetName`/`range`, không nhận URL tùy ý để tránh SSRF.

- Dùng Google Sheets API với service account hoặc OAuth server-side đã cấu hình.
- Credential lưu Secret Manager/environment, tuyệt đối không lưu DB/job.
- Cho phép whitelist spreadsheet hoặc validate quyền share.
- Snapshot values và content hash tại preview.
- Confirm dùng snapshot đã lưu; nếu sheet thay đổi phải tạo preview mới.
- Rate limit, timeout, retry có backoff; lỗi Google trả typed error `SHEET_ACCESS_DENIED`, `SHEET_NOT_FOUND`, `SHEET_RATE_LIMITED`.

### 8.3. Manual grid

- FE gửi tối đa 500 rows/lần.
- Backend dùng cùng normalization/validation engine với Excel/Sheets.
- Không có code path ghi DB trực tiếp bỏ qua preview.

### 8.4. Quy tắc merge

- Student natural key ưu tiên `(tenant, school, student_code)`.
- Nếu thiếu student code, preview không tự merge theo tên; yêu cầu resolve thủ công hoặc generate new ID/code theo policy.
- Class key `(tenant, school, academic_year, normalized_name)`.
- Không tự chuyển lớp student nếu file không bật `allowClassTransfer`.
- Duplicates trong file được báo ở preview.
- Confirm idempotent theo job + payload hash.

---

## 9. Permission và scope

### 9.1. Permission mới

```text
lms.school.read
lms.school.create
lms.school.update
lms.school.archive
lms.school.import
lms.class.manage
lms.student.read
lms.student.manage
lms.student.archive
lms.subject.manage
lms.subject.enroll
lms.class_log.read_school
lms.class_log.read_self
lms.class_log.write_self
lms.class_log.submit_self
lms.class_log.review
lms.weekly_evaluation.write_self
lms.training_evaluation.read
lms.training_evaluation.manage
lms.practice_score.read
lms.practice_score.write
lms.practice_score.finalize
lms.practice_score.reopen
lms.assessment.read
lms.assessment.manage
lms.question.request
lms.question.import
lms.question.permission.review
lms.training_dashboard.read
```

### 9.2. Role mapping đề xuất

- LCMS/PĐT: read/manage school scope, review log/evaluation, assessment manage, permission review.
- School operator: school/class/student/import/subject manage trong managed education units.
- Teacher: class scope, log self, weekly evaluation self, practice scores cho lớp được phân công, request/import question khi có grant.
- Student: không có các API LCMS này.
- System admin: wildcard nhưng vẫn phải truyền tenant context hợp lệ.

### 9.3. Defense in depth

- `SecurityConfig` chặn theo path + permission.
- Command service kiểm tra tenant, education unit và class ownership lại trước mutation.
- GraphQL resolver dùng `GraphQlAccessSupport`.
- Repository query luôn filter tenant/scope trong SQL/JPQL.
- RLS là lớp cuối, không thay cho service authorization.
- Denied override thắng allow.

---

## 10. Read model, cache và hiệu năng GraphQL

### 10.1. Projection bắt buộc

Mỗi workspace có:

1. Base page projection.
2. Aggregate projection cho metric/card/chart.
3. Batch projection `IN (:ids)` cho relation còn lại.
4. DTO mapper.
5. Query-count integration test.

Không dùng entity graph cho dashboard. Không group hàng chục nghìn student/attempt trong Java.

### 10.2. Query budget

| Query | Mục tiêu SQL count | P95 mục tiêu |
|---|---:|---:|
| `schoolDirectory` | ≤ 4 | < 400 ms |
| `schoolOperations` | ≤ 7 | < 500 ms |
| `schoolStudentDirectory` | ≤ 6 | < 500 ms |
| `trainingMonitoringDashboard` | ≤ 10 giai đoạn 1; ≤ 5 khi có read table | < 700 ms |
| `trainingMetricDrilldown` | ≤ 5 | < 600 ms |
| `classLogWorkspace` | ≤ 5 | < 500 ms |
| `assessmentGovernanceWorkspace` | ≤ 7 | < 600 ms |

GraphQL execution deadline giữ 3–5 giây; JDBC query timeout riêng thấp hơn deadline.

### 10.3. Cache

- Directory/profile reference: 5 phút.
- Dashboard/metric: 30–60 giây.
- Class log list khi vận hành: 15–30 giây hoặc không cache theo page nếu invalidation chưa chắc chắn.
- Cache key bắt buộc gồm tenant, user/permission shape nếu field gated, school, year, grade, class, date range, filters, page, size.
- Dùng Caffeine hiện có giai đoạn đầu; distributed cache chỉ thêm sau load test nhiều instance.
- Event/command invalidation theo aggregate scope, không `allEntries=true` toàn hệ thống.

### 10.4. GraphQL guard

- operationName bắt buộc.
- depth 6–8, complexity theo field/list multiplier.
- page default 20–50, hard max 200.
- dashboard array class/week có hard bound.
- rate limit tenant/user/operationName.
- introspection off ở production trừ internal/admin policy.
- mask variables chứa search phone/email/file/sheet identifiers trong log.

---

## 11. Transaction, concurrency và idempotency

- Create school là một transaction: education unit → profile → year → grades → audit/outbox.
- Update dùng `@Version` hoặc explicit version; stale trả `409 STALE_VERSION`.
- Import confirm dùng job lock (`SELECT ... FOR UPDATE`) và state transition atomic.
- Idempotency key unique theo `(tenant, operation, key)` hoặc dùng pattern hiện có của quiz/calendar.
- Bulk enrollment dùng upsert/unique constraint, không check-then-insert trong loop.
- Practice score batch dùng version per assessment và upsert bounded rows.
- Submit class log idempotent; submit lại cùng version trả current result.
- Assessment schedule tạo quiz version/package/assignment/recipients trong orchestration transaction hoặc saga/outbox nếu package storage ngoài DB.
- Mọi state transition kiểm tra allowed transition trong domain service.

---

## 12. Scheduler và retention

Job đề xuất:

1. `ClassLogDeadlineJob`: đánh dấu expected calendar event chưa có log thành missing/late projection.
2. `WeeklyEvaluationReminderJob`: notification cho giáo viên chưa submit.
3. `AssessmentOpenCloseJob`: mở/đóng campaign theo giờ.
4. `AssessmentAutoSubmitJob`: submit/lock recipient hết giờ idempotently.
5. `TemporaryQuestionPurgeJob`: archive/purge câu hỏi tạm sau khi đủ điều kiện.
6. `ImportJobExpiryJob`: xóa object/preview payload quá hạn.
7. `TrainingMetricsRefreshJob`: backfill/repair read metrics.

Mỗi job:

- distributed lock hoặc DB claim/`SKIP LOCKED`.
- batch size hữu hạn.
- retry + dead-letter/failure status.
- metric duration/success/failure/items.
- audit actor `system:<job-name>`.

---

## 13. Error contract

Sử dụng `GlobalExceptionHandler` hiện tại và typed exception:

- `400`: malformed command/file/sheet reference.
- `401`: thiếu principal/session.
- `403`: permission hoặc scope mismatch.
- `404`: school/class/student/log/campaign không tồn tại trong tenant.
- `409`: stale version, duplicate class/student, invalid state transition, active dependency.
- `413`: file/row count vượt giới hạn.
- `422`: field validation, import row validation, rubric/score invalid.
- `429`: Google/API/GraphQL rate limited.

Mã lỗi nghiệp vụ tối thiểu:

```text
SCHOOL_TYPE_CONFLICT
ACADEMIC_YEAR_ALREADY_CURRENT
CLASS_DUPLICATE
STUDENT_CODE_DUPLICATE
STUDENT_ALREADY_IN_ACTIVE_CLASS
SUBJECT_NOT_OFFERED
IMPORT_PREVIEW_EXPIRED
IMPORT_PAYLOAD_CHANGED
IMPORT_HAS_ERRORS
CLASS_LOG_NOT_OWNED
CLASS_LOG_INVALID_TRANSITION
EVALUATION_ALREADY_FINALIZED
SCORE_OUT_OF_RANGE
QUESTION_GRANT_REQUIRED
QUESTION_GRANT_EXPIRED
QUESTION_GRANT_LIMIT_EXCEEDED
QUESTION_IMPORT_ONLY
ASSESSMENT_WINDOW_CLOSED
TEMP_QUESTION_PURGE_BLOCKED
```

Validation error trả field/row/cell details; không chỉ message chung.

---

## 14. Cấu trúc package đề xuất

```text
controller/
  lcms/
    SchoolCommandController.java
    SchoolImportController.java
    TrainingOperationsController.java
    AssessmentGovernanceController.java
    graphql/
      LcmsSchoolOperationsGraphQlController.java
      LcmsTrainingMonitoringGraphQlController.java

service/
  school/
    SchoolCommandService.java
    SchoolImportService.java
    SchoolEnrollmentService.java
  training/
    ClassLogCommandService.java
    TrainingEvaluationCommandService.java
    PracticeScoreCommandService.java
  assessment/
    QuestionGrantService.java
    AssessmentCampaignService.java
    TemporaryQuestionLifecycleService.java
  lcms/readmodel/
    SchoolOperationsReadService.java
    TrainingMonitoringReadService.java
    AssessmentGovernanceReadService.java

common/request/{school,training,assessment}/
common/response/{school,training,assessment}/
repository/{school,training,assessment}/projection/
model/{school,training,assessment}/
```

Không dồn toàn bộ logic vào `LmsRuntimeService` hoặc controller hiện có.

---

## 15. Kế hoạch triển khai theo phase

### Phase 0 — Contract và foundation (3–4 ngày BE)

- Chốt enum, permission, error codes.
- Bổ sung tenant cho education unit và sửa unscoped cache.
- Tạo skeleton package/controller/service/DTO.
- Schema GraphQL additions ở mức contract.
- Contract tests fail-first.

### Phase 1 — School master, year, grade, class (5–7 ngày)

- V50 + phần class của V51.
- REST school/year/grade/class commands.
- GraphQL school directory/operations.
- Scope/security/audit/cache.

### Phase 2 — Student directory, enrollment và import (7–10 ngày)

- Student class history.
- Subject offering/enrollment.
- Excel/manual pipeline.
- Google Sheets adapter.
- GraphQL student directory/import workspace.
- Transaction/idempotency/query-count tests.

### Phase 3 — Sổ đầu bài và đánh giá (7–10 ngày)

- Class log + attendance.
- Weekly teacher evaluation.
- Training rubric/evaluation.
- Practice assessment/scores.
- GraphQL workspaces và deadline/reminder jobs.

### Phase 4 — Dashboard và drill-down (5–8 ngày)

- Aggregate projections.
- Wave/distribution/class health/log coverage.
- Drill-down query.
- Index/EXPLAIN/query-count/load test.
- Thêm read tables nếu direct aggregation không đạt.

### Phase 5 — Assessment governance (7–10 ngày)

- Permission request/grant.
- Question file import bắt buộc.
- Assessment campaign orchestration dùng quiz/assignment hiện hữu.
- Time window, auto-submit, purge lifecycle.
- Governance workspace GraphQL.

### Phase 6 — Hardening và rollout (5–7 ngày)

- RLS/matrix security test.
- Audit/observability/alert.
- Load/soak/native smoke.
- Backfill dữ liệu mock/legacy.
- FE contract verification và staged rollout.

Tổng ước lượng: **39–56 ngày công BE**. Với 2 BE làm song song theo ranh giới school/import và training/assessment, thời gian lịch khoảng 5–7 tuần, chưa tính thay đổi lớn từ nghiệp vụ hoặc Google credential approval.

---

## 16. Test plan

### 16.1. Unit tests

- Default grade policy theo school type.
- Academic year `end = start + 1`.
- State transitions.
- Subject enrollment union/idempotency.
- Import normalization/mapping/duplicate detection.
- Evaluation weighted score/classification.
- Question grant boundary time/count/scope.
- Temporary purge eligibility.

### 16.2. REST contract tests

- Request validation và `ApiResponse` envelope.
- Permission/path SecurityConfig.
- Idempotency và stale version.
- Multipart MIME/size.
- Preview/confirm không ghi trước confirmation.
- Archive không hard-delete.

### 16.3. GraphQL tests

- Namespace field mapping.
- Tenant mismatch bị 403.
- School/class scope matrix.
- Field-level permission cho score/evaluation/sensitive detail.
- Pagination hard max.
- OperationName/depth/complexity/deadline.
- Nullable gated fields không làm lộ dữ liệu.

### 16.4. PostgreSQL integration

- Testcontainers + Flyway từ clean DB.
- FK/unique/check/partial index.
- RLS tenant isolation.
- Native projection mapping.
- Query-count với nhiều school/class/student/attempt để lộ N+1.
- EXPLAIN ANALYZE cho dashboard và drill-down.

### 16.5. Workflow tests

- School create → grades → class import → student import → subject enrollment.
- Calendar event → class log submit → PĐT review → dashboard update.
- Practice score save/finalize/reopen.
- Teacher request → approve → question import → campaign → assignment → attempts → close → purge.
- Google Sheet thay đổi sau preview phải bị chặn confirm.
- Retry command không tạo duplicate.

### 16.6. Performance

- Dataset chuẩn: 100 schools, 5.000 classes, 200.000 students, 1 triệu class logs, 5 triệu scores/attempt answers.
- Load theo operation mix thực tế, không chỉ một dashboard query.
- Dashboard P95/P99, DB CPU, Hikari wait, query timeout, cache hit.
- Soak test scheduler + import + dashboard đồng thời.

---

## 17. Observability

Metrics tối thiểu:

```text
erg_school_command_total{operation,status}
erg_school_import_jobs_total{type,source,status}
erg_school_import_rows_total{status,error_code}
erg_class_log_total{status,school}
erg_class_log_submission_delay_seconds
erg_weekly_evaluation_completion_ratio
erg_training_dashboard_duration_seconds{operation_name}
erg_training_dashboard_sql_count
erg_question_permission_requests_total{status}
erg_assessment_campaigns_total{status,source}
erg_temporary_question_purge_total{status}
```

Log structured gồm request id, tenant, actor, school, class, aggregate id, operation, duration; không log raw answer, PII đầy đủ, file contents hoặc Google credential.

Alert:

- GraphQL timeout/error rate.
- Import FAILED/PARTIAL tăng.
- Purge FAILED/PENDING quá SLA.
- Class log missing vượt ngưỡng.
- Hikari wait/DB saturation.
- Scheduler lag.

---

## 18. Rollout và compatibility

1. Migration additive; chưa xóa column/table legacy.
2. Backfill `education_units.tenant_id`, school profiles, academic year hiện hành.
3. Dual-write `lms_students.academic_class_id` và enrollment history.
4. Các read GraphQL mới dùng feature flag theo tenant/school.
5. FE chuyển từng màn hình khỏi mock sang GraphQL/REST.
6. So sánh metric dashboard mới với query đối soát SQL.
7. Bật jobs reminder/auto-close ở dry-run trước.
8. Bật purge ở archive-only trước; hard purge payload sau khi audit 2 chu kỳ.
9. Deprecate GET read endpoints legacy liên quan sau khi FE không còn dùng.
10. Không dùng GraphQL Mutation mới; lập backlog chuyển calendar mutation client sang REST nếu còn consumer.

---

## 19. Definition of Done

Một phase chỉ hoàn thành khi:

- Flyway chạy được từ clean DB và upgrade DB có dữ liệu.
- REST/GraphQL contract có typed DTO và test.
- Permission + tenant + school/class scope test đủ allow/deny.
- Không trả entity/projection trực tiếp.
- Query count bounded, không N+1.
- Có composite index và EXPLAIN cho hot query.
- Command có idempotency/optimistic locking phù hợp.
- Audit event và cache invalidation hoạt động.
- Loading lớn không có unbounded list/Java filtering.
- OpenAPI/Postman và GraphQL operation mẫu cập nhật.
- FE có query/mutation contract, error codes và pagination rõ ràng.
- Metrics/log/alert cơ bản đã có.
- Không làm lộ raw answer hoặc PII ngoài permission.

---

## 20. Các quyết định cần chốt trước Phase 1

Nếu product chưa trả lời, dùng mặc định dưới đây để BE không bị chặn:

1. **Trường có thuộc một tenant duy nhất?** Mặc định: có; thêm `tenant_id` vào `education_units`.
2. **Đổi loại hình trường sau khi có dữ liệu?** Mặc định: cho phép khi không xung đột; nếu có lớp/khối trái policy thì 409.
3. **Student code tự sinh?** Mặc định: backend sinh khi create manual, import thiếu code là warning và cần policy explicit trước confirm.
4. **Một học sinh một lớp active trong một năm học?** Mặc định: có.
5. **Điểm thực hành scale?** Mặc định 0–10 nhưng lưu `max_score` để hỗ trợ scale khác.
6. **Deadline báo giảng?** Mặc định cuối ngày dạy; cấu hình được theo tenant/school.
7. **Khóa sổ đầu bài?** Mặc định PĐT approve hoặc tự lock sau 7 ngày.
8. **Câu hỏi tạm giữ bao lâu?** Mặc định archive ngay sau campaign locked, purge payload sau 7 ngày; quiz version/result giữ theo retention chính.
9. **Google Sheets credential mode?** Mặc định service account với sheet được share; OAuth per-user là phase sau.
10. **Dashboard near-real-time?** Mặc định SLA 60 giây, không realtime subscription ở phase đầu.

---

## 21. Thứ tự PR khuyến nghị

1. `school-foundation-migrations-security`
2. `school-command-rest-api`
3. `school-directory-graphql-read-model`
4. `student-class-subject-enrollment`
5. `school-import-preview-confirm`
6. `class-log-weekly-evaluation`
7. `practice-assessment-scores`
8. `training-dashboard-graphql`
9. `training-dashboard-drilldown`
10. `question-authoring-grants-import`
11. `assessment-campaign-lifecycle`
12. `temporary-question-retention-jobs`
13. `performance-security-rollout-gates`

Mỗi PR migration/schema phải đi kèm contract test và rollback/forward-fix note. Không gộp toàn bộ domain vào một PR lớn.
