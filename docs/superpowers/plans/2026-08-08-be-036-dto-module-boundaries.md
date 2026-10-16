# BE-036 — DTO và mapper thuộc module sở hữu

## Mục tiêu

Đóng pilot boundary cho `learningcontent` và quiz bundle mà không thay đổi JSON contract đang phát hành. Controller/resolver/public facade không được lộ JPA entity, repository projection hoặc domain type nằm ngoài module sở hữu; DTO public phải nằm ở module root/api và assembler phải là pure code dùng được cho read/write.

## Phạm vi

- `LearningContentController` và facade hiện tại: trả module-owned response DTO, giữ nguyên field/name/nullability/serialization.
- `LcmsQuizBundleController`: bọc response bằng envelope typed gồm version/signature/payload bounded; không đưa persistence object vào payload.
- Architecture/contract tests: fail-closed khi public API import entity/repository hoặc common domain type ngoài allowlist.
- Cập nhật OpenAPI/GraphQL chỉ khi generated output thay đổi; không sửa vendored contract bằng tay.
- Không di chuyển schema, không đổi authorization/tenant/cache semantics, không mở rộng mapper framework.

## Ngoài phạm vi / điều kiện dừng

- Không big-bang migrate toàn bộ controller; các legacy seam còn lại phải được đánh dấu rõ và chuyển thành task phụ thuộc.
- Không phá byte-for-byte JSON contract. Nếu pilot cho thấy contract hiện tại phụ thuộc entity leak, tạo compatibility DTO/adapter có `@Deprecated` thay vì xóa ngay.

## Kế hoạch TDD

1. **RED — architecture boundary**: thêm test quét public controller/facade và cấm import `@Entity`, repository, `model` ngoài allowlist; thêm test xác nhận quiz envelope có version/signature/payload typed.
2. **RED — contract snapshots**: chạy test hiện hữu của Learning Content và quiz bundle để chốt baseline serialization, nullability và status code.
3. **GREEN — learningcontent**: giới thiệu module-owned response records/API và một pure assembler; controller chỉ gọi facade/assembler, không nhận entity/projection.
4. **GREEN — quiz bundle**: giới thiệu typed envelope và payload DTO trong module lcms/quiz; giữ signature/hash semantics hiện tại, map bounded JSON qua immutable payload.
5. **REFACTOR — common cleanup**: chỉ giữ primitive transport types trong `common`; xoá import không cần, cập nhật package-info/module docs và allowlist test.
6. **VERIFY**: focused architecture + controller/contract tests, compile, OpenAPI/GraphQL checksum gates, rồi `mvnw verify` phân loại rõ lỗi môi trường (Docker/PostgreSQL/JSONB) với lỗi regression.

## Tiêu chí hoàn thành

- Architecture ratchet xanh và không có public pilot trả entity/repository projection.
- Learning Content JSON trước/sau giống nhau trên contract fixtures.
- Quiz bundle có envelope typed, version được kiểm tra và payload không chứa persistence type.
- `mvnw -DskipTests compile`, focused tests và contract checksum gates xanh.
- Workbook `Tất cả Task` cập nhật evidence, progress và scope path; không đánh dấu hoàn thành nếu còn regression hoặc blocker ngoài phạm vi chưa ghi rõ.
