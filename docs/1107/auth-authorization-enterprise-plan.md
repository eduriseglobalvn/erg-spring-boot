# Ke hoach hoan thien Authentication va Authorization Enterprise

Ngay lap: 2026-07-11

Pham vi: `erg-spring` backend va `erg-system` frontend cho LMS, LCMS, eLearning.

## Trang thai trien khai 2026-07-11

Da hoan thanh va co test:

- Da go public register, forgot/reset password, Google, Apple va OAuth routes khoi backend; da xoa Google Identity/button va provider API khoi frontend.
- Login contract dung `identifier`, ho tro username noi bo hoac email; migration `V43` them username unique khong phan biet hoa thuong.
- Session moi co absolute expiry 8 gio cho role dac quyen va 12 gio cho user thuong; refresh khong con keo dai `expires_at` 30 ngay.
- Gioi han chinh xac 2 web + 1 mobile/PWA; cung thiet bi khong bi dem lap. Khi vuot quota, backend serialize login bang row lock, revoke session cu, khong khoa vinh vien account va tra `DEVICE_LIMIT_EXCEEDED`.
- Migration `V44` va model `security_events` ghi audit `DEVICE_LIMIT_EXCEEDED` append-only.
- Admin provisioning khong nhan mat khau vinh vien: username noi bo, email tuy chon, temporary credential mot lan, trang thai `INVITED`.
- Public PIN activation va social-auth model/schema legacy da bi go bang migration `V47`/`V48`.
- Keycloak internal-only realm, Authorization Code client scaffold, Redis Spring Session, cookie HttpOnly `__Host-erg_session`, CSRF endpoint va BFF feature flag da co.
- OIDC bridge chi chap nhan user noi bo `ACTIVE`; role, permission, deny override va portal lay tu DB, khong tin IdP claim. Test bridge bao phu ACTIVE, INVITED va identity khong co trong DB.
- Login response va BFF session da dung effective permission/portal entitlement doc lap cho LMS, LCMS, CRM va eLearning; portal khong duoc cap bi chan bang `PORTAL_FORBIDDEN` truoc khi tao session.
- FE BFF mode dung cookie + CSRF, khong gui bearer token va khong doc legacy localStorage token. Legacy mode chi con doc du lieu cu de rollout; session moi ghi sessionStorage.
- FE da xoa bypass theo email/role va viec tu dong gop LMS/LCMS; eLearning khong con map sang LMS.
- Quiz editor da luu recovery draft debounce 1,5 giay, luu ngay khi session invalid/replaced va `pagehide`, khoi phuc ban local moi hon server, xoa sau publish; re-auth dialog giu editor mounted de tranh mat noi dung.
- `POST /auth/logout-all`, inventory session va revoke tung session da co. Focused FE auth/draft suite 36 test, typecheck va focused BE auth/BFF suite dang xanh.

Chua hoan thanh, khong duoc coi la production-ready:

- Tich hop Keycloak Admin API vao workflow admin provisioning: tao identity noi bo, `UPDATE_PASSWORD`, `CONFIGURE_TOTP` cho staff, idempotency va compensation khi DB/Keycloak chi thanh cong mot phia.
- Bat buoc MFA cho staff o runtime, security hold ngan va outbox alert khi device overflow; signed HttpOnly device cookie van chua co.
- Registry session Redis/distributed revoke, Pub/Sub `SESSION_REVOKED`/`AUTHZ_VERSION_CHANGED` va invalidation tren nhieu backend instance.
- Refresh-token reuse detection cho legacy mode trong giai do chuyen tiep; sau rollout phai xoa hoan toan browser token/localStorage compatibility path.
- Tenant binding server-side day du, `authz_version`, DB-only policy engine thong nhat REST/GraphQL va kiem tra object scope/cross-tenant tren moi service.
- Re-auth callback/retry command hoan chinh va autosave server co `docVersion` + idempotency; local recovery hien la lop chong mat du lieu, chua thay the server autosave.
- Chay profile enterprise voi Keycloak/PostgreSQL/Redis that, kiem thu revoke da node, CSRF/CORS/OAuth state, SSO ba portal, load 5.000 session va canary rollout.

## 1. Hien trang va muc tieu

### Van de P0 da xac nhan

- Access token duoc cong bo het han sau 15 phut nhung backend thuc te kiem tra `user_sessions.expires_at` dang dat theo refresh TTL dai ngay.
- Refresh token dang truot them 30 ngay sau moi lan refresh, chua co absolute expiry hoac reuse detection.
- Backend dang public self-registration, forgot/reset password, Google, Apple va OAuth social; tat ca cac surface nay trai voi mo hinh tai khoan noi bo.
- Google/Apple login dang tin `emailVerified`, `providerId`, `googleSub` do frontend gui. Day la lo hong nghiem trong trong luc cac endpoint con ton tai va phai duoc xoa, khong chi an tren UI.
- Frontend dat access/refresh token trong localStorage va cookie JavaScript-readable; SSO con truyen token qua `?sso_token=`.
- Frontend co nhanh cap quyen portal dua tren email admin, role va du lieu localStorage.
- Frontend van co Google Identity script/button, register mutation, provider login, provider account gia lap va forgot-password action.
- eLearning hien thi dang nhap bang ma hoc sinh hoac email, nhung backend `LoginRequest` bat buoc `@Email`; contract nay dang khong thong nhat.
- Backend login tra `permissions=[]` va chi mot portal, trong khi frontend tu gop LMS/LCMS.
- Quyen dang ton tai dong thoi trong DB va `ROLE_PERMISSIONS` hardcode, kho audit va de sai lech.
- Quiz editor chu yeu luu draft khi publish, chua du an toan khi phien het han giua luc dang soan.
- Baseline kiem thu tai thoi diem ra soat: backend focused auth/security test con 1 failure va 1 error; frontend auth test chay bang Vitest con 2 failure do ky vong header cu.

### Muc tieu

- Keycloak lam Identity Provider chuan OIDC.
- Keycloak la dich vu tu host cua ERG, chi chua tai khoan noi bo; khong ket noi Google, Apple/iCloud, Facebook hoac IdP ben thu ba.
- `erg-spring` lam BFF/API va nguon su that cho RBAC, tenant, scope.
- Browser khong giu OAuth token.
- SSO chung cho LMS, LCMS, eLearning, nhung quyen tung portal doc lap.
- Dam bao van hanh nhieu user, nhieu thiet bi, nhieu instance backend ma revoke/session/audit van nhat quan.

## 2. Kien truc xac thuc

1. Trien khai Keycloak self-hosted realm `erg`, cac confidential client `erg-lms`, `erg-lcms`, `erg-elearning` va `erg-api`.
2. Moi portal dung Authorization Code Flow; BFF thuc hien code exchange. Khong dung implicit flow hoac token trong URL.
3. Luu access/refresh token phia server trong Redis-backed Spring Session; trinh duyet chi nhan cookie `__Host-erg_session`, `HttpOnly`, `Secure`, `SameSite=Lax`.
4. Bat CSRF cho cookie-authenticated command; frontend gui CSRF token qua header. CORS chi chap nhan danh sach host ERG chinh xac.
5. Tat self-registration, social login, identity brokering va public forgot/reset password trong Keycloak va `erg-spring`.
6. Xoa endpoint/code/config/model cho Google, Apple/iCloud, Facebook va `social_accounts`; khong sua migration cu, them migration moi de drop bang/cot legacy sau khi backup va kiem tra khong con du lieu can giu.
7. Nguoi dung thu nghiem noi bo duoc import sang Keycloak, reset mat khau mot lan va dang ky MFA. MFA bat buoc cho toan bo nhan su.
8. Thoi han mac dinh:
   - Keycloak access token: 5 phut, chi ton tai phia server.
   - Idle: admin 30 phut, nhan su 2 gio, hoc sinh 2 gio.
   - Absolute re-authentication: admin/role nhay cam 8 gio; giao vien va hoc sinh 12 gio.
   - Ghi nho thiet bi khong vuot absolute timeout; admin khong duoc remember-me.
9. Khong dua vao su kien dong trinh duyet de revoke vi khong dang tin cay. Session cookie mac dinh khong persistent; phien server tu het han theo idle/absolute TTL.
10. SSO khong chia se token giua subdomain: portal moi redirect qua Keycloak, nhan phien rieng tuc thoi neu nguoi dung da xac thuc, roi backend kiem tra entitlement portal.

## 3. Vong doi tai khoan noi bo

- Chi `system.super_admin` hoac admin co `users.create` moi duoc cap tai khoan qua backend; frontend khong goi Keycloak Admin API truc tiep.
- API tao user nhan `username`, email tuy chon, ho ten, tenant, account type, roles, portal entitlements va education-unit/class scopes. `username` la ma noi bo duy nhat va co the la ma hoc sinh/nhan vien.
- Backend tao user Keycloak va ban ghi domain trong mot workflow co idempotency; neu mot ben that bai thi rollback/compensate va ghi audit event.
- Tai khoan moi o trang thai `INVITED`, co temporary credential mot lan va bat buoc `UPDATE_PASSWORD`; nhan su bat buoc them `CONFIGURE_TOTP`/WebAuthn truoc khi ACTIVE.
- Khong de admin dat mat khau vinh vien cho user. Temporary password/activation code chi hien thi mot lan, het han sau 24 gio va khong ghi log.
- Khong co public `/register`, `/forgot-password`, `/reset-password`. Khoi phuc tai khoan do admin khoi tao, bat buoc ghi ly do, security event va revoke toan bo session cu.
- Login nhan mot truong `identifier` thay cho `email`, cho phep username noi bo hoac email da xac minh. Khong doan tenant tu input cua client; tenant duoc resolve tu membership/portal sau authentication.
- Trang thai chuan: `INVITED`, `ACTIVE`, `LOCKED`, `DISABLED`. `LOCKED`/`DISABLED` phai revoke ngay moi session tren Keycloak, Redis va app session registry.
- Import hang loat hoc sinh/nhan su phai co validate, dry-run, idempotency key, file-level audit va ket qua tung dong; khong tra password hang loat trong response/log.

## 4. Session, thiet bi va audit

- Chuyen `user_sessions` thanh registry phien ung dung: `keycloakSid`, user, tenant, device ID/type, UA hash, IP prefix, auth time, last seen, idle/absolute expiry, risk score va revoke reason. Khong con luu token hash cua browser.
- Gioi han moi user: toi da 2 web va 1 mobile. Mobile hien duoc coi la PWA/mobile browser, nen phan loai chi best-effort; chuan bi app-instance key/attestation khi co native app.
- Dung signed HttpOnly device cookie, khong dung fingerprint canvas hoac du lieu co tinh theo doi qua muc.
- Serialize thao tac dang nhap theo user bang PostgreSQL advisory lock hoac row lock de nhieu node khong vuot gioi han do race condition.
- Thiet bi thu tu: tu choi dang nhap moi, revoke toan bo ba phien cu va Keycloak sessions, tao security hold ngan, bat password + MFA lai, gui canh bao user va admin.
- Them bang append-only `security_events`, partition theo thoi gian, luu login, new device, device overflow, revoke, MFA, role change, denied access, document view/download/export/publish; tuyet doi khong luu token hoac noi dung tai lieu.
- Giu audit toi thieu 365 ngay; index theo tenant/user/event/time; day alert qua outbox. Voi tai lieu tai xuong, them watermark chua user, thoi gian va audit ID.
- Redis Pub/Sub phat `SESSION_REVOKED` va `AUTHZ_VERSION_CHANGED` toi moi instance, loai bo cua so cache local khi revoke/quyen thay doi.

## 5. Phan quyen va tenant

- DB la nguon su that duy nhat cho role, permission, deny override, portal entitlement va education-unit/class scope; loai bo recursive `ROLE_PERMISSIONS`.
- Chuan hoa permission theo action: `resource.read/create/update/delete/publish/download/export`; chi giu `system.super_admin` lam super role.
- Moi user co `authz_version`; thay role/scope/deny se tang version, xoa Redis cache va co the revoke phien khi thay doi quyen nhay cam.
- Route security chi lam coarse gate; service bat buoc kiem tra object ownership, tenant, truong, lop va trang thai tai nguyen.
- GraphQL endpoint chung van yeu cau authentication; tung resolver/field nhay cam phai dung cung policy engine voi REST.
- Tenant lay tu membership da chon va bind vao server session, khong tin `X-Tenant-ID` cua browser. PostgreSQL RLS phai fail-closed khi thieu tenant GUC.
- Xoa toan bo bypass dua tren email admin hoac role frontend. Frontend chi dung `/auth/session` de hien thi; backend luon la noi quyet dinh.
- Tao contract test inventory de moi REST mapping va GraphQL resolver deu co public/authenticated/permission policy ro rang.

## 6. Frontend, API va bao ve ban nhap

### Public contract moi

- `GET /api/v1/auth/session`: tra user, roles, effective permissions, portals, tenant, session/device va cac moc expiry; khong tra token.
- `GET /api/v1/auth/login?portal=&returnTo=` va callback BFF.
- `POST /api/v1/auth/logout`, `POST /api/v1/auth/logout-all`.
- `GET /api/v1/auth/sessions`, `DELETE /api/v1/auth/sessions/{id}`.
- `POST /api/v1/auth/reauth/start`.
- `POST /api/v1/admin/users`, `POST /api/v1/admin/users/import`, `POST /api/v1/admin/users/{id}/reset-credential`, `PATCH /api/v1/admin/users/{id}/status` la cac surface duy nhat de cap/thu hoi credential noi bo.
- Xoa hoan toan public contract `/auth/register`, `/auth/google/login`, `/auth/oauth/**`, `/auth/forgot-password`, `/auth/reset-password`.
- Loi chuan hoa: `SESSION_EXPIRED`, `REAUTH_REQUIRED`, `SESSION_REVOKED`, `DEVICE_LIMIT_EXCEEDED`, `PORTAL_FORBIDDEN`, `TENANT_FORBIDDEN`.

### Frontend changes

- Xoa cross-domain cookie, `sso_token`, token localStorage, JWT decoding va refresh logic.
- Xoa Google Identity script/button/component, `AuthProvider` social union, register mode, provider mutation va local provider account gia lap.
- Login form chi con `identifier`, password va remember-device theo policy. Link quen mat khau duoc thay bang thong tin lien he admin noi bo, khong goi public recovery API.
- API client chi dung `credentials: include`, CSRF header va single-flight re-auth.
- Khong persist auth response hoac du lieu nhay cam vao TanStack Query/PWA cache.
- Logout va doi identity phai xoa cache theo user/tenant.

### Quiz editor va draft safety

- Quiz editor autosave server sau 2 giay debounce, dung `docVersion` va idempotency key.
- IndexedDB giu recovery snapshot theo user/tenant/quiz toi da 24 gio.
- Khi het phien, dung mutation, giu draft, mo re-auth dialog tai cho; sau thanh cong tiep tuc sync.
- Neu dang nhap user khac, khong hien thi draft cua user cu.

## 7. Trien khai va kiem thu

### Thu tu trien khai

1. Sua baseline test hong va lap permission/endpoint matrix truoc khi thay auth.
2. Dung Keycloak + Redis internal-only, tat registration/social IdP, migration user noi bo, MFA va BFF phia sau feature flag.
3. Trien khai session/device/audit schema va distributed revocation.
4. Chuyen frontend tung portal sang cookie BFF: LMS, LCMS, sau do eLearning; giu SSO chung nhung entitlement rieng.
5. Trien khai policy engine, object scope va RLS fail-closed; xoa bypass/hardcoded permission.
6. Them autosave va re-auth overlay truoc khi bat absolute timeout.
7. Canary voi tai khoan noi bo; revoke toan bo legacy sessions, drop social-auth data legacy roi xoa endpoint/token storage cu.

### Test bat buoc

- Expiry theo dong ho gia lap.
- Refresh/revoke da node.
- Login dong thoi va race gioi han thiet bi.
- Gioi han 2 web + 1 mobile.
- MFA cho nhan su.
- SSO ba portal.
- Portal forbidden va tenant forbidden.
- Deny override thang grant/wildcard.
- Cross-tenant IDOR.
- GraphQL field security.
- CSRF/CORS.
- OAuth state/PKCE.
- Public register/social/forgot/reset routes phai tra 404/405 hoac deny-by-default va khong con trong OpenAPI.
- Admin create/import/reset user phai co permission, tenant scope, idempotency, audit va temporary credential expiry.
- Login bang ma hoc sinh/ma nhan vien va email noi bo.
- Static contract test dam bao source auth khong con Google Identity, Apple/iCloud, Facebook hoac `social_accounts`; cac tich hop Google Slides/Drive va Firebase khong thuoc auth va duoc giu nguyen.
- Draft recovery va dang nhap lai bang user khac.

### Load va monitoring

- Load test toi thieu 5.000 phien dong thoi.
- Muc tieu p95 login/session duoi 300 ms.
- Authorization cache-hit duoi 50 ms.
- Khong race vuot gioi han thiet bi.
- Khong co truy van tenant thieu GUC.
- Theo doi login failure, revoke latency, active session cardinality, device overflow, permission-denied spike va audit-outbox lag.
