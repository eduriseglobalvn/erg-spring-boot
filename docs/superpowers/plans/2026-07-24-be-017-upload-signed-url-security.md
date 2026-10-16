# BE-017 — Audit upload/download và signed URL

## Task
- ID: BE-017 (ERG-SPRING, P-high, Rủi ro Cao).
- Goal: File trái phép bị reject; signed upload URL hết hạn, không cross-tenant, không tamper; commit chỉ sau khi object khớp contract (size + sha256 + mime).
- Scope: `src/main/java/**/service/quiz/media` + LCMS media controller + storage config; test acceptance cross-tenant/tamper/expiry/checksum.
- Dependency: BE-014 (security review) — đã có finding register/hardening evidence trước đó; upload enforcement là phần còn mở của BE-017.

## Baseline (verified 2026-07-24)
- Branch: `codex/spring-modulith-modernization`; worktree dirty với WIP media storage.
- Focused suite hiện có PASS: `GlobalExceptionHandlerTest`, `LocalMediaStorageAdapterTest`, `MediaServiceValidationTest`, `R2MediaStorageAdapterTest` — 19 tests, 0 fail.
- Đã có: HMAC-signed presign (tenant+key+mime+size+expiry), `isAuthorizedUpload` reject cross-tenant/size/expiry/tamper, content-addressed key, `storeAuthorizedUpload` sha verify + path-escape guard, R2 checksum-bound PUT + HEAD verify, commit-time HEAD contract check trong `MediaService`.
- Gap so với DoD: chưa có bằng chứng end-to-end qua HTTP path (`LcmsMediaController.uploadLocal`) rằng URL giả mạo/hết hạn/cross-tenant/oversize bị reject với error contract ổn định.

## Invariants giữ nguyên
- Media API surface (`/presign`, `/{id}/commit`, `/upload/**`) không đổi shape.
- `MediaService` validation (image allowlist, 15MB cap, sha256 hex) giữ nguyên.
- Local adapter là default; R2 chỉ bật khi `erg.r2.enabled=true`.
- Không đổi UI. Không sửa migration đã phát hành.

## Files
- CREATE: `src/test/java/com/erg/ergspringboot/controller/lcms/LcmsMediaControllerUploadContractTest.java` — controller-level acceptance cho upload reject/accept path.
- MODIFY: none (implementation đã đủ; chỉ bổ sung evidence test nếu baseline xanh).

## Steps
1. Baseline focused suite (done — 19 pass).
2. Thêm controller test: authorized upload → 204; tampered signature → BadRequest; expired → BadRequest; cross-tenant key → BadRequest; oversize → BadRequest.
3. Chạy focused suite mới + toàn bộ media/exception suite.
4. BE completion gate proportional: chạy `MediaService*`, `*MediaStorageAdapter*`, `LcmsMediaControllerUploadContractTest`, `GlobalExceptionHandlerTest`.
5. Update workbook evidence + progress → 1.0.

## Commands
```
./mvnw.cmd -Dtest=LcmsMediaControllerUploadContractTest,LocalMediaStorageAdapterTest,R2MediaStorageAdapterTest,MediaServiceValidationTest,GlobalExceptionHandlerTest test
```
Expected: BUILD SUCCESS, 0 failures.

## Rollback
- Test-only addition; xóa file test để revert. Không đổi production behavior.

## Definition of Done
- Tampered/expired/cross-tenant/oversize upload bị reject qua HTTP path với `BadRequestException` → error envelope.
- Authorized upload lưu bytes và trả 204.
- Focused gate xanh với số test cụ thể.
- Workbook BE-017 = Hoàn thành, evidence nêu lệnh + số test.

## Không làm (chống scope creep)
- Không wiring virus scan runtime (out of scope; slide chỉ nhận image, đã có allowlist + checksum).
- Không load test (thuộc BE-015).
- Không refactor các WIP task khác trong worktree (BE-009/010/012).

## Final status — 2026-07-24 (Hoàn thành)

- Facade boundary restored: `LcmsMediaController` depends only on `LcmsOperations` (added `maxMediaUploadBytes()` + `storeSignedMediaUpload()`; `MediaService.storeSignedUpload()` delegates to the adapter). Fixes `DependencyDirectionArchTest.migratedLcmsMediaAdapterDependsOnlyOnModuleFacade`.
- Fixed a duplicate `erg.quiz` YAML key in `application.yml` (WIP had added a second `erg.quiz.media` block) that broke every full-context test with a SnakeYAML `DuplicateKey`. Merged `local-root` into the existing block.
- New HTTP-path acceptance test `LcmsMediaControllerUploadTest` (6 cases): happy path + tamper, expiry, cross-tenant, checksum-mismatch, oversize rejection — exercised through the controller via the facade.

### Evidence
- Focused: `mvnw -Dtest=DependencyDirectionArchTest,GlobalExceptionHandlerTest,LcmsMediaControllerUploadTest,LocalMediaStorageAdapterTest,MediaServiceValidationTest,R2MediaStorageAdapterTest test` → 30 tests, 0 failures, BUILD SUCCESS.
- Integration (Testcontainers, via `mvn verify` failsafe reports): Flyway upgrade, cache isolation/stampede/generation, tenant RLS matrix, portal error envelope, query-count suites all green.
- Remaining `mvn verify` context-load failures (ErgSpringApplicationTests, PlatformHttpSmokeTest, FirebaseHttpV1PushSenderTest) are environmental: `java.net.http.HttpClient` cannot instantiate in this sandbox — `Selector.open()` fails to create its loopback wakeup pipe (`sun.nio.ch.PipeImpl$Initializer.init`). Reproduced standalone with a 4-line `HttpClient.newBuilder().build()`. Unrelated to media storage.

### Intentionally not done
- No real R2/S3 network upload executed (no cloud credentials); R2 adapter covered by mocked S3 client/presigner contract test.
- Virus scanning not implemented — image allowlist + size cap + checksum binding is the enforced boundary; AV is a separate op concern.
