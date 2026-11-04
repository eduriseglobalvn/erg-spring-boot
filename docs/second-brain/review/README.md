# Đánh giá toàn diện erg-spring và kế hoạch khắc phục

> Ngày: 2026-11-03 · Nhánh `Backup` (worktree có thay đổi chưa commit) · Phương pháp: đọc code, không chạy app/test/migration.
> Nguồn chi tiết: [10 bảo mật/platform](10-verify-security-platform.md) · [11 học tập/quiz/lịch](11-verify-learning-calendar.md) · [12 CRM/thông báo/audit](12-verify-crm-notify-audit.md) · [13 DB/migration/secret](13-verify-database-secrets.md) · [14 cấu trúc/chất lượng](14-review-structure-quality.md).
> Ký hiệu nguồn trong bảng: `A*`, `N*`(10) → file 10; `B*`, `(a)…(e)` → file 11; `C*` → file 12; `D*` → file 13; `SQ-*` → file 14. Mục có dấu ✔ là đã được người tổng hợp tự đối chiếu lại với code.

---

## 1. Kết luận ngắn

**Chưa sẵn sàng cho production ở mức "nhiều trường, nhiều người dùng thật".** Phần lõi được làm cẩn thận: token opaque lưu hash, tenant suy ra từ session phía server, outbox có `SKIP LOCKED` và lease, Argon2, nhiều chỗ fail-closed, contract có checksum. Tuy vậy còn:

- **3 lỗi Critical**:
  - Tài khoản super admin dùng hash mật khẩu cố định nằm trong migration. Dump DB thật (chứa hash, email, session) đã được push lên remote.
  - Luồng làm bài của học sinh bị chặn với dữ liệu tạo qua API, đồng thời có nhánh cho phép bất kỳ ai làm bài.
  - Học sinh nộp được bài thay người khác.
- **Khoảng 35 lỗi High**: leo thang quyền lên `*`, cấu hình production thiếu gần hết khối `erg.*`, khóa mã hóa outbox mặc định công khai, phát hiện replay refresh token bị rollback, IDOR ở CRM, lộ đáp án, chấm điểm sai…
- **Logic không lồng sâu theo nghĩa if/else** (tối đa khoảng 5 tầng). Vấn đề nằm ở **lồng tầng gọi và trùng quy tắc**: một lời gọi đi qua 3–5 lớp chỉ chuyển tiếp, và cùng một quy tắc được viết lại 3–16 lần với ý nghĩa đã lệch nhau.

### Bảng điểm (1–10)

| Tiêu chí | Điểm | Lý do chính |
|---|---|---|
| Độ hoàn thiện tính năng | **5** | Auth, tenant, CRM workspace, calendar, outbox đã thật. Luồng giao bài → làm bài → chấm → điểm chưa chạy xuyên suốt. Thông báo tự động chưa hoạt động. 13 controller chỉ là prototype in-memory. Excel import ném `NotImplementedException`. |
| Phân lớp rõ ràng | **4** | Hai kiến trúc chạy song song (`service/` 118 file và `*/internal` 78 file). 27 Operations facade có khoảng 218 dòng chỉ chuyển tiếp. Còn bean `@Deprecated` song song với bản mới. |
| Trùng lặp / lệch quy tắc | **4** | Quyền hiệu lực được tính ở 3 nơi với 3 nghĩa khác nhau. Tập trạng thái attempt bị chép 16 lần. Kiểm tra scope lớp viết lại ít nhất 4 lần. |
| Độ lồng / dễ đọc | **5** | Độ lồng điều kiện chấp nhận được, nhưng nhiều method nhồi 10+ câu lệnh vào một dòng 300–1.000 ký tự, đúng ở những chỗ có bug. |
| Bảo mật | **4** | Thiết kế token và tenant tốt, nhưng có 1 Critical về secret và nhiều lỗ High về phân quyền, cấu hình và brute-force. |
| An toàn dữ liệu / migration | **5** | Có pattern NOT VALID → VALIDATE và test upgrade path. Nhưng seed admin và dữ liệu demo nằm trong migration, migration phá hủy không có lưới an toàn, 3 bảng mới thiếu RLS, V88 sẽ fail trên dữ liệu hosted. |
| Xử lý lỗi / transaction | **6** | Đa số catch rethrow có ngữ cảnh. Một số chỗ "best effort" lại làm hỏng transaction. Còn gọi R2 và connector bên trong transaction. |
| Hiệu năng | **5** | Mỗi lần nộp bài xóa toàn bộ 6 cache của mọi tenant. Có N+1 ở learning outcomes. Nhiều danh sách bị cắt ngầm. |
| Test cho code rủi ro | **5** | Nhiều unit test dùng mock nên không bắt được các lỗi chính (C7, A6, B2). Thiếu test âm cho IDOR. Gate RLS không chặn được merge. |

---

## 2. Lỗi Critical (sửa trước mọi việc khác)

| # | Lỗi | Bằng chứng | Hậu quả |
|---|---|---|---|
| CR-1 ✔ | Super admin `usr_super_admin` dùng hash cố định, `ACTIVE`, scope `*`, không bị buộc đổi mật khẩu, và được tạo ở mọi môi trường | `V7__enterprise_admin_account_seed.sql:22-24`, `V51:3` (mặc định `must_change_password=false`) | Ai đọc repo cũng dò được hash offline. Môi trường nào chưa đổi mật khẩu thì có thể bị chiếm toàn quyền. |
| CR-2 ✔ | Dump DB thật đang được git track và đã push lên `origin/Backup` | `backups/*.sql`, `backups/*.dump` (có trên `origin/Backup`; `origin/main` chỉ có manifest) | Lộ hash admin, khoảng 41 email nội bộ, 29 session kèm hash token, IP/UA, 770 hồ sơ học sinh (có thể là PII theo NĐ 13/2023), cùng host DB hosted. Token trong `.codex-runtime/login*.json` cũng còn trong lịch sử git. |
| CR-3 ✔ | Học sinh không bắt đầu được bài tập tạo qua API, đồng thời bài giao cả lớp lại cho ai cũng làm được | `LmsAttemptUseCase.java:97,351`; không có `setAuthUserId` nào trong `src/main/java`; nhánh `.orElse(true)` | Luồng làm bài bị chặn hoàn toàn với dữ liệu thật. Bài giao cả lớp thì mọi user có quyền submit trong tenant đều làm được. |
| CR-4 ✔ | Nộp hoặc sửa attempt không kiểm tra người sở hữu, chỉ kiểm tra lớp | `LmsAttemptUseCase.java:224,349-350` | Học sinh B nộp bài trống thay học sinh A, nên A bị 0 điểm và mất lượt làm. Assignment không gắn lớp thì không có kiểm tra nào. |

---

## 3. Lỗi High theo nhóm (tóm tắt, chi tiết xem file nguồn)

**Bảo mật & cấu hình**
- ✔ A3: quyền `*` được coi là toàn quyền vô điều kiện. `assignPermissions` không có `assertCanGrant`, nên admin có thể tự cấp `*` cho mình (`ErgPolicyService.java:92-94`, `UserRoleAssignmentUseCase.java:56-62`).
- ✔ A1: dòng 119–464 của `application.yml` là document chỉ dành cho `staging`. Production thiếu toàn bộ `erg.*` (CORS, trusted proxy, key mã hóa push token, giới hạn GraphQL), nên các cơ chế bảo mật này mất tác dụng mà không có cảnh báo.
- ✔ A10: key mã hóa outbox có giá trị mặc định trong source (`OutboxPayloadCipher.java:22-27`), và production không có guard.
- ✔ A6: phát hiện replay refresh token bị rollback, nên token bị đánh cắp vẫn dùng được (`AuthRefreshUseCase.java:61-80`).
- N1 (file 10): `forward-headers-strategy: framework` lấy IP bên trái của `X-Forwarded-For`, nên kẻ tấn công giả được IP và vượt giới hạn đăng nhập theo IP.
- A12: không có lockout (các khóa `max-failed-login` không ai đọc), và lộ trạng thái tài khoản trước khi kiểm mật khẩu.
- ✔ Thay đổi chưa commit: TLS mail bị đảo mặc định; kiểm tra admin role dùng `contains("principal")` nên quá rộng.

**Phân quyền dữ liệu (IDOR)**
- ✔ C1: CRM `/seo/{seoId}/schools|performance` cho xem deal và contact của SEO khác, có thể cả tenant khác.
- (d2): giáo viên sửa hoặc xóa cứng được assignment của lớp không dạy. B8: tải báo cáo chỉ kiểm tra tenant.
- (a), SQ-06: package quiz trả `answerKey` (mặc định `client-first`) và không kiểm tra học sinh có được giao bài hay không.
- N6 (file 12): người tạo handover CRM tự duyệt được, và `isReady` cho phép thiếu một mục checklist.

**Đúng nghiệp vụ**
- ✔ SQ-04: chấm dự phòng tính điểm bằng số key trả lời, nên gửi 50 key rác cho đề 10 câu là đạt 100%.
- B1: assignment nhiều quiz chỉ bắt đầu được quiz đầu tiên. B3: không có chấm tay, nên bài essay kẹt mãi ở `needs_review`. SQ-03: `needs_review` vẫn bị tính vào giới hạn số lượt, trong khi dashboard báo là "chưa nộp".
- (b1): không có thời gian ân hạn. Nộp trễ 1 giây là mất toàn bộ bài, và attempt kẹt `in_progress`.
- B6: publish batch làm sống lại event đã hủy. B7: `DELETE_CONFLICTING` hủy lịch cũ nhưng không tạo lịch mới, và vẫn commit.
- ✔ C7: thông báo "lịch đã xuất bản" không bao giờ gửi được, và lặp lại vô hạn mỗi 30 giây (N7).
- SQ-05: projection lỗi làm transaction rollback-only, nên cả lượt nộp bài trả 500.

**Vận hành / DB**
- N2 (file 13): V88 tạo unique index mà không dedupe, trong khi snapshot hosted có 120 `student_code` trùng, nên **deploy sẽ fail**.
- N1 (file 13): `curriculum_teaching_plan_settings`, `curriculum_programs`, `curriculum_program_rows` không có RLS.
- D4: dev và môi trường hosted theo `.env` kết nối bằng owner hoặc superuser, nên RLS bị bỏ qua.
- D2: migration xóa dữ liệu (V11, V34, V40, V42) không có quarantine hay đếm dòng.
- ✔ N2 (file 12): SMTP không có timeout, và scheduler chỉ có 1 thread, nên một mail treo làm đứng mọi job nền.
- ✔ N1 (file 12): rate limit GraphQL theo `operationName` do client đặt, nên bị vượt dễ dàng, và nhãn metric tăng không giới hạn.

---

## 4. "Code có lồng logic không?"

| Dạng | Kết luận | Bằng chứng |
|---|---|---|
| Lồng if/for sâu | Không nghiêm trọng (tối đa khoảng 5 tầng) | `GradingEngine.java:251`, `StudentDashboardReadService.java:328`, `CalendarConflictService.java:148`, `AuthLoginUseCase.java:345` |
| Lồng tầng gọi (pass-through) | **Có, nặng** | Nộp bài: `LmsController → LmsOperations → LmsRuntimePort → LmsRuntimeUseCase → LmsAttemptUseCase → LmsAttemptService → Repository`, trong đó 3 lớp chỉ chuyển tiếp, có thêm 1 bean `@Deprecated` song song. Upload media: 3 hop chuyển tiếp. |
| Hai kiến trúc song song | **Có** | `service/` (legacy) và `*/internal` (Modulith). Code `internal` import ngược vào `service.*`/`repository.*` 95 lần. Modulith verify chỉ quét package có annotation (SQ-17). |
| Một quy tắc viết nhiều nơi, đã lệch nhau | **Có, nguy hiểm nhất** | Quyền hiệu lực có 3 nơi (Login, ErgPrincipal, OIDC), trong đó `revoke` lúc là deny, lúc là grant. Wildcard `*` có 4 định nghĩa. Tập trạng thái attempt chép 16 lần. Scope đơn vị giáo dục ở `ErgPrincipalService:323` khác `SessionService:209`. Hai định danh học sinh (auth userId và `Student.id`) được xử lý tùy từng chỗ. |
| Class ôm nhiều việc | Có, mức trung bình | `LearningOutcomesService` (846 dòng, 6 việc), `QuizCompileService`, `LearningContentService`, `AuthLoginUseCase`, `LmsAttemptUseCase` (dày nhất: chấm điểm, projection, cache, metrics, 3 cơ chế idempotency). |
| Constructor "tương thích" chỉ để giữ test cũ | Có | `LmsAttemptUseCase` có 4 constructor; nhiều nhánh `legacy != null`; `SessionService` có constructor truyền `null`. Hành vi production khác hành vi trong test. |

---

## 5. Kế hoạch khắc phục chi tiết

Effort: S < 0,5 ngày · M 0,5–2 ngày · L > 2 ngày (1 dev backend). Mỗi mục kết thúc bằng một test hoặc bằng chứng nghiệm thu. Migration chỉ được thêm mới (append-only).

### Giai đoạn 0 — Khẩn cấp (ngày 1–3): đóng lỗ có thể khai thác ngay

| ID | Việc cần làm | Nguồn | File chính | Effort | Nghiệm thu |
|---|---|---|---|---|---|
| P0-1 | Đổi mật khẩu super admin trên **mọi** DB đang chạy và thu hồi session. Thêm migration forward: đặt `must_change_password=true` cho `usr_super_admin` khi hash vẫn là hash của V7. | CR-1 / D1 | `db/migration/postgres/V110__…sql` | S | Đăng nhập bằng mật khẩu gốc thất bại trên mọi môi trường; migration chạy được 2 lần không lỗi. **2026-11-05:** V110 đổi hash + thu hồi session; V126 bật `must_change_password` khi hash vẫn là của V110 (`SuperAdminForcedPasswordChangeMigrationContractTest`). Server chạy V110/V126 ở lần deploy tới; admin đổi mật khẩu ở lần đăng nhập đầu. |
| P0-2 | `git rm --cached backups/*` và `.codex-runtime`, thêm vào `.gitignore`. Dọn lịch sử (`git filter-repo`) và force-push **(cần người có thẩm quyền duyệt)**. Rotate mọi credential, token và session có trong dump. | CR-2 / D8 | `backups/`, `.gitignore` | M | `git log --all -- backups` trống trên remote; session trong dump bị revoke. **2026-11-04:** lịch sử đã dọn và force-push cả 4 nhánh (0 commit chứa `backups/` trên origin). Thu hồi session và đổi credential DB/Redis server làm lúc deploy theo `docs/runbooks/credential-rotation-p0-2.md`. Thu hồi session cũng nằm trong `ops/postgres/predeploy-p0.sql`; đổi mật khẩu Postgres/Redis làm trên console nhà cung cấp. **Đã chạy trên server (2026-11-04):** `predeploy-p0.sql` trong `erg-postgres-1`: revoke 0, còn hiệu lực 0 (session trong dump đã hết hạn/thu hồi). Mật khẩu Postgres/Redis production đã được chủ repo đổi (xác nhận 2026-11-04). Xong. |
| P0-3 | Fail-closed key outbox: production thiếu key hoặc key bằng hằng mặc định thì không khởi động. Khai báo biến trong `.env.production`. | A10 | `OutboxPayloadCipher.java` | S | Test khởi động profile prod không có key thì phải fail. **2026-11-05:** `OutboxPayloadCipherTest.prodProfileRefusesMissingOrBuiltInKey`. Server cần `ERG_OUTBOX_ENCRYPTION_KEY` trước khi deploy bản mới. |
| P0-4 | Chỉ coi `*` là toàn quyền khi principal là platform-wide. `assignPermissions` phải gọi `assertCanGrant`; cấm cấp `*`/`rbac.*` và cấm tự cấp quyền cho chính mình. | A3 | `ErgPolicyService.java:92`, `UserRoleAssignmentUseCase.java:56` | M | Test: admin tenant tự cấp `*` thì nhận 403. **2026-11-05:** `ProtectedRolePolicy.assertCanGrantPermissions`, `ProtectedRolePolicyTest`. |
| P0-5 | Kiểm tra quyền sở hữu attempt trong `mutable()`. Sửa `assigned()`: resolve Student trước khi so sánh, và `orElse(false)`. Thêm luồng liên kết `Student.authUserId`. | CR-3, CR-4 / B2, SQ-01 | `LmsAttemptUseCase.java:97-98,224,349-351`, `LmsOperationalCommandService.java:373` | M | Test tích hợp: tạo assignment qua API, học sinh start và submit được; học sinh B submit attempt của A thì 403. **2026-11-05:** `LmsAttemptFlowIntegrationTest`, `LmsAttemptOwnershipAndGradingTest`. |
| P0-6 | CRM: `seoId` khác user hiện tại thì bắt buộc `crm.analytics.read`; lọc theo scope đơn vị. Chặn tự duyệt handover; `isReady` phải yêu cầu đủ checklist. | C1, N6, C3 | `CrmSchoolController.java:25-45`, `CrmSchoolService.java:55-84`, `CrmWorkspaceService.java:249,398` | S | Test: `seo.staff` gọi bằng id SEO khác thì 403. **2026-11-05:** `CrmSchoolServiceTest`, `CrmWorkspaceServiceTest`. |
| P0-7 | Ghi việc thu hồi khi phát hiện replay trong `REQUIRES_NEW` (hoặc `noRollbackFor`). | A6 | `AuthRefreshUseCase.java:61-80` | S | Test DB thật: dùng lại R1 thì R2 bị revoke và có event `REFRESH_TOKEN_REUSE`. **2026-11-05:** `AuthRefreshConcurrencyIntegrationTest` (REFRESH_TOKEN_REUSE). |
| P0-8 | Sửa nguồn IP: `forward-headers-strategy: native` cùng `remoteip.internal-proxies`; Nginx đặt `proxy_set_header X-Forwarded-For $remote_addr`. | N1 (10) | `application.yml:116`, `ops/docker/nginx/*.conf` | S | Request có `X-Forwarded-For` giả vẫn bị tính theo IP thật. **2026-11-05:** `forward-headers-strategy: native`, nginx đặt XFF = `$remote_addr`, `TrustedClientIpResolverTest`. Server: deploy lại nginx. |
| P0-9 | Thay đổi chưa commit: khôi phục `ssl:false / starttls:true`; dùng so khớp tên role chính xác thay cho `contains`. | review diff | `application.yml:89-95`, `SessionService.java` | S | Test bảng role: `vice_principal` không được scope admin. **2026-11-05:** `ApplicationYamlProfileBindingTest`, `SessionServiceTest`. |
| P0-10 | **Chặn deploy** cho tới khi chạy query đếm trùng `student_code`/`code` trên DB đích. Nếu có trùng thì dọn dữ liệu bằng runbook trước khi migrate tới V88. | N2 (13) | `V88__…sql:6-11` | M | Query đếm trùng trả 0 trên DB đích. **2026-11-04:** DB local (V109) trả 0 cho cả 3 khoá; DB server chạy lại `ops/postgres/preflight-v88-duplicates.sql` lúc deploy. Lúc deploy chạy một lệnh `psql "$TARGET_URL" -v ON_ERROR_STOP=1 -f ops/postgres/predeploy-p0.sql` (dừng nếu còn trùng). **Đã chạy trên server (2026-11-04):** DB ở V107 (V88 đã áp dụng), `predeploy-p0.sql` báo trùng 0/0/0. Xong. |

### Giai đoạn 1 — Tuần 1: cấu hình, DB, chống lạm dụng

| ID | Việc cần làm | Nguồn | File chính | Effort | Nghiệm thu |
|---|---|---|---|---|---|
| P1-1 | Đưa `erg.*`, `management.*`, `server.tomcat`, `springdoc` lên document YAML chung; mỗi profile chỉ giữ giá trị khác biệt. Prod đặt `registration-token-encryption-required=true`. | A1, A11 | `application.yml:119-464` | M | `@SpringBootTest(profiles="prod")` assert CORS, trusted proxy và key push được bind từ env. **2026-11-05:** YAML chung + `ApplicationYamlProfileBindingTest`. |
| P1-2 | Thêm migration RLS (theo mẫu V90) cho 3 bảng curriculum. Bắt `TenantRlsMatrixIntegrationTest` chạy trong CI có Docker và không được skip. | N1 (13) | `V111__…sql` | S | Test RLS xanh trong CI. **2026-11-05:** V111 + `TenantRlsMatrixIntegrationTest`, CI bắt Docker và fail nếu skip. |
| P1-3 | Khi khởi động: role DB runtime là superuser, BYPASSRLS hoặc owner bảng thì production dừng. Sửa `.env.example` sang dùng `erg_app`. | D4 | `ApplicationRunner` mới, `.env*.example` | S | Chạy bằng owner ở profile prod thì không khởi động được. **2026-11-05:** `RuntimeDatabaseRoleGuard` + `RuntimeDatabaseRoleGuardIntegrationTest`. |
| P1-4 | Production đặt `baselineOnMigrate=false`. Script reset chỉ chạy với host local, hoặc khi có cờ `-AllowRemoteHost` khớp chính xác. | N3, N4 (13) | `FlywayConfig.java:33`, `scripts/reset-and-seed-postgres.ps1` | S | Script từ chối chạy với host từ xa. **2026-11-05:** `FlywayConfigTest`; guard script từ chối URL nhiều host, self-check `scripts/postgres-target-guard.test.ps1` chạy trong CI. |
| P1-5 | Lockout và backoff theo `userId`. Sai mật khẩu thì luôn trả `AUTH_INVALID_CREDENTIALS`, kể cả khi tài khoản bị khóa hay vô hiệu. Inject bắt buộc rate limiter và enforcement. | A12, SQ-14 | `LoginFailureEnforcementService.java`, `AuthLoginUseCase.java:115-174` | M | Test: sai 5 lần thì bị khóa N phút; email bị disable cùng mật khẩu sai trả 401. **2026-11-05:** `AuthOperationsFlowTest` (khóa theo userId, lỗi chung `AUTH_INVALID_CREDENTIALS`). |
| P1-6 | Khóa rate limit GraphQL theo `(tenant, user)`. Nhãn metric chỉ nhận operation trong allowlist, còn lại gom thành `other`. Thêm `MeterFilter` giới hạn số tag. | A2, N1 (12), C12 | `GraphQlGuardFilter.java:142`, `GraphQlContextInterceptor.java:36` | S | Đổi `operationName` vẫn bị giới hạn. **2026-11-05:** `GraphQlSecurityGuardContractTest`, `GraphQlOperationMetricTagTest`. |
| P1-7 | Thêm timeout SMTP (5 giây), `spring.task.scheduling.pool.size>=4`, và advisory lock cho từng job nền. | N2, C16 (12) | `application.yml:81-95`, các `@Scheduled` | S | SMTP giả treo không chặn các worker khác. **2026-11-05:** Timeout SMTP 5 s, pool 4, `ClusterJobLock` cho mọi job nền kể cả refresh-token retention; `HungSmtpRelayTimeoutTest`. |
| P1-8 | `GlobalExceptionHandler` kế thừa `ResponseEntityExceptionHandler`. Lỗi nghiệp vụ calendar trả 409/404 thay vì 500. | A5, N7 (11) | `GlobalExceptionHandler.java:282` | S | Gọi sai method trả 405; publish lại batch trả 409. **2026-11-05:** Map `ErrorResponseException` thay vì kế thừa (cùng kết quả 405/415); calendar trả 409/404. |
| P1-9 | Gỡ phụ thuộc BE-035 hoặc gia hạn baseline (có review) để `./mvnw verify` xanh trở lại. | A4 | `internal-access-baseline.tsv:5` | S | `verify` xanh. **2026-11-05:** `internal-access-baseline.tsv` rỗng, `./mvnw verify` xanh (1466 + 59). |

### Giai đoạn 2 — Tuần 2–3: đúng nghiệp vụ (LMS, lịch, thông báo, audit)

| ID | Việc cần làm | Nguồn | Effort | Nghiệm thu |
|---|---|---|---|---|
| P2-1 | Start chấp nhận quiz có trong `assignment_resources` và dùng đúng `quizVersionId` đã ghim. | B1, N1 (11) | S | Assignment `[Q1,Q2]`: start Q2 OK, và làm đúng version đã ghim. **2026-11-05:** `startAcceptsSecondaryQuizFromAssignmentResourcesAndUsesPinnedVersionPackage`. |
| P2-2 | Bài `kind=test` bắt buộc `server-authoritative`. Tạo quyền `lms.quiz.play` cho học sinh, tách khỏi `lms.quiz.read`. Package chỉ trả cho học sinh được giao bài. | (a), N3 (11), SQ-06 | M | Học sinh không thấy `answerKey`; quiz không được giao trả 403. **2026-11-05:** `QuizPublishServiceTest`, `LmsLearnerPackageAccessTest`, quyền `lms.quiz.play`. |
| P2-3 | Chấm dự phòng chuyển sang `needs_review` thay vì đếm câu. Đọc `passPercent`. Chuẩn hóa NFC và gộp khoảng trắng cho câu trả lời ngắn. | SQ-04, (c) | S | 50 key rác cho 0 điểm; "Hà Nội" gõ NFD vẫn chấm đúng. **2026-11-05:** `GradingEngineTest` (NFC, needs_review). |
| P2-4 | Thêm command chấm tay (`gradeManualAnswer`), chuyển trạng thái sang `graded`. Đưa tập trạng thái thành tham số lấy từ `LmsStatePolicy` (bỏ 16 bản chép). | B3, SQ-03 | M | Bài essay được chấm tay vào điểm lớp; giới hạn lượt và dashboard thống nhất. **2026-11-05:** `gradeManualAnswer`; điểm chấm tay đi vào projection lớp (`teacherGradesManualAnswerAndAttemptMovesToGraded`); `AttemptStateParityTest` + `AttemptStateSqlParityTest` khóa các literal trạng thái theo `LmsStatePolicy`. |
| P2-5 | Thời gian ân hạn 60–120 giây và đánh dấu `late`. Tự đóng attempt hết hạn. Thêm partial unique index cho `in_progress`. Start phải kiểm tra `assignment.status`. | (b1), (b2) | M | Nộp trễ 5 giây vẫn được nhận; 2 lệnh start song song chỉ tạo 1 attempt. **2026-11-05:** Grace 90 s, đánh dấu late, partial unique index V115, các test start/submit. Job `AttemptExpirySweepJob` (5 phút, cluster lock) tự đóng attempt quá hạn kể cả khi học sinh không quay lại (`LmsAttemptFlowIntegrationTest.expirySweepFindsAndClosesAnAttemptPastItsDuration`, Postgres thật). |
| P2-6 | Lệnh ghi assignment phải kiểm tra scope lớp và đơn vị. Status/download báo cáo phải kiểm tra `requestedBy` hoặc `canAccessClass`. | (d2), B8 | S | Giáo viên lớp khác xóa assignment hoặc tải báo cáo thì 403. **2026-11-05:** `LmsAssignmentCommandServiceTest`, `ReportingOperationsTest`. |
| P2-7 | Lịch: publish bỏ qua event `CANCELLED`. `DELETE_CONFLICTING` tính xung đột còn lại trước khi hủy. Validate ngày cho `deleteTeacherSchedule`. Khóa theo giáo viên khi import song song. | B6, B7, B9, (e1) | M | 4 test hồi quy tương ứng. **2026-11-05:** Các test trong `CalendarCommandUseCaseTest`. |
| P2-8 | Thông báo: bỏ key `eventType` và thêm test tích hợp với service thật. Giới hạn số lần recovery, quá giới hạn thì quarantine. Áp dụng preference khi gửi. Từ chối thiết bị chỉ có FID. Ghi kết quả outbox theo `locked_by`. | C7, N7, C8, C9, C15 | M | Lịch publish xong thì giáo viên nhận được thông báo; poison event dừng sau N lần. **2026-11-05:** `CalendarBatchPublishedConsumerTest`, `ModuleEventRecoveryJobQuarantineIntegrationTest`. |
| P2-9 | Audit: trigger cho phép chỉ cập nhật `legal_hold`, DELETE chỉ khi hết hạn và không bị hold. Sửa cutoff retention về `now()`. Mở rộng redaction và dùng HMAC. | C11, N3, C17 (12) | M | Bật được legal hold; bản ghi được giữ đúng 365 ngày. **2026-11-05:** V121 + `AuditLegalHoldTriggerIntegrationTest`, `AuditRedactionPolicyTest`. Server cần `AUDIT_REDACTION_HMAC_SECRET`. |
| P2-10 | Chạy projection `AFTER_COMMIT`. Xóa cache theo tenant/lớp thay vì xóa toàn bộ. | SQ-05, SQ-09 | M | Projection lỗi không làm hỏng lượt nộp; hit-rate cache ổn định khi tải cao. **2026-11-05:** Projection `AFTER_COMMIT` (`projectionRunsAfterCommitAndItsFailureCannotBreakTheSubmission`); evict theo tenant. Còn lại: số liệu hit-rate chỉ đo được trên môi trường benchmark (job k6 trong CI). |
| P2-11 | Media: bỏ `@Transactional` ở cấp class, gọi R2 ngoài transaction. Connector: tách 3 bước, sửa `retryCount`. | SQ-07, SQ-08, C2, N4 (12) | M | Retry dừng ở 3 lần; không giữ connection DB khi gọi ra ngoài. **2026-11-05:** `EnterpriseConnectorServiceTest` (provider ngoài transaction, retry dừng ở giới hạn). |

### Giai đoạn 3 — Tháng 2 trở đi: gỡ rối cấu trúc

| ID | Việc cần làm | Nguồn | Effort | Nghiệm thu |
|---|---|---|---|---|
| P3-1 | Gom về một `EffectivePermissionResolver`, một `AccessScopeAssembler`, một `AssignmentAccessPolicy`. Chỉ giữ một định nghĩa cho `*`. | SQ-02, SQ-13, N8 (11) | M–L | Test bảng grant/allow/deny/revoke/hết hạn dùng chung cho Login, ErgPrincipal và OIDC. **2026-11-05:** Một `EffectivePermissionResolver`, một `AccessScopeAssembler`, một quy tắc `*` (`WorkspaceCapabilityMapper.isPlatformWide`, dùng chung với `AuthorizationCapabilityPolicy`). |
| P3-2 | Bỏ các tầng chỉ chuyển tiếp (Operations → UseCase một dòng). Xóa bean `@Deprecated` (`LmsRuntimeService`…), constructor tương thích và code chết (`checkVersion`, `abandon`). | SQ-18, layering | L | Số hop chuyển tiếp ≤ 1 cho mỗi call chain. **2026-11-04:** đã xóa `AuthService`, `AuthorizationService`, `CurriculumService`, `ReportJobService`, `CalendarReadService`, `CalendarCommandService`, `EducationUnitService` cùng 12 lớp `*Compatibility` và các constructor/nhánh `legacy`. Còn giữ có chủ đích: `UserService`, `LearningContentService`, `CrmSchoolService`, `CrmWorkspaceService`, `StudentEngagementService`, `StudentDashboardReadService` vì đây vẫn là code thật duy nhất (dời vào module thuộc P3-4); endpoint `@Deprecated` giữ theo contract protocol. Ceiling `service` 112 → 104. Vòng 2: `CrmSchoolService`, `CrmWorkspaceService`, `StudentEngagementService`, `StudentDashboardReadService`, `LearningContentService` đã vào module và bỏ `@Deprecated`; `UserService` đã xóa. |
| P3-3 | Mỗi loại câu hỏi là một strategy (strip, extractKey, normalize, grade) đăng ký trong `QuestionKindRegistry`. | SQ-15 | L | Thêm loại câu hỏi mới chỉ cần sửa 1 file; test lộ đáp án chạy cho mọi loại. **2026-11-04:** `QuestionKindStrategy` (lcms `quiz-publish`) gom strip, extractKey, kiểm tra answer key (publish gate) và grade cho từng loại; `QuestionKindRegistry` gắn thẳng strategy vào descriptor (bỏ enum `AnswerKeyRule`/`GradingRule`). `GradingEngine` (lms) chỉ tra strategy, `QuizValidationService` không còn switch. Loại mới = 1 hằng strategy + 1 dòng đăng ký. Xong. |
| P3-4 | Chuyển dần `service/lms`, `repository/lms` vào `lms/internal`. Thêm rule ArchUnit "`*.internal..` không phụ thuộc `..service..`". | SQ-17 | L | Ratchet baseline giảm theo từng sprint. **2026-11-04:** `service/{auth,notifications,calendar,learningcontent,curriculum,reporting,lcms,users}` đã vào module; ceiling `service` 112 → 57. Còn lại có lý do: `service/auth/{PasswordService,AccountMailSender}` và `service/authorization/*` (dời sẽ tạo vòng users↔auth/authorization, cần lật phụ thuộc trước); `service/{quiz,elearning,crm,school,...}` chưa có module đích; `model/`, `repository/` chưa dời. **Vòng 2 (2026-11-04):** thêm crm, elearning, school, quiz (→ lcms/lms/reporting), sessions, outbox; lật phụ thuộc users↔auth/authorization bằng port trong `users`. `service` chỉ còn 6 service prototype (P3-10), ceiling 112 → 6. Đã thêm rule ArchUnit `*.internal..` không phụ thuộc `com.erg.ergspringboot.service..`. **Vòng 3 (2026-11-04):** entity/repository của audit, calendar, content, crm, lcms (quiz authoring/bank/media), learningcontent, reporting, school, users, outbox, notifications, learningoutcomes, learningstandards, enterpriseconnectors và 12 aggregate lms đã vào `<module>.persistence` (named interface `persistence` khi module khác dùng). Ceiling `model` 185 → 1, `repository` 136 → 0. **Vòng 4 (2026-11-04):** cụm identity quanh `User` (session, role, permission, access scope, work shift…) vào `users.persistence.{auth,authorization,organization}` (auth/authorization/organization vốn đã phụ thuộc users nên không tạo vòng); curriculum vào `curriculum.persistence`, phần impact đọc ngược lcms/lms/learningcontent thay bằng port `CurriculumUsageCounter` (fail closed nếu không có counter); quiz runtime/version vào `lcms.persistence.quiz`, GraphQL report gọi thẳng `QuizReportService`. Package `repository/` đã xoá, `model/` chỉ còn `BaseEntity` dùng chung. Xong. |
| P3-5 | Tách các class lớn theo việc (bảng ở file 14 §3). | SQ-20 | L | Không class nào vượt khoảng 400 dòng hoặc ôm hơn 2 việc. **2026-11-05:** Đã tách 13 class; `ClassSizeRatchetTest` chặn class > 400 dòng, ngoại lệ có review: 2 repository LMS (chỉ khai báo query) và `QuestionKindStrategy` (một strategy mỗi loại câu hỏi). |
| P3-6 | Thêm `tenant_id` và RLS cho các bảng `crm_*` theo kiểu expand → backfill → NOT NULL. Thay `Map<String,Object>` bằng record cho `policySnapshot`, `revenueLines`, `checklist`. | C4/D5, SQ-19 | M–L | Test RLS cho CRM; lỗi parse snapshot phải throw. **2026-11-05:** V118 (tenant_id + RLS cho `crm_*`), `RevenueLine`/`ChecklistItem` là record. Snapshot hỏng giờ được log thay vì nuốt im (`AttemptDeadlinePolicyTest`); API trả `AttemptPolicySnapshotResponse` có kiểu, cột jsonb giữ Map để không phải migrate dữ liệu cũ. |
| P3-7 | Đưa seed demo sang một Flyway location riêng chỉ bật ở dev. Chuyển tài khoản demo có `login_count=0` sang `INACTIVE`. Migration phá hủy về sau phải có quarantine và đếm dòng. | D3, N8, D2 (13) | M | DB prod mới không có dữ liệu demo. **2026-11-04:** location `classpath:db/seed/dev` chỉ bật ở profile `dev` (test `FlywayConfigTest`); 3 seeder demo có `@Profile("!prod")`. Dữ liệu demo từ V11/V21/V68 đã áp dụng không xóa (migration append-only, có tài khoản placeholder giữ FK); tài khoản demo đã `INACTIVE` (V112). DB prod mới: đặt `ERG_FLYWAY_DEMO_PURGE=true` cho lần deploy đầu, V125 xóa đúng các id demo (quarantine `_quarantine_v125_*`, đếm dòng, dừng nếu DB không mới hoặc có dòng thật tham chiếu); mặc định là no-op. Giữ `usr_super_admin`, `usr_relationship_placeholder`. Test `DemoSeedPurgeMigrationIntegrationTest`. |
| P3-8 | Xóa cấu hình chết (`jwt-*`, `max-failed-login`, `geo-*`, `cookie.*`, `rate-limit.*`). Xóa rule `permitAll` của route prototype. Khai báo `security: []` cho endpoint public trong OpenAPI và đánh dấu deprecated cho 9 path non-v1. | N5 (10), D7, SQ completeness | S–M | Test fail khi có khóa `erg.*` không được bind. **2026-11-04:** `ErgPropertyKeysAreBoundTest` fail khi có khóa `erg.*` không được bind; đã xóa 76 khóa chết. |
| P3-9 | Soft-delete user và ẩn danh hóa PII. Đổi FK `user_activities` sang `SET NULL`. Thêm index `user_id` để cascade không phải quét toàn bảng. | N5 (13) | M | Xóa user không làm mất audit trail. **2026-11-05:** `UserLifecycleUseCaseSoftDeleteTest`, V113 FK `SET NULL`. |
| P3-10 | Quyết định cho các module prototype (CMS, catalog, reviews, recruitment, AI, crawler…): làm thật có persistence, hoặc xóa hẳn. | file 12 §3.1, file 14 §5 | L (tùy phạm vi) | Danh sách module được chốt với Product. |

### Ước lượng tổng

| Giai đoạn | Số việc | Công sức ước tính (1 dev) |
|---|---|---|
| 0 — Khẩn cấp | 10 | 5–7 ngày |
| 1 — Tuần 1 | 9 | 5–7 ngày |
| 2 — Tuần 2–3 | 11 | 12–16 ngày |
| 3 — Cấu trúc | 10 | 25+ ngày (có thể làm song song với tính năng mới) |

Với 2 dev backend, giai đoạn 0 và 1 có thể xong trong khoảng 1,5 tuần.

---

## 6. Giới hạn của đánh giá này

- Chỉ đọc code: không chạy app, test, migration hay kết nối DB. Hành vi theo framework (ví dụ `X-Forwarded-For`) được suy ra từ tài liệu framework, chưa tái hiện trên runtime.
- Mục ✔ là đã được người tổng hợp đối chiếu lại với code. Các mục còn lại do 5 agent kiểm chứng; mỗi mục có file:line trong file nguồn.
- Có một mâu thuẫn đã được sửa: file 10 (N2) nói endpoint reviews/recruitment in-memory đang mở ở production. Thực tế `EngagementController` có `@Profile("prototype")`, nên route này không tồn tại ở production. Mức rủi ro thực là Low; rủi ro còn lại là rule `permitAll` vẫn nằm trong `SecurityConfig`.
- Không đánh giá frontend, hạ tầng cloud thật, hay dữ liệu đang chạy ở production.
