# BE-037 — Tách User/Account persistence khỏi module tiêu thụ

## Mục tiêu

Giảm fan-out của `model.users.User` và `UserRepository` bằng account owner cùng các projection/query ports nhỏ. Module khác chỉ dùng scalar account IDs hoặc immutable public projections; không lộ JPA entity/repository qua public APIs.

## Phạm vi pilot

- `users` public package: bổ sung account identity/projection records và named query ports cho login/session, authorization, admin profile và teacher discovery.
- Legacy `UserService`/`UserRepository` tiếp tục là adapter implementation; không đổi bảng, khóa, tenant/RLS hay password/session behavior.
- Chuyển các caller dễ cô lập nhất (calendar teacher lookup, authorization/session reads) sang port/projection; inventory caller còn lại thành compatibility allowlist có owner.
- Architecture ratchet cấm import `model.users.User`/`repository.auth.user.UserRepository` từ module public/internal ngoài named adapters.

## TDD

1. RED: thêm source/ArchUnit test quét foreign-module imports và yêu cầu projection port không chứa `@Entity`/repository.
2. RED: thêm contract tests cho login/session, authorization, teacher discovery giữ nguyên null/tenant semantics.
3. GREEN: tạo immutable `UserAccountProjection` và các query ports nhỏ; adapter ban đầu bọc repository/service hiện có.
4. GREEN: migrate caller pilot, giữ deprecated compatibility constructors cho legacy tests.
5. VERIFY: focused user/auth/authorization/calendar regression, architecture/Modulith suite, compile, contract checksum; classify DB/infra blockers.

## Không làm

- Không tạo generic `Person`/`Account` superclass.
- Không đổi schema hoặc backfill dữ liệu trong pilot.
- Không đánh dấu hoàn thành nếu public caller vẫn trả entity hoặc tenant/authorization semantics thay đổi.
