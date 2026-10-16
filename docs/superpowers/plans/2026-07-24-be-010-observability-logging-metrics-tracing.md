# BE-010 — Chuẩn hóa logging, metrics và tracing

## Task
- **ID:** BE-010 (ERG-SPRING, P1, Observability)
- **Goal:** Gắn requestId, operationName, tenant scope, duration và outcome; không log token/PII. Có dashboard latency/error theo endpoint và GraphQL operation.
- **Dependency:** BE-006 (Hoàn thành) — đã xác minh.

## Baseline (source hiện tại)
- Branch: codex/spring-modulith-modernization (dirty; nhiều WIP).
- WIP đã có trong worktree (untracked):
  - `ops/observability/grafana/erg-backend-slo.json` — Grafana SLO dashboard (REST p95/error, GraphQL p95, DB pool, cache, modulith lag).
  - `docs/runbooks/observability-slo.md` — SLO runbook.
  - `src/test/java/com/erg/ergspringboot/platform/performance/ObservabilityDashboardContractTest.java` — lock dashboard + tracing config.
- Đã có sẵn (committed):
  - `ApiAccessLogFilter` — log method/path/status/duration + requestId/tenantId/userId; mask UUID, numeric id dài, token/secret/password/credential ở cả path lẫn query.
  - `GraphQlContextInterceptor` — timer `graphql.operation.duration` tag `operation`+`result`.
  - `CacheMetrics` — counter `erg.cache.operations` tag cache/tier/operation/outcome.
  - `application.yml` — `management.tracing.sampling.probability` (0.1 default) + zipkin endpoint; prometheus actuator exposure.

## Vấn đề (gap theo DoD)
Dashboard panel "Cache hit/miss and admission" query metric **không tồn tại**:
- `erg_cache_requests_total` (tag `result`) — app thực tế emit `erg.cache.operations` → `erg_cache_operations_total` (tag `outcome`).
- `erg_cache_load_rejected_total` — không có emitter nào.
→ Panel cache sẽ rỗng (no data) trên Grafana; contract test chỉ assert chuỗi `erg_cache` nên không bắt được sai lệch tên.

## Invariants giữ nguyên
- Không đổi emitter `CacheMetrics` (`erg.cache.operations`) — đã có test `RedisHealthAndMetricsTest` khóa tag bounded.
- Không đổi tên timer GraphQL/REST (đúng chuẩn Micrometer).
- Không đổi access-log masking (đã an toàn PII/token).
- Không đổi tracing/actuator config.

## File create/modify
1. MODIFY `ops/observability/grafana/erg-backend-slo.json`
   - Panel 5: query `erg_cache_operations_total` thật (get hit/miss theo outcome; admission = size_bypass|decode_failure) thay cho tên metric không tồn tại.
2. MODIFY `src/test/java/.../ObservabilityDashboardContractTest.java`
   - Assert `erg_cache_operations_total` (tên metric app thực emit) thay cho `erg_cache` chung chung, để chống regression tên metric.

## Các bước
1. Xác minh emitter thực tế (`erg.cache.operations`, outcomes: hit/miss/stored/size_bypass/decode_failure) và GraphQL timer tag.
2. Sửa dashboard panel cache sang metric đúng.
3. Sửa contract test lock tên metric đúng.
4. Validate JSON + chạy `ObservabilityDashboardContractTest`.

## Command & expected
- `mvnw -Dtest=ObservabilityDashboardContractTest test` → BUILD SUCCESS.

## Rollback
- Chỉ 2 file (dashboard JSON + contract test). Revert cả hai là đủ; không đổi source/schema.

## Definition of Done (đo được)
- Dashboard có panel latency/error theo REST route (uri/method) và GraphQL operation — signal khớp metric thật.
- Panel cache query metric app thực emit (không rỗng).
- Access log có requestId/tenant/duration/outcome, mask token/PII (đã có + xác minh).
- Tracing sampling + zipkin export cấu hình sẵn.
- Contract test lock cấu trúc dashboard + tracing xanh.

## Không làm (chống scope creep)
- Không thêm cache load-rejected counter mới (không có admission-control kiểu Caffeine ở tầng này; dùng size_bypass/decode_failure làm admission signal thật).
- Không đổi emitter/tag hiện có.
- Không dựng Grafana/Prometheus provisioning stack (ngoài scope; dashboard JSON + runbook là đủ evidence).
