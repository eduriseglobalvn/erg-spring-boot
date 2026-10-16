# BE-052: typed workspace/role/scope/tenant session contract

## Mục tiêu

Xuất một session contract canonical để FE dùng cho shared auth và workspace
switching. Domain/path chỉ chọn workspace trình bày; capability và scope được
backend cấp từ authenticated principal. `portals`/`lcms`/`lms`/`elearning` chỉ
còn ở compatibility boundary, không được dùng làm quyền mới.

## Phạm vi

- `SessionBootstrapResponse` thêm `tenantId` và `workspaces` canonical, giữ
  `portals` additive để FE cũ migrate dần.
- `WorkspaceCapabilityMapper` map wildcard, permission namespace, role và
  legacy portal về `ADMIN|TEACHER|STUDENT|CRM` theo một rule deterministic.
- `ErgPrincipalService`, OIDC bridge và `SessionService` derive capability từ
  quyền/role đã xác thực; không coi `portal` do request login gửi vào là quyền.
- OIDC bridge lấy tenant từ claim đã ký (`tenant_id`/`tenantId`/`tenant`) hoặc
  tenant cấu hình server cho single-ERG; `X-Tenant-ID` chỉ còn là consistency
  check và mismatch bị từ chối, không còn là nguồn tenant authorization.
- BFF `/api/v1/auth/session` trả cùng vocabulary workspace, tenant, role và
  scope projection; không trả token.
- Tests khóa multi-role, fail-closed unknown namespace, tenant/scope parity và
  backward-compatible legacy `portals` projection.

## Không làm trong slice này

- Không đổi cookie/CORS/CSRF transport (BE-053).
- Không thêm database migration hoặc custom-domain/multi-tenant billing.
- Không hand-edit OpenAPI/GraphQL snapshot; sau khi runtime contract publish,
  dùng export/sync chính thức và chạy breaking-change gate.

## TDD / verification

1. Viết failing tests cho mapper wildcard/role/permission và session response
   fields.
2. Implement mapper + response DTO + service/controller projection.
3. Chạy focused Maven tests (security mapper, session service, BFF controller).
4. Publish/sync OpenAPI từ runtime nếu môi trường cho phép; chạy
   `export-openapi-contract --check`, GraphQL check và contract library tests.
5. Chạy `mvnw.cmd verify` hoặc ghi rõ blocker nếu test hạ tầng/DB không khả dụng.

## Acceptance evidence

- Authenticated multi-role account nhận được tất cả workspace backend cấp,
  không phụ thuộc hostname hoặc `portal` query.
- `tenantId`, role/permission, `workspaces`, `currentScope` và manageable
  scopes nằm trong session response; scope không được lấy từ client để mở rộng.
- FE có thể chuyển Admin/Teacher/Student/CRM bằng cùng session; legacy alias
  chỉ dùng redirect/compatibility.
