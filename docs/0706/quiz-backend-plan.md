# Kế hoạch thiết kế Backend cho Quiz (Authoring + Runtime + Report)

> **Phiên bản:** 2026-07-06
> **Phạm vi:** Toàn vòng đời — Authoring (soạn quiz, LCMS `/quiz-editor` + `/questions` + `/quiz-bank`) + Phân phối (LMS giao bài tập, hoạt động học tập, kho luyện tập, tiến độ) + Runtime (làm bài/chấm) + Report (báo cáo).
> **DB:** PostgreSQL. **Transport:** GraphQL cho đọc (LCMS authoring/report) + REST cho ghi và runtime.
> **Grading:** Chuẩn bị cả `client-first` và `server-authoritative`.
> **Đối tượng:** Tài liệu spec cho đội Backend triển khai. Đây KHÔNG phải code.

---

## Context — Tại sao có tài liệu này

FE đã có sẵn một trình soạn Quiz (`/quiz-editor`) rất giàu tính năng nhưng **hiện chạy 100% bằng mock client-side** — chưa có API lưu/tải. Cần một tài liệu thiết kế BE **chi tiết, ràng buộc rõ ràng** để đội BE (PostgreSQL) triển khai, bám sát đúng UI hiện tại.

Những dữ kiện đã xác minh từ code FE:

- **14 dạng câu hỏi** với cấu trúc đáp án khác nhau (`quiz-editor-types.ts` dùng kebab-case cho authoring, `src/lib/types.ts` dùng snake_case cho runtime).
- Editor state (`use-quiz-editor-state.ts`) lưu cây lồng nhau `groups → slides`, mỗi slide có `options`, `textStyles`, `feedbackRows`, `media`… — hiện chỉ là mock factory (`mock-quiz-editor-project.ts`).
- **Media đang lưu base64 data URL** (`question-editor-dialog.tsx` dùng `FileReader.readAsDataURL`) — BE thật phải chuyển sang object storage.
- Đã có 2 transport sẵn: **GraphQL** cho LCMS authoring (`graphql-client.ts`, `question-bank-api.ts`, `GraphQlPage<T>`; portal suy từ URL path, tenant từ session — **không tin client header**) và **REST** envelope `{data,error,success}` cho runtime (`api-client.ts`).
- Contract runtime (`docs/backend-assessment-contract.md`) đã định nghĩa QuizPackage/Attempt/sync/idempotency. **Khoảng trống là phía authoring/creation.**

Quyết định đã chốt:
1. Phạm vi: **Authoring + Phân phối (LMS) + Runtime + Report** (toàn vòng đời).
2. Transport authoring: **GraphQL cho đọc, REST cho ghi**.
3. Grading: **chuẩn bị cả client-first & server-authoritative**.
4. API phải phục vụ đồng thời: LCMS `/quiz-bank` + `/questions` (đã render qua GraphQL workspace, chỉ thay mock bằng bảng thật) VÀ LMS (danh sách hoạt động học tập, giao bài tập chứa danh sách bài + câu hỏi, kho luyện tập, tiến độ học tập) — chi tiết ở §1.9, §4.1, §4.7–4.8.

---

## 0. Nguyên tắc chủ đạo: tách DRAFT / PUBLISHED

Hai chế độ lưu trữ, một vòng đời:

- **DRAFT (mutable, nặng JSONB).** Phản chiếu cây editor 1:1 (`QuizProjectSettings` + `QuizEditorGroup[]` + `QuizEditorSlide[]`). Author sửa liên tục qua autosave. **Không** normalize từng field slide (phần lớn là trình bày: `textStyles`, `elementOffsets`, `layoutPreset`, `animationPreset`). Lưu cả cây dưới dạng JSONB; chỉ giữ cột routing/index ở dạng quan hệ.
- **PUBLISHED (immutable, normalized + compiled).** Khi publish: đóng băng snapshot `quiz_versions` VÀ compile runtime `Quiz` (snake `QuestionKind`) vào bảng chuẩn hóa + tạo `QuizPackage` đã ký. Phía này phục vụ runtime/grading/report → phải index được và không bao giờ đổi.

Lý do: I/O authoring là whole-document, tần suất cao → JSONB thắng. Đọc runtime/report có lọc, tổng hợp, ổn định → quan hệ thắng. Compile là cầu một chiều.

---

## 1. Lược đồ PostgreSQL

**Quy ước:** `id` UUID (v7 cho index locality); mọi bảng tenant có `tenant_id text not null default 'erg'`; timestamp `timestamptz`; soft-delete `deleted_at` chỉ ở bảng authoring; blob linh hoạt = `jsonb`. Bật **RLS** theo `tenant_id = current_setting('app.tenant_id')`. Session set `app.tenant_id`/`app.user_id`/`app.portal` từ phiên đăng nhập — **không lấy từ client header** (đúng ghi chú trong `graphql-client.ts`).

### 1.1 Tenancy / scope
- `tenants(id text pk, name, created_at)` — seed `'erg'`.
- Portal enum `crm|elearning|lcms|lms`; bảng authoring `portal='lcms'`.

### 1.2 Authoring (DRAFT)

**`quizzes`** — danh tính quiz xuyên suốt các version:

| col | type | ghi chú |
|---|---|---|
| id | uuid pk | = runtime `quizId` |
| tenant_id | text | RLS |
| portal | text | 'lcms' |
| title | text | denormalize từ `settings.info.title` để search |
| subtitle | text | runtime `subtitle` (vd TRAINING) |
| kind | text | 'train' \| 'test' |
| subject_id / level_id / category_id | uuid null | filterable |
| status | text | draft \| reviewing \| ready \| published \| archived |
| current_version_id | uuid fk→quiz_versions null | version active mới nhất |
| latest_draft_id | uuid fk→quiz_drafts | draft đang làm |
| owner_user_id | uuid | |
| content_scope | jsonb | FE `ContentScope` |
| created_at/updated_at/deleted_at | timestamptz | |

Index: `(tenant_id,portal,status)`, `(tenant_id,subject_id,level_id,category_id)`, GIN tsvector(`title`), `(tenant_id,updated_at desc)`.

**`quiz_drafts`** — bản làm việc mutable:

| col | type | ghi chú |
|---|---|---|
| id | uuid pk | |
| quiz_id | uuid fk | |
| tenant_id | text | |
| settings | jsonb | full `QuizProjectSettings` |
| player_template | jsonb | `QuizPlayerTemplate` |
| tree | jsonb | `QuizEditorGroup[]` (groups→slides) |
| schema_version | int | phiên bản schema của `settings`/`tree` (xem §11) — tăng khi đổi cấu trúc blob |
| doc_version | bigint | đếm optimistic-lock, tăng mỗi write |
| base_version_id | uuid fk→quiz_versions null | version đã fork ra |
| updated_by | uuid | |
| updated_at | timestamptz | |

> **Quyết định:** draft slide **KHÔNG normalize làm nguồn sự thật** — giữ nguyên 1 document JSONB. Thêm GIN(`tree` jsonb_path_ops) để truy vấn bank-usage (`tree @> '{...bankQuestionId...}'`). Chỉ thêm bảng chiếu nhẹ `quiz_draft_slides(draft_id, slide_id, group_id, kind, bank_question_id, order_index)` NẾU truy vấn bank-usage trở nên nóng — đây là index dẫn xuất, không phải nguồn sự thật.

### 1.3 Versioning

**`quiz_versions`** — snapshot publish immutable:

| col | type | ghi chú |
|---|---|---|
| id | uuid pk | |
| quiz_id | uuid fk | |
| tenant_id | text | |
| version_label | text | 'v1','v2'… → runtime `Quiz.version` |
| version_number | int | tăng đều theo quiz |
| authoring_snapshot | jsonb | đóng băng draft {settings,player_template,tree} để fork/audit |
| runtime_quiz | jsonb | Quiz compiled, KHÔNG chứa key — payload an toàn |
| answer_key | jsonb | đáp án đúng theo questionId — **BÍ MẬT** |
| grading_mode | text | client-first \| server-authoritative |
| schema_version | int | **phiên bản schema của blob** (runtime_quiz/answer_key) — xem §11 |
| content_hash | text | sha256 trên canonical(runtime_quiz + answer_key + assets manifest) |
| published_by | uuid | |
| published_at | timestamptz | |
| is_active | bool | 1 active/quiz |

Ràng buộc: `unique(quiz_id,version_number)`, `unique(quiz_id,version_label)`, partial unique `(quiz_id) where is_active`. **INSERT-only**; chỉ UPDATE duy nhất là lật `is_active`.

> `schema_version` là chốt tiến hóa model (§11): mọi blob JSONB (`runtime_quiz`, `answer_key`, `authoring_snapshot`) đều mang số phiên bản schema để reader biết cách đọc — thêm/bớt field về sau không cần migrate lại toàn bộ dòng cũ.

**`quiz_packages`** — gói đã compile + ký (đúng shape contract):

| col | type | ghi chú |
|---|---|---|
| id | text pk | `pkg_{quizId}_{versionLabel}_{hashPrefix}` |
| quiz_id | uuid | |
| quiz_version_id | uuid fk | |
| quiz_version | text | echo label |
| tenant_id | text | |
| content_hash | text | |
| signature | text | base64 canonical payload |
| public_key_id | text | vd 'erg-prod-key-2026-01' |
| grading_mode | text | |
| schema_version | int | phiên bản schema của `package_json` (§11) |
| package_format | text | `single` (1 quiz) \| `bundle` (nhiều quiz, portable §12) |
| assets_manifest | jsonb | danh sách media {mediaId, url, sha256, byteSize, mime} để tải offline |
| package_json | jsonb | `QuizPackage.quiz` (không key khi testing) |
| generated_at / expires_at | timestamptz | |

Index `(quiz_id,quiz_version)`, `(tenant_id,quiz_id)`. Immutable — mục tiêu cache TTL dài (§7).

> `assets_manifest` là bắt buộc cho desktop portable (§12): app phải biết toàn bộ media cần prefetch để chạy offline. `package_format='bundle'` cho phép gói nhiều quiz vào một file `.ergpack` mang đi.

### 1.4 Bảng câu hỏi PUBLISHED chuẩn hóa (report/grading)

**`quiz_version_sections`** — `id, quiz_version_id fk, title, order_index`.

**`quiz_version_questions`**:

| col | type | ghi chú |
|---|---|---|
| id | uuid pk | |
| quiz_version_id / section_id | uuid fk | |
| runtime_question_id | text | `Question.id` |
| source_slide_id | text | truy vết editor |
| bank_question_id | uuid null | nếu lấy từ bank |
| kind | text | snake `QuestionKind` |
| authoring_type | text | kebab, audit mapping |
| points | numeric(8,2) | |
| is_auto_gradable | bool | false cho essay; likert là survey |
| grading_policy | text | auto \| manual \| survey |
| order_index | int | |
| body | jsonb | runtime `Question` trừ key |
| answer_key | jsonb | đáp án đúng của câu này |

Index `(quiz_version_id,order_index)`, `(kind)`, `(bank_question_id)`, `(quiz_version_id,is_auto_gradable)`.

> 14 loại body dị thể giữ JSONB (chuẩn hóa choices/matching/blanks thành 14 bảng con là over-engineering; luôn fetch nguyên khối để render/chấm, không lọc theo giá trị bên trong). Nhưng `kind`/`points`/`grading_policy`/`bank_question_id` là cột thật vì report tổng hợp trên chúng.

### 1.5 Ngân hàng câu hỏi (chính thức hóa entity đang chạy)
- `question_bank_subjects(id, tenant_id, label, description, company_scope_label, created_at)`.
- `question_bank_levels(id, subject_id fk, label, description, order_index)`.
- `question_bank_categories(id, subject_id fk, level_id fk, label)`.
- `question_bank_questions(id, tenant_id, subject_id/level_id/category_id fk, scope jsonb, type kebab, difficulty core|stretch|challenge, status ready|reviewing|pilot, stem, objective, tags text[], content jsonb (body+key), mastery_rate, usage_count, schools_using, owner_user_id, created_at/updated_at)`. Index `(tenant_id,subject_id,level_id,category_id)`, `(tenant_id,type,status)`, GIN(tags), tsvector(stem). Phục vụ trực tiếp `LcmsQuestionBankWorkspace` đang chạy.
- `quiz_bank_items(id, tenant_id, title, kind train|test, status, subject_id, level_id, question_ids uuid[], source_mode manual|auto-random, duration_label, owner_user_id, updated_at)`. GIN cho array containment.

**Liên kết bank ↔ quiz = tham chiếu ở draft, copy khi publish.** Ở draft, slide lấy từ bank giữ `bankQuestionId` (tham chiếu mềm); sửa nội dung fork cục bộ (đúng như `question-bank-to-quiz.ts` đang copy field vào slide). Khi publish, `quiz_version_questions.body` là **snapshot đóng băng**, vẫn giữ `bank_question_id` để truy nguồn + `usage_count++`. Đảm bảo đề đã publish không đổi khi ai đó sửa câu hỏi bank sau này.

### 1.6 Media

**`media_assets`** — `id, tenant_id, sha256, storage_key, url, mime_type, byte_size, width, height, original_name, uploaded_by, created_at, ref_count`. Unique `(tenant_id,sha256)` → dedup. Slide tham chiếu `media_assets.id`/`url` thay cho base64 (thay thế đường `FileReader.readAsDataURL`).

**`media_references`** — `(media_id fk, owner_type, owner_id, tenant_id)`, owner_type ∈ {draft_slide, quiz_version, bank_question}. Điều khiển `ref_count` + GC orphan.

### 1.7 Attempt / runtime (nhất quán contract)

**`attempts`** — 1 dòng/attempt, upsert theo `id` (FE có thể tự sinh id ở client-first):

| col | type | ghi chú |
|---|---|---|
| id | uuid pk | client- hoặc server-sinh |
| tenant_id / quiz_id | | |
| quiz_version_id | uuid fk | giải từ package → ghim version |
| package_id | text fk | |
| student_user_id | uuid | |
| class_id / school_id | uuid null | scope report/authz |
| mode | text | training \| testing |
| grading_mode | text | |
| status | text | not_started \| in_progress \| submitted |
| submitted_count / total_questions | int | |
| total_score / max_score / percent | numeric | |
| passed | bool null | |
| started_at / submitted_at / last_active_at | timestamptz | |
| client_runtime | text | web \| tauri |
| answers | jsonb | `Record<questionId, AnswerRecord>` |

Index `(tenant_id,quiz_id,quiz_version_id)`, `(student_user_id,quiz_id)`, `(class_id,quiz_id)`, `(quiz_id,submitted_at)` cho leaderboard.

**`attempt_events`** — append-only, idempotent theo `event.id`:

| col | type | ghi chú |
|---|---|---|
| id | uuid pk | = `AttemptEvent.id` (idempotency) |
| attempt_id | uuid fk | |
| tenant_id / quiz_id / package_id | | |
| type | text | attempt_started \| answer_graded \| attempt_submitted \| sync_failed \| sync_succeeded |
| question_id | text null | |
| answer | jsonb null | raw `AnswerPayload` |
| result | jsonb null | `AnswerResult` |
| meta | jsonb null | |
| created_at | timestamptz | đồng hồ client |
| received_at | timestamptz | đồng hồ server |

`insert … on conflict (id) do nothing` → replay không nhân đôi điểm. Index `(attempt_id, created_at)`; chấp nhận out-of-order.

**`attempt_answers`** — kết quả chấm chuẩn hóa từng câu (nguồn report):

| col | type | ghi chú |
|---|---|---|
| attempt_id + quiz_version_question_id | composite pk | |
| tenant_id | text | |
| question_kind | text | denormalize cho report |
| raw_answer | jsonb | `AnswerPayload` |
| correct | bool null | null cho essay-pending/likert |
| partially_right | bool | |
| awarded_points / max_points | numeric | |
| graded_by | text | client \| server \| teacher |
| graded_at | timestamptz | |
| needs_manual_grade | bool | essay true đến khi chấm |

Index `(quiz_version_question_id)`, `(attempt_id)`.

**`student_progress`** — projection cho dashboard live giáo viên (shape `StudentProgress`): `id, tenant_id, student_user_id, student_name, quiz_id, quiz_title, mode, status, current_question_index, current_question_title, answered_count, total_questions, percent_complete, score, passed, started_at, submitted_at, last_active_at`. Index `(quiz_id,status)`, `(class_id,quiz_id)`.

### 1.8 Bảng tổng hợp report (materialized bằng bảng thường, recompute sau submit)
- `report_quiz_stats` — theo quiz_version: attempts_count, submitted_count, avg_percent, pass_rate, avg_duration_sec, score_histogram jsonb.
- `report_question_stats` — theo quiz_version_question: attempts, correct_count, partial_count, avg_awarded, p_value (độ khó), discrimination, distractor_distribution jsonb.
- `report_leaderboard` — theo (quiz_id, quiz_version_id, student): best_percent, best_score, best_attempt_id, rank. Index `(quiz_id,quiz_version_id,best_percent desc)`.

> Dùng bảng thường + upsert tăng dần khi submit (KHÔNG materialized view — REFRESH toàn bộ không scale theo mỗi submit).

### 1.9 LMS — Giao bài tập, Hoạt động học tập, Kho luyện tập, Tiến độ

> Các màn FE cần phục vụ: LMS `/homework` (danh sách hoạt động học tập), `/homework/assign` (giao bài tập — 2 bước: cấu hình + chọn đối tượng & tài nguyên), `/homework/exercise-bank` (kho luyện tập), `/homework/progress?assignmentId=` (tiến độ theo bài), `/homework/class` + `/homework/student-groups` (quản lý lớp/nhóm), và elearning `/student` (dashboard học sinh). Đây là lớp **phân phối** quiz đã publish tới học sinh — tách bạch với lớp authoring (§1.2–1.4). Một quiz publish (LCMS) → được giao thành nhiều `assignment` (LMS) → sinh `attempt` (§1.7) theo từng học sinh.

**Kho luyện tập = view trên quiz đã publish.** FE `Exercise`/`AssignmentCatalogItem` (fields: `title, subject, level, type quiz|practice|project|exam, difficulty, status, questionCount, durationMinutes, tags, assignedCount, kind train|test, subjectId, levelId, topicId, program`) **không phải bảng mới** — nó là chiếu từ `quizzes` + `quiz_versions.current`. `questionCount`/`durationMinutes` lấy từ version active; `assignedCount` đếm từ `assignments`. Chỉ quiz `status='published'` mới xuất hiện trong kho (trừ khi lọc draft/archived).

**`assignments`** — bài tập được giao (nguồn: `AssignmentRun` + `LmsAssignment`):

| col | type | ghi chú |
|---|---|---|
| id | uuid pk | |
| tenant_id | text | RLS |
| portal | text | 'lms' |
| title | text | tên bài giao |
| subject_id / subject_label | uuid / text | |
| target_level | text | vd "Khối 6" |
| assigned_by | uuid | teacher user id |
| recipient_mode | text | `class` \| `grade` \| `group` \| `individual` |
| attempt_limit | int | 1–20 (`oneAttemptOnly` ↔ 1) |
| max_duration_minutes | int | 1–240 |
| start_at / due_at | timestamptz | mở / hạn nộp |
| status | text | `draft` \| `active` \| `closed` \| `overdue` |
| created_at / updated_at | timestamptz | |

Index `(tenant_id, portal, status)`, `(assigned_by)`, `(due_at)`, `(subject_id, target_level)`.

**`assignment_resources`** — nhiều quiz/bài trong một assignment (`selectedResourceIds` là **danh sách**, không phải 1 quiz):

| col | type | ghi chú |
|---|---|---|
| assignment_id | uuid fk | |
| quiz_id | uuid fk→quizzes | bài được giao |
| quiz_version_id | uuid fk→quiz_versions | **ghim version lúc giao** (đề không đổi khi tác giả publish version mới) |
| order_index | int | thứ tự trong bài giao |

PK `(assignment_id, quiz_id)`. Ghim `quiz_version_id` khi giao là ràng buộc then chốt: attempt của học sinh (§1.7 `attempts.quiz_version_id`) phải khớp version đã giao, không trôi theo publish sau đó.

**`assignment_recipients`** — resolve mọi `recipient_mode` về danh sách phẳng học sinh (FE bắt `selectedStudentIds` / `selectedGradeClassIds` / `selectedAssignmentGroupIds` tuỳ mode; BE quy về `student_user_id`):

| col | type | ghi chú |
|---|---|---|
| assignment_id | uuid fk | |
| student_user_id | uuid | |
| source_type | text | `class` \| `grade` \| `group` \| `individual` (dấu vết chọn từ đâu) |
| source_id | uuid null | class_id / grade / group_id gốc |
| missing | bool | chưa mở khi quá hạn |
| late | bool | nộp sau hạn |

PK `(assignment_id, student_user_id)`. Index `(student_user_id)` (để render bài của học sinh ở portal elearning), `(assignment_id, missing)`.

**`student_groups`** + **`student_group_members`** — nhóm học sinh định sẵn (nguồn `assign-homework-groups.ts`): `student_groups(id, tenant_id, name, color, source_label, owner_user_id, created_at)`; `student_group_members(group_id fk, student_user_id, PK cả hai)`. Dùng để giao bài theo nhóm và lọc ở màn quản lý lớp.

**Lớp/trường** dùng lại bảng danh mục sẵn có của hệ thống (school/class/student) — tài liệu này chỉ tham chiếu bằng `class_id`/`school_id`. Các chỉ số hiển thị (`completionRate`, `averageScore`, `riskStudents`, phân loại A–E theo `averageScore`) là **tính toán/projection**, không lưu cứng: suy từ `attempts` + `assignment_recipients` qua bảng report (§1.8) hoặc query trực tiếp có cache ngắn.

**Tiến độ học tập** không cần bảng mới ngoài `attempts` + `assignment_recipients` + `student_progress` (§1.7). Trạng thái UI (`completed|inprogress|notstarted`) suy từ attempt: có `submittedAt`/`passed`/`percent=100` → completed; có `startedAt`/status doing → inprogress; recipient không có attempt → notstarted. BE nên trả sẵn cả `latestAttempt` và `bestAttempt` cho mỗi recipient (đúng `LmsAssignmentRecipient`) để FE không phải suy luận.

---

## 2. Vòng đời authoring & versioning

```
create → quiz_drafts (doc_version=0)
  │ autosave (PATCH tree/settings, doc_version++, optimistic lock)
validate (server chạy luật §8) → errors[]  (phải pass)
publish → compile(draft) → {runtime_quiz, answer_key, content_hash}
  │ INSERT quiz_versions (version_number=max+1, is_active, lật cái cũ)
  │ INSERT quiz_version_sections/questions (chuẩn hóa)
  │ INSERT quiz_packages (ký payload)
  │ UPDATE quizzes.current_version_id, status='published'
  │ bump bank usage_count
edit published → fork: quiz_drafts mới, base_version_id = version active,
                 seed từ quiz_versions.authoring_snapshot
                 (version đã publish giữ nguyên immutable & live)
```

Publish không bao giờ sửa version cũ. Học sinh đang làm bài giữ nguyên `package_id`/version (attempt ghim `quiz_version_id`). `version_label` tự tăng `vN` (cho phép override thủ công). Draft→version là **một transaction**; ký (sign) có thể async nhưng dòng version sở hữu `content_hash`.

---

## 3. Ánh xạ compile Authoring → Runtime

Slide không phải câu hỏi (`info-slide`, `result-slide`, `intro-slide`, `user-info`, `instruction-slide`) rớt khỏi danh sách câu hỏi runtime → gộp vào cấu hình result/intro cấp quiz hoặc metadata player. Group → runtime `Section[]` (`group.title`→section.title; `rule='random'`+`randomCount` → metadata chọn ngẫu nhiên đưa vào package settings cho player sample).

| Authoring `QuestionType` | Runtime `QuestionKind` | Đáp án (→answer_key) | Policy |
|---|---|---|---|
| multiple-choice | single_choice | correctChoiceId | auto |
| multiple-response | multiple_response | correctChoiceIds[] | auto, partial |
| true-false | true_false | correct choice | auto |
| short-answer | short_answer | correctAnswers[] | auto (normalize match) |
| numeric | numeric | value + tolerance | auto (tolerance) |
| sequence | sequence | thứ tự đúng | auto, partial |
| matching | matching | prompt→response map | auto, partial theo cặp |
| fill-in-the-blanks | fill_blank | đáp án từng blank | auto, partial |
| select-from-lists | select_from_lists / inline_choice | correctOptionId/blank | auto, partial |
| drag-the-words | drag_words | word đúng từng slot | auto, partial |
| hotspot | hotspot | vùng đúng | auto (hit-test) |
| drag-and-drop | drag_drop | item→target map | auto, partial |
| likert-scale | likert_scale | **không có (survey)** | survey, không điểm |
| essay | essay | **không có** | manual (giáo viên) |

Điểm cần lưu ý:
- **likert-scale** → `grading_policy='survey'`, `is_auto_gradable=false`, điểm 0→max; giữ raw answer cho phân phối.
- **essay** → `grading_policy='manual'`, `needs_manual_grade=true`, max = tổng rubric; điểm do endpoint giáo viên set.
- **select-from-lists**: runtime có cả `inline_choice` và `select_from_lists`. Heuristic: `select_from_lists` khi có list option gắn từng blank trong câu; `inline_choice` cho 1 dropdown inline đơn. Ghi kind đã chọn vào `quiz_version_questions.kind`. (Quyết định: dùng heuristic + để ngỏ khả năng thêm toggle editor sau.)
- **Field chỉ dùng cho trình bày** (`textStyles`, `elementOffsets`, `layoutPreset`, `animationPreset`, `themeColor`, `layers`, `infoPage`) không có vai trò chấm điểm. Chỉ mang cái player cần (theme/layout) vào `runtime_quiz`; bỏ phần ergonomics editor.
- **feedbackRows** → runtime `Question.feedback{correct,incorrect,partial}`; `score`/`branching` từng row gộp vào points + metadata branching.
- **Tách answer-key**: compile phát ra `body` (choices mất `correct`, blanks mất `correctAnswers`, hotspot mất `correct`, xáo trộn option) VÀ `answer_key`. Package server-authoritative chỉ nhúng `body`. Package client-first CÓ THỂ nhúng key trong `runtime_quiz` cho luyện tập — quyết định theo từng version.

---

## 4. Contract API

GraphQL `POST /api/v1/graphql` (operationName+query+variables, portal suy từ URL, tenant từ session, `GraphQlPage<T>`). REST `/api/v1/...`, Bearer, envelope `{data,error,success}`, `X-Request-ID`, `X-Idempotency-Key`.

### 4.1 GraphQL đọc (LCMS)
- **`LcmsQuizWorkspace`** — list + facets (giống question-bank workspace): `quizzes { items { id title subtitle kind status subjectId levelId currentVersionLabel questionCount updatedAt } + GraphQlPage } subjects{id label} levels{id label} topics{id label}`. Input `{tenantId, search, subjectId, levelId, topicId, status, kind, page, size}` (clamp size ≤ 50 như bank).
- **`LcmsQuizEditorDocument`** — hydrate editor (thay mock factory trong `use-quiz-editor-state.ts`): `quizDraft(quizId){ quizId draftId docVersion baseVersionLabel updatedAt settings playerTemplate tree }`.
- **`LcmsQuizVersionHistory`** — `quizVersions(quizId){ items{ id versionLabel versionNumber gradingMode contentHash publishedBy publishedAt isActive } }`.
- **`LcmsQuizReport`** — `quizReport(input){ quiz{...} stats{...} questionStats{...} leaderboard{items + GraphQlPage} progress{items} }`. Input `{quizId, quizVersionId?, classId?, page, size}`.
- **`LcmsQuestionBankWorkspace`** (ĐÃ chạy — phục vụ `/questions`): giữ nguyên `questionBankWorkspace(input){ questions{items{id subjectId levelId topicId title type status usageCount updatedAt}+GraphQlPage} subjects{id label} levels{id label} topics{id label} quizzes{id title status} }`. BE chỉ cần back bằng bảng thật (§1.5) thay mock; FE `question-bank-api.ts` đã gọi sẵn, clamp size ≤ 50.
- **`LcmsQuizBankWorkspace`** (phục vụ `/quiz-bank`): list `quizBankItems{ items{ id title kind train|test status subjectId subjectLabel levelId levelLabel topicLabels questionIds questionCount durationLabel scopeLabel sourceMode manual|auto-random ownerLabel updatedAt } + GraphQlPage } subjects levels topics`. Ánh xạ trực tiếp từ `quizzes`+`quiz_versions` (không bảng riêng — `quiz_bank_items` chỉ dùng cho gói tĩnh thủ công/auto-random).

> `/questions` và `/quiz-bank` là hai màn LCMS đã render qua `DashboardContent` với GraphQL workspace; BE chỉ thay nguồn mock bằng bảng thật, **giữ nguyên tên operation + shape `GraphQlPage`** để FE không phải sửa.
> Runtime reads giữ REST theo contract có sẵn (`GET /quizzes`, `/quizzes/:id/package`, `/quizzes/:id`) — không chuyển sang GraphQL.

### 4.2 REST ghi — authoring (envelope; create nhận `X-Idempotency-Key`)
- `POST /lcms/quizzes` — `{title, subtitle?, kind, subjectId?, levelId?, categoryId?}` → 201 `{quizId, draftId, docVersion:0}`.
- `PUT /lcms/quizzes/:id/draft` — autosave full `{docVersion, settings, playerTemplate, tree}`. `docVersion` lệch → 409 `{code:STALE_DRAFT, serverDocVersion}`; else ghi, `doc_version++` → 200 `{docVersion}`.
- `PATCH /lcms/quizzes/:id/draft` — autosave từng phần (merge patch/sub-tree) cho save theo gõ phím; cùng lock. Ưu tiên để tiết kiệm băng thông.
- `POST /lcms/quizzes/:id/slides` — upsert slide `{groupId, slide, afterSlideId?}` (server gán id nếu thiếu, validate kind) → 200 tree fragment.
- `POST /lcms/quizzes/:id/reorder` — `{groups:[{groupId, slideIds:[...]}]}` → 200.
- `POST /lcms/quizzes/:id/validate` — chạy luật §8 → 200 `{valid, errors:[{path,code,message}]}`, không mutate.
- `POST /lcms/quizzes/:id/publish` — `{docVersion, gradingMode, versionLabel?}`, `X-Idempotency-Key` bắt buộc. 409 stale; 422 `{code:VALIDATION_FAILED, errors}`; 201 `{quizVersionId, versionLabel, versionNumber, packageId, contentHash}`.
- `POST /lcms/quizzes/:id/versions/:versionId/fork` — tạo draft từ snapshot → 201 `{draftId, docVersion:0, baseVersionLabel}`.

### 4.3 REST ghi — media (§5)
- `POST /lcms/media/presign` — `{sha256, mimeType, byteSize, originalName}`. Nếu tồn tại → 200 `{deduped:true, mediaId, url}`; else 200 `{deduped:false, mediaId, uploadUrl, storageKey, headers}`.
- `POST /lcms/media/:id/commit` — sau khi client PUT; server HEAD object, verify size/hash, trích dims → 200 `{mediaId, url, width, height}`.

### 4.4 REST ghi — runtime (giữ nguyên theo contract)
`POST /attempts`, `POST /attempts/:id/sync`, `POST /attempts/:id/submit` đúng contract. Idempotent theo `attemptId` + `event.id`. Server verify package hash/version, giải `quiz_version_id`, upsert attempt/events, chiếu `student_progress`, enqueue recompute report.

### 4.5 Export report
- `POST /lcms/quizzes/:id/report/export` — `{quizVersionId?, classId?, format:csv|xlsx}` → 202 `{jobId}`; poll `GET /lcms/exports/:jobId` → `{status, downloadUrl?}`. Async cho export toàn lớp.

### 4.6 Chấm tay (essay) — giáo viên
- `POST /lcms/attempts/:id/grade` — `{grades:[{questionId, awardedPoints, feedback?}]}`, `X-Idempotency-Key`. Cập nhật `attempt_answers` (graded_by=teacher, needs_manual_grade=false), recompute total `attempts`, chạy lại leaderboard. 200 attempt cập nhật; 422 nếu awardedPoints > max_points.

### 4.7 GraphQL đọc — LMS (giáo viên) & elearning (học sinh) — phần lớn ĐÃ chạy

Các operation này FE đã gọi trong `lms-graphql-api.ts` / `student-dashboard-api.ts`; BE chỉ cần back bằng bảng thật (§1.7, §1.9). **Giữ nguyên tên + shape `GraphQlPage`.**

- **`LmsTeacherHomeworkWorkspace`** (`/homework`, `/home`): `{ summary{activeAssignmentCount overdueAssignmentCount pendingReviewAssignmentCount} classOptions[] assignments:GraphQlPage<LmsAssignment> pendingReviews:GraphQlPage overdueStudents:GraphQlPage }`. Input `{status?, page?, size?}`. `assignments` → FE map thành `AssignmentRun` (completionRate/submittedCount/inProgressCount/needsReviewCount tính từ report §1.8).
- **`LmsAssignmentProgressWorkspace`** (`/homework/progress`): `{ assignment summary{submittedCount inProgressCount needsReviewCount} attempts:GraphQlPage<LmsAttempt> recipients:GraphQlPage<LmsAssignmentRecipient> missingLateStudents:GraphQlPage }`. Input `{assignmentId, page?, size?}`. Mỗi recipient trả kèm `latestAttempt`+`bestAttempt` (BE tính, FE không suy luận).
- **`LmsClassWorkspace`** (`/homework/class`): `{ classInfo scoreSummary{completedAttemptCount attemptedStudentCount averagePercent bestPercent} riskSummary{missingOverdueStudentCount lowScoreStudentCount inactiveStudentCount} students:GraphQlPage assignments:GraphQlPage }`. Input `{classId, schoolId?, studentStatus?, assignmentStatus?, page, size}`.
- **`LmsLearningResourceLibrary`** / **kho luyện tập** (`/homework/exercise-bank`): trả cây taxonomy `subjects→levels→topics→items` + `resources`; **items là chiếu từ quiz đã publish** (§1.9), không phải bảng riêng. Input `{subjectId?, levelId?, gradeId?, difficulty?, type?, status?, search?, page?, size?}` (khớp bộ lọc FE: subject/level/difficulty/type/status).
- **`ElearningStudentDashboard`** (portal elearning `/student`): `{ profile{...averageScore completedAssignments totalAssignments weeklyGoalProgress...} assignments[]{id quizId title status progressRate answeredCount totalQuestions score maxScore dueLabel attempts[]} teacherAnnouncements[] }`. Bài của học sinh = join `assignment_recipients` (theo `student_user_id`) × `assignments` × `attempts`.

### 4.8 REST ghi — LMS giao bài / nhóm

- `POST /lms/assignments` — `X-Idempotency-Key`. Body: `{title, subjectId, startAt, dueAt, attemptLimit, maxDurationMinutes, recipientMode: class|grade|group|individual, recipientIds:[...], resourceQuizIds:[...]}`. Server: tạo `assignments`, ghim `quiz_version_id` active cho mỗi resource vào `assignment_resources`, **resolve recipientIds → danh sách phẳng** `assignment_recipients` theo mode (class→toàn HS lớp, grade→HS các lớp thuộc khối, group→thành viên nhóm, individual→chính danh sách). → 201 `{assignmentId}`.
- `PATCH /lms/assignments/:id` — sửa tiêu đề/hạn/attemptLimit/status (`draft→active→closed`). Không cho sửa `assignment_resources.quiz_version_id` sau khi có attempt (giữ đề ổn định); muốn đổi đề → tạo assignment mới.
- `POST /lms/assignments/:id/recipients` — thêm/bớt học sinh (diff), giữ attempt cũ.
- `DELETE /lms/assignments/:id` — chỉ khi chưa có attempt; nếu có → chuyển `status='closed'` (soft) thay vì xóa.
- `POST /lms/student-groups` / `PATCH /lms/student-groups/:id` / `DELETE …` — CRUD nhóm học sinh (name/color/source_label + members). Xóa nhóm không xóa assignment đã giao (recipients đã resolve phẳng).

> Giao bài KHÔNG lưu answer key và KHÔNG sinh package mới — nó tham chiếu `quiz_packages` của version đã ghim. Học sinh làm bài đi qua đúng runtime REST §4.4 với `assignmentId` + `quizId` + `packageId` của version được giao.

---

## 5. Xử lý media (thay base64)

Hash-first, dedup, presign direct-to-storage, commit, ref-count GC:
1. Editor hash file phía client → `POST /media/presign`.
2. Nếu deduped, dùng lại `mediaId`/`url` ngay.
3. Nếu không, PUT bytes lên presigned URL (S3/GCS/MinIO), rồi `POST /media/:id/commit` (server verify size/mime/dims, từ chối nếu hash lệch).
4. Slide media thành `{type:'image', src:url, alt, name}` — `QuizEditorSlideMedia.src` giờ chứa URL CDN, không phải `data:`.
5. Khi publish, `runtime_quiz` nhúng cùng URL; assets manifest của package liệt kê để Tauri prefetch offline. CDN cache dài với key content-hash (immutable).
6. GC orphan: `media_references` giữ `ref_count`; job đêm xóa object storage có `ref_count=0` quá thời gian ân hạn (để undo/redo không mất bytes). Reference của published-version không bao giờ giảm (immutability ghim assets).

Bảo mật: presigned URL sống ngắn (~5 phút); allowlist mime chỉ ảnh; giới hạn byte enforced ở presign và commit HEAD.

---

## 6. Chấm điểm — cả 2 mode

Answer key nằm ở `quiz_versions.answer_key` + `quiz_version_questions.answer_key`, phía server, không bao giờ vào package testing.

- **client-first** (luyện tập/homework/Tauri offline, mặc định): package có thể nhúng key trong `runtime_quiz`; FE `AssessmentEngine` chấm cục bộ, log `answer_graded`, sync. Khi sync: lưu event, cập nhật progress, không chấm lại. Khi submit: server **recompute** từ `answer_key` của mình (lưu cả kết quả client + server, đánh dấu lệch trong `meta`) hoặc chấp nhận `clientResult` cho homework thuần. **Khuyến nghị: recompute bất cứ khi nào có key.**
- **server-authoritative** (thi thật): package chỉ nhúng `body`. FE gửi raw `AnswerPayload`; server chấm bằng `answer_key`, trả `Attempt` có điểm (key không bao giờ echo).

Chống lộ (testing): compile tách correctness vào `answer_key`; builder package server-authoritative dùng `body` đã strip; GraphQL/REST hướng học sinh không bao giờ select `answer_key`; xáo trộn choices/options để vị trí không lộ đáp án.

Luật từng loại (server mirror FE engine): single/true_false exact; multiple_response partial = (đúng − sai)/tổng_đúng floor 0 khi `acceptPartial`, else all-or-nothing; matching/drag_*/inline theo tỉ lệ từng item; sequence theo tỉ lệ vị trí đúng; fill/short_answer normalize (trim/case/dấu theo config) match `correctAnswers[]`, partial từng blank; numeric `|v − correct| ≤ tolerance`; hotspot point-in-region trên tọa độ chuẩn hóa; likert survey (0 điểm); essay manual. Ghi chấm keyed theo (attempt, question) với `on conflict do update` chỉ khi `graded_at` mới hơn — không bao giờ cộng dồn.

---

## 7. Chiến lược caching

| Layer | Cái gì | Ở đâu | Chính sách | Invalidation |
|---|---|---|---|---|
| QuizPackage | `GET /quizzes/:id/package` | CDN `Cache-Control: public, max-age=1y, immutable` (URL mang version+hashPrefix) + Redis `pkg:{quizId}:{version}` | immutable, content-addressed | publish mới → version mới → **key mới**; key cũ hợp lệ mãi; không purge |
| Package ETag | cùng endpoint | `ETag: contentHash`, 304 | validate | chỉ đổi theo version |
| List quiz/workspace | `LcmsQuizWorkspace`, `GET /quizzes` | Redis `list:{tenant}:{portal}:{filters}:{page}` | ngắn 30–60s | bust khi create/publish/status (tag theo tenant) |
| List question-bank | `LcmsQuestionBankWorkspace` | Redis 60s + ETag tùy chọn | | bust khi câu hỏi bank đổi |
| Quiz-bank / kho luyện tập | `LcmsQuizBankWorkspace`, `LmsLearningResourceLibrary` | Redis 60s | ngắn | bust khi publish/giao bài (đổi assignedCount) |
| Homework workspace | `LmsTeacherHomeworkWorkspace`, `LmsClassWorkspace` | Redis 30–60s theo (teacher/class) | ngắn | bust khi tạo/sửa assignment hoặc có submit mới |
| Assignment progress | `LmsAssignmentProgressWorkspace` | Redis 15–30s theo assignmentId | rất ngắn (live) | bust khi có attempt sync/submit của bài đó |
| Report aggregate | `report_*`, leaderboard | bảng report + Redis 5–15 phút | | bust khi job post-submit xong |
| Media | bytes asset | CDN, key content-hash | immutable, max-age=1y | không bao giờ (hash mới = key mới) |
| Draft | doc editor | không cache | fresh; optimistic-lock là nguồn sự thật | n/a |

Insight (đúng "cache theo quizVersion" của contract): mọi thứ hướng runtime đều content-addressed theo version → publish là sự kiện cache thuần cộng thêm, không race invalidation, cache cũ tự hết hạn.

---

## 8. Ràng buộc / validation & toàn vẹn

**Cổng publish** (chạy ở `/validate` và lặp lại trong transaction `/publish`):
- ≥1 câu tự chấm (không tính info/intro/result slide).
- Mỗi câu auto-gradable có đáp án đúng giải được: single_choice đúng 1; multiple_response ≥1; matching mọi prompt được map; fill_blank mọi blank ≥1 đáp án; numeric có value; hotspot ≥1 vùng đúng; sequence đủ thứ tự; drag_* mọi slot/item có target.
- essay tổng rubric > 0; likert ≥1 row + scale (survey, không key).
- `points ≥ 0`; `passingRate ∈ [0,100]`; `timeLimit` khớp `M:SS` và thống nhất với runtime `timeLimitMinutes`.
- group `rule='random'` ⇒ `1 ≤ randomCount ≤ slides.length`; `randomQuestionCount ≤ tổng`.
- `answerSubmission ∈ {one-by-one, all-at-once}`; `passwordMode='password'` ⇒ password không rỗng.
- Mọi media reference giải được về asset đã commit (không base64/dangling khi publish).
- Unique active version/quiz; unique `(quiz_id, version_number/label)`.

**Toàn vẹn DB:** FK `on delete restrict` cho dữ liệu published; CHECK (percent 0–100, points ≥ 0, enum); `submitted_count ≤ total_questions`, `total_score ≤ max_score`; `attempt_events.id` PK = idempotency; `attempts.id` upsert.

**Concurrency:** autosave optimistic lock qua `doc_version` (409 STALE_DRAFT → FE reload/merge); publish dùng `SELECT … FOR UPDATE` trên dòng quiz + idempotency key chống tạo version trùng; report job dùng advisory lock theo version; grading upsert idempotent theo (attempt, question).

---

## 9. Giai đoạn triển khai đề xuất

- **Phase 0 — Nền tảng.** Migration: tenancy/RLS, `quizzes`, `quiz_drafts`, `media_assets`, chính thức hóa bảng question-bank (back cho `LcmsQuestionBankWorkspace` đang chạy). Wiring session→`app.tenant_id`. **Chốt convention tiến hóa (§11.1) ngay từ đầu:** mọi blob JSONB mang `schemaVersion`, cột `schema_version int` song song, lớp `migrateToLatest()` + tolerant reader, enum lưu `text`+CHECK (không PG enum) — làm sau rất khó lắp ngược.
- **Phase 1 — Authoring CRUD (mở khóa editor nhanh nhất).** `POST /lcms/quizzes`, `PUT/PATCH …/draft` optimistic lock, `LcmsQuizEditorDocument` + `LcmsQuizWorkspace`, slide upsert/reorder, media presign/commit. Cho phép FE thay mock factory trong `use-quiz-editor-state.ts` bằng save/load thật và bỏ base64.
- **Phase 2 — Publish/compile/package.** `/validate`, `/publish`, compile (§3) + tách key, `quiz_versions` + bảng chuẩn hóa, ký, version history, fork. Tạo `QuizPackage` thật.
- **Phase 3 — Wiring runtime.** Endpoint contract chạy trên package thật (`GET /quizzes`, `/package` với CDN/ETag, `/quizzes/:id`, `POST /attempts`, `/sync`, `/submit`), server grading cả 2 mode, ingest event idempotent, projection `student_progress`.
- **Phase 4 — LMS phân phối (giao bài / kho luyện tập / tiến độ).** Bảng `assignments`, `assignment_resources` (ghim `quiz_version_id`), `assignment_recipients` (resolve phẳng theo mode), `student_groups`. REST §4.8 (`POST /lms/assignments`, recipients, groups). GraphQL §4.7 (`LmsTeacherHomeworkWorkspace`, `LmsAssignmentProgressWorkspace`, `LmsClassWorkspace`, `LmsLearningResourceLibrary` = chiếu quiz published, `ElearningStudentDashboard`) back bằng bảng thật. Mở khóa `/homework`, `/homework/assign`, `/homework/exercise-bank`, `/homework/progress`, `/student`.
- **Phase 5 — Reporting.** Job recompute post-submit, bảng `report_*`, `LcmsQuizReport` + số liệu progress/leaderboard cho LMS (completionRate, phân loại A–E, riskSummary), chấm essay tay, export async, cache.
- **Phase 6 — Gia cố + Portable bundle.** GC media orphan, hết hạn/xoay khóa ký package, endpoint audit raw-event cho giáo viên, ma trận test RLS/authz (student/class/school), kiểm tra tải + caching. **Portable (§11.2):** `quiz_bundles`, `POST/GET /lms/bundles`, đóng gói `.ergquiz` (content-addressed + ký Ed25519), resolve media `asset://{sha256}` offline, sync ngược qua §4.4. Nền tảng `schemaVersion` (§11.1) phải có sẵn từ Phase 0 để bundle cũ luôn mở được.

Thứ tự: 0 → 1 (media song song) → 2 → 3 → 4 → 5 → 6. Phase 1 mở khóa authoring FE (`/quiz-editor`, `/questions`, `/quiz-bank`) ngay; Phase 3 mở khóa player học sinh; Phase 4 mở khóa toàn bộ luồng LMS giao bài/tiến độ. Phase 4 chỉ phụ thuộc Phase 2 (cần quiz đã publish để giao) — có thể chạy song song với Phase 3/5.

---

## 10. Quyết định mặc định cho các câu hỏi mở
- `version_label`: tự động `vN`, cho phép override thủ công lúc publish.
- client-first training: nhúng key trong package + recompute khi submit; cấu hình theo từng version.
- `select-from-lists`: dùng heuristic map sang `select_from_lists`/`inline_choice`; để ngỏ toggle editor tương lai.
- Thời gian ân hạn GC media orphan: mặc định 7 ngày; version archived giữ vô thời hạn cho tới khi có chính sách retention riêng.

---

## 11. Tiến hóa model & đóng gói Portable (desktop offline)

Hai yêu cầu dài hạn: **(A)** đổi model về sau chỉ ảnh hưởng ít, không "đập" DB; **(B)** fork sang app desktop chạy **không cần mạng**, bài tập đóng thành **một bộ portable**. Hai yêu cầu này bổ trợ nhau: cùng một nguyên tắc "content-addressed + versioned blob".

### 11.1 Chiến lược để đổi model mà không đập DB

Nguyên tắc: **giữ phần dễ đổi trong JSONB có đánh version, giữ phần cần index/khóa/tổng hợp ở cột quan hệ.** Đổi cột quan hệ là migration nặng; đổi trong JSONB chỉ là đổi code đọc/ghi.

1. **Mọi blob JSONB then chốt mang `schemaVersion`** (số nguyên tăng dần) ngay trong payload: `quiz_drafts.tree/settings/player_template`, `quiz_versions.runtime_quiz/answer_key/authoring_snapshot`, `quiz_packages.package_json`, `attempts.answers`, `attempt_events.answer/result`, `question_bank_questions.content`. Cột `schema_version int` (đã thêm ở §1.2/1.3) là bản sao top-level để **query/migration lọc nhanh** không cần mở JSON.
2. **Đọc theo "tolerant reader"**: BE (và FE/desktop) đọc blob qua một lớp `migrateToLatest(payload)` — nhận `schemaVersion` cũ, nâng cấp trong bộ nhớ lên shape hiện tại. Thêm field mới = optional + default; đổi tên = map ở migrator; bỏ field = migrator bỏ qua. **Không migration DB** cho phần lớn thay đổi.
3. **Migration lười (lazy), không big-bang**: dữ liệu cũ giữ nguyên `schemaVersion` cũ; chỉ nâng cấp-và-ghi-lại khi có dịp chạm vào (autosave draft, publish version mới). `quiz_versions`/`quiz_packages` **immutable** nên KHÔNG ghi đè — version cũ giữ schema cũ vĩnh viễn; reader phải đọc được mọi `schemaVersion` đã từng phát hành (giữ migrator cộng dồn). Đây là điều kiện bắt buộc để package portable cũ vẫn mở được.
4. **Thêm field runtime/authoring**: chỉ sửa TS type + migrator, không migration. Nếu cần **filter/aggregate** theo field mới → mới thêm cột quan hệ (generated column từ JSONB, hoặc backfill 1 lần). Ví dụ: sau này cần lọc theo `difficulty` cấp câu hỏi trong version → `ALTER TABLE quiz_version_questions ADD COLUMN difficulty text GENERATED ALWAYS AS (body->>'difficulty') STORED` — không đụng dữ liệu cũ.
5. **Enum kiểu mở**: `kind`, `grading_policy`, `resourceType`, `status`… lưu `text` + CHECK (KHÔNG dùng PG `enum` type — thêm giá trị vào PG enum phải `ALTER TYPE`, còn CHECK chỉ cần drop/add constraint). Question `kind` mới (dạng câu hỏi thứ 15) = thêm 1 giá trị text, không đổi cấu trúc bảng.
6. **Contract API versioned**: giữ tiền tố `/api/v1`; GraphQL thêm field không phá vỡ (chỉ thêm optional). Breaking change → `/api/v2` song song, desktop cũ vẫn gọi v1.
7. **Không xóa cứng**: bỏ field ở FE → để lại field trong blob (reader bỏ qua) cho tới khi chắc chắn không desktop/version nào còn dùng, rồi mới dọn ở lần migrate-on-write kế tiếp.

> Tóm tắt "ảnh hưởng ít": thêm field → 0 migration (JSONB + migrator). Cần index field mới → 1 generated column (không backfill nặng). Thêm dạng câu hỏi → 1 giá trị enum text. Breaking → `/api/v2` cạnh v1. Chỉ đổi khóa chính/quan hệ lõi mới là migration thật — mà thiết kế này đã đẩy hết phần hay đổi ra khỏi cột lõi.

### 11.2 Portable bundle — đóng gói offline cho desktop

Desktop app (Tauri, xem `docs/backend-assessment-contract.md` §Tauri) cần **một bộ tự chứa** để chạy không mạng. Bộ này là mở rộng của `quiz_packages` sẵn có — **không đẻ nguồn sự thật mới**, chỉ là cách đóng gói nhiều package + assets + rubric.

**Định dạng `.ergquiz` (zip)** — manifest + nội dung content-addressed:
```
bundle.json          # manifest: bundleId, schemaVersion, createdAt, expiresAt,
                     #   publicKeyId, signature, gradingMode, danh sách packageIds
quizzes/
  {packageId}.json   # = quiz_packages.package_json (runtime Quiz, đã strip key nếu testing)
answer-keys/
  {packageId}.json   # answer_key — CHỈ có khi grading client-first (bỏ hẳn khi testing)
assets/
  {sha256}.{ext}     # media theo hash (trùng media_assets.sha256) — offline, không URL
signature.bin        # ký Ed25519 trên canonical(bundle.json + hash mọi file)
```

Nguyên tắc:
- **Content-addressed toàn bộ**: quiz theo `packageId` (đã mang version+hash), asset theo `sha256`. Bundle chỉ là tập hợp con package immutable → **tái lập được, verify được, cache được**. Hai bundle chứa cùng quiz chia sẻ cùng file asset.
- **`bundle.json` mang `schemaVersion`** riêng (song song với schemaVersion từng package) → desktop cũ đọc bundle mới bằng chính migrator §11.1.
- **Chữ ký + hash**: desktop verify chữ ký bằng `publicKeyId` (khớp `quiz_packages.public_key_id`) trước khi tin. Bất kỳ file nào lệch hash → từ chối. Chống sửa đề/đáp án offline.
- **SQLite offline** đúng như contract: `quiz_packages`, `attempt_sessions`, `sync_outbox`. Thêm cột `schema_version` cho mỗi bảng SQLite để đối xứng với server. Bundle giải nén → nạp vào `quiz_packages` local.
- **Assets offline**: desktop KHÔNG dùng URL CDN mà đọc `assets/{sha256}` trong bundle → khi compile `runtime_quiz` cho bundle, thay `media.src` (URL) bằng tham chiếu tương đối `asset://{sha256}` (hoặc kèm bảng map sha256→đường-trong-zip trong manifest). Web vẫn dùng URL CDN. **Cùng một `runtime_quiz`, chỉ khác lớp resolve media** — giữ 1 nguồn, 2 cách phân giải.
- **Chấm offline**: bundle mang `answer-keys/` nên desktop chấm client-first bằng `AssessmentEngine` (đã có ở FE). Bài thi server-authoritative **không** đóng key vào bundle → hoặc không cho offline, hoặc chỉ thu answer log rồi chấm khi online.
- **Đồng bộ ngược**: attempt/event làm offline ghi vào `sync_outbox`; khi có mạng đẩy lên đúng REST §4.4 (`/attempts/:id/sync`, `/submit`) — idempotent theo `attemptId`+`event.id` nên đẩy lại nhiều lần vô hại.

**API đóng gói bundle** (thêm vào §4, REST):
- `POST /lms/bundles` — `{assignmentId?, quizVersionIds:[...], includeAnswerKeys:bool, expiresAt?}` → 202 `{bundleId, jobId}` (đóng gói async: gom package + asset + ký). Từ chối `includeAnswerKeys=true` nếu có version testing (chống lộ đề).
- `GET /lms/bundles/:id` — `{status, downloadUrl?, sizeBytes, contentHash, expiresAt}`. `downloadUrl` là object-storage content-addressed, cache dài.
- `GET /lms/bundles/:id/manifest` — trả `bundle.json` để desktop kiểm tra trước khi tải nguyên bộ (tải chênh lệch: bỏ qua asset đã có theo sha256).

**Bảng `quiz_bundles`** (thêm vào §1): `id uuid pk, tenant_id, schema_version int, assignment_id uuid null, package_ids text[], include_answer_keys bool, content_hash, signature, public_key_id, storage_key, size_bytes, generated_by, generated_at, expires_at`. Immutable như `quiz_packages`. Index `(tenant_id, assignment_id)`.

### 11.3 Caching cho bundle
Bundle immutable + content-addressed y như package: CDN `max-age=1y, immutable`, key mang `bundleId`+hashPrefix, ETag=contentHash. Đổi nội dung → bundle mới, id mới; bundle cũ vẫn hợp lệ cho desktop đã tải. Desktop tải chênh lệch theo `sha256` asset (bỏ qua file đã có cục bộ).

---

## Phụ lục A — File FE tham chiếu quan trọng (để đội BE đối chiếu)
- `src/features/lcms/quiz/quiz-editor/types/quiz-editor-types.ts` — model authoring mà draft JSONB phải phản chiếu.
- `src/lib/types.ts` — model runtime mà bước compile phải phát ra + shape attempt/package.
- `docs/backend-assessment-contract.md` — endpoint runtime/idempotency phải giữ nhất quán.
- `src/features/lcms/quiz/question-bank/api/question-bank-api.ts` — pattern GraphQL workspace + `GraphQlPage` để nhân bản cho quizzes.
- `src/features/lcms/quiz/question-bank/api/question-bank-to-quiz.ts` — ngữ nghĩa copy bank→slide hiện tại (cơ sở cho reference-vs-copy-on-publish).
- `src/lib/graphql-client.ts`, `src/lib/api-client.ts` — transport + quy ước authz (tenant/portal từ session, không từ client).
- `src/features/lms/api/lms-graphql-api.ts` — các operation LMS đã chạy (`LmsTeacherHomeworkWorkspace`, `LmsAssignmentProgressWorkspace`, `LmsClassWorkspace`, `LmsLearningResourceLibrary`) + shape `LmsAssignment`/`LmsAttempt`/`LmsAssignmentRecipient` mà §1.9/§4.7 phải khớp.
- `src/features/lms/components/assign-homework-page.tsx` — wizard giao bài (recipientMode, selectedResourceIds, attemptLimit, dates) → payload `POST /lms/assignments`.
- `src/features/lms/components/exercise-bank-page.tsx` — kho luyện tập (`Exercise` fields + bộ lọc) là chiếu từ quiz publish.
- `src/features/lms/components/homework-progress-page.tsx` — suy trạng thái tiến độ từ `latestAttempt`/`bestAttempt`.
- `src/features/elearning/student-dashboard/api/student-dashboard-api.ts` — `ElearningStudentDashboard` shape cho portal học sinh.

---

## Phụ lục B — Nghiệm thu (khi BE triển khai xong)
Tài liệu này là spec, không có code chạy. Khi BE dựng theo tài liệu, nghiệm thu bằng:
1. FE trỏ `VITE_API_BASE` vào BE thật, mở `/quiz-editor` → tạo/tải/lưu draft thật (thay mock), autosave trả `docVersion` tăng, 409 khi lock lệch.
2. Upload ảnh trong editor → không còn base64, `media.src` là URL CDN.
3. Publish → sinh `quiz_versions` + `quiz_packages`, `GET /quizzes/:id/package` trả package ký, ETag=contentHash, cache immutable.
4. Player học sinh (đã có sẵn) chạy client-first: chấm cục bộ, sync event idempotent, submit → server recompute khớp.
5. Server-authoritative: package không chứa answer_key (kiểm tra payload), server trả điểm đúng.
6. Report: sau vài submit, `LcmsQuizReport` trả stats/leaderboard/progress; chấm essay tay cập nhật lại total.
7. LCMS `/questions` + `/quiz-bank`: `LcmsQuestionBankWorkspace` + `LcmsQuizBankWorkspace` trả dữ liệu thật (không mock), lọc/paginate chuẩn `GraphQlPage`.
8. LMS `/homework/assign`: giao 1 bài chứa nhiều quiz cho lớp/khối/nhóm → sinh `assignment` + `assignment_resources` (ghim version) + `assignment_recipients` phẳng đúng số học sinh.
9. LMS `/homework` + `/homework/progress`: `LmsTeacherHomeworkWorkspace`/`LmsAssignmentProgressWorkspace` trả assignment + recipient kèm `latestAttempt`/`bestAttempt`; trạng thái completed/inprogress/notstarted khớp attempt.
10. LMS `/homework/exercise-bank`: kho luyện tập chỉ hiện quiz đã publish, `questionCount`/`durationMinutes` lấy từ version active.
11. elearning `/student`: học sinh thấy đúng bài được giao (join theo `student_user_id`), làm bài đi qua runtime REST với version đã ghim.
12. Tiến hóa model: thêm 1 field optional vào `runtime_quiz`/`answers` + bump `schemaVersion` → version/attempt cũ (schemaVersion cũ) vẫn đọc được qua migrator, KHÔNG chạy migration DB. Thêm 1 giá trị `kind` mới → chỉ sửa CHECK constraint.
13. Portable bundle: `POST /lms/bundles` → tải `.ergquiz`, verify chữ ký + hash offline, giải nén nạp SQLite, chấm client-first không mạng bằng `answer-keys/`; nối mạng lại → `sync_outbox` đẩy attempt/event idempotent. Bài testing: bundle KHÔNG chứa answer key.
