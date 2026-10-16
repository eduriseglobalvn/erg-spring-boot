# Quiz Backend — FE Integration Guide

> **Phiên bản:** 2026-07-08
> **Đối tượng:** Đội FE thay mock của `/quiz-editor`, `/questions`, `/quiz-bank` và LMS bằng API thật.
> **Nguồn:** Bám sát `quiz-backend-plan.md`. Đây là mô tả **những gì backend đã build thật** (endpoint + shape thực tế trong code), kèm các điểm lệch spec cần biết.

Transport: **GraphQL cho đọc** (`POST /api/v1/graphql`, envelope GraphQL chuẩn), **REST cho ghi/runtime** (envelope `ApiResponse` `{statusCode,message,data,errors,timestamp,path,request_id}`). Tenant/portal lấy từ session principal — **không tin header client**.

## FE integration ledger - cap nhat tu dot audit/sua 2026-07-09

Phan nay la ban tong hop truc tiep nhung gi da duoc noi/sua trong `erg-system` va `erg-spring` de FE tich hop tiep. Muc tieu la giu UI hien co, thay mock bang API/data layer that, dong thoi giam tai BE cho case khoang 5000 hoc sinh mo bai cung luc.

### Trang thai theo module

| Module | Trang thai FE | Backend/contract lien quan | Cache/invalidation | Con lai truoc production |
|---|---|---|---|---|
| Runtime package/player | Da co `quiz-runtime-api.ts`, `quiz-runtime-query.ts`, `local-quiz-attempt-store.ts`; `PlayerShell` dung TanStack Query, start attempt chi khi Begin/Submit, draft/answer/sync co mutation rieng | `GET /api/v1/lms/quizzes/{quizId}/package`, `POST /api/v1/lms/attempts`, submit/draft/answer/sync; attempt pin `quizVersionId` va `packageHash` | BE Caffeine `quizRuntimePackage` + HTTP `ETag/304`; FE browser cache + normalized memory cache + query key `["quiz-runtime","package",tenantId,portal,quizId]`; publish clear memory cache va invalidate runtime query | Can browser E2E voi auth/Postgres/RLS that: training/testing attempt, submit, report roundtrip |
| Shuffle cau hoi/dap an | Da chot FE shuffle deterministic theo attempt seed; testing shuffle questions/choices, training khong shuffle | BE tra canonical immutable package, khong tao package ca nhan hoa theo learner | Giu mot package cache duoc cho nhieu hoc sinh; seed FE khong lam doi `questionId`/`choiceId` nen grading map theo id goc | Can verify tren package that: training co answer key khi client-first, testing khong leak answer key khi server-authoritative |
| Training mode | Logic tach rieng: khong shuffle, co local/offline grading khi package co answer key, feedback tung buoc, save answer tung cau sau server start | Runtime answer save va final submit van ghi BE; BE authoritative khi submit | Draft/answer mutation keys on dinh cho Tauri/offline; local store giu session va replay metadata | Can E2E learner flow tren browser that |
| Testing mode | Logic tach rieng: shuffle deterministic, khong feedback tung cau, final submit, khong local-grade de thay diem BE | Submit chap nhan BE aggregate `AttemptSubmitResponse(score,maxScore,percent,passed)` hoac attempt shape hop le | Final submit invalidate report cache/root `quiz-reports` va assignment report key | Can E2E confirm khong start attempt khi page load va retry khong duplicate |
| Question bank | Da co `question-bank-api.ts`, `question-bank-query.ts`; workspace dung query data, API-enabled error/empty khong fallback mock | GraphQL `questionBankWorkspace` tra taxonomy, questions, quiz options, metadata FE can | Query key co tenant scope; mock chi la no-API-base/initial dev path, khong che BE empty | Can verify seed Postgres tra dung mock IDs cu va FE row data that |
| Quiz bank -> editor | Row quiz-bank mo `/quiz-editor?quizId=...`; create quiz tu selected questions goi BE create -> save draft -> navigate | REST create/save draft; GraphQL hydrate `quizDraft`; BE seed mirror quiz-bank demo IDs | Editor save/publish invalidate editor workspace/draft/versions/runtime package | Can browser E2E: open row, hydrate draft, save, publish, reopen package moi |
| Quiz editor authoring | Da co `quiz-editor-api.ts`, `quiz-editor-query.ts`; cover workspace/draft/versions/create/save/validate/publish/fork/media/bundle | REST validate/publish/fork/media; GraphQL `quizWorkspace`, `quizDraft`, `quizVersions` | Mutation keys on dinh; publish clear runtime memory package va invalidate runtime/editor queries | Can verify conflict `STALE_DRAFT`, invalid JSON fail ro, media upload voi storage env that |
| Slide/reorder editor commands | Da them adapter va hooks `upsertQuizEditorSlide`, `reorderQuizEditorSlides`, `useUpsertQuizEditorSlideMutation`, `useReorderQuizEditorSlidesMutation` | `POST /api/v1/lcms/quizzes/{quizId}/slides`, `POST /api/v1/lcms/quizzes/{quizId}/reorder` | Invalidate draft/workspace dung scope sau mutation | Can wire vao UI save-per-slide neu product muon; hien tai full draft save van du dung |
| Reports | Da co `quiz-reports/api/quiz-report-api.ts`, `quiz-report-query.ts`, `index.ts`; `HomeworkProgressPage` dung `assignmentReport` vao table/stat hien co va dung `quizQuestionAnalytics` de hien tong so cau + ty le dung TB trong card tong quan | GraphQL `quizQuestionAnalytics(quizVersionId)`, `assignmentReport(assignmentId)` voi permission gate report/grade/manage; `AssignmentDetail`, `HomeworkAssignmentCard`, `AssignmentSummaryItem` deu tra pinned resources/`quizVersionId` de FE goi question analytics dung version da giao | FE keys: `["quiz-reports","question-analytics",tenantId,quizVersionId]`, `["quiz-reports","assignment-report",tenantId,assignmentId]`; BE Caffeine `quizQuestionAnalytics` va `quizAssignmentReport` TTL 30s co stats; attempt/assignment writes clear report caches sau commit; runtime submit invalidate FE report root | Can submit/report/browser E2E tren Postgres/auth/RLS that de xac nhan score/progress/question analytics hien dung voi normalized rows va cache hit/evict dung |
| Assignment commands | Da co `lms-assignment-command-api.ts`, `lms-assignment-command-query.ts`; `AssignHomeworkPage` goi BE create assignment khi API base bat; `LmsTeacherShell` khong con them `createdRuns` local id gia trong API-backed path sau khi BE create thanh cong | `POST/PUT/DELETE /api/v1/lms/assignments`, recipients, class/group/student target | FE mutations invalidate homework workspace, assignment progress va report caches qua `lmsAssignmentReadQueryKeys`; BE assignment writes clear `lmsHomeworkWorkspace`, `lmsClassWorkspace`, `lmsAssignmentProgress`, `studentDashboard` sau transaction commit; danh sach homework API-backed chi tin BE workspace/refetch | Can verify assignment create -> homework list/progress/report tren BE auth that |
| Assign homework recipient picker | Class mode trong `AssignHomeworkPage` da hydrate hoc sinh tu BE `classWorkspace`; group mode da hydrate BE `studentGroups`; grade mode da hydrate hoc sinh cho tung lop grade tu BE `classWorkspace` bang TanStack `useQueries`; API-enabled missing/empty students/groups giu empty that, khong fallback mock; class/group/grade submit deu dung BE-backed recipient IDs | GraphQL `classWorkspace`, GraphQL `lms.studentGroups`, REST create assignment | Class/grade workspace keys gom ve `lmsAssignmentReadQueryKeys.classWorkspace({tenantId,usage,schoolId,classId})` va chi enabled theo mode; student group key `["lms","student-groups",tenantId,classId]` chi enabled o group mode; create mutation van invalidate homework/progress/report caches | Can verify browser E2E: class/group/grade assignment create -> homework/progress/report refetch tren BE auth that |
| Attempt/runtime writes | FE giu answer, draft va event trong browser/Tauri storage; chi goi `submit` khi learner nop bai | `startAttempt` tao session server; chi `submitAttempt` ghi answers, event, diem va progress. `saveDraft`/`saveAnswer`/`syncAttempt` la compatibility no-op co kiem tra access/package | Chi start va submit clear read caches; submit project `student_progress` truoc khi evict | Can E2E final-submit burst voi nhieu learner va retry submit cung idempotency key |
| Elearning student dashboard | `StudentDashboardWorkspace` dung TanStack Query va GraphQL `studentDashboard`; API-enabled error/empty khong con bom mock assignments/announcements | BE GraphQL `elearning.studentDashboard(input)` doc tu read model + cache `studentDashboard` TTL 30s | Query key da co `tenantId` + account id; BE attempt/assignment writes clear `studentDashboard` sau commit; no-API-base moi dung mock dashboard | Can browser E2E voi real student session de verify dashboard refetch sau assignment create/start/submit |
| LMS teacher bootstrap/scope | `LmsTeacherShell` va `DashboardShell` dung TanStack Query cho session bootstrap; API-enabled bootstrap/list education units error khong fallback mock; BE homework empty khong con hien `assignmentRuns` mock | `GET /api/v1/sessions/current`; homework GraphQL workspace | Bootstrap key scoped theo `portal/tenant/account`: `["dashboard","bootstrap",portal,tenantId,accountId]`; FE in-memory session-bootstrap cache cung scoped theo portal/account/token fingerprint; homework key tenant-scoped `lmsAssignmentReadQueryKeys.homeworkWorkspace(tenantId)`; API-backed runs chi lay tu BE workspace, no-API-base moi dung classroom/local mock | Can E2E real teacher session de verify scope school/class, permissions, homework empty/error states va doi account khong bleed bootstrap cache |
| LMS/LCMS learning resources | `learning-resource-api.ts`, teacher resource dashboard va LCMS authoring workspace/dialog da harden: API-enabled bootstrap/progress/managed schools/workspace empty-error khong fallback mock; quiz-bank attach list khong dung fallback quiz/exercise mock khi API base bat | REST/GraphQL hoclieu/studio/bootstrap, library bootstrap/progress/explorer operations, `listManageableUnits`, question-bank query | No-API-base/flag mock van giu dev/offline path; API-enabled error propagate de TanStack/UI hien error/empty that; `adminOperations` education-unit/learning-resource/user-access query keys da scoped tenant de tranh cross-tenant bleed | Can E2E real admin/teacher session verify hoclieu tree/resource empty/error, attach quiz bank, explorer write permissions |
| LMS classroom/account/admin fallbacks | `ClassManagementPage`, `ClassStudentsWorkspace`, `LmsLoginLogsPage`, `centers-api.ts` da harden: API-enabled empty/error khong fallback hoc sinh/session/center mock | GraphQL `classWorkspace`, REST `/api/v1/users/me/sessions`, `/api/v1/centers` | Query van dung TanStack; no-API-base moi dung mock de dev/offline; API-enabled missing data thanh empty/error that | Can E2E real teacher/account/admin session verify class students empty/error, login sessions empty/error, centers permissions |
| LMS read cache keys | Da gom ve `lmsAssignmentReadQueryKeys.homeworkWorkspace(tenantId)`, `.assignmentProgress(tenantId, assignmentId)`, `.classWorkspace({tenantId,usage,schoolId,classId})` va `teachingCalendarQueryKeys` de het mismatch query/invalidation va tranh cross-tenant bleed | Read model LMS/homework/admin/hoclieu/class workspace/teaching-calendar hien co | Assignment mutations invalidate root dung query dang doc; account login-session, dashboard bootstrap, adminOperations va teaching-calendar keys cung scoped theo tenant/account/portal khi can | Can tiep tuc chuan hoa neu them read model moi |
| Student groups | `StudentGroupsPage` da hydrate BE qua TanStack `useLmsStudentGroupsQuery` khi API base bat; roster hoc sinh de them/bo khoi nhom cung hydrate tu BE `classWorkspace` bang TanStack `useQueries`; create/update/delete goi BE mutations; no-API-base van giu local/offline mock. API-enabled empty la empty that, khong bom mock | BE co GraphQL `lms.studentGroups(input:{tenantId,classId})`, GraphQL `classWorkspace`, va REST `POST/PUT/DELETE /api/v1/lms/student-groups`; read/write contract tra va persist `id,name,classId,note,color,ownerUserId,studentIds,studentCount,createdAt,updatedAt`; migration `V31__student_group_ui_metadata.sql` them `note/color` | BE cache `lmsStudentGroups` TTL 30s + evict all entries khi create/update/delete group; FE key `["lms","student-groups",tenantId,classId|"all"]`, stale 30s, gc 30m; roster query key `lmsAssignmentReadQueryKeys.classWorkspace({tenantId,usage:"student-groups-roster",schoolId,classId})`, stale 60s/gc 5m; mutations invalidate student-groups root, homework workspace, report root | Can E2E auth/Postgres verify create/edit/reload/group assignment |

### Quyet dinh chot cho shuffle va tai 5000 hoc sinh

- BE khong shuffle rieng tung hoc sinh. BE chi tra mot canonical runtime package bat bien theo tenant/quiz/version de cache duoc tot.
- FE shuffle deterministic theo `attemptSeed` o testing mode. Seed hien gom `contentHash`, `quizVersion` va local attempt id, nen cung attempt thi thu tu on dinh, khac attempt thi khac thu tu.
- Training mode khong shuffle cau hoi/dap an vi day la flow hoc co feedback, co the cham local/offline va can thu tu tac gia on dinh.
- Testing mode shuffle cau hoi/dap an, khong feedback tung cau, final submit de BE cham authoritative.
- Khong doi `questionId`/`choiceId` khi shuffle. FE chi doi thu tu hien thi, submit van theo id goc de BE grade dung version pin.

### Chinh sach mock/fallback da chot

- Khi chua cau hinh API base: FE duoc dung local/mock path de dev/offline khong vo man hinh.
- Khi da cau hinh API base: GraphQL/REST loi phai propagate ra UI/query error; khong fallback mock am tham.
- Khi BE tra empty workspace: FE hien empty that; khong bom lai mock questions/quizzes.
- Khi runtime start/submit shape sai: FE fail ro; khong danh dau `serverStarted` bang local id va khong lay local grading de thay diem BE.

### Bang chung da verify gan nhat

- FE `bun run typecheck` pass.
- FE targeted learning-resource fallback suite pass 2 files / 9 tests: `learning-resource-explorer-operations-api.test.ts`, `teacher-resource-dashboard-schools.test.ts`.
- FE targeted classroom/account/admin fallback suite pass 3 files / 8 tests: `classroom-api-fallback.test.ts`, `centers-api.test.ts`, `lms-login-logs-page.test.ts`.
- FE targeted LCMS content-dialog fallback suite pass trong `learning-resource-content-dialog.test.ts`, khoa API-enabled empty quiz bank khong fallback exercise mock.
- FE targeted assign-homework fallback suite pass trong `assign-homework-student-utils.test.ts`, khoa API-enabled class/group/grade recipient picker khong fallback mock students/groups/class-student IDs.
- FE targeted suite pass 15 files / 60 tests: assignment command API/query, homework progress report wiring, create quiz from bank, question-bank API/query, quiz-editor API/query, quiz-runtime API/query/local store/fallback, quiz-report API/query, `api-client`.
- BE targeted tests truoc do pass 43 tests cho authoring/runtime/cache/seed/report contract; contract/read-model tests pass 18/18.
- Docker/Postgres E2E hien chua chay duoc do Docker Desktop daemon khong ket noi duoc `npipe:////./pipe/dockerDesktopLinuxEngine`. Vi vay chua duoc danh dau production done.

### Viec FE/BE can lam tiep

1. Chay Postgres/auth/RLS/browser E2E that cho student groups: hydrate group tu BE, tao/sua `name/note/color/studentIds`, reload thay du lieu con nguyen, giao bai theo group va report refetch dung.
2. Chay Postgres/auth/RLS/browser E2E that: seed -> question bank -> quiz bank -> editor hydrate -> create/save/publish -> runtime cache invalidation -> training/testing attempt -> submit -> assignment report/question analytics.
3. Xac minh production policy answer key: training/client-first co key cho local feedback, testing/server-authoritative khong leak key.
4. Xac minh burst/caching: package warm cache hit, `ETag/304` dung, FE normalized memory cache xu ly body rong, publish evict cache sau transaction commit.

### Cap nhat thuc thi 2026-07-09
Phan nay la delta moi nhat de FE dung tai lieu goc nay nhu ke hoach tich hop hien tai, khong phai chi doc nhu spec ban dau.

- BE report GraphQL da co that cho `quizQuestionAnalytics(quizVersionId)` va `assignmentReport(assignmentId)`, tinh tu bang normalized runtime/report, co permission gate `lms.report.read`/`lms.grade.read`/`lms.manage`.
- FE runtime/question-bank/quiz-editor da duoc noi theo data layer va TanStack Query/Mutation, UI giu nguyen. Cac mutation runtime da co `mutationKey` on dinh de phuc vu Tauri/offline instrumentation.
- Quiz-editor authoring adapter da cover ca `POST /api/v1/lcms/quizzes/{quizId}/slides` va `POST /api/v1/lcms/quizzes/{quizId}/reorder`, khong chi save draft tong. Cac mutation editor co `mutationKey` on dinh va invalidate draft/workspace dung scope.
- FE da co LCMS quiz-report API/query layer rieng tai `src/features/lcms/quiz/quiz-reports`, dung GraphQL `POST /api/v1/graphql`, key co tenant scope:
  - `["quiz-reports","question-analytics",tenantId,quizVersionId]`
  - `["quiz-reports","assignment-report",tenantId,assignmentId]`
- LMS `HomeworkProgressPage` da wire `assignmentReport` vao table/stat hien co, khong doi UI chinh. Khi API base bat, man nay uu tien BE report/progress rows va khong fallback mock neu BE tra empty/error.
- BE `assignmentProgressWorkspace.assignment.resources` da expose pinned assignment resources (`quizId`, `quizVersionId`, `quizVersionLabel`, `orderIndex`) bang projection-only read de FE biet dung quiz version da giao.
- BE `teacherHomeworkWorkspace.assignments.items.resources` va `classWorkspace.assignments.items.resources` cung da expose pinned resources bang batch projection query theo page assignment, tranh N+1 khi danh sach bai tap lon.
- LMS `HomeworkProgressPage` da lay `quizVersionId` tu selected run hoac assignment progress resources, goi `useQuizQuestionAnalyticsQuery`, va hien tom tat so cau/ty le dung TB trong card tong quan hien co.
- Report query nen dat `staleTime` ngan hon runtime package, khoang 60 giay, `gcTime` khoang 30 phut. Report la du lieu thay doi sau submit/review, khong nen stale 5 phut nhu package bat bien.
- Khi API base da cau hinh, report FE khong duoc fallback mock am tham. Neu GraphQL loi, UI phai hien error/retry/empty ro rang tuy ngu canh.
- `PlayerShell` da invalidate `quiz-reports` root va assignment report key sau khi final submit thanh cong, de man report refetch thay vi giu score/progress cu trong TanStack cache.
- LMS assignment command endpoints da co FE adapter/mutation layer tai `src/features/lms/api/lms-assignment-command-api.ts` va `lms-assignment-command-query.ts`. `AssignHomeworkPage` da goi BE `POST /api/v1/lms/assignments` khi API base bat; local callback chi con la no-API-base dev/offline path.
- Homework workspace/progress cache keys da duoc gom ve `lmsAssignmentReadQueryKeys`, de assignment mutations invalidate dung `HomeworkPanel`, `HomeworkProgressPage` va report cache sau khi giao/sua/xoa bai.
- BE assignment create/update/update-recipients/delete da clear cac read caches lien quan sau commit (`lmsHomeworkWorkspace`, `lmsClassWorkspace`, `lmsAssignmentProgress`, `studentDashboard`), nen FE TanStack refetch sau mutation khong bi BE tra lai du lieu stale trong TTL 30s.
- Learner answer, draft va event chi nam trong browser/Tauri storage truoc submit. Runtime `saveDraft`, `saveAnswer` va `syncAttempt` giu lai de tuong thich API, nhung khong ghi attempt/event/sync receipt va khong clear cache.
- `submitAttempt` la write path duy nhat cho bai lam: persist answers + client events, server grade, refresh `student_progress` va clear cache sau commit.
- FE Elearning student dashboard da harden fallback: khi API base bat, GraphQL loi se propagate thanh query error, BE empty assignments/announcements giu empty that, khong tu do mock vao UI. Query key student dashboard da them tenant scope de tranh dung nham cache khi doi tenant/session.
- FE LMS teacher bootstrap/list education units da harden fallback: khi API base bat, `/api/v1/sessions/current` loi hoac empty se propagate query error, khong fallback mock school/class/scope. `LmsTeacherShell` cung khong con dung `assignmentRuns` mock khi API base bat ma BE homework workspace tra empty, va khong con bom `createdRuns` local id gia sau API-backed assignment create.
- FE learning resources/LCMS authoring da harden fallback: khi API base bat, library bootstrap/progress REST loi se propagate thay vi tra mock/empty; managed-school units empty la empty that, loi list units khong fallback classroom mock; LCMS authoring workspace empty/error khong bom `mockExplorerModel`/`mockExplorerResources`; quiz-bank attach list khong fallback `fallbackQuizBankItems` khi question-bank query dang API-enabled empty/error.
- FE LCMS attach exercise dialog da harden fallback: `TaxonomyCreateDialog` dung `resolveExerciseLibraryItems`, API-enabled quiz bank empty/error khong con tu bom `mockExerciseLibrary`; no-API-base van co mock exercise library de dev/offline.
- FE classroom/account/admin fallbacks da harden: `ClassManagementPage` va `ClassStudentsWorkspace` khong con dung fallback students khi API base bat ma class workspace chua co data; `LmsLoginLogsPage` khong hien mock sessions khi API-enabled empty/error va login-session cache key da co tenant/account scope; `getCenters()` chi tra empty trong no-API-base dev/offline, API-enabled error se propagate.
- FE `AssignHomeworkPage` da harden class/group/grade recipient picker: khi API base bat, page fetch `classWorkspace` qua TanStack Query va resolve hoc sinh tu BE; group mode fetch `lms.studentGroups` theo class hien tai va map ve UI group option; grade mode fetch `classWorkspace` cho tung lop grade bang TanStack `useQueries` va resolve recipient IDs tu BE-backed class-student maps. BE empty/missing thi hien empty that, khong tu lay `classroomStudents`/`assignmentGroups` mock; API-enabled grade submit da duoc mo lai vi khong con ghi mock class/student IDs. De giam tai BE, class workspace query chinh chi enabled o class/group mode, student-groups query chi enabled o group mode, grade class queries chi enabled o grade mode.
- FE `StudentGroupsPage` da harden roster hoc sinh: khi API base bat, danh sach hoc sinh de them/bo khoi nhom lay tu BE `classWorkspace` bang TanStack `useQueries` theo class filter; no-API-base moi dung `classroomStudents` mock. Khi BE dang empty/loading/error, page khong tu do mock student IDs vao group, tranh persist ID gia len BE.
- Chua coi production done neu chua co Postgres/auth/RLS/browser E2E: seed -> question bank -> editor -> publish -> package cache invalidation -> training/testing attempt -> submit -> report roundtrip.

### Cập nhật thực thi 2026-07-08
Tài liệu này đã được cập nhật theo trạng thái triển khai thật ở cả `erg-spring` và `erg-system`, không còn chỉ là kế hoạch API ban đầu. Các quyết định cần FE bám theo:

- BE chịu trách nhiệm trả **canonical immutable runtime package** theo quiz/version, có Caffeine cache + HTTP `ETag`; BE không shuffle package riêng cho từng học sinh.
- FE chịu trách nhiệm **shuffle deterministic theo attempt seed** cho testing mode; training mode không shuffle để giữ flow học/từng bước ổn định.
- `training` và `testing` là hai logic runtime khác nhau, không dùng một flow chung rồi bật/tắt vài flag UI.
- FE không gọi start attempt khi page load; chỉ start server attempt khi learner bấm bắt đầu hoặc ngay trước submit.
- Question-bank, quiz-editor và runtime đều đã có TanStack Query/data-layer trong FE; UI hiện tại giữ nguyên, thay mock bằng BE theo từng query/mutation.
- BE seed đã mirror ID mock cũ của FE để FE bỏ mock mà không đổi routing, selection, editor hydrate hoặc demo runtime.
- Phần còn thiếu để chốt production là Postgres/auth E2E thật: seed trên Postgres, RLS/session, package answer-key policy, start/submit/report qua browser.

### Cách FE dùng tài liệu này sau các lần sửa 2026-07-08
Đây là tài liệu gốc đã được cập nhật theo toàn bộ phần đã audit/sửa ở cả BE và FE; FE có thể dùng trực tiếp làm plan tích hợp hiện tại thay vì đọc rải rác từng commit hoặc report phụ.

Trạng thái đã chốt cho FE:
- Runtime, question-bank và quiz-editor đều đã có data layer theo TanStack Query; UI hiện tại giữ nguyên.
- Mock/local data chỉ còn là dev/offline fallback khi chưa cấu hình API base. Khi API base đã bật, lỗi GraphQL/REST phải nổi lên rõ ràng, không tự dùng mock như dữ liệu thật.
- Runtime package dùng cache nhiều lớp: BE Caffeine `quizRuntimePackage`, HTTP `ETag`/`304`, browser cache, FE normalized package memory cache và TanStack Query.
- Shuffle nằm ở FE, deterministic theo attempt seed. BE chỉ trả canonical package để chịu tải 5000 học sinh mở cùng bài.
- Training và testing là hai flow runtime khác nhau: training không shuffle và có thể feedback/local grading; testing shuffle, không feedback từng câu, submit cuối do BE authoritative.
- Start attempt không chạy khi page load; chỉ start khi learner bấm bắt đầu hoặc ngay trước submit, với `X-Idempotency-Key` ổn định.
- Submit FE đã chấp nhận response tổng hợp từ BE `AttemptSubmitResponse(score,maxScore,percent,passed)` và không ghi đè điểm BE bằng local grading.
- Runtime draft/answer/event được giữ client-side. FE không gọi `PATCH /attempts/{id}/draft`, `PUT /attempts/{id}/answers/{questionId}` hay `POST /attempts/{id}/sync` trong flow learner; gửi toàn bộ answers và client events đúng một lần qua submit, với retry cùng `X-Idempotency-Key` khi mạng lỗi.
- Editor draft hydrate không còn âm thầm fallback mock khi API base đã bật mà BE trả JSON lỗi; draft JSON lỗi sẽ fail rõ để tránh mở UI bằng dữ liệu default giả.
- Việc chưa được coi là xong production là Postgres/auth/RLS/browser E2E thật, vì Docker Desktop hiện chưa chạy hoặc chưa có external Postgres env.

---

## 0. Xác thực & quy ước chung

- Mọi route dưới `/api/v1/**` yêu cầu đã đăng nhập. Field GraphQL LCMS còn kiểm tra permission ở tầng resolver.
- **Idempotency:** các lệnh tạo/publish/start attempt nhận header `X-Idempotency-Key`. Gửi lại cùng key → trả cùng kết quả, không tạo trùng. Với runtime start, key được lưu ở `lms_attempts.start_idempotency_key` và có unique index theo `tenant_id, assignment_id, quiz_id, student_id, start_idempotency_key`.
- **Draft save-on-exit:** FE giữ toàn bộ state client-side và chỉ gọi lưu khi thoát editor (hoặc checkpoint thỉnh thoảng). `docVersion` là **tùy chọn**: bỏ trống → last-write-wins (luôn ghi đè state server). Gửi `docVersion` (khi mở 2 editor cùng quiz, muốn chống đè) → sai version trả `409` code `STALE_DRAFT` (message kèm `serverDocVersion`).
- Lỗi validation publish → `422` code `VALIDATION_FAILED`, `errors` là danh sách `{field,tag,actual_tag,message}` (field = path câu hỏi).

---

## 1. Authoring — soạn quiz (`/quiz-editor`)

### 1.1 Tạo quiz
`POST /api/v1/lcms/quizzes` + `X-Idempotency-Key`
```jsonc
// body
{ "title": "…", "subtitle": "…", "kind": "train|test",
  "subjectId": "…", "levelId": "…", "categoryId": "…" }
// data
{ "quizId": "…", "draftId": "…", "docVersion": 0 }
```
Tạo quiz + draft rỗng trong 1 transaction. `kind` mặc định `train`.

### 1.2 Lưu draft (save-on-exit)
FE giữ state trong editor; chỉ gọi lưu khi thoát (hoặc checkpoint). Không cần autosave mỗi thao tác.
- Toàn bộ tài liệu: `PUT /api/v1/lcms/quizzes/{quizId}/draft`
- Vá 1 phần: `PATCH …/draft` (cùng shape)
```jsonc
// body — docVersion TÙY CHỌN (bỏ trống = last-write-wins)
{ "settings": {…}, "playerTemplate": {…}, "tree": [ {groupId, slides:[…]} ] }
// data
{ "docVersion": 4 }   // server luôn tăng, trả về để FE tham chiếu
```
- Upsert 1 slide: `POST …/quizzes/{quizId}/slides` body `{groupId, slide, afterSlideId?, docVersion?}` — server gán `slideId` nếu thiếu.
- Sắp xếp: `POST …/quizzes/{quizId}/reorder` body `{groups:[{groupId, slideIds:[…]}], docVersion?}`.

Tất cả trả `{docVersion}` mới. Nếu FE **có** gửi `docVersion` mà sai → `409 STALE_DRAFT`; bỏ trống thì luôn ghi đè.

### 1.3 Media (thay base64 data URL)
Luồng hash-first, 2 bước:
1. `POST /api/v1/lcms/media/presign` body `{sha256, mimeType, byteSize, originalName}`
   → `{deduped, mediaId, url, uploadUrl, storageKey}`. Nếu `deduped=true` thì bytes đã tồn tại — dùng luôn `mediaId`/`url`, **bỏ qua bước 2**.
2. PUT bytes lên `uploadUrl`, rồi `POST /api/v1/lcms/media/{mediaId}/commit` body `{width?, height?}` → `{mediaId, url, width, height}`.
- Chỉ nhận ảnh (`png/jpeg/webp/gif/svg+xml`), tối đa 15MB. Slide tham chiếu media bằng `url`, không nhúng base64 nữa.

### 1.4 Đọc workspace + hydrate editor (GraphQL)
```graphql
query { quizWorkspace(input:{search,subjectId,levelId,categoryId,status,kind,page,size}) {
  tenantId
  quizzes { items { id title subtitle kind status subjectId levelId categoryId currentVersionLabel questionCount updatedAt } page size totalItems totalPages hasNext hasPrevious }
  subjects { id label } levels { id label } categories { id label }
} }

query { quizDraft(quizId:"…") { quizId draftId docVersion baseVersionLabel updatedAt settings playerTemplate tree } }
```
- Page shape trùng `GraphQlPage<T>` hiện có (map thẳng), `size` bị clamp ≤ 50.
- **Lưu ý:** `settings`/`playerTemplate`/`tree` trả về là **chuỗi JSON** (String), không phải object — FE `JSON.parse()`. (Backend chưa đăng ký custom scalar `JSON`; dùng String để không phải wire scalar.)

---

## 2. Question bank (`/questions`, `/quiz-bank`)
Workspace `questionBankWorkspace` cũ giữ nguyên (chỉ thay mock bằng bảng thật). Bảng `lms_questions` đã có thêm cột bank (`category_id`, `objective`, `difficulty`, `tags`, `content`, `mastery_rate`, `usage_count`, `schools_using`). GraphQL workspace hiện đọc taxonomy từ `lms_subjects`, `lms_levels`, `lms_topics` và quiz options từ `lms_quizzes` để FE có thể bỏ mock mà không đổi UI.
- **Lệch spec:** `tags` lưu **jsonb `List<String>`** (không phải `text[]`) để đồng nhất cách repo map list + GIN `jsonb_path_ops`.
- Question rows trong `questionBankWorkspace` hiện trả đủ metadata FE cần để tạo quiz từ bank mà không đoán bằng mock: `objective`, `difficulty`, `tags`, `choices`, `answer`, `masteryRate`, `schoolsUsing`, `usageCount`.
- Quiz options trong `questionBankWorkspace` hiện trả đủ metadata để FE không phải đoán từ mock: `kind`, `subjectId`, `levelId`, `categoryId`, `questionIds`.

### 2.1 Settings ownership: quiz riêng, question riêng, question-set override

BE hiện tách 3 lớp settings, FE cần gửi đúng field để không lẫn nghiệp vụ:

- `Quiz.settings` / `QuizDraft.settings`: setting cấp bài quiz, ví dụ mode runtime, result, theme, policy chung. Đây không phải setting riêng của từng câu.
- `Question.settings`: setting gốc/default của câu hỏi trong ngân hàng câu hỏi. REST create/update question nhận field `settings` cạnh `content`/`metadata`.
- `lms_quiz_questions.settings`: setting của câu hỏi khi được chọn vào một bộ/quiz. Đây là lớp **question-set override** và sẽ ghi đè `Question.settings` khi publish/runtime.

Khi editor lưu draft, nếu slide có `questionId` và có một trong các field `questionSetSettings`, `settingsOverride`, `overrideSettings`, BE sẽ sync vào `lms_quiz_questions.settings`. Khi publish, BE đọc lại `Question.settings` + `lms_quiz_questions.settings` từ DB rồi compile runtime body theo thứ tự ưu tiên:

```text
Question.settings < slide.questionSettings < slide.settings < lms_quiz_questions.settings < slide.questionSetSettings/settingsOverride/overrideSettings
```

Kết quả cuối cùng nằm trong `runtimeQuestion.body.settings`. FE nên ưu tiên dùng `questionSetSettings` cho cấu hình override của câu đã chọn trong quiz, tránh ghi đè vào `Question.settings` nếu chỉ muốn thay đổi trong một bài cụ thể.

---

## 3. Publish / version / fork
- Validate (không mutate): `POST /api/v1/lcms/quizzes/{quizId}/validate` → `{valid, errors:[{path,code,message}]}`.
- Publish: `POST /api/v1/lcms/quizzes/{quizId}/publish` + `X-Idempotency-Key` (**bắt buộc**), body `{docVersion?, gradingMode?, versionLabel?}`.
  - `gradingMode`: `client-first` (mặc định, gói CÓ answer key để chấm offline) hoặc `server-authoritative` (gói KHÔNG có answer key).
  - Thành công → `{quizVersionId, versionLabel, versionNumber, packageId, contentHash}`.
  - `docVersion` optional (giống save draft): gửi để bảo vệ conflict → sai thì `409 STALE_DRAFT`; bỏ qua = publish state hiện tại. Không qua gate → `422 VALIDATION_FAILED`.
- Fork version thành draft mới: `POST /api/v1/lcms/quizzes/{quizId}/versions/{versionId}/fork` → `{draftId, docVersion:0, baseVersionLabel}`.
- Lịch sử version (GraphQL): `quizVersions(quizId){ quizId versions { id versionLabel versionNumber gradingMode contentHash publishedBy publishedAt isActive } }`.

Compile tách answer key khỏi body người chơi thấy (14 loại authoring → runtime kind). Publish đóng băng `quiz_versions` + normalized questions + `quiz_packages` đã ký (SHA-256 content hash + chữ ký).
- **Lệch spec:** chữ ký dùng **HMAC-SHA256** (không phải Ed25519) — repo chưa có wiring JCE Ed25519. Contract verify (`contentHash`+`signature`+`publicKeyId`) giống hệt; đổi thuật toán sau chỉ sửa 1 class.

---

## 4. Runtime — làm bài
Endpoint runtime `/api/v1/lms/**` giữ nguyên path + shape (`LmsRuntimeDtos`). Thay đổi bên trong:
- `GET …/lms/quizzes/{quizId}/package` giờ ưu tiên gói đã publish từ `quiz_packages`; quiz chưa publish fallback blob cũ. Gói `server-authoritative` **không** kèm answer key.
- Attempt được ghim `quizVersionId` khi start. Submit chấm server-side theo version ghim (nếu có), ghi normalized `attempt_answers` + cập nhật `student_progress`; câu essay → `needs_review`. Attempt cũ (chưa có version) vẫn dùng scoring đếm cũ.

### 4.1 Quyết định tải lớn: BE trả canonical package, FE shuffle theo attempt
Mục tiêu vận hành mới là chịu được khoảng **5000 học sinh mở bài cùng lúc**. Vì vậy không tạo package cá nhân hóa ở BE cho từng học sinh.

- BE chỉ trả **một immutable/canonical runtime package** theo `tenantId + quizId + active version`.
- Package được cache ở BE bằng cache `quizRuntimePackage` TTL 10 phút, size 10.000, có `recordStats()` để quan sát hit/miss và `@Cacheable(sync = true)` để tránh cold-cache stampede.
- Publish quiz evict `quizRuntimePackage` **sau transaction commit**, tránh cửa sổ cache lại version cũ.
- FE gọi package bằng `cache: "default"` để browser dùng HTTP cache/revalidation.
- Endpoint package trả `ETag = contentHash` và `Cache-Control: private, max-age=300, must-revalidate`; `If-None-Match` khớp → `304 Not Modified`.
- `api-client.ts` coi HTTP `304 Not Modified` là revalidation thành công không có body, trả `undefined` thay vì throw `HTTP_304`.
- FE runtime giữ memory cache package đã normalize theo `portal:quizId`; nếu HTTP revalidation trả `304`/body rỗng thì dùng package đã cache thay vì crash normalize.
- FE runtime dùng thêm TanStack Query layer: `src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-query.ts`. Query key là `["quiz-runtime","package",tenantId,portal,quizId]`, `staleTime=5 phút`, `gcTime=30 phút`. `PlayerShell` hydrate local session từ query cache và retry bằng `refetch()`, không tạo vòng refetch khi `data` thay đổi.

### 4.2 Shuffle câu hỏi / đáp án
Quyết định cuối cùng: **FE shuffle**, BE không shuffle theo từng learner.

- FE nhận canonical package rồi gọi `prepareQuizPackageForAttempt(quizPackage, attemptSeed)`.
- `attemptSeed` lấy từ local session, hiện là `${contentHash}:${quizVersion}:${localAttemptId}`.
- Shuffle deterministic: cùng seed → cùng thứ tự; khác student/attempt → thứ tự khác; `contentHash` không đổi.
- Testing mode ép `shuffleQuestions=true` và `shuffleChoices=true` ở FE runtime.
- Training mode ép `shuffleQuestions=false` và `shuffleChoices=false` để học viên học theo thứ tự tác giả, feedback từng bước ổn định.
- Các nhóm được shuffle ở FE: thứ tự câu hỏi trong section, choices, drag-drop items, inline blank options, word bank. Không đổi `questionId`/`choiceId`, nên submit/grading vẫn map theo id gốc.

### 4.3 Start attempt không chạy khi page load
Để giảm write-load khi nhiều học sinh chỉ mở trang/chưa bấm bắt đầu:

- FE chỉ `GET package` khi load player.
- FE tạo local session trước bằng `localQuizAttemptStore.createSession`.
- FE chỉ gọi `POST /api/v1/lms/attempts` khi learner thật sự bấm bắt đầu, hoặc ngay trước submit nếu chưa start server.
- Request start gửi `X-Idempotency-Key` ổn định từ local session (`startIdempotencyKey`).
- BE replay attempt cũ nếu cùng key, không ghi DB lại; DB có unique index để chặn duplicate attempt khi retry/race.

Request start:
```http
POST /api/v1/lms/attempts
X-Idempotency-Key: start-...
Content-Type: application/json
```

```jsonc
{
  "assignmentId": "...",
  "quizId": "...",
  "packageId": "...",
  "packageHash": "..."
}
```

### 4.4 Training và testing khác logic
FE không được dùng một flow duy nhất cho cả hai mode.

Training:
- Không shuffle.
- Cho phép chấm từng câu/local step feedback.
- Có thể lưu local answer/result trước khi final submit.
- `client-first` package có thể có `answerKey` để offline/local grading; `server-authoritative` không có key.

Testing:
- Shuffle câu hỏi + đáp án theo attempt seed.
- Không reveal feedback từng câu.
- Submit cuối là điểm chốt; BE vẫn là nguồn chấm authoritative nếu attempt đã pin `quizVersionId`.
- Timer/due date/max duration do BE enforce khi start/submit; FE chỉ hiển thị và chủ động submit để UX tốt hơn.

### 4.5 Submit payload đã mở rộng
FE submit nên gửi đủ package/version/result/event để BE kiểm tra conflict và ghi audit:

```jsonc
{
  "answers": { "questionId": { "...": "normalized answer" } },
  "submittedAt": "2026-07-08T02:00:00.000Z",
  "packageHash": "content-hash",
  "quizVersion": "v3",
  "clientResult": { "score": 8, "maxScore": 10, "percent": 80 },
  "clientEvents": [
    { "id": "evt-...", "type": "answer_graded", "questionId": "q1", "createdAt": "..." }
  ]
}
```

BE hiện reject `packageHash` conflict, reject submit quá `dueAt`, reject submit quá `startedAt + maxDurationMinutes`, và ghi `attempt_submitted` + batch client events.

BE-056 bổ sung `data.policySnapshot` trong response start/resume. Đây là snapshot immutable do server tạo từ assignment và published package, gồm `teacherId`, `questionCount`, `durationMinutes`, `deadlineAt`, `attemptLimit`, `quizVersionId`, `packageId`/`packageHash`, `gradingMode` và server time bắt đầu. FE chỉ dùng snapshot này để khôi phục timer/policy; không đọc lại assignment/quiz mutable để kéo dài hoặc đổi attempt đang chạy. `submittedAt` trong request chỉ còn là trường tương thích wire, còn expiry, `submittedAt` lưu DB và event audit luôn dùng server UTC. Submit có thể gửi `X-Idempotency-Key`; retry cùng key trả lại kết quả đầu tiên và optimistic version trên attempt chặn grading đôi khi xảy ra đồng thời.

### 4.6 Client-side runtime state and final submit
Trong luc lam bai, FE luu answers, draft va event trong browser/Tauri storage. Khong gui autosave hay replay event log len backend.

Khi learner nop bai, FE gui `{ answers, clientEvents }` mot lan den `POST /api/v1/lms/attempts/{attemptId}/submit` voi `X-Idempotency-Key` on dinh. Backend luu attempt, event append-only, tu cham bang quiz version da pin va cap nhat progress trong cung transaction. Retry cung key tra lai ket qua dau tien, khong ghi/grade trung lap.

---

## 5. LMS distribution — giao bài tập
- Tạo: `POST /api/v1/lms/assignments` + `X-Idempotency-Key`
```jsonc
{ "title":"…", "quizIds":["…"],           // nhiều quiz/1 assignment; mỗi quiz ghim active version
  "recipientMode":"class|group|students",  // resolve server-side thành danh sách phẳng
  "classId":"…", "groupId":"…", "studentIds":[…],
  "startAt":"…","dueAt":"…","attemptLimit":3,"maxDurationMinutes":30,"teacherNote":"…" }
// data
{ "assignmentId":"…", "recipientCount": 25, "resources":[{quizId,quizVersionId,quizVersionLabel}] }
```
- Sửa: `PATCH …/assignments/{id}`; đổi recipient: `PUT …/assignments/{id}/recipients`; xoá: `DELETE …/assignments/{id}`.
- Nhóm học sinh: `POST/PUT/DELETE /api/v1/lms/student-groups[/{id}]`.
- Quiz phải có version đã publish mới giao được (chưa publish → `400`).
- **Lệch spec (tương thích):** assignment mới vẫn ghi cột cũ `academic_class_id` (primary class) + `quiz_id` (quiz đầu) song song bảng normalized, để read model LMS cũ chạy tiếp trong lúc chuyển tiếp.

---

## 6. Report (GraphQL)
```graphql
query { quizQuestionAnalytics(quizVersionId:"…") {
  quizVersionId
  questions { quizVersionQuestionId questionKind answeredCount correctCount partialCount correctRate averageAwardedPoints maxPoints }
} }

query { assignmentReport(assignmentId:"…") {
  assignmentId totalRecipients submittedCount inProgressCount notStartedCount needsReviewCount averagePercent
  students { studentUserId studentName status answeredCount totalQuestions percentComplete score passed submittedAt }
} }
```
Tính từ bảng normalized (`attempt_answers`, `student_progress`) — không scan JSONB.

Trang thai tich hop FE cap nhat 2026-07-09:
- BE da expose day du schema/DTO/service/controller cho hai query tren.
- FE da co feature-level adapter/query layer rieng cho LCMS quiz reports. Component report khong can goi GraphQL truc tiep; hay dung data layer theo pattern da dung cho runtime/question-bank/editor.
- File FE da them:
  - `src/features/lcms/quiz/quiz-reports/api/quiz-report-api.ts`
  - `src/features/lcms/quiz/quiz-reports/api/quiz-report-query.ts`
  - `src/features/lcms/quiz/quiz-reports/api/quiz-report-api.test.ts`
  - `src/features/lcms/quiz/quiz-reports/api/quiz-report-query.test.ts`
- Query key bat buoc co tenant/scope: `["quiz-reports","question-analytics",tenantId,quizVersionId]` va `["quiz-reports","assignment-report",tenantId,assignmentId]`.
- `staleTime` nen la 60 giay, `gcTime` 30 phut. Sau submit/review/invalidate diem, FE phai invalidate cac report key lien quan.
- Khi API base da bat, loi report phai propagate; khong dung mock/local report nhu du lieu production.

---

## 7. Portable offline bundle
- `GET /api/v1/lcms/quizzes/{quizId}/bundle` (active version) hoặc `GET /api/v1/lcms/quiz-versions/{quizVersionId}/bundle`.
- Trả 1 JSON tự chứa: `package`, `signatureBlock{contentHash,signature,publicKeyId}`, `offlineGradable`, `gradingMode`, `assetsManifest`. Player offline verify chữ ký rồi chạy.
- `server-authoritative` → `offlineGradable=false` và bundle **không** có answer key (phải sync về server chấm).

---

## 8. Bảo mật (RLS)
RLS đã bật cho toàn bộ bảng quiz-domain với chính sách cô lập theo `tenant_id`. GUC `app.tenant_id` được set tự động ở **đầu mỗi transaction** (`TenantAwareJpaTransactionManager` đọc tenant từ session principal), nên mọi query trong request bị hard-scope theo tenant. Policy **permissive khi GUC chưa set** (migration/admin/background không bị khoá). FE không cần làm gì — tenant suy từ session, không nhận từ header client.

---

## 9. Checklist FE tích hợp sau cập nhật 2026-07-08
Các mục dưới đây là trạng thái đã implement/verify trong `erg-system` và cần giữ khi nối UI thật.

### 9.1 Runtime API adapter
File chính: `src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-api.ts`.

- Đổi runtime path sang `/api/v1/lms/...`.
- `getQuizPackage(quizId, portal)` truyền portal mặc định `elearning`, có thể override `lms`.
- Package request dùng `cache: "default"`.
- Normalize được cả shape cũ `QuizPackage` và shape BE mới `QuizPackageResponse` (`quiz.quiz`, `slides`, `settings`, `result`, `theme`, `answerKey`).
- Nếu package `client-first`, FE merge `answerKey` vào model câu hỏi để local grading/offline grading.
- Nếu package `server-authoritative`, FE không kỳ vọng có answer key.
- Có memory cache package đã normalize để chịu được `304`/body rỗng khi HTTP revalidation lộ lên app.
- Có TanStack Query hook `useQuizPackageQuery(quizId, portal)` export qua `src/features/lcms/quiz/quiz-runtime/index.ts`; dùng hook này trong player/runtime thay vì gọi `getQuizPackage` trực tiếp trong component.
- Có TanStack mutation hooks `useSaveAttemptDraftMutation`, `useSaveAttemptAnswerMutation`, `useSyncAttemptMutation`; API adapter tương ứng là `saveAttemptDraft`, `saveAttemptAnswer`, `syncAttempt`.
- Runtime mutations có `mutationKey` ổn định cho MutationCache/devtools/offline instrumentation: `["quiz-runtime","mutation","attempt-draft"]`, `["quiz-runtime","mutation","attempt-answer"]`, `["quiz-runtime","mutation","attempt-sync"]`.
- `PlayerShell` dùng debounce 1.5 giây cho draft sync sau khi server attempt đã start, không ghi BE khi learner chỉ mở package/chưa Begin.

### 9.2 Local attempt session
Files chính:
- `src/features/lcms/quiz/quiz-runtime/api/local-quiz-attempt-store.ts`
- `src/components/quiz/player-shell-utils.ts`
- `src/components/quiz/player-shell.tsx`

Session local cần lưu:
- `serverStarted`
- `shuffleSeed`
- `startIdempotencyKey`
- `submitIdempotencyKey`
- `clientEvents[]` có `id`

Flow chuẩn:
1. Load canonical package.
2. Restore hoặc tạo local session.
3. Gọi `prepareQuizPackageForAttempt(quizPackage, session.shuffleSeed ?? session.attemptId)`.
4. Render bằng playable package đã shuffle/không shuffle theo mode.
5. Chỉ `ensureServerAttempt` khi learner bắt đầu hoặc trước submit.
6. Nếu submit fail, giữ attempt id mới nhất trong local store để retry không mất server attempt.

### 9.3 Mode behavior
Training mode:
- Không shuffle.
- Cho phép chấm từng câu/local step feedback.
- Feedback từng bước có thể hiện ngay theo `revealFeedbackPerStep`.
- Final submit vẫn gửi payload đầy đủ để BE ghi attempt/report.

Testing mode:
- Shuffle questions + choices deterministic theo attempt.
- Không feedback từng câu.
- Submit cuối; BE chấm theo version pin.
- FE không được tạo attempt server trước khi learner bắt đầu.

### 9.4 BE cache/runtime hardening đã có
FE có thể dựa vào các behavior này:

- `GET /api/v1/lms/quizzes/{quizId}/package` có `ETag` theo `contentHash`.
- Browser/private cache được phép giữ 5 phút và revalidate.
- BE cache package 10 phút bằng `quizRuntimePackage`.
- Cache có stampede protection (`sync=true`) và stats (`recordStats()`).
- Publish evict package cache sau commit.
- Start attempt có idempotency bằng `X-Idempotency-Key` + unique DB index.
- Start reject trước `startAt`, sau `dueAt`, hoặc khi `attemptLimit` đã đạt.
- Submit reject sau `dueAt`, quá max duration, hoặc `packageHash` conflict.

### 9.5 Test đã cập nhật
FE:
- `bun run typecheck`
- `bun run test src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-api.test.ts src/features/lcms/quiz/quiz-runtime/api/local-quiz-attempt-store.test.ts`
- `bun run test src/features/lcms/quiz/question-bank/api/question-bank-query.test.ts src/features/lcms/quiz/quiz-editor/api/quiz-editor-api.test.ts src/features/lcms/quiz/quiz-editor/api/quiz-editor-query.test.ts src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-api.test.ts src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-query.test.ts src/features/lcms/quiz/quiz-runtime/api/local-quiz-attempt-store.test.ts src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-fallback.test.ts`

BE:
- `.\mvnw "-Dtest=LcmsQuizAuthoringControllerTest,GraphQlEndpointConfigurationTest,LcmsQuizWorkspaceGraphQlControllerTest,CacheConfigTest,LmsControllerHttpCacheTest,LmsRuntimeServiceAttemptRulesTest,QuizCompileServiceTest,GradingEngineTest" test`
- `.\mvnw "-Dtest=QuestionBankDemoSeedParityTest" test`
- `.\mvnw "-Derg.reseed.devdb=true" "-Dtest=DatabaseReseedRunnerTest" test` dùng cho seed verification Postgres thật. Test này đã được ép `@ActiveProfiles("test")` và `@DynamicPropertySource` để đăng ký datasource từ `PostgreSqlTestDatabase`; nếu Docker/external Postgres không chạy thì fail-fast, không fallback sang H2.

Các test mới/đã cập nhật cover:
- REST authoring validate/publish/fork controller mappings.
- GraphQL `QuizDraftDocument` contract đúng field `settings`, `playerTemplate`, `tree`.
- HTTP `ETag`/`Cache-Control`/`304`.
- Runtime package cache config có stats.
- Start idempotency replay.
- Assignment due/max duration/attempt limit.
- FE package normalization.
- FE package memory cache khi revalidation trả body rỗng.
- Deterministic per-attempt shuffle.
- Local attempt session persistence, event IDs, server-start marker, submit failure marker, runtime fallback guard.
- Runtime draft/answer/sync adapter contract: draft save chấp nhận BE `AttemptResponse` snapshot thật (`score`, `status`, `answers`), training save-answer phải confirm `saved=true`, sync event log phải fail khi BE trả `conflicts`.
- Editor API-enabled draft hydrate fail rõ khi BE trả invalid JSON, không fallback về mock/default draft như production data.
- Question-bank TanStack query key/cache policy and mock `initialData`/`placeholderData`.
- Quiz-editor TanStack workspace/draft/version query keys có tenant scope.
- Runtime package TanStack query key có tenant + portal + quiz scope.
- `QuestionBankDemoSeedParityTest` đọc FE source trong `../erg-system` và BE seed source để guard FE mock question IDs, question type/difficulty/status metadata, quiz-bank IDs/questionIds/kind/status, và 14 runtime question type demos không bị lệch khỏi BE seed.
- Postgres seed verification test assert exact FE mock question IDs, quiz-bank IDs, demo quiz current versions, và `quiz_packages` cho `erg-training-demo`/`erg-testing-demo`. Lần chạy gần nhất chưa pass vì Docker Desktop daemon không chạy (`dockerDesktopLinuxEngine` pipe không tồn tại), không phải vì seed assertion fail.

### 9.6 Seed demo thật cho FE bỏ mock runtime/question-bank
BE đã có `QuestionBankDemoBootstrapSeedService` và `QuizDemoBootstrapSeedService`, cùng được wire vào `LmsBootstrapSeedService.seedDefaultHierarchy()`.

- Seed chạy khi `erg.lifecycle.lms-seed-on-startup=true`, cùng lifecycle bootstrap LMS hiện có.
- Question-bank seed tạo exact IDs từ `erg-system/src/features/lcms/quiz/question-bank/api/mock-question-bank.ts`:
  - Subjects: `ic3-gs6`, `mathematics`, `english`, `science`.
  - IC3 levels/topics: `ic3-l1`, `ic3-l2`, `ic3-l3` và toàn bộ topic `*-topic-*`.
  - Questions: `qb-ic3-001` đến `qb-ic3-012`, `qb-math-001`, `qb-math-002`, `qb-eng-001`, `qb-sci-001`.
  - Quiz-bank rows: `quiz-ic3-l1-foundation-train`, `quiz-ic3-l2-excel-test`, `quiz-ic3-l3-security-train`, `quiz-ic3-l3-mock-test`.
- Tạo/publish idempotent 2 quiz có ID ổn định để FE gọi thẳng package endpoint:
  - `erg-training-demo`: `kind=train`, runtime `mode=training`, `client-first`, không shuffle, reveal feedback từng bước.
  - `erg-testing-demo`: `kind=test`, runtime `mode=testing`, `server-authoritative`, FE shuffle question/answer theo attempt seed, không leak answer key.
- Fixture đi qua đúng pipeline thật: `lms_quizzes` + `quiz_drafts` authoring tree -> `QuizPublishService.publish()` -> `quiz_versions` + `quiz_packages`; không bypass compile/publish.
- Draft seed bao phủ 14 type đang có trong FE mock/runtime: single choice, multiple response, true/false, short answer, numeric, sequence, matching, fill blank, select-from-lists, drag-words, hotspot, drag-drop, likert, essay.
- FE question-bank cũng có TanStack Query layer riêng: `src/features/lcms/quiz/question-bank/api/question-bank-query.ts`, query key `["question-bank","workspace",tenantId]`, `staleTime=5 phút`, `gcTime=30 phút`. UI hiện tại không đổi, chỉ chuyển query/cache ra feature API.
- Question-bank fallback mock/initial data nằm trong TanStack Query options (`initialData`/`placeholderData`), nên component đọc `subjects/questions/quizzes` trực tiếp từ query data và chỉ giữ local UI state cho filter/search/selection/auto-pick.
- `initialDataUpdatedAt=0` để mock fallback không bị coi là dữ liệu fresh trong 5 phút; khi API base có thật, query vẫn fetch BE ngay sau mount nhưng UI không bị trống trong lúc chờ.
- `loadQuestionBankData` chỉ trả mock khi FE chưa cấu hình API base. Nếu API base đã bật mà GraphQL/auth lỗi, query phải fail qua TanStack Query thay vì âm thầm render mock như dữ liệu thật.
- Lưu ý: seed question-bank hiện mirror exact IDs và nội dung test semantic bằng ASCII để tránh churn encoding; FE mapper dùng exact ID để hydrate lại template UI/mock giàu chi tiết, nên giao diện không đổi.

### 9.7 Quiz editor authoring data layer cho FE
FE đã có API adapter và TanStack Query/mutation layer cho toàn bộ authoring surface đang có ở BE, để bước nối UI thật không phải gọi fetch rải rác trong component.

Files chính:
- `src/features/lcms/quiz/quiz-editor/api/quiz-editor-api.ts`
- `src/features/lcms/quiz/quiz-editor/api/quiz-editor-query.ts`
- `src/features/lcms/quiz/quiz-editor/index.ts`

Capabilities đã có trong FE layer:
- GraphQL `loadQuizEditorWorkspace(input)` cho list quiz/taxonomy, có tenant/page/filter.
- GraphQL `loadQuizEditorDraft(quizId)` hydrate `settings`, `playerTemplate`, `tree` từ JSON string BE trả về.
- GraphQL `loadQuizEditorVersions(quizId)` cho lịch sử publish.
- REST create quiz: `POST /api/v1/lcms/quizzes` + `X-Idempotency-Key`.
- REST save draft: `PUT /api/v1/lcms/quizzes/{quizId}/draft`.
- REST validate/publish/fork version.
- REST media presign/commit để tháo base64 khỏi editor.
- REST export offline bundle theo `quizId` hoặc `quizVersionId`.

Cache/query policy:
- Workspace key: `["quiz-editor","workspace",tenantId,normalizedFilters]`, `staleTime=60s`, `gcTime=30 phút`.
- Draft key: `["quiz-editor","draft",tenantId,quizId]`, `staleTime=15s`, `gcTime=30 phút`.
- Versions key: `["quiz-editor","versions",tenantId,quizId]`, `staleTime=60s`, `gcTime=30 phút`.
- Create/save/publish/fork mutations invalidate đúng workspace/draft/version keys; media mutations không invalidate workspace vì không đổi read model quiz.

Trạng thái wiring UI:
- Editor UI hiện vẫn giữ layout/behavior cũ.
- Route `/quiz-editor?quizId=...` đã được nối qua LCMS shell -> `DashboardContent` -> `QuizEditorWorkspace`.
- Quiz-bank row trong `/quiz-bank` đã mở `/quiz-editor?quizId={id}` bằng chính ID quiz từ BE/read model, không thêm cột/nút mới nên UI không đổi.
- Khi có `quizId`, `QuizEditorWorkspace` gọi `useQuizEditorDraftQuery(quizId)` và hydrate draft vào `useQuizEditorState` một lần theo signature `quizId:docVersion`.
- API adapter đã normalize shape draft từ BE seed/publish pipeline sang shape FE editor ổn định:
  - settings BE dạng `{info,runtime,result,theme}` được merge vào `QuizProjectSettings` đầy đủ;
  - player template thiếu field được merge default;
  - tree BE dùng `groupId` được map thành `QuizEditorGroup.id`;
  - thiếu field vẫn merge default để UI ổn định; invalid JSON từ BE khi API base đã bật sẽ fail rõ, không fallback mock.
- Save/publish dùng `docVersion` từ draft query để có conflict protection; nếu không có `quizId` thì editor giữ flow local/mock như cũ.
- Ribbon `Publish` khi đang có `quizId` đã gọi save draft trước, sau đó gọi publish BE bằng idempotency key `quiz-editor-publish:{quizId}:{docVersion}`. Publish success invalidate editor query cache, runtime package TanStack Query cache và clear FE normalized runtime package memory cache theo quizId để không mở lại package cũ.
- Flow "tạo quiz từ câu đã chọn" đã gọi `createQuizEditorQuiz`, build draft tree từ selected question-bank questions, `saveQuizEditorDraft`, rồi navigate sang `/quiz-editor?quizId={newQuizId}`. Nếu FE chưa có API base, flow fallback local import cũ để dev offline không vỡ; nếu API base đã bật mà create/save lỗi, FE báo lỗi và không mở editor local giả như đã lưu BE. Logic này đã được tách ra `src/features/lcms/create-quiz-from-bank.ts` để test rõ ràng và tránh nhét thêm business logic vào LCMS shell.
- BE contract đã được vá để khớp FE:
  - GraphQL `QuizDraftDocument` dùng field `settings`, `playerTemplate`, `tree` đúng với Java response record và FE query.
  - REST `/api/v1/lcms/quizzes/{quizId}/validate`, `/publish`, `/versions/{versionId}/fork` đã có mapping thật trong `LcmsQuizAuthoringController` và delegate vào `QuizPublishService`.

### 9.8 Tổng hợp những gì đã làm để FE tích hợp
Phần này là changelog thực tế so với kế hoạch ban đầu, để FE không phải suy luận từ từng commit/file rời rạc.

Backend đã sẵn sàng cho FE ở các điểm sau:
- Runtime package đã đi theo hướng canonical immutable package, không personalized package theo học sinh.
- BE có cache `quizRuntimePackage` bằng Caffeine, TTL 10 phút, size 10.000, `recordStats()`, `@Cacheable(sync = true)` để chống stampede.
- Package endpoint có `ETag = contentHash`, `Cache-Control: private, max-age=300, must-revalidate`, và trả `304` khi `If-None-Match` khớp.
- Publish evict runtime package cache sau transaction commit.
- Start attempt có `X-Idempotency-Key`, persist `start_idempotency_key`, unique index theo tenant/assignment/quiz/student/key, và replay kết quả cũ khi retry.
- Runtime enforce `startAt`, `dueAt`, `maxDurationMinutes`, `attemptLimit`, `packageHash` conflict, pin quiz version khi start, và submit/grading theo version đã pin.
- Demo question-bank/quiz-bank seed dùng exact ID từ FE mock để FE bỏ mock mà không đổi UI.
- Demo quiz runtime seed tạo `erg-training-demo` và `erg-testing-demo` bằng pipeline thật: draft -> validate/compile -> publish -> `quiz_versions` + `quiz_packages`.
- BE contract authoring đã được vá: GraphQL draft field đúng tên FE đang query; REST validate/publish/fork có controller mapping thật.

Frontend đã được nối theo hướng data-layer trước, UI giữ nguyên:
- Runtime package dùng `useQuizPackageQuery(quizId, portal)` thay vì fetch trực tiếp trong component.
- Runtime query policy nằm ở `src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-query.ts`, key theo `tenantId + portal + quizId`, `staleTime=5 phút`, `gcTime=30 phút`.
- `api-client.ts` tôn trọng `init.cache`, nên package request dùng browser HTTP cache/revalidation đúng ý đồ BE.
- Runtime normalize được cả package shape cũ và shape BE mới; `client-first` merge answer key để local/offline grading, `server-authoritative` không kỳ vọng answer key.
- `api-client.ts` xử lý HTTP `304` như empty success; FE có normalized package memory cache để xử lý trường hợp HTTP revalidation lộ `304`/body rỗng lên app.
- Runtime local fallback chỉ dùng khi FE chưa cấu hình API base. Nếu API base đã bật mà package/start/submit lỗi, lỗi phải propagate qua TanStack/API flow thay vì âm thầm dùng local quiz hoặc local grading.
- Runtime API contract đã chặt hơn: khi API base đã bật, start attempt phải trả `attemptId` thật; submit chấp nhận full `Attempt`/`attempt`/`result` hoặc aggregate BE `AttemptSubmitResponse(score,maxScore,percent,passed)`. FE không còn dùng local attempt id để đánh dấu server-started, và không local-grade thay thế điểm BE khi submit trả aggregate.
- Runtime draft/answer/sync API đã được nối đủ: `saveAttemptDraft`, `saveAttemptAnswer`, `syncAttempt` nằm trong `quiz-runtime-api.ts`; TanStack mutations nằm trong `quiz-runtime-query.ts`; `PlayerShell` debounce draft sync sau Begin và training submit từng câu gọi save-answer.
- Local attempt session lưu `serverStarted`, `shuffleSeed`, `startIdempotencyKey`, và start server attempt chỉ khi learner bấm bắt đầu hoặc trước submit.
- `prepareQuizPackageForAttempt` giữ shuffle deterministic ở FE. Testing shuffle câu hỏi/đáp án theo attempt seed; training không shuffle.
- Question-bank workspace dùng TanStack Query layer riêng, query key có tenant scope.
- Question-bank workspace lấy read-model trực tiếp từ query data có `initialData`, không còn sync `subjects/questions/quizzes` vào component state.
- Question-bank fallback mock hiện nằm trong query options, có `initialDataUpdatedAt=0` để không che BE data sau khi nối API, nên component chỉ giữ UI state: filter/search/selection/auto-pick.
- `loadQuestionBankData(tenantId)` nhận đúng tenant từ query options; consumer phụ không gọi trực tiếp nữa mà dùng chung `questionBankWorkspaceQueryOptions()`.
- Khi API base tồn tại, question-bank GraphQL lỗi sẽ propagate ra query error; mock không còn được dùng làm fallback production âm thầm.
- Khi API base tồn tại và BE trả workspace rỗng, mapper question-bank trả danh sách rỗng thay vì tự đổ `questionBankQuestions`/`quizBankItems` mock. Local templates chỉ được dùng để enrich những ID BE thật sự trả về, không phát minh thêm row mock.
- Nếu question-bank query lỗi, workspace hiển thị cảnh báo sync và dùng dataset rỗng cho phần tương tác, tránh tình huống `initialData` mock còn nằm trong cache rồi user tạo quiz giả như BE đang live.
- Question rows trong FE hiện dùng `objective`, `difficulty`, `tags`, `choices`, `answer`, `masteryRate`, `schoolsUsing` từ BE GraphQL; local template chỉ còn fallback cho field BE không trả hoặc data cũ.
- Quiz-bank rows trong FE hiện dùng `kind`, `subjectId`, `levelId`, `categoryId`, `questionIds` từ BE GraphQL; local template chỉ enrich các chi tiết UI chưa có trong BE như duration/source label cho seeded IDs.
- Consumer phụ trong learning-resource authoring dùng lại `questionBankWorkspaceQueryOptions()` thay vì tự khai báo query key/cache, nên question-bank data chỉ có một policy tenant-scoped trong FE.
- Quiz-editor có API adapter + TanStack Query/mutation layer cho workspace, draft, versions, create, save, validate, publish, fork, media presign/commit, bundle export.
- `/quiz-editor?quizId=...` đã hydrate draft thật từ BE vào editor state một lần theo `quizId:docVersion`.
- API-enabled editor draft hydrate fail rõ khi BE trả invalid JSON, không fallback về mock/default draft trong production path.
- `/quiz-bank` row mở editor bằng `quizId` thật, không thêm button/cột mới nên UI không đổi.
- Flow tạo quiz từ selected question-bank questions đã tạo quiz BE, build draft tree, save draft, rồi navigate sang editor quiz mới; nếu chưa có API base thì fallback local flow cũ, còn API-enabled create/save lỗi thì không fallback local. Test coverage nằm ở `src/features/lcms/create-quiz-from-bank.test.ts`.
- Ribbon Publish khi có `quizId` đã save draft trước, publish bằng idempotency key ổn định, invalidate editor/runtime TanStack Query cache, và clear FE normalized runtime package cache theo quiz.

Các file FE chính cần bám khi tiếp tục tích hợp:
- Runtime package/query/session: `src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-api.ts`, `src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-query.ts`, `src/features/lcms/quiz/quiz-runtime/api/local-quiz-attempt-store.ts`, `src/features/lcms/quiz/quiz-runtime/components/player-shell.tsx`.
- Shuffle/training/testing policy: `src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-api.ts` và các test runtime cùng thư mục. Testing shuffle deterministic theo seed; training không shuffle.
- Question-bank data ownership: `src/features/lcms/quiz/question-bank/api/question-bank-api.ts`, `src/features/lcms/quiz/question-bank/api/question-bank-query.ts`, `src/features/lcms/quiz/question-bank/components/question-bank-workspace.tsx`.
- Quiz-editor authoring: `src/features/lcms/quiz/quiz-editor/api/quiz-editor-api.ts`, `src/features/lcms/quiz/quiz-editor/api/quiz-editor-query.ts`, `src/features/lcms/quiz/quiz-editor/components/quiz-editor-workspace.tsx`.
- Quiz-editor slide/reorder commands: `upsertQuizEditorSlide`, `reorderQuizEditorSlides`, `useUpsertQuizEditorSlideMutation`, `useReorderQuizEditorSlidesMutation`; dung cho FE nao muon luu/vat ly tung slide thay vi PUT ca draft.
- Create quiz từ bank: `src/features/lcms/create-quiz-from-bank.ts`; đây là chỗ chặn API-enabled failure để không mở draft local giả.
- Quiz reports: `src/features/lcms/quiz/quiz-reports/api/quiz-report-api.ts` va `src/features/lcms/quiz/quiz-reports/api/quiz-report-query.ts`; BE da co `quizQuestionAnalytics` va `assignmentReport`, FE da co adapter/query layer. `HomeworkProgressPage` da dung `assignmentReport` de nap table/stat hien co va dung pinned `quizVersionId` tu selected run/list resources hoac assignment progress resources de goi `quizQuestionAnalytics`.
- Assignment commands: `src/features/lms/api/lms-assignment-command-api.ts` va `src/features/lms/api/lms-assignment-command-query.ts` map BE create/update/delete assignment va student group endpoints. Mutation keys on dinh cho Tauri/offline instrumentation, va mutations invalidate homework/progress/report caches.
- LMS homework/progress read keys: `lmsAssignmentReadQueryKeys.homeworkWorkspace()` va `lmsAssignmentReadQueryKeys.assignmentProgress(assignmentId)` la source chung cho query va invalidation; tranh mismatch cache key sau assignment mutations.
- BE contract/schema liên quan: `src/main/resources/graphql/schema.graphqls`, `LcmsWorkspaceResponses`, `LcmsWorkspaceReadService`, `LcmsQuizAuthoringController`, `LmsRuntimeService`, `QuizPublishService`, `QuestionBankDemoBootstrapSeedService`, `QuizDemoBootstrapSeedService`.

Những điểm đã rà soát và chốt cho FE:
- Không còn dùng mock như production fallback khi API base đã cấu hình. Mock chỉ còn là dev/offline path hoặc `initialData`/`placeholderData` có timestamp cũ.
- API-enabled empty workspace là empty thật, không tự bơm lại question/quiz mock.
- API-enabled runtime package/start/submit lỗi thì propagate lỗi, không local-grade âm thầm.
- API-enabled runtime success shape sai cũng fail rõ ràng: start không có `attemptId` thì không đánh dấu `serverStarted`; submit không có full Attempt hoặc aggregate `AttemptSubmitResponse` hợp lệ thì không dựng kết quả local.
- API-enabled runtime draft/answer/sync lỗi cũng nổi lên UI qua `submitError`, không được coi là sync thành công.
- API-enabled editor draft JSON sai thì fail rõ ràng, không hydrate bằng mock/default trong production path.
- Publish invalidates đúng editor/runtime TanStack Query cache và clear normalized package memory cache để tránh mở lại package cũ.
- Question-bank/read-model đã trả metadata đủ để FE không đoán `kind`, taxonomy, `questionIds`, answer/choices/tags/difficulty từ local template.
- BE/FE caching đã đủ lớp cho burst 5000 học sinh mở bài ở mức code/targeted test: Caffeine cache + `@Cacheable(sync=true)` + HTTP ETag/304 + `api-client` empty-304 handling + FE TanStack Query + browser cache + FE normalized memory cache.

Thứ tự FE nên tích hợp/soát lại từ tài liệu này:
1. Runtime player: dùng `useQuizPackageQuery`, local session, deterministic shuffle, start-on-begin, submit payload có package hash/events.
2. Question-bank: đọc `useQuestionBankWorkspaceQuery`, bỏ state copy của `subjects/questions/quizzes`, giữ mock chỉ là `initialData`/offline fallback.
3. Quiz-bank -> editor: row mở `/quiz-editor?quizId=...`, editor hydrate draft thật theo `quizId:docVersion`.
4. Create quiz từ selected questions: create quiz BE -> save draft -> navigate editor; fallback local chỉ dùng khi dev chưa có API base.
5. Publish/editor commands: save draft trước publish, dùng idempotency key ổn định, invalidate editor/runtime cache và clear normalized package cache. FE adapter cung da co slide upsert/reorder commands cho contract BE day du.
6. Report data layer/UI wiring: da co FE adapter/query cho `quizQuestionAnalytics` va `assignmentReport`, key co tenant scope, staleTime khoang 60 giay, khong mock fallback khi API base da bat. `HomeworkProgressPage` da wire `assignmentReport` va `quizQuestionAnalytics`; `PlayerShell` da invalidate report cache sau final submit. Buoc tiep theo la verify submit/report roundtrip tren browser that.
7. Assignment distribution: `AssignHomeworkPage` da goi BE create assignment khi API base bat, voi idempotency key, quizIds, recipientMode/classId/groupId/studentIds, start/due, attemptLimit va maxDurationMinutes. No-API-base van giu local callback cu de dev/offline khong vo UI.
8. E2E production: chạy qua Postgres/auth thật để xác nhận seed, RLS, package answer-key policy, assignment create, cache invalidation và submit/report.

### 9.9 Checklist còn lại cho FE/E2E trước khi chốt production
Các mục dưới đây chưa nên coi là hoàn tất chỉ bằng unit/targeted test; cần chạy trên Postgres/auth thật.

- Bật Docker Desktop hoặc cấu hình `ERG_TEST_POSTGRES_URL`/`ERG_TEST_POSTGRES_USERNAME`/`ERG_TEST_POSTGRES_PASSWORD`, sau đó chạy `.\mvnw "-Derg.reseed.devdb=true" "-Dtest=DatabaseReseedRunnerTest" test` để xác minh seed trên Postgres thật.
- Sau khi seed test pass, xác minh `questionBankWorkspace` trả dữ liệu BE cho các ID mock cũ.
- Gọi package thật cho `erg-training-demo` và xác nhận package có answer key khi `gradingMode=client-first`.
- Gọi package thật cho `erg-testing-demo` và xác nhận package không leak answer key khi `gradingMode=server-authoritative`.
- Mở `/quiz-bank`, click row quiz-bank, xác nhận route `/quiz-editor?quizId=...` hydrate đúng draft.
- Tạo quiz từ selected questions, save draft, publish, sau đó mở runtime package và xác nhận nhận version/package mới sau cache invalidation.
- Chạy một attempt training: không shuffle, feedback/local grading hoạt động, final submit ghi attempt/report.
- Chạy một attempt testing: shuffle deterministic, không feedback từng câu, start attempt chỉ xảy ra sau begin, submit được BE chấm theo version pin.
- Sau submit, goi lai `assignmentReport` va `quizQuestionAnalytics` qua FE report data layer de xac nhan score/progress/question analytics hien tren UI dung voi pinned `quizVersionId` va bang normalized.
- Kiểm tra retry network/offline nhẹ: start attempt retry không tạo duplicate attempt; client event có `id` để replay idempotent.
- Kiểm tra RLS/auth/session thật: tenant lấy từ principal/session, không lấy từ header client.

---

## Phụ lục — điểm lệch spec cần nhớ
| Hạng mục | Spec | Đã build | Lý do |
|---|---|---|---|
| Draft blobs qua GraphQL | JSON scalar | **String (JSON)** | Chưa wire custom scalar |
| `tags` câu hỏi | `text[]` | **jsonb `List<String>`** | Đồng nhất cách map list + GIN |
| Chữ ký package | Ed25519 | **HMAC-SHA256** | Chưa có JCE Ed25519 wiring; contract giống hệt |
| Assignment cũ | chỉ normalized | **dual-write cột legacy** | Giữ read model LMS cũ chạy khi chuyển tiếp |
| Cột monolithic quiz cũ | retire | **giữ lại** | `LmsRuntimeService` + query-count test còn dùng; retire khi hết consumer |
| Shuffle theo learner | BE shuffle package cá nhân hóa | **FE shuffle deterministic theo attempt seed** | Giảm tải BE, giữ canonical package cache được cho 5000 học sinh |
| Start attempt | start ngay khi load bài | **local session trước, server start khi learner bắt đầu/submit** | Giảm write-load và tránh attempt rác |
| Package cache | chỉ app cache nội bộ | **BE Caffeine + HTTP ETag + FE normalized memory cache** | Chịu burst load và 304/body rỗng |
| Start idempotency | header chưa persist | **persist `start_idempotency_key` + unique index** | Chặn duplicate attempt khi retry/race |

> **Trạng thái xác minh cập nhật 2026-07-08:** targeted BE tests đã pass 43/43 (`LcmsQuizAuthoringControllerTest`, `GraphQlEndpointConfigurationTest`, `LcmsQuizWorkspaceGraphQlControllerTest`, `CacheConfigTest`, `LmsControllerHttpCacheTest`, `LmsRuntimeServiceAttemptRulesTest`, `QuestionBankDemoSeedParityTest`, `QuizCompileServiceTest`, `GradingEngineTest`). `QuestionBankDemoSeedParityTest` pass 3/3 và guard FE question-bank mock 16/16 IDs, question type/difficulty/status metadata, quiz-bank rows/questionIds/kind/status, và runtime question type demos 14/14 có trong BE seed. Contract/read-model tests mới pass 18/18 với `LcmsWorkspaceReadServiceTest`, `LcmsWorkspaceGraphQlControllerTest`, `GraphQlEndpointConfigurationTest`, xác nhận question-bank question metadata và quiz-bank composition đã expose đúng GraphQL. FE targeted tests pass 9 files / 31 tests với `create-quiz-from-bank.test.ts`, `question-bank-api.test.ts`, `question-bank-query.test.ts`, `quiz-editor-api.test.ts`, `quiz-editor-query.test.ts`, `quiz-runtime-api.test.ts`, `quiz-runtime-query.test.ts`, `local-quiz-attempt-store.test.ts`, `quiz-runtime-fallback.test.ts`; `bun run test src/lib/api-client.test.ts` pass 10/10 và khóa HTTP 304 revalidation; `bun run typecheck` pass sau khi thêm runtime/question-bank/quiz-editor query layers + `/quiz-editor?quizId=...` draft hydrate + quiz-bank open editor + create quiz from selected questions + publish save/publish/cache invalidation + tenant-scoped cache keys + API-enabled runtime/create/save failure propagation + runtime response-shape guards + aggregate submit response mapping + empty-304 handling. `DatabaseReseedRunnerTest` đã được chuyển sang Postgres-required verification, không còn âm thầm fallback H2 khi migration Postgres-only không tương thích H2. Lần chạy gần nhất bị chặn bởi môi trường vì Docker Desktop daemon chưa chạy (`docker version` báo không kết nối được `npipe:////./pipe/dockerDesktopLinuxEngine`); cần bật Docker hoặc trỏ `ERG_TEST_POSTGRES_URL` tới Postgres thật để xác minh RLS + Flyway + seed + submit/report end-to-end.

> **Trang thai xac minh cap nhat 2026-07-09:** FE targeted suite pass 15 files / 60 tests voi `lms-assignment-command-api.test.ts`, `lms-assignment-command-query.test.ts`, `homework-progress-page.test.ts`, `create-quiz-from-bank.test.ts`, `question-bank-api.test.ts`, `question-bank-query.test.ts`, `quiz-editor-api.test.ts`, `quiz-editor-query.test.ts`, `quiz-runtime-api.test.ts`, `quiz-runtime-query.test.ts`, `local-quiz-attempt-store.test.ts`, `quiz-runtime-fallback.test.ts`, `quiz-report-api.test.ts`, `quiz-report-query.test.ts`, `api-client.test.ts`; `bun run typecheck` pass. Runtime/editor/assignment mutation keys da duoc test khoa cho Tauri/offline instrumentation. BE da co LCMS report GraphQL (`quizQuestionAnalytics`, `assignmentReport`), FE da them report adapter/query layer tai `src/features/lcms/quiz/quiz-reports`, `HomeworkProgressPage` da wire `assignmentReport` vao UI hien co, `AssignHomeworkPage` da goi BE create assignment khi API base bat, quiz-editor adapter da cover slide upsert/reorder endpoints, va LMS homework/progress query keys da duoc chuan hoa de assignment mutations invalidate dung cache. Full production proof van bi chan boi Docker Desktop/Postgres env va can chay lai Postgres/auth/RLS/browser E2E truoc khi chot done.

> **Trang thai xac minh cap nhat 2026-07-09 lan 2:** BE `AssignmentDetail` da expose pinned `resources` bang projection read, FE `LmsAssignment`/`AssignmentRun` da mang `quizVersionId`, va `HomeworkProgressPage` da goi `quizQuestionAnalytics` bang pinned version de hien tom tat analytics trong card tong quan. `bun run typecheck` pass; FE targeted report/assignment suite pass 6 files / 18 tests. BE unit tests `LmsReadModelServiceTest` va `LmsHomeworkWorkspaceGraphQlControllerTest` pass 22 tests. BE integration `LmsAssignmentProgressWorkspaceQueryCountIntegrationTest` hien bi chan truoc code can test do H2 khong chay duoc migration Postgres-only `V11__relationship_integrity_seed_cleanup.sql` (`DELETE ... USING`), nen can chay bang Postgres/Docker hoac sua migration test profile truoc khi lay bang chung query-count production.

> **Trang thai xac minh cap nhat 2026-07-09 lan 3:** BE da mo rong pinned `resources` len ca `HomeworkAssignmentCard` va `AssignmentSummaryItem`; read model load resources theo batch `assignmentId IN (...)` tren page hien tai de tranh N+1. FE `LmsClassWorkspace` va `LmsTeacherHomeworkWorkspace` query da request `resources`, nen `mapAssignmentsToRuns` co `quizVersionId` ngay tu danh sach bai giao. `.\mvnw "-Dtest=LmsReadModelServiceTest,LmsHomeworkWorkspaceGraphQlControllerTest" test` pass 22 tests; `bun run typecheck` pass; FE targeted mapper/report/cache suite pass 4 files / 10 tests.

> **Trang thai xac minh cap nhat 2026-07-09 lan 4:** Audit cache BE/FE phat hien assignment command chua evict cac read cache BE, co the lam FE refetch ve du lieu stale du TanStack invalidate dung. Da them eviction sau commit cho `lmsHomeworkWorkspace`, `lmsClassWorkspace`, `lmsAssignmentProgress`, `studentDashboard`; idempotent replay khong evict vi khong co write moi. `.\mvnw "-Dtest=LmsAssignmentCommandServiceTest,LmsReadModelServiceTest,LmsHomeworkWorkspaceGraphQlControllerTest,CacheConfigTest" test` pass 29 tests; FE assignment command cache suite pass 2 files / 8 tests.

> **Trang thai xac minh cap nhat 2026-07-09 lan 5:** Audit tiep phan attempt/runtime phat hien `startAttempt`, `saveDraft`, `saveAnswer`, `submitAttempt` truoc do ghi attempt/projection nhung chua clear BE read caches. Da them eviction sau commit cho `lmsHomeworkWorkspace`, `lmsClassWorkspace`, `lmsAssignmentProgress`, `studentDashboard`; rejected submit va idempotent start replay khong evict vi khong co write moi. `syncAttempt` tam thoi khong evict vi chi replay event log, khong merge visible answer/status/projection. `.\mvnw "-Dtest=LmsRuntimeServiceAttemptRulesTest" test` pass 7 tests; `.\mvnw "-Dtest=LmsRuntimeServiceAttemptRulesTest,LmsAssignmentCommandServiceTest,LmsReadModelServiceTest,LmsHomeworkWorkspaceGraphQlControllerTest,CacheConfigTest,QuizReportServiceTest" test` pass 40 tests.

> **Trang thai xac minh cap nhat 2026-07-09 lan 6:** Audit FE Elearning phat hien `student-dashboard-api.ts` con catch GraphQL error va fallback ve mock khi API base da bat; component cung bom mock announcements khi BE tra empty. Da sua de no-API-base moi dung mock, API-enabled error propagate, API-enabled empty giu empty assignments/announcements, va query key `student-dashboard` co them tenant scope. `bun run test src/features/elearning/student-dashboard/api/student-dashboard-api.test.ts` pass 3 tests; `bun run typecheck` pass.

> **Trang thai xac minh cap nhat 2026-07-09 lan 7:** Audit FE LMS teacher bootstrap phat hien `lms-dashboard-api.ts` va `LmsTeacherShell` van co fallback mock khi API base bat: session/list education units loi thi tra mock, homework workspace empty thi hien `assignmentRuns` mock. Da sua de API-enabled error/empty khong bi che; no-API-base van dung mock dev/offline. `bun run test src/features/lms/infrastructure/lms-dashboard-api.test.ts src/features/lms/api/lms-graphql-api.test.ts src/features/lms/api/lms-assignment-command-query.test.ts` pass 3 files / 8 tests; `bun run typecheck` pass.

> **Trang thai xac minh cap nhat 2026-07-09 lan 8:** Audit tiep FE learning resources/LCMS authoring phat hien `learning-resource-api.ts`, `teacher-resource-dashboard-api.ts` va `LearningResourceAuthoringWorkspace` con fallback mock/empty khi API base bat. Da sua de API-enabled library bootstrap/progress loi propagate, managed-school empty giu empty that, list units loi khong fallback classroom mock, authoring workspace empty/error khong bom mock explorer/resources, va quiz-bank attach list khong fallback quiz mock khi API-enabled empty/error. `bun run test src/features/lms/learning-resources/api/learning-resource-explorer-operations-api.test.ts src/features/lms/learning-resources/api/teacher-resource-dashboard-schools.test.ts` pass 2 files / 9 tests; `bun run typecheck` pass.

> **Trang thai xac minh cap nhat 2026-07-09 lan 9:** Audit tiep FE classroom/account/admin phat hien `ClassManagementPage`, `ClassStudentsWorkspace`, `LmsLoginLogsPage` va `centers-api.ts` con co the che API-enabled empty/error bang mock students, mock login sessions hoac empty centers. Da sua de API-enabled class workspace missing data la empty that, login sessions empty/error khong hien mock, va center load error propagate khi co API base; no-API-base van giu mock/dev fallback. `bun run test src/features/lms/classroom/components/classroom-api-fallback.test.ts src/features/lcms/admin-operations/api/centers-api.test.ts src/features/lms/components/lms-login-logs-page.test.ts` pass 3 files / 8 tests; `bun run typecheck` pass.

> **Trang thai xac minh cap nhat 2026-07-09 lan 10:** Audit tiep LCMS authoring dialog phat hien `TaxonomyCreateDialog` van fallback `mockExerciseLibrary` khi quiz-bank attach list rong, ke ca khi API base bat va question-bank query dang empty/error. Da tach `resolveExerciseLibraryItems` sang util va sua de API-enabled empty quiz bank giu empty that; no-API-base moi dung exercise mock. `bun run test src/features/lcms/admin-operations/utils/learning-resource-content-dialog.test.ts src/features/lms/classroom/components/classroom-api-fallback.test.ts src/features/lcms/admin-operations/api/centers-api.test.ts src/features/lms/components/lms-login-logs-page.test.ts src/features/lms/learning-resources/api/learning-resource-explorer-operations-api.test.ts src/features/lms/learning-resources/api/teacher-resource-dashboard-schools.test.ts` pass 6 files / 24 tests; `bun run typecheck` pass.

> **Trang thai xac minh cap nhat 2026-07-09 lan 11:** Audit tiep `AssignHomeworkPage` phat hien class recipient picker van lay hoc sinh tu `classroomStudents`/`getClassStudents` mock khi API base bat. Da them TanStack Query `classWorkspace` cho lop dang giao, resolve hoc sinh qua BE khi API-enabled, BE empty/missing giu empty that, no-API-base moi dung mock. Tai thoi diem lan 11, API-enabled grade/group submit duoc chan tam thoi de tranh ghi mock group/student IDs; trang thai nay da duoc thay the boi lan 12/13 ben duoi. `bun run test src/features/lms/components/assign-homework-student-utils.test.ts src/features/lms/classroom/components/classroom-api-fallback.test.ts src/features/lcms/admin-operations/utils/learning-resource-content-dialog.test.ts src/features/lcms/admin-operations/api/centers-api.test.ts src/features/lms/components/lms-login-logs-page.test.ts` pass 5 files / 17 tests; `bun run typecheck` pass.

> **Trang thai xac minh cap nhat 2026-07-09 lan 12:** Noi tiep group mode cua `AssignHomeworkPage`: khi API base bat, page dung `useLmsStudentGroupsQuery(undefined, assignmentClassId)` de hydrate nhom hoc sinh tu BE `lms.studentGroups`, map ve UI group option va resolve empty/error theo BE thay vi fallback `assignmentGroups` mock. API-enabled group submit duoc mo lai vi recipient lay tu BE group/student IDs; tai thoi diem lan 12 grade mode con cho BE-backed grade picker va da duoc mo lai o lan 13. `bun run test src/features/lms/components/assign-homework-student-utils.test.ts src/features/lms/api/lms-assignment-command-query.test.ts src/features/lms/api/lms-assignment-command-api.test.ts src/features/lms/classroom/components/classroom-api-fallback.test.ts` pass 4 files / 16 tests; `bun run typecheck` pass.

> **Trang thai xac minh cap nhat 2026-07-09 lan 13:** Noi tiep grade mode cua `AssignHomeworkPage`: khi API base bat, page dung TanStack `useQueries` voi key `["lms","assign-homework","grade-class-workspace", schoolId, classId]` de hydrate hoc sinh tung lop grade tu BE `classWorkspace`, build `gradeStudentIdsByClass`, va `buildAssignmentRecipientInput` resolve recipient IDs bang `resolveAssignHomeworkGradeStudentIds` thay vi mock. API-enabled grade submit da duoc mo lai vi class/group/grade deu lay recipient tu BE-backed data; BE empty/missing giu empty that, khong fallback `classroomStudents`. `bun run test src/features/lms/components/assign-homework-student-utils.test.ts src/features/lms/api/lms-assignment-command-query.test.ts src/features/lms/api/lms-assignment-command-api.test.ts src/features/lms/classroom/components/classroom-api-fallback.test.ts` pass 4 files / 17 tests; `bun run test src/features/lms/components/assign-homework-student-utils.test.ts src/features/lms/api/lms-assignment-command-query.test.ts src/features/lms/api/lms-assignment-command-api.test.ts src/features/lms/classroom/components/classroom-api-fallback.test.ts src/features/lcms/admin-operations/utils/learning-resource-content-dialog.test.ts src/features/lcms/admin-operations/api/centers-api.test.ts src/features/lms/components/lms-login-logs-page.test.ts` pass 7 files / 28 tests; `bun run typecheck` pass.

> **Trang thai xac minh cap nhat 2026-07-09 lan 14:** Giam tai BE cho `AssignHomeworkPage`: `lmsStudentGroupsQueryOptions`/`useLmsStudentGroupsQuery` da nhan `enabled` option, student-groups query chi bat khi target mode la `group`; class workspace query chinh chi bat o `class/group`, con grade da dung `useQueries` rieng chi enabled o `grade`. Cach nay giu UI hien co nhung tranh goi GraphQL phu khi user dang lam mode khac, phu hop muc tieu giam tai BE luc nhieu nguoi mo man giao bai. `bun run test src/features/lms/api/lms-assignment-command-query.test.ts src/features/lms/components/assign-homework-student-utils.test.ts src/features/lms/api/lms-assignment-command-api.test.ts src/features/lms/classroom/components/classroom-api-fallback.test.ts` pass 4 files / 18 tests; `bun run typecheck` pass.

> **Trang thai xac minh cap nhat 2026-07-09 lan 15:** Audit tiep `StudentGroupsPage` phat hien page da ghi student groups len BE nhung roster hoc sinh de them/bo khoi nhom van doc tu `classroomStudents` mock, co nguy co persist mock student IDs khi API base bat. Da noi roster sang BE `classWorkspace` bang TanStack `useQueries` voi key `["lms","student-groups","class-roster", schoolId, classId]`, chi query theo class filter hien tai hoac fan-out khi user chon tat ca lop; no-API-base moi dung mock. Student-groups read cung duoc `enabled: apiBacked` de offline/dev khong tao query thua. `bun run test src/features/lms/api/lms-assignment-command-query.test.ts src/features/lms/api/lms-assignment-command-api.test.ts src/features/lms/components/assign-homework-student-utils.test.ts src/features/lms/classroom/components/classroom-api-fallback.test.ts` pass 4 files / 18 tests; `bun run typecheck` pass.

> **Trang thai xac minh cap nhat 2026-07-09 lan 16:** Audit tiep `LmsTeacherShell` phat hien sau khi `AssignHomeworkPage` create assignment that tren BE, shell van them `createdRuns` local voi id gia dang `assignment-Date.now`. Run gia nay co the hien trong homework list va lam progress/report goi sai assignment id. Da gom callback tao assignment/exercise vao `handleLocalAssignmentCreated`: API-backed chi navigate/refetch qua TanStack invalidation va BE homework workspace, no-API-base moi them local run cho dev/offline. `bun run test src/features/lms/api/lms-assignment-command-query.test.ts src/features/lms/api/lms-assignment-command-api.test.ts src/features/lms/components/assign-homework-student-utils.test.ts src/features/lms/classroom/components/classroom-api-fallback.test.ts src/features/lms/components/homework-progress-page.test.ts` pass 5 files / 21 tests; `bun run typecheck` pass.

> **Trang thai xac minh cap nhat 2026-07-09 lan 17:** Khoa chat hon fix lan 16: tach `resolveLmsTeacherRuns` sang util thuan va doi `LmsTeacherShell` de API-backed `runs` chi lay `workspaceRuns` tu BE, khong merge `createdRuns` local trong bat ky truong hop nao. No-API-base van giu `createdRuns + workspace/mock fallback` de dev/offline. Them `lms-teacher-shell.test.ts` de lock behavior nay. `bun run test src/features/lms/components/lms-teacher-shell.test.ts src/features/lms/api/lms-assignment-command-query.test.ts src/features/lms/api/lms-assignment-command-api.test.ts src/features/lms/components/homework-progress-page.test.ts` pass 4 files / 14 tests; `bun run typecheck` pass.

> **Trang thai xac minh cap nhat 2026-07-09 lan 18:** Audit TanStack cache scope phat hien `lmsAssignmentReadQueryKeys.homeworkWorkspace()` va `assignmentProgress(assignmentId)` thieu tenant trong key, co rui ro cross-tenant cache bleed cho FE web/Tauri. Da doi homework key thanh `["lms-teacher-shell","teacher-homework-workspace",tenantId]`, assignment progress key thanh `["lms","assignment-progress-workspace",tenantId,assignmentId]`, va truyen tenant tu `getDefaultTenantId()` o `LmsTeacherShell`/`HomeworkProgressPage`. Invalidation root van giu nguyen de mutations clear rong cac scope lien quan. `bun run test src/features/lms/api/lms-assignment-command-query.test.ts src/features/lms/components/lms-teacher-shell.test.ts src/features/lms/components/homework-progress-page.test.ts src/features/lms/api/lms-assignment-command-api.test.ts` pass 4 files / 14 tests; `bun run typecheck` pass.

> **Trang thai xac minh cap nhat 2026-07-09 lan 19:** Audit TanStack account cache phat hien `queryKeys.account.loginSessions()` qua chung, co the giu session logs cua tai khoan/tenant truoc khi FE chay lau hoac fork sang Tauri. Da doi key thanh `['account','login-sessions',tenantId,accountId|'anonymous']`; `LmsLoginLogsPage` lay `account.id` tu `useAuthSession('lms')` va tenant tu `getDefaultTenantId()` de truyen vao key. No-API-base fallback mock session van giu nguyen, API-enabled empty/error khong fallback mock. `bun run test src/lib/query-keys.test.ts src/features/lms/components/lms-login-logs-page.test.ts src/features/lms/api/lms-assignment-command-query.test.ts src/features/lms/components/lms-teacher-shell.test.ts` pass 4 files / 9 tests; `bun run typecheck` pass.

> **Trang thai xac minh cap nhat 2026-07-09 lan 20:** Audit BE caching cho report phat hien `QuizReportService.assignmentReport` va `questionAnalyticsByVersion` doc normalized runtime rows nhung chua co Caffeine cache, trong khi FE da dung TanStack `quiz-reports` cho homework progress. Da them BE caches `quizAssignmentReport` va `quizQuestionAnalytics` TTL 30s co `recordStats()`, them `@Cacheable` tenant-scoped (`{tenantId,assignmentId}` va `{tenantId,quizVersionId}`), va noi vao after-commit eviction cua `LmsRuntimeService` (`startAttempt`, `saveAnswer`, `submitAttempt`) cung `LmsAssignmentCommandService` (create/update recipients/update/delete assignment). FE tiep tuc invalidate root `quiz-reports` nhu hien co; BE se giam tai aggregate queries khi nhieu giao vien/hoc sinh doc report dong thoi nhung khong giu stale sau attempt/assignment write. `syncAttempt` van khong evict vi chi ghi event log append-only, khong doi visible progress/report state. Verification: `./mvnw "-Dtest=CacheConfigTest,LmsRuntimeServiceAttemptRulesTest,LmsAssignmentCommandServiceTest,QuizReportServiceTest" test` pass 4 backend test files / 19 tests.

> **Trang thai xac minh cap nhat 2026-07-09 lan 21:** Audit FE session/bootstrap cache phat hien `queryKeys.dashboard.bootstrap()` va `queryKeys.lmsTeacherShell.bootstrap()` van la key chung `['dashboard','bootstrap']`, dong thoi `loadCurrentSessionBootstrap()` co in-memory cache/request global 60s cho `/api/v1/sessions/current`. Neu doi account/portal nhanh trong web hoac future Tauri, FE co the reuse bootstrap cua user cu. Da doi dashboard bootstrap key thanh `['dashboard','bootstrap',portal,tenantId,accountId|'anonymous']`, `LmsTeacherShell` dung `queryKeys.lmsTeacherShell.bootstrap(tenantId, account?.id)`, `DashboardShell` truyen tenant/account vao key, va session-bootstrap memory cache scoped theo portal/session portal/account/token fingerprint. UI khong doi. Verification: `bun run test src/lib/query-keys.test.ts src/features/lms/infrastructure/lms-dashboard-api.test.ts` pass 2 files / 6 tests; `bun run typecheck` pass.

> **Trang thai xac minh cap nhat 2026-07-09 lan 22:** Audit tiep TanStack keys phat hien `queryKeys.adminOperations` cho education units, learning resources workspace va user-access dang thieu tenant scope. Day la nhom LCMS/admin read data nhay cam voi school/tenant scope, co nguy co bleed khi admin doi tenant/session trong web lau hoac Tauri. Da them `tenantId` vao `educationUnits`, `learningResourcesV2`, `learningResourcesWorkspace`, va toan bo user-access keys (`users/options/detail/preview/scopes`), giu optional default `getDefaultTenantId()` de khong doi UI/call sites hien co. Invalidation prefix van match theo tenant root. Verification: `bun run test src/lib/query-keys.test.ts` pass 1 file / 3 tests; `bun run typecheck` pass.

> **Trang thai xac minh cap nhat 2026-07-09 lan 23:** Audit direct TanStack keys phat hien cac query `classWorkspace` trong `AssignHomeworkPage`, `ClassManagementPage`, `ClassStudentsWorkspace` va roster cua `StudentGroupsPage` co school/class scope nhung thieu tenant scope. Da them helper `lmsAssignmentReadQueryKeys.classWorkspace({tenantId,usage,schoolId,classId})` va doi bon luong doc class workspace sang helper nay: assign homework class, assign homework grade fan-out, class management, class students workspace, student-groups roster. UI/data contract khong doi; key co them `usage` de khong tron cache giua cac projection size/filter khac nhau. Verification: `bun run test src/features/lms/api/lms-assignment-command-query.test.ts src/features/lms/components/assign-homework-student-utils.test.ts src/features/lms/classroom/components/classroom-api-fallback.test.ts src/features/lms/components/lms-teacher-shell.test.ts` pass 4 files / 15 tests; `bun run typecheck` pass.

> **Trang thai xac minh cap nhat 2026-07-09 lan 24:** Audit teaching-calendar FE phat hien LMS/LCMS calendar workspace key co portal va filter input nhung reference catalog key van thieu tenant, con workspace key duoc ghep truc tiep trong component. Da them `teachingCalendarQueryKeys` trong `teaching-calendar-api.ts` voi `portalRoot(portal,tenantId)`, `workspace(portal,usage,input)` va `referenceCatalog(portal,tenantId)`, sau do doi `LmsCenterUpCalendarWorkspace` va `LcmsGoogleCalendarWorkspace` sang helper nay. Invalidation sau create schedule gio invalidate theo portal-root tenant-scoped. UI/GraphQL contract khong doi. Verification: `bun run test src/features/teaching-calendar/api/teaching-calendar-query-keys.test.ts` pass 1 file / 1 test; `bun run typecheck` pass.

> **Trang thai xac minh cap nhat 2026-07-09 lan 25:** Audit notification FE phat hien `notificationQueryKeys` chi scoped theo portal, trong khi inbox/unread/detail/preferences/device registration la du lieu user-specific va co the bi giu lau boi Service Worker/Tauri/offline cache. Da them `NotificationQueryScope` gom `tenantId` va `accountId`, doi `root/inbox/unreadCount/detail/preferences/device` sang key `portal + tenant + account`, them `resolveNotificationQueryScope(portal)` doc account tu session hien tai va tenant tu `getDefaultTenantId()`. `LmsNotificationCenter`, `LmsNotificationListPage`, `LmsNotificationDetailPage` va `LmsTeacherShell` da dung scoped keys; mutation va Service Worker notification message invalidate dung scoped root. UI/API payload khong doi. Dong thoi cap nhat notification API tests theo contract hien tai: `api-client` khong gui `X-Portal`/`X-Tenant-ID` nua vi BE suy ra tu URL/session. Verification: `bun run test src/features/notifications/api/notification-api.test.ts` pass 1 file / 5 tests; `bun run test src/features/lms/components/lms-teacher-shell.test.ts` pass 1 file / 2 tests; `bun run typecheck` pass.

> **Trang thai xac minh cap nhat 2026-07-09 lan 26:** Audit LMS learning-resource library phat hien `libraryBootstrapQueryKey(schoolId,academicYear)` thieu tenant va `libraryProgressQueryKey(schoolId,academicYear)` thieu ca tenant/account, trong khi progress la du lieu theo giao vien. Da doi helper sang object-scope: bootstrap key `learning-resources/library-bootstrap/tenant/school/year`, progress key `learning-resources/library-progress/tenant/account/school/year`; `LearningResourceDashboardScopeProvider` nhan them optional `tenantId/accountId`, va `LmsTeacherShell` truyen `tenantId` cung `account?.id`. Prefetch va return `progressQueryKey` dung cung scoped key, UI/API payload khong doi. Verification: `bun run test src/features/lms/learning-resources/hooks/use-learning-resource-library-catalog.test.ts` pass 1 file / 2 tests; `bun run test src/features/lms/components/lms-teacher-shell.test.ts` pass 1 file / 2 tests; `bun run typecheck` pass.

> **Trang thai xac minh cap nhat 2026-07-09 lan 27:** Audit shared FE query-key registry phat hien `queryKeys.questionBank.workspace()` van tra key global `question-bank/workspace`, trong khi question-bank API layer da tenant-scoped. Neu FE tich hop man LCMS moi dung nham shared registry thi co the tron cache question/quiz giua tenant. Da doi `queryKeys.questionBank.workspace(tenantId=getDefaultTenantId())` thanh `question-bank/workspace/tenantId` va them contract test trong `src/lib/query-keys.test.ts`. UI/API payload khong doi. Verification: `bun run test src/lib/query-keys.test.ts` pass 1 file / 4 tests; `bun run test src/features/lcms/quiz/question-bank/api/question-bank-query.test.ts` pass 1 file / 1 test; `bun run typecheck` pass.

> **Trang thai xac minh cap nhat 2026-07-09 lan 28:** Audit quiz-editor TanStack invalidation phat hien cac mutation create/save/upsert/reorder/publish dang invalidate `quizEditorQueryKeys.root` toan cuc, va publish invalidate `quizRuntimeQueryKeys.root` toan cuc. Da them `quizRuntimeQueryKeys.packageRoot(tenantId)` va doi quiz-editor mutations sang invalidate `quizEditorQueryKeys.workspaceRoot(getDefaultTenantId())` + draft/version exact keys; publish van `clearNormalizedQuizPackageCache(quizId)` nhung TanStack runtime chi invalidate `quizRuntimeQueryKeys.packageRoot(getDefaultTenantId())` thay vi toan bo runtime. Muc tieu la giam refetch thua khi nhieu teacher publish/luu quiz dong thoi, giu UI/API payload khong doi. Verification: `bun run test src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-query.test.ts src/features/lcms/quiz/quiz-editor/api/quiz-editor-query.test.ts` pass 2 files / 5 tests; `bun run typecheck` pass; `rg "invalidateQueries\(\{ queryKey: quizEditorQueryKeys\.root|invalidateQueries\(\{ queryKey: quizRuntimeQueryKeys\.root" src/features/lcms/quiz src/components/quiz -n` khong con ket qua.

> **Trang thai xac minh cap nhat 2026-07-09 lan 29:** Audit LMS assignment/student-group mutations phat hien read keys da tenant-scoped nhung invalidation van dung broad roots `homeworkRoot`, `assignmentProgressRoot`, `studentGroupsRoot`, co the refetch thua cross-tenant trong web/Tauri long-running session. Da them `homeworkTenantRoot(tenantId)`, `assignmentProgressTenantRoot(tenantId)`, `studentGroupsTenantRoot(tenantId)` va doi `invalidateAssignmentReads`/`invalidateStudentGroupReads` sang tenant hien tai tu `getDefaultTenantId()`. `quizReportQueryKeys.assignmentReport` invalidation cung truyen tenant ro rang. UI/API payload khong doi. Verification: `bun run test src/features/lms/api/lms-assignment-command-query.test.ts src/features/lms/components/assign-homework-student-utils.test.ts src/features/lms/classroom/components/classroom-api-fallback.test.ts src/features/lms/components/lms-teacher-shell.test.ts` pass 4 files / 15 tests; `bun run typecheck` pass; `rg "invalidateQueries\(\{ queryKey: lmsAssignmentReadQueryKeys\.(homeworkRoot|assignmentProgressRoot|studentGroupsRoot)" src/features/lms -n` khong con ket qua.

> **Trang thai xac minh cap nhat 2026-07-09 lan 30:** Audit quiz report FE cache phat hien `quizReportQueryKeys.root` van bi invalidate toan cuc sau runtime submit va assignment/student-group writes, trong khi report keys da co tenant trong tung leaf. Da them `quizReportQueryKeys.reportTenantRoot(tenantId)` va doi key shape sang `quiz-reports/tenantId/assignment-report/assignmentId` va `quiz-reports/tenantId/question-analytics/quizVersionId`. `PlayerShell` sau submit attempt va `lms-assignment-command-query` sau assignment/group mutations gio invalidate `reportTenantRoot(getDefaultTenantId())` thay vi `root`; assignment report exact invalidation van dung assignmentId + tenant. UI/API payload khong doi. Verification: `bun run test src/features/lms/api/lms-assignment-command-query.test.ts src/features/lcms/quiz/quiz-reports/api/quiz-report-query.test.ts` pass 2 files / 6 tests; `bun run typecheck` pass; `rg "invalidateQueries\(\{ queryKey: quizReportQueryKeys\.root|quizReportQueryKeys\.root" src -n` chi con helper noi bo `reportTenantRoot`.

> **Trang thai xac minh cap nhat 2026-07-09 lan 31:** Audit runtime package memory cache phat hien `normalizedPackageCache` trong `quiz-runtime-api.ts` keyed theo `portal:quizId`, trong khi TanStack package key da co `tenantId`. Khi BE tra 304/empty body, FE co nguy co reuse normalized package cua tenant khac trong long-running web/Tauri process. Da them tham so `tenantId=getDefaultTenantId()` vao `getQuizPackage(quizId,portal,tenantId)`, doi cache key thanh `tenantId:portal:quizId`, va `quizPackageQueryOptions` truyen tenantId tu query key xuong API cache. `clearNormalizedQuizPackageCache(quizId)` van hoat dong vi key moi van ket thuc bang `:quizId`. UI/API payload khong doi. Verification: `bun run test src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-api.test.ts src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-query.test.ts` pass 2 files / 22 tests; `bun run typecheck` pass.

> **Trang thai xac minh cap nhat 2026-07-09 lan 32:** Audit local/offline attempt persistence phat hien `localQuizAttemptStore` keyed theo `assignmentId:quizId`, co nguy co restore nham draft/answer/event queue khi doi hoc sinh/account/tenant trong cung browser/Tauri process. Da them `LocalQuizAttemptScope` gom `tenantId`, `portal`, `accountId`; session luu optional scope fields va key moi la `tenantId:portal:accountId:assignmentId:quizId`. `PlayerShell` lay viewer hien tai qua `getCurrentElearningViewerSession()`, truyen `attemptScope` vao `getSession/createFreshLocalSession`, va cac fail paths cung doc scoped session. `getSession()` khong scope van doc default anonymous scoped key truoc roi fallback legacy key de khong mat draft cu trong test/dev. UI/API payload khong doi. Verification: `bun run test src/features/lcms/quiz/quiz-runtime/api/local-quiz-attempt-store.test.ts src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-api.test.ts src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-query.test.ts` pass 3 files / 26 tests; `bun run typecheck` pass.

> **Trang thai xac minh cap nhat 2026-07-09 lan 33:** Audit `src/lib/api-client.ts` phat hien in-flight GET dedupe key chi gom URL + Authorization. Dieu nay giam duplicate request tot, nhung chua phan biet explicit portal va browser cache mode trong long-running web/Tauri session. Da doi dedupe key sang URL + resolved portal + Authorization + cache mode (`init.cache ?? "no-store"`) + cac response-variant headers on dinh (`Accept`, `Accept-Language`, `If-Modified-Since`, `If-None-Match`, `Range`), van bo qua header nhieu nhu `X-Request-ID`. Muc tieu: khong gop nham request cung URL/token khi teacher dung merged LMS/LCMS credential hoac khi runtime package co request `cache:"default"` song song voi request `no-store`. UI/API payload khong doi. Verification: `bun run test src/lib/api-client.test.ts` pass 12/12; targeted combined suite `bun run test src/lib/api-client.test.ts src/features/lms/learning-resources/api/teacher-resource-dashboard-schools.test.ts` pass 2 files / 18 tests; `bun run typecheck` pass.

> **Trang thai xac minh cap nhat 2026-07-09 lan 34:** Audit LMS teacher resource dashboard phat hien `subjectTreeCache` va `subjectProgressCache` la module-level in-flight promise cache keyed theo subject/node/school/year, chua co tenant/account credential scope. Cache nay tu xoa sau khi promise ket thuc, nhung neu doi account/tenant trong cung browser/Tauri khi request cu chua ve thi request moi co the reuse promise cu. Da them `getTeacherDashboardCacheScope()` gom `VITE_TENANT_ID || "erg"`, portal `lms`, va current LMS access token vao cache key cho tree/progress. Day la in-memory only, khong thay doi UI/API payload. Verification: them regression tests trong `teacher-resource-dashboard-schools.test.ts` chung minh cung input nhung token khac se goi API rieng cho tree/progress; `bun run test src/features/lms/learning-resources/api/teacher-resource-dashboard-schools.test.ts` pass 6/6; combined targeted suite va `bun run typecheck` pass.

> **Trang thai xac minh cap nhat 2026-07-09 lan 35:** Audit them localStorage/cache legacy phat hien `src/lib/attempt-store.ts` chi duoc dung boi `src/lib/api.ts`; search `rg "lib/api" src -n` khong co UI/runtime consumer import `@/lib/api` nua. Runtime quiz moi dang di qua `localQuizAttemptStore` da duoc scope o lan 32, nen chua sua legacy store de tranh churn khong nam tren path FE integration hien tai. BE cache/report targeted suite da chay lai: `./mvnw "-Dtest=CacheConfigTest,LmsRuntimeServiceAttemptRulesTest,LmsAssignmentCommandServiceTest,QuizReportServiceTest" test` pass 4 files / 19 tests. Ghi chu: legacy `attempt-store.ts` nen duoc retire hoac scope neu sau nay co consumer moi quay lai dung `src/lib/api.ts`.

> **Trang thai xac minh cap nhat 2026-07-09 lan 36:** Audit BE runtime package cache xac nhan `quizRuntimePackage` da cache theo `tenantId:quizId`, HTTP endpoint `/api/v1/lms/quizzes/{quizId}/package` da co private `Cache-Control: max-age=300, must-revalidate` va ETag theo `contentHash`. Publish path `QuizPublishService.publish()` da goi `evictRuntimePackageAfterCommit(tenantId, quizId)` sau khi active version/package moi duoc save, nhung truoc do chua co test khoa behavior nay. Da them `QuizPublishServiceTest.publishEvictsRuntimePackageCacheForTenantQuiz`, mock publish happy path va verify cache `quizRuntimePackage` evict key `tenant-a:quiz-1`, dong thoi assert quiz current version/package hash duoc cap nhat. Verification: `./mvnw "-Dtest=QuizPublishServiceTest" test` pass 1/1; combined BE cache/runtime/report suite `./mvnw "-Dtest=CacheConfigTest,LmsRuntimeServiceAttemptRulesTest,LmsAssignmentCommandServiceTest,QuizReportServiceTest,QuizPublishServiceTest,LmsControllerHttpCacheTest" test` pass 6 files / 21 tests.

> **Trang thai xac minh cap nhat 2026-07-09 lan 37:** Audit FE API-enabled fallback va local cache tiep theo. Student dashboard da co guard dung: khi API base bat, GraphQL failure propagate va empty dashboard khong refill mock. Learning-resource library fallback GraphQL -> REST BE la chap nhan duoc, va chi ve mock khi khong co API base. Gap moi tim thay o LMS mobile PWA notification device: TanStack notification keys/API da tenant/account scoped nhung hook `usePwaNotifications` van luu localStorage bang key global `erg-lms-pwa-notification-device`, co nguy co account B/Tauri session B thay device cua account A la enabled. Da them `getPwaNotificationDeviceStorageKey()` gom `tenantId` tu `getDefaultTenantId()` va LMS `accountId` tu `readStoredAuthSession("lms")`; read/write/clear device registration dung key scoped, legacy key chi con cho anonymous/no-session. Them `use-pwa-notifications.test.tsx` de khoa key scope va chung minh signed-in account khong coi legacy global device la enabled. Verification: `bun run test src/features/lms/mobile/hooks/use-pwa-notifications.test.tsx` pass 3/3; `bun run test src/features/lms/mobile/hooks/use-pwa-notifications.test.tsx src/features/notifications/api/notification-api.test.ts` pass 2 files / 8 tests; `bun run typecheck` pass. UI/API payload khong doi.

> **Trang thai xac minh cap nhat 2026-07-09 lan 38:** Audit tiep TanStack query keys cho LMS class workspace phat hien `lmsAssignmentReadQueryKeys.classWorkspace` da co tenant/school/class/usage nhung chua co account. Vi BE GraphQL/RLS doc class workspace theo session, cung tenant + classId nhung doi teacher/account trong web/Tauri process co the reuse cache cua account truoc. Da them `accountId` vao class workspace key, mac dinh lay tu `readStoredAuthSession("lms")`, them `classWorkspaceTenantRoot(tenantId)` va cho assignment/student-group mutations invalidate class workspace tenant root vi workspace nay hien thi roster + assignments. Test `lms-assignment-command-query.test.ts` cap nhat expected key shape `['lms','class-workspace',tenantId,accountId,usage,schoolId,classId]`. Verification: `bun run test src/features/lms/api/lms-assignment-command-query.test.ts` pass 4/4; targeted suite `bun run test src/features/lms/api/lms-assignment-command-query.test.ts src/features/lms/components/assign-homework-student-utils.test.ts src/features/lms/classroom/components/classroom-api-fallback.test.ts src/features/lms/components/lms-teacher-shell.test.ts src/features/lms/mobile/hooks/use-pwa-notifications.test.tsx` pass 5 files / 18 tests; `bun run typecheck` pass. UI/API payload khong doi.

> **Trang thai xac minh cap nhat 2026-07-09 lan 39:** Audit TanStack `initialData`/`placeholderData` cho LCMS question bank phat hien `question-bank-query.ts` luon seed `mockQuestionBankData` vao cache, ke ca khi API base da bat. Dieu nay lam UI co the hien mock FE trong luc BE dang load/loi, trai voi nguyen tac API-enabled khong silent fallback va muc tieu mirror mock sang BE seed. Da sua `questionBankWorkspaceQueryOptions` de chi dung mock initial/placeholder khi `hasApiBase()` false; khi API-backed thi khong co `initialData`, `placeholderData` chi reuse previous data va khong tu tao mock. `QuestionBankWorkspace` dung empty typed fallback khi query chua co data hoac error de khong crash trong loading window, nhung khong fill bang mock. Verification: `bun run test src/features/lcms/quiz/question-bank/api/question-bank-query.test.ts src/features/lcms/quiz/question-bank/api/question-bank-api.test.ts` pass 2 files / 5 tests; `bun run typecheck` pass. UI khong doi; FE integration can tiep tuc coi BE GraphQL question-bank workspace la source of truth khi API base bat.

> **Trang thai xac minh cap nhat 2026-07-09 lan 40:** Audit BE cache key tiep theo phat hien `LmsRuntimeService.listClassesBySchool` cache `lmsClassesBySchool` dung `schoolId` raw, trong khi khi `schoolId` null service resolve sang `access.currentEducationUnitId()`. Hai session cung tenant va managed school set nhung current school khac co the dung chung cache key va nhan class list sai. Da them `#access.currentEducationUnitId()` vao key `lmsClassesBySchool` va them regression architecture test `lmsClassesBySchoolCacheKeyIncludesResolvedDefaultSchoolScope`. Verification: `./mvnw "-Dtest=ServiceBoundaryArchitectureTest,CacheConfigTest" test` pass 6/6; combined BE cache/runtime/report suite `./mvnw "-Dtest=CacheConfigTest,LmsRuntimeServiceAttemptRulesTest,LmsAssignmentCommandServiceTest,QuizReportServiceTest,QuizPublishServiceTest,LmsControllerHttpCacheTest" test` pass 6 files / 21 tests. API payload/UI khong doi; BE cache an toan hon cho session bootstrap/LMS class selector trong web/Tauri dai han.

> **Trang thai xac minh cap nhat 2026-07-09 lan 41:** Kiem tra phan con lai cua goal thay production proof Postgres/auth/RLS/browser van bi chan boi moi truong: `docker version` co client nhung khong ket noi duoc `npipe:////./pipe/dockerDesktopLinuxEngine`, va khong co `ERG_TEST_POSTGRES_URL`. Trong phan co the lam ngay, da sua `GraphQlEnterpriseSecurityAuditTest` de pin implementation hien tai cua `GraphQlRateLimiter` la Caffeine bounded cache (`maximumSize(20_000)`, `expireAfterAccess`) thay vi ky vong cu `ConcurrentHashMap`. Da sua `AccessControlSecurityTest` va `LmsAssignmentProgressWorkspaceQueryCountIntegrationTest` de dung `@EnabledIf("postgresProofDatabaseAvailable")`, dang ky Postgres proof DB khi co Docker/external URL, va skip sach thay vi roi xuong H2 roi fail migration Postgres-only V11. Verification: security/GraphQL suite `./mvnw "-Dtest=GraphQlEnterpriseSecurityAuditTest,GraphQlSecurityGuardContractTest,TenantContextTest,PermissionAuthorizationManagerTest,AccessControlSecurityTest,LcmsWorkspaceGraphQlControllerTest,LmsClassWorkspaceGraphQlControllerTest,StudentDashboardGraphQlControllerTest" test` pass build voi 53 tests, 0 fail/error, 17 skipped do thieu Postgres; Postgres-required proof suite `./mvnw "-Dtest=DatabaseReseedRunnerTest,TenantGucApplierIntegrationTest,QuestionBankWorkspaceQueryCountIntegrationTest,LmsAssignmentProgressWorkspaceQueryCountIntegrationTest" test` pass build voi 5 skipped, 0 fail/error. Con lai can Docker Desktop hoac `ERG_TEST_POSTGRES_URL` de chay proof that su: reseed Postgres, Tenant GUC/RLS plumbing, query-count burst proofs, browser E2E API-backed flow.

> **Trang thai xac minh cap nhat 2026-07-09 lan 42:** Xu ly loi BE user gap khi LCMS question bank goi GraphQL: PostgreSQL bao `ERROR: function lower(bytea) does not exist` vi query cu dung mau `:search is null or lower(...) like lower('%' || :search || '%')`; khi search null, Hibernate/PostgreSQL co the suy kieu parameter thanh `bytea`. Da tach `QuestionRepository.findQuestionBankQuestions` thanh 2 query ro rang: co search thi bind `search` non-null va dung `concat('%', lower(:search), '%')`, khong search thi goi `findQuestionBankQuestionsWithoutSearch`, khong con so sanh `lower(:status)` voi tham so null. `LcmsWorkspaceReadService.questionBankWorkspace` normalize search/status truoc khi chon query. Dong thoi sua nguyen nhan Elearning hoc sinh thay 0 bai: student dashboard read model cu chi loc `lms_assignments.academic_class_id`, trong khi assignment command da fan-out nguoi nhan vao `assignment_recipients` cho mode `class/group/students`. Da doi `AssignmentRepository.findStudentDashboardAssignments`, `countActiveStudentAssignments`, `countPendingStudentAssignmentActions` doc theo `AssignmentRecipient(studentId)` va chi fallback sang class legacy khi assignment chua co recipient row nao; `StudentDashboardReadService` khong con bat buoc student phai co `academicClassId` moi doc duoc assignment recipient. Cache BE da duoc xac nhan: assignment create/update/delete/update recipients da clear `studentDashboard` sau commit, idempotent replay khong clear vi khong ghi. FE can luu y: LCMS co question/quiz khong dong nghia Elearning co bai; Elearning chi hien khi co assignment active da resolve recipient cho hoc sinh do. Sau deploy can restart BE de xoa loi query dang chay trong process cu; neu van 0 bai, kiem DB `assignment_recipients` theo `student_id/auth_user_id` va assignment status/tenant truoc khi nghi FE. Verification: `./mvnw "-Dtest=LcmsWorkspaceReadServiceTest,LcmsWorkspaceGraphQlControllerTest,StudentDashboardReadServiceTest,LmsAssignmentCommandServiceTest" test` pass 27 tests, 0 fail/error.

> **Trang thai xac minh cap nhat 2026-07-09 lan 43:** Dieu tra anh user gui cho thay `/quiz-editor` dang hien `sampleQuiz` local tu FE (`src/lib/sample-quiz.ts`), khong phai bang chung DB question bank da co cung bo cau hoi; `/questions` la API-backed GraphQL `LcmsQuestionBankWorkspace` qua `POST /api/v1/graphql`, khong phai REST `/questions`. Question bank seed truoc do mirror `mock-question-bank.ts` va startup seed mac dinh dang tat, nen DB hien tai co the rong neu chua chay seed. Da them 15 cau sample editor vao `QuestionBankDemoBootstrapSeedService`, them quiz bank item `quiz-sample-editor-question-types` title `Đây là bài kiểm tra mẫu`, them initializer rieng `LIFECYCLE__QUESTION_BANK_SEED_ON_STARTUP=true` de upsert question bank ma khong reset DB, va them `QuestionBankSeedRunnerTest` guarded by `-Derg.seed.question-bank.devdb=true`. Da chay seed non-destructive vao PostgreSQL Aiven tu `.env`; runner assert co `qb-sample-q01-single-choice`, `qb-sample-q15-essay`, `quiz-sample-editor-question-types` trong DB. Verification: `./mvnw "-Dtest=QuestionBankDemoSeedParityTest,QuestionBankSeedRunnerTest" test` pass 4 tests voi runner skip khi khong bat guard; `./mvnw "-Dtest=QuestionBankSeedRunnerTest" "-Derg.seed.question-bank.devdb=true" test` pass 1/1 va seed DB that.

> **Trang thai xac minh cap nhat 2026-07-09 lan 44:** Log tiep theo cua user cho thay `lower(bytea)` da chuyen sang `QuizRepository.findQuestionBankQuizOptions`, khong con o question table. Nguyen nhan tuong tu: query `(:status is null or lower(q.status)=lower(:status))` bind `status=null` cho quiz options trong question-bank workspace. Da doi query co status sang `lower(q.status)=:status` vi service da normalize status, them `findQuestionBankQuizOptionsWithoutStatus`, va `LcmsWorkspaceReadService.questionBankWorkspace` chon query without-status khi input status null. Verification: `./mvnw "-Dtest=LcmsWorkspaceReadServiceTest,LcmsWorkspaceGraphQlControllerTest,QuestionBankDemoSeedParityTest,QuestionBankSeedRunnerTest" test` pass 18 tests, 0 fail/error, 1 seed runner skipped do guard.

> **Trang thai xac minh cap nhat 2026-07-09 lan 45:** Anh user bao `/quiz-editor` khong co fetch API. Root cause FE: khi URL khong co `?quizId=...`, `QuizEditorWorkspace` chi render state local/mock va goi `useQuizEditorDraftQuery("")` voi `enabled=false`, nen DevTools Fetch/XHR khong co request. Da them `useQuizEditorWorkspaceQuery({size:20})` vao editor mount, chon `effectiveQuizId` tu workspace (uu tien `quiz-sample-editor-question-types`), sau do `useQuizEditorDraftQuery(effectiveQuizId)` de hydrate tu BE. Publish/save cung dung `effectiveQuizId` thay vi chi `quizId` tu URL. BE seed sample quiz da bo sung `quiz_drafts` toi thieu cho `quiz-sample-editor-question-types` de default editor khong fetch roi fail `quiz draft not found`; da seed lai DB Aiven bang guarded runner. Verification FE: `bun run typecheck` pass va `bun run test src/features/lcms/quiz/quiz-editor/api/quiz-editor-query.test.ts` pass 2/2. Verification BE: `./mvnw "-Dtest=QuestionBankDemoSeedParityTest,QuestionBankSeedRunnerTest,LcmsWorkspaceReadServiceTest,LcmsWorkspaceGraphQlControllerTest" test` pass 18 tests, 1 guarded skip; `./mvnw "-Dtest=QuestionBankSeedRunnerTest" "-Derg.seed.question-bank.devdb=true" test` pass 1/1 va upsert draft DB that.

> **Trang thai xac minh cap nhat 2026-07-09 lan 46:** Bo sung theo yeu cau create/edit cua `/quiz-editor`: create mode khong con chi dua vao draft/local sample ma goi `useQuestionBankWorkspaceQuery` de fetch `LcmsQuestionBankWorkspace` theo scope dang chon tren ribbon. FE map scope UI sang taxonomy BE (`ic3-gs6-level-1/2/3` -> `ic3-l1/l2/l3`), dua `subjectId`, `levelId`, `topicId` neu map duoc, `status=ready`, `page=0`, `size=50` vao GraphQL input; query key question-bank co them normalized filter object de cache khong tron du lieu giua mon/level/chu de. Create mode replace danh sach cau hoi trong manager bang du lieu BE theo filter; edit mode co `quizId` van hydrate draft da luu va khong bi question-bank auto-replace. Neu BE da loc topic thi FE khong loc them bang rule text local de tranh an mat data. Verification FE: `bun run typecheck` pass; `bun run test src/features/lcms/quiz/question-bank/api/question-bank-query.test.ts src/features/lcms/quiz/question-bank/api/question-bank-api.test.ts src/features/lcms/quiz/quiz-editor/api/quiz-editor-query.test.ts` pass 3 files / 9 tests.

> **Trang thai xac minh cap nhat 2026-07-09 lan 47:** Hoan tat yeu cau API-backed taxonomy/filter cho quiz authoring. BE sua loi GraphQL `questionIds: [String!]!` bi null trong `questionBankWorkspace/quizWorkspace` bang cach sanitize `questionIds` null/blank ve list string sach, title quiz fallback an toan, va `questionCount` tinh tu `questionIds`; `QuestionBankOption` bo sung `subjectId`, `levelId` nullable de FE biet level/topic thuoc parent nao. `quizWorkspace` tra `categories` tu `TopicRepository` theo `levelId`; `questionBankWorkspace` loc quiz options them `topicId/categoryId` thay vi chi subject/level/status. FE `/questions` va `/quiz-bank` khong khoi tao taxonomy tu mock `ic3-gs6` nua: mount dau tien goi API khong filter de lay subjects/levels/topics, sau do moi refetch theo `subjectId`, `levelId`, `topicId`, `search`, `status=ready`, `page=0`, `size=50`; search debounce gui len BE, khong loc text client-side. FE `/quiz-editor` ribbon mon/level/chu de cung lay tu `questionBankWorkspace` response va create mode thay danh sach cau hoi theo filter BE; edit mode co `quizId` van hydrate draft. Verification: BE `./mvnw "-Dtest=LcmsWorkspaceReadServiceTest,LcmsWorkspaceGraphQlControllerTest,LcmsQuizWorkspaceGraphQlControllerTest" test` pass 17 tests; FE `bun run typecheck` pass; FE `bun run test src/features/lcms/quiz/question-bank/api/question-bank-query.test.ts src/features/lcms/quiz/question-bank/api/question-bank-api.test.ts src/features/lcms/create-quiz-from-bank.test.ts src/features/lcms/quiz/quiz-editor/api/quiz-editor-query.test.ts` pass 4 files / 13 tests.

> **Trang thai xac minh cap nhat 2026-07-09 lan 48:** Bo sung flow tao quiz/bai tap tu ngan hang cau hoi theo anh 3: khi chon nhieu cau va bam tao quiz, FE yeu cau nhap ten bai tap/quiz, sau do truyen title vao `createQuizFromBankQuestions` va goi BE create quiz + save draft nhu cu; metadata subjectId/levelId/categoryId lay tu cau hoi dau tien trong selection de quiz-bank loc lai duoc. Verification FE sau thay doi: `bun run typecheck` pass; `bun run test src/features/lcms/create-quiz-from-bank.test.ts src/features/lcms/quiz/question-bank/api/question-bank-query.test.ts src/features/lcms/quiz/question-bank/api/question-bank-api.test.ts src/features/lcms/quiz/quiz-editor/api/quiz-editor-query.test.ts` pass 4 files / 13 tests.

> **Trang thai xac minh cap nhat 2026-07-09 lan 49:** Hoan tat dot sua tiep theo cho API-backed LCMS quiz/question bank theo yeu cau anh 1/2/3. BE fix loi projection `findQuizSummaries` thieu `q.questionIds` trong constructor `QuizSummaryProjection`, tranh loi runtime/compile khi `quizWorkspace` tinh `questionCount`. BE seed `QuestionBankDemoBootstrapSeedService` chuyen sample active sang dung 3 mon `IC3 GS6`, `Python`, `MOS`; them levels/topics/questions/quizzes mau cho Python va MOS, dong thoi danh dau inactive cac subject demo cu do seed tao (`mathematics`, `english`, `science`) de taxonomy mau khong con lech yeu cau. BE `QuestionBankQuizOption`/DTO/schema bo sung cac field FE can hien thi: `questionCount`, `durationLabel`, `scopeLabel`, `sourceMode`, `ownerLabel`, `updatedAt`; FE query va mapper uu tien field BE thay vi phai lay template mock. BE them cache Caffeine TTL 30s cho `lcmsQuestionBankWorkspace` va `lcmsQuizWorkspace` theo tenant/filter/page/size, seed evict cac cache LCMS/quiz runtime de tranh stale sau khi upsert du lieu. FE mock/offline duoc dong bo thanh IC3/Python/MOS, quiz mock khong con hardcode IC3 ma suy subject/level tu selected questions; FE taxonomy builder loc categories theo level cua tung subject de khi chon Python/MOS khong lan topic IC3. Verification BE: `./mvnw "-Dtest=LcmsWorkspaceReadServiceTest,LcmsWorkspaceGraphQlControllerTest,LcmsQuizWorkspaceGraphQlControllerTest,QuestionBankDemoSeedParityTest,QuestionBankSeedRunnerTest,CacheConfigTest" test` pass 22 tests, 0 failures/errors, 1 guarded seed skip. Verification FE: `bun run typecheck` pass; `bun run test src/features/lcms/create-quiz-from-bank.test.ts src/features/lcms/quiz/question-bank/api/question-bank-query.test.ts src/features/lcms/quiz/question-bank/api/question-bank-api.test.ts src/features/lcms/quiz/quiz-editor/api/quiz-editor-query.test.ts` pass 4 files / 13 tests. Da chay seed that vao PostgreSQL cau hinh hien tai bang `./mvnw "-Dtest=QuestionBankSeedRunnerTest" "-Derg.seed.question-bank.devdb=true" test`, pass 1/1 va log ket noi `jdbc:postgresql://postgre-vuongquoctrann-9ac2.j.aivencloud.com:21590/defaultdb?sslmode=require`; can restart BE runtime dang chay de nap schema GraphQL/cache config moi.

> **Trang thai xac minh cap nhat 2026-07-09 lan 50:** Bo sung tiep phan con thieu cho authoring ngan hang cau hoi that. BE them REST command API `POST /api/v1/lcms/questions`, `PUT /api/v1/lcms/questions/{questionId}`, `DELETE /api/v1/lcms/questions/{questionId}`; delete la archive mem (`status=archived`, `archivedAt`) de khong pha quiz da tham chieu cau hoi. `QuestionBankAuthoringService` validate cac truong bat buoc `subjectId`, `levelId`, `topicId`, `stem`, normalize `type/difficulty/status`, gan tenant/user tu principal, va evict `lcmsQuestionBankWorkspace` + `lcmsQuizWorkspace` sau moi write. `QuestionRepository` co `findByIdAndTenantId` va read query mac dinh loai cau `archived` tru khi FE filter `status=archived`, tranh truong hop cau da xoa van quay lai trong lan fetch taxonomy/list. FE `question-bank-api.ts` them `createQuestionBankQuestion`, `updateQuestionBankQuestion`, `deleteQuestionBankQuestion`; `question-bank-query.ts` them mutation hooks va invalidate root `question-bank` query sau create/update/archive. Luu y: man hinh hien tai da co du data fetch/filter/select/create quiz; form UI tao/sua cau hoi rieng neu product muon hien modal inline thi nen dung cac hook moi nay, khong can tao endpoint khac.

> **Trang thai xac minh cap nhat 2026-07-09 lan 51:** Noi CRUD cau hoi vao chinh UI `/questions`, khong de hook API nam rieng. `QuestionBankWorkspace` co nut tao cau hoi trong header, modal tao/sua cau hoi lay taxonomy hien tai (`subjectId`, `levelId`, `topicId/categoryId`) va goi `POST/PUT /api/v1/lcms/questions`; tung row cau hoi co action `Edit` va `Delete`, delete goi `DELETE /api/v1/lcms/questions/{questionId}` de archive mem va bo cau do khoi selection sau mutation. Cac mutation dung TanStack invalidate root `question-bank`, nen list cau hoi va quiz-bank refetch lai tu BE/cache thay vi state local. UI co pending state cho save/delete va toast thanh cong/loi. Verification FE cap nhat: `bun run typecheck` pass; `bun run test src/features/lcms/quiz/question-bank/api/question-bank-query.test.ts src/features/lcms/quiz/question-bank/api/question-bank-api.test.ts src/features/lcms/create-quiz-from-bank.test.ts` pass 3 files / 12 tests.

> **Trang thai xac minh cap nhat 2026-07-09 lan 52:** Khoa them security cho API ghi LCMS quiz/question bank. Truoc do `POST/PUT/PATCH /api/v1/lcms/quizzes` da co permission guard nhung `DELETE /api/v1/lcms/quizzes/**` va `POST/PUT/DELETE /api/v1/lcms/questions/**` co the roi xuong rule authenticated chung; da them matcher bat buoc `lms.quiz.create` cho cac command nay de khong chi can login la sua/xoa duoc ngan hang cau hoi/bai tap. Them coverage trong `AccessControlSecurityTest` cho question authoring va delete question/quiz; test integration nay compile nhung bi skip o may hien tai vi khong co Docker/Testcontainers DB. Verification BE khong phu thuoc Docker: `./mvnw "-Dtest=QuestionBankDemoSeedParityTest,LcmsWorkspaceReadServiceTest,LcmsWorkspaceGraphQlControllerTest,LcmsQuizWorkspaceGraphQlControllerTest,CacheConfigTest" test` pass 21/21; `./mvnw "-Dtest=AccessControlSecurityTest,LcmsQuestionBankAuthoringControllerTest,QuestionBankAuthoringServiceTest,LcmsQuizAuthoringControllerTest" test` build success, non-Docker authoring tests pass va `AccessControlSecurityTest` skip 19/19 do Docker unavailable. Verification FE sau security change: `bun run typecheck` pass. Seed parity hien BE co du `qb-python-001..003`, `qb-mos-001..003` va IC3 sample IDs can cho FE mock, dong thoi seed BE la superset voi cac sample editor `qb-sample-q01..q15`.

> **Trang thai xac minh cap nhat 2026-07-09 lan 53:** Hoan thien field parity taxonomy giua BE va FE. Truoc do FE `QuestionBankSubject/Level` van tu suy `description`, `companyScopeLabel`, `categoryIds` tu label/mock; BE `QuestionBankOption` moi chi co `id/label/subjectId/levelId`. Da mo rong `QuestionBankOptionResponse`, GraphQL `QuestionBankOption`, repository projection va FE query/DTO de tra them `description`, `scopeLabel`, `categoryIds`. `questionBankWorkspace` va `quizWorkspace` deu enrich `categoryIds` cho level tu danh sach topic DB, giup man `/questions`, `/quiz-bank`, `/quiz-editor` dung taxonomy DB that thay vi field suy tu mock. FE mapper uu tien `description/categoryIds/scopeLabel` tu BE. Verification BE: `./mvnw "-Dtest=LcmsWorkspaceReadServiceTest,LcmsWorkspaceGraphQlControllerTest,LcmsQuizWorkspaceGraphQlControllerTest,QuestionBankDemoSeedParityTest,CacheConfigTest" test` pass 21/21. Verification FE: `bun run typecheck` pass va `bun run test src/features/lcms/quiz/question-bank/api/question-bank-api.test.ts src/features/lcms/quiz/question-bank/api/question-bank-query.test.ts src/features/lcms/quiz/quiz-editor/api/quiz-editor-query.test.ts` pass 3 files / 10 tests.

> **Trang thai xac minh cap nhat 2026-07-09 lan 54:** Siết them regression tests cho taxonomy field parity. FE `question-bank-api.test.ts` co case xac nhan mapper dung `description`, `scopeLabel`, `categoryIds` tu BE thay vi tu mock/fallback. BE `GraphQlShellContractTest` kiem schema `QuestionBankOption` co `description`, `scopeLabel`, `categoryIds`; `LcmsWorkspaceReadServiceTest` kiem level option tra `categoryIds` tu topic DB. Verification moi: BE `./mvnw "-Dtest=GraphQlShellContractTest,LcmsWorkspaceReadServiceTest,LcmsWorkspaceGraphQlControllerTest,LcmsQuizWorkspaceGraphQlControllerTest" test` pass 20/20; FE `bun run typecheck` pass; FE `bun run test src/features/lcms/quiz/question-bank/api/question-bank-api.test.ts src/features/lcms/quiz/question-bank/api/question-bank-query.test.ts` pass 2 files / 9 tests.

> **Trang thai xac minh cap nhat 2026-07-09 lan 55:** Sua tiep man `/quiz-editor` cho dung yeu cau create/edit/update phai lay `Mon hoc`, `Level`, `Chu de` tu API. Truoc do editor van giu `courseScopeOptions`/`levelScopeOptions` hardcode va khi doi mon hoc thi lay level dau tien tu list tinh; dieu nay lam UI trong editor trong nhu khong call API va co the chon sai level khi tao/sua bai. Da doi `QuizEditorWorkspace` de khi API base bat thi taxonomy chi den tu `questionBankWorkspace` theo params hien tai (`subjectId`, `levelId`, `topicId`, `status=ready`); fallback IC3/local chi con duoc dung khi khong co API base. Khi doi mon hoc, editor lay level dau tien cua subject API vua tra ve, reset topic ve all, sau do query lai danh sach cau hoi theo params moi. Verification FE: `bun run typecheck` pass va `bun run test src/features/lcms/quiz/question-bank/api/question-bank-api.test.ts src/features/lcms/quiz/question-bank/api/question-bank-query.test.ts src/features/lcms/quiz/quiz-editor/api/quiz-editor-query.test.ts` pass 3 files / 11 tests. Luu y van can restart BE runtime de schema/controller/cache/seed moi co hieu luc tren man hinh dang mo; neu server cu chua restart thi GraphQL co the van bao `INTERNAL_ERROR` hoac UI nhin nhu rong du DB da seed.
> **Trang thai xac minh cap nhat 2026-07-09 lan 56:** Siet tiep `/quiz-editor` de tranh request dau tien bi anh huong boi state local. Khi `hasApiBase()` bat, `selectedCourseId` khoi tao rong va `selectedLevelId` khoi tao `null`; editor cho `questionBankWorkspace` tra taxonomy DB roi moi tu dong chon subject/level dau tien tu API. Khi khong co API base moi dung fallback IC3 local. BE audit xac nhan `questionBankWorkspace` map `topicId` vao repository quiz `categoryId`, nen `/quiz-bank` loc BE theo `subjectId`, `levelId`, `topicId/categoryId`; FE tiep tuc loc hien thi theo `kind` va scope. Verification FE moi: `bun run typecheck` pass; `bun run test src/features/lcms/quiz/question-bank/api/question-bank-api.test.ts src/features/lcms/quiz/question-bank/api/question-bank-query.test.ts src/features/lcms/quiz/quiz-editor/api/quiz-editor-query.test.ts` pass 3 files / 11 tests.

> **Trang thai xac minh cap nhat 2026-07-09 lan 57:** Bo sung search/kind filter dung API cho man `/quiz-bank`. `QuestionBankWorkspaceInput` them `quizKind`; `questionBankWorkspace` dua `quizKind` vao cache key va truyen `search` + `quizKind` xuong `QuizRepository`, nen danh sach bai tap loc DB theo `subjectId`, `levelId`, `topicId/categoryId`, `search` title va `kind` (`train`/`test`) thay vi chi loc client. FE `QuestionBankWorkspace` truyen `quizKind` khi view quiz-bank, them search box trong `QuizBankPanel`, normalize `search`/`quizKind` truoc GraphQL request va dua `quizKind` vao TanStack query key. Verification: BE `./mvnw "-Dtest=GraphQlShellContractTest,LcmsWorkspaceReadServiceTest,LcmsWorkspaceGraphQlControllerTest,LcmsQuizWorkspaceGraphQlControllerTest" test` pass 20/20; FE `bun run typecheck` pass; FE `bun run test src/features/lcms/quiz/question-bank/api/question-bank-api.test.ts src/features/lcms/quiz/question-bank/api/question-bank-query.test.ts src/features/lcms/quiz/quiz-editor/api/quiz-editor-query.test.ts` pass 3 files / 11 tests.

> **Trang thai xac minh cap nhat 2026-07-09 lan 58:** Sua loi phu sau khi them search `/quiz-bank`: FE truoc do loc topic cho quiz bang cach doi `questionIds` sang danh sach `questions` hien tai. Khi search theo ten bai tap, BE co the tra quiz dung nhung `questions` rong vi search khong match cau hoi, lam FE an quiz sai. Da them `categoryId` vao `QuizBankItem`, mapper `mapQuiz` giu `categoryId` tu BE, mock quiz cung set category tu cau hoi dau tien, va `visibleQuizzes` uu tien `quiz.categoryId === topicId` truoc khi fallback qua `questionIds`. Verification FE: `bun run typecheck` pass; targeted question-bank/editor query tests pass 3 files / 11 tests.

> **Trang thai xac minh cap nhat 2026-07-09 lan 59:** Them migration `V32__quiz_bank_hot_path_indexes.sql` de giam tai DB cho `/questions`, `/quiz-bank`, `/quiz-editor` khi nhieu user load dong thoi. Migration bat `pg_trgm`, them trigram index cho `lower(title)` tren `lms_quizzes` va `lower(stem)` tren `lms_questions` de khop voi query `lower(...) like '%search%'`, them partial composite index cho quiz-bank active/non-deleted theo `tenant_id`, taxonomy, `kind/status`, `updated_at`, va partial composite index cho question-bank active/non-archived theo `tenant_id`, taxonomy, `status`, `updated_at`. Muc tieu: search/filter DB dung index thay vi scan khi 5000 hoc sinh/giao vien cung doc. Verification BE: `./mvnw "-Dtest=GraphQlShellContractTest,LcmsWorkspaceReadServiceTest,LcmsWorkspaceGraphQlControllerTest,LcmsQuizWorkspaceGraphQlControllerTest" test` pass 20/20.

> **Trang thai xac minh cap nhat 2026-07-09 lan 60:** Giam tai FE/API cho question-bank bang phan trang that. Truoc do `loadQuestionBankWorkspace` tu dong fetch tat ca page cau hoi neu BE bao `hasNext`, de gay burst request va tai DB/cache lon khi ngan hang cau hoi phinh ra. Da doi FE chi fetch page hien tai, expose `questionPage` trong `QuestionBankApiData`, them nut Previous/Next va tong so cau hoi trong `QuestionLibrary`, query key/page state theo filter hien tai, reset page ve 0 khi doi subject/level/topic/search/view, va luu `questionCache` trong workspace de giu selection khi chuyen page. Them regression test dam bao GraphQL chi duoc goi 1 lan cho page hien tai ke ca khi BE bao con page tiep. Verification FE: `bun run typecheck` pass; `bun run test src/features/lcms/quiz/question-bank/api/question-bank-api.test.ts src/features/lcms/quiz/question-bank/api/question-bank-query.test.ts src/features/lcms/quiz/quiz-editor/api/quiz-editor-query.test.ts` pass 3 files / 12 tests.

> **Trang thai xac minh cap nhat 2026-07-09 lan 61:** Hoan thien UX tao bai tap tu ngan hang cau hoi. Truoc do click `Create quiz` tu selected questions tao ngay voi title tu sinh va mac dinh `train`, chua dung yeu cau product la select nhieu cau hoi roi dat ten bai tap/chon che do. `QuestionBankWorkspace` nay mo `QuizCreateDialog`, bat buoc nhap ten bai tap, chon `train` hoac `test`, roi goi flow API co san `onCreateQuiz(selectedQuestions, { title, kind })`; LCMS shell tiep tuc tao quiz qua REST va save draft tu selected question IDs. Selection van duoc giu qua pagination bang `questionCache`. Verification FE: `bun run typecheck` pass; `bun run test src/features/lcms/create-quiz-from-bank.test.ts src/features/lcms/quiz/question-bank/api/question-bank-api.test.ts src/features/lcms/quiz/question-bank/api/question-bank-query.test.ts` pass 3 files / 14 tests.

> **Trang thai xac minh cap nhat 2026-07-09 lan 62:** Siet UX/data-consistency cho selection khi tao bai tap tu question bank. Selection duoc giu khi chuyen page trong cung filter, nhung se duoc clear khi doi subject/level/topic hoac search de tranh tao nham bai tap tu cau hoi khong con nam trong bo loc hien tai. `QuestionBankWorkspace` them `pickTopic` va `changeSearch` wrappers de reset `selectedIds` dung luc, trong khi `questionCache` van giu du lieu cau hoi da tai de selection qua pagination hoat dong. Verification FE: `bun run typecheck` pass; `bun run test src/features/lcms/create-quiz-from-bank.test.ts src/features/lcms/quiz/question-bank/api/question-bank-api.test.ts src/features/lcms/quiz/question-bank/api/question-bank-query.test.ts` pass 3 files / 14 tests.

> **Trang thai xac minh cap nhat 2026-07-09 lan 63:** Chay lai dev PostgreSQL proof cho question-bank seed va migration moi. Lenh `./mvnw "-Dtest=QuestionBankSeedRunnerTest" "-Derg.seed.question-bank.devdb=true" test` da connect Aiven PostgreSQL `jdbc:postgresql://postgre-vuongquoctrann-9ac2.j.aivencloud.com:21590/defaultdb?sslmode=require`, Flyway apply thanh cong `V32__quiz_bank_hot_path_indexes.sql` va bao schema public len version v32 trong 1.320s. Spring GraphQL schema inspection khong co unmapped fields/arguments/nullness errors, GraphQL endpoint `/api/v1/graphql` load duoc. Test seed pass 1/1, xac nhan DB dev da nhan migration/index hot-path va seed question-bank IC3/Python/MOS tiep tuc hop le tren PostgreSQL that.

> **Trang thai xac minh cap nhat 2026-07-09 lan 64:** Fix loi runtime con lai lam FE thay `INTERNAL_ERROR` o `lcms.quizWorkspace` tren PostgreSQL/Aiven. Proof DB mo rong da bat duoc JPQL nullable filter dang `lower(:search/:status/:kind)` bi PostgreSQL suy luan tham so null thanh `bytea`, dan toi loi `function lower(bytea) does not exist` khi man `/quiz-bank` hoac `/quiz-editor` load khong co search. Da tach `QuizRepository.findQuizSummaries` thanh query co search va `findQuizSummariesWithoutSearch`, service `LcmsQuizWorkspaceReadService` chon dung path theo filter, va query chi lower cot DB, khong lower nullable parameter. Verification BE local: `./mvnw "-Dtest=GraphQlShellContractTest,LcmsWorkspaceReadServiceTest,LcmsWorkspaceGraphQlControllerTest,LcmsQuizWorkspaceGraphQlControllerTest" test` pass 20/20. Verification DB that: `./mvnw "-Dtest=QuestionBankSeedRunnerTest" "-Derg.seed.question-bank.devdb=true" test` pass 1/1 tren Aiven PostgreSQL 17.10, schema public o version 32, xac nhan seed IC3/Python/MOS + read model `questionBankWorkspace` va `quizWorkspace` doc duoc du lieu that khong con `INTERNAL_ERROR`. Verification FE: `bun run typecheck` pass; targeted question-bank/editor query tests pass 3 files / 12 tests.

> **Trang thai xac minh cap nhat 2026-07-09 lan 65:** Gia co contract de tranh FE co field nhung BE schema khong expose. `GraphQlShellContractTest` nay assert day du cac field FE dang request cho `/questions`, `/quiz-bank`, `/quiz-editor`: question scope/taxonomy labels, `choices`, `usageCount`, `recommendedCluster`, quiz `kind/categoryId/questionIds/questionCount/durationLabel/sourceMode/ownerLabel`, `QuizWorkspace.quizzes/categories`, va `QuizSummary.currentVersionLabel`. Chay lai `./mvnw "-Dtest=GraphQlShellContractTest,LcmsWorkspaceReadServiceTest,LcmsWorkspaceGraphQlControllerTest,LcmsQuizWorkspaceGraphQlControllerTest" test` pass 20/20, dong thoi truoc do suite write/read `LcmsQuestionBankAuthoringControllerTest,QuestionBankAuthoringServiceTest,LcmsQuizAuthoringControllerTest,QuizDraftServiceTest,QuizPublishServiceTest,...` pass 34/34, xac nhan API create/edit/delete question, create/save/delete quiz va cache eviction van hop le.

> **Trang thai xac minh cap nhat 2026-07-09 lan 66:** Toi uu duong doc `/quiz-bank` de giam tai khi nhieu user mo ngan hang bai tap. Truoc do FE dung `questionBankWorkspace` cho ca `/questions` va `/quiz-bank`, nen ngay ca man chi can quizzes/taxonomy van query page cau hoi + usage count. Da them GraphQL input `includeQuestions: Boolean`; mac dinh null/true giu hanh vi cu cho `/questions`, con FE `/quiz-bank` truyen `includeQuestions:false`. BE `LcmsWorkspaceReadService` dua flag nay vao cache key va khi false tra `questions` page rong dung shape nhung khong goi `QuestionRepository`/usage count. FE query key cung them `includeQuestions` de khong dung nham cache giua hai man. Verification BE: `./mvnw "-Dtest=GraphQlShellContractTest,LcmsWorkspaceReadServiceTest,LcmsWorkspaceGraphQlControllerTest,LcmsQuizWorkspaceGraphQlControllerTest" test` pass 21/21, trong do co test moi xac nhan `includeQuestions=false` khong cham question repository. Verification FE: `bun run typecheck` pass; `bun run test src/features/lcms/quiz/question-bank/api/question-bank-api.test.ts src/features/lcms/quiz/question-bank/api/question-bank-query.test.ts src/features/lcms/create-quiz-from-bank.test.ts` pass 3 files / 14 tests. Verification DB that: `./mvnw "-Dtest=QuestionBankSeedRunnerTest" "-Derg.seed.question-bank.devdb=true" test` pass 1/1 tren Aiven PostgreSQL 17.10, schema public version 32, seed/read model IC3/Python/MOS van doc duoc du lieu that.

> **Trang thai xac minh cap nhat 2026-07-09 lan 67:** Sau khi `/quiz-bank` khong con fetch page cau hoi, sua UI count trong dropdown chu de de khong hien `0 cau` sai ngu canh. `QuestionBankWorkspace` nay khi view la `quiz-bank` se tinh mo ta topic bang so bai tap/quiz khop subject, level, topic, kind, search va scope; view `/questions` van dung so cau hoi nhu cu. Them copy `quizCount` cho VI/EN. Verification FE: `bun run typecheck` pass; targeted question-bank/create-quiz tests pass 3 files / 14 tests.

> **Trang thai xac minh cap nhat 2026-07-09 lan 68:** Khoa ro logic train/test va chon noi tron cau hoi/dap an. BE tiep tuc tra canonical quiz package de cache tot, khong sinh 5000 bien the package rieng cho tung hoc sinh. FE runtime `prepareQuizPackageForAttempt` dung package hash + quiz id + version + attempt seed de tron deterministic tren client cho `testing`, ep `shuffleQuestions/shuffleChoices=true`; `training` ep `shuffleQuestions/shuffleChoices=false` va giu canonical order, ke ca neu authoring settings luu sai. Da them regression test `keeps training attempts in canonical order even when authoring settings request shuffle`, trong khi test cu da xac nhan `testing` shuffle per-attempt va khong doi canonical package hash. Verification FE: `bun run test src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-api.test.ts src/features/lcms/quiz/quiz-runtime/api/quiz-runtime-query.test.ts` pass 2 files / 23 tests; `bun run typecheck` pass.

> **Trang thai xac minh cap nhat 2026-07-09 lan 69:** Chay live smoke instance rieng tu code hien tai tren port `18080` voi trusted header auth bat rieng cho smoke, connect Aiven PostgreSQL `17.10`, Flyway schema public version `32`. Goi GraphQL HTTP that vao `/api/v1/graphql`: `questionBankWorkspace(includeQuestions:false, subjectId=python, levelId=python-foundation, quizKind=train)` tra `questions.totalItems=0` va quiz `quiz-python-foundation-train` voi `questionIds=[qb-python-001,qb-python-002]`, log query count 4 queries; `questionBankWorkspace(subjectId=python, levelId=python-foundation, status=ready)` tra cau hoi `qb-python-001/qb-python-002`, choices/answer/usageCount va subjects co `ic3-gs6`, `python`, `mos`, log query count 6 queries; `quizWorkspace(subjectId=ic3-gs6, levelId=ic3-l1, kind=train)` tra `quiz-sample-editor-question-types` 15 cau va `quiz-ic3-l1-foundation-train`, log query count 4 queries. Smoke process da duoc tat sau khi kiem tra, khong de lai port 18080 dang listen.

> **Trang thai xac minh cap nhat 2026-07-09 lan 70:** Restart BE chinh port `8080` tu code hien tai de thay process cu start luc 15:50. Process moi listen tren `8080` voi PID `37408`; FE dev server `D:\ERG\erg-system` dang co Vite port `3001` va proxy `/api` ve `http://localhost:8080`, nen man LCMS se dung backend moi. Goi lai GraphQL HTTP that tren dung port `8080`: `/quiz-bank` path `questionBankWorkspace(includeQuestions:false, subjectId=python, levelId=python-foundation, quizKind=train)` tra `questions.totalItems=0`, subjects co `ic3-gs6/python/mos`, quiz `quiz-python-foundation-train` voi `questionIds=[qb-python-001,qb-python-002]`; `/questions` path tra `qb-python-001/qb-python-002` voi choices/answer/usageCount; `/quiz-editor` path `quizWorkspace(subjectId=ic3-gs6, levelId=ic3-l1, kind=train)` tra `quiz-sample-editor-question-types` 15 cau va `quiz-ic3-l1-foundation-train`. BE log tren 8080 ghi GraphQL POST 200 cho cac request live nay.

> **Trang thai xac minh cap nhat 2026-07-10 lan 71:** Refactor tiep domain relationship/settings cho question vs quiz. BE them `lms_questions.settings` lam setting goc cua cau hoi, `lms_quiz_questions.settings` lam setting cua cau hoi trong bo/quiz va la lop override. `QuestionBankAuthoringService` nhan/persist `settings` khi create/update question; `QuizDraftService` sync `questionSetSettings`/`settingsOverride`/`overrideSettings` tu slide vao bang join; `QuizPublishService` doc lai setting cau hoi + setting bo cau hoi tu DB truoc khi compile. `QuizCompileService` merge runtime body theo rule `Question.settings < slide.questionSettings < slide.settings < lms_quiz_questions.settings < slide question-set override`, nen setting cua bo cau hoi ghi de setting cua cau hoi dung nhu yeu cau. Verification BE: `./mvnw -q "-Dtest=QuestionBankAuthoringServiceTest,LcmsQuestionBankAuthoringControllerTest,QuizDraftServiceTest,QuizCompileServiceTest,QuizPublishServiceTest" test` pass; `./mvnw -q test-compile` pass; `./mvnw -q "-Dtest=QuestionBankAuthoringServiceTest,QuizDraftServiceTest,LcmsWorkspaceReadServiceTest,GraphQlShellContractTest,QuestionBankDemoSeedParityTest,QuizCompileServiceTest,QuizPublishServiceTest,LcmsQuestionBankAuthoringControllerTest" test` pass.

> **Trang thai xac minh cap nhat 2026-07-10 lan 72:** Rà lại relationship DB sau khi tach `Question`/`Quiz`/`QuestionType`/`QuestionSet`. Them migration `V34__quiz_runtime_relationship_integrity_hardening.sql` de khoa cac quan he con ho: `lms_quizzes.current_version_id/latest_draft_id`, `lms_questions.category_id`, `quiz_drafts.quiz_id/base_version_id/updated_by`, `quiz_versions.published_by`, `lms_attempts.quiz_version_id/class_id/school_id`, `attempt_events`, `attempt_answers`, `student_progress`, `assignment_resources`, `assignment_recipients`, `student_groups`, `student_group_members`, va cac bang legacy `question_bank_*`/`quiz_bank_items`. Migration co cleanup orphan truoc khi add + validate FK de DB cu migrate duoc. Sua `QuizQuestionLinkService` dung native delete truoc khi insert lai link de seed/save draft idempotent voi du lieu `lms_quiz_questions` da co tu migration. Da chay `QuestionBankSeedRunnerTest` tren Aiven PostgreSQL 17.10: Flyway apply V33+V34 thanh cong, schema public len version 34, seed question-bank/quiz sample pass. Verification BE: `./mvnw -q test-compile` pass va `./mvnw -q "-Dtest=DatabaseRelationshipMigrationContractTest,QuizAuthoringServiceTest,QuizDraftServiceTest,QuizPublishServiceTest,QuizCompileServiceTest,QuestionBankAuthoringServiceTest,QuestionBankDemoSeedParityTest" test` pass.
