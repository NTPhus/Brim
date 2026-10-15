# Deploy BRIM

## Chạy cùng phòng trên nhiều máy

Một người host backend/frontend. Những người khác mở **cùng địa chỉ host**, không chạy mỗi người một backend riêng. Trong LAN, dùng `http://IP_CUA_HOST:8088`, tạo phòng và chọn phòng đó từ sảnh trên các máy còn lại. Rắn/Tank cho phép tham gia trận đang chạy nếu còn chỗ; cờ yêu cầu đủ ghế trước khi bắt đầu.

Trên Internet, trỏ domain về VPS và đặt HTTPS reverse proxy phía trước frontend. Browser dùng đường dẫn tương đối `/api`, vì vậy REST và WebSocket luôn đi tới cùng host. Frontend có fallback tạo UUID bằng `getRandomValues()` để chạy được trên HTTP LAN; môi trường Internet vẫn nên dùng HTTPS. Không dùng `localhost` làm địa chỉ chia sẻ cho máy khác.

## Build và chạy Docker

Yêu cầu Docker Engine + Compose. Tại root repo:

```bash
cp .env.example .env
docker compose -f docker-compose.deploy.yml build
docker compose -f docker-compose.deploy.yml up -d
docker compose -f docker-compose.deploy.yml logs -f backend
```

Mở `http://localhost:8088`. Chỉ frontend xuất cổng ra host. Nginx phục vụ SPA và proxy `/api` cùng WebSocket tới backend. Backend chạy Java 17 bằng user không có quyền root; heap được giới hạn tương đối theo RAM container. Redis/Kafka giữ dữ liệu bằng volume. Trạng thái trận trong RAM backend **không** được khôi phục khi restart.

Frontend Dockerfile được build với context root, không phải thư mục frontend:

```bash
docker build -t brim-backend .
docker build -t brim-frontend -f frontend/Dockerfile .
```

`docker-compose.yml` phục vụ phát triển local, khác với `docker-compose.deploy.yml`. Kafka trong bộ deploy chỉ quảng bá địa chỉ nội bộ `kafka:29092`, tránh lỗi container cố kết nối `localhost:9092`. Compose dev có INTERNAL/EXTERNAL listeners để cả app host và Kafka UI kết nối được.

Để thử cục bộ mà không mở ra LAN:

```bash
FRONTEND_PORT=127.0.0.1:8088 docker compose -f docker-compose.deploy.yml up -d
```

Dừng mà giữ dữ liệu:

```bash
docker compose -f docker-compose.deploy.yml down
```

Không thêm `-v` nếu muốn giữ bảng điểm và Kafka log. Nếu deploy container mới, các trận đang chơi kết thúc vì chưa có snapshot recovery.

## Cấu hình tài nguyên

| Biến | Mặc định | Ý nghĩa |
|---|---:|---|
| FRONTEND_PORT | 8088 | Cổng public của Nginx |
| GAME_RUNTIME_WORKERS | 2 | Số worker tick dùng chung |
| GAME_RUNTIME_MAX_ROOMS | 64 | Giới hạn trận realtime hoạt động |
| GAME_BOT_WORKERS | 1 | Số phép tìm nước cờ chạy đồng thời |
| GAME_ROOM_MAX_ROOMS | 128 | Tổng số room; có thể truyền trực tiếp qua environment backend |

Giới hạn này là cơ chế tránh tài nguyên tăng vô hạn, **không phải cam kết 64 trận luôn mượt**. Đo bằng k6 trước khi tăng. Ngân sách RAM Compose gồm backend 1 GiB, Redis 384 MiB, Kafka 1 GiB, ZooKeeper 512 MiB và Nginx 128 MiB; host cần thêm RAM cho OS và Docker. Kafka stack tương đối nặng cho VPS nhỏ.

## Giới hạn triển khai hiện tại

- Chạy **một backend**: WebSocket simple broker và trận đấu đều nằm cục bộ. Không tăng `--scale backend` rồi kỳ vọng room tự đồng bộ.
- Chưa có đăng nhập và xác thực chủ sở hữu playerId. Đây là bản demo/học tập; môi trường public cần authentication và kiểm tra quyền theo session trước khi mở cho người không tin cậy.
- Kafka một broker, replicas=1 chưa có HA. Redis là standalone. H2 mặc định vẫn là DB thử nghiệm; hiện room và leaderboard dùng Redis.
- TLS/domain đặt ở reverse proxy ngoài cùng; cấu hình HTTPS theo hạ tầng host của bạn.
- Endpoint `/api/actuator/health`, `/api/actuator/metrics` chỉ nên truy cập từ mạng nội bộ. Nginx đã chặn đường public này.

Tham khảo [kiến trúc](ARCHITECTURE.md), [mạng và tải](NETWORKING_AND_PERFORMANCE.md).
