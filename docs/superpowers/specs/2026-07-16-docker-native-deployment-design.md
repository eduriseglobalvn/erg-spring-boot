# Thiết kế triển khai Docker Native Image

## Mục tiêu

Cung cấp một quy trình dựa trên Docker, hỗ trợ:

- Hạ tầng local để chạy mã nguồn Spring Boot từ IDE trên Windows.
- Môi trường local gần giống production, sử dụng đúng native image Linux amd64 sẽ triển khai lên server.
- Nhiều backend stateless chạy sau Nginx để cân bằng tải.
- Chỉ biên dịch native image trên máy phát triển. Server production chỉ kéo và chạy image đã được build sẵn.

## Các môi trường

### Phát triển local bằng IDE

Docker Compose khởi động PostgreSQL, Redis và Nginx. Ứng dụng Spring Boot được chạy từ IDE trên Windows qua cổng 1606. Nginx kết nối đến ứng dụng qua `host.docker.internal:1606` và cung cấp cùng một đầu vào HTTP như stack chạy hoàn toàn bằng container.

PostgreSQL và Redis sử dụng named volume để dữ liệu local không bị mất khi khởi động lại container. Cổng của chúng chỉ được publish trên loopback để IDE có thể truy cập. Health check kiểm soát thứ tự khởi động của các dịch vụ phụ thuộc.

### Kiểm thử native image tại local

Docker Desktop build native image Linux amd64 từ mã nguồn hiện tại bằng Dockerfile nhiều stage. Docker Compose chạy image này thành nhiều container backend và Nginx, cùng với PostgreSQL và Redis.

Mỗi backend có thể chạy tác vụ nền vì các worker outbox, báo cáo và thông báo hiện tại claim công việc theo transaction, lease và `FOR UPDATE SKIP LOCKED`. Thiết kế này phù hợp với giới hạn của Spring AOT đối với bean được bật/tắt bằng `@ConditionalOnProperty`, đồng thời tránh phải native-compile hai binary khác nhau.

### Production

Máy phát triển hoặc CI build và push image Linux amd64 với tag bất biến lên Docker Registry. Server sử dụng file biến môi trường production để chọn chính xác tag đó, chạy `docker compose pull` và tạo lại các dịch vụ. Server không chạy compiler, không tải dependency Maven, không build GraalVM và không chạy Docker build.

Để rollback, chọn lại tag image bất biến của phiên bản trước rồi tạo lại stack.

## File và câu lệnh

- `Dockerfile` nhiều stage build file thực thi native rồi sao chép nó vào runtime image tối giản, chạy bằng user không phải root.
- Compose file nền định nghĩa PostgreSQL, Redis, private network, volume, backend và các health check dùng chung.
- Các Compose overlay cho development và môi trường gần production lựa chọn cấu hình Nginx cùng các cổng cần publish.
- Cấu hình Nginx development proxy đến `host.docker.internal:1606`.
- Cấu hình Nginx container tìm tất cả backend replica khỏe mạnh qua Docker DNS và cân bằng request giữa chúng.
- Script PowerShell hỗ trợ phát triển trên Windows; shell script hỗ trợ vận hành server Linux.

Các thao tác được hỗ trợ:

- `dev`: khởi động PostgreSQL, Redis và Nginx development để backend chạy từ IDE sử dụng.
- `build`: build native image Linux amd64 tại local.
- `push`: push native image có version lên Registry đã cấu hình.
- `up`: khởi động stack gần production với số lượng backend replica có thể tùy chỉnh.
- `deploy`: kéo tag đã build sẵn và tạo lại các dịch vụ trên server mà không build.
- `down`, `logs` và `config`: vận hành và kiểm tra stack.

## Mạng và cân bằng tải

Trong production, chỉ Nginx mở cổng HTTP của ứng dụng ra bên ngoài. PostgreSQL, Redis và các backend replica giao tiếp qua private network của Compose. Ở môi trường phát triển local, PostgreSQL và Redis có thể publish cổng trên loopback để IDE kết nối.

Nginx chuyển tiếp các proxy header tiêu chuẩn và sử dụng DNS resolver tích hợp của Docker. Nhờ đó, Nginx có thể phát hiện các backend container được scale mà không cần tạo danh sách upstream tĩnh. Cấu hình bao gồm header nâng cấp WebSocket và timeout phù hợp cho GraphQL cũng như các endpoint streaming trong tương lai.

Health check của container sử dụng Actuator readiness. Nginx cung cấp một health endpoint đơn giản để kiểm tra từ host.

## Cấu hình và secret

Các file biến môi trường mẫu chỉ chứa giá trị placeholder an toàn. Mật khẩu cơ sở dữ liệu, JWT secret, thông tin đăng nhập Registry và khóa dịch vụ bên ngoài không được lưu trong Git. Các biến môi trường runtime cấu hình URL datasource và Redis bằng service name của Compose.

Image được gắn tag theo định danh bản phát hành, chẳng hạn Git commit SHA. Quy trình deploy không chỉ dựa vào tag `latest`. Compose production nhận đầy đủ địa chỉ Registry và tag image qua biến `ERG_APP_IMAGE`.

## Xử lý lỗi

- PostgreSQL và Redis phải vượt qua health check trước khi backend khởi động.
- Container backend tự khởi động lại trừ khi bị dừng chủ động và báo trạng thái sẵn sàng qua Actuator.
- Nginx thử một upstream khác khi gặp lỗi kết nối, timeout và một số lỗi gateway phù hợp.
- Native build hoặc image push thất bại không ảnh hưởng đến image đang chạy trên server.
- Có thể rollback deployment thất bại bằng cách khôi phục tag image trước đó.

## Kiểm chứng

Kiểm tra cấu hình bao gồm render Docker Compose cho cả chế độ IDE local lẫn native/production, kiểm tra cú pháp Nginx và kiểm tra các stage của Dockerfile.

Kiểm tra runtime bao gồm khởi động dependency local; xác nhận PostgreSQL và Redis khỏe mạnh; xác nhận Nginx proxy được đến backend chạy từ IDE; build image Linux amd64; scale ít nhất hai backend container; và quan sát request đi qua Nginx. Quá trình biên dịch native có thể phát hiện lỗi AOT trong ứng dụng, vì vậy kết quả này được báo cáo riêng với kết quả kiểm tra tĩnh của Compose.

## Phạm vi

Thiết kế này sử dụng Docker Compose thuần và Nginx. Thiết kế giả định đã có tài khoản Docker Hub, GHCR hoặc Registry riêng và không tự dựng dịch vụ Registry. Phạm vi không bao gồm Docker Swarm, Kubernetes, tự động cấp chứng chỉ TLS hoặc cơ sở dữ liệu high availability. Các phần này có thể được bổ sung độc lập khi nhu cầu vận hành yêu cầu.
