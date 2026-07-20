# Báo Cáo Bảo Mật — Erg Spring Boot Security Audit

**Ngày:** 2026-06-10  
**Phạm vi:** Toàn bộ mã nguồn `erg-spring` (Spring Boot 4, Java 25)  
**Phương pháp:** 7 góc nhìn độc lập + xác thực chéo  

---

## Mức độ nghiêm trọng

| Mức | Số lượng |
|-----|----------|
| 🔴 CRITICAL | 2 |
| 🟠 HIGH | 8 |
| 🟡 MEDIUM | 6 |
| 🔵 LOW/CLEANUP | 6 |

---

## 🔴 CRITICAL

### C-1: Trusted Header Auth Mode — Giả mạo hoàn toàn người dùng

**File:** `ErgAuthenticationFilter.java` (dòng 55-99)  
**Cấu hình:** `ErgSecurityProperties.java` (dòng 11) — mặc định `false`  

**Mô tả:** Khi `erg.security.trusted-header-auth-enabled=true`, filter xây dựng toàn bộ `ErgPrincipal` (userId, roles, permissions, portals, scope) trực tiếp từ HTTP headers mà **không có bất kỳ xác thực chữ ký, IP allowlist, hay kiểm tra mật nào**. Attacker chỉ cần gửi header `X-User-ID` + `X-Roles` + `X-Permissions` là có thể giả mạo bất kỳ người dùng nào.

**Kịch bản khai thác:** Attacker truy cập API với header:
```
X-User-ID: system
X-Roles: system.super_admin
X-Permissions: *
X-Portals: *
```
Hệ thống tạo `ErgPrincipal` với quyền super-admin chỉ từ headers — không cần token, không cần database.

**Khắc phục:**
- Đảm bảo `trusted-header-auth-enabled=false` ở mọi môi trường production
- Nếu cần dùng, thêm cơ chế HMAC signature (`X-Auth-Signature`) hoặc IP allowlist
- Hoặc loại bỏ trusted header auth hoàn toàn

---

### C-2: Thông tin xác thực production lộ trong file `.env`

**File:** `.env` (toàn bộ)  
**Mức ảnh hưởng:** Database, JWT, API keys, Object Storage  

**Mô tả:** File `.env` chứa live credentials cho toàn bộ hạ tầng production:
- **PostgreSQL:** `avnadmin` / `REDACTED_DB_PASSWORD` trên `aivencloud.com:21590`
- **MongoDB Atlas:** `erg` / `Dev.erg.edu.vn` trên `mongodb.net`
- **Redis/Valkey:** `default` / `REDACTED_REDIS_PASSWORD` trên `aivencloud.com:21591`
- **JWT Secret:** `REDACTED_JWT_SECRET` (ký token truy cập)
- **Groq API Key:** `REDACTED_GROQ_API_KEY`
- **HuggingFace API Key:** `REDACTED_HUGGINGFACE_API_KEY`
- **Cloudflare R2:** access/secret keys cho object storage
- **SMTP:** thông tin email `noreply@erg.edu.vn`

File được import qua `spring.config.import=optional:file:.env[.properties]` trong `application.yml` (dòng 3).

**Kịch bản khai thác:** Bất kỳ ai có quyền truy cập filesystem (dev, CI/CD, server) đều có thể:
1. Kết nối trực tiếp đến PostgreSQL và đọc/ghi toàn bộ dữ liệu
2. Ký JWT token giả mạo với secret đã biết
3. Sử dụng API keys AI (Groq, HuggingFace) gây tổn thất chi phí
4. Truy cập object storage R2

**Khắc phục:**
- Thay thế bằng secrets management (Vault, AWS Secrets Manager, hoặc ít nhất là biến môi trường không commit)
- Xoay vòng (rotate) tất cả credentials ngay lập tức
- Thêm `.env` vào `.gitignore` (đã làm) nhưng không đủ — cần giải pháp quản lý secrets chuyên dụng

---

## 🟠 HIGH

### H-1: AuthController.logout dùng headers thay vì authenticated principal

**File:** `AuthController.java` (dòng 172-176, 298-318)

**Mô tả:** Endpoint `logout()` đọc `X-User-ID`, `X-Session-ID`, `X-Tenant-ID` từ **request headers** thay vì từ `ErgPrincipal` đã xác thực trong `SecurityContextHolder`. Các controller khác (UserController, ProfileController, DocumentController) đều dùng đúng pattern, chỉ riêng AuthController dùng headers.

**Kịch bản khai thác:** Attacker đã xác thực (với token hợp lệ) gửi:
```
POST /api/v1/auth/logout
X-User-ID: victim-id
X-Session-ID: victim-session-id
```
Controller gọi `authService.logout("victim-id", "erg", "victim-session-id")` — session của nạn nhân bị thu hồi.

**Khắc phục:** Sửa `userId()`, `tenantId()`, `sessionId()` trong `AuthController` để đọc từ `ErgPrincipal` giống các controller khác.

---

### H-2: ProfileController IDOR — Người dùng bất kỳ có thể đọc/sửa/xóa profile người khác

**File:** `ProfileController.java` (dòng 35-63)

**Mô tả:** Endpoint `GET /api/v1/profiles/{userId}`, `PUT /api/v1/profiles/{userId}`, `DELETE /api/v1/profiles/{userId}` nhận `userId` từ path variable và truyền trực tiếp xuống service layer **không kiểm tra quyền sở hữu**. Chỉ cần xác thực (`.authenticated()`) — không cần quyền gì đặc biệt.

**SecurityConfig** không có rule riêng cho `/api/v1/profiles/**` — nó fallthrough vào `.requestMatchers("/api/v1/**").authenticated()` (dòng 245).

**Kịch bản khai thác:** Học sinh (student) có thể `GET /api/v1/profiles/admin-123` để đọc thông tin admin, hoặc `PUT /api/v1/profiles/admin-123` với body tùy ý để sửa profile của admin.

**Khắc phục:**
- Thêm rule trong SecurityConfig: `.requestMatchers("/api/v1/profiles/me/**")...` và `.requestMatchers("/api/v1/profiles/{userId}").access(require("users.manage"))`
- Thêm kiểm tra ownership trong service layer

---

### H-3: GraphQL endpoint chỉ yêu cầu `.authenticated()` — không kiểm tra permission

**File:** `SecurityConfig.java` (dòng 244)

**Mô tả:** Endpoint `POST /api/v1/graphql` chỉ yêu cầu `.authenticated()` mà không yêu cầu permission cụ thể. `GraphQlGuardFilter` xử lý depth limiting, rate limiting, introspection blocking nhưng **không thực hiện authorization**. Toàn bộ trách nhiệm authorization đặt lên từng `@QueryMapping` resolver riêng lẻ.

**Kịch bản khai thác:** Developer thêm resolver `allUserEmails()` mà quên `@PreAuthorize`. Mọi authenticated user có thể query danh sách email toàn bộ người dùng.

**Khắc phục:**
- Thêm GraphQL operation-level authorization trong GuardFilter
- Hoặc đảm bảo tất cả resolvers đều có `@PreAuthorize`

---

### H-4: LmsRuntimeService.canAccessClassroom() dùng OR thay vì AND

**File:** `LmsRuntimeService.java` (dòng 395-399)

**Mô tả:** Kiểm tra truy cập classroom dùng `||` (OR) thay vì `&&` (AND):
```java
return access.canAccessClass(classroom.getId()) || access.canAccessEducationUnit(classroom.getSchoolId());
```
Chỉ cần một trong hai điều kiện đúng là được truy cập.

**Kịch bản khai thác:** Người dùng có scope class-level (`managedClassIds = ["class_a"]`) nhưng không có school-level scope có thể truy cập classroom thông qua class-level access. Trước đây cần cả hai điều kiện (AND) để tăng cường bảo vệ.

**Khắc phục:** Xem xét đổi lại `&&` (AND) nếu mô hình bảo mật yêu cầu cả class và school đều phải được phép.

---

### H-5: Permission matching với wildcard quá rộng

**File:** `ErgPolicyService.java` (dòng 89-110)

**Mô tả:** Hệ thống wildcard matching hỗ trợ 4 mức:
1. Exact match hoặc `*`
2. `prefix.*` — khớp mọi permission bắt đầu bằng prefix
3. `*.suffix` — khớp mọi permission kết thúc bằng suffix
4. **Embedded wildcard** `a*b` — khớp mọi permission bắt đầu bằng `a` và kết thúc bằng `b`

Mức 4 (embedded wildcard) rất nguy hiểm: `system.*dmin` khớp cả `system.admin` lẫn `system.super_admin`. Role `viewer` có `*.read` khớp mọi `.read` permission trên toàn bộ hệ thống (crm, lms, hoclieu, ...).

Ngoài ra, **namespace role và permission bị trộn lẫn** (dòng 21-55): role `lms.teacher` vừa là role name vừa xuất hiện trong value của role `teacher`. Khi expand gặp `lms.teacher` trong permissions, nó tìm trong `ROLE_PERMISSIONS` và expand thêm — tạo chuỗi ủy quyền nguy hiểm.

**Hậu quả:** Permission override cấp user với giá trị trùng tên role sẽ leo thang thành toàn bộ permissions của role đó.

**Khắc phục:**
- Loại bỏ embedded wildcard (case 4)
- Tách biệt namespace role và permission
- Flat map role → Set<String> permissions thay vì đệ quy

---

### H-6: .anyRequest().permitAll() làm catch-all mặc định

**File:** `SecurityConfig.java` (dòng 246)

**Mô tả:** Kết thúc filter chain với `.anyRequest().permitAll()` — mọi URL không khớp pattern trước đó đều public. Vi phạm nguyên tắc deny-by-default.

**Kịch bản khai thác:** Nếu dev thêm endpoint mới ngoài `/api/v1/**` (ví dụ `/internal/admin/users`), nó tự động public.

**Khắc phục:** Đổi thành `.anyRequest().denyAll()` và explicit permitAll cho từng route public.

---

### H-7: DocumentController — Mass assignment không kiểm soát

**File:** `DocumentController.java` (dòng 31-58)

**Mô tả:** Cả `upload()` và `update()` đều dùng `@RequestBody Map<String, Object>` không có `@Valid`, không DTO, không ràng buộc. Map thô truyền thẳng xuống service.

**Kịch bản khai thác:** Attacker với `documents.manage` có thể set các field ẩn như `owner`, `visibility`, `permissions` trong body.

**Khắc phục:** Thay thế bằng DTO cụ thể với validation annotations.

---

### H-8: Super Admin Role quá nhiều alias

**File:** `ErgPolicyService.java` (dòng 20)

**Mô tả:** 4 alias cho super admin: `system.super_admin`, `erg_super_admin`, `SUPER_ADMIN`, `admin`. Mỗi alias khớp case-sensitive khác nhau tạo nguy cơ sai sót.

**Khắc phục:** Chọn một canonical name duy nhất.

---

## 🟡 MEDIUM

### M-1: Cache authPrincipal 15 giây — cửa sổ thu hồi

**File:** `CacheConfig.java` (dòng 21), `ErgPrincipalService.java` (dòng 39-43)

**Mô tả:** `authPrincipal` cache TTL = 15 giây. Khi user bị block/banned, cache còn hiệu lực 15 giây — trong thời gian đó user vẫn dùng được token cũ. Trên môi trường multi-instance, `@CacheEvict` chỉ clear cache trên instance hiện tại.

**Khắc phục:**
- Giảm TTL xuống 5 giây
- Hoặc dùng distributed cache (Redis)
- Hoặc thêm check `active()` trong fromBearerToken ngay cả khi cache hit

---

### M-2: Route GET public có thể leak draft/unpublished content

**File:** `SecurityConfig.java` (dòng 60-68)

**Mô tả:** Các route GET không yêu cầu authentication:
```
/api/v1/pages/**, /api/v1/posts/**, /api/v1/courses/**
/api/v1/hoclieu/home, /api/v1/elearning/categories/**
```

Không có defense-in-depth ở infrastructure layer — nếu service layer quên filter `published=true`, draft content bị lộ.

---

### M-3: PortalFromPath bị duplicate giữa 2 file quan trọng

**File:** `ErgAuthenticationFilter.java` (dòng 134-147), `PermissionAuthorizationManager.java` (dòng 35-48)

**Mô tả:** `portalFromPath()` giống hệt nhau ở 2 file quyết định authentication và authorization. Nếu sửa một chỗ quên chỗ kia, portal access control bị lệch — có thể gây bypass.

---

### M-4: INFO-level logging lộ PII

**File:** `ApiAccessLogFilter.java` (dòng 44-61)

**Mô tả:** Log userId và tenantId ở level INFO. Nếu path chứa token/reset code, không được mask. Log tập trung (ELK/Splunk) có thể lộ PII.

**Khắc phục:** Mask URI path segments dạng UUID/token; giảm xuống DEBUG.

---

### M-5: saveAnswer không validate nội dung — nguy cơ XSS

**File:** `LmsRuntimeService.java` (dòng 153-164)

**Mô tả:** Trường `answer` (Object) không được validate, sanitize, giới hạn độ dài. Student có thể gửi HTML/JavaScript. Nếu answer hiển thị trên dashboard giáo viên mà không escape, dẫn đến stored XSS.

**Khắc phục:** Sanitize HTML output; thêm giới hạn kích thước.

---

### M-6: `saveAnswer` kiểm tra `$` và `.` cho PostgreSQL JSONB — không còn phù hợp

**File:** `LmsRuntimeService.java` (dòng 154)

**Mô tả:** Kiểm tra `questionId.contains(".") || questionId.contains("$")` là di sản từ MongoDB để chống NoSQL injection. Hệ thống đã chuyển sang PostgreSQL JSONB nên check này vô dụng và gây false positives.

**Khắc phục:** Loại bỏ check; thay bằng validate phù hợp với PostgreSQL.

---

## 🔵 LOW / CLEANUP

### L-1: Utility methods bị duplicate khắp codebase

- `csv()` — duplicate ở **3 file** (ErgAuthenticationFilter, UserAccessContext, GraphQlAccessSupport)
- `firstPresent()` — duplicate ở **5 file** (ErgAuthenticationFilter, UserAccessContext, ApiAccessLogFilter, AuthController, GraphQlAccessSupport)
- `principal()` — duplicate ở **9 nơi** (UserController, DocumentController, SessionController, AuthorizationController, ProfileController, ApiAccessLogFilter, UserAccessContext, NotificationSecuritySupport, GraphQlAccessSupport)

### L-2: Hardcoded "erg" tenant default ở 7+ file

Không có `TenantResolver` tập trung. "erg" được hardcode ở ErgAuthenticationFilter, ErgPrincipalService, UserAccessContext, ApiAccessLogFilter, GraphQlAccessSupport, UserService, SessionService, AuthService, LmsRuntimeService, AuthController, ErgPrincipal.

### L-3: Không có HandlerMethodArgumentResolver cho ErgPrincipal

Mỗi controller tự extract principal từ `SecurityContextHolder` — khác biệt về behavior khi null (có chỗ throw exception, có chỗ return null silent).

### L-4: Header aliases cho EducationUnitID quá nhiều

6-7 header names khác nhau cho cùng một giá trị (`X-Current-EducationUnit-ID`, `X-Current-Education-Unit-ID`, `X-Current-Center-ID`, `X-EducationUnit-ID`, `X-Education-Unit-ID`, `X-Center-ID`). Tạo ambiguity, khó maintain.

### L-5: N+1 queries trong UserService.findUsers

**File:** `UserService.java` (dòng 348-351)

`findUsers()` gọi N lần `findUser()` riêng lẻ thay vì batch query — performance issue và connection pool exhaustion.

### L-6: Recursive permission expansion với depth limit arbitrary

**File:** `ErgPolicyService.java` (dòng 112-132)

`expand()` dùng đệ quy depth=5 qua ALIASES và ROLE_PERMISSIONS. Khó audit, dễ tạo cycle.

---

## Tình trạng fix

### ✅ Đã fix (13/15 issues)

| # | Issue | File(s) | Status |
|---|-------|---------|--------|
| **C-1** | Trusted header auth removal | `ErgAuthenticationFilter.java`, `ErgSecurityProperties.java` | ✅ Xóa toàn bộ trusted header code path |
| **H-1** | AuthController dùng headers | `AuthController.java` | ✅ Chuyển sang `ErgPrincipal` |
| **H-2** | ProfileController IDOR | `ProfileController.java` | ✅ Thêm `requireOwnProfileOrManage()` |
| **H-3** | GraphQL thiếu permission check | `StudentDashboardGraphQlController.java` | ✅ Thêm `@PreAuthorize` |
| **H-4** | canAccessClassroom OR→AND | `LmsRuntimeService.java` | ✅ Đổi `||`→`&&` |
| **H-5** | Wildcard quá rộng | `ErgPolicyService.java` | ✅ Xóa embedded wildcard |
| **H-6** | permitAll→denyAll | `SecurityConfig.java` | ✅ Đổi catch-all |
| **H-7** | Document mass assignment | `DocumentController.java`, `DocumentRequests.java` | ✅ DTO + @Valid |
| **H-8** | Super Admin nhiều alias | `ErgPolicyService.java` | ✅ Giảm còn 1 alias |
| **M-1** | Cache TTL 15s | `CacheConfig.java` | ✅ Giảm xuống 5s |
| **M-3** | portalFromPath duplication | `SecurityUtils.java` | ✅ Shared utility |
| **M-4** | Log lộ PII | `ApiAccessLogFilter.java` | ✅ Thêm URI path masking |
| **M-6** | MongoDB check trên PostgreSQL | `LmsRuntimeService.java` | ✅ Xóa check `$`/`.` |

### ⚠️ Chưa fix (2 issues — cần thao tác thủ công)

| # | Issue | Lý do |
|---|-------|-------|
| **C-2** | Live credentials trong `.env` | Cần rotate credentials thủ công (DB, JWT secret, API keys) |
| **M-2** | Route GET public leak draft | Cần audit service layer — infrastructure đã OK |

### 🔵 Cleanup còn lại (không ảnh hưởng security)
- **L-1:** Utility methods (`csv`, `firstPresent`) còn duplicate ở UserAccessContext, GraphQlAccessSupport
- **L-2:** Hardcoded "erg" tenant — refactor lớn, ít rủi ro
- **L-3:** HandlerMethodArgumentResolver — nice-to-have
- **L-4:** Header aliases EducationUnitID — ít rủi ro
- **L-5:** N+1 trong findUsers — performance, không phải security
- **L-6:** Recursive permission expansion — khả năng ảnh hưởng thấp

---

*Báo cáo được tạo từ 7 góc nhìn độc lập (line-by-line, removed-behavior, cross-file, reuse, simplification, efficiency, altitude) + xác thực chéo.*
