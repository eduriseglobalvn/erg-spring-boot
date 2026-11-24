# Docker: local dev và production

Lệnh chạy tại thư mục gốc của repo. Production chỉ có MỘT backend (`erg-backend-1`) chạy image JVM (`--target jvm-runtime` của `Dockerfile`).

## Các file

| File | Vai trò |
| --- | --- |
| `compose.yml` | PostgreSQL, hai Redis (state, cache), Nginx cơ sở |
| `compose.native.yml` | Override production: service `backend` chạy image JVM (tên file là tên cũ), Nginx, volume ảnh chấm công |
| `compose.monitor.yml` | Prometheus, Grafana, Loki, Alertmanager, node-exporter |
| `compose.dev.yml` | Chỉ cho local dev: mở port PostgreSQL/Redis và đưa Nginx tới Spring Boot trong IDE |
| `compose.benchmark.yml` | Môi trường đo tải cô lập (`load/`) |
| `.env.production.example` | Mẫu env production; bản thật `.env.production` chỉ nằm trên server |
| `.env.dev.example` | Mẫu env local dev |

## Local dev

Docker chỉ chạy hạ tầng; Spring Boot chạy trong IDE ở port `1606`.

```powershell
Copy-Item ops/docker/.env.dev.example ops/docker/.env.dev   # lần đầu, không ghi đè file có sẵn
docker compose --project-directory ops/docker --env-file ops/docker/.env.dev `
  -f ops/docker/compose.yml -f ops/docker/compose.dev.yml up -d
```

Đặt các biến trong `ops/docker/.env.dev` vào Run Configuration của IDE (hoặc `.env` ở root, dự án tự đọc file này), rồi chạy `ErgSpringApplication`.

| Dịch vụ | Địa chỉ |
| --- | --- |
| API qua Nginx | `http://localhost:8080` (`/ready` để kiểm tra) |
| Spring Boot trong IDE | `http://localhost:1606` |
| PostgreSQL | `localhost:5432`, database/user `erg` |
| Redis state / cache | `localhost:6379` / `localhost:6380` |

## Production

Merge vào `main` -> workflow `erg-spring-ci` xanh -> `.github/workflows/deploy.yml` gửi source của commit qua SSH (Cloudflare tunnel) tới `ops/docker/ci-entry.sh`, và script này chạy `ops/docker/deploy.sh <sha>` trên server:

1. giải nén source, build image JVM `erg-spring:<sha>-jvm` NGAY TRÊN SERVER;
2. sao lưu PostgreSQL (`pg_dump`);
3. chạy thử migration và khởi động trên bản restore cô lập (worker tắt);
4. chuyển backend và Nginx sang image mới, chờ healthy; nếu lỗi thì tự quay lại image trước (migration chỉ đi tới, không rollback).

Deploy chạy ngay sau khi CI xanh, giờ nào cũng được (lúc đổi container backend ngắt khoảng một phút); lịch 21:35 mỗi ngày bù commit bị bỏ qua vì đang có deploy khác. Chạy tay `workflow_dispatch` với một sha phải là commit của `main` đã có CI xanh.

Trước khi chạm production, `deploy.sh` dừng nếu `POSTGRES_PASSWORD`, `POSTGRES_RUNTIME_PASSWORD`, `REDIS_STATE_PASSWORD`, `REDIS_CACHE_PASSWORD`, `GRAFANA_PASSWORD` hoặc `ERG_BOOTSTRAP_ADMIN_PASSWORD` trong `.env.production` trống hoặc còn giá trị mẫu cũ (`bash scripts/deploy-secret-check.test.sh` kiểm phần này). Tạo mỗi giá trị bằng `openssl rand -hex 32`. Xoay mật khẩu: `docs/runbooks/credential-rotation-p0-2.md`.

Xem trạng thái trên server:

```bash
docker compose --env-file ops/docker/.env.production \
  -f ops/docker/compose.yml -f ops/docker/compose.native.yml -f ops/docker/compose.monitor.yml ps
```

Quay lại bản trước: `ERG_ROLLBACK_IMAGE` trong `.env.production` giữ image đang chạy trước lần deploy gần nhất (bot Telegram cũng có lệnh `/rollback`).
