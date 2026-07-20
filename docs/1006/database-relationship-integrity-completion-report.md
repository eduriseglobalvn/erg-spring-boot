# Bao cao hoan tat rang buoc quan he du lieu ERG Spring

Ngay cap nhat: 2026-06-10  
Pham vi: `D:\ERG\erg-spring`, PostgreSQL migrations sau `V10__firebase_push_notifications.sql`.

## 1. Ket luan

Da bo sung lop rang buoc quan he du lieu o DB cho cac bang nghiep vu chinh thay vi chi de quan he logic trong source/JPA. Chuoi migration hien tai khong con trung version Flyway va dang theo thu tu:

```text
V11__relationship_integrity_seed_cleanup.sql
V12__core_relationship_foreign_keys_not_valid.sql
V13__lms_relationship_foreign_keys_not_valid.sql
V14__hoclieu_relationship_foreign_keys_not_valid.sql
V15__crm_notification_relationship_foreign_keys_not_valid.sql
V16__validate_relationship_foreign_keys.sql
```

Tong so FK moi duoc khai bao: `80`.

```text
Core/RBAC/User/Org: 23
LMS: 19
HocLieu: 19
CRM/Notification: 19
Validate trong V16: 80/80
```

Cap nhat sau yeu cau reset ngay 2026-06-10: da reset schema `public` tren PostgreSQL dang cau hinh trong `.env` (`defaultdb` tren Aiven), migrate lai toan bo `V1..V16`, va seed lai du lieu bootstrap.

## 2. File da them/sua

- `src/main/resources/db/migration/postgres/V11__relationship_integrity_seed_cleanup.sql`
- `src/main/resources/db/migration/postgres/V12__core_relationship_foreign_keys_not_valid.sql`
- `src/main/resources/db/migration/postgres/V13__lms_relationship_foreign_keys_not_valid.sql`
- `src/main/resources/db/migration/postgres/V14__hoclieu_relationship_foreign_keys_not_valid.sql`
- `src/main/resources/db/migration/postgres/V15__crm_notification_relationship_foreign_keys_not_valid.sql`
- `src/main/resources/db/migration/postgres/V16__validate_relationship_foreign_keys.sql`
- `src/main/java/com/erg/ergspringboot/service/lms/LmsAccountBootstrapSeedService.java`
- `src/test/java/com/erg/ergspringboot/config/DatabaseRelationshipMigrationContractTest.java`
- `src/test/java/com/erg/ergspringboot/config/DatabaseRelationshipConstraintIntegrationTest.java`
- `src/test/java/com/erg/ergspringboot/config/DatabaseReseedRunnerTest.java`

## 3. Nhom quan he da rang buoc

### Core, auth, RBAC, organization

Da rang buoc cac quan he quan trong:

- `user_roles.user_id -> users.id`
- `user_roles.role_id -> roles.id`
- `role_permissions.role_id -> roles.id`
- `role_permissions.permission_id -> permissions.id`
- `permissions.group_name -> permission_groups.name`
- `education_units.parent_id -> education_units.id`
- `education_units.owner_user_id -> users.id`
- `education_units.created_by -> users.id`
- `user_sessions.user_id -> users.id`
- `auth_login_attempts.user_id -> users.id`
- `profiles.user_id -> users.id`
- `social_accounts.user_id -> users.id`
- `certificates.user_id -> users.id`
- `user_activities.user_id -> users.id`
- `user_activities.target_user_id -> users.id`
- `user_permissions.user_id -> users.id`
- `user_permissions.permission -> permissions.name`
- `user_permissions.created_by -> users.id`
- `user_access_scopes.user_id -> users.id`
- `user_access_scopes.education_unit_id -> education_units.id`
- `work_shifts.user_id -> users.id`
- `work_shifts.school_id -> education_units.id`
- `work_shifts.confirmed_by -> users.id`

### LMS

Da rang buoc lop hoc, bai giao, quiz, cau hoi, hoc sinh va attempt:

- `academic_classes.school_id -> education_units.id`
- `academic_classes.homeroom_teacher_id -> users.id`
- `lms_assignments.academic_class_id -> academic_classes.id`
- `lms_assignments.quiz_id -> lms_quizzes.id`
- `lms_assignments.subject_id -> lms_subjects.id`
- `lms_assignments.assigned_by -> users.id`
- `lms_levels.subject_id -> lms_subjects.id`
- `lms_topics.level_id -> lms_levels.id`
- `lms_questions.subject_id -> lms_subjects.id`
- `lms_questions.level_id -> lms_levels.id`
- `lms_questions.topic_id -> lms_topics.id`
- `lms_quizzes.subject_id -> lms_subjects.id`
- `lms_quizzes.level_id -> lms_levels.id`
- `lms_students.school_id -> education_units.id`
- `lms_students.academic_class_id -> academic_classes.id`
- `lms_students.auth_user_id -> users.id`
- `lms_attempts.assignment_id -> lms_assignments.id`
- `lms_attempts.quiz_id -> lms_quizzes.id`
- `lms_attempts.student_id -> lms_students.id`

### HocLieu

Da rang buoc taxonomy, resource, asset, slide cache, progress va annotation:

- `hoclieu_taxonomy.parent_id -> hoclieu_taxonomy.id`
- `hoclieu_taxonomy.subject_id -> hoclieu_taxonomy.id`
- `hoclieu_taxonomy.category_id -> hoclieu_taxonomy.id`
- `hoclieu_resources.subject_id -> hoclieu_taxonomy.id`
- `hoclieu_resources.category_id -> hoclieu_taxonomy.id`
- `hoclieu_resources.section_id -> hoclieu_taxonomy.id`
- `hoclieu_resources.created_by -> users.id`
- `hoclieu_resources.updated_by -> users.id`
- `hoclieu_assets.resource_id -> hoclieu_resources.id`
- `hoclieu_resource_items.resource_id -> hoclieu_resources.id`
- `hoclieu_resource_items.asset_id -> hoclieu_assets.id`
- `hoclieu_asset_slide_cache.asset_id -> hoclieu_assets.id`
- `hoclieu_progress_state.user_id -> users.id`
- `hoclieu_progress_state.school_id -> education_units.id`
- `hoclieu_progress_state.resource_id -> hoclieu_resources.id`
- `hoclieu_progress_state.asset_id -> hoclieu_assets.id`
- `hoclieu_slide_annotations.user_id -> users.id`
- `hoclieu_slide_annotations.school_id -> education_units.id`
- `hoclieu_slide_annotations.asset_id -> hoclieu_assets.id`

### CRM va notification

Da rang buoc CRM school pipeline va cac lien ket notification toi user/message/device:

- `crm_school_profiles.school_id -> education_units.id`
- `crm_school_profiles.owner_user_id -> users.id`
- `crm_school_profiles.lead_owner_user_id -> users.id`
- `crm_school_profiles.manager_user_id -> users.id`
- `crm_school_profiles.introduced_by_user_id -> users.id`
- `crm_school_contacts.school_id -> education_units.id`
- `crm_school_deals.school_id -> education_units.id`
- `crm_school_deals.owner_user_id -> users.id`
- `crm_school_activities.school_id -> education_units.id`
- `crm_school_activities.deal_id -> crm_school_deals.id`
- `crm_school_activities.actor_user_id -> users.id`
- `crm_referrals.school_id -> education_units.id`
- `crm_referrals.referrer_user_id -> users.id`
- `notification_messages.created_by -> users.id`
- `notification_recipients.user_id -> users.id`
- `notification_device_registrations.user_id -> users.id`
- `notification_preferences.user_id -> users.id`
- `notification_delivery_attempts.notification_id -> notification_messages.id`
- `notification_delivery_attempts.device_registration_id -> notification_device_registrations.id`

## 4. Cleanup va seed data truoc khi validate

`V11` lam cac viec chinh:

- Seed parent user bi thieu cho LMS/notification demo: `usr_teacher_ba`, `usr_teacher_cl`, `usr_stu_ba_0001`, `usr_stu_cl_0001`.
- Seed `usr_relationship_placeholder` de giu CRM deal trong truong hop owner user cu khong ton tai.
- Tao role/scope/profile co dieu kien `WHERE EXISTS` va `WHERE NOT EXISTS`, dam bao migration idempotent.
- Xoa cac child row khong con parent hop le khi child khong co y nghia neu dung mot minh.
- Set `NULL` cho optional reference/audit reference khi parent khong ton tai.
- Cleanup them notification outbox va topic membership de khong bi fail validate khi device/message bi xoa.

Sau khi reset DB, da phat hien va sua them loi seed service: `LmsAccountBootstrapSeedService` cap nhat permission `profile.self` sang group `profile`, trong khi migration seed group hop le la `users`. Khi FK `fk_permissions_group_name` duoc bat, seed bi chan dung. Da sua service dung group `users`, khop voi `V4__enterprise_reference_seed.sql`.

Da them `DatabaseReseedRunnerTest` co guard:

```powershell
.\mvnw.cmd "-Dtest=DatabaseReseedRunnerTest" "-Derg.reseed.devdb=true" test
```

Runner nay chi chay khi bat system property `erg.reseed.devdb=true`, tranh viec CI/unit test vo tinh ghi vao DB dang cau hinh.

## 5. Diem loi

- DB bat dau enforce integrity that, khong con chi dua vao convention `*_id` trong code.
- Giam rui ro orphan data cho user, role, permission, school, class, assignment, attempt, hoc lieu, CRM va notification.
- `NOT VALID` giup them constraint theo cach than thien hon voi bang lon; buoc validate duoc tach rieng de de kiem soat rollout.
- Co test contract ngan viec them/sua migration ma quen validate constraint.
- Integration test co guard de chay voi PostgreSQL that khi co Docker hoac `ERG_TEST_POSTGRES_URL`.

## 6. Diem can khac phuc tiep

- Chua normalize cac quan he dang nam trong JSON/JSONB, nen DB chua the FK tung phan tu:
  - `lms_assignments.student_ids`
  - `lms_quizzes.question_ids`
  - `lms_quizzes.topic_ids`
  - cac danh sach ID tuong tu trong announcement/discussion neu co.
- Chua enforce tenant boundary bang composite FK. FK hien tai dam bao parent ton tai, nhung chua dam bao child va parent cung `tenant_id`.
- Chua chay Flyway migrate tren PostgreSQL test that trong turn nay vi Docker/Testcontainers khong available tren may.
- Can chay dry-run/staging voi snapshot DB that de xem co orphan nao ngoai seed demo hay khong. Neu co, `V16 VALIDATE CONSTRAINT` se fail ro constraint nao can sua.
- Nen bo sung bang noi cho assignment recipients va quiz questions/topics neu cac quan he nay la source of truth.

## 7. Ket qua reset, seed va test

Da reset schema va migrate lai bang Flyway Maven plugin:

```powershell
.\mvnw.cmd org.flywaydb:flyway-maven-plugin:11.14.1:clean ...
.\mvnw.cmd org.flywaydb:flyway-maven-plugin:11.14.1:migrate ...
```

Ket qua: schema `public` duoc recreate, Flyway len version `16`.

Da seed lai bootstrap bang:

```powershell
.\mvnw.cmd "-Dtest=DatabaseReseedRunnerTest" "-Derg.reseed.devdb=true" test
```

Ket qua: pass, `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`.

Runner da assert toi thieu:

```text
users >= 6
education_units >= 4
academic_classes >= 14
lms_students >= 2
hoclieu_taxonomy >= 8
hoclieu_resources >= 4
hoclieu_assets >= 4
```

Da chay contract test:

```powershell
.\mvnw.cmd -Dtest=DatabaseRelationshipMigrationContractTest test
```

Ket qua: pass, `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`.

Da chay integration test tren PostgreSQL that bang datasource Aiven vua reset:

```powershell
.\mvnw.cmd "-Dtest=DatabaseRelationshipMigrationContractTest,DatabaseRelationshipConstraintIntegrationTest" `
  "-Derg.test.postgres.url=<SPRING_DATASOURCE_URL>" `
  "-Derg.test.postgres.username=<SPRING_DATASOURCE_USERNAME>" `
  "-Derg.test.postgres.password=<SPRING_DATASOURCE_PASSWORD>" test
```

Ket qua: pass, integration test khong bi skip:

```text
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
```

Da check them bang script:

```text
NO_DUPLICATE_MIGRATION_VERSIONS
DECLARED=80
VALIDATED=80
MISSING=0
EXTRA=0
```

## 8. Khuyen nghi rollout

1. Chay migrations tren PostgreSQL staging co du lieu giong production.
2. Neu `V16` fail, khong drop DB; doc constraint fail, audit orphan cua bang do, cleanup co chu dich.
3. Sau khi staging pass, moi apply len production trong maintenance window nho.
4. Theo doi query latency tren cac bang lon sau khi them FK/index.
5. Lap phase 2 cho normalization JSON arrays va composite tenant FK.

## 9. Lenh reset-seed chuan

Da them runbook rieng:

```text
docs/1006/reset-and-seed-postgres-runbook.md
```

Lenh chay khi can xoa schema cu va dung lai bang moi co relationship:

```powershell
.\scripts\reset-and-seed-postgres.ps1 -Confirmation I_UNDERSTAND_THIS_DELETES_DATA
```
