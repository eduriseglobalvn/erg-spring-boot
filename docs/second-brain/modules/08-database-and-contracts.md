# 08 - Database schema evolution và Contracts (Flyway, GraphQL, OpenAPI, ops/postgres)

Phạm vi đã đọc: 105 file `src/main/resources/db/migration/postgres/V*.sql` (+ `V0__baseline.sql`), `src/test/resources/db/migration/V73__...sql`, `src/main/resources/graphql/**` (config + 8 module + `schema.graphqls`), `contracts/**` (OpenAPI JSON được tóm tắt bằng script đọc-only, GraphQL sha), `ops/postgres/**`, `ops/docker/postgres/**`.
Ghi chú độ phủ: các migration seed/FK dài được đọc theo khối đầu + grep các câu lệnh quan trọng (V4, V6, V11, V12-V16 phần danh sách FK, V21, V33/V34 phần FK, V38 phần backfill DO-block, V39, V41, V52, V57 phần danh sách permission). Số dòng trích dẫn là dòng thật trong file (với V1-V10 chỉ trích file, không trích dòng).

---

## 1. Phạm vi và tổng quan schema

Schema PostgreSQL do Flyway quản lý (`src/main/resources/application.yml:38-42`: `spring.flyway.enabled=${FLYWAY_ENABLED:true}`, `locations=classpath:db/migration` - tức quét đệ quy cả `db/migration/V0__baseline.sql` và thư mục con `postgres/`, `baseline-on-migrate=true`, `baseline-version=0`). Dãy version là V0, V1..V103, V106..V109 (không có V104, V105). Id thực thể là chuỗi (`varchar(24)` thời đầu, mở rộng `varchar(36)` ở V8 cho UUID v7, nhiều bảng mới dùng `varchar(36/96)`), hầu hết bảng nghiệp vụ có `tenant_id` và (từ V29 trở đi) được bảo vệ bằng Row Level Security theo GUC `app.tenant_id` / `app.maintenance_mode`.

Lịch sử tổng quát: lõi auth/RBAC + LMS + CRM + học liệu (V1-V9) -> thông báo Firebase (V10) -> khóa ngoại 3 bước expand/validate (V11-V16) -> lịch giảng dạy (V17-V22) -> backend Quiz (V23-V34) -> hardening phiên/đăng nhập (V35, V43-V44, V51-V64, V70-V71, V78) -> chuyển học liệu legacy `hoclieu_*` sang `curriculum_*`/`content_*` (V36-V42, V45-V46, V49) -> RLS đa tenant (V29, V65, V74-V75, V81, V90, v.v.) -> các "contract" BE-0xx (V82-V99) -> bổ sung chương trình/PPCT và sửa độ rộng cột (V100-V109).

---

## 2. Timeline migration (nhóm theo chủ đề)

Thư mục gốc: `src/main/resources/db/migration/`. Tất cả file dưới đây nằm trong `postgres/` trừ V0.

### 2.1 Baseline và lõi (V0-V9)
- `V0__baseline.sql`: chỉ là comment placeholder (schema trước đó do Hibernate validate quản lý).
- `postgres/V1__postgres_core_schema.sql`: tạo `users`, `roles`, `permission_groups`, `permissions`, `user_roles`, `role_permissions`, `education_units` (cây đơn vị: system/center/school), `user_sessions`, `auth_pins`, `auth_login_attempts`, `profiles`, `social_accounts`, `certificates`, `user_activities`, `user_permissions` (override quyền theo user), `user_access_scopes` (phạm vi module theo đơn vị), `work_shifts` + index tra cứu phiên theo hash token.
- `V2__learning_resource_schema.sql`: `academic_classes`, `lms_assignments`, cụm `hoclieu_*` (taxonomy, resources, assets, resource_items, asset_slide_cache, progress_state, slide_annotations) với unique `uk_hoclieu_progress_state_asset_scope`, `uk_hoclieu_annotation_owner_scope`. Cụm hoclieu bị xoá ở V40.
- `V3__lms_runtime_schema.sql`: `lms_subjects`, `lms_levels`, `lms_topics`, `lms_questions`, `lms_quizzes`, `lms_students`, `lms_attempts` (answers/events là jsonb nhúng).
- `V4__enterprise_reference_seed.sql`: seed idempotent `permission_groups`, `permissions` (vd `*`, `lms.read`...), roles (`system.super_admin`, `lms.admin`, `lms.instructor`, `lms.learner`...), cây đơn vị (`ctr_system`, `ctr_hoclieu`, trường demo `sch_binh_an`, `sch_cat_lai`).
- `V5__school_crm_schema.sql`: `crm_school_profiles`, `crm_school_contacts`, `crm_school_deals`, `crm_school_activities`, `crm_referrals`.
- `V6__school_crm_permission_seed.sql`: nhóm quyền `crm`/`organization`, quyền `crm.*`, `organization.school.activate`, role `seo.staff/lead/manager`, `center.manager`.
- `V7__enterprise_admin_account_seed.sql`: seed tài khoản super admin cố định (user, user_roles, user_access_scopes, profile). File chứa giá trị password hash argon2id cố định - xem Rủi ro 7.2.
- `V8__expand_string_ids_for_uuid_v7.sql`: nới `varchar(24)` -> `varchar(36)` cho id/FK-id của users/roles/permissions/sessions/LMS/CRM... (lý do: UUID v7).
- `V9__lms_hoclieu_read_model_indexes.sql`: index read-model (assignment theo lớp/status/due, attempts, students, hoclieu_resources).

### 2.2 Thông báo push (V10, V15, V19, V80, V86)
- `V10__firebase_push_notifications.sql`: `notification_messages` (unique `(tenant_id, dedupe_key)`), `notification_recipients` (unique `(notification_id,user_id,portal)`), `notification_device_registrations`, `notification_outbox` (pickup theo status/next_attempt_at, `max_attempts` mặc định 8), `notification_delivery_attempts`, `notification_topic_memberships`, `notification_preferences`.
- `V19__notification_retention_policy.sql`: `notification_retention_config` + hàm `cleanup_stale_delivery_attempts()` + lịch `pg_cron` 02:00 nếu có extension `pg_cron` (V19:67-76).
- `V80__notification_outbox_retention_index.sql`: partial index cho purge outbox `sent/dead` theo `created_at`.
- `V86__notification_delivery_provider_registry.sql`: thêm cột `provider` cho outbox, `provider_error_code/message` cho delivery attempts (đa nhà cung cấp, không chỉ Firebase).

### 2.3 Toàn vẹn quan hệ: FK "expand -> validate" (V11-V16, V18, V33-V34)
- `V11__relationship_integrity_seed_cleanup.sql` (449 dòng): seed vài user/role/scope demo rồi DELETE các dòng mồ côi trong ~30 bảng (V11:110-444) để chuẩn bị FK.
- `V12`, `V13`, `V14`, `V15`: thêm index cột FK và `ADD CONSTRAINT ... NOT VALID` cho nhóm core (V12), LMS (V13), hoclieu (V14), CRM + notification (V15; V15:1-7 nới cột owner CRM sang `varchar(36)`).
- `V16__validate_relationship_foreign_keys.sql`: `VALIDATE CONSTRAINT` toàn bộ FK trên.
- `V17`/`V18`: FK lịch giảng dạy tạo NOT VALID ở V17:116-167 rồi validate ở V18.
- Mẫu lặp lại: V33 (14 FK NOT VALID + validate cuối file V33:260+), V34 (cleanup + FK + 36 `VALIDATE`, V34:349-384).

### 2.4 Lịch giảng dạy / Teaching calendar (V17-V22)
- `V17__teaching_schedule_schema.sql`: `teaching_schedule_batches`, `teaching_schedule_events`, `teaching_schedule_event_teachers`, `teaching_schedule_rooms`; quyền `lms.schedule.read_self/read/manage` gán cho role `lms.admin`, `lms.instructor`, `lms.learner` (V17:171-215).
- `V19` (xem 2.2), `V20__teaching_schedule_conflicts_audit.sql`: `teaching_schedule_conflicts` (loại xung đột lớp/giáo viên/phòng).
- `V21__teaching_calendar_seed_data.sql`: dữ liệu demo (4 trường, 5 user giáo viên, môn, lớp, batch/event).
- `V22__teaching_calendar_enterprise_indexes.sql`: index hot-path cho GraphQL calendar (time-range theo tenant/school/class/subject, phòng theo `lower(room_name)` bỏ CANCELLED).

### 2.5 Backend Quiz (V23-V34)
- `V23__quiz_authoring_core.sql`: cột routing/phiên bản cho `lms_quizzes` (`portal`, `current_version_id`, `latest_draft_id`, `idempotency_key`, `deleted_at`...), bảng `quiz_drafts` (jsonb `tree`, `doc_version` lạc quan), GIN full-text/`jsonb_path_ops`.
- `V24__question_bank_formalize.sql`: cột ngân hàng câu hỏi trên `lms_questions`, `question_bank_subjects/levels/categories`, `quiz_bank_items`.
- `V25__media_assets.sql`: `media_assets` (dedup `(tenant_id, sha256)`), `media_references` (ref-count idempotent).
- `V26__quiz_versions.sql`: `quiz_versions` (snapshot bất biến, 1 bản active/quiz), `quiz_version_sections`, `quiz_version_questions`, `quiz_packages` (gói ký, unique theo version).
- `V27__attempts_runtime.sql`: `attempt_events` (append-only idempotent theo id), `attempt_answers` (PK `(attempt_id, quiz_version_question_id)`), `student_progress`; ghim attempt vào `quiz_version_id`.
- `V28__lms_distribution.sql`: `assignment_resources` (nhiều quiz/bài giao, ghim version), `assignment_recipients` (phẳng theo học sinh), `student_groups`, `student_group_members`; thêm `attempt_limit`, `max_duration_minutes`, `start_at`, `idempotency_key`... cho `lms_assignments`.
- `V29__quiz_rls_enable.sql`: bật + FORCE RLS cho 18 bảng quiz (policy tương thích: GUC rỗng = mở, V29:56-68) - bị thắt chặt ở V65.
- `V30`: unique idempotency khi bắt đầu attempt. `V31`: `note`, `color` cho `student_groups`. `V32`: `pg_trgm` + index lọc/ILIKE ngân hàng.
- `V33__question_type_and_quiz_question_relationships.sql`: bảng tham chiếu `question_types` (15 loại, seed + chuẩn hoá dữ liệu cũ `lms_questions.type`), `lms_quiz_questions` (quan hệ thật quiz-câu hỏi, backfill từ `lms_quizzes.question_ids`, V33:196-246), FK.
- `V34__quiz_runtime_relationship_integrity_hardening.sql`: dọn dữ liệu mồ côi (có `DELETE`) rồi thêm FK cho toàn bộ bảng quiz/runtime/distribution.

### 2.6 Phiên, đăng nhập, tài khoản (V35, V43-V44, V47-V48, V51, V53, V55, V58, V60-V62, V64, V70-V72, V78, V100, V108-V109)
- `V35`: `device_fingerprint` + index phiên/đăng nhập. `V58`: `user_sessions.platform` (slot desktop/mobile, backfill theo user-agent). `V70`/`V71`: phát hiện replay refresh token (`previous_refresh_token_hash`, bảng `refresh_token_history`). `V78`: `access_token_expires_at` (token truy cập trước đó sống bằng phiên 8-12h, xem comment V78:1-5).
- `V43`: `users.username` + unique `lower(username)` partial. `V64`: index `lower(username)`, `lower(email)`.
- `V44`: `security_events`; `V60`: mở rộng (platform/result/risk_code/session_id/metadata) + `employee_profiles`. `V53`: `user_activities.request_id/metadata`.
- `V47`: DROP `social_accounts` và cột Google/provider trên `users`. `V48`: DROP `auth_pins`.
- `V51`: vòng đời tài khoản (`must_change_password`, `recovery_email`, `recovery_email_verified`) + `account_verification_codes` (chỉ lưu hash; unique mã đang hoạt động theo `(user_id,purpose)`). `V55`: backfill `recovery_email_verified` cho super admin. `V109`: nới `account_verification_codes.user_id` lên 36.
- `V72__webhook_ingress.sql`: `webhook_receipts` (dedupe + hết hạn), `webhook_inbox`.
- `V100__server_side_auth_session_tenant_lookup.sql`: hàm `SECURITY DEFINER erg_resolve_session_tenant(hash)` để biết tenant trước khi RLS có hiệu lực.

### 2.7 RBAC / quyền (V6, V17, V52, V54, V56-V57, V59, V61-V63, V77, V84, V108)
- `V52__lms_teacher_permission_seed.sql`: danh mục quyền LMS giáo viên + 6 role template (`lms_teacher_standard`, `lms_homeroom_teacher`, `lms_subject_teacher`, `lms_teacher_assistant`, `lms_teacher_readonly`, `lms_teacher_lead`) và gán quyền (342 dòng).
- `V54`: thu hồi 8 quyền "server-inactive" (`lms.exercise.assign`, `lms.grade.write/finalize/export`, `lms.attendance.write/export`, `lms.class_log.write`, `lms.report.export`) khỏi 6 role giáo viên (V54:1-22) - V85 sau đó dùng `lms.grade.write`, `lms.attendance.write` ở GraphQL (xem Rủi ro 7.9).
- `V56`: unique `lower(roles.name)`. `V57`: metadata permission (`resource/action/portal/supported_scope/is_system/status`) + bảng `permission_bindings` (REST/GRAPHQL/ROUTE/SERVICE) + seed nhiều nhóm/quyền. `V61`: `users.authz_version`. `V62`: `roles.updated_by`.
- `V59__scoped_role_assignments.sql`: `user_role_assignments` (scope SYSTEM/TENANT/CENTER/SCHOOL/CLASS, thu hồi mềm), đổi tên role `center.manager` -> `center_manager`, thêm role `it_operator`, `pdt_operator`, `system.admin`, `system.god_mode`.
- `V63`: sửa kiểu UUID -> varchar(36) cho id RBAC ở DB cũ (drop FK, đổi kiểu, khôi phục FK).
- `V77`: index ngược cho `user_roles(role_id)`, `role_permissions(permission_id)`. `V84`: seed lặp quyền `elearning.*` (xem Rủi ro 7.8). `V108`: nới `user_role_assignments` (user_id/assigned_by/revoked_by -> 36, role_id -> 64).

### 2.8 Chuyển đổi học liệu legacy -> curriculum/content (V36-V42, V45-V46, V49, V92, V101-V107)
- `V36__canonical_curriculum_schema.sql`: `curriculum_subjects/levels/topics` (unique `(tenant, code)`).
- `V37__canonical_content_schema.sql`: `content_items`, `content_assets`, `content_item_assets`, `topic_content_items`.
- `V38__canonical_curriculum_backfill_bridge.sql` (1034 dòng): bảng `legacy_id_mappings` + DO-block backfill `lms_subjects/levels/topics` và taxonomy hoclieu -> `curriculum_*`.
- `V39__canonical_content_backfill_from_legacy_resources.sql`: backfill `hoclieu_resources/assets/items` -> `content_*` (id sinh bằng md5).
- `V40__drop_legacy_hoclieu_tables.sql`: `DROP TABLE ... CASCADE` 7 bảng hoclieu + xoá mapping.
- `V41` seed canonical (IC3, Tin hoc...). `V42`: quyền `content.*`, `curriculum.manage`; thay `hoclieu.*` -> `content.manage`; xoá `ctr_hoclieu`, chuyển con sang `ctr_content_ops`; `DROP TABLE legacy_id_mappings CASCADE` (V42:112). `V45`: lặp lại dọn tên runtime. `V46`: `updated_at` cho 2 bảng liên kết.
- `V49__assessment_canonical_curriculum_cutover.sql`: map id cũ (`ic3-gs6`, `python`, `mos`...) sang id canonical rồi đổi FK của `lms_questions`/`lms_quizzes` sang `curriculum_*` (V49:26-86).
- `V92__content_lifecycle_contract.sql`: `lifecycle_state` (DRAFT/IN_REVIEW/APPROVED/PUBLISHED/ARCHIVED) + `record_version` cho 6 bảng, bảng `content_lifecycle_transitions`.
- `V101`/`V102`: `curriculum_teaching_plan_settings` + seed demo. `V103`: thêm level IC3 L2/L3. `V106`/`V107`: `curriculum_programs`, `curriculum_program_rows` (PPCT theo level) + seed demo 12 tiết.

### 2.9 Trường, lớp, vận hành LMS (V50, V82-V83, V85, V87-V91, V98)
- `V50__school_operational_core.sql`: `tenant_id`/`version` cho `education_units`, unique slug theo tenant, `school_operational_profiles`, `school_academic_years` (1 năm CURRENT/trường), `school_grades`; quyền `lms.school.*`, `lms.class.manage`.
- `V82__crm_workspace_contract.sql`: mở rộng CRM (lead_status, probability, revenue/cost lines jsonb, idempotency) + `crm_handovers`.
- `V83`: `lms_announcements`, `student_discussion_*`, `student_announcement_reads` (+ RLS). `V85`: `lms_attendance_records`, `lms_gradebook_columns`, `lms_gradebook_scores`.
- `V87`: `lms_attempts.policy_snapshot/submit_idempotency_key/record_version`. `V88`: `record_version` + unique `student_code`/`subject.code`/tên lớp, bảng `lms_student_enrollments`. `V89`: `lms_sync_operations` (receipt offline sync). `V90`: bật RLS cho enrollments. `V98`: `lms_assignments.offline_allowed`.

### 2.10 Hạ tầng bất đồng bộ, báo cáo, audit (V66-V69, V73-V76, V79, V81, V91, V93)
- `V66`: `outbox_events` (thông báo SMTP...). `V67`: `report_jobs`. `V69`: `assignment_progress_aggregates` 64 shard + `report_reconciliation_queue` + trigger trên `student_progress`. `V73`: `event_publication` (Spring Modulith). `V91`: `report_jobs.format/expires_at`.
- `V74`/`V75`/`V81`: role runtime `erg_app`, RLS trên mọi bảng có `tenant_id`, assertion không phải owner/superuser/BYPASSRLS. `V65`: thắt chặt policy quiz.
- `V76`: unique idempotency (assignments, quiz_versions, quizzes) sau khi NULL hoá bản trùng. `V79`: index "latest/best score" cho attempts. `V68`: seed demo quiz_version/package, đẩy `due_at` demo.
- `V93__immutable_audit_log.sql`: `audit_events` append-only + hash chain.

### 2.11 Contract học tập và tích hợp (V94-V97, V99)
- `V94`/`V95`: `learning_paths`, `learning_path_nodes`, `competencies`, `competency_attainments`, `outcome_certificates`, `learning_path_completions`, receipts. `V96`: SCORM/LTI/xAPI (`learning_standard_*`). `V97`: `enterprise_connectors` (SIS/HRIS/SCIM), runs, receipts. `V99`: metadata gói portable (`quiz_packages` thêm ký/thu hồi/kênh phát hành).

---

## 3. Bảng/entity chính hiện tại (theo domain)

Quy ước chung: id `varchar(36)` (hoặc 96), `created_at/updated_at timestamptz`, hầu hết có `tenant_id`; enum lưu dưới dạng text (+ CHECK ở bảng mới).

### 3.1 Auth / RBAC / tài khoản
- `users` (V1+V43/V51/V61): `status`, `account_type`, `tenant_id`, `username` (unique `lower(username)` partial, V43), `recovery_email` (unique `lower`, V51), `must_change_password`, `authz_version`. Cột provider/google đã bị bỏ (V47).
- `roles` (unique `lower(name)` V56), `permissions` (unique `name`, thêm `resource/action/portal/supported_scope/is_system/status` V57), `permission_groups` (unique `name`), `role_permissions`, `user_roles`.
- `user_role_assignments` (V59): role theo phạm vi + hết hạn + thu hồi; unique "đang hiệu lực" theo `(user_id, role_id, tenant_id, scope_type, coalesce(scope_id,''))`.
- `user_permissions` (override theo user, `grant_type`), `user_access_scopes` (modules + đơn vị), `permission_bindings` (permission ↔ bề mặt REST/GRAPHQL/ROUTE/SERVICE; unique `(permission_id, surface_type, surface_identifier)`).
- `user_sessions` (hash token, `platform`, `access_token_expires_at`, `previous_refresh_token_hash`), `refresh_token_history`, `auth_login_attempts`, `security_events`, `account_verification_codes`, `user_activities` (nhật ký hành động, có `request_id`), `employee_profiles`, `profiles`, `certificates`, `work_shifts`.
- `education_units` (cây `parent_id`; `type`: system/center/school; `tenant_id`, `version`; unique `(tenant_id, lower(slug))` V50).

### 3.2 LMS (lớp, học sinh, giao bài, làm bài)
- `academic_classes` (FK `school_id` -> `education_units` RESTRICT, `homeroom_teacher_id` -> users SET NULL; unique `(tenant, school, lower(name), coalesce(academic_year,''))` V88), `lms_students` (unique `(tenant, lower(student_code))` V88), `lms_subjects` (unique `(tenant, lower(code))`), `lms_student_enrollments` (unique "đang ENROLLED" `(tenant, student, class, coalesce(year,''))` V88:36-38).
- `lms_assignments` (cột legacy `academic_class_id/quiz_id` + `recipient_mode`, `attempt_limit`, `offline_allowed`, unique `(tenant,idempotency_key)`), `assignment_resources` (PK `(assignment_id, quiz_id)`, ghim version), `assignment_recipients` (unique `(assignment_id, student_id)`), `student_groups`, `student_group_members`.
- `lms_attempts` (`status`, `quiz_version_id`, `policy_snapshot`, `start_idempotency_key`, `submit_idempotency_key`, `record_version`), `attempt_events`, `attempt_answers`, `student_progress` (unique `(tenant, student_user_id, quiz_id, assignment_id)`), `assignment_progress_aggregates` (64 shard), `report_reconciliation_queue`.
- Sổ điểm/điểm danh: `lms_attendance_records` (CHECK lesson 1..20, status PRESENT/ABSENT/LATE/EXCUSED), `lms_gradebook_columns` (kind TOPIC/QUIZ/OFFLINE), `lms_gradebook_scores` (FK ghép tới cột).
- Tương tác học viên: `lms_announcements`, `student_discussion_threads/comments/reactions`, `student_announcement_reads`.
- Trường: `school_operational_profiles`, `school_academic_years`, `school_grades`.
- Lịch: `teaching_schedule_batches` (DRAFT/PUBLISHED/CANCELLED), `teaching_schedule_events`, `teaching_schedule_event_teachers` (MAIN/ASSISTANT), `teaching_schedule_rooms`, `teaching_schedule_conflicts`.
- Kết quả học tập: `learning_paths`, `learning_path_nodes`, `competencies`, `competency_attainments`, `learning_path_completions`, `outcome_certificates`; chuẩn: `learning_standard_packages/lti_registrations/xapi_statements`.

### 3.3 LCMS (nội dung, quiz, ngân hàng câu hỏi)
- Curriculum: `curriculum_subjects` -> `curriculum_levels` -> `curriculum_topics` (FK RESTRICT, unique code trong cha); `curriculum_programs` (unique `(tenant, subject, level, academic_year)`), `curriculum_program_rows` (unique `(program_id, period)`), `curriculum_teaching_plan_settings`.
- Content: `content_items`, `content_assets`, `content_item_assets`, `topic_content_items`, `content_lifecycle_transitions`.
- Ngân hàng: `lms_questions` (FK `type` -> `question_types`, subject/level/topic/category -> `curriculum_*` từ V49), `lms_quizzes`, `lms_quiz_questions`, `quiz_drafts`, `quiz_versions`, `quiz_version_sections`, `quiz_version_questions`, `quiz_packages`, `quiz_bank_items`, `question_bank_subjects/levels/categories`, `question_types`, `media_assets`, `media_references`.
- Enum/trạng thái quan trọng: `lifecycle_state` (5 giá trị, V92:28-31), `quiz_versions.grading_mode` (mặc định `client-first`), `question_types.grading_policy` (`auto|manual|survey`).

### 3.4 CRM
- `crm_school_profiles` (1 trường = 1 hồ sơ; `lead_status`, `probability`, `risks` jsonb), `crm_school_contacts`, `crm_school_deals` (`stage`, doanh thu `numeric(18,2)`, `revenue_lines/cost_lines` jsonb), `crm_school_activities`, `crm_referrals`, `crm_handovers` (FK tới lead/opportunity; unique idempotency `(tenant, requested_by, key)`). Các bảng `crm_*` (trừ handovers) không có `tenant_id`.

### 3.5 Thông báo / outbox / webhook / sự kiện
- `notification_messages`, `notification_recipients` (status unread...), `notification_device_registrations`, `notification_outbox` (status pending...; `provider`), `notification_delivery_attempts`, `notification_topic_memberships`, `notification_preferences`, `notification_retention_config`.
- `outbox_events` (status `pending|processing|retry|sent|dead`, lease, unique `(tenant, idempotency_key)`), `report_jobs` (`queued|processing|completed|failed`, `format json|csv`), `webhook_receipts`, `webhook_inbox` (`pending|processing|completed|failed`), `event_publication` (Spring Modulith), `lms_sync_operations`.

### 3.6 Audit và tích hợp doanh nghiệp
- `audit_events` (hash chain `previous_hash/event_hash`, `retention_until`, `legal_hold`, outcome SUCCESS/FAILURE/DENIED/PARTIAL), `security_events`, `user_activities`.
- `enterprise_connectors` (SIS/HRIS/SCIM, `secret_ref` bắt buộc dạng `secret://...`), `enterprise_connector_runs`, `enterprise_connector_command_receipts`; các bảng `*_command_receipts` (outcomes, standards) làm khoá idempotency `(tenant, idempotency_key, operation)`.

---

## 4. Ràng buộc dữ liệu nghiệp vụ ở DB

### 4.1 CHECK
- Lịch: batch `status` DRAFT/PUBLISHED/CANCELLED và `repeat_weeks` 1..52 (`V17__...sql:22-23`); event `weekday` 1..7, `period_count` 1..10, `start_at < end_at` (V17:56-60); teacher role MAIN/ASSISTANT (V17:82); xung đột `ROOM/CLASS/TEACHER_TIME_CONFLICT` (V20:21-22).
- Trường: loại trường PRIMARY/SECONDARY/HIGH_SCHOOL/UNIVERSITY/TRAINING_CENTER (V50:31-32), onboarding DRAFT/ONBOARDING/ACTIVE/PAUSED/ARCHIVED (V50:33-34), năm học `end_year = start_year + 1` (V50:54), ngày kết thúc >= bắt đầu (V50:55).
- RBAC: scope `SYSTEM/TENANT/CENTER/SCHOOL/CLASS`, `scope_id` bắt buộc trừ SYSTEM/TENANT, `expires_at > created_at` (V59:19-25); `permission_bindings.surface_type` (V57:30-31); nhân sự `experience_years >= 0` và `terminated_at >= joined_at` (V60:22-25).
- Outbox/report: `outbox_events.status` (V66:19), `report_jobs.status`, `progress 0..100` (V67:23-24), `format json|csv` (V91:8), shard `0..63` và các bộ đếm >= 0 (V69:18-21), `report_reconciliation_queue.status` (V69:39).
- Điểm danh/sổ điểm: lesson 1..20, status, `max_score > 0`, `score <= max_score` (V85:14-15,33-34,52). Phản ứng thảo luận (V83:98-99). Sync receipt PENDING/SUCCEEDED (V89:22). Thông báo retention >= 1 ngày (V19:13).
- Lifecycle 5 trạng thái cho 6 bảng, sinh động trong DO-block (V92:28-31) + `content_lifecycle_transitions` kind/state (V92:54-59).
- Audit: outcome và `scope_type` (V93:25-28). Outcomes: path DRAFT/PUBLISHED/ARCHIVED (V94:16), attainment IN_PROGRESS/ATTAINED/REVOKED + progress 0..100 (V95:20-27), certificate ISSUED/REVOKED/EXPIRED (V94:80), completion INCOMPLETE/COMPLETED (V95:47-51). Standards: SCORM_12/SCORM_2004, size <= 512MB (V96:23-25). Connector: kind SIS/HRIS/SCIM, `secret_ref LIKE 'secret://%'`, hash 64 hex, `retry_count <= 3` (V97:21-24,44-47,60). `quiz_packages.size_bytes >= 0` (V99:20-29). `legacy_id_mappings.confidence` 0..1 (V38:15-16, bảng bị drop ở V42).
- Webhook inbox status (V72:16).

### 4.2 Unique / partial unique index
- Chỉ một phiên bản quiz active: `uq_quiz_versions_active ON quiz_versions(quiz_id) WHERE is_active` (V26:34-35); số/nhãn version unique (V26:30-33); một package/version (V26:108-109).
- Chỉ một năm học CURRENT mỗi trường: `uk_school_year_current` (V50:60-61).
- Chỉ một mã xác minh đang hoạt động mỗi (user, purpose): `uq_account_verification_codes_active WHERE consumed_at IS NULL` (V51:33-35).
- Role scope đang hiệu lực không trùng: `uq_user_role_assignments_active_scope WHERE revoked_at IS NULL` (V59:28-30). Ghi danh hiện hành duy nhất: `uq_lms_enrollment_current_membership WHERE status='ENROLLED'` (V88:36-38).
- Idempotency: assignments/quiz_versions/quizzes (V76:19-21,38-40,57-59), start attempt (V30:6-8), submit attempt (V87:12-14), CRM deals/activities/handovers (V82:51-56), thảo luận (V83:123-126), enrollment (V88:33-35), audit `(tenant_id, idempotency_key)` (V93:31-32), receipts V94/V96/V97, `lms_sync_operations` PK `(tenant_id, account_id, idempotency_key)` (V89:20), batch lịch `(tenant, idempotency_key)` (V17:27-29).
- Định danh: `uq_users_username_normalized` (V43:9-11), `uq_users_recovery_email_normalized` (V51:7-9), `ux_roles_name_lower` (V56:5-7), `uk_education_units_tenant_slug` (V50:8-9), tên lớp/mã học sinh/mã môn (V88:6-11), `uk_profiles_user_id` (V12:2), `uq_media_assets_tenant_sha256` (V25:26-27).
- Notification: `(tenant_id, dedupe_key)` (V10), recipients `(notification_id,user_id,portal)`, device `(tenant, firebase_installation_id)` và `(tenant, registration_token_hash)`.
- Học tập: `ux_outcome_certificates_number`, `ux_outcome_certificates_identity (tenant, learner, path)`, `ux_competency_attainments_identity` (V94:96-105), LTI `(tenant, issuer, client_id)` (V96:80-81), xAPI `(tenant, statement_id)` (V96:84-85), connector `(tenant, provider_key)` (V97:63-64).

### 4.3 Trigger / function
- `erg_student_progress_assignment_aggregate_trigger` + `trg_student_progress_assignment_aggregate` (AFTER INSERT/UPDATE/DELETE trên `student_progress`, V69:98-137): trừ delta bản cũ, cộng delta bản mới vào shard `hashtextextended(student_user_id,0) & 63`; thao tác UPDATE/DELETE còn đẩy `report_reconciliation_queue`. `erg_apply_assignment_progress_delta` (V69:45-96) và `erg_reconcile_assignment_progress` (V69:139-160) xây lại từ nguồn.
- `erg_audit_events_append_only` + trigger `audit_events_append_only` (BEFORE UPDATE OR DELETE, V93:55-70): luôn RAISE `audit_events_append_only`, trừ DELETE khi GUC `app.audit_retention_job = 'true'`.
- `erg_resolve_session_tenant(p_access_token_hash)` (V100:4-21): `SECURITY DEFINER`, `search_path` cố định, `REVOKE ALL FROM PUBLIC`, chỉ cấp EXECUTE cho `erg_app`/`erg` (V100:25-33); trả tenant khi đúng 1 tenant khớp phiên còn hạn.
- `cleanup_stale_delivery_attempts()` (V19:31-61) + job `pg_cron` có điều kiện (V19:67-76).

### 4.4 Row Level Security
- Policy cú pháp hiện hành: `app.maintenance_mode = 'true'` HOẶC (`app.tenant_id` khác rỗng VÀ `tenant_id = app.tenant_id`). Áp dụng: 18 bảng quiz (V29 lần đầu, thắt chặt V65:15-33), `report_jobs` (V67:32-43), 2 bảng aggregate (V69:178-194), mọi bảng còn lại có cột `tenant_id` (V75:12-39, ENABLE nhưng KHÔNG FORCE), `crm_handovers` (V82:58-69), bảng V83/V85/V89/V90 (ENABLE).
- FORCE RLS: các bảng quiz (V29/V65), `report_jobs`, aggregates, `content_lifecycle_transitions` (V92:67-68), `audit_events` (V93:40-41), nhóm outcomes (V94:135-137), `learning_path_completions` (V95:61-62), standards (V96:99-100), connectors (V97:83-84).
- Role runtime `erg_app`: tạo ngoài Flyway ở `ops/docker/postgres/init/02-runtime-role.sh` (NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT; mật khẩu lấy từ biến môi trường, không ghi trong repo); `V74` cấp CRUD trên mọi bảng/sequence; `V81:19-37` raise nếu `erg_app` là superuser/BYPASSRLS hoặc sở hữu bảng. Cả V74 lẫn V81 bị bỏ qua nếu role `erg_app` chưa tồn tại.

---

## 5. GraphQL

Nguồn: `src/main/resources/graphql/modules/*.graphqls` (8 file, đánh số thứ tự nạp) và `schema.graphqls` = nối các module (39 076 byte). Sha256 thực tế của `schema.graphqls` bằng `contracts/graphql/schema.graphqls.sha256` (`6426a173...ae88c`) - khớp. `graphql.config.yml` chỉ là placeholder (comment) để Hibernate enhancement scan không gặp file 0 byte.

Namespace gốc (`Query`, `00-shell`): `lms: LmsQuery`, `lcms: LcmsQuery`, `crm: PlatformNamespace` (chỉ `namespace`/`status`, không có truy vấn nghiệp vụ), `elearning: ElearningQuery`. Hầu hết id/ngày là `String`; scalar tuỳ chỉnh/directive: không có; enum chỉ ở lịch (`TeachingScheduleTeacherRole`, `TeachingCalendarViewMode`, `TeachingScheduleStatus`, `TeachingScheduleResolveMode`).

### 5.1 `LmsQuery` (cổng giáo viên/học sinh LMS)
- `learningResourceLibrary`, `classWorkspace`, `teacherHomeworkWorkspace`, `assignmentProgressWorkspace`, `studentGroups`, `myTeachingCalendar`, `myStudentCalendar`, `attendanceWorkspace`, `gradebookWorkspace`; mở rộng ở `35-learning-outcomes`: `learningOutcomes`, `learningOutcomePath`, `verifyOutcomeCertificate`.
- Kiểu chính: `ClassWorkspace` (thông tin lớp, tóm tắt điểm/rủi ro, danh sách học sinh/bài tập phân trang), `TeacherHomeworkWorkspace` (thẻ bài giao, chờ chấm, học sinh trễ hạn), `AssignmentProgressWorkspace` (chi tiết, attempts, người nhận, thiếu/trễ), `LearningResourceLibrary` (thẻ học liệu, cây taxonomy, tiến độ), `AttendanceWorkspace`, `GradebookWorkspace`.

### 5.2 `LcmsQuery` (cổng quản trị nội dung/trường)
- Ngân hàng & quiz: `questionBankWorkspace`, `quizWorkspace`, `quizDraft(quizId)`, `quizVersions(quizId)`, `quizQuestionAnalytics(quizVersionId)`, `assignmentReport(assignmentId, page, size)`.
- Quyền truy cập: `accessManagementWorkspace` (người dùng, role, scope, quyền hiệu lực).
- Lịch: `teachingCalendarWorkspace`, `teachingScheduleBatches`, `teachingScheduleBatch`.
- Trường (`45-school-operations-mutations` + `10-school-quiz`): `schoolDirectory`, `schoolOperations` (hồ sơ trường, năm học, khối, lớp, học sinh, môn, quyền thao tác).

### 5.3 `ElearningQuery`
- `studentDashboard` (viewer, hồ sơ, tóm tắt điểm, khóa học, bài tập, hành động kế tiếp, thông báo, danh sách lớp), `discussionFeed`.

### 5.4 `Mutation` (khai báo `type Mutation` ở `40-mutation-social`, `extend` ở file 35 và 45)
- Điểm danh/sổ điểm: `upsertAttendance`, `upsertGradebookScore`.
- Thảo luận học viên: `createStudentDiscussion`, `replyStudentDiscussion`, `setStudentDiscussionReaction`, `markStudentAnnouncementRead`.
- Lịch giảng dạy: `previewTeachingSchedule`, `createTeachingSchedule`, `updateTeachingScheduleEvent`, `deleteTeachingScheduleEvent`, `deleteTeacherSchedule`, `publishTeachingScheduleBatch`, `cancelTeachingScheduleBatch`, `createRoom`, `importTeachingScheduleFromExcel`.
- Vận hành trường (45): `createSchoolClass/updateSchoolClass/archiveSchoolClass`, `createSchoolStudent/updateSchoolStudent/archiveSchoolStudent`, `importSchoolStudents`, `createSchoolSubject/updateSchoolSubject/archiveSchoolSubject`, `enrollSchoolStudent`, `bulkEnrollSchoolStudents`.
- Kết quả học tập (35): `createLearningPath`, `publishLearningPath`, `addLearningPathNode`, `createCompetency`, `recordCompetencyAttainment`, `evaluateLearningPathCompletion`, `issueOutcomeCertificate`, `revokeOutcomeCertificate` - tất cả input có `idempotencyKey: String!` và (khi sửa) `expectedVersion`.
- Không có mutation GraphQL cho quiz authoring, giao bài, attempts, CRM, tài khoản (các việc này nằm ở REST, xem mục 6).

---

## 6. REST / OpenAPI

`contracts/openapi/erg-openapi.json`: OpenAPI 3.1.0, title `ERG Spring API`, version `v1`, 225 path / 263 operation / 451 schema; sha256 trong `erg-openapi.sha256` khớp (`3fdc86b0...a48da852`). Không có `tags` mức document; nhóm theo controller (tag mặc định springdoc). Mọi response bọc `ApiResponse<T>` (rất nhiều schema `ApiResponse*`) và có `ValidationErrorResponse`. Chỉ có một security scheme `bearerAuth`, áp dụng toàn cục.

Nhóm theo controller / prefix:
- Xác thực & phiên: `auth-controller` (`/api/v1/auth/login|refresh|logout|logout-all|login-attempts|security/ip-status`), `session-controller` (`/api/v1/session/current`, `/api/v1/sessions/current|context`).
- Tài khoản & nhân sự: `user-controller` (`/api/v1/users...`: tự phục vụ `/me`, mật khẩu, recovery-email challenge/verify, sessions, onboarding, bulk, roles, permissions, resend-invite, reset-credential), `admin-employee-controller` (`/api/v1/admin/users...`), `profile-controller` (`/api/v1/profiles`).
- RBAC: `authorization-controller` (`/api/v1/roles`, `/permissions`, `/permissions/catalog`, `/permission-groups`, `/users/{id}/effective-permissions|permission-overrides|role-assignments|preview`, `/me/feature-config`).
- Tổ chức: `education-unit-controller` (`/api/v1/centers`, `/education-units`), `school-command-controller` (`/api/v1/lcms/schools...`: trường, năm học activate/close, khối, sync-defaults, restore), `lms-operational-command-controller` (`/api/v1/lcms/schools/{id}/classes|students`, `/lcms/subjects`, `/lcms/enrollments`).
- LMS học sinh/giao bài: `lms-controller` (`/api/v1/lms/attempts...` start/answers/draft/submit/sync, `quizzes/{id}/package`, `students/me/assignments|scores`, `assignments/{id}/progress`, `classes`), `lms-assignment-controller` (`/api/v1/lms/assignments`, `.../recipients`, `/lms/student-groups`).
- LCMS: `lcms-quiz-authoring-controller` (`/api/v1/lcms/quizzes`: draft PUT/PATCH, publish, validate, reorder, slides, fork), `lcms-question-bank-authoring-controller` (`/lcms/questions`), `lcms-media-controller` (`/lcms/media/presign|commit|upload/...`), `lcms-quiz-bundle-controller` (`/lcms/quiz-packages/updates|{id}/download|manifest|revoke`, `/lcms/quiz-versions/{id}/bundle`, `/lcms/quizzes/{id}/bundle`), `curriculum-controller` (`/api/v1/curriculum/tree|subjects|levels|topics` + archive/impact), `learning-content-controller` (`/api/v1/content/items|assets|lectures/google-slides`, `/curriculum/topics/{id}/content-items`), `content-lifecycle-controller` (`POST /api/v1/content/{resourceKind}/{resourceId}/lifecycle`), `calendar-rest-controller` (`/api/v1/calendar...`: workspace, my-calendar, batches, import-excel, rooms).
- CRM: `crm-workspace-controller` (`/api/v1/crm/workspace|leads|opportunities|follow-ups|handovers`), `crm-school-controller` (`/api/v1/crm/seo/{seoId}/schools|performance`).
- Học tập & tiêu chuẩn: `learning-outcomes-controller` (16 operation: paths, competencies, attainments, certificates + verify), `learning-standards-controller` (SCORM packages/validate/launch, LTI registrations/launch, xAPI statements).
- Doanh nghiệp/vận hành: `enterprise-connector-controller` (connectors CRUD-ish + dry-run/sync/retry/deprovision), `audit-controller` (`/api/v1/audit/events|export`), `report-job-controller` (`/api/v1/reports/jobs...`), `notification-controller` + `notification-device-controller` + `notification-admin-controller` (`/api/v1/notifications...`, `/notification-devices...`, `/admin/notifications/send|broadcast|stats|outbox/run|replay-dead`), `platform-health-controller` (`/api/v1/health|ready`, `/healthz`, `/ready`, `/metrics`), `webhook-controller` (`POST /webhooks/discord|telegram`).

Nhận xét đối chiếu (sự thật từ JSON):
- `learning-content-controller` công bố 2 bộ đường dẫn: `/api/content/*`, `/api/curriculum/topics/*` (không có `v1`) và `/api/v1/content/*`, `/api/v1/curriculum/topics/*` - 10 cặp trùng.
- Một số tài nguyên có cả `PUT` và `PATCH` (lớp, học sinh, môn, trường, role, profile, quiz draft); có cả `GET /api/v1/users` lẫn `GET /api/v1/users/` (và `/admin/users/`), cả `/session/current` lẫn `/sessions/current`.
- Spec không khai báo `security` theo operation (0/263 có trường `security`); do đó login/refresh/webhook/health/verify-certificate đang hiển thị là yêu cầu `bearerAuth` toàn cục dù thực tế là công khai (xem Rủi ro 7.12). Với 6 operation mẫu được kiểm tra, chỉ khai báo response `200`.

---

## 7. Rủi ro

7.1 Migration không đảo ngược được (dữ liệu bị xoá/đổi): V11 (DELETE mồ côi ~30 bảng, V11:110-444), V34 (DELETE `quiz_drafts`, `attempt_events`, `attempt_answers`, `student_progress`, `assignment_*`, `student_groups`... mồ côi, V34:19-116), V40 (`DROP TABLE ... CASCADE` 7 bảng hoclieu), V42 (DELETE role/permission/education_units và `DROP TABLE legacy_id_mappings CASCADE`, V42:37-112), V45 (DELETE tương tự), V47 (DROP `social_accounts` + 4 cột `users`), V48 (DROP `auth_pins`), V54 (DELETE `role_permissions`), V76 (NULL hoá idempotency_key bản trùng, V76:12-16,32-36,51-55), V33 (UPDATE chuẩn hoá `lms_questions.type`, ép các giá trị lạ thành `multiple_choice`, V33:72-86). Theo AGENTS.md migration là append-only: sửa tiếp bằng forward fix, không có đường rollback trong repo.

7.2 Bí mật/seed trong migration (bảo mật): `V7__enterprise_admin_account_seed.sql` ghi cứng một password hash argon2id cho tài khoản super admin và `V11` seed user `@example.test` với `password_hash = NULL`; `V21`, `V33` seed user có email thật dạng `@erg.edu.vn` và `seed.*@erg.local`. Đây là dữ liệu demo nằm trong đường migration production (không có profile tách). Nghi ngờ: nếu chưa đổi mật khẩu super admin sau lần triển khai đầu thì tài khoản có credential biết trước. (Không sao chép hash vào note này.)

7.3 Seed/fixture demo trộn trong migration chính: V21, V41, V68 (đẩy `due_at` của assignment demo về +7 ngày mỗi lần chạy trên DB mới), V102, V107 (V102:1 và V107:1 ghi rõ "Local/demo fixture"). Trên production các hàng demo (`sch_binh_an`, `sub_ic3`...) sẽ tồn tại.

7.4 Thứ tự/phụ thuộc: V17 gán quyền theo tên role `lms.admin|lms.instructor|lms.learner` (có từ V4), nhưng V59:43-49 đổi tên role `center.manager` -> `center_manager`; V50:112 vẫn lọc theo tên cũ `center.manager` - chạy đúng thứ tự (V50 trước V59) nên không sao với DB mới, nhưng DB đã tồn tại role dưới tên khác thì V50 bỏ qua im lặng (nghi ngờ). V49 tạo FK hợp lệ ngay (không NOT VALID, V49:80-86) sau khi remap id; dữ liệu còn id lạ (không thuộc `ic3-gs6|python|mos|...`) sẽ làm migration thất bại. V88 tạo unique index trên `lower(student_code)`, `lower(code)`, `lower(name)` mà không dọn trùng trước (V88:6-11) - fail nếu DB đã có trùng. Thiếu V104, V105 (khoảng trống số version; Flyway chấp nhận nhưng nên xác nhận cố ý).

7.5 Phạm vi RLS: V75 chỉ bật cho bảng có `tenant_id` tại thời điểm chạy và không FORCE; bảng tạo sau V75 phải tự bật (V82, V83, V85, V89 tự bật; `lms_student_enrollments` ở V88 bị quên và chỉ vá ở V90). Các bảng `crm_school_*`, `crm_referrals`, `curriculum_*` có `tenant_id` nhưng không phải mọi bảng được kiểm chứng; `crm_school_*` không có `tenant_id` nên không bị RLS (nghi ngờ rò rỉ chéo tenant nếu service không lọc). Cột `tenant_id` ở V2/V3 (academic_classes, lms_assignments, lms_attempts...) cho phép NULL (ví dụ V2:5,17; V3:99) - hàng NULL không thoả policy nên vô hình với runtime role (nghi ngờ).

7.6 Trigger tổng hợp tiến độ (`V69`): chạy với mọi UPDATE của `student_progress` (kể cả cập nhật nhịp hoạt động) - tăng chi phí ghi (nghi ngờ hiệu năng); nhánh trừ chỉ `UPDATE` shard hiện có (không insert) nên aggregate có thể lệch cho đến khi `report_reconciliation_queue` được xử lý.

7.7 Audit "bất biến" có lỗ hổng cấu hình: DELETE được cho phép khi GUC `app.audit_retention_job='true'` (V93:59-62); GUC này do session đặt nên bất kỳ kết nối nào có thể bật nó (nghi ngờ - chưa xác minh code ứng dụng có chặn). Chủ bảng cũng có thể `DISABLE TRIGGER`.

7.8 Dữ liệu trùng/chồng chéo: V83 và V84 seed cùng 3 quyền `elearning.*` với id khác nhau (`perm_elearning_*` vs `perm_el_*`); `ON CONFLICT (name)` giữ id V83 nên id `perm_el_*` không tồn tại (V83:22-24 so với V84:7-15). V42 và V45 lặp lại cùng phần dọn `hoclieu` (V42:37-112 vs V45:52-67).

7.9 Quyền: V54 thu hồi `lms.grade.write`, `lms.attendance.write` khỏi 6 role giáo viên vì "server-inactive", nhưng V85:73-97 đăng ký binding GraphQL cho `lms.attendance.write`/`lms.grade.write` (`Mutation.upsertAttendance`, `Mutation.upsertGradebookScore`) và schema đã có hai mutation này. Nghi ngờ: giáo viên chuẩn mất quyền ghi điểm danh/điểm nếu không có migration/seed khác cấp lại (cần xác minh trong V57/V84/V85 và code).

7.10 Ràng buộc yếu: `lms_attendance_records`/`lms_gradebook_*` không có FK tới lớp/học sinh/môn (V85:1-52), chỉ unique tự nhiên; `subject_id varchar(100)` khác kiểu id chuẩn. Token đăng ký thiết bị lưu nguyên văn (`notification_device_registrations.registration_token text`, V10) kèm hash - nghi ngờ rủi ro nếu bị lộ DB (đối chiếu quy tắc không lưu secret).

7.11 Lệch kiểu/độ rộng id đã phải vá sau: V59 dùng `varchar(24)` cho user/role id, V51 dùng `varchar(24)` cho `account_verification_codes.user_id` -> phải vá ở V108/V109 vì user id UUID v7 dài 36 (V8). Nguy cơ tái diễn khi thêm bảng mới.

7.12 Lệch contract: OpenAPI không khai báo endpoint công khai (login/refresh/webhooks/health/verify certificate) là `security: []` (sự thật mục 6) - client sinh mã có thể đòi bearer. Trùng đường dẫn `/api/*` và `/api/v1/*`, trùng PUT/PATCH, trailing slash làm bề mặt contract phình to. GraphQL `crm` chỉ là stub trong khi REST CRM phong phú (không phải lỗi, nhưng khác kỳ vọng "GraphQL-first").

7.13 Bản test lệch bản main: `src/test/resources/db/migration/V73__spring_modulith_event_publication.sql` định nghĩa `event_publication` bằng `varchar(512/1000000)` và index btree `event_publication_serialized_event_hash_idx(serialized_event)`, còn bản main V73 dùng `text` và `USING hash(serialized_event)`. Vì cả hai có cùng version 73 và không thấy override `spring.flyway.locations` trong `src/test/resources`, nghi ngờ Flyway sẽ báo trùng version nếu test chạy Flyway với classpath gộp (chưa xác minh; có test `EventPublicationRegistryContractTest` chỉ đọc file main).

7.14 Chỉ mục ngoài Flyway: ba index ở `ops/postgres/online/*.sql` (assignment recipient lookup, user activity cursor, attempt version/status) dùng `CREATE INDEX CONCURRENTLY` và "không chạy bằng Flyway startup" - môi trường không chạy tay sẽ thiếu index so với DB benchmark. `V19` phụ thuộc `pg_cron` (không có thì không có job dọn); `V89` ghi rõ không có retention trong DB cho `lms_sync_operations`.

7.15 `pg_trgm` (V32:6) và `pg_stat_statements` (ops init) cần quyền tạo extension; V32 chạy trong transaction migration - fail nếu user migrate không đủ quyền.

---

## 8. Câu hỏi còn mở

1. V104 và V105 bị bỏ trống có chủ ý hay là nhánh đã rút lại? DB production đang ở version nào?
2. Mật khẩu/credential của tài khoản super admin seed ở V7 đã được đổi trên mọi môi trường chưa; có kế hoạch tách seed demo (V7, V11, V21, V41, V68, V102, V107) khỏi đường migration production?
3. Test sử dụng Flyway với classpath nào (có gộp `src/test/resources/db/migration/V73` với `postgres/V73` không)? Test V73 có phải bản cho H2/DB khác?
4. `lms.grade.write` / `lms.attendance.write` sau V54: giáo viên được cấp lại ở đâu (V57, V84, V85 hay code `ErgPolicyService`)?
5. Các service có luôn đặt `app.tenant_id` / `TenantContext.maintenance()` cho worker nền (outbox, report, retention) khi RLS đã bật? GUC `app.audit_retention_job` được bảo vệ thế nào?
6. `crm_school_*` có cần `tenant_id` + RLS không, hay tenant suy ra qua `education_units`?
7. Hợp đồng OpenAPI có kế hoạch gỡ các đường dẫn không-`v1` và khai báo `security: []` cho endpoint công khai không (theo giao thức `add -> publish -> sync -> generate -> migrate -> deprecate -> observe -> remove`)?
8. Bảng legacy còn dùng làm "cache tương thích": `lms_quizzes.question_ids/slides`, `lms_attempts.answers/events`, `lms_assignments.academic_class_id/quiz_id` (comment V23, V27, V28) - lộ trình gỡ khi nào?
9. Index `ops/postgres/online/*` đã được áp trên staging/prod chưa, và có nên đưa vào migration có điều kiện?
