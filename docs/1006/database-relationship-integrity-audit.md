# Audit ràng buộc quan hệ dữ liệu ERG Spring

Ngày audit: 2026-06-10  
Phạm vi: PostgreSQL public schema đang chạy local, migration `src/main/resources/db/migration/postgres`, JPA entity mapping trong `src/main/java/com/erg/ergspringboot/model`.

## 1. Kết luận ngắn

Nhận định của anh là đúng: schema hiện tại đang **thiếu ràng buộc quan hệ dữ liệu ở database** cho phần lớn bảng nghiệp vụ. Nhóm notification mới có một số foreign key nội bộ, còn các bảng auth, LMS, học liệu, CRM chủ yếu chỉ có cột `*_id` và index, chưa có `FOREIGN KEY`.

Kết quả audit trực tiếp trên PostgreSQL:

```text
TOTAL_TABLES=46
TABLES_WITH_FK=4
FK_COUNT=4
```

4 FK hiện có đều thuộc notification:

```text
notification_delivery_attempts.outbox_id -> notification_outbox.id
notification_outbox.notification_id -> notification_messages.id
notification_recipients.notification_id -> notification_messages.id
notification_topic_memberships.device_registration_id -> notification_device_registrations.id
```

Đã kiểm tra 66 quan hệ nghiệp vụ quan trọng đáng lẽ nên có ràng buộc DB:

```text
CHECKED=66
EXPECTED_RELATIONSHIPS_WITHOUT_FK=66
RELATIONSHIPS_WITH_ORPHANS=6
```

Nghĩa là DB hiện **chưa enforce referential integrity** cho các quan hệ lõi như user-role, role-permission, user-session, class-assignment, assignment-attempt, student-user, học liệu-resource-asset, CRM-school-deal.

## 2. Bằng chứng từ migration

### 2.1 Auth/RBAC chỉ có khóa chính, chưa có FK

`V1__postgres_core_schema.sql` tạo các bảng join nhưng không khai báo `REFERENCES`:

```sql
CREATE TABLE IF NOT EXISTS user_roles (
    user_id varchar(24) NOT NULL,
    role_id varchar(24) NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id varchar(24) NOT NULL,
    permission_id varchar(24) NOT NULL,
    PRIMARY KEY (role_id, permission_id)
);
```

Các bảng như `user_sessions`, `profiles`, `social_accounts`, `certificates`, `user_access_scopes` có `user_id` hoặc `education_unit_id`, nhưng không có FK tới `users` hoặc `education_units`.

### 2.2 LMS/Học liệu có nhiều `*_id` nhưng chủ yếu là index

`V2__learning_resource_schema.sql`:

```sql
academic_classes.school_id
academic_classes.homeroom_teacher_id
lms_assignments.academic_class_id
lms_assignments.quiz_id
lms_assignments.subject_id
lms_assignments.assigned_by
hoclieu_assets.resource_id
hoclieu_resource_items.resource_id
hoclieu_resource_items.asset_id
```

Các cột trên chỉ được index, không có `REFERENCES`.

`V3__lms_runtime_schema.sql`:

```sql
lms_levels.subject_id
lms_topics.level_id
lms_questions.subject_id
lms_questions.level_id
lms_questions.topic_id
lms_quizzes.subject_id
lms_quizzes.level_id
lms_students.school_id
lms_students.academic_class_id
lms_students.auth_user_id
lms_attempts.assignment_id
lms_attempts.quiz_id
lms_attempts.student_id
```

Tất cả đang là cột tham chiếu logic, chưa phải quan hệ DB.

### 2.3 Notification có FK nội bộ nhưng vẫn thiếu FK tới users

`V10__firebase_push_notifications.sql` có FK nội bộ:

```sql
notification_recipients.notification_id REFERENCES notification_messages(id) ON DELETE CASCADE
notification_outbox.notification_id REFERENCES notification_messages(id) ON DELETE CASCADE
notification_delivery_attempts.outbox_id REFERENCES notification_outbox(id) ON DELETE SET NULL
notification_topic_memberships.device_registration_id REFERENCES notification_device_registrations(id) ON DELETE CASCADE
```

Nhưng các cột sau vẫn chưa có FK tới `users(id)`:

```text
notification_recipients.user_id
notification_device_registrations.user_id
notification_preferences.user_id
```

## 3. Bằng chứng từ JPA entity

Source có một số mapping quan hệ, ví dụ:

- `User.roles` dùng `@ManyToMany` qua `user_roles`.
- `Role.permissions` dùng `@ManyToMany` qua `role_permissions`.
- `Assignment.academicClass` dùng `@ManyToOne`.
- `AssetRecord.resource` và `ResourceItemRecord.asset/resource` dùng `@ManyToOne`.

Nhưng nhiều mapping dùng:

```java
insertable = false, updatable = false
```

Mapping này giúp Hibernate đọc quan hệ khi query entity graph/lazy loading, nhưng không tự tạo FK vì cấu hình đang là:

```yaml
spring:
  flyway:
    enabled: false
  jpa:
    hibernate:
      ddl-auto: validate
```

`ddl-auto=validate` chỉ kiểm tra schema, không sinh constraint. Vì vậy DB chỉ có FK nếu migration SQL tự khai báo.

## 4. Dữ liệu orphan đã thấy trong DB local

Một số quan hệ đã có dữ liệu con trỏ tới parent không tồn tại:

```text
academic_classes.homeroom_teacher_id -> users.id orphans=2
lms_assignments.assigned_by -> users.id orphans=2
lms_students.auth_user_id -> users.id orphans=2
notification_recipients.user_id -> users.id orphans=3
notification_device_registrations.user_id -> users.id orphans=2
notification_preferences.user_id -> users.id orphans=3
```

Đây có thể là seed/test data chưa khớp user master, nhưng nó chứng minh nếu add FK ngay sẽ fail migration. Cần cleanup hoặc bổ sung parent seed trước.

## 5. Rủi ro hiện tại

1. Dữ liệu mồ côi có thể tăng dần.
   Ví dụ xóa user nhưng session/profile/notification/device/preference vẫn còn.

2. Query/read-model có thể trả dữ liệu sai nghiệp vụ.
   Ví dụ attempt trỏ tới assignment đã xóa, học sinh trỏ tới lớp không còn tồn tại.

3. Báo cáo/GraphQL projection dễ lệch.
   Các workspace đọc theo `tenant_id + *_id`; nếu parent không tồn tại, UI vẫn có thể hiển thị record bán hợp lệ.

4. Không bảo vệ được tenant boundary ở tầng DB.
   FK đơn `child.parent_id -> parent.id` chỉ kiểm tra parent tồn tại. Với multi-tenant, nên cân nhắc composite FK `(tenant_id, parent_id) -> (tenant_id, id)` cho bảng tenant-scoped.

5. JSON array không enforce được FK.
   Các field như `lms_assignments.student_ids`, `lms_quizzes.question_ids`, `lms_quizzes.topic_ids` là `jsonb`; DB không thể tạo FK từng phần tử. Những quan hệ này cần bảng nối riêng nếu muốn integrity thật.

## 6. Nguyên tắc sửa đúng

Không nên thêm tất cả FK một lần. Với DB lớn và mục tiêu 10.000 user / 5.000 concurrent, nên migrate theo wave:

1. Audit orphan.
2. Cleanup hoặc seed bổ sung parent.
3. Thêm FK bằng `NOT VALID`.
4. Chạy app smoke/integration.
5. `VALIDATE CONSTRAINT` theo từng nhóm.
6. Bật test chặn regression để migration sau không quên FK.

Ví dụ chiến lược PostgreSQL:

```sql
ALTER TABLE user_sessions
ADD CONSTRAINT fk_user_sessions_user
FOREIGN KEY (user_id) REFERENCES users(id)
ON DELETE CASCADE
NOT VALID;

ALTER TABLE user_sessions VALIDATE CONSTRAINT fk_user_sessions_user;
```

`NOT VALID` giúp thêm constraint không quét toàn bộ bảng ngay tại thời điểm add. Bước validate có thể chạy sau khi đã kiểm soát dữ liệu và traffic.

## 7. Wave P0 nên làm trước

### 7.1 Auth/RBAC/User core

Các quan hệ bắt buộc:

```text
user_roles.user_id -> users.id
user_roles.role_id -> roles.id
role_permissions.role_id -> roles.id
role_permissions.permission_id -> permissions.id
profiles.user_id -> users.id
social_accounts.user_id -> users.id
certificates.user_id -> users.id
user_sessions.user_id -> users.id
user_permissions.user_id -> users.id
user_access_scopes.user_id -> users.id
user_access_scopes.education_unit_id -> education_units.id
work_shifts.user_id -> users.id
education_units.parent_id -> education_units.id
```

Gợi ý delete rule:

```text
user_roles / role_permissions: ON DELETE CASCADE
profiles / social_accounts / certificates / sessions: ON DELETE CASCADE hoặc RESTRICT tùy chính sách xóa user
user_activities / auth_login_attempts: ON DELETE SET NULL nếu giữ audit
education_units.parent_id: ON DELETE RESTRICT hoặc SET NULL
```

### 7.2 LMS runtime lõi

Các quan hệ bắt buộc:

```text
academic_classes.school_id -> education_units.id
academic_classes.homeroom_teacher_id -> users.id
lms_assignments.academic_class_id -> academic_classes.id
lms_assignments.quiz_id -> lms_quizzes.id
lms_assignments.subject_id -> lms_subjects.id
lms_assignments.assigned_by -> users.id
lms_levels.subject_id -> lms_subjects.id
lms_topics.level_id -> lms_levels.id
lms_questions.subject_id -> lms_subjects.id
lms_questions.level_id -> lms_levels.id
lms_questions.topic_id -> lms_topics.id
lms_quizzes.subject_id -> lms_subjects.id
lms_quizzes.level_id -> lms_levels.id
lms_students.school_id -> education_units.id
lms_students.academic_class_id -> academic_classes.id
lms_students.auth_user_id -> users.id
lms_attempts.assignment_id -> lms_assignments.id
lms_attempts.quiz_id -> lms_quizzes.id
lms_attempts.student_id -> lms_students.id
```

Gợi ý delete rule:

```text
subject/level/topic/question/quiz: ưu tiên RESTRICT hoặc soft delete, không cascade sâu.
class/student/assignment/attempt: ưu tiên RESTRICT hoặc soft delete; chỉ cascade khi record là thuần child và không cần audit.
attempt: thường không nên cascade mất lịch sử học tập.
```

### 7.3 Học liệu

Các quan hệ bắt buộc:

```text
hoclieu_taxonomy.parent_id -> hoclieu_taxonomy.id
hoclieu_resources.subject_id -> hoclieu_taxonomy.id
hoclieu_resources.category_id -> hoclieu_taxonomy.id
hoclieu_resources.section_id -> hoclieu_taxonomy.id
hoclieu_assets.resource_id -> hoclieu_resources.id
hoclieu_resource_items.resource_id -> hoclieu_resources.id
hoclieu_resource_items.asset_id -> hoclieu_assets.id
hoclieu_asset_slide_cache.asset_id -> hoclieu_assets.id
hoclieu_progress_state.user_id -> users.id
hoclieu_progress_state.school_id -> education_units.id
hoclieu_progress_state.resource_id -> hoclieu_resources.id
hoclieu_progress_state.asset_id -> hoclieu_assets.id
hoclieu_slide_annotations.user_id -> users.id
hoclieu_slide_annotations.asset_id -> hoclieu_assets.id
```

### 7.4 CRM school

Các quan hệ bắt buộc:

```text
crm_school_profiles.school_id -> education_units.id
crm_school_contacts.school_id -> education_units.id
crm_school_deals.school_id -> education_units.id
crm_school_deals.owner_user_id -> users.id
crm_school_activities.school_id -> education_units.id
crm_school_activities.deal_id -> crm_school_deals.id
crm_school_activities.actor_user_id -> users.id
crm_referrals.school_id -> education_units.id
crm_referrals.referrer_user_id -> users.id
```

### 7.5 Notification bổ sung

Notification đã có FK nội bộ, nhưng nên thêm:

```text
notification_recipients.user_id -> users.id
notification_device_registrations.user_id -> users.id
notification_preferences.user_id -> users.id
notification_delivery_attempts.notification_id -> notification_messages.id
notification_delivery_attempts.device_registration_id -> notification_device_registrations.id
```

Cần cleanup seed user trước vì hiện đang có orphan.

## 8. Quan hệ cần normalize, không nên để JSON nếu cần integrity

Các field này đang là `jsonb`, không ràng buộc được từng phần tử:

```text
lms_assignments.student_ids
lms_quizzes.question_ids
lms_quizzes.topic_ids
hoclieu_taxonomy.level_ids
announcements.academic_class_ids
announcements.student_ids
discussion_threads.attachment_ids
discussion_replies.attachment_ids
```

Nên tạo bảng nối:

```text
lms_assignment_recipients(tenant_id, assignment_id, student_id, status, assigned_at)
lms_quiz_questions(tenant_id, quiz_id, question_id, sort_order, points)
lms_quiz_topics(tenant_id, quiz_id, topic_id)
lms_announcement_classes(tenant_id, announcement_id, academic_class_id)
lms_announcement_students(tenant_id, announcement_id, student_id)
lms_discussion_attachments(thread_id/reply_id, attachment_id)
```

Giữ JSON chỉ nên dùng cho snapshot/render/cache, không nên làm source of truth cho quan hệ nghiệp vụ quan trọng.

## 9. Test/guard nên bổ sung

1. Test đọc migration để đảm bảo các bảng P0 có FK.
2. Integration test dùng PostgreSQL container hoặc DB test thật để query `information_schema`.
3. Orphan detector chạy trong CI trước khi validate FK.
4. Test delete rule theo nghiệp vụ: xóa/disable user, xóa lớp, archive quiz, revoke device.
5. GraphQL/read-model test: không trả record có parent đã soft-deleted/inactive nếu nghiệp vụ không cho phép.

## 10. Đề xuất next step

Nên làm một migration mới theo hướng:

```text
V11__relationship_integrity_cleanup.sql
V12__core_relationship_foreign_keys_not_valid.sql
V13__lms_hoclieu_relationship_foreign_keys_not_valid.sql
V14__crm_notification_relationship_foreign_keys_not_valid.sql
V15__validate_relationship_foreign_keys.sql
```

Trong đó V11 xử lý orphan trước:

```text
academic_classes.homeroom_teacher_id
lms_assignments.assigned_by
lms_students.auth_user_id
notification_recipients.user_id
notification_device_registrations.user_id
notification_preferences.user_id
```

Sau khi V11 sạch, mới add FK. Nếu cần giữ seed demo hiện tại, tạo thêm các user seed tương ứng thay vì set null.

