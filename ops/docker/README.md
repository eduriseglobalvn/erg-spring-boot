# Docker cho local, test và production

Tài liệu này quy định bốn quy trình riêng biệt. Tất cả lệnh Windows được chạy tại thư mục gốc `D:\ERG\erg-spring`. Tất cả lệnh Linux được chạy tại thư mục gốc của dự án trên server.

## 1. Phân biệt các chế độ

| Chế độ | Spring Boot chạy ở đâu? | Dịch vụ Docker | File env | Lệnh chính |
| --- | --- | --- | --- | --- |
| Local dev | IDE trên Windows, port `1606` | PostgreSQL, Redis, Nginx | `ops/docker/.env.dev` | `.\scripts\docker.ps1 dev` |
| Local Docker test | Native executable trong Linux container | PostgreSQL, Redis, Nginx, nhiều backend | `ops/docker/.env.production` | `.\scripts\docker.ps1 test 2 -Image erg-spring:local-native` |
| Build release local | BuildKit/GraalVM trong Linux container | Không chạy stack ứng dụng | Không dùng env runtime | `.\scripts\docker.ps1 build -Image <registry/image:tag>` |
| Publish release | BuildKit/GraalVM trong Linux container | Không chạy stack ứng dụng | Không dùng env runtime | `.\scripts\docker.ps1 release -Image <registry/image:git-sha>` |
| Production | Native executable trong Linux container | PostgreSQL, Redis, Nginx, nhiều backend | `ops/docker/.env.production` | `./scripts/docker.sh prod 3` |

Ý nghĩa các action:

- `dev`: chỉ bật hạ tầng local. Không build và không chạy source Spring Boot trong Docker.
- `test`: chạy image native đã build ở local để kiểm tra gần giống production. Không tự build lại image.
- `build`: compile source thành native image `linux/amd64`. Không bật stack.
- `up`: bí danh của `test`.
- `push`: đẩy image đã build lên Registry.
- `release`: kiểm tra image tag là Git SHA bất biến rồi build và push image `linux/amd64` bằng một lệnh. Action này không chạy server và không cần runtime secret.
- `prod`: server pull digest image rồi chạy với `--no-build --wait`; lỗi health sẽ rollback về digest trước đó. Server tuyệt đối không compile.
- `deploy`: bí danh của `prod`.
- `logs`: xem log Nginx và các backend của stack native.
- `down`: dừng cả stack dev và stack native, không xóa volume dữ liệu.
- `config`: kiểm tra cú pháp cả hai cấu hình Compose.

## 2. Local dev trên Windows

Mục tiêu của chế độ này là sửa code nhanh bằng IDE. Docker chỉ chạy PostgreSQL, Redis và Nginx.

### Bước 1: bật hạ tầng

```powershell
.\scripts\docker.ps1 dev
```

Lần đầu chạy, script tự tạo `ops/docker/.env.dev` từ `.env.dev.example` và không ghi đè file đã tồn tại.

### Bước 2: cấu hình Spring Boot trong IDE

Dùng các biến trong `ops/docker/.env.dev` cho Run Configuration của IDE. Dự án cũng tự đọc file `.env` ở thư mục chạy, nên có thể đưa các giá trị tương ứng vào `.env` tại root nếu IDE không hỗ trợ env file.

Các biến kết nối quan trọng:

```dotenv
SPRING_PROFILES_ACTIVE=dev
SERVER_PORT=1606
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/erg
SPRING_DATASOURCE_USERNAME=erg
SPRING_DATASOURCE_PASSWORD=erg-local-password
SPRING_DATA_REDIS_URL=redis://:erg-local-redis-state@localhost:6379
ERG_REDIS_STATE_URL=redis://:erg-local-redis-state@localhost:6379
ERG_REDIS_CACHE_URL=redis://:erg-local-redis-cache@localhost:6380
FLYWAY_ENABLED=true
```

Khởi động `ErgSpringApplication` trong IDE với port `1606`.

### Bước 3: gọi API qua Nginx

```powershell
Invoke-RestMethod http://localhost:8080/ready
```

Luồng request:

```text
Client -> localhost:8080 -> Nginx trong Docker
       -> host.docker.internal:1606 -> Spring Boot trong IDE
```

Địa chỉ local:

| Dịch vụ | Địa chỉ |
| --- | --- |
| API qua Nginx | `http://localhost:8080` |
| Spring Boot trong IDE | `http://localhost:1606` |
| PostgreSQL | `localhost:5432`, database/user `erg` |
| Redis state/session/rate-limit | `localhost:6379` |
| Redis business cache | `localhost:6380` |

Xem trạng thái hạ tầng:

```powershell
docker compose --project-directory ops/docker --env-file ops/docker/.env.dev `
  -f ops/docker/compose.yml -f ops/docker/compose.dev.yml ps
```

## 3. Local test giống production

Chế độ này build source thành native image Linux rồi chạy toàn bộ ứng dụng trong Docker. Không có hot reload; mỗi thay đổi source cần chạy lại `build`.

### Bước 1: tạo env chạy container

```powershell
Copy-Item ops/docker/.env.production.example ops/docker/.env.production
```

Nếu file đã tồn tại thì không copy đè. Khi chỉ test local, có thể giữ các giá trị placeholder cho tích hợp đang tắt, nhưng PostgreSQL password, Redis password và các giá trị Spring phải đồng nhất trong file.

### Bước 2: build native image local

```powershell
.\scripts\docker.ps1 build -Image erg-spring:local-native
```

Docker sẽ:

1. Dùng GraalVM 25 bên trong Linux builder container.
2. Compile source với profile AOT `prod`.
3. Tạo native executable tương thích x86_64 bằng `-march=compatibility`.
4. Đóng executable vào runtime image Linux `amd64` chạy bằng user `erg`.

Windows không cần cài Maven, JDK hoặc GraalVM để thực hiện build này. Native build dùng nhiều tài nguyên; nên cấp Docker Desktop khoảng 16 GB RAM nếu máy cho phép.

### Bước 3: chạy nhiều backend sau Nginx

Nếu stack dev vẫn dùng `8080`, đổi cổng native test sang `18080`:

```powershell
$env:ERG_HTTP_PORT = "18080"
.\scripts\docker.ps1 test 2 -Image erg-spring:local-native
```

Số `2` là số backend. Có thể dùng `test 3`, `test 4`, v.v. Backend chỉ mở port `1606` trong Docker network; chỉ Nginx publish port ra máy Windows.

### Bước 4: kiểm tra

```powershell
Invoke-RestMethod http://localhost:18080/nginx-health
Invoke-RestMethod http://localhost:18080/ready
.\scripts\docker.ps1 logs
```

Kiểm tra đủ số replica:

```powershell
$env:ERG_HTTP_PORT = "18080"
docker compose --project-directory ops/docker --env-file ops/docker/.env.production `
  -f ops/docker/compose.yml -f ops/docker/compose.native.yml ps
```

### Test mã nguồn và test runtime Docker

`mvn test` là unit/integration test của mã nguồn. Lệnh này không khởi động Nginx và không kiểm tra native image:

```powershell
.\mvnw.cmd -Dspring.profiles.active=test test
```

`docker.ps1 test` là smoke test runtime: chạy đúng image Linux amd64 đã build, kết nối PostgreSQL/Redis bằng tên service Docker và đi qua Nginx. Quy trình local đầy đủ:

```text
1. dev:         .\scripts\docker.ps1 dev
2. test mã:     .\mvnw.cmd -Dspring.profiles.active=test test
3. build:       .\scripts\docker.ps1 build -Image <registry>/erg-spring:<git-sha>
4. test Docker: .\scripts\docker.ps1 test 2 -Image <registry>/erg-spring:<git-sha>
5. release:     .\scripts\docker.ps1 release -Image <registry>/erg-spring:<git-sha>
6. prod:        ./scripts/docker.sh prod 3   # digest target + rollback digest in .env.production
```

## 4. Build và push bản release tại Windows

Luôn dùng tag bất biến, ví dụ Git commit SHA. Không chỉ dùng `latest` cho production vì khó rollback và khó xác định phiên bản đang chạy.

Ví dụ với GitHub Container Registry:

```powershell
$tag = git rev-parse --short HEAD
$image = "ghcr.io/your-org/erg-spring:$tag"

docker login ghcr.io
.\scripts\docker.ps1 release -Image $image
```

release chạy docker buildx build --platform linux/amd64 --push trực tiếp, nên không tạo image tạm trên máy và không cần lệnh push thứ hai. Action chỉ chấp nhận tên có dạng registry/repository:<7-64 ký tự Git SHA>; các tag trôi nổi như latest, main, dev bị từ chối để giữ khả năng rollback. Sau bước này Registry đã chứa image hoàn chỉnh cho Linux x86_64. Không cần và không được build lại trên server.

Trên Linux có thể dùng cùng workflow:

```bash
export ERG_APP_IMAGE="ghcr.io/your-org/erg-spring:$(git rev-parse --short HEAD)"
docker login ghcr.io
./scripts/docker.sh release
```

push vẫn tồn tại cho trường hợp image đã được build trước, nhưng cũng áp dụng cùng guard Git-SHA. `prod/deploy` chỉ nhận `registry/repository@sha256:<64 hex digest>`, chạy `pull` rồi `up --no-build --wait --wait-timeout 180`. Nếu health gate thất bại, script tự pull/chạy `ERG_ROLLBACK_IMAGE` (một digest khác); nếu rollback cũng lỗi, lệnh trả mã lỗi để hệ thống vận hành can thiệp. Tag Git-SHA chỉ dùng cho build/release, không dùng trực tiếp để chạy production.

## 5. Deploy production trên Linux

Server cần Docker Engine, Docker Compose v2 và quyền pull image. Server không cần JDK, Maven hoặc GraalVM.

Các file tối thiểu cần có trên server:

```text
ops/docker/compose.yml
ops/docker/compose.native.yml
ops/docker/nginx/native.conf
ops/docker/.env.production
scripts/docker.sh
```

### Deploy lần đầu

```bash
cp ops/docker/.env.production.example ops/docker/.env.production
chmod 600 ops/docker/.env.production
nano ops/docker/.env.production
```

Phải thay ít nhất các giá trị sau:

- `ERG_APP_IMAGE`: digest của image vừa push, ví dụ `ghcr.io/your-org/erg-spring@sha256:<64-hex-digest>`.
- `ERG_ROLLBACK_IMAGE`: digest known-good trước đó và phải khác `ERG_APP_IMAGE`.
- `POSTGRES_PASSWORD`, `REDIS_STATE_PASSWORD` và `REDIS_CACHE_PASSWORD`.
- `SECRET_AUTH__JWT_SECRET` và `SECRET_AUTH__JWT_REFRESH_SECRET`.
- `HTTP__CORS__ALLOWED_ORIGINS` và `AUTH__COOKIE__DOMAIN`.
- Secret của SMTP, Firebase, R2 hoặc AI nếu tích hợp tương ứng được bật.

Chạy ba backend:

```bash
docker login ghcr.io
chmod +x scripts/docker.sh
./scripts/docker.sh config
./scripts/docker.sh prod 3
./scripts/docker.sh logs
```

`prod` (và bí danh `deploy`) chạy pull và health-gated start:

```text
docker compose pull
docker compose up --no-build --scale backend=3 --wait --wait-timeout 180
```

### Phát hành phiên bản mới

1. Build và push một tag mới từ Windows.
2. Lấy digest manifest của tag vừa push (`docker buildx imagetools inspect <image:git-sha>`).
3. Đặt digest mới vào `ERG_APP_IMAGE`, giữ digest đang chạy làm `ERG_ROLLBACK_IMAGE`.
4. Chạy lại:

```bash
./scripts/docker.sh prod 3
```

### Rollback

Không sửa thủ công về tag cũ. Đặt digest cũ vào `ERG_ROLLBACK_IMAGE` và chạy `prod`; health gate của lần phát hành mới sẽ tự rollback về digest đó khi cần. Named volume PostgreSQL và Redis không bị xóa khi container được recreate.

## 6. File env

| File | Dùng ở đâu? | Có commit không? |
| --- | --- | --- |
| `.env.dev.example` | Mẫu local dev: IDE + PostgreSQL/Redis/Nginx | Có |
| `.env.dev` | Giá trị local dev thực tế | Không |
| `.env.production.example` | Mẫu local native test và production | Có |
| `.env.production` | Giá trị runtime container thực tế | Không |
| `.env` ở root | Cấu hình cá nhân mà Spring Boot có thể tự đọc | Không |

Không đưa password, token, private key hoặc service-account JSON vào Git hay Docker image.

## 7. Lệnh vận hành

Windows:

```powershell
.\scripts\docker.ps1 config
.\scripts\docker.ps1 logs
.\scripts\docker.ps1 down
```

Linux:

```bash
./scripts/docker.sh config
./scripts/docker.sh logs
./scripts/docker.sh down
```

`down` không xóa named volume. Chỉ dùng `docker compose down --volumes` khi chủ động muốn xóa toàn bộ dữ liệu của đúng Compose project.

## 8. Cấu trúc file

```text
Dockerfile                         Build native image Linux amd64
.dockerignore                      Loại secret, target và file thừa khỏi build context
ops/docker/compose.yml             PostgreSQL, Redis, network và volume dùng chung
ops/docker/compose.dev.yml         Local dev: publish DB/Redis và proxy về IDE
ops/docker/compose.native.yml      Native test/production: backend image + Nginx
ops/docker/nginx/dev.conf          Proxy tới host.docker.internal:1606
ops/docker/nginx/native.conf       Cân bằng tải các container backend
ops/docker/.env.dev.example        Mẫu env local dev và Spring Boot IDE
ops/docker/.env.production.example Mẫu env container/production
scripts/docker.ps1                 Lệnh cho Windows
scripts/docker.sh                  Lệnh cho Linux
```
