# BE-007 — Audit application cache: tenant-isolation completeness + sensitive-name guard

## Task
- **ID:** BE-007 (ERG-SPRING, P1, Caching)
- **Goal:** Không chia sẻ cache sai tenant; có metrics hit/miss và invalidation tests.
- **Dependency:** BE-004 (Hoàn thành) — đã xác minh.

## Baseline (source hiện tại, khảo sát)
- `config/CacheConfig` đăng ký **25 descriptor**; helper `descriptor(...)` hardcode `tenantRequired=true` cho mọi cache.
- Key: `erg:cache:v2:{<tenantHash>}:<name>:g<gen>:<keyHash>`; `ErgTieredCache.redisKey()` trả null (không ghi L2) nếu `tenantRequired && tenant==null`.
- Metrics hit/miss: `erg.cache.operations{cache,tier,operation,outcome}`, bounded-cardinality test đã có (`RedisHealthAndMetricsTest`), dashboard contract test đã có (BE-010).
- Invalidation: generation-based, transaction-aware (afterCommit), cross-instance Redis pub/sub; tests `BusinessCacheInvalidationIntegrationTest`, `CacheGenerationIntegrationTest`, `CacheStampedeIntegrationTest`.
- Stampede: `CacheLoadCoordinator` single-flight + bounded admission (BE-007 evidence 2026-07-22).

## Vấn đề (gap còn addable, không phải deploy-time)
1. **Completeness gap:** `BusinessCacheIsolationIntegrationTest` chỉ kiểm 13 tên hardcode, trong khi có 25 descriptor. Không test nào iterate toàn bộ `getCacheNames()` khẳng định `tenantRequired==true`. Invariant hiện đúng (helper hardcode), nhưng một descriptor tương lai thêm qua constructor thô có thể set false mà không bị phát hiện.
2. **Sensitive-name guard:** không test nào chặn cache name khớp pattern auth/token/session/password → rủi ro cache nhầm dữ liệu nhạy cảm ở L2.

## Invariants giữ nguyên
- Không đổi CacheConfig descriptors hiện có.
- Không đổi key format / TTL / invalidation semantics.
- Test thuần unit (không cần Redis/Testcontainers) để chạy được mọi môi trường.

## File create/modify
1. MODIFY `src/test/java/com/erg/ergspringboot/platform/cache/BusinessCacheIsolationIntegrationTest.java`
   - Thêm test iterate **toàn bộ** `manager.getCacheNames()` → mọi descriptor `tenantRequired==true` và `valueType != Object`.
   - Thêm test guard: không cache name nào khớp pattern nhạy cảm (token/password/secret/credential/session/refresh/jwt).

## Các bước
1. Đọc registry API (`getCacheNames`, `descriptor`).
2. Thêm 2 test.
3. Chạy focused: `BusinessCacheIsolationIntegrationTest`.

## Command & expected
- `mvnw -Dtest=BusinessCacheIsolationIntegrationTest test` → BUILD SUCCESS.

## Definition of Done (đo được)
- Test khẳng định **mọi** descriptor đã đăng ký là tenant-scoped (không chỉ 13).
- Guard chặn cache name nhạy cảm.
- Focused test xanh.

## Không làm (chống scope creep)
- Không thêm cache mới / đổi TTL.
- Production distributed-cache hit/miss SLO evidence = deploy-time (dashboard contract đã test ở BE-010); ngoài scope code.

## Final status — 2026-07-24 (Hoàn thành)

Xác minh trên source: mọi cache đã tenant-scoped, có metrics hit/miss bounded, invalidation + stampede đã test. Gap còn lại là 2 guard unit-level (không phải deploy-only).

- **Completeness guard**: `descriptor()` helper hardcode `tenantRequired=true` cho cả 25 descriptor, nhưng test cũ chỉ khóa 13 cache. Thêm test iterate `getCacheNames()` assert **mọi** descriptor tenant-scoped → chống future cache set `tenantRequired=false` lọt lưới.
- **Sensitive-name guard**: thêm test assert không cache nào tên chứa token/password/secret/credential/session/jwt/refresh. Role/permission metadata cache được phép (không phải token).
- Bỏ assertion "strongly typed" cho toàn bộ (một số cache dùng generic value type hợp lệ) — chỉ khóa tenant-scoping + sensitive-name, đúng invariant DoD.

### Changed
- `test/.../platform/cache/BusinessCacheIsolationIntegrationTest.java` — +2 test (every-descriptor tenant-scoped; no-secret-name).

### Evidence
- Focused: `mvnw -Dtest=BusinessCacheIsolationIntegrationTest test` → 3 tests, 0 failures, BUILD SUCCESS.
- Xác minh: `CacheConfig` 25 descriptor, key format `erg:cache:v2:{tenantHash}:...`, `ErgTieredCache.redisKey()` trả null nếu tenantRequired && tenant==null (không ghi L2 cross-tenant). Metrics `erg.cache.operations{cache,tier,operation,outcome}` bounded (RedisHealthAndMetricsTest).

### Intentionally not done
- Không thêm assertion strongly-typed toàn bộ (generic value type hợp lệ cho vài cache).
- Production distributed-cache hit/miss SLO là deploy-time evidence (dashboard contract đã test ở BE-010); ngoài scope sandbox.
