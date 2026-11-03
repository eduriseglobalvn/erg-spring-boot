# 03 — Identity & Access (auth, users, sessions, profiles, authorization, security)

> Phạm vi đọc: các package `auth`, `users`, `sessions`, `profiles`, `authorization` (kể cả `lms/`, `lcms/`, `calendar/`... `authorization`), `platform/security`, `operations`, `configuration`, `support`, cùng migration V1/V35–V78/V100/V108–V109 và GraphQL `accessManagementWorkspace`.
> Mọi đường dẫn tính từ gốc repo `erg-spring`; `M:` = `src/main/java/com/erg/ergspringboot/`, `T:` = `src/test/java/com/erg/ergspringboot/`, `R:` = `src/main/resources/`.
> Chỉ ghi điều đã đọc trong code; chỗ chưa xác minh được ghi rõ "nghi ngờ" / "chưa xác minh". Không có secret/token nào trong tài liệu.

---

## 1. Phạm vi và trách nhiệm

Module này sở hữu toàn bộ vòng đời danh tính: đăng nhập local (mật khẩu Argon2, access token mờ 15 phút + refresh token xoay vòng, lưu hash trong `user_sessions`), BFF OIDC tùy chọn (cookie session + CSRF), tài khoản/hồ sơ người dùng, nhân sự (`employee_profiles`), phiên đăng nhập/thiết bị, RBAC (role, permission, override theo user, gán role theo phạm vi `user_role_assignments`) và toàn bộ lớp bảo mật HTTP (filter chain, tenant context → GUC Postgres cho RLS, chính sách deny-aware).
Quyết định quyền được tính từ server-side (session row + role/permission trong DB), không tin header/portal do client gửi (trừ chế độ `trusted-header-auth` chỉ cho test).
Tenant lấy từ session đã xác thực (`ErgPrincipal.tenantId`), không từ header/body.
Các package `operations`, `configuration`, `support` trong phạm vi hầu như là prototype/dead code (xem §9).

Kiến trúc lớp: Controller → `*Operations` (facade module) → `*UseCase` (logic thật). `AuthService`, `UserService`, `AuthorizationService` là adapter `@Deprecated` chỉ ủy quyền xuống facade; mỗi facade còn có constructor "legacy" giữ cho test cũ (rẽ nhánh `legacy != null ? ... : ...`) — xem `M:auth/AuthOperations.java:24-132`, `M:users/UserOperations.java:20-58`, `M:authorization/AuthorizationCommandOperations.java:35-228`.

---

## 2. Class/entity chính và vai trò

| Class | Đường dẫn | Vai trò |
|---|---|---|
| `AuthLoginUseCase` | `M:auth/internal/login/AuthLoginUseCase.java` | Chính sách đăng nhập, tính quyền hiệu lực, thay thế phiên, cấp token |
| `AuthRefreshUseCase` | `M:auth/internal/refresh/AuthRefreshUseCase.java` | Xoay refresh token, phát hiện replay, thu hồi toàn tài khoản |
| `AuthSessionLifecycleUseCase` | `M:auth/internal/session/AuthSessionLifecycleUseCase.java` | logout / logout-all |
| `AuthSecurityQueryUseCase` | `M:auth/internal/security/AuthSecurityQueryUseCase.java` | Lịch sử đăng nhập (100 dòng), trạng thái IP |
| `AuthOperations` | `M:auth/AuthOperations.java` | Facade auth + recovery-email |
| `AuthLoginRateLimiter` | `M:service/auth/AuthLoginRateLimiter.java` | Giới hạn tốc độ 3 chiều (IP / tài khoản / thiết bị) |
| `LoginFailureEnforcementService` | `M:service/auth/LoginFailureEnforcementService.java` | Ghi login-attempt thất bại trong transaction độc lập (REQUIRES_NEW) |
| `AccountVerificationService` | `M:service/auth/AccountVerificationService.java` | OTP xác minh recovery email |
| `AuthTokenService` / `PasswordService` | `M:service/auth/` | Token ngẫu nhiên 48 byte + SHA-256 / Argon2 |
| `TransactionalAccountMailSender`, `SmtpAccountMailSender` | `M:service/auth/` | Gửi mail qua outbox (primary) / SMTP trực tiếp |
| `RefreshTokenHistoryRetentionJob` | `M:service/auth/RefreshTokenHistoryRetentionJob.java` | Xóa lịch sử refresh > 30 ngày |
| `OidcBffController` | `M:controller/auth/OidcBffController.java` | Login redirect/session/CSRF cho BFF OIDC |
| `UserOperations` + `User*UseCase` | `M:users/` (create, lifecycle, password, profile, query, roleassignment, session) | Nghiệp vụ tài khoản |
| `EmployeeManagementService`, `EmployeeSecurityActivityService` | `M:service/users/` | Hồ sơ nhân sự + feed bảo mật hợp nhất |
| `ProfileService` | `M:service/profiles/ProfileService.java` | CRUD bảng `profiles` (hồ sơ giáo viên mở rộng) |
| `SessionService` | `M:service/sessions/SessionService.java` | Bootstrap phiên: roles, quyền, đơn vị/lớp quản lý, scope hiện tại |
| `AuthorizationCommandOperations` + 5 use case | `M:authorization/` | Role CRUD, catalog quyền, gán role trực tiếp, override quyền, truy vấn quyền hiệu lực |
| `ScopedRoleAssignmentService` | `M:service/authorization/ScopedRoleAssignmentService.java` | Gán/thu hồi role theo scope (SYSTEM/TENANT/CENTER/SCHOOL/CLASS) |
| `AuthorizationMutationAuditService` | `M:service/authorization/AuthorizationMutationAuditService.java` | Tăng `authz_version`, ghi user_activities + security_events + audit log |
| `ErgAuthenticationFilter` | `M:platform/security/ErgAuthenticationFilter.java` | Xác thực Bearer → `ErgPrincipal`, chặn portal sai / tài khoản bị hạn chế |
| `ErgPrincipalService` | `M:platform/security/ErgPrincipalService.java` | Tra session theo hash token, dựng principal (role, quyền, scope) |
| `ErgPolicyService` | `M:platform/security/ErgPolicyService.java` | Quyết định allow/deny (wildcard, alias, deny thắng) |
| `AuthorizationCapabilityPolicy`, `WorkspaceCapabilityMapper` | `M:platform/security/` | Ánh xạ role/permission → workspace ADMIN/TEACHER/STUDENT/CRM |
| `ProtectedRolePolicy` | `M:platform/security/ProtectedRolePolicy.java` | Bảo vệ role `system.*` |
| `SecurityConfig` | `M:platform/security/SecurityConfig.java` | Filter chain, route → permission |
| `PermissionAuthorizationManager`, `MethodAuthorizationEvaluator` | `M:platform/security/` | Cầu nối route-rule và `@PreAuthorize` sang `ErgPolicyService` |
| `TenantContext`, `TenantContextFilter`, `TenantGucApplier` | `M:platform/security/` | Đẩy tenant/user/portal vào GUC `app.*` cho RLS |
| `OidcPrincipalBridgeFilter`, `OidcMfaPolicy` | `M:platform/security/` | Dựng principal từ OidcUser + yêu cầu MFA cho vai trò đặc quyền |
| `TrustedClientIpResolver` | `M:platform/security/TrustedClientIpResolver.java` | Lấy IP client tin cậy (XFF chỉ khi peer trong CIDR proxy tin cậy) |
| `AccessScopeSet` / `AccessScopeAssembler` / `UserAccessContext` | `M:platform/security/` | Mô hình phạm vi dữ liệu (đơn vị giáo dục, lớp...) |
| Entity | `M:model/users/User.java`, `model/auth/{AuthSession,AuthLoginAttempt,RefreshTokenHistory,SecurityEvent,AccountVerificationCode}.java`, `model/authorization/{Role,Permission,PermissionGroup,PermissionBinding,UserPermissionOverride,UserRoleAssignment}.java`, `model/users/{Profile,EmployeeProfile,UserActivity}.java`, `model/organization/UserAccessScope.java` | Bảng dữ liệu (§6) |
| Annotation `@UsersManage`, `@Rbac*`, `@UserSelf*`,... | `M:authorization/authorization/*.java` | Meta-annotation `@PreAuthorize("@methodAuthorization.isAllowedAny(authentication, ...)")` (bảng §4.6) |

---

## 3. Nghiệp vụ và luồng xử lý

### 3.1 Đăng nhập local — `POST /api/v1/auth/login`

`M:controller/auth/AuthController.java:43-58` → `AuthOperations.login` → `M:auth/internal/login/AuthLoginUseCase.java:141-185`.

1. Chuẩn hóa `identifier` (trim + lowercase, `:549`), portal (admin/crm/lms/elearning/lcms, mặc định `lms`, `:557`).
2. Rate limit (nếu bean có): sai → `AuthFailure.TooManyAttempts` 429 (`:145-147`). `retryAfterSeconds` của limiter bị bỏ, không có header Retry-After.
3. `users.findForLogin(identifier)` (`UserRepository.java:48-50`: tìm id theo `lower(username)` hoặc `lower(email)` **không giới hạn tenant**, rồi nạp role/permission/override). Không thấy user → chạy `burnUnknownAccountCheck` (Argon2 giả để cân thời gian, `PasswordService.java:29-31`), ghi attempt, ném `InvalidCredentials` (`:148-153`).
4. Kiểm tra trạng thái **trước khi so mật khẩu** (`:156-174`):
   - `BLOCKED`/`BANNED` → `ACCOUNT_LOCKED` (403)
   - `INACTIVE` → `ACCOUNT_DISABLED` (403)
   - `INVITED` + `mustChangePassword=true` → được vào "phiên first-login bị hạn chế" (`restrictedFirstLogin`); `INVITED` còn lại → `ACCOUNT_ACTIVATION_REQUIRED`
   - `PENDING` + provider `local` → `EMAIL_NOT_VERIFIED`
5. So mật khẩu Argon2 (`PasswordService.matches`, `:175`); sai → ghi failure có `enforceThreshold=true`, ném `AUTH_INVALID_CREDENTIALS` 401.
6. `restrictedFirstLogin` → đặt `status=ACTIVE` ngay lúc login (`:180-183`), việc ép đổi mật khẩu dựa vào cờ `mustChangePassword` (xem §3.7).
7. `issueTokens` (`:187-244`): tính quyền hiệu lực (§4.1), tính danh sách portal "entitled" (chỉ trả về, không chặn — `:190-193`), sinh access/refresh token (48 byte URL-safe), **khóa dòng user** `select ... for update` (`UserRepository.java:41`; timeout/lock fail → `AUTH_LOGIN_IN_PROGRESS` 409, `:246-254`), áp dụng quy tắc thay thế phiên (§3.2), lưu `AuthSession` (chỉ lưu hash SHA-256 của token), ghi `AuthLoginAttempt SUCCESS`, cập nhật `lastLoginAt/loginCount/lastLoginProvider`.
8. Trả `AuthResponse`: profile, accessToken, refreshToken, `expiresIn=900`, `Bearer`, thông tin session, `permissions`, `portals`, `accountType` (`:240-243`).

TTL (`:57-61`): access token 15 phút; phiên (refresh) 8 giờ nếu có role chứa `admin`/`manager`/`coordinator`, ngược lại 12 giờ (`:393-401`). Refresh **không** gia hạn `expiresAt` của phiên (chỉ `accessTokenExpiresAt`, `AuthRefreshUseCase.java:95-101`) → trần cứng 8/12 giờ.

Controller dựng `AuthClientContext` (IP qua `TrustedClientIpResolver`, User-Agent, deviceId/name/fingerprint từ body; tên thiết bị suy ra từ UA nếu thiếu — `AuthController.java:103-153`).

### 3.2 Quy tắc thay thế phiên (single-session theo ngữ cảnh trình duyệt)

`AuthLoginUseCase.java:198-213, 457-482`:
- Lấy các phiên chưa thu hồi và còn hạn của (user, tenant).
- "Ngữ cảnh trình duyệt đã biết" = cùng `deviceId` **và** (cùng `deviceFingerprint` nếu cả hai có, ngược lại cùng deviceName+userAgent). IP đã biết = trùng IP của bất kỳ phiên đang hoạt động.
- Nếu **không** khớp trình duyệt **và** không khớp IP → thu hồi toàn bộ phiên đang hoạt động với `revokedReason="session_replaced"` + ghi `SecurityEvent SESSION_REPLACED` (MEDIUM); nếu ≥ ngưỡng (mặc định 5 lần/10 phút, cấu hình `erg.auth.rapid-session-replacement`) → thêm `RAPID_SESSION_REPLACEMENT` (HIGH, chỉ cảnh báo, không chặn login).
- Cùng trình duyệt mở nhiều portal (LMS + LCMS) → không thu hồi nhau (test `sameBrowserContextCanOpenLmsAndLcmsWithoutRevokingEitherPortalSession`).
- Bearer cũ bị thay thế khi dùng tiếp sẽ nhận `401 AUTH_SESSION_REPLACED` (`SecurityConfig.java:81-93`, `ErgPrincipalService.java:167-175` — chỉ khi không có `tenantResolver`, xem §9).

### 3.3 Làm mới token — `POST /api/v1/auth/refresh`

`AuthRefreshUseCase.refresh` (`:61-104`), `@Transactional`:
1. Hash refresh token, `lockByRefreshTokenHash` (PESSIMISTIC_WRITE, `AuthSessionRepository.java:38-40`) và lọc phiên còn hiệu lực.
2. Không có phiên hợp lệ → tìm theo `previousRefreshTokenHash`, hoặc trong `refresh_token_history` (token đã tiêu thụ ở các vòng xoay cũ hơn). Nếu thấy → **replay**: `handleReplay` (`:106-133`) thu hồi toàn bộ phiên của user (`revokedReason="refresh_token_reuse"`) và ghi `SecurityEvent REFRESH_TOKEN_REUSE` (CRITICAL, riskCode `TOKEN_REPLAY`); luôn ném `AUTH_INVALID_TOKEN` 401 (lệnh ném nằm ngoài transaction thành công → xem §9 về rollback).
3. Hợp lệ → lưu hash token cũ vào `refresh_token_history` (unique `token_hash`), đặt `previousRefreshTokenHash`, sinh cặp token mới, `accessTokenExpiresAt = now+15'`.
`RefreshTokenHistoryRetentionJob` cron `0 20 3 * * *` (mặc định) xóa history có `consumed_at` > 30 ngày.

### 3.4 Đăng xuất

- `POST /api/v1/auth/logout` → `AuthSessionLifecycleUseCase.logout` (`:24-31`): thu hồi đúng phiên theo `(sessionId, userId, tenantId)`; không thấy → `AUTH_INVALID_TOKEN`.
- `POST /api/v1/auth/logout-all` → thu hồi mọi phiên còn hoạt động, trả số lượng (`:33-39`).
- Trong chế độ BFF, `/api/v1/auth/logout` do Spring Security logout filter xử lý (xóa cookie `__Host-erg_session`, trả 204 — `SecurityConfig.java:59-65`); `OidcBffController.logout` chỉ là helper (`:134-141`).

### 3.5 Giới hạn tốc độ & chống brute-force

- `AuthLoginRateLimiter.allow` (`:71-89`): cửa sổ cố định 60 giây, 3 bộ đếm: nguồn (IP) mặc định 30/phút, tài khoản 10/phút, thiết bị 20/phút (`ErgAuthProperties.java:69-79`, YAML `R:application.yml:552-556`). Khóa counter = `erg:state:v1:auth-login-rate-limit:<dimension>:<sha256(giá trị)>:<windowStart>` (`:102-104`) — không chứa tenant. Dùng Redis router nếu có, ngược lại bộ đếm local trong process. Lỗi hạ tầng (RuntimeException) → **từ chối** (fail-closed, `:84-86`).
- Thất bại mật khẩu: `LoginFailureEnforcementService.recordFailure` (REQUIRES_NEW, `:51-115`) ghi `auth_login_attempts` (result `FAILED`); nếu thất bại 15 phút gần nhất theo email hoặc IP ≥ 5 → chỉ ghi `SecurityEvent LOGIN_THROTTLE_REQUIRED` (HIGH, result `CHALLENGE_REQUIRED`, `:96-114`). **Không khóa tài khoản, không thu hồi phiên** (test `repeatedFailedLoginDoesNotBlockUserOrRevokeSessions`; comment `AuthLoginUseCase.java:313`: "Durable account state must not be mutated from attacker-controlled failures"). Hàm `blockUserIfFailedLoginThresholdReached` (`:302-314`) thực chất là no-op.
- `GET /api/v1/auth/login-attempts`, `GET /api/v1/auth/security/ip-status` (`AuthSecurityQueryUseCase`): "blocked" chỉ là chỉ báo từ bộ đếm (≥5 trong 15'), các trường geo/ip-block đều hằng `false/null` (`:70-80`).

### 3.6 Xác minh recovery email (OTP) — `/api/v1/users/me/recovery-email/*`

`AccountVerificationService` (hằng `:27-29`: purpose `RECOVERY_EMAIL`, hết hạn 10 phút, cooldown 60 giây):
- **Challenge** (`:66-115`): user lấy theo (id, tenant). Email Google (`@gmail.com`/`@googlemail.com`) cho phép xác minh chính email đó (tự xác minh). Ngược lại: recovery phải khác email chính (`RECOVERY_EMAIL_REQUIRED` 400) và không được dùng bởi tài khoản khác (`RECOVERY_EMAIL_CONFLICT` 409). Nếu đã có mã chưa dùng và gửi < 60s trước → `OTP_RATE_LIMITED` 429; ngược lại đánh dấu mã cũ consumed. Sinh OTP 6 số bằng `SecureRandom`, lưu **hash Argon2** (`codeHash`), `maxAttempts=5`, gửi qua `AccountMailSender.sendRecoveryEmailCode`, ghi `user_activities` (`RECOVERY_EMAIL_CHALLENGE_SENT`, email bị che).
- **Verify** (`:125-157`): challenge phải thuộc đúng user/tenant/purpose; đã consumed → `OTP_INVALID`; hết hạn → `OTP_EXPIRED`; `attempts >= maxAttempts` → `OTP_INVALID`; sai OTP → tăng `attempts`. Đúng → `consumedAt`, `recoveryEmail=targetEmail`, `recoveryEmailVerified=true`, ghi `RECOVERY_EMAIL_VERIFIED`. Trả `lifecycle` mới.
- DB: unique partial `uq_account_verification_codes_active (user_id, purpose) WHERE consumed_at IS NULL` (V51) — chỉ một mã đang hoạt động; unique `lower(recovery_email)` (V51).

### 3.7 Vòng đời tài khoản ("lifecycle")

`nextSteps` được tính ở nhiều nơi (trùng lặp, §9): `COMPLETE_PROFILE` nếu `!isProfileCompleted`; `VERIFY_RECOVERY_EMAIL` nếu `!recoveryEmailVerified`; `CHANGE_PASSWORD` nếu `mustChangePassword`. Rỗng → `ACTIVE`; ngược lại `PROVISIONED` (status INVITED) hoặc `FIRST_LOGIN_RESTRICTED` (ví dụ `M:users/internal/query/UserQueryUseCase.java:161-174`).
Thực thi trong `ErgPrincipalService.isLifecycleRestricted` (`:216-220`) đặt `attributes.lifecycleRestricted`, rồi `ErgAuthenticationFilter` (`:67-70, 224-230`) trả `403 ACCOUNT_LIFECYCLE_RESTRICTED` cho mọi request **trừ**: `/api/v1/users/me`, `/api/v1/users/me/**`, `/api/v1/sessions/**`, `/api/v1/auth/logout`, `/api/v1/auth/refresh`.
`changePassword` chỉ đặt lại `status=ACTIVE` nếu profile đã hoàn tất **và** recovery email đã xác minh (`UserPasswordUseCase.java:54-55`).

### 3.8 Tạo user (admin) — `POST /api/v1/users`, `POST /api/v1/admin/users`

`UserProvisioningUseCase.create` (`M:users/internal/create/UserProvisioningUseCase.java:54-93`):
1. Email chính chuẩn hóa lowercase; recovery email (nếu có) phải khác email chính; `credentialDelivery` ∈ {`EMAIL_INVITE`,`ONE_TIME_PASSWORD`} (`:135-136`).
2. Trùng `username`/`email` (toàn cục, không theo tenant) → 409.
3. Mật khẩu tạm = `IdGenerator.newId() + "Aa1!"`; role theo **tên** (`roleGroupIds`), thiếu role nào → 400; `ProtectedRolePolicy.assertCanGrant(actor, roles)` (§4.3).
4. `policies[].roleGroup` bắt buộc nằm trong role được gán.
5. Tạo `User`: `username=email`, `status=INVITED`, `provider=local`, `accountType=erg`, `mustChangePassword=true`, `recoveryEmailVerified=false`, `tenantId` = tenant của actor (mặc định `erg`), `isProfileCompleted = có fullName && phone`.
6. Mỗi `policy` → một `UserAccessScope(educationUnitId=policy.scopeId, role=roleGroup, modules="a,b")`; `scopeType` của policy **không được lưu** ở đây.
7. Gửi `mail.sendTeacherInvite(email, name, credential)` **luôn luôn** (`:86`), kể cả khi `ONE_TIME_PASSWORD`; ghi `user_activities` `TEACHER_ACCOUNT_PROVISIONED`. Trả mật khẩu tạm trong response chỉ khi `ONE_TIME_PASSWORD`.
- `reset` (`:95-114`): đặt mật khẩu tạm mới, `mustChangePassword=true`, thu hồi mọi phiên (`credential_reset`), gửi lại mail, ghi `TEACHER_INVITE_RESENT`/`TEACHER_CREDENTIAL_RESET`.
- Tạo nhân sự (`POST /api/v1/admin/users`, `EmployeeManagementService.createEmployee`, `:115-149`): kiểm `employeeCode` duy nhất trong tenant, gọi `createUser` với modules cố định `["lms"]` (`:127-134`), sau đó **thêm** `ScopedRoleAssignmentService.assign` cho từng policy (role theo scope), rồi upsert `employee_profiles`, ghi `EMPLOYEE_PROFILE_CREATED`.

### 3.9 Cập nhật trạng thái / xóa user

`UserLifecycleUseCase` (`M:users/internal/lifecycle/UserLifecycleUseCase.java`): status hợp lệ `ACTIVE|PENDING|BLOCKED|BANNED|INACTIVE` (`:68-69`, **không** có `INVITED`); chuyển sang `BLOCKED/BANNED/INACTIVE` thu hồi mọi phiên (`status_changed_<x>`, `:53-57`). Ghi `EMPLOYEE_STATUS_CHANGED` chỉ ở đường đơn lẻ (`:58-64`); `bulkStatus` không ghi audit. `delete`/`bulkDelete` là **xóa cứng** (`users.delete`, `:46, 51-52`).

### 3.10 Đổi mật khẩu — `PUT /api/v1/users/me/password`

`UserPasswordUseCase.change` (`:42-65`): so mật khẩu cũ (sai → `BadRequest "invalid old password"`), băm mật khẩu mới (validate `@Size(min=8,max=128)` ở `UserRequests.java:32`, không có luật độ phức tạp), `mustChangePassword=false`, thu hồi **các phiên khác** (giữ phiên hiện tại nếu biết `sessionId`), ghi `ACCOUNT_PASSWORD_CHANGED`.

### 3.11 Gán role & override quyền cho user

- **Role trực tiếp (bảng `user_roles`)**:
  - `POST /api/v1/users/{id}/roles` (`UserRoleAssignmentUseCase.assignRoles`, `:48-55`): **thay thế toàn bộ** tập role theo tên; qua `ProtectedRolePolicy`; gọi `AuthorizationMutationAuditService.authorizationChanged` (tăng `authz_version`).
  - `DELETE /api/v1/users/{userId}/roles/{roleId}` (`AuthorizationDirectRoleUseCase.remove`, `:26`) và `POST /api/v1/users/roles/bulk` (`:27`, thêm một role cho nhiều user, kiểm tất cả user thuộc tenant, 404 nếu thiếu).
- **Override quyền (`user_permissions`)**:
  - `POST /api/v1/users/{id}/permissions` (`UserRoleAssignmentUseCase.assignPermissions`, `:56-62`): `grantType` ∈ {GRANT, DENY}; **xóa toàn bộ override cũ của user** (`overrides.deleteByUserId`, `:58`) rồi tạo mới mọi permission với cùng grantType.
  - `POST` thêm một override đơn lẻ qua `AuthorizationPermissionOverrideUseCase.add` (`:41-60`) và `DELETE /api/v1/users/{userId}/permissions/{overrideId}` (`:62-73`); đều tăng `authz_version`. Controller `AuthorizationController` hiện **không** có endpoint POST add-override (chỉ DELETE, xem §5.3) — `add` chỉ được gọi từ facade/test.
- **Role theo phạm vi** `POST /api/v1/users/{userId}/role-assignments`, `DELETE .../{assignmentId}` (`ScopedRoleAssignmentService`):
  - assign (`:56-101`): user phải thuộc tenant; role tìm theo id hoặc tên; `assertCanGrant`; `scopeType` ∈ {SYSTEM,TENANT,CENTER,SCHOOL,CLASS}; scopeId bắt buộc trừ SYSTEM/TENANT; `expiresAt` phải ở tương lai; actor bắt buộc; tạo `UserRoleAssignment`, tăng `authz_version`.
  - revoke (`:103-141`): idempotent (đã thu hồi thì trả nguyên trạng, không đổi version); lưu `revokedBy`, `revokeReason` (mặc định "revoked by administrator").
  - Unique partial `uq_user_role_assignments_active_scope` chống trùng gán đang hiệu lực (V59).
- **Preview** `POST /api/v1/users/{id}/preview`: mô phỏng thêm `grant` (string) / bỏ `deny` (string) trên quyền hiệu lực — chỉ hỗ trợ **một** quyền mỗi khóa (`AuthorizationAccessQueryUseCase.java:20`).

### 3.12 Định nghĩa Role — `/api/v1/roles`

`AuthorizationRoleDefinitionUseCase` (`:28-41`):
- `create`: tên không được bắt đầu `system.` (`ProtectedRolePolicy.assertRoleDefinitionAllowed`); `isDefault=false`; quyền resolve theo tên (thiếu → lỗi); ghi `ROLE_CREATED`.
- `update` (PUT/PATCH): role bảo vệ (`system.*`) chỉ sửa được khi actor có `*` (`assertCanManageDefinition`); đổi tên cũng bị chặn vào namespace `system.`; sau khi lưu → `authorizationChanged` cho mọi user bị ảnh hưởng (role trực tiếp + gán theo phạm vi) → tăng `authz_version` của họ.
- `delete`: tương tự; xóa cứng role.
- `list`/`get` ẩn role `system.god_mode` (`:28-29`); list cache `authorizationRoles` key `'all'`; tạo/sửa/xóa `@CacheEvict(allEntries)`.

### 3.13 Bootstrap phiên — `GET /api/v1/sessions/current` (alias `/api/v1/session/current`) & `/api/v1/sessions/context`

`SessionService.currentBootstrap` (`M:service/sessions/SessionService.java:103-146`) cho frontend dựng UI:
1. Phiên phải active (chưa thu hồi, chưa hết hạn, user `ACTIVE`, `:310-317`) — không xét `accessTokenExpiresAt`.
2. Role + quyền: gộp từ role trực tiếp, áp override (override sau cùng ghi đè; GRANT/ALLOW hoặc rỗng = cấp, còn lại = deny, `:332-372`) — **không** gồm role gán theo phạm vi cho phần quyền (chỉ dùng để tính đơn vị).
3. `canAccessGlobalErg = quyền chứa "*"`. Đơn vị quản lý: toàn cục → top 500 `education_units`; ngược lại gom từ `UserAccessScope` (module chứa `lms`/rỗng/`*`), `UserRoleAssignment` scope SCHOOL/CENTER/SYSTEM/ORGANIZATION, và các trường giáo viên chủ nhiệm/giảng dạy (`classroomRepository.findTeacherManagedSchoolIds`) nếu role giống teacher; admin đơn vị (center_admin/school_admin/principal/lms.admin) mở rộng sang toàn bộ đơn vị con (BFS có chặn 500, `:238-256`). Vượt 500 đơn vị hoặc 500 lớp → `BadRequest` "chọn phạm vi hẹp hơn".
4. Scope hiện tại: dựa trên header `X-Current-*` (qua `UserAccessContext.from`, đã kẹp vào tập được phép) → lớp / đơn vị / fallback đơn vị đầu / `system` / `none` (`:416-452`).
5. Trả `workspaces` (canonical) và `portals` (legacy) tính từ role + quyền.
`SessionController` (`:47-81`): `X-Session-ID`/`X-Tenant-ID` nếu gửi phải khớp principal, nếu không → 401; tenant luôn lấy từ principal.

### 3.14 Luồng xác thực mỗi request (Bearer)

`ErgAuthenticationFilter.doFilterInternal` (`:52-74`):
1. BFF bật mà gửi Bearer → 401 `BFF_COOKIE_AUTH_REQUIRED`.
2. Có `Authorization: Bearer` (hoặc header tin cậy khi bật cho test): nếu không gửi `X-Tenant-ID` → `ErgPrincipalService.resolveTenantId` gọi hàm SQL `public.erg_resolve_session_tenant(hash)` (SECURITY DEFINER, V100) để suy tenant từ session (chỉ phiên chưa thu hồi, chưa hết hạn, access token còn hạn; trả NULL nếu nhiều tenant trùng hash); nếu có `X-Tenant-ID` thì chỉ chấp nhận khi khớp tenant đã resolve (`ErgPrincipalService.java:69-72`).
3. Nạp phiên + user + role + permission + override + accessScopes (`findPrincipalByAccessTokenHashAndTenantId`, entity graph), phiên phải `active` (`:177-190`: chưa thu hồi, session chưa hết hạn, access token chưa hết hạn, user `ACTIVE`).
4. Dựng `ErgPrincipal` (`:192-214`): roles (trực tiếp + gán theo phạm vi), quyền (role + override, lowercase) rồi thêm quyền của role theo phạm vi và **bỏ chúng khỏi `denied`** (`:346-360`), `accessLevel = "admin"` nếu có `*`, thuộc tính scope (`managedEducationUnitIds`: `*` nếu có wildcard, ngược lại từ access_scopes+assignments CENTER/SCHOOL; `managedClassIds` từ assignments CLASS), cờ `lifecycleRestricted`.
5. `canAccessRequestedPortal`: portal suy từ đường dẫn (`SecurityUtils.portalFromPath`, `:51-64`) phải có workspace tương ứng, ngược lại 403 "Portal session does not match requested API."
6. Chặn tài khoản `lifecycleRestricted` ngoài danh sách onboarding; đặt `Authentication` với authorities `role:*` / `perm:*`.
Sau đó `SecurityConfig` áp luật route (§4.5), `TenantContextFilter` (LOWEST_PRECEDENCE) đẩy tenant/user/portal vào `TenantContext`, và `TenantGucApplier` đặt `app.tenant_id`, `app.user_id`, `app.portal`, `app.maintenance_mode` bằng `set_config(..., true)` (transaction-local) — chính sách RLS đọc các GUC này.

### 3.15 BFF OIDC (khi `erg.auth.bff-enabled=true`)

- `GET /api/v1/auth/login?portal=&returnTo=` (`OidcBffController.java:60-86`): làm sạch portal (mặc định `lms`) và `returnTo` (chỉ đường dẫn nội bộ `/...`, cấm `//`, `:143-151`), lưu vào HTTP session, redirect 302 `/oauth2/authorization/erg-api`. Nếu portal là alias cũ (`lcms`, `lms`, `elearning`, `crm`) → thêm header `Deprecation`/`Sunset`/`Link`, và **sau ngày sunset 2026-10-31 trả `410 GONE`** (`LegacyNamespacePolicy.java:13-18, 42-44`).
- `GET /api/v1/auth/session` → `BffSessionResponse` (user, tenant, roles, permissions, denied, portals, workspaces, accessLevel, profile đã lọc khóa nhạy cảm, scope). `GET /api/v1/auth/csrf` trả header+token CSRF (cookie `XSRF-TOKEN`, `Secure`, `SameSite=Lax`, `SecurityConfig.java:51-57`).
- `OidcPrincipalBridgeFilter` (`:62-98`): tenant từ claim `tenant_id|tenantId|tenant` hoặc `erg.auth.bff-tenant-id` (mặc định `erg`); `X-Tenant-ID` nếu có phải khớp; tìm user theo `preferred_username`/email trong tenant; user phải `ACTIVE`; `OidcMfaPolicy` yêu cầu MFA (`amr` ∈ {mfa,otp,totp,webauthn,passkey,hwk,swk} hoặc `acr` chứa `mfa`/`loa|aal ≥2`) cho role chứa `admin/teacher/instructor/employee/manager/staff` (→ 403 `MFA_REQUIRED`). Principal dựng ở đây chỉ gồm role trực tiếp + override; **không** có role gán theo phạm vi, không cờ lifecycle (xem §9).

---

## 4. Quy tắc nghiệp vụ, validation, phân quyền

### 4.1 Quyền hiệu lực (khi login/refresh/principal)

- Login (`AuthLoginUseCase.java:316-356`): hợp của quyền từ role trực tiếp + role gán theo phạm vi còn hiệu lực + override; override `deny` loại quyền khỏi tập, `grant` thêm vào, **áp theo thứ tự lặp, sau cùng**.
- Request (`ErgPrincipalService.java:247-281, 346-360`): quyền role + override (deny: thêm vào `denied` và gỡ khỏi `granted`; grant: gỡ `denied`); sau đó **quyền từ role theo phạm vi được cộng thêm và gỡ khỏi `denied`** (phạm vi không giới hạn quyền, chỉ ảnh hưởng `managed*Ids`).
- Quyền được so sánh lowercase ở principal; tập trả lúc login giữ nguyên chữ.

### 4.2 Quyết định allow/deny — `ErgPolicyService`

- `decide` (`:27-43`): `allowed = portalAllowed && tenantAllowed && granted`; `granted = !denied && (permission ∈ {profile.self} || khớp grant)`.
- `profile.self` là quyền nền cho **mọi** principal đã xác thực (`:25`).
- Khớp wildcard (`:92-103`): `*`, `x.*` (tiền tố), `*.read` (hậu tố). Riêng `*` chỉ có tác dụng khi `isPlatformWide` (`AuthorizationCapabilityPolicy.java:49-60`): phải có role `system.super_admin`/`erg_super_admin`/`global_admin` **và** quyền `*` **và** không bị deny `*`.
- Alias (`:13-21`): `roles.read→rbac.role.read,rbac.permission.read`; `roles.assign→rbac.role.assign,rbac.override.manage`; `content.manage→content.*,curriculum.*,cms.*,courses.*,media.*`; `system.logs→audit.read`... (mở rộng đệ quy tối đa 5 cấp).
- `isAllowedAny`: nếu **bất kỳ** quyền trong tập bị deny thì từ chối trước (deny thắng) (`:54-70`).
- `tenantMatches`: tenant yêu cầu phải bằng tenant của principal (`AuthorizationCapabilityPolicy.java:30-36`).

### 4.3 Role bảo vệ (`ProtectedRolePolicy`)

- Tên role bắt đầu `system.` là "protected" (`:15, 66-69`).
- `system.god_mode` không thể gán qua luồng thường (403, "requires step-up authentication", `:27-34`).
- Gán role bảo vệ khác (vd `system.super_admin`, `system.admin`) yêu cầu actor có `*` trong permissions (`assertPlatformWildcard`, `:49-60`) — lưu ý chỉ kiểm quyền `*`, không kiểm role system.
- Tạo/đổi tên role vào namespace `system.` bị cấm (`:17-25`); sửa/xóa định nghĩa role bảo vệ cần `*`.
- Được áp dụng: tạo user, `assignRoles` (UserRoleAssignmentUseCase), `AuthorizationDirectRoleUseCase` (assign/remove/bulk), `ScopedRoleAssignmentService.assign`, `RoleDefinitionUseCase` (create/update/delete). **Không áp dụng**: `ScopedRoleAssignmentService.revoke`, `assignPermissions`/`addPermissionOverride` (cho phép cấp quyền bất kỳ trong catalog), `UserLifecycleUseCase` (đổi trạng thái/xóa) — xem §9.

### 4.4 Workspace (ADMIN / TEACHER / STUDENT / CRM)

`WorkspaceCapabilityMapper.canonicalWorkspacesFromVerifiedGrants` (`:55-71`): nếu có role hệ thống + `*` (không bị deny) → tất cả; ngược lại suy từ token role/permission: ADMIN ← `admin|administrator|super_admin|...|lms_admin|workspace.admin` hoặc tiền tố `admin.|lcms.|rbac.|content.|curriculum.|media.`; TEACHER ← `lms|teacher|instructor` hoặc `lms.|teacher.|instructor.`; STUDENT ← `student|learner|elearning` hoặc `elearning.|student.`; CRM ← `crm|crm_manager` hoặc `crm.`. Quyền bị deny cùng token sẽ gỡ workspace; deny `*`/`workspace.*` xóa hết (`:115-124`).
Ánh xạ portal cũ: `admin,lcms→ADMIN`, `lms→TEACHER`, `elearning→STUDENT`, `crm→CRM` (`:13-22`). Đường dẫn `/api/v1/admin*`, `/api/v1/users*` (trừ `/users/me*`) → yêu cầu workspace `admin` (`SecurityUtils.java:51-64`).

### 4.5 Luật route trong `SecurityConfig` (rút gọn, thứ tự khớp từ trên xuống)

- Public: `OPTIONS /**`; Prometheus (`/actuator/prometheus` GET chỉ khi `erg.observability.prometheus-public-enabled=true`); health/ready/metrics/api-docs/scalar/robots/sitemap; `POST /webhooks/discord|telegram`; `POST /api/v1/auth/login|refresh`; `GET /api/v1/auth/login`, `/oauth2/**`, `/login/oauth2/**`; GET công khai CMS (`pages, menus/structure, posts, courses, elearning/categories|levels, recruitment/jobs, reviews, public-disclosure, trending`); `POST recruitment/apply, reviews, reviews/*/helpful`; `PUT /api/v1/lcms/media/upload/quiz-media/**` (`:98-130, 170`).
- GET `posts/hidden|trash|preview/**` cần `cms.posts.update|delete|*`; `reviews/admin*` chỉ cần authenticated.
- `POST /api/v1/graphql` → authenticated (phân quyền chi tiết ở resolver).
- RBAC: GET `/roles/**,/permissions/**,/permission-groups/**` ← `rbac.role.read`; POST/PUT/PATCH/DELETE `/roles` ← `rbac.role.create|update|delete`; `/users/*/roles/**`, `/users/*/role-assignments/**`, `/users/*/permissions/**`, `/users/roles/bulk` ← `rbac.role.assign`; GET `/users/*/effective-permissions|permission-overrides` ← `rbac.permission.read` (`:132-145`).
- Self-service: `/users/me` GET ← `account.self.read|profile.self`; PATCH ← `account.self.update|profile.self`; recovery-email ← `account.self.recovery_email.update|profile.self`; password ← `account.self.password.change|profile.self`; sessions ← `account.self.sessions.read|...`; còn lại `/users/me/**` ← `profile.self` (`:146-159`).
- `/api/v1/users`, `/api/v1/users/**`, `/api/v1/admin/users/**` ← `users.manage` (`:160-163`).
- Khác: CRM (`crm.analytics.read`, `crm.school.read`, `crm.deal.manage`), AI content (`api-keys.manage`, `ai-content.use`), content/curriculum (`content.*`, `lms.resource.*`), `/api/v1/operations/**` & `/api/v1/audit/**` ← `operations.manage`, `/migration/**,/platform/**` ← `platform.read`, crawler ← `crawler.manage`, notifications, elearning ← `elearning.manage`, seo, cms posts/categories, courses, documents, public-disclosure; `/api/v1/**` còn lại chỉ cần authenticated; `anyRequest().denyAll()` (`:252-253`).
- Chế độ BFF: CSRF cookie, session `IF_REQUIRED`, oauth2Login; ngược lại CSRF tắt và session STATELESS (`:50-78`).
- Lỗi chuẩn hóa qua `SecurityErrorResponseWriter` (envelope `ApiResponse.failure`, header `X-Request-ID`, `Cache-Control: no-store`): 401 `AUTHENTICATION_REQUIRED`/`AUTH_SESSION_REPLACED`, 403 `ACCESS_DENIED`.

### 4.6 Quyền theo annotation (method security) — các package `*/authorization`

Mỗi annotation = `@PreAuthorize("@methodAuthorization.isAllowedAny(authentication, <perm...>)")`; `MethodAuthorizationEvaluator` yêu cầu principal có tenant, portal=null, rồi gọi `ErgPolicyService.isAllowedAny`.

| Nhóm | Annotation → quyền chấp nhận |
|---|---|
| Tài khoản/RBAC (`authorization/authorization`) | `@UsersManage`: `users.manage,*`; `@RbacRoleRead|Create|Update|Delete|Assign`: `rbac.role.read|create|update|delete|assign` + `*`; `@RbacPermissionRead`: `rbac.permission.read,*`; `@UserSelfRead`: `account.self.read,profile.self,*`; `@UserSelfUpdate`: `account.self.update,profile.self,*`; `@UserSelfPasswordChange`: `account.self.password.change,profile.self,*`; `@UserSelfSessionsRead`: `account.self.sessions.read,account.self.read,profile.self,*`; `@CmsEditorialRead`: `cms.posts.update,cms.posts.delete,*`; `@AuthenticatedEndpoint`: `isAuthenticated()` |
| Audit | `@AuditRead`: `audit.read,audit.manage` (không có `*`); `@AuditExport`: `audit.export,audit.manage` |
| Calendar (`calendar/authorization`) | Create/Update/Delete/Import/Publish/Read: `lms.schedule.<hành động>` hoặc `lms.schedule.manage`, `lms.manage`, `*`; `CalendarSelfRead`: thêm `lms.schedule.read_self` |
| LMS (`lms/authorization`) | `lms.assignment.{create,update,delete,assign,read,read_self,submit,progress.read}`, `lms.class.read`, `lms.grade.read`, `lms.quiz.read`, `lms.resource.read` (+`content.resource.read,content.manage`), `lms.student_group.{create,update,delete}`; nhiều cái chấp nhận thêm `lms.manage,*` |
| LCMS (`lcms/authorization`) | `lms.class.manage`, `lms.enrollment.manage`, `lms.quiz.{create,read,publish}`, `lms.exercise.{create,update,delete,read}`, `lms.report.read`, `lms.school.{create,read,update,archive}`, `lms.scope.read`, `lms.student.manage`, `lms.subject.manage` (+`curriculum.manage`), workspace read (`content.manage`) |
| E-learning | `elearning.dashboard.read`, `elearning.announcement.read_self`, `elearning.discussion.write_self` (+`*`) |
| Learning outcomes / standards | `learning.outcomes.{read,manage,issue_certificate}`, `learning.standards.{read,manage,launch}` (manage bao các quyền con) |
| Enterprise connectors | `enterprise.connectors.{read,sync,manage}` |
| Content lifecycle | `content.manage,curriculum.manage,content.lifecycle.{submit,approve,publish,archive,rollback},lms.quiz.publish` (không có `*` trong danh sách) |
| Reporting | `lms.report.read,lms.grade.read,lms.manage,*` |

### 4.7 Validation chính

- `LoginRequest`: `identifier` `@NotBlank @Size(max=254)`, `password` `@NotBlank` (`M:common/request/auth/AuthRequests.java:11-19`); `RefreshRequest.refreshToken` `@NotBlank`.
- `OnboardingRequest.fullName` `@NotBlank 2..100`; `UpdateProfileRequest.fullName 2..100`; phone ≤ 20; `ChangePasswordRequest.newPassword 8..128`; `RecoveryEmailVerifyRequest.otp` đúng 6 ký tự; `AdminCreateUserRequest`: `primaryEmail @Email`, `roleGroupIds` & `policies` không rỗng; `CreateEmployeeRequest` bắt buộc `profile` + `policies` (`EmployeeDtos.java:38-47`); `experienceYears >= 0`.
- Phân trang: user list mặc định 20, tối đa 100, trang bắt đầu từ 1 (`UserQueryUseCase.java:62-63`); catalog quyền trang bắt đầu **từ 0**, mặc định 25, tối đa 100 (`AuthorizationPermissionCatalogUseCase.java:25`); feed bảo mật hợp nhất giới hạn `page*limit ≤ 2000` (`EmployeeSecurityActivityService.java:40, 237-243`).
- Isolation: mọi lookup user dùng `findByIdAndTenantId` (404 nếu khác tenant); test `AuthorizationServiceSecurityTest` kiểm tra chặn user ngoại tenant trước khi mutate.

---

## 5. API

### 5.1 REST — Auth (`M:controller/auth/AuthController.java`, bật khi `erg.auth.local-login-enabled` (mặc định true))

| Method | Path | Mô tả |
|---|---|---|
| POST | `/api/v1/auth/login` | Đăng nhập (§3.1) |
| POST | `/api/v1/auth/refresh` | Xoay refresh token |
| POST | `/api/v1/auth/logout` | Thu hồi phiên hiện tại |
| POST | `/api/v1/auth/logout-all` | Thu hồi mọi phiên |
| GET | `/api/v1/auth/login-attempts?email=` | 100 lần thử gần nhất của chính user (tham số `email` chỉ dùng khi không có userId) |
| GET | `/api/v1/auth/security/ip-status?email=` | Thống kê thất bại theo IP/email |

BFF (`OidcBffController`, khi `erg.auth.bff-enabled=true`): `GET /api/v1/auth/login`, `GET /api/v1/auth/session`, `GET /api/v1/auth/csrf`; logout do Spring Security.

### 5.2 REST — Users (`M:controller/users/UserController.java`, `/api/v1/users`)

Self: `GET /me`, `PATCH /me`, `POST /me/recovery-email/challenges`, `POST /me/recovery-email/verify`, `POST /onboarding`, `PUT /me/password`, `GET /me/sessions`, `DELETE /me/sessions/{sessionId}`.
Admin (`@UsersManage`): `GET ""|"/"` (page, limit, search, status, role), `POST ""` (tạo user), `POST /{userId}/resend-invite`, `POST /{userId}/reset-credential`, `PUT /bulk/status`, `DELETE /bulk`, `GET /{userId}`, `GET /{userId}/activity`, `PUT /{userId}/status`, `POST /{userId}/roles`, `POST /{userId}/permissions`, `DELETE /{userId}`.

### 5.3 REST — Admin nhân sự (`M:controller/users/AdminEmployeeController.java`, `/api/v1/admin/users`, `@UsersManage`)

`GET ""` (danh sách nhân sự kèm profile, số thiết bị hoạt động, nền tảng, rủi ro), `GET /security-feed` (userId, sourceType ∈ SECURITY_EVENT|LOGIN_ATTEMPT|USER_ACTIVITY|SESSION), `POST ""` (tạo nhân sự), `GET /{userId}`, `PATCH /{userId}` (fullName, phone, profile), `PUT /{userId}/status`, `POST /{userId}/roles`, `POST /{userId}/reset-credential`, `GET /{userId}/activity`, `GET /{userId}/security-events`, `GET /{userId}/sessions`, `POST /{userId}/sessions/revoke` (body `{sessionId}`).

### 5.4 REST — Sessions & Profiles

- `GET /api/v1/sessions/current` (+alias `/api/v1/session/current`) — bootstrap (§3.13); `GET /api/v1/sessions/context` — thông tin phiên (`SessionController`).
- `/api/v1/profiles` (`M:controller/profiles/ProfileController.java`): `GET /{userId}`, `GET /me`, `POST` (tạo; body có `userId`), `PUT /{userId}`, `DELETE /{userId}`. Kiểm tra sở hữu: `userId == principal.userId` hoặc `users.manage` (`:68-73`) — **trừ `POST`**.

### 5.5 REST — Authorization (`M:controller/authorization/AuthorizationController.java`, `/api/v1`)

`GET /roles`, `GET /roles/{roleId}`, `POST /roles`, `PUT|PATCH /roles/{roleId}`, `DELETE /roles/{roleId}`, `GET /permissions`, `GET /permissions/catalog` (page, limit, search, group, portal, status, used), `GET /permissions/{permissionId}`, `GET /permission-groups`, `DELETE /users/{userId}/roles/{roleId}`, `POST /users/roles/bulk`, `POST /users/{userId}/role-assignments`, `DELETE /users/{userId}/role-assignments/{assignmentId}`, `DELETE /users/{userId}/permissions/{overrideId}`, `GET /users/{userId}/effective-permissions`, `GET /users/{userId}/permission-overrides`, `GET /users/{userId}/feature-config`, `GET /me/feature-config`, `POST /users/{userId}/preview`.

### 5.6 GraphQL

Trong phạm vi chỉ có `lcms.accessManagementWorkspace(input: AccessManagementWorkspaceInput)` (schema `R:graphql/modules/30-lms.graphqls:60-67, 478+`; resolver `M:controller/lcms/graphql/LcmsWorkspaceGraphQlController.java:50-66`, `@LcmsScopeRead`): yêu cầu `lms.scope.read`/`*`; `tenantId` đầu vào phải bằng tenant principal (`GraphQlAccessSupport.verifiedTenantId` → Forbidden nếu lệch); dữ liệu qua `UserRepository.findAccessManagementRows` (tìm kiếm tên/email/phone, lọc status & roleId, tối đa 50/trang), kèm `roleOptions`, `scopeOptions`; danh sách quyền hiệu lực chỉ trả khi principal có quyền đọc và không bị deny `*`/`rbac.permission.read`/`users.manage`. Không có mutation/query GraphQL riêng cho login/role/session.

### 5.7 Prototype (không phải production)

`M:controller/operations/AnalyticsOperationsController.java` — `@Profile("prototype")`: `/api/v1/insight/*`, `/api/v1/audit/logs[/{id}]`, `/api/v1/operations/{system-status,logs,configs,firewall/*}` đều đọc/ghi vào `ModuleWorkflowService` (map in-memory theo process, `@Profile("prototype")`, kiểm `ProcessLocalStatePolicy`). `firewall/check/{ip}` luôn trả `blocked:false`, `firewall/block|unblock` chỉ "accepted" mà không chặn gì.

---

## 6. Dữ liệu

| Bảng | Entity | Điểm chính |
|---|---|---|
| `users` | `User` | V1 + V43 (thêm `username`, unique `lower(username)` toàn cục) + V51 (`must_change_password`, `recovery_email`, `recovery_email_verified`, unique `lower(recovery_email)`) + V61 (`authz_version bigint default 1`) + V64 (index `lower(username)`,`lower(email)` cho login) + V56 (`(tenant_id,id)`, `(tenant_id,created_at desc)`). `status` text tự do: ACTIVE/INVITED/PENDING/BLOCKED/BANNED/INACTIVE. Không thấy unique email ở V1; chỉ có kiểm tra tồn tại ở tầng service (chưa xác minh index unique email nếu ở migration khác) |
| `roles`, `permissions`, `permission_groups`, `role_permissions`, `user_roles` | `Role`, `Permission`, `PermissionGroup` | **Không có `tenant_id`** (global toàn hệ thống). V56: unique `lower(roles.name)`; V57: thêm `resource,action,portal,supported_scope,is_system,status,deprecated_at` cho permissions, index catalog; V62: `roles.updated_by`; V63 sửa ID RBAC cũ dạng UUID |
| `permission_bindings` | `PermissionBinding` | V57: `(permission_id, surface_type, surface_identifier)` unique; `surface_type ∈ REST|GRAPHQL|ROUTE|SERVICE`; `enforced` |
| `user_permissions` | `UserPermissionOverride` | override per-user: `permission` (tên, không FK id), `grant_type` GRANT/DENY, `expires_at`; index theo `user_id` (V56) |
| `user_role_assignments` | `UserRoleAssignment` | V59 (+V108 nới độ rộng id): scope SYSTEM/TENANT/CENTER/SCHOOL/CLASS, `expires_at`, `revoked_*`; unique partial active theo `(user_id, role_id, tenant_id, scope_type, coalesce(scope_id,''))`; FK `ON DELETE CASCADE` cho user và role |
| `user_access_scopes` | `UserAccessScope` | `(user_id, education_unit_id, modules, role)`; **không có tenant_id**; chỉ index, không unique |
| `user_sessions` | `AuthSession` | Lưu **hash** access/refresh token; index `(session_id,tenant_id)`, `(access_token_hash,tenant_id)`, `(refresh_token_hash,tenant_id)`, `(user_id,tenant_id,revoked_at)`, theo fingerprint (V35), platform slot (V58), `previous_refresh_token_hash` (V70), `access_token_expires_at` (V78). Cột `platform` ∈ WEB_DESKTOP/WEB_MOBILE |
| `refresh_token_history` | `RefreshTokenHistory` | V71: `token_hash` UNIQUE, index session & `consumed_at` (retention) |
| `auth_login_attempts` | `AuthLoginAttempt` | index theo (user,created), (email,created), (ip,result) và V35 (email,result,created desc), (ip,result,created desc) |
| `security_events` | `SecurityEvent` | V44 + V60 (`platform,result,risk_code,session_id,metadata jsonb`); index tenant-user-time, risk |
| `user_activities` | `UserActivity` | audit hoạt động tài khoản; V53 thêm `request_id`, `metadata jsonb` |
| `account_verification_codes` | `AccountVerificationCode` | V51 (+V109 nới `user_id`); unique active `(user_id,purpose)` |
| `profiles` | `Profile` | 1-1 với user (hồ sơ giáo viên mở rộng: triết lý giảng dạy, chuyên môn, rating, ghi chú nội bộ) |
| `employee_profiles` | `EmployeeProfile` | V60: unique `(tenant_id,user_id)` và `(tenant_id,employee_code)`; manager FK `ON DELETE SET NULL`; check kinh nghiệm ≥ 0, `terminated_at ≥ joined_at` |
| `certificates` | `Certificate` | thuộc user (chủ yếu dùng bởi learning-outcomes) |
| `system_configs`, `firewall_rules` | `SystemConfig`, `FirewallRule` | **không có repository/service sử dụng trong code** (dead entity) |

RLS: V75 bật `ENABLE ROW LEVEL SECURITY` (không FORCE) cho mọi bảng có cột `tenant_id` (gồm `users`, `user_sessions`, `user_role_assignments`, `security_events`...) với chính sách theo GUC `app.tenant_id` / `app.maintenance_mode`; vai trò runtime `erg_app` không được là owner/BYPASSRLS (V81 assertion). Hàm `public.erg_resolve_session_tenant` (V100, SECURITY DEFINER) cho phép tra tenant khi chưa có GUC.
`authz_version`: tăng mỗi khi quyền thay đổi (`AuthorizationMutationAuditService.applyBatch`, `:79-122`, khóa dòng `lockAuthzVersions`); hiện chưa thấy cơ chế cache nào đọc giá trị này trong phạm vi đã đọc (chưa xác minh).

---

## 7. Tích hợp ngoài và sự kiện

- **Email**: `AccountMailSender` có hai cài đặt. `TransactionalAccountMailSender` (`@Primary`, `M:service/auth/TransactionalAccountMailSender.java:31-55`) ghi `OutboxEvent` loại `ACCOUNT_TEACHER_INVITE` / `ACCOUNT_RECOVERY_CODE` trong cùng transaction (idempotency key = `recipient:sha256(secret)`; secret được mã hóa bằng `OutboxPayloadCipher`); worker `M:service/outbox/TransactionalOutboxWorker.java:69-71` gửi thật (SMTP qua `SmtpAccountMailSender`, `from` lấy `erg.mail.from`/`spring.mail.username`, mặc định `noreply@erg.edu.vn`). Nội dung mail tiếng Việt, mật khẩu tạm/OTP nằm trong thân mail.
- **Redis** (tùy chọn): bộ đếm rate-limit đăng nhập (`GraphQlRateLimitCounterRouter`), fallback bộ đếm local.
- **Audit tập trung**: `AuthorizationMutationAuditService` gọi `AuditOperations.record(...)` (audit log bất biến V93) với action `AUTHORIZATION_CHANGED`/`AUTHORIZATION_ROLE_CHANGED`; idempotency key = băm 32-bit `String.hashCode()` (`:244-258`).
- **Cache**: `@Cacheable` `authorizationRoles` (key `'all'`), `authorizationPermissions` (`'all'`, `'groups'`) — khóa không chứa tenant. Không có cache principal/token trong phạm vi (mỗi request tra DB).
- **Postgres session GUC**: `TenantGucApplier` (`app.tenant_id|user_id|portal|maintenance_mode`).
- **Metrics**: counter `erg.auth.login.count{result}`, `erg.auth.legacy_workspace_alias.requests`.
- **OIDC/Keycloak**: client registration `erg-api`, cấu hình trong `R:application-enterprise-auth.yml` (chưa đọc chi tiết); test hợp đồng `KeycloakInternalRealmContractTest`.
- **LTI 1.3**: `M:learningstandards/internal/security/RsaLtiLaunchTokenVerifier.java` (nằm trong gói `security` của learningstandards): xác thực JWT RS256 bằng public key do tenant đăng ký (kiểm `iss`, `aud`, `deployment_id`, `nonce`, `exp`, `iat` lệch ≤ 60s, `target_link_uri` https + host allowlist); không tải JWKS từ xa.
- **Nginx/Prometheus/Webhook**: các test hợp đồng `NginxSecurityConfigContractTest`, `PrometheusAccessRequestMatcherTest`, `WebhookSecurityBoundaryTest`.

---

## 8. Test hiện có

Đã đọc tên/ý định của các test chính (không đọc toàn bộ thân từng test):
- **Auth** `T:service/auth/AuthServiceTest.java` (≈20 case): login thành công/lưu hash, refresh xoay + replay (previous và history), role `super_admin` không kèm `*` không được all-portal, quyền scoped role trả về, login bằng username nội bộ, portal chỉ là gợi ý, PENDING/BANNED/INACTIVE bị từ chối, INVITED vào phiên hạn chế, cùng trình duyệt nhiều portal, đổi IP không thu hồi, trình duyệt+IP mới thu hồi, lần thay thế thứ 5 chỉ cảnh báo, thất bại lặp lại không khóa, failure ghi transaction độc lập, lock timeout → 409, logout/logout-all, login attempts, ip-status. `AuthRefreshConcurrencyIntegrationTest` (khóa refresh), `AuthHttpLifecycleIntegrationTest` (envelope lỗi, không lộ tồn tại tài khoản), `AuthLoginRateLimiterTest`, `LoginFailureEnforcementServiceTest`, `RefreshTokenHistoryRetentionJobTest`, `AccountVerificationServiceTest` (2 case: hash OTP + tiêu thụ; email trùng), `TransactionalAccountMailSenderTest`, `LoginIdentityLookupIndexMigrationContractTest`, kiểm kiến trúc `Auth*ArchitectureTest`.
- **Users** `UserServiceTest` (≈25 case: profile, onboarding, đổi mật khẩu, phiên, tạo user nguyên tử + scope, role bảo vệ, reset credential, ban thu hồi phiên, bulk, thay thế role/permission), `EmployeeManagementServiceTest`, `EmployeeSecurityActivityServiceTest`, contract test controller (`UserControllerContractTest`, `AdminEmployeeControllerContractTest`, `EmployeeSecurityFeedContractTest`), `User*BoundaryTest`.
- **Sessions** `SessionServiceTest` (5 case: teacher scope, wildcard, admin workspace, giới hạn 500 lớp, scoped assignments).
- **Authorization** `AuthorizationServiceSecurityTest` (cô lập tenant, protected role, ẩn god_mode), `AuthorizationRoleMutationServiceTest`, `ScopedRoleAssignmentServiceTest`, `AuthorizationMutationAuditServiceTest`, `PermissionCatalogServiceTest`, `RoleCatalogMetadataContractTest`, `LegacyRbacIdRepairMigrationContractTest`, `AuthorizationControllerMutationContractTest`.
- **Security** (≈45 file trong `T:platform/security`): `SecurityHardeningTest` (header giả mạo bị bỏ qua, Bearer, BFF từ chối Bearer, tenant context, session thay thế, first-login hạn chế không gọi được LMS, header session/tenant giả), `ErgPrincipalServiceTest`, `OidcPrincipalBridgeFilterTest`, `OidcMfaPolicyTest`, `ProtectedRolePolicyTest`, `MethodAuthorizationEvaluatorTest`, `AuthorizationCapabilityPolicyTest`, `WorkspaceCapabilityMapperTest`, `TrustedClientIpResolverTest`, `TenantContextTest`, `TenantGucApplierIntegrationTest`, `TenantRls*IntegrationTest` (cô lập RLS với role không-owner), `GraphQl*Security*Test`, nhiều `*MigrationContractTest` (V51, V59, V60, V61, RLS, permission catalog...), `PublicRoutePolicyContractTest`, `TrustedHeaderAuth`/`InternalOnlyAuthContractTest`, `Nginx...`.

**Khoảng trống rõ ràng (chưa thấy test):**
- `ProfileController`/`ProfileService`: không có test hành vi (chỉ xuất hiện trong `ModulePublicApiContractTest`).
- `AuthorizationPermissionOverrideUseCase` (add/delete) chỉ qua `AuthorizationRoleMutationServiceTest`; `AuthorizationPermissionCatalogUseCase`, `AuthSecurityQueryUseCase` chỉ qua test kiến trúc/dịch vụ gián tiếp.
- Đường `UserLifecycleUseCase.delete/bulkDelete` kiểm tra số lượng nhưng chưa có test về hậu quả FK/phiên đang sống.
- `ScopedRoleAssignmentService.revoke` với role bảo vệ; ma trận route `SecurityConfig` ↔ `@UsersManage` kép (`/users/{id}/roles`).
- Không thấy test tích hợp cho đường login thật dưới RLS với role `erg_app` (xem §10).
- Không có test cho thời điểm sau sunset `2026-10-31` của BFF login (`OidcBffControllerTest` chưa đọc chi tiết).

---

## 9. Rủi ro, nợ kỹ thuật, nghi ngờ bug

1. **Lộ trạng thái tài khoản trước khi xác thực mật khẩu** — `AuthLoginUseCase.java:156-174` trả `ACCOUNT_LOCKED` / `ACCOUNT_DISABLED` / `ACCOUNT_ACTIVATION_REQUIRED` / `EMAIL_NOT_VERIFIED` trước khi so mật khẩu (`:175`): kẻ tấn công chỉ cần biết email để dò tài khoản bị khóa/chưa kích hoạt (user enumeration một phần). Test `unknownLoginUsesGenericInvalidCredentials...` chỉ phủ trường hợp tài khoản không tồn tại.
2. **Nghi ngờ: `POST /api/v1/users/onboarding` không gọi được với user thường** — `SecurityConfig.java:160-161` ép `/api/v1/users/**` phải có `users.manage` (chỉ `/users/me/**` được miễn); filter coi path này thuộc portal `admin` (`SecurityUtils.java:54-56`); và `isOnboardingRequest` (`ErgAuthenticationFilter.java:224-230`) không liệt kê `/api/v1/users/onboarding` nên tài khoản lifecycle-restricted (đối tượng cần onboarding) bị 403 `ACCOUNT_LIFECYCLE_RESTRICTED`. Test controller (`UserControllerContractTest:147`) chỉ test controller độc lập; `GET/PATCH /users/me` có thể là đường thay thế. Cần xác minh với frontend.
3. **Nghi ngờ rò rỉ chéo tenant ở danh mục role/permission**: `roles`, `permissions` là bảng global, cache `authorizationRoles` key `'all'` không có tenant (`AuthorizationRoleDefinitionUseCase.java:28`), và `RoleResponse`/`PermissionResponse` chứa danh sách `userId` từ `role.getUsers()` (mọi tenant) và số user (`...CatalogUseCase.java:29`). Sửa/xóa role của một tenant ảnh hưởng mọi tenant (vi phạm quy tắc "namespace cache theo tenant" trong `AGENTS.md`).
4. **`assignPermissions` xóa sạch override cũ** (`UserRoleAssignmentUseCase.java:58`): một lần gọi với GRANT sẽ làm mất mọi DENY đã có (mô hình hóa "replace"). Ngoài ra không có kiểm tra giới hạn đặc quyền: người có `users.manage` + `rbac.role.assign` có thể cấp bất kỳ permission trong catalog (gồm `*` nếu tồn tại trong bảng `permissions` — chưa xác minh) cho chính mình (không thấy chặn self-grant ở `UserRoleAssignmentUseCase`/`AuthorizationPermissionOverrideUseCase`).
5. **Quyền từ role gán theo phạm vi vô hiệu hóa DENY của user** — `ErgPrincipalService.java:354-357` (`nextDenied.remove(permission)`): nếu cùng quyền được cấp qua `user_role_assignments` thì override DENY biến mất; ngược lại luồng login (`AuthLoginUseCase.java:339-354`) áp DENY sau cùng. Hành vi không nhất quán giữa hai nơi. Các quyền của role theo phạm vi cũng được cộng **toàn cục** (scope không giới hạn quyền, chỉ tính `managed*Ids`).
6. **Không nhất quán định nghĩa "wildcard"**: `AccessScopeAssembler.from` (`:23-25`) trao phạm vi `*` cho bất kỳ principal nào có quyền `*`, trong khi `AuthorizationCapabilityPolicy.isPlatformWide` còn đòi role hệ thống (`:49-60`); `ErgPrincipalService` đặt `accessLevel="admin"` chỉ dựa vào `*` (`:208`). Role có `*` nhưng không phải system role sẽ được quyền dữ liệu `*` nhưng không được khớp quyền `*` ở `ErgPolicyService`.
7. **Role bảo vệ chỉ kiểm tra `*`, không kiểm role** (`ProtectedRolePolicy.java:49-60`); `ScopedRoleAssignmentService.revoke` (`:103-141`), `UserLifecycleUseCase` (khóa/ban/xóa), `UserProvisioningUseCase.reset` (reset mật khẩu) không kiểm tra mục tiêu có phải super admin → người có `users.manage` có thể đặt lại mật khẩu/khóa/xóa tài khoản `system.super_admin` (nghi ngờ, chưa xác minh có chặn ở nơi khác).
8. **Xóa user là xóa cứng, không tăng `authz_version`, không thu hồi phiên rõ ràng** (`UserLifecycleUseCase.java:46, 51-52`); phụ thuộc FK/cascade DB (V12/V16 FK; chưa xác minh `ON DELETE`). `bulkStatus`/`bulkDelete` không ghi audit và duyệt từng id bằng `find` (N+1), một id sai → cả lô lỗi.
9. **Mật khẩu tạm luôn được gửi mail** kể cả khi chọn `ONE_TIME_PASSWORD` (`UserProvisioningUseCase.java:86, 108`) → secret vừa đi mail vừa trả trong response; nội dung mail và outbox mang mật khẩu tạm (được mã hóa trong payload outbox nhưng ở dạng rõ trong email). Mật khẩu tạm = `IdGenerator.newId() + "Aa1!"` — độ ngẫu nhiên phụ thuộc generator ID (UUID v7 theo thời gian, chưa xác minh entropy).
10. **Dò tài khoản/IP qua `GET /auth/security/ip-status`**: bất kỳ user đã xác thực nào có thể truyền email tùy ý để lấy số lần thất bại toàn thời gian (`countByAttemptedEmailIgnoreCaseAndResultIn`, không lọc tenant, `AuthSecurityQueryUseCase.java:45-81`); cũng không lọc theo tenant ở `login-attempts`.
11. **`AuthLoginUseCase.recordLoginAttempt` ghi tenant cố định `erg`** (`:261`) và `LoginFailureEnforcementService` dùng tenant của user hoặc `erg`; nên với user thuộc tenant khác, thành công/thất bại có thể nằm ở tenant sai trong feed bảo mật theo tenant. Tên user/email toàn cục (unique `lower(username)`, `lower(email)` không theo tenant) → đa tenant chưa thực sự hỗ trợ trùng định danh.
12. **Thiết kế rate-limit**: bộ đếm tăng cả với lần đăng nhập đúng (10/phút/tài khoản); không trả `Retry-After` (`AuthLoginUseCase.java:145-147` bỏ `retryAfterSeconds`); local counter chỉ hiệu lực theo từng instance khi không có Redis.
13. **Refresh/replay**: `handleReplay` rồi ném `InvalidToken` trong cùng `@Transactional` — `AuthFailure` kế thừa `ApiException extends RuntimeException` (`M:exception/ApiException.java:5`) và `refresh` là `@Transactional` không có `noRollbackFor` → giao dịch rollback, việc thu hồi phiên + SecurityEvent `REFRESH_TOKEN_REUSE` có thể không được commit (nghi ngờ mạnh, chưa chạy thử; test `reusedPreviousRefreshToken...` là unit test mock nên không phát hiện). Cần xác minh `noRollbackFor` hoặc cấu hình `ApiException`.
14. **Sunset BFF alias đã quá hạn**: `LegacyNamespacePolicy.DEFAULT_SUNSET = 2026-10-31`; `OidcBffController.login` mặc định `portal=lms` (là alias cũ) → từ 2026-11-01 trả `410 GONE` nếu không truyền portal canonical (frontend dùng `admin|teacher|student|crm`?). Chú ý: `PORTALS` trong controller (`:43`) không chứa `teacher`/`student`, nên mọi giá trị khác bị ép về `lms` rồi 410 (nghi ngờ lỗi cấu hình khi hết hạn).
15. **Nhân đôi logic lifecycle/`maskEmail`**: hàm tính `nextSteps` + `maskEmail` lặp ≥ 6 chỗ (`AuthLoginUseCase`, `AccountVerificationService`, `SessionService`, `User*UseCase`...) và còn khác nhau: `AccountVerificationService.lifecycle` (`:185`) không trả `PROVISIONED`. Nợ kỹ thuật dễ lệch hành vi.
16. **Nợ kiến trúc legacy**: 3 facade (`AuthOperations`, `UserOperations`, `AuthorizationCommandOperations`) giữ nhánh `legacy != null`; 3 service `@Deprecated`; setter `@Autowired(required=false)` làm bean "đa trạng thái"; `UserOperations.java` viết nhiều lệnh trên một dòng (khó đọc/diff). Entity `Session`, `SessionUser`, `SystemConfig`, `FirewallRule` không dùng; thuộc tính `erg.lifecycle.auth-bootstrap-admin-on-startup` có trong YAML nhưng không có code đọc (grep không thấy).
17. **`ProfileController.createProfile` không kiểm tra sở hữu** (`:45-49`): bất kỳ user đã xác thực (chỉ cần `/api/v1/**` authenticated) có thể tạo `profiles` cho `userId` bất kỳ; `ProfileService` không lọc tenant (`findById`, `findByUserId` không tenant) và `toResponse` luôn trả `socialLinks` và `specialties` rỗng dù đã lưu (`ProfileService.java:119, 123`) — nghi ngờ bug (dữ liệu ghi nhưng không đọc lại được).
18. **`EffectivePermissionsResponse` (admin) khác quyền thực tế**: `LegacyUserAuthorizationAdapter.snapshot` (`:45-69`) không bỏ override hết hạn, không gồm role gán theo phạm vi → màn hình quản trị có thể hiển thị khác thực tế.
19. **`SessionService.isSessionActive` không xét `accessTokenExpiresAt`** (`:310-317`), trong khi `ErgPrincipalService.active` có (`:177-190`) → nhất quán thấp nhưng không gây hở vì phải qua filter trước.
20. **Phân trang không đồng nhất**: catalog quyền 0-based, user list 1-based; feed hợp nhất `total` cộng tổng của 4 nguồn nhưng cửa sổ sắp xếp chỉ lấy `page*limit` từ mỗi nguồn (`EmployeeSecurityActivityService.java:76-137`) — số trang có thể không khớp dữ liệu thực trả về.
21. **Idempotency key audit dùng `String.hashCode()` 32 bit** (`AuthorizationMutationAuditService.java:256-257`) → nguy cơ va chạm làm bỏ qua bản ghi audit hợp lệ (nghi ngờ, phụ thuộc cơ chế dedupe của `AuditOperations`).
22. **Route/annotation kép**: `POST /users/{id}/roles` bị `SecurityConfig` yêu cầu `rbac.role.assign` (`:142-143`) và còn `@UsersManage` ở method (`UserController.java:272-273`) → cần cả hai quyền; trong khi `/admin/users/{id}/roles` (AdminEmployeeController) chỉ qua `users.manage` ở route (`:162-163`) — hai đường cùng chức năng với quyền khác nhau.
23. **OIDC principal thiếu**: không nạp role gán theo phạm vi, không `lifecycleRestricted`, sessionId là ID HTTP session (không phải `user_sessions`) (`OidcPrincipalBridgeFilter.java:124-155`).
24. **Gói `support` trong phạm vi chủ yếu là code không liên quan danh tính**: `service/support/ModuleWorkflowService` (prototype in-memory) và `service/quiz/support/JsonbSchemaMigrator` (đọc JSONB quiz có `schemaVersion`) — nên được phân loại sang module khác khi viết tài liệu quiz.

---

## 10. Câu hỏi còn mở

1. Đường đăng nhập chưa xác thực chạy với vai trò DB nào? `users` có RLS (V75) nhưng login không đặt GUC `app.tenant_id` (`TenantGucApplier.apply` bỏ qua khi context null) — runtime `erg_app` có đọc được `users` khi login không (có `app.maintenance_mode`/owner/ngoại lệ nào ngoài những gì đã đọc)? Test `legacyUserTableIsIsolatedForANonOwnerRuntimeRole` cần đọc kỹ.
2. `/api/v1/users/onboarding`: frontend (`erg-system`) thực sự gọi đường nào? Có cần whitelist trong `isOnboardingRequest` và route `SecurityConfig` không?
3. Giá trị `portal` canonical mà frontend gửi tới `/api/v1/auth/login` (BFF) sau mốc sunset 2026-10-31 là gì? Sunset có được gia hạn bằng cấu hình không (`DEFAULT_SUNSET` hằng số)?
4. Có permission `*` trong bảng `permissions` và role nào đang giữ nó (seed V4/V6/V7)? Ai được phép gán permission `*` bằng `assignPermissions`/`addPermissionOverride`?
5. `authz_version` được ai đọc (cache principal, GraphQL, token)? Hiện chỉ thấy ghi (`AuthorizationMutationAuditService`).
6. Ai tiêu thụ email `ACCOUNT_TEACHER_INVITE` cho nhân sự không phải giáo viên (tên "Teacher invite" cho mọi tài khoản admin tạo)? Nội dung mail có nên tách?
7. Mối quan hệ `user_access_scopes` (policy lúc tạo user) và `user_role_assignments` (gán theo scope): cả hai cùng tồn tại để tính phạm vi — nguồn nào là chuẩn lâu dài?
8. `AuthFailure.IpBlocked/GeoBlocked/DeviceLimitExceeded/PortalForbidden` có còn được ném ở đâu không (chưa thấy usage trong phạm vi) — là di sản từ `erg-go`?
9. `POST` thêm override đơn lẻ (`AuthorizationPermissionOverrideUseCase.add`) không có endpoint trong `AuthorizationController` — có endpoint khác (GraphQL/legacy) hay là chức năng bỏ lửng?
10. `ApiException` kế thừa lớp nào và có `rollbackFor` mặc định cho `AuthFailure.InvalidToken` trong `AuthRefreshUseCase.refresh` (liên quan mục 9.13)?
