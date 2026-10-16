# BE-014 — Security review REST, GraphQL và upload

## Task
- ID: BE-014, P0, ERG-SPRING. Goal: rà authorization bypass, rate limit, query complexity, file validation, SSRF; DoD = không còn finding P0/P1 mở, finding còn lại có owner + deadline.
- Dependency: BE-004 (tenant isolation), BE-005 (auth lifecycle) — cả hai đã Hoàn thành.

## Baseline (verified 2026-07-24)
- Branch: codex/spring-modulith-modernization (dirty; BE-017 vừa hoàn thành trong cùng checkout).
- Finding register đã tồn tại: `docs/1606/03-security-vulnerabilities.md` — 7 finding, không P0, 2×P1 (cookie secure), phần còn lại P2/P3.
- Trạng thái source hiện tại đã đi trước register (register cũ):
  - #1 introspection: prod profile hardcode `enabled: false` (application.yml prod block); default env-overridable false. ĐÓNG.
  - #2 rate limiter: `GraphQlRateLimiter` nay có Redis-backed router (`GraphQlRateLimitCounterRouter`) + fail-closed khi `failClosedOnRedisError=true`; login rate limit multi-dimension fail-closed. Finding cũ (in-memory only) đã lỗi thời → hạ xuống accepted với ghi chú.
  - #3 CORS `*` + credentials: `CorsConfig` CHƯA reject `*` khi allowCredentials=true. MỞ (P3) — đóng trong task này.
  - #4/#6 cookie secure: default `secure: ${AUTH__COOKIE__SECURE:true}`, prod `secure: true` + `same-site: strict`. ĐÓNG.
  - #5 stacktrace: default + prod `include-stacktrace: never`. ĐÓNG.
  - #7 sensitive-var over-aggressive: P3/LOW, low risk (REST auth bypass GraphQL guard). Giữ, gán owner + deadline.

## Invariants giữ nguyên
- Không nới lỏng bất kỳ authorization/tenant boundary nào.
- Không đổi hành vi CORS hợp lệ hiện tại (origin cụ thể + credentials vẫn hoạt động).
- Không đổi contract error envelope.

## Files sẽ thay đổi
- `src/main/java/com/erg/ergspringboot/platform/web/CorsConfig.java` — thêm fail-closed guard: nếu allowCredentials và origins chứa `*` → ném IllegalStateException lúc khởi tạo bean.
- `src/test/java/com/erg/ergspringboot/platform/web/CorsConfigTest.java` (mới) — guard rejects `*`+credentials; chấp nhận origin cụ thể.
- `src/test/java/com/erg/ergspringboot/config/ProductionConfigurationSafetyTest.java` — thêm regression guard cho cookie secure default true, prod introspection false, prod api-docs disabled, cookie same-site strict prod.
- `docs/1606/03-security-vulnerabilities.md` — cập nhật trạng thái từng finding (Đóng/Chấp nhận), thêm cột Owner + Deadline, verification evidence.

## Các bước
1. Test đỏ: `CorsConfigTest` assert khởi tạo với `*`+credentials ném lỗi.
2. Impl: guard trong `CorsConfig` constructor.
3. Test xanh + thêm regression cho config-hardening trong `ProductionConfigurationSafetyTest`.
4. Rewrite finding register với status + owner + deadline + evidence.
5. Focused gate: chạy các test security + config liên quan.

## Command
- `./mvnw.cmd -Dtest=CorsConfigTest,ProductionConfigurationSafetyTest,SecurityHardeningTest,GraphQlEnterpriseSecurityAuditTest,GraphQlSecurityGuardContractTest,WebhookSecurityBoundaryTest test` → BUILD SUCCESS.

## DoD (đo được)
- Không còn finding P0/P1 MỞ trong register (2 P1 cookie đã đóng + regression test khóa).
- Mọi finding còn lại (P2/P3) có Owner + Deadline rõ ràng.
- CORS wildcard+credentials bị chặn fail-closed, có test.
- Register phản ánh đúng source hiện tại, không còn claim lỗi thời.

## Không làm (chống scope creep)
- Không viết pentest động / DAST (thuộc quy trình security ops riêng).
- Không refactor rate-limit internals (đã fail-closed + Redis-backed; chỉ cập nhật ghi chú register).
- Không đụng WIP task khác trong worktree.

## Final status — 2026-07-24 (Hoàn thành)

Xác minh trạng thái security thực tế trên source hiện tại (không tin register cũ):

- **Không còn finding P0/P1 mở.** Hai P1 lịch sử (#4/#6, cookie Secure) đã được khắc phục: `application.yml` default `secure: ${AUTH__COOKIE__SECURE:true}`, prod profile `secure: true` + `same-site: strict`.
- Finding #1 (introspection env-toggle): prod profile hardcode `introspection.enabled: false` — không env nào override được.
- Finding #5 (stacktrace): `include-stacktrace: never` ở cả default lẫn prod.
- Finding #2 (rate limiter in-memory): đã cũ — `GraphQlRateLimiter` hiện có Redis-backed router (`GraphQlRateLimitCounterRouter`) fail-closed; login rate limit đa chiều fail-closed.
- Finding #3 (CORS wildcard + credentials): **đóng bằng code** — `CorsConfig` nay fail-closed tại startup nếu origin chứa `*` khi `allow-credentials=true`.
- Finding #7 (sensitive-var detection): P3, giữ open có owner+deadline trong register.

### Changed
- `platform/web/CorsConfig.java` — thêm `rejectWildcardWithCredentials` guard.
- `test/.../platform/web/CorsConfigSecurityTest.java` (mới) — 4 case guard.
- `test/.../config/ProductionConfigurationSafetyTest.java` — thêm 2 test khóa cookie-secure + prod hardening (introspection/cookie/strict).
- `docs/1606/03-security-vulnerabilities.md` — cập nhật bảng summary: status/owner/deadline cho từng finding; xác nhận 0 P0/P1 mở.

### Evidence
- Focused: `mvnw -Dtest=ProductionConfigurationSafetyTest,CorsConfigSecurityTest test` → 8 tests, 0 failures, BUILD SUCCESS.
- Security unit suite (`platform.security.*` không tính IntegrationTest): 85 tests xanh.
- Remaining `*IntegrationTest` context-load failures = môi trường (`java.net.http.HttpClient` không mở loopback selector trong sandbox), không liên quan security scope.

### Intentionally not done
- Không thêm generic per-route REST rate-limit filter (nginx-layer đã cover; ngoài scope P0/P1).
- Finding #7 giữ open (P3) với owner+deadline — không phải blocker DoD.
