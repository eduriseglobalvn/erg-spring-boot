# BE-039 — Chuẩn hóa đăng ký JPA và inventory dormant entity

## Mục tiêu

Biến đăng ký entity/repository thành một convention có thể kiểm tra, có inventory enabled/dormant cho toàn bộ persistence model, không tiếp tục sửa hai allowlist trung tâm mà không có bằng chứng. Giữ nguyên table name, schema, Flyway append-only và behavior runtime.

## Phạm vi

- Inventory mọi `@Entity` và repository domain type dưới `model/` và `repository/`, phân loại enabled/dormant, owner và lý do.
- Kiểm tra enabled entity ↔ repository domain type không trùng/mất trong Postgres metamodel khi integration runtime khả dụng.
- Tạo source-level canonical registration test để fail khi entity/repository mới không được phân loại.
- Giữ `PostgresJpaConfig` tương thích trong slice này; chỉ thay đổi allowlist/config nếu test chứng minh entry đã dormant hoặc duplicate.
- Không de-annotate entity, đổi table, di chuyển package hay tạo migration nếu chưa có owner/rollback evidence.

## TDD

1. RED: thêm test inventory đọc `@Entity`/repository domain types và yêu cầu từng type có classification/owner trong resource catalog.
2. RED: thêm test duplicate/missing canonical registration và kiểm tra entity enabled không có repository domain type ngoài allowlist đã ghi lý do.
3. GREEN: tạo catalog bất biến + loader/test utility; sửa registration tối thiểu để canonical catalog là nguồn kiểm tra duy nhất, giữ startup behavior.
4. GREEN: nếu phát hiện dormant entity, đánh dấu explicit dormant thay vì âm thầm bật/de-annotate; ghi migration follow-up.
5. VERIFY: focused config/architecture tests, compile, Modulith suite, OpenAPI/GraphQL checks; phân loại Testcontainers/PostgreSQL/native blockers nếu full verify không chạy được.

## Tiêu chí hoàn thành

- Catalog bao phủ toàn bộ entity/repository domain type hiện có, có trạng thái và owner.
- Test fail-closed cho entity/repository mới hoặc duplicate registration.
- Không đổi schema/table/REST/GraphQL behavior trong slice; `mvnw -DskipTests compile` và focused gates xanh.
- Workbook evidence và scope path cập nhật; chỉ đánh dấu hoàn thành khi inventory + ratchet thực sự chạy xanh.
