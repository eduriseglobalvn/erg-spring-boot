# 01 – Learning Delivery (LMS, Quiz, E-learning, Curriculum, Learning content/outcomes/standards)

> Second brain – module 01. Viết bằng tiếng Việt; tên class/endpoint/định danh giữ tiếng Anh.
> Quy ước đường dẫn: `B` = `src/main/java/com/erg/ergspringboot`. Mọi đường dẫn dưới đây tính từ gốc repo `erg-spring`; `B/...:số` là `file:dòng` (dòng theo bản code lúc đọc, nhánh `Backup`, worktree đang dirty).
> Phạm vi đọc: ~330 file có segment lms/quiz/elearning/courses/learningcontent/content/curriculum/learningstandards/learningoutcomes. Đọc kỹ toàn bộ phần logic nghiệp vụ (use case, service, controller, policy, validator, migration liên quan) và các test chính; DTO/record/projection/repository/entity chỉ đọc lướt (cấu trúc, annotation, query quan trọng). Các nơi "chưa đọc kỹ" được ghi rõ.

---

## 1. Phạm vi và trách nhiệm

Module "Learning Delivery" là lõi nghiệp vụ dạy–học của ERG: giáo viên/biên tập viên soạn **bài kiểm tra (quiz)** từ ngân hàng câu hỏi, **xuất bản** thành phiên bản bất biến có chữ ký, **giao bài (assignment)** cho lớp/nhóm/học sinh, học sinh **làm bài (attempt)** → server **chấm điểm**, đồng thời có báo cáo tiến độ, điểm danh/sổ điểm, khung chương trình **curriculum** (subject → level → topic), kho **learning content** gắn vào topic, **vòng đời nội dung** (draft → review → approved → published → archived), và 3 module "hợp đồng" mới: **learning outcomes** (lộ trình, năng lực, chứng chỉ), **learning standards** (SCORM/LTI/xAPI) và **e-learning** của học sinh (dashboard, thảo luận lớp).
Đây là backend canonical (REST + GraphQL), đa tenant (tenant lấy từ `UserAccessContext`/principal, không tin client), ghi chú ID dạng String (UUID v7, V8).
Phần quản trị trường/lớp/học sinh (ghi danh, import) nằm trong `lms.LmsOperationalOperations` nhưng controller REST/GraphQL của nó ở `controller/lcms/*` (thuộc slice LCMS – chỉ tham chiếu ở đây).

---

## 2. Class/entity chính và vai trò

### 2.1 Facade / use case (lớp ứng dụng)

| Class | Vai trò | File |
|---|---|---|
| `LmsOperations` | Facade lệnh LMS (start/submit attempt, assignment, group, đọc student) | `B/lms/LmsOperations.java` |
| `LmsQueryOperations` | Facade đọc workspace (class, teacher homework, assignment progress, groups) | `B/lms/LmsQueryOperations.java` |
| `LmsOperationalOperations` → `LmsOperationalCommandService` | Ghi danh mục: class, student, subject, enrollment, import | `B/lms/LmsOperationalOperations.java`, `B/service/lms/LmsOperationalCommandService.java` |
| `LmsAttemptUseCase` | **Trái tim làm bài**: start/resume/submit, policy snapshot, idempotency, chấm, evict cache | `B/lms/internal/attempt/LmsAttemptUseCase.java` |
| `LmsRuntimeUseCase` (@Primary) | Ghép các use case runtime thành `LmsRuntimePort` | `B/lms/internal/runtime/LmsRuntimeUseCase.java` |
| `LmsRuntimeService` (@Deprecated) | Adapter tương thích bọc `LmsRuntimePort` | `B/service/lms/LmsRuntimeService.java` |
| `LmsAssignmentCommandService` | Tạo/sửa/xoá assignment, recipient, student group | `B/service/lms/LmsAssignmentCommandService.java` |
| `LmsStudentReadModelUseCase`, `LmsAssignmentProgressQueryUseCase`, `LmsClassWorkspaceReadUseCase`, `LmsTeacherHomeworkReadUseCase`, `LmsStudentGroupReadUseCase`, `LmsClassroomQueryUseCase`, `LmsResourceQueryUseCase` | Read model (cache, phân trang bị chặn) | `B/lms/internal/readmodel/*`, `B/lms/internal/classroom/*`, `B/lms/internal/resource/*` |
| `LmsAcademicRecordUseCase` (+GraphQL controller) | Điểm danh & sổ điểm (JDBC thuần) | `B/lms/internal/academicrecord/*` |
| `LmsStatePolicy` | Từ vựng trạng thái assignment/attempt, chuẩn hoá filter fail-closed | `B/lms/LmsStatePolicy.java` |
| `QuizAuthoringService`, `QuizDraftService`, `QuizQuestionLinkService`, `QuestionBankAuthoringService` | Soạn quiz/bản nháp/câu hỏi ngân hàng | `B/service/quiz/authoring/*` |
| `QuizPublishService`, `QuizCompileService`, `QuizValidationService`, `QuizPackageSigner`, `QuestionKindRegistry/Mapping`, `PortableQuizAssetBundler` | Biên dịch + kiểm tra + xuất bản + ký | `B/service/quiz/publish/*` |
| `QuizRuntimePackageService` | Phục vụ package đang active (cache) | `B/service/quiz/runtime/QuizRuntimePackageService.java` |
| `GradingEngine`, `QuizAttemptGradingService`, `AttemptEventRecorder` | Chấm từng câu, tổng điểm, `attempt_answers`, `student_progress`, `attempt_events` | `B/service/quiz/runtime/*` |
| `QuizReportService` | Báo cáo theo câu hỏi / theo assignment | `B/service/quiz/report/QuizReportService.java` |
| `PortableQuizBundleService` | Xuất bundle offline (Tauri player), manifest, update feed, revoke | `B/service/quiz/bundle/PortableQuizBundleService.java` |
| `MediaService` + `MediaStorageAdapter` (Local/R2) | Presign → upload → commit ảnh quiz (dedupe theo sha256) | `B/service/quiz/media/*` |
| `LmsAssessmentCurriculumBridge` | Kiểm chuỗi subject/level/topic khi tạo quiz/câu hỏi + snapshot | `B/service/quiz/assessment/*` |
| `JsonbSchemaMigrator` | Đọc JSONB có phiên bản (hiện **không được dùng** ngoài test – xem §9) | `B/service/quiz/support/JsonbSchemaMigrator.java` |
| `CurriculumOperations` → `CurriculumCommandUseCase`, `CurriculumTreeQueryUseCase`, `CurriculumImpactQueryUseCase`, `CurriculumChainValidationUseCase`; `CurriculumProgramService`, `CurriculumTeachingPlanService` | Taxonomy + chương trình + kế hoạch dạy | `B/curriculum/*`, `B/service/curriculum/*` |
| `ContentLifecycleOperations`, `LifecycleTransitionPolicy` | Máy trạng thái vòng đời nội dung dùng chung | `B/content/lifecycle/*` |
| `LearningContentOperations` → `LegacyLearningContentAdapter` → `LearningContentService` (@Deprecated) ; `LearningResourceLibraryReadService` | Content item/asset/placement + thư viện tài nguyên | `B/learningcontent/*`, `B/service/learningcontent/*` |
| `LearningOutcomesService` (port `LearningOutcomesPort`) | Lộ trình, năng lực, hoàn thành, chứng chỉ | `B/learningoutcomes/internal/LearningOutcomesService.java` |
| `LearningStandardsService` (+`ScormPackageValidator`, `XapiStatementPolicy`, `RsaLtiLaunchTokenVerifier`) | SCORM package, LTI 1.3 launch, xAPI statement | `B/learningstandards/*` |
| `ElearningOperations` → `StudentDashboardReadService`, `StudentEngagementService` (@Deprecated adapter) | Dashboard học sinh + thảo luận lớp | `B/elearning/ElearningOperations.java`, `B/service/elearning/*` |

### 2.2 Entity chính (bảng DB)

| Entity | Bảng | Ghi chú |
|---|---|---|
| `Quiz` | `lms_quizzes` | Định danh quiz; `current_version_id`, `latest_draft_id`, `lifecycle_state`, `deleted_at` (soft delete), `idempotency_key` (`B/model/lms/Quiz.java`) |
| `QuizDraft` | `quiz_drafts` | Bản nháp (settings, playerTemplate, tree JSONB, `docVersion`) |
| `QuizVersion` / `QuizVersionSection` / `QuizVersionQuestion` / `QuizPackage` | `quiz_versions`, `quiz_version_sections`, `quiz_version_questions`, `quiz_packages` | Phiên bản bất biến + câu hỏi chuẩn hoá + package ký |
| `Question`, `QuizQuestion`, `QuestionType`, `QuestionBank*`, `QuizBankItem` | `lms_questions`, `lms_quiz_questions`, `question_types`, `question_bank_*`, `quiz_bank_items` | Ngân hàng câu hỏi + liên kết quiz–câu hỏi (UNIQUE tenant,quiz,question) |
| `Assignment` | `lms_assignments` | Giao bài; cột legacy `academic_class_id`(NOT NULL)/`quiz_id` + `student_ids` JSONB |
| `AssignmentResource` / `AssignmentRecipient` | `assignment_resources` (PK assignment_id+quiz_id), `assignment_recipients` | Nhiều quiz/assignment, mỗi quiz ghim version; recipient phẳng theo học sinh |
| `StudentGroup` / `StudentGroupMember` | `student_groups`, `student_group_members` | Nhóm học sinh |
| `Attempt` | `lms_attempts` | Lượt làm bài: answers JSONB, `policy_snapshot`, `start/submit_idempotency_key`, `record_version` |
| `AttemptAnswerRow`, `AttemptEvent`, `StudentProgress` | `attempt_answers`, `attempt_events`, `student_progress` | Chuẩn hoá từng câu / audit event / tiến độ cho dashboard |
| `Classroom`, `Student`, `StudentEnrollment`, `Subject`, `Level`, `Topic` (LMS) | `academic_classes`, `lms_students`, `lms_student_enrollments`, `lms_subjects`, `lms_levels`, `lms_topics` | Danh mục vận hành (`Subject/Level/Topic` của LMS là bộ **cũ**, song song với `curriculum_*`) |
| `CurriculumSubject/Level/Topic`, `CurriculumProgram(+Row)`, `CurriculumTeachingPlanSetting` | `curriculum_subjects/levels/topics`, `curriculum_programs/_rows`, `curriculum_teaching_plan_settings` | Taxonomy canonical |
| `ContentItem`, `ContentAsset`, `ContentItemAsset`, `TopicContentItem` | `content_items`, `content_assets`, `content_item_assets`, `topic_content_items` | Kho nội dung + placement vào topic |
| `ContentLifecycleTransition` | `content_lifecycle_transitions` | Nhật ký chuyển trạng thái |
| `LearningPath`, `LearningPathNode`, `Competency`, `CompetencyAttainment`, `LearningPathCompletion`, `OutcomeCertificate`, `LearningOutcomeCommandReceipt` | `learning_paths`, `learning_path_nodes`, `competencies`, `competency_attainments`, `learning_path_completions`, `outcome_certificates`, `learning_outcome_command_receipts` | Outcomes |
| `LearningPackage`, `LtiRegistration`, `XapiStatement`, `LearningStandardsCommandReceipt` | `learning_standard_packages`, `learning_standard_lti_registrations`, `learning_standard_xapi_statements`, `learning_standard_command_receipts` | Standards |
| Mô hình `courses/*` (`Course`, `CourseEnrollment`, `CourseLesson`, `CourseProgress`, `CourseSyllabus`, `CourseInstructor`), `elearning/*` (`ElearningCategory/Level/Unit`), `content/*` (`Page`, `Post`, `PostCategory`) | – | **Chỉ có entity**, không thấy service/controller trong các thư mục slice; `Post/Page` thuộc CMS (module 05). Xem câu hỏi mở §10 |

---

## 3. Nghiệp vụ và luồng xử lý

### 3.1 Soạn và xuất bản quiz

**Tạo quiz** – `QuizAuthoringService.create` (`B/service/quiz/authoring/QuizAuthoringService.java:55-110`)
1. Bắt buộc `title`; người tạo phải tồn tại trong `users` của tenant (`:149-157`).
2. Idempotent theo `X-Idempotency-Key` (tìm `findByTenantIdAndIdempotencyKey`, `:62-69`; unique index `uq_lms_quizzes_tenant_idempotency`, V76).
3. `AssessmentCurriculumBridge.validateQuizPlacement(subject, level, categoryId)` – cho phép bỏ trống cả 3, nhưng nếu có thì phải đúng chuỗi subject→level→topic của tenant (`B/service/quiz/assessment/LmsAssessmentCurriculumBridge.java:69-115`; **không** kiểm trạng thái active).
4. `kind` mặc định `train`, chỉ nhận `train|test` (`:133-143`). Status `draft`, version 0, portal `lcms`.
5. Tạo `QuizDraft` rỗng (`docVersion=0`), gán `latestDraftId`, đồng bộ liên kết câu hỏi thủ công.
- **Xoá quiz** = soft delete (`deletedAt`, `status="deleted"`), **không** kiểm assignment đang chạy (`:112-122`).

**Lưu bản nháp** – `QuizDraftService` (`B/service/quiz/authoring/QuizDraftService.java`)
- `saveDraft` (PUT/PATCH draft), `upsertSlide`, `reorder`: lấy draft mới nhất (`findFirstByQuizIdAndTenantIdOrderByUpdatedAtDesc`), khoá lạc quan **tuỳ chọn** theo `docVersion` (null ⇒ last-write-wins; có giá trị mà lệch ⇒ `StaleDraftException` 409 `STALE_DRAFT`) (`:245-262`).
- Mỗi lần ghi: đồng bộ `lms_quiz_questions` từ các slide có `questionId` (xoá hết rồi chèn lại bằng JDBC `delete from lms_quiz_questions…`, bỏ id không tồn tại trong ngân hàng) và cập nhật `Quiz.questionIds` (`:187-210`, `QuizQuestionLinkService.java:37-97`); tăng `docVersion` (`:234-238`).
- Idempotency/receipt qua `LmsSyncOperationService` (module `platform.sync`) khi client gửi `idempotencyKey` (`:161-177`).
- Cả 3 thao tác **evict toàn bộ** các cache `lcmsQuestionBankWorkspace`, `lcmsQuizWorkspace`, `quizRuntimePackage` (`:72, 99, 133`) – xem rủi ro §9.

**Validate / Publish** – `QuizPublishService` (`B/service/quiz/publish/QuizPublishService.java`)
- `validate` (`:138-146`, readOnly) và `publish` (`:148-241`) dùng chung: lấy draft mới nhất → `compileWithSettings` (gộp settings từ câu hỏi ngân hàng + settings liên kết quiz, `:303-321`) → `QuizCompileService.compile` → `QuizValidationService.validate`.
- Quy tắc publish (bắt buộc `X-Idempotency-Key`, `:150-153`):
  1. Replay: nếu đã có `quiz_versions.idempotency_key` trùng ⇒ trả lại version cũ (`:157-168`, unique `uq_quiz_versions_publish_idempotency`, V76).
  2. Nếu `docVersion` gửi lên khác draft ⇒ 409 (`:421-430`).
  3. Có lỗi validate ⇒ `ValidationFailedException` HTTP 422 `VALIDATION_FAILED` kèm danh sách `{path, code, message}` (`:175-177, 535-548`).
  4. `gradingMode` mặc định `client-first`; chỉ nhận `client-first|server-authoritative` (`:432-442`).
  5. `versionNumber = max+1`, nhãn mặc định `v{n}`; version cũ `isActive=false`, version mới `isActive=true` (partial unique `uq_quiz_versions_active`, V26); lưu `authoringSnapshot` (draft + snapshot curriculum), `runtimeQuiz`, `answerKey`, hash, ký.
  6. Lưu `quiz_version_sections` + `quiz_version_questions` (`:272-301`).
  7. Tạo `QuizPackage` id = `pkg_{quizId}_{label}_{hash8}`, `packageFormat=single` (`:351-382`).
  8. Cập nhật `Quiz.currentVersionId/status="published"/version/packageHash/publishedAt` rồi gọi `ContentLifecyclePort.recordPublishedSnapshot` (chuyển `lifecycle_state` → PUBLISHED) (`:227-235`); evict cache `quizRuntimePackage[tenant:quiz]`, `lcmsQuestionBankWorkspace`, `lcmsQuizWorkspace` sau commit (`:474-493`).
- **Package payload**: `{schemaVersion, quizId, version, gradingMode, quiz, assetsManifest, answerKey?}`; `answerKey` chỉ nhúng khi `client-first` (`:384-402`). Hash = `sha256:` của JSON chuẩn hoá khoá-sắp-xếp (`:502-533`); chữ ký: Ed25519 (PKCS#8) hoặc HMAC-SHA256 (mặc định) (`QuizPackageSigner.java:68-92`).
- **Fork** từ một version cũ → tạo draft mới `docVersion=0`, `baseVersionId` (`:243-270`).

**Biên dịch** – `QuizCompileService` (`B/service/quiz/publish/QuizCompileService.java`)
- Cây `groups → slides`; slide không phải câu hỏi (info/result/intro/user-info/instruction) bị loại (`QuestionKindMapping.java`); mỗi group = 1 section.
- 14 loại câu hỏi đóng kín trong `QuestionKindRegistry` (single_choice, multiple_response, true_false, short_answer, numeric, sequence, matching, fill_blank, select_from_lists, drag_words, hotspot, drag_drop, likert_scale[survey], essay[manual]) (`QuestionKindRegistry.java:18-47`).
- Tách đáp án khỏi body: xoá cờ `correct`, `correctAnswers`, `tolerance`… khỏi body gửi cho học sinh và dồn vào `answerKey` (`:151-178, 391-433`).
- **Điểm do hệ thống chia đều**: tổng cố định 1000.00 điểm chia đều cho các câu (`PACKAGE_TOTAL_POINT_CENTS=100_000`, `:29`; `autoQuestionPoints :656-664`); điểm từng câu do giáo viên đặt **không** được dùng (có test `distributesPackagePointsFromFixedOneThousandPointTotal`).

**Quy tắc validate** (`QuizValidationService.java:26-98`): ít nhất 1 câu auto-gradable (`NO_GRADABLE_QUESTION`); mỗi câu auto phải có đáp án theo luật loại câu (`MISSING_ANSWER_KEY`, path `questions[i].answerKey`); essay/likert không cần đáp án.

**Câu hỏi ngân hàng** – `QuestionBankAuthoringService` (`:42-101`): bắt buộc subject/level/topic/stem; kiểm placement qua bridge (`validateQuestionPlacement`, yêu cầu đủ chuỗi); `type` mặc định `multiple_choice` (phải tồn tại trong `question_types`); `difficulty ∈ {core,stretch,challenge}`; `status ∈ {ready,reviewing,pilot,draft,archived,deleted}` mặc định `ready`; scope `global` hoặc `center/school` (+scopeId). Xoá = lưu trữ (archived).

### 3.2 Giao bài (assignment)

`LmsAssignmentCommandService.create` (`B/service/lms/LmsAssignmentCommandService.java:139-194`):
1. Yêu cầu ≥1 `quizIds` (`:140-142`). Idempotency theo `X-Idempotency-Key` (receipt `lms.assignment` + `findByTenantIdAndIdempotencyKey`; unique `uq_lms_assignments_tenant_idempotency`, V76).
2. `recipientMode` mặc định `class`; chấp nhận `class|group|students` (`:496-506`):
   - class: `classId` bắt buộc → toàn bộ học sinh lớp (`findByTenantIdAndAcademicClassIdOrderByFullNameAsc`).
   - group: `groupId` bắt buộc → thành viên nhóm (id lạ/khác tenant bị loại âm thầm, `:426-438`).
   - students: `studentIds` bắt buộc.
   - 0 người nhận ⇒ 400 (`:159-161`).
3. Lớp chính (`academic_class_id`) = `classId` nếu có, nếu không là lớp của học sinh đầu tiên có lớp, else 400 (`:440-450`). Quiz đầu tiên → cột legacy `quiz_id`; `student_ids` JSONB = id học sinh (**Student.id**, không phải auth user id) (`:164-181`).
4. Mỗi quiz **ghim version đang active** vào `assignment_resources` (quiz chưa publish ⇒ 400 `quiz … has no published version`) (`:354-379`).
5. Ghi `assignment_recipients` (unique assignment+student, V28). Status mặc định `active`. Evict 7 cache đọc sau commit (`:468-485`).
- **Không** có validation: `dueAt > startAt`, `attemptLimit/maxDurationMinutes ≥ 0`, quiz thuộc tenant/được phép dùng, người giao có quyền với lớp (chỉ kiểm tenant) – xem §9.

`updateAssignment` (`:220-251`): PATCH từng trường khác null (title, startAt, dueAt, attemptLimit, maxDurationMinutes, offlineAllowed, teacherNote, status – status chỉ lower-case, **không** kiểm theo tập trạng thái). `updateRecipients` (`:196-218`): xoá hết recipient rồi dựng lại (không động tới attempt đã có). `delete` (`:253-259`): hard delete recipients/resources/assignment; FK `lms_attempts.assignment_id → lms_assignments ON DELETE RESTRICT` (V13:104-108) ⇒ assignment đã có attempt sẽ bị lỗi ràng buộc DB.
Nhóm học sinh: `createGroup/updateGroup/deleteGroup` (`:261-352`) – `studentCount` lưu denormalized; update với `studentIds` != null thì thay toàn bộ thành viên. Không kiểm học sinh thuộc tenant/lớp của nhóm.

### 3.3 Làm bài (attempt) – state machine

Trạng thái attempt (`LmsStatePolicy.java:11-18, 40-61`; giá trị lưu): `in_progress` → `submitted` | `needs_review`. Từ vựng filter còn có `started`, `review_required`, `graded`, `completed` nhưng **không có code nào ghi** `graded/completed/review_required` (đã grep) – xem §9.
- Editable = chưa thuộc {submitted, graded, completed, needs_review, review_required}.
- Đếm vào giới hạn lượt = {submitted, needs_review, review_required, graded, completed}.

**Start** `LmsAttemptUseCase.start` (`B/lms/internal/attempt/LmsAttemptUseCase.java:90-139`), transaction `postgresTransactionManager`:
1. Tenant bắt buộc (`AuthenticatedTenant.require`). Tải assignment theo tenant; `ensureClassAccess` – lớp phải thuộc phạm vi `access.canAccessClass` & `canAccessEducationUnit` (`:94-95, 350`).
2. `assignment.quizId == request.quizId` (`:96`) ← chỉ quiz **chính**; học sinh phải có trong `studentIds` (nếu rỗng thì so lớp học sinh với lớp assignment) (`:97, 351`).
3. Idempotency start: nếu có `X-Idempotency-Key` & đã có attempt với key đó ⇒ trả lại (`:100-104`, unique `uq_lms_attempts_start_idempotency`, V30). Sau đó `LmsSyncOperationService.reserve("quiz.attempt.start")`; replay ⇒ trả kết quả đã lưu.
4. Nếu đang có attempt `in_progress` cho (assignment, quiz, student) ⇒ **resume** (kiểm package id/hash khớp, cập nhật `lastActiveAt`) (`:109-119`).
5. Ngược lại: `ensureOpen` (chưa tới `startAt` hoặc quá `dueAt` ⇒ 400; **không** xét `assignment.status`) (`:328`); `validatePackageContract` – nếu có package active thì `packageId` và `packageHash` client gửi phải khớp (hash chuẩn = `content_hash` package) (`:265-274`); không có package (quiz legacy) ⇒ tin request (cầu nối tương thích); `ensureLimit` – `attemptLimit>0` và số attempt đã hoàn tất ≥ limit ⇒ 409 `attempt limit reached` (`:329`).
6. Tạo attempt: `status=in_progress`, gắn `packageId/hash/quizVersionId/quizVersion/gradingMode`, `mode` = `testing` nếu quiz.kind là `test|testing` else `training` (`:354`), `policySnapshot` (assignmentId, quizId, version, package, teacherId, questionCount, durationMinutes, `deadlineAt = min(dueAt, startedAt+duration)`, attemptLimit, gradingMode, `offlineAllowed = assignment.offlineAllowed && gradingMode=="server-authoritative"`, startedAt, serverTimeAtStart, sourceAssignmentUpdatedAt) (`:290-313`).
7. Ghi log `EXAM_ATTEMPT_START/RESUME`, evict 6 cache sau commit (`:33-34, 390`).

**Lưu nháp/đáp án/sync** (`saveDraft`, `saveAnswer`, `sync`, `:145-170`): chỉ **kiểm quyền & trạng thái** rồi trả; **không ghi gì** (đáp án nằm ở client cho tới khi submit). `saveAnswer` từ chối `questionId` chứa `.`/`$`. `sync` trả `conflict` nếu `packageHash` lệch.

**Submit** `submit` (`:172-220`):
1. Tải attempt + kiểm lớp (`mutable`); `packageHash` request (nếu có) phải khớp ⇒ nếu không 400 `package_hash conflict`.
2. Idempotency submit theo `idempotencyKey` (receipt `quiz.attempt.submit`); cùng key đã submit ⇒ trả kết quả đã lưu (`:181-184`); attempt đã không còn editable ⇒ trả kết quả hiện có (idempotent, không chấm lại) (`:185-189`); khác key trong khi đã có key ⇒ 409 `attempt submission is already in progress` (`:190-192`).
3. Gộp đáp án gửi lên vào `answers`; **giờ nộp = giờ server** (`submittedAt` client bị bỏ qua) (`:196-197`). `ensureSubmitAllowed`: nếu `snapshot.deadlineAt` bị vượt ⇒ 400 `attempt duration exceeded` hoặc `assignment is past due` (`:330-348`); fallback dùng `dueAt`/`maxDurationMinutes` của assignment nếu snapshot thiếu.
4. Chấm: nếu attempt có `quizVersionId` ⇒ `QuizAttemptGradingService.gradeSubmission` (server-authoritative **bất kể** gradingMode) (`:202-203`); nếu có câu manual ⇒ status `needs_review`, ngược lại `submitted`. Nếu **không có** version (quiz legacy) ⇒ **điểm = số câu đã trả lời**, `max = số questionIds` (tối thiểu 1), `passed = percent ≥ 50` – không đối chiếu đúng/sai (`:204`).
5. Ghi attempt, `attempt_events` (`attempt_submitted` + batch `clientEvents`, idempotent theo event id), cập nhật `student_progress` (best-effort: lỗi chỉ log WARN), evict cache, metric `erg.lms.attempt.submit{result}`.

### 3.4 Chấm điểm

`QuizAttemptGradingService.gradeSubmission` (`B/service/quiz/runtime/QuizAttemptGradingService.java:76-150`):
- Duyệt `quiz_version_questions` của version ghim; câu `survey` không tính vào mẫu số; câu `manual` ⇒ `needsManualGrade`, 0 điểm; còn lại gọi `GradingEngine.grade`.
- `percent = awarded/max*100` (0..100), **`passed = !anyManual && percent ≥ 50`** (`:49, 139`). Tức attempt có essay luôn `passed=false` cho tới khi (không tồn tại luồng) chấm tay.
- Upsert `attempt_answers` từng câu (chỉ ghi đè nếu `gradedAt` mới hơn) (`:152-181`).
- `projectProgress` upsert `student_progress` theo (tenant, studentUserId, quizId, assignmentId) (`:184-227`; unique `uq_student_progress_attempt_scope`, V27).
`GradingEngine` (`:59-190`): single/true_false khớp `correctChoiceId`; multiple_response all-or-nothing hoặc `(đúng−sai)/tổng_đúng` sàn 0 khi `acceptPartial`; short_answer so khớp sau trim+lowercase; numeric trong `tolerance`; sequence theo vị trí; matching/drag_drop/fill_blank/select/drag_words theo tỉ lệ; hotspot điểm trong vùng chữ nhật; hỗ trợ alias payload FE (`textResponses`, `numericValue`, `sequenceOrder`, `matchingAssignments`, `dragDropPlacements`, `hotspotPoint`…) (`:214-272`); kind lạ ⇒ `IllegalArgumentException` (fail-closed).

### 3.5 Đọc/báo cáo (read model)

- Học sinh: `GET /api/v1/lms/students/me/assignments|scores` (`LmsStudentReadModelUseCase.listAssignments :34-42` cache `lmsStudentAssignments`; `listScores :44-52`), size tối đa 100.
- Giáo viên: `teacherHomeworkWorkspace` (cards, pending review, overdue; size ≤50, cache `lmsHomeworkWorkspace`), `assignmentProgressWorkspace` (summary, attempts, recipients, missing/late; cache `lmsAssignmentProgress`), `classWorkspace` (roster + assignments, score/risk summary tuỳ quyền; ngưỡng rủi ro 50%), `studentGroups` (tối đa 200 nhóm).
- Báo cáo quiz: `QuizReportService` đọc từ bảng chuẩn hoá (`attempt_answers`, `student_progress` + aggregate bảng V69), cache `quizQuestionAnalytics`, `quizAssignmentReport` (`:50-130`).
- Bộ lọc trạng thái dùng `LmsStatePolicy.assignmentFilter/attemptFilter` – giá trị lạ ⇒ 400 `unsupported … status` (không nới rộng truy vấn) (`:22-28, 74-81`).

### 3.6 Danh mục vận hành, điểm danh, sổ điểm

`LmsOperationalCommandService` (`B/service/lms/LmsOperationalCommandService.java`), mọi thao tác ghi `UserActivity` audit và `@CacheEvict` các cache thư mục:
- Class: tạo (unique `(tenant, school, name ignore case, academicYear)` – check + index `uq_academic_classes_tenant_school_year_name` V88), sửa/ archive yêu cầu `version` khớp `recordVersion` (409 nếu lệch, 400 nếu thiếu) (`:67-129`).
- Student: mã HS unique theo tenant (ignore case), `academicClassId` chỉ được thiết lập khi tạo; đổi lớp phải qua **enroll** (`updateStudent` ném 409 "Move the student through the enrollment command") (`:131-192`).
- `importStudents`: lặp từng item; trùng mã + cùng tên ⇒ replay; trùng mã khác tên ⇒ 409; `idempotencyKey` chỉ ghi vào audit, **không** dùng để dò replay (`:194-217`).
- Subject (LMS cũ `lms_subjects`): mã unique theo tenant; scope tenant hoặc school (`:219-272`).
- `enroll` (`:274-306`): idempotent theo key (`uq_lms_enrollment_tenant_idempotency`); không cho ghi danh lặp (status `ENROLLED` cùng student+class+year ⇒ 409; unique `uq_lms_enrollment_current_membership`); đồng thời **ghi đè** `student.academicClassId` = lớp mới và status `ENROLLED`; enrollment cũ ở lớp trước **không** bị đóng. `bulkEnroll` gọi `enroll` từng item với key `root:studentId:classId`.
- Status hợp lệ: `ACTIVE, INACTIVE, ARCHIVED, DRAFT, ENROLLED` (`:43`).

`LmsAcademicRecordUseCase` (GraphQL: `upsertAttendance`, `upsertGradebookScore`, `attendanceWorkspace`, `gradebookWorkspace`):
- Điểm danh: status ∈ {PRESENT, ABSENT, LATE, EXCUSED}, `lessonIndex` 1..20, khoá tự nhiên `(tenant,class,student,subject,date,lesson)` (unique `uq_lms_attendance_natural`, V85); optimistic lock bằng `expectedVersion` (`STALE_WRITE` qua `SyncConflictException`); truy vấn khoảng ngày ≤ 93 ngày (`:46-134`).
- Sổ điểm: `kind ∈ {TOPIC, QUIZ, OFFLINE}`, `0 ≤ score ≤ maxScore`, `maxScore>0`; upsert cột (tự cập nhật label/max/position) rồi điểm (`:136-194`).
- Cả hai đi qua receipt offline `lms_sync_operations` (V89) nếu client gửi `operationId/idempotencyKey`.
- Không kiểm học sinh thuộc lớp hay quiz/column tồn tại ngoài phạm vi `assertClassScope` ở controller.

### 3.7 Curriculum (taxonomy)

- Cây: `CurriculumSubject → CurriculumLevel → CurriculumTopic`; mã (`code`) unique theo cha (`uq_curriculum_subjects_tenant_code`, `…levels_tenant_subject_code`, `…topics_tenant_level_code`, V36). Status `active|archived|draft` (`CurriculumCommandUseCase.java:28-31`).
- Tạo cấp con yêu cầu cha **active** (`CURRICULUM_PARENT_NOT_ACTIVE`) (`:95-96, 140-143`). `archive` chỉ đặt status (không cascade, không check impact). **Hard delete** chỉ khi `impact.canHardDelete` (không còn câu hỏi/quiz/version đã publish/content/assignment/attempt/con) (`:272-278`, `CurriculumImpactQueryUseCase.java:111-138`).
- `GET tree` giới hạn 500 node mỗi tầng (`CURRICULUM_TREE_TOO_LARGE`), mặc định chỉ `active`, cache `curriculumTree` (`CurriculumTreeQueryUseCase.java:36, 51-93`; `CurriculumOperations.java:54-58`).
- `CurriculumProgramService` (`PUT/GET /program`): chương trình theo (subject, level, academicYear) (unique V106), ghi đè toàn bộ dòng; tự bỏ tiền tố `subject-/level-/category-` khỏi id; `expectedVersion` tuỳ chọn (`:40-72`).
- `CurriculumTeachingPlanService`: cấu hình lịch dạy (startDate, `sessionsPerDay` 1..8, `teachingDays` 0..6, timezone mặc định `Asia/Ho_Chi_Minh`), kiểm phạm vi trường (người có `curriculum.manage|*` bỏ kiểm) (`:21-73`).
- Tenant mặc định `"erg"` nếu `access` thiếu tenant (`CurriculumCommandUseCase.java:325-327`, tương tự tree/impact/chain).

### 3.8 Vòng đời nội dung (content lifecycle)

`LifecycleTransitionPolicy` (`B/content/lifecycle/LifecycleTransitionPolicy.java:33-60`) – ma trận hợp lệ:

```
DRAFT -> IN_REVIEW | ARCHIVED
IN_REVIEW -> APPROVED | DRAFT | ARCHIVED
APPROVED -> PUBLISHED | DRAFT | ARCHIVED
PUBLISHED -> ARCHIVED | (DRAFT chỉ khi quizRollback)
ARCHIVED -> DRAFT
```
Bắt buộc `reason` khi → ARCHIVED, PUBLISHED→DRAFT, IN_REVIEW→DRAFT, rollback quiz. `validateValidatedQuizPublish` là cầu nối nội bộ cho publish quiz (không cho PUBLISHED/ARCHIVED).

`ContentLifecycleOperations.transition` (`B/content/lifecycle/ContentLifecycleOperations.java:84-141`): kiểm principal khớp context (`requireAccess :176-191`); quyền theo đích: IN_REVIEW=`content.lifecycle.submit`, APPROVED=`.approve`, PUBLISHED=`.publish` (quiz: `lms.quiz.publish`), ARCHIVED=`.archive`, DRAFT=`.rollback`; `content.manage`/`curriculum.manage` thay thế (`:215-247`); idempotency theo `(tenant,kind,resource,key)` – replay chỉ khi cùng target/expectedVersion/operationId/sourceVersion/reason, ngược lại `IDEMPOTENCY_CONFLICT` (`:96-110`); `expectedVersion` lệch ⇒ `STALE_WRITE`; **người submit review không được tự approve** trừ khi có quyền elevated (`:249-255`); PUBLISHED của quiz không đi đường này mà phải qua publish contract (`:124-129`); rollback quiz tạo draft mới từ snapshot version (`:296-317`). Loại tài nguyên: `CURRICULUM_SUBJECT|LEVEL|TOPIC`, `CONTENT_ITEM`, `QUESTION`, `QUIZ` (`LifecycleResourceKind.java`).
`statusFor` (`:422-426`) đồng bộ cột `status` = tên trạng thái chữ thường (riêng PUBLISHED của curriculum → `active`) – xem rủi ro §9.

### 3.9 Learning content & thư viện tài nguyên

`LearningContentService` (legacy, @Deprecated nhưng đang là implement thật) – `B/service/learningcontent/LearningContentService.java`:
- `createContentItem` (status mặc định `draft`, visibility `private`, `contentType` upper-case), `updateContentItem` (khoá lạc quan `expectedVersion` + receipt `lms.content_item`), `deleteContentItem` (**hard delete** + xoá placement/asset-link, `:293-312`), `createContentAsset/updateContentAsset` (merge metadata), `attachContentAsset` (unique item+asset+role, V37), `placeContentItem` (topic phải `active` trong tenant – `existsActiveTopicInTenant`, `:445`), `removePlacement`, `reorderPlacements` (tất cả placement phải thuộc topic), `createGoogleSlidesLecture` (URL chỉ nhận `docs.google.com/presentation/d/.../(embed|edit|present|pub)`; tạo item `LECTURE` **status published, visibility public** + asset `GOOGLE_SLIDE` + placement trong một transaction) (`:186-244`).
- `LearningResourceLibraryReadService.learningResourceLibrary` (`:66-128`): cây taxonomy (≤500 node/tầng) + đếm tài nguyên + trang card (size mặc định 20, tối đa 50); cache `learningResourceLibrary` (key gồm userId). Filter `status=active` được hiểu là `active|published`.

### 3.10 Learning outcomes

`LearningOutcomesService` (`B/learningoutcomes/internal/LearningOutcomesService.java`):
- **Path**: `createPath` (DRAFT, scope theo education unit/class) → `addNode` (chỉ khi DRAFT, `expectedPathVersion` khớp, ≤100 node, trùng `contentRefId` bị cấm) → `publishPath` (DRAFT→PUBLISHED, cần ≥1 node, `expectedVersion`) (`:162-267`).
- **Competency**: `createCompetency` (code upper-case unique theo tenant, status ACTIVE).
- **Attainment**: `recordAttainment` (`:336-398`): `progressPercent` mặc định 100 (0..100), `ATTAINED` nếu 100 else `IN_PROGRESS`; nếu có `learningPathId` thì path phải PUBLISHED và cùng lớp; **mỗi (learner, competency, path) chỉ ghi một lần** (409 nếu đã có) – bản `IN_PROGRESS` không thể nâng lên `ATTAINED` (§9).
- **Completion**: `evaluateCompletion` (`:412-478`): client gửi `completedContentRefIds`; mỗi ref phải thuộc path; `progress = hoàn_thành_bắt_buộc/tổng_bắt_buộc` (100 nếu không có node bắt buộc); `COMPLETED` nếu đủ node bắt buộc; upsert theo (learner,path); `completedNodeCount` = số ref gửi lên (kể cả node không bắt buộc).
- **Certificate**: `issueCertificate` (`:497-550`): path PUBLISHED, completion `COMPLETED`, `expiresAt` tương lai, mỗi learner–path một chứng chỉ, `certificateNumber` unique theo tenant, `verificationId` = id mới (unique `ux_outcome_certificates_verification`); `revokeCertificate` (cần `learning.outcomes.manage` hoặc `*`, `expectedVersion`, `reason`); `verifyCertificate` tra theo `verificationId` **trong tenant & scope của path**; status hiệu lực trả `EXPIRED` khi quá hạn (`:777-781`).
- Mọi lệnh: bắt buộc `Idempotency-Key` (≤255), receipt `(tenant,key,operation)` với **hash payload** → cùng key khác payload ⇒ 409; khoá advisory `pg_advisory_xact_lock(hash(tenant:operation:key))` chống đua (`:614-639`); audit (`AuditOperations`) + publish domain event (Spring Modulith) kèm `correlationId` (`:183-190`).
- Truy cập learner: chính learner hoặc người có `learning.outcomes.manage|*` (`:654-672`).

### 3.11 Learning standards (SCORM / LTI 1.3 / xAPI)

`LearningStandardsService` (`B/learningstandards/internal/LearningStandardsService.java`):
- **Package** `UPLOADING → VALIDATING → READY | QUARANTINED`: `createPackage` (standard `SCORM_12|SCORM_2004`, `byteSize ≤ 512MB`, `artifactKey`/`launchPath` an toàn – không `..`, `\`, scheme, `/` đầu; sha256 artifact/manifest; unique `(tenant,artifact_key)`) (`:104-150`); `validatePackage` (`:165-216`): đối chiếu manifest XML (regex, chặn DOCTYPE/ENTITY, `schemaVersion` đúng chuẩn, tối đa 512K ký tự) + `storage.head(tenant, artifactKey)` khớp size/sha + hash manifest khớp đã đăng ký ⇒ `READY` (event `LearningPackageReady`) hoặc `QUARANTINED` (kèm `quarantineReason`, event `LearningPackageQuarantined`); `launchPackage` chỉ khi `READY`.
- **LTI**: `createLtiRegistration` (issuer https, `allowedTargetHost`, PEM RSA public; unique `(tenant,issuer,client_id)`); `launchLti` (`:281-312`) xác thực JWT RS256 (iss, aud, deployment_id, nonce khớp, `exp/iat`, `target_link_uri` https + host allow-list) – **không gọi JWKS từ xa** (`RsaLtiLaunchTokenVerifier.java:45-81`); phát `LtiLaunchAccepted`; replay theo receipt (key mặc định = sha256(token)).
- **xAPI**: `ingestXapi` (cần `learning.standards.manage`): verb/object phải là URI http(s) tuyệt đối; **không lưu body**, chỉ `payloadSha256` + `actorHash` (sha256 actorId); trùng `statementId` khác payload ⇒ 409; phát `XapiStatementAccepted` → `XapiStatementAcceptedConsumer` (`@ApplicationModuleListener`) → `XapiStatementForwarder` (mặc định `NoopXapiStatementForwarder` chỉ log DEBUG).
- Quyền kiểm cả `deniedPermissions` (từ chối trước) rồi `permissions`/`*` (`:416-425`).

### 3.12 E-learning học sinh

- `StudentDashboardReadService.studentDashboard` (`B/service/elearning/readmodel/StudentDashboardReadService.java:120-203`): chỉ chính học sinh (tenant/studentUserId yêu cầu phải trùng principal); quyền `elearning.assignment.read_self` (service, `:415-419`) **và** `elearning.dashboard.read` (annotation GraphQL); lớp được chọn phải nằm trong ghi danh `ENROLLED` hoặc lớp legacy (nếu không ⇒ 403) (`:376-396`); trả 8 bài đầu, đếm active/pending, điểm tổng hợp (chỉ `submitted|graded|completed`), 5 thông báo (cache `studentDashboard`).
- `StudentEngagementService` (JDBC, `B/service/elearning/engagement/StudentEngagementService.java`): feed thảo luận của **lớp hiện tại** của học sinh (size ≤50); `createDiscussion` (title ≤250, content ≤4000, idempotent theo `(tenant, author, key)` + xử lý đua `DataIntegrityViolationException`); `reply` (post phải thuộc lớp; parent comment phải thuộc post; cập nhật `latest_activity_at`); `setReaction` (POST|COMMENT; 7 loại reaction; bấm lại cùng loại = bỏ); `markAnnouncementRead` (thông báo phải `PUBLISHED` và target ALL/lớp/học sinh). Giáo viên không dùng được các lệnh này (yêu cầu có bản ghi `Student` gắn `authUserId` và có lớp, `:189-192`).

### 3.13 Media & gói offline

- `MediaService` (`B/service/quiz/media/MediaService.java`): presign nhận `sha256` (64 hex), MIME chỉ `png/jpeg/webp/gif` (SVG bị loại vì XSS), ≤15MB; nếu cùng tenant+sha đã `committed` ⇒ trả `deduped` ngay, ngược lại tạo asset `pending` + URL upload ký hạn 5 phút (Local) hoặc presigned PUT (R2); `commit` đối chiếu `headObject` (size, sha, mime) rồi đặt `committed`. PUT upload local là `permitAll` ở `SecurityConfig` nhưng bắt buộc chữ ký HMAC trong URL (`SecurityConfig` dòng `PUT /api/v1/lcms/media/upload/quiz-media/**`, `LocalMediaStorageAdapter.java:81-101`).
- `PortableQuizBundleService` (`B/service/quiz/bundle/PortableQuizBundleService.java`): export active (rơi về version cũ hơn nếu package bị revoke/hết hạn), manifest, update feed (kênh `stable|beta|canary`, ≤100 mục, chỉ package `portableReady` & tương thích `minRuntimeVersion`), `revoke` (idempotent, lý do 1..256). `portableReady` chỉ khi hash sha256 + chữ ký + `publicKeyId` + thuật toán **Ed25519** + chưa revoke/hết hạn + không có `url/src` http(s) ngoài (`:332-366`). Bundle gỡ khoá nhạy cảm (`accessToken`, `password`, `apiKey`…) và gỡ `answerKey` khi server-authoritative (`:404-445`).

---

## 4. Quy tắc nghiệp vụ, validation, phân quyền

### 4.1 Cơ chế phân quyền
- **URL-level** (`B/platform/security/SecurityConfig.java`): `/api/v1/curriculum/**` & `/api/v1/content/**` – GET cần `content.resource.read|content.manage|lms.resource.read|*` (`:175-176`); POST/PATCH/DELETE `/api/v1/content/**` cần `lms.resource.create|update|delete` hoặc `content.manage` (`:177-182`); placement `/curriculum/topics/*/content-items/**` cần `lms.resource.share|content.manage|curriculum.manage` (`:183-184`); còn lại curriculum/content cần `content.manage|curriculum.manage|*` (`:185-186`); `/api/v1/elearning/**` và admin cần `elearning.manage` (`:218-219`). `/api/v1/graphql` yêu cầu authenticated (`:128`). Controller curriculum **không** có `@PreAuthorize` riêng.
- **Method-level** (`@PreAuthorize("@methodAuthorization.isAllowedAny(authentication, …)")`) qua meta-annotation, định nghĩa trong `B/lms/authorization/*`, `B/learningoutcomes/authorization/*`, `B/learningstandards/authorization/*`, `B/elearning/authorization/*`, `B/content/lifecycle/authorization/ContentLifecycleTransition.java`. Quyền tương ứng (luôn kèm `*`):

| Annotation | Quyền chấp nhận |
|---|---|
| `@LmsAssignmentCreate/Update/Assign/Delete` | `lms.assignment.create/update/assign/delete` |
| `@LmsStudentGroupCreate/Update/Delete` | `lms.student_group.create/update/delete` |
| `@LmsSubmit` | `lms.assignment.submit` |
| `@LmsAssignmentReadSelf` | `lms.assignment.read_self` |
| `@LmsAssignmentRead` | `lms.assignment.read` |
| `@LmsAssignmentProgressRead` | `lms.assignment.progress.read`, `lms.grade.read`, `lms.manage` |
| `@LmsClassRead` | `lms.class.read`, `lms.manage` |
| `@LmsGradeRead` / `@LmsQuizRead` | `lms.grade.read` / `lms.quiz.read` |
| `@LmsResourceRead` | `lms.resource.read`, `content.resource.read`, `content.manage` |
| `@LearningOutcomesRead/Write/IssueCertificate` | `learning.outcomes.read\|manage` / `.manage` / `.issue_certificate\|manage` |
| `@LearningStandardsRead/Write/Launch` | `learning.standards.read\|manage` / `.manage` / `.launch\|manage` |
| `@ElearningDashboardRead/DiscussionWriteSelf/AnnouncementReadSelf` | `elearning.dashboard.read` / `.discussion.write_self` / `.announcement.read_self` |
| `@ContentLifecycleTransition` | `content.manage`, `curriculum.manage`, `content.lifecycle.submit\|approve\|publish\|archive\|rollback`, `lms.quiz.publish` |
| LCMS quiz: `@LcmsQuizCreate/Update/Delete`, `@LcmsQuizPublish`, `@LcmsQuizBundleRead`, `@LcmsQuestionCreate/Update/Delete`, `@LcmsMediaWrite` (`B/lcms/authorization/*`) | tạo/sửa: `lms.exercise.create\|update\|delete` hoặc `lms.quiz.create`; **publish & revoke package**: `lms.quiz.publish`; bundle: `lms.quiz.read`; media: `lms.quiz.create` |

- **GraphQL** thêm kiểm trong controller bằng `GraphQlAccessSupport`: `assertPermission`, `verifiedTenantId` (không cho tenant khác principal), `assertClassScope`, `assertEducationUnitScope`; điểm danh: `lms.attendance.read|write`, sổ điểm `lms.grade.read|write` (+`lms.manage`) (`LmsAcademicRecordGraphQlController.java:26-62`); Class workspace: score summary chỉ khi có `lms.grade.read|lms.manage`, risk summary khi có `lms.progress.read|lms.grade.read|…` (`LmsClassWorkspaceGraphQlController.java:35-43`); assignment progress: sau khi tải, kiểm lại phạm vi theo `assignedBy` hoặc lớp được quản lý (`LmsHomeworkWorkspaceGraphQlController.java:97-114`).
- **Phạm vi lớp/trường** ở runtime: `UserAccessContext.canAccessClass/canAccessEducationUnit` trong start/submit/progress/read-model (`LmsAttemptUseCase.java:350`, `LmsStudentReadModelUseCase.java:89-96`).

### 4.2 Quy tắc then chốt (tóm tắt, kèm tham chiếu)
- Tenant luôn từ context (`AuthenticatedTenant.require`), không từ body (`LmsAttemptUseCase.java:412`); đọc theo `tenantId` ở mọi repository.
- Idempotency: assignment (header), quiz create/publish (header bắt buộc khi publish), attempt start/submit (header + receipt), lifecycle (bắt buộc), outcomes/standards (bắt buộc, hash payload), enrollment/import (header), thảo luận (bắt buộc).
- Tối ưu khoá: `recordVersion` ở Attempt/Assignment/Class/Student/Subject/Program/ContentItem…; `docVersion` cho draft (tuỳ chọn!).
- Hết hạn nộp: dùng giờ server + `deadlineAt` ghim lúc start (`LmsAttemptUseCase.java:196-197, 330-348`).
- Package bất biến & anti-leak: `server-authoritative` không có `answerKey`; `QuizRuntimePackageService` xoá thêm `answerKey` phòng thủ (`:96-103`); `client-first` **gửi đáp án cho client** (thiết kế để chấm offline).
- Taxonomy: cha phải active khi tạo con; hard delete chỉ khi không còn phụ thuộc.
- Lifecycle: ma trận trạng thái cố định + lý do bắt buộc + tách người submit/approve.
- Outcomes: không cấp chứng chỉ nếu path chưa PUBLISHED hoặc learner chưa COMPLETED; idempotency payload-bound.

---

## 5. API

### 5.1 REST

**LMS runtime** – `B/controller/lms/LmsController.java` (`@RequestMapping("/api/v1/lms")`)

| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| GET | `/students/me/assignments?page&size` | `lms.assignment.read_self` | Bài được giao cho tôi (mặc định size 50) |
| GET | `/students/me/scores?page&size` | `lms.assignment.read_self` | Điểm (3 attempt gần nhất/assignment) |
| GET | `/quizzes/{quizId}/package` | `lms.quiz.read` | Package runtime; **ETag = contentHash**, `Cache-Control: private, max-age=300, must-revalidate`, hỗ trợ `If-None-Match` → 304 |
| GET | `/quizzes/{quizId}` | `lms.quiz.read` | Chi tiết quiz (suy ra từ package) |
| POST | `/attempts` | `lms.assignment.submit` | Start/resume attempt (`X-Idempotency-Key`) |
| PATCH | `/attempts/{id}/draft`; PUT `/attempts/{id}/answers/{questionId}`; POST `/attempts/{id}/answers` | idem | **No-op tương thích** (đáp án ở client) |
| POST | `/attempts/{id}/sync` | idem | Kiểm `packageHash`, trả `conflict` nếu lệch |
| POST | `/attempts/{id}/submit` | idem | Nộp & chấm |
| GET | `/quizzes/{quizId}/students` | `lms.grade.read` | Danh sách attempt (≤500) lọc theo phạm vi lớp |
| GET | `/assignments/{id}/progress` | `lms.assignment.progress.read` | Đếm submitted/inProgress/notStarted/needsReview |
| GET | `/education-units`, `/classes?schoolId` | `lms.class.read`/`lms.manage` | Đơn vị & lớp theo phạm vi |

**LMS distribution** – `B/controller/lms/LmsAssignmentController.java`: POST `/api/v1/lms/assignments` (create, header `X-Idempotency-Key`), PATCH `/assignments/{id}`, PUT `/assignments/{id}/recipients`, DELETE `/assignments/{id}`, POST/PUT/DELETE `/student-groups[/{groupId}]`.

**Quiz authoring (LCMS)** – `B/controller/lcms/LcmsQuizAuthoringController.java`: POST `/api/v1/lcms/quizzes`; PUT|PATCH `/quizzes/{id}/draft`; POST `/quizzes/{id}/slides`, `/reorder`, `/validate`, `/publish` (`lms.quiz.publish`), `/versions/{versionId}/fork`; DELETE `/quizzes/{id}`. `LcmsQuestionBankAuthoringController`: POST/PUT/DELETE `/api/v1/lcms/questions[/{id}]`. `LcmsMediaController`: POST `/media/presign`, POST `/media/{id}/commit`, PUT `/media/upload/quiz-media/{tenantId}/{prefix}/{filename}`. `LcmsQuizBundleController`: GET `/quizzes/{id}/bundle`, `/quiz-versions/{id}/bundle`, `/quiz-packages/{id}/manifest`, `/quiz-packages/{id}/download` (302 tới `downloadUrl`), `/quiz-packages/updates?quizId&channel&currentVersion&runtimeVersion`; POST `/quiz-packages/{id}/revoke`.

**Curriculum** – `B/controller/curriculum/CurriculumController.java` (`/api/v1/curriculum`): GET `/tree?status`; POST `/subjects`; PATCH `/subjects/{id}`; POST `/subjects/{id}/archive`; DELETE `/subjects/{id}` (hard); GET `/subjects/{id}/impact`; POST `/subjects/{subjectId}/levels`; PATCH|POST archive|DELETE|GET impact cho `/levels/{id}`; POST `/levels/{levelId}/topics`; PATCH|archive|DELETE|impact cho `/topics/{id}`; GET|PUT `/program?subjectId&levelId&academicYear`; GET|PUT `/teaching-plan?schoolId&subjectId&academicYear`.

**Learning content** – `B/controller/learningcontent/LearningContentController.java` (cả tiền tố `/api/content` và `/api/v1/content`): POST `/lectures/google-slides`, POST `/items`, PATCH|DELETE `/items/{id}`, POST `/assets`, PATCH `/assets/{id}`, POST `/items/{id}/assets`; POST|DELETE `/curriculum/topics/{topicId}/content-items[/{placementId}]`, PATCH `…/content-items/reorder`.

**Content lifecycle** – `B/controller/content/ContentLifecycleController.java`: POST `/api/v1/content/{resourceKind}/{resourceId}/lifecycle` (body: `targetState`, `reason`, `expectedVersion`, `idempotencyKey`, `operationId`, `sourceVersionId`).

**Learning outcomes** – `B/controller/learningoutcomes/LearningOutcomesController.java` (`/api/v1/learning-outcomes`): POST|GET `/paths`; POST `/paths/{id}/publish`, `/paths/{id}/nodes`, `/paths/{id}/completion`; GET `/paths/{id}`; POST|GET `/competencies`, GET `/competencies/{id}`; POST `/attainments`; GET `/learners/{id}/attainments|completions|certificates`; POST `/certificates`, `/certificates/{id}/revoke`; GET `/certificates/verify/{verificationId}`.

**Learning standards** – `B/controller/learningstandards/LearningStandardsController.java` (`/api/v1/learning-standards`): POST|GET `/packages`, GET `/packages/{id}`, POST `/packages/{id}/validate`, GET `/packages/{id}/launch`; POST `/lti/registrations`, POST `/lti/registrations/{id}/launch`; POST `/xapi/statements`.

**Quản trị danh mục (LCMS, tham chiếu)** – `B/controller/lcms/LmsOperationalCommandController.java` (`/api/v1/lcms`): POST|PUT|PATCH|DELETE `/schools/{schoolId}/classes[/{id}]`, `/schools/{schoolId}/students[/{id}]`, POST `/students/import|bulk`, `/subjects`, `/enrollments`, `/enrollments/bulk`.

### 5.2 GraphQL (endpoint `POST /api/v1/graphql`; schema ở `src/main/resources/graphql/modules/*`)

| Operation | Resolver | Mô tả |
|---|---|---|
| `Query.lms.learningResourceLibrary` | `LmsLearningResourceGraphQlController` | Thư viện tài nguyên (taxonomy + cards) |
| `Query.lms.classWorkspace` | `LmsClassWorkspaceGraphQlController` | Roster + assignments + score/risk summary |
| `Query.lms.teacherHomeworkWorkspace` / `assignmentProgressWorkspace` / `studentGroups` | `LmsHomeworkWorkspaceGraphQlController` | Dashboard bài tập GV |
| `Query.lms.attendanceWorkspace` / `gradebookWorkspace`; `Mutation.upsertAttendance` / `upsertGradebookScore` | `LmsAcademicRecordGraphQlController` | Điểm danh / sổ điểm (optimistic version + receipt) |
| `Query.lms.learningOutcomes` / `learningOutcomePath` / `verifyOutcomeCertificate`; `Mutation.createLearningPath` / `publishLearningPath` / `addLearningPathNode` / `createCompetency` / `recordCompetencyAttainment` / `evaluateLearningPathCompletion` / `issueOutcomeCertificate` / `revokeOutcomeCertificate` | `LearningOutcomesGraphQlController` (`35-learning-outcomes.graphqls`) | Outcomes |
| `Query.elearning.studentDashboard` | `StudentDashboardGraphQlController` | Dashboard học sinh |
| `Query.elearning.discussionFeed`; `Mutation.createStudentDiscussion` / `replyStudentDiscussion` / `setStudentDiscussionReaction` / `markStudentAnnouncementRead` | `StudentEngagementGraphQlController` | Thảo luận + thông báo |
| `Query.lcms.quizWorkspace` / `quizDraft` / `quizVersions` / `questionBankWorkspace` / `quizQuestionAnalytics` / `assignmentReport` | `controller/lcms/graphql/*` (slice LCMS) | Đọc quiz/ngân hàng/báo cáo |
| `Mutation.createSchoolClass … bulkEnrollSchoolStudents` | `LcmsSchoolOperationsMutationGraphQlController` (`45-school-operations-mutations.graphqls`) | Quản trị lớp/HS/môn/ghi danh |

Không có GraphQL cho curriculum/learning-standards/content-lifecycle (chỉ REST).

---

## 6. Dữ liệu

### 6.1 Quan hệ chính
```
curriculum_subjects 1─n curriculum_levels 1─n curriculum_topics 1─n topic_content_items n─1 content_items 1─n content_item_assets n─1 content_assets
lms_quizzes 1─n quiz_drafts ; 1─n quiz_versions(1 active) 1─n quiz_version_sections/quiz_version_questions ; quiz_versions 1─1 quiz_packages
lms_quizzes n─n lms_questions (lms_quiz_questions)
lms_assignments 1─n assignment_resources(quiz+version) ; 1─n assignment_recipients(student) ; (legacy) academic_class_id/quiz_id/student_ids
lms_assignments 1─n lms_attempts 1─n attempt_answers / attempt_events ; student_progress(tenant,student_user,quiz,assignment)
academic_classes 1─n lms_students ; lms_student_enrollments (student,class,year)
learning_paths 1─n learning_path_nodes ; learning_path_completions(learner,path) ; competency_attainments ; outcome_certificates(learner,path)
learning_standard_packages / lti_registrations / xapi_statements + command_receipts
content_lifecycle_transitions(tenant, resource_kind, resource_id)
```

### 6.2 Unique / index đáng nhớ
- Quiz: `uq_quiz_versions_quiz_number`, `uq_quiz_versions_quiz_label`, **`uq_quiz_versions_active` (partial: 1 active/quiz)**, `uq_quiz_versions_publish_idempotency` (V76), `uq_quiz_packages_version` (V26), `uq_lms_quizzes_tenant_idempotency` (V76), `uq_lms_quiz_questions_quiz_question` (V33).
- Giao bài/attempt: `uq_assignment_recipients_assignment_student` (V28), `uq_lms_assignments_tenant_idempotency` (V76), `uq_lms_attempts_start_idempotency` (partial, V30), `uq_lms_attempts_submit_idempotency` (V87), `uq_student_progress_attempt_scope` (V27). **Không có** unique/ràng buộc ngăn nhiều attempt `in_progress` cho cùng (assignment,quiz,student).
- Vận hành: `uq_academic_classes_tenant_school_year_name`, `uq_lms_students_tenant_code`, `uq_lms_subjects_tenant_code`, `uq_lms_enrollment_tenant_idempotency`, `uq_lms_enrollment_current_membership` (partial `status='ENROLLED'`) (V88); `uq_lms_attendance_natural` (V85); `uq_lms_sync_operation_id (tenant,account,operation)` (V89).
- Curriculum: `uq_curriculum_subjects_tenant_code`, `uq_curriculum_levels_tenant_subject_code`, `uq_curriculum_topics_tenant_level_code` (V36); `uq_curriculum_program_scope`, **`uq_curriculum_program_row_period (program_id, period)`** (V106).
- Content: UNIQUE `(tenant, content_item, content_asset, asset_role)` và `(tenant, topic, content_item, content_role)` (V37); `ux_content_lifecycle_idempotency` (V92).
- Outcomes (V94/V95): `ux_competencies_tenant_code`, `ux_competency_attainments_identity`, `ux_outcome_certificates_number/identity/verification`, `ux_learning_outcome_receipts_key`. Standards (V96): `ux_learning_standard_packages_artifact`, `…lti_registration_identity`, `…xapi_statement_identity`, `…receipts_key`.
- FK: `lms_attempts.assignment_id → lms_assignments ON DELETE RESTRICT`, `quiz_id → lms_quizzes ON DELETE RESTRICT` (V13, ban đầu NOT VALID, validate ở V16); `attempt_answers/attempt_events → lms_attempts` (V34).
- RLS: V29 (quiz), V65/V74/V75/V81 (runtime role + tenant RLS matrix), V90 (enrollment).

### 6.3 Migration liên quan (V…)
V2 learning_resource_schema · V3 lms_runtime_schema · V8 ID mở rộng (UUID v7) · V9 read-model indexes · V13/V16 FK LMS · V23 quiz_authoring_core · V24 question_bank_formalize · V25 media_assets · V26 quiz_versions · V27 attempts_runtime · V28 lms_distribution (assignment_resources/recipients) · V29 quiz_rls · V30 attempt_start_idempotency · V31 student_group_ui_metadata · V32 quiz_bank_hot_path_indexes · V33 question_type & quiz_question relationships · V34 quiz_runtime_relationship_integrity · V36–V39, V41, V42, V45, V46 canonical curriculum & content (+ backfill, cutover, drop bảng hoclieu ở V40) · V49 assessment canonical curriculum cutover · V50 school_operational_core · V52/V54 quyền GV · V66 transactional outbox · V67/V69 report aggregate · V73 modulith event publication · V76 command idempotency uniqueness · V79 attempt score indexes · V83/V84 student engagement · V85 attendance & gradebook · V87 attempt policy snapshot · V88 LMS operational write · V89 offline sync receipts · V90 enrollment RLS · V92 content lifecycle · V94/V95 learning outcomes · V96 learning standards · V98 assignment offline policy · V99 portable package update contract · V101–V103 teaching plan/IC3 · V106–V107 curriculum program. (Số V104–V105 không tồn tại trong thư mục.)
Bảng chưa xác nhận trong migration đã grep: `elearning_*` (không có), `course_progress` (entity có, chưa tìm migration).

---

## 7. Tích hợp ngoài và sự kiện

- **Cache (Spring Cache)** – tên đã thấy: `lmsHomeworkWorkspace`, `lmsClassWorkspace`, `lmsAssignmentProgress`, `lmsStudentAssignments`, `lmsStudentGroups`, `lmsClassesBySchool`, `lmsSchoolSubjects`, `studentDashboard`, `quizAssignmentReport`, `quizQuestionAnalytics`, `quizRuntimePackage`, `lcmsQuestionBankWorkspace`, `lcmsQuizWorkspace`, `lcmsSchoolOperations`, `lcmsSchoolDirectory`, `curriculumTree`, `learningResourceLibrary`. Evict thường qua `TransactionSynchronization.afterCommit` và xoá toàn bộ cache (`clear()`) (`LmsAttemptUseCase.java:390`, `LmsAssignmentCommandService.java:468-485`).
- **Idempotency/offline sync**: `platform.sync.LmsSyncOperationService` (`reserve/complete/abandon/result/serverTime`, bảng `lms_sync_operations`, V89) dùng bởi attempt, assignment, draft quiz, content item/asset, điểm danh/sổ điểm; xung đột trả `SyncConflictException(STALE_WRITE)` (nằm ngoài slice, chỉ đọc cách gọi).
- **Domain event (Spring Modulith `ApplicationEventPublisher`)**: Outcomes phát `LearningPathCreated/Published`, `CompetencyProgressRecorded/Attained`, `LearningPathCompletionEvaluated`, `OutcomeCertificateIssued/Revoked` (kèm `correlationId`); Standards phát `LearningPackageReady/Quarantined`, `LtiLaunchAccepted`, `XapiStatementAccepted`. **Chỉ** `XapiStatementAcceptedConsumer` có listener (→ forwarder noop); các event Outcomes/Standards khác không thấy listener nào trong repo (grep) – chưa có thông báo/đồng bộ hạ nguồn. Bảng `event_publication` (V73) và outbox (V66) tồn tại nhưng slice này không ghi outbox trực tiếp.
- **Audit**: Outcomes/Standards ghi `AuditOperations.record(...)`; vận hành LMS ghi `UserActivity`; lifecycle ghi `content_lifecycle_transitions`.
- **Lưu trữ**: ảnh quiz → `LocalMediaStorageAdapter` (thư mục `.erg-media`) hoặc `R2MediaStorageAdapter` (Cloudflare R2/S3, bật `erg.r2.enabled=true`; timeout 10s/5s) (`MediaStorageConfig.java`); khoá ký URL media phải khác JWT secret ở profile `prod` (`:92-102`). SCORM artifact → `FileSystemLearningStandardsArtifactStorage`.
- **Ký package**: `erg.quiz.package.signing-algorithm` (`HMAC-SHA256` mặc định | `Ed25519`), `signing-key`, `public-key-id`, `ed25519-private-key` (`QuizPackageSigner.java:33-47`).
- **Email / push / AI**: không có trong slice này (không thấy gọi). Thông báo LMS (`LmsNotificationEventPublisherTest`) thuộc module notifications; không thấy assignment-create/submit phát thông báo trong code đã đọc.
- **Seed khởi động** (tuỳ cờ `erg.lifecycle.lms-seed-on-startup`, `question-bank-seed-on-startup`): trường `sch_binh_an`, `sch_cat_lai`, 12 lớp, tài khoản admin, ngân hàng câu hỏi & quiz demo (`LmsBootstrapSeedService`, `LmsAccountBootstrapSeedService` – tenant cố định `erg`).
- **Metrics/log**: timer `erg.lms.attempt.submit{result=passed|failed}`; log `EXAM_ATTEMPT_START|RESUME|DRAFT|SUBMIT` (chứa studentId/score – cần lưu ý PII).

---

## 8. Test hiện có

Đã có (kiểm chứng gì):
- **LMS runtime**: `LmsAttemptPolicyContractTest` (policy snapshot lấy từ server, offline mặc định deny/`client-first` bị từ chối, hash package lệch bị chặn, giờ nộp theo server, replay submit không chấm lại); `LmsRuntimeServiceAttemptRulesTest` (quá hạn, hết lượt, replay start theo key, evict cache sau start/submit, draft/answer/sync không ghi, vượt thời lượng); `LmsRuntimeControllerContractTest`, `LmsControllerHttpCacheTest` (ETag/304); `LmsStatePolicyTest`; boundary/architecture tests cho các use case (`Lms*ArchitectureTest`, `LmsQueryPortBoundaryTest`).
- **Giao bài**: `LmsAssignmentCommandServiceTest` (ghim version active, làm phẳng recipient, idempotent, 0 recipient, thiếu quizIds, group mode, offline opt-in); `LmsOperationalCommandServiceTest` (scope trường, version stale, import replay, enroll replay); `LmsReadModelServiceTest` (826 dòng: ép size, không nested lookup, summary theo quyền); `*QueryCountIntegrationTest` (đếm query workspace); `*RepositoryReadModelContractTest`.
- **Quiz**: `QuizCompileServiceTest` (14 loại, tách answer key, chia điểm 1000), `QuizValidationServiceTest`, `QuizPublishServiceTest` (**chỉ** 1 test: evict cache), `GradingEngineTest` (mọi luật chấm), `QuizReportServiceTest`, `QuizDraftServiceTest`, `QuizAuthoringServiceTest`, `QuestionBankAuthoringServiceTest`, `PortableQuizBundleServiceTest`, `QuizPackageSignerTest`, `QuestionKindRegistryTest`, media tests (Local/R2/validation/upload), `JsonbSchemaMigratorTest`, `LmsAssessmentCurriculumBridgeTest`, `LcmsQuiz*ControllerTest`.
- **Curriculum**: `CurriculumServiceTest` (chain, archive, hard delete, impact), `CurriculumTeachingPlanServiceTest`, architecture tests, route/contract tests.
- **Lifecycle**: `LifecycleTransitionPolicyTest`, `ContentLifecycleOperationsTest` (tenant từ context, stale, idempotency theo kind, tách submitter/approver), migration/authorization contract.
- **Content**: `LearningContentServiceTest`, `LearningResourceLibraryReadServiceTest` (chỉ 2 test), route/boundary tests.
- **Outcomes**: `LearningOutcomesServiceTest` (idempotency + conflict payload, stale publish, cross-learner, scope, completion→certificate→revoke→verify); **Standards**: `LearningStandardsServiceTest`, `RsaLtiLaunchTokenVerifierTest`, `ScormPackageValidatorTest`, `XapiStatementPolicyTest`, storage/consumer tests.
- **E-learning**: `StudentDashboardReadService*Test` (quyền, tenant spoof, scope lớp, bounded), `StudentEngagementServiceTest` (3 test), GraphQL controller/schema tests.
- Test đối chiếu migration (`*MigrationContractTest`) cho các V đã nêu.

Khoảng trống rõ ràng (không thấy trong các test đã liệt kê/grep tên):
1. Không có test cho **assignment nhiều quiz** khi start attempt (chỉ quiz chính được chấp nhận) và cho việc `assigned()` dùng Student.id vs auth user id.
2. Không có test đồng thời (race) cho start attempt (2 `in_progress`), giới hạn lượt, publish song song.
3. Không có test luồng **chấm tay / chuyển `needs_review` → `graded`** (không có code).
4. `QuizPublishService` gần như chưa có test nghiệp vụ (idempotent replay, 422, stale draft, ký, deactivate version cũ).
5. Không test cho `LmsAcademicRecordUseCase` ngoài 3 trường hợp validate; không test `upsertGradebookScore` stale/replay.
6. Không test phân quyền URL cho `/api/v1/curriculum|content` trong `SecurityConfig`, không test `LearningStandardsController`/`LearningOutcomesController` HTTP.
7. Không test hiển thị tài nguyên `draft/private` trong `learningResourceLibrary` khi không truyền `status`.
8. Không test `StudentEngagementService` tạo/reply/reaction thành công ngoài 3 ca lỗi.
9. Không test migration thực với dữ liệu (ngoài "contract" text) cho V36–V46 backfill.

---

## 9. Rủi ro, nợ kỹ thuật, nghi ngờ bug

(Đã đối chiếu code; mục "nghi ngờ" = suy ra từ đọc code, chưa chạy/chưa xác minh với dữ liệu thật.)

**Nghiêm trọng / có khả năng gây lỗi nghiệp vụ**
1. **Nghi ngờ – assignment nhiều quiz không làm được quiz thứ 2+**: `start` yêu cầu `assignment.getQuizId() == request.quizId` (`B/lms/internal/attempt/LmsAttemptUseCase.java:96`) trong khi `create` lưu nhiều quiz vào `assignment_resources` và chỉ quiz đầu vào cột `quiz_id` (`LmsAssignmentCommandService.java:168, 183`). Không thấy chỗ nào đọc `assignment_resources` ở đường start.
2. **Nghi ngờ – `assigned()` so sánh sai loại ID**: `start` gọi `assigned(assignment, studentId)` với `studentId` = **auth userId** (`:97`) nhưng `assignment.studentIds` lưu **Student.id** (`LmsAssignmentCommandService.java:169`); `persistedStudent` chỉ được tính ở `:98` sau đó. Chỉ hoạt động nếu Student.id == auth user id. Tương tự `listScores` truyền `studentId` (auth) vào `findStudentAssignments` (`LmsStudentReadModelUseCase.java:47`) khác `listAssignments` (đổi sang `student.getId()`, `:36-38`). Ngoài ra không thấy nơi nào gán `Student.authUserId` trong `src/main` (grep `setAuthUserId` rỗng) – chưa rõ luồng liên kết tài khoản–học sinh.
3. **Attempt có essay (câu manual) không thể hoàn tất**: submit đặt `needs_review` (`:203`) nhưng không có endpoint/use case chấm tay hay chuyển sang `graded` (grep `"graded"`/`manualGrade` chỉ thấy hằng số/filter). `passed` luôn `false` khi có câu manual (`QuizAttemptGradingService.java:139`); các thống kê điểm lớp/dashboard chỉ tính `submitted|graded|completed` (`AttemptRepository.java:373, 396`) ⇒ bài essay không vào điểm.
4. **Chấm fallback cho quiz legacy không đối chiếu đáp án**: không có `quizVersionId` ⇒ `score = số câu đã trả lời`, `passed = ≥50%` (`LmsAttemptUseCase.java:204`). Nếu quiz legacy còn được giao thì kết quả sai bản chất.
5. **Rò đáp án ở chế độ `client-first` (mặc định)**: `publish` mặc định `client-first` (`QuizPublishService.java:435`) và package nhúng `answerKey` (`:398-400`); `GET /lms/quizzes/{id}/package` trả `answerKey` cho mọi người có `lms.quiz.read` (`QuizRuntimePackageService.java:96-103`) và **không kiểm học sinh có được giao bài hay không** (`LmsResourceQueryUseCase.java:23-36`). Có chủ đích cho chấm offline nhưng là rủi ro gian lận với bài `test`.
6. **Nghi ngờ – chặn start chỉ theo ngày giờ, không theo trạng thái**: `ensureOpen` bỏ qua `assignment.status` (draft/archived) (`:328`); không kiểm `Quiz.status/deletedAt`/lifecycle; gói bị `revoke` vẫn được `activePackageEntity` trả cho attempt (`revokedAt` chỉ được kiểm ở `PortableQuizBundleService.export`, `:185`). Student list assignments cũng không lọc `status` (`AssignmentRepository.findStudentAssignments`), `LmsStatePolicy.assignmentIsVisible` không được dùng ở đâu.
7. **Nghi ngờ – race tạo 2 attempt `in_progress` / vượt `attemptLimit`**: kiểm tra `findInProgress` rồi `countCompletedForLimit` rồi `save` không khoá, DB không có unique tương ứng (V30 chỉ unique theo idempotency key; V87 theo submit key). `submit` cũng chỉ dựa `Attempt.record_version` nếu có `@Version` (chưa xác nhận).
8. **Trạng thái lifecycle làm hỏng bộ lọc curriculum**: `statusFor` ghi `status = in_review/approved/draft…` (`ContentLifecycleOperations.java:422-426`) trong khi curriculum chỉ chấp nhận `active|archived|draft` (`CurriculumCommandUseCase.java:31, 308-319`) và mọi truy vấn cây/chain/placement dùng `active` ⇒ node ở `IN_REVIEW/APPROVED` vừa biến mất khỏi cây mặc định vừa không còn "active" cho tạo con/placement. Hai trục `status` và `lifecycle_state` chưa được hợp nhất. Ngoài ra `LearningContentService.updateContentItem`/`createGoogleSlidesLecture` đặt thẳng `status=published/public` bỏ qua lifecycle (`:201-202, 277-279`).
9. **Nghi ngờ – thư viện tài nguyên lộ nội dung `draft/private`**: không truyền `status`/`visibility` ⇒ không lọc (`ContentItemRepository.java:44-49`), `educationUnitId` chỉ echo, không dùng lọc (`LearningResourceLibraryReadService.java:120-127`); quyền gọi chỉ `lms.resource.read|content.resource.read|content.manage` (`LmsResourceRead`).
10. **Outcomes – attainment không nâng cấp được**: unique/exists theo (learner, competency, path) ⇒ bản `IN_PROGRESS` không thể thành `ATTAINED` (409) (`LearningOutcomesService.java:366-369`); không có lệnh cập nhật. `evaluateCompletion` tin hoàn toàn `completedContentRefIds` do caller gửi (không đối chiếu attempt/tiến độ thật) (`:422-451`).
11. **Outcomes – lệch quyền annotation/service**: `@LearningOutcomesIssueCertificate` cho phép `learning.outcomes.issue_certificate` (`B/learningoutcomes/authorization/LearningOutcomesIssueCertificate.java`) nhưng `issueCertificate` gọi `ensureLearnerAccess` (chỉ chính learner hoặc `manage`, `:654-658`) và `revokeCertificate` yêu cầu `manage` (`:558`) ⇒ người chỉ có `issue_certificate` không cấp/thu hồi được cho người khác.

**Trung bình**
12. **Cache wipe toàn cục**: mỗi `submit`/`start` `clear()` 6 cache (cả `studentDashboard`) cho mọi tenant (`LmsAttemptUseCase.java:390`); mỗi autosave draft xoá toàn bộ `quizRuntimePackage` (`QuizDraftService.java:72, 99, 133`) ⇒ trong giờ thi package bị tải lại hàng loạt (cache stampede) và editor đang soạn làm chậm học sinh đang thi.
13. **Cache lệch sau ghi danh**: `enroll`/`bulkEnroll` chỉ evict `lmsClassesBySchool,lcmsSchoolOperations,lcmsSchoolDirectory` (`LmsOperationalCommandService.java:275`), không evict `studentDashboard`, `lmsStudentAssignments` ⇒ dashboard/assignment có thể cũ.
14. **Quiz không có kiểm sở hữu/phạm vi**: update/publish/delete/fork chỉ kiểm tenant (`LcmsQuizAuthoringController`, `QuizPublishService.java:154`); `QuestionBankAuthoringService.update/archive` không kiểm `scope`/owner (`:57-79`) ⇒ GV bất kỳ có quyền chung sửa/xoá quiz–câu hỏi của người khác trong tenant (kể cả phạm vi school).
15. **Assignment write không kiểm phạm vi lớp/quiz**: `LmsAssignmentCommandService` chỉ lọc theo tenant (`requireAssignment :487-494`); giao quiz của tenant khác không bị chặn trực tiếp (chỉ tìm version theo tenant), nhưng giáo viên có thể giao cho **bất kỳ lớp** trong tenant, sửa/xoá assignment của người khác; `updateAssignment` không kiểm `status` thuộc tập hợp lệ, `dueAt/startAt`, số âm.
16. **Xoá assignment** hard-delete nhưng FK RESTRICT với attempt ⇒ lỗi DB (500 hoặc 409 tuỳ handler) thay vì lỗi nghiệp vụ rõ ràng (`:253-259`, V13:104-108).
17. **`CurriculumProgramService`**: `uq_curriculum_program_row_period` (V106) nhưng service chấp nhận `period` trùng từ client ⇒ vi phạm unique (500) (`:66-70, 91`); xoá–chèn lại toàn bộ dòng mỗi lần lưu. Tenant có thể rơi về `"erg"` khi context thiếu tenant (các use case curriculum) – hành vi mặc định nguy hiểm nếu một ngày `access` null.
18. **`importStudents`**: `idempotencyKey` không dùng để replay; mỗi item gọi `createStudent` (mỗi lần `assertSchool` + `school()`), cả batch một transaction, không giới hạn kích thước ở service (xem validation request – chưa đọc kỹ) (`:194-217`).
19. **`enroll` không đóng enrollment cũ** và ghi đè `academicClassId` (`:290-304`) ⇒ học sinh có thể có nhiều dòng `ENROLLED` (nhiều lớp) trong khi nhiều chỗ chỉ dùng `academicClassId` đơn (Start attempt fallback, StudentEngagement `requireStudent`).
20. **`StudentEngagementService`**: `assignmentId` của thread không kiểm tra thuộc lớp; `json()`/`parseJsonList()` tự dựng/đọc JSON bằng chuỗi (`:367-378`) – ID chứa dấu phẩy sẽ hỏng; reply/reaction không giới hạn tốc độ.
21. **LTI/xAPI**: nonce chỉ được bảo vệ gián tiếp qua receipt (key mặc định = sha256 token) – token mới cùng nonce vẫn qua; `launchLti`/`ingestXapi` không dùng advisory lock như outcomes (`begin` không khoá) ⇒ đua cùng key có thể phát sinh lỗi unique thay vì replay (`LearningStandardsService.java:362-368`). `launchLti` không tạo session, chỉ phát event.
22. **Media local không có endpoint đọc**: `publicUrl` trỏ `/api/v1/lcms/media/blob/...` nhưng không có controller phục vụ `media/blob` (grep) ⇒ URL ảnh local có thể không truy cập được (R2 dùng public domain).
23. **Ký/HMAC**: `signing-key` mặc định rỗng → `SecretKeySpec` rỗng sẽ ném lỗi khi publish nếu thuật toán HMAC mà chưa cấu hình (`QuizPackageSigner.java:86-92`); cần cấu hình đúng ở môi trường. `portableReady` chỉ cho Ed25519 (`PortableQuizBundleService.java:345`), nên cấu hình mặc định (HMAC) ⇒ bundle không bao giờ "portable ready". Bundle xuất bản đã **lọc khoá nhạy cảm** (`isSensitiveKey`) – nếu payload có khoá trùng tên thì chữ ký (tính trên payload gốc) sẽ không khớp bản xuất (suy luận, chưa kiểm chứng).

**Nợ kỹ thuật / dọn dẹp**
24. Hai bộ danh mục song song: `lms_subjects/levels/topics` (entity `model/lms/Subject|Level|Topic`, `LmsOperationalCommandService.createSubject`) và `curriculum_*` (canonical). `AssessmentCurriculumBridge` javadoc nói "đọc bảng lms_*" nhưng code đọc `curriculum_*` (`LmsAssessmentCurriculumBridge.java:93-111`; test tên "UsesLegacyLmsRefs" nhưng assert `canonical-curriculum`).
25. `JsonbSchemaMigrator` chỉ được dùng trong test (grep) – "tolerant reader" chưa nối vào đường đọc thật; `docVersion` tuỳ chọn khiến soạn song song có thể ghi đè nhau (`QuizDraftService.java:245-262`).
26. Nhiều lớp compatibility/@Deprecated và constructor đôi (`LmsRuntimeService`, `LearningContentService`, `StudentEngagementService`, `StudentDashboardReadService`, `CurriculumOperations(CurriculumService)`), nhiều khối code nén một dòng (`LmsAttemptUseCase`, read-model use cases) khó review.
27. `QuizValidationService` javadoc nói kiểm "media must be committed" ở publish service, nhưng `QuizPublishService` không có phụ thuộc/kiểm tra media nào (constructor `:80-112`) – ràng buộc này hiện không được thực thi (suy ra từ code).
28. Mô hình `courses/*`, `elearning/*` (Category/Level/Unit) chỉ còn entity; chưa thấy sử dụng (xem §10).
29. Log `EXAM_ATTEMPT_*` ghi `studentId`, điểm số (PII nhẹ) ở mức INFO.
30. Thiếu phiên bản V104–V105 trong chuỗi Flyway (có thể do xoá/đổi số).

---

## 10. Câu hỏi còn mở

1. Student↔account: Student.id có luôn bằng auth user id không, hay có bước provisioning ngoài slice gán `authUserId`? (ảnh hưởng `assigned()` và `listScores`).
2. Có chủ đích chỉ cho phép làm quiz đầu tiên của assignment nhiều quiz không? Frontend hiện gọi `/attempts` cho quiz nào? (cần đối chiếu `erg-system`).
3. Ai chấm tay câu essay và bằng API nào? Có kế hoạch endpoint chuyển `needs_review → graded`?
4. `client-first` làm mặc định: có chấp nhận lộ đáp án cho bài `test` chính thức? Có chính sách ép `server-authoritative` khi `kind=test`?
5. Hai trục `status` và `lifecycle_state` (curriculum/content/quiz/question) sẽ hợp nhất thế nào? Trạng thái `in_review/approved` có thực sự ẩn node khỏi cây?
6. `courses/*`, `elearning/*` (Course, CourseProgress, ElearningLevel…) còn dùng không, hay chỉ còn entity legacy (ERG-066)? Migration cho `course_progress` ở đâu?
7. Thư viện tài nguyên dành cho học sinh hay giáo viên? Có cần lọc `published/public` cứng cho vai học sinh?
8. Ai tiêu thụ event Outcomes/Standards (thông báo, báo cáo, LRS)? Khi nào `NoopXapiStatementForwarder` được thay?
9. Quyền `learning.outcomes.issue_certificate` kỳ vọng làm được gì khi service yêu cầu `manage`?
10. `PACKAGE_TOTAL_POINT_CENTS` = 1000 điểm chia đều: có plan cho trọng số từng câu (`QuizQuestion.points` đã có trường nhưng không dùng khi compile)?
11. `attemptLimit` có ý định áp dụng theo assignment-quiz hay theo assignment (hiện đếm theo cặp assignment+quiz+student)?
12. Chính sách xoá quiz/assignment đã có attempt (soft-delete, khoá, hay cấm)?
13. Vì sao `elearning.assignment.read_self` (service) và `elearning.dashboard.read` (GraphQL) cùng được yêu cầu cho dashboard – có chủ đích hay sót?
14. Có endpoint phục vụ `media/blob` ngoài slice (static/filter) cho chế độ local không?
