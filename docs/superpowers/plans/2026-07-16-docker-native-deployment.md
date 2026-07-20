# Kế hoạch triển khai Docker Native Image

> **Dành cho agent triển khai:** BẮT BUỘC sử dụng `superpowers:executing-plans` để thực hiện lần lượt từng task. Các bước dùng checkbox (`- [ ]`) để theo dõi.

**Mục tiêu:** Tạo quy trình Docker Compose cho local IDE trên Windows và triển khai nhiều backend native Linux amd64 sau Nginx mà server không phải build.

**Kiến trúc:** Docker Compose base chứa PostgreSQL và Redis; overlay development thêm Nginx proxy đến IDE qua `host.docker.internal`; overlay native thêm backend image và Nginx dùng Docker DNS. Native image được build trên Docker Desktop, gắn tag bất biến, push lên Registry rồi server chỉ pull và recreate.

**Công nghệ:** Spring Boot 4, Java/GraalVM 25, Docker BuildKit, Docker Compose, PostgreSQL 17, Redis 7.4, Nginx.

---

## Cấu trúc file

- `Dockerfile`: multi-stage build native Linux amd64 và runtime non-root.
- `.dockerignore`: loại source control, output build, secret và tài liệu khỏi build context.
- `ops/docker/compose.yml`: PostgreSQL, Redis, volume và network dùng chung.
- `ops/docker/compose.dev.yml`: Nginx local proxy tới backend chạy trong IDE.
- `ops/docker/compose.native.yml`: backend native có thể scale và Nginx production-like.
- `ops/docker/nginx/dev.conf`: upstream `host.docker.internal:1606`.
- `ops/docker/nginx/native.conf`: upstream động qua Docker DNS.
- `ops/docker/.env.dev.example`: biến cho PostgreSQL/Redis local và Spring Boot chạy từ IDE.
- `ops/docker/.env.production.example`: image tag và toàn bộ biến Spring Boot cần cho container/server.
- `scripts/docker.ps1`: lệnh `dev`, `build`, `push`, `up`, `deploy`, `down`, `logs`, `config` trên Windows.
- `scripts/docker.sh`: các lệnh tương đương trên Linux.
- `ops/docker/README.md`: hướng dẫn bằng tiếng Việt.

### Task 1: Tạo kiểm tra hợp đồng cấu hình

- [ ] Tạo `scripts/verify-docker-config.ps1` kiểm tra file bắt buộc, `linux/amd64`, không có `build:` trong đường deploy, upstream IDE, Docker DNS, health check và biến Spring Boot bắt buộc.
- [ ] Chạy `powershell -ExecutionPolicy Bypass -File scripts/verify-docker-config.ps1` và xác nhận FAIL vì các file triển khai chưa tồn tại.

### Task 2: Tạo image native

- [ ] Tạo `.dockerignore` loại `.git`, `.idea`, `.env`, `target`, `docs`, `load` và dữ liệu local.
- [ ] Tạo `Dockerfile` với builder `ghcr.io/graalvm/native-image-community:25`, chạy `./mvnw -Pnative -DskipTests native:compile`, sau đó copy `target/erg-spring` sang Debian slim runtime, cài CA certificate/curl, tạo user `erg`, expose 1606 và chạy native binary.
- [ ] Chạy kiểm tra hợp đồng để xác nhận các assertion image đã PASS.

### Task 3: Tạo Compose local và native

- [ ] Thay `ops/docker/compose.production.yml` cũ bằng `compose.yml`, `compose.dev.yml`, `compose.native.yml` để tránh giữ hai nguồn cấu hình production xung đột.
- [ ] Cấu hình PostgreSQL 17 và Redis 7.4 với named volume, health check, private network; local publish `127.0.0.1:5432` và `127.0.0.1:6379`.
- [ ] Cấu hình backend chỉ dùng `image: ${ERG_APP_IMAGE}`, không có `build:`, nhận env Spring Boot, chờ PostgreSQL/Redis healthy và có Actuator readiness health check.
- [ ] Cấu hình Nginx native resolve service `backend` qua `127.0.0.11`, retry upstream và expose cổng `${ERG_HTTP_PORT:-8080}`.
- [ ] Cấu hình Nginx dev proxy tới `host.docker.internal:1606`.
- [ ] Render bằng `docker compose ... config` cho cả dev và native; xác nhận không lỗi cú pháp và production không có build directive.

### Task 4: Tạo đầy đủ file env Spring Boot

- [ ] Tạo `.env.dev.example` với PostgreSQL/Redis local, datasource `jdbc:postgresql://localhost:5432/erg`, Flyway, port 1606, cookie local và secret placeholder.
- [ ] Tạo `.env.production.example` với `ERG_APP_IMAGE=registry/name:immutable-tag`, datasource `jdbc:postgresql://postgres:5432/erg`, Redis service URL, profile prod, Flyway, pool size, worker, Actuator, CORS, cookie và secret placeholder.
- [ ] Bảo đảm `.gitignore` chỉ cho phép commit file `*.example`, không commit `.env` thật.
- [ ] Chạy kiểm tra hợp đồng và xác nhận các biến bắt buộc đều có mặt.

### Task 5: Tạo lệnh vận hành Windows và Linux

- [ ] Tạo `scripts/docker.ps1` xác thực action, image tag và số replica; `build` dùng `docker buildx build --platform linux/amd64 --load`; `push` dùng `--push`; `deploy` chỉ chạy pull/up và không gọi build.
- [ ] Tạo `scripts/docker.sh` với cùng giao diện; server dùng `deploy [replicas]`.
- [ ] Thêm `config` để render cả hai stack và `logs` để theo dõi Nginx/backend.
- [ ] Dùng PowerShell parser và `bash -n` nếu Bash khả dụng để kiểm tra cú pháp.

### Task 6: Viết hướng dẫn và kiểm chứng

- [ ] Viết `ops/docker/README.md` bằng tiếng Việt, gồm copy env, chạy `dev`, cấu hình IDE, build/push/up local, login Registry, deploy/rollback server và lưu ý Docker Desktop WSL2 cần ít nhất khoảng 8 GB RAM cho native build.
- [ ] Chạy `scripts/verify-docker-config.ps1` và yêu cầu toàn bộ assertion PASS.
- [ ] Chạy `docker compose config` cho dev/native.
- [ ] Nếu Docker daemon hoạt động, chạy Nginx config test và khởi động PostgreSQL/Redis để kiểm tra health; nếu daemon không hoạt động, báo rõ giới hạn này.
- [ ] Chạy `git diff --check` và rà lại diff chỉ gồm hạ tầng/tài liệu dự kiến.
