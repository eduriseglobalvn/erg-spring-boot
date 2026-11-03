# ERG Spring — Second Brain (chỉ mục)

Tài liệu này là "bộ nhớ" của repo `erg-spring`: mô tả code làm gì, luồng nghiệp vụ ra sao, và những chỗ nghi ngờ cần kiểm tra. Đọc file này trước, rồi mở file module tương ứng khi cần chi tiết.

**Quy ước:** mọi nhận định trong các file module đều dựa trên code đã đọc, có đường dẫn file. Mục đánh dấu **"nghi ngờ"** là chưa chạy hoặc chưa xác minh lại, không được coi là lỗi xác nhận.

## Bản đồ module

| # | File | Phạm vi |
|---|------|---------|
| 01 | [01-learning-delivery.md](modules/01-learning-delivery.md) | LMS, quiz, e-learning, khóa học, học liệu, chuẩn và kết quả học tập |
| 02 | [02-lcms-calendar-reporting.md](modules/02-lcms-calendar-reporting.md) | LCMS (quiz authoring, media), lịch dạy/bulk, báo cáo |
| 03 | [03-identity-access.md](modules/03-identity-access.md) | Đăng nhập, refresh token, xác minh, user, role/permission, session |
| 04 | [04-crm-school-enterprise.md](modules/04-crm-school-enterprise.md) | CRM trường học, deal, SEO sales, school/organization, connector doanh nghiệp |
| 05 | [05-public-web-content-community.md](modules/05-public-web-content-community.md) | CMS công khai, catalog, SEO, crawler, bot, review, tài liệu |
| 06 | [06-notifications-audit-outbox.md](modules/06-notifications-audit-outbox.md) | Thông báo (push, email), audit log, outbox |
| 07 | [07-platform-infrastructure.md](modules/07-platform-infrastructure.md) | Security filter, cache, GraphQL setup, rate limit, webhook, cấu hình |
| 08 | [08-database-and-contracts.md](modules/08-database-and-contracts.md) | 105+ migration SQL, RLS, schema GraphQL và OpenAPI |
| 09 | [09-docs-and-runbooks.md](modules/09-docs-and-runbooks.md) | Tài liệu `docs/`: kiến trúc, runbook, báo cáo, tài liệu lỗi thời |

## Bức tranh chung (rút từ các module)

- **Kiến trúc:** Spring Boot 4 (Java), Postgres là nguồn dữ liệu chính, Redis cho cache/session, outbox để gửi email và thông báo đáng tin cậy.
- **Đa tenant:** dữ liệu được cô lập theo `tenant_id`, có RLS ở Postgres. Một số bảng và cache còn chưa theo tenant (xem module 03, 08).
- **Phân quyền:** route trong `SecurityConfig` ánh xạ sang permission như `crm.school.read`, `users.manage`, `rbac.role.assign`; ngoài ra có role và scope (trường, trung tâm).
- **Prototype:** nhiều module CMS, catalog, crawler, bot, recruitment chỉ là store trong bộ nhớ, bật bằng profile `prototype` (module 05, 04). Production tắt các store này bằng cấu hình.
- **Background job:** outbox worker (SKIP LOCKED, lease), worker báo cáo, job dọn retention (module 06, 07).

## Vấn đề nghi ngờ ưu tiên (chưa xác minh toàn bộ)

Các mục dưới đây lấy từ notes của subagent. Mục đã được mình đối chiếu với code được ghi trong báo cáo review; các mục còn lại cần kiểm tra trước khi sửa.

1. **Khóa mã hóa outbox có giá trị mặc định công khai** — `service/outbox/OutboxPayloadCipher.java` (đã xác minh).
2. **TLS mail bị đảo mặc định** — `application.yml`, thay đổi chưa commit (đã xác minh).
3. **SEO user xem được dữ liệu SEO khác** — `controller/crm/CrmSchoolController.java` (đã xác minh đường đi, cần test thực tế) — module 04.
4. **Kiểm tra admin role quá rộng** — `SessionService` (đã xác minh) — module 03.
5. **Thông báo lịch xuất bản bị từ chối** do trùng key `eventType` — module 06 (đã xác minh key trùng, chưa đọc đường từ chối).
6. **Refresh token có thể bị rollback khi phát hiện replay** — `AuthRefreshUseCase` trong `@Transactional` — module 03 (nghi ngờ).
7. **Role và permission dùng chung giữa các tenant** — module 03 (nghi ngờ).
8. **Khối cấu hình `erg.*` nằm trong profile staging** — module 07 (nghi ngờ, cần kiểm tra production).
9. **Rate limit GraphQL dùng `operationName` do client gửi** — module 07 (nghi ngờ).
10. **Multi-quiz assignment chỉ chạy quiz đầu tiên** — module 01 (đọc code, chưa chạy).
11. **Chấm bài tự luận chưa có đường xử lý** — attempt kẹt ở `needs_review` — module 01 (đọc code).
12. **Migration có thay đổi không đảo ngược và mật khẩu cố định trong migration** — module 08 (nghi ngờ, cần kiểm tra V7 và các lệnh DROP/CASCADE).
13. **Bulk calendar có thể hủy event cũ mà không tạo batch mới** — module 02 (nghi ngờ).
14. **Báo cáo không kiểm tra người yêu cầu** — module 02 (nghi ngờ).
15. **Tài liệu có chuỗi giống credential** — module 09 (nghi ngờ, không chép lại ở đây).

## Phạm vi và giới hạn

- Notes được viết bởi 9 subagent, mỗi agent phụ trách một nhóm. Mức độ đọc từng file khác nhau: một số module đọc gần hết, một số chỉ đọc phần chính (module 03 đọc ~130/321 file, module 01 đọc ~75 file cốt lõi).
- Không ai chạy app, chạy test, hay chạy migration trong quá trình viết notes.
- Các chỗ chưa đọc được ghi rõ trong từng file module, mục "Câu hỏi còn mở".
- Các thay đổi chưa commit trong working tree (mail, SessionService, AccountVerificationService, GraphQL limit, V108/V109) được review riêng, không nằm trong notes này.

## Cách cập nhật

Khi code đổi, sửa đúng file module và đặt lại mục "nghi ngờ" nếu đã xác minh. Đừng thêm thông tin không có trong code; ghi rõ khi chưa chắc.
