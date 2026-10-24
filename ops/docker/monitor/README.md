# Hướng Dẫn Vận Hành Hệ Thống Giám Sát (Monitoring & Logging Stack)

Hệ thống giám sát được cấu hình riêng qua file `ops/docker/compose.monitor.yml`, tích hợp trực tiếp với stack chính (`compose.yml` + `compose.native.yml`).

## 1. Thành phần phần mềm (PLG Stack + Exporters)

| Thành phần | Vai trò | Mức RAM chiếm dụng | Port nội bộ / ngoài |
|---|---|---|---|
| **Prometheus v3.5** | Thu thập metric từ app và server định kỳ (Time-Series DB) | ~256MB - 512MB | `127.0.0.1:9090` |
| **Loki v3.3** | Thu thập và lưu trữ log tập trung (Log Indexer) | ~200MB - 384MB | `127.0.0.1:3100` |
| **Promtail v3.3** | Đọc log trực tiếp từ Docker containers đẩy vào Loki | ~64MB - 128MB | Nội bộ Docker |
| **Grafana v12.1** | Giao diện trực quan hóa Dashboard & xem log tập trung | ~150MB - 256MB | `:3000` |
| **Node Exporter v1.9** | Đo CPU, RAM, Disk NVMe, Network của máy tính vật lý | ~32MB | `127.0.0.1:9100` |
| **PostgreSQL Exporter** | Giám sát connection pool, active transactions, deadlocks | ~64MB | Nội bộ Docker |
| **Redis Exporters** | Giám sát bộ nhớ Redis State & Cache, eviction, hit rate | ~32MB x 2 | Nội bộ Docker |
| **Tổng cộng** | Toàn bộ monitoring stack chiếm **~1GB - 1.2GB RAM**, hoàn toàn êm ái trên cấu hình 16GB RAM. |

---

## 2. Lệnh khởi động & dừng Monitoring

Chạy kèm với stack backend chính:

```bash
# Khởi động cả stack ứng dụng + monitoring:
docker compose --project-directory ops/docker \
  --env-file ops/docker/.env.production \
  -f ops/docker/compose.yml \
  -f ops/docker/compose.native.yml \
  -f ops/docker/compose.monitor.yml \
  up -d

# Hoặc nếu chỉ muốn bật/tắt riêng monitoring:
docker compose --project-directory ops/docker \
  --env-file ops/docker/.env.production \
  -f ops/docker/compose.yml \
  -f ops/docker/compose.monitor.yml \
  up -d

# Tắt monitoring khi cần giải phóng RAM:
docker compose --project-directory ops/docker \
  -f ops/docker/compose.monitor.yml down
```

---

## 3. Truy cập Grafana

- URL: `http://<IP_MAY_SERVER>:3000`
- Tài khoản mặc định: `admin`
- Mật khẩu: Cấu hình biến `GRAFANA_PASSWORD` trong file `.env.production` (mặc định: `erg-grafana-password`).

### Dashboards được nạp sẵn tự động:
1. **ERG Server Upgrade & Operations (i5-7500 / 16GB RAM)**:
   - Đo % CPU, RAM khả dụng, % dung lượng SSD NVMe, I/O wait.
   - Đo tổng Requests/giây (RPS), phân loại theo 2xx, 4xx, 5xx.
   - Độ trễ REST p50, p95, p99.
   - Sức tải connection pool PostgreSQL (HikariCP Active vs Pending).
   - Bộ nhớ Redis State & Cache.
   - Bảng Live Stream Log từ Loki hiển thị ngay dưới dashboard.
2. **ERG Backend SLO**:
   - P95 latency theo từng endpoint route.
   - Tỷ lệ lỗi chi tiết theo nghiệp vụ GraphQL / REST.
   - Modulith Event lag time.

---

## 4. Cách xem Log bằng Loki trong Grafana

1. Vào Grafana → Menu góc trái → Chọn **Explore** (biểu tượng la bàn).
2. Ở dropdown trên cùng, chọn data source là **Loki**.
3. Các câu lệnh truy vấn mẫu:
   - Xem toàn bộ log của backend: `{container=~".*backend.*"}`
   - Chỉ lọc log lỗi (ERROR / WARN): `{container=~".*backend.*"} |= "ERROR"`
   - Tìm log theo traceId: `{container=~".*backend.*"} |= "traceId=..."`
   - Xem log Nginx: `{service="nginx"}`
   - Xem log PostgreSQL: `{service="postgres"}`

---

## 5. Dấu hiệu trong Monitoring để quyết định Nâng Cấp Server

Theo dõi bảng điều khiển trong vòng 2-4 tuần lúc cao điểm:

| Chỉ số | Vị trí theo dõi | Ngưỡng báo động cần NÂNG CẤP |
|---|---|---|
| **CPU Load** | Gauge 1: CPU Utilization | Duy trì liên tục **> 80%** trong 15-30 phút cao điểm $\rightarrow$ Cần nâng cấp CPU nhiều core hơn (i7/Xeon/Ryzen). |
| **RAM Khả dụng** | Gauge 2: Available RAM | Còn trống **< 1.5GB** $\rightarrow$ Cần nâng RAM lên 32GB hoặc 64GB ngay để tránh OOM Killer kill container. |
| **SSD NVMe Space** | Gauge 3: NVMe Free Space | Dung lượng còn **< 15%** $\rightarrow$ SSD 256GB bị chật bởi DB/Logs/Backups, cần nâng cấp SSD 512GB/1TB hoặc gắn thêm ổ HDD phụ lưu backup. |
| **Disk I/O Wait** | Gauge 4: Disk I/O Utilization | Duy trì **> 50-60%** $\rightarrow$ Nghẽn I/O đọc ghi đĩa, cần cấu hình tách ổ đĩa OS và ổ đĩa Data PostgreSQL. |
| **Hikari Pending** | Time-series 7: Hikari Queue | Có chỉ số `pending > 0` liên tục $\rightarrow$ App bị nghẽn connection vào DB, báo hiệu cần tách PostgreSQL ra một server vật lý độc lập. |
| **Request Rate & Latency**| Timeseries 5 & 6 | Khi RPS tăng cao và P95 latency nhảy vọt từ <100ms lên **>1s**, xem log slow query để quyết định scale horizontal backend. |
