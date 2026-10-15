# Multiplayer, mạng kém và tài nguyên

## Một thế giới, nhiều màn hình

Server giữ thế giới đầy đủ để kiểm tra luật. Client chỉ nhận snapshot vùng quanh avatar:

- Tank: nửa chiều rộng 1.300 px, nửa chiều cao 900 px, lọc xe/đạn/item/tường giao vùng.
- Snake: 1.800 × 1.200 px mỗi phía; lọc thức ăn và rắn có thân đi qua vùng. Rắn giao vùng vẫn gửi cả thân, nhưng chiều dài bị giới hạn 512 điểm.
- Snapshot gửi vào `/topic/game/{roomId}/state/{playerId}`, khoảng 10 Hz; simulation khoảng 20 Hz.
- Camera giới hạn zoom theo kích thước viewport để không nhìn quá vùng server đã gửi.
- Minimap và danh sách avatar hiện phản ánh dữ liệu **gần người chơi**, không còn toàn bản đồ.

Frontend trước đây đã bỏ qua một số vật ngoài camera khi vẽ, nhưng server vẫn gửi toàn state. Lọc render không tiết kiệm bandwidth. Thay đổi hiện tại lọc ở server và dọn cache render của avatar đã ra khỏi vùng.

AOI vẫn quét danh sách entity khi tạo snapshot; chưa có spatial subscription theo chunk hoặc delta protocol. Với số player nhỏ trong mỗi room, cách này đơn giản và có thể kiểm thử. SpatialIndex hiện giảm quét va chạm thân rắn; chưa phải broad phase cho mọi loại entity.

## Khi mạng không ổn định

- STOMP có heartbeat hai chiều và tự kết nối lại.
- Subscription được giữ lại và đăng ký lại khi reconnect; lệnh join được gửi lại cho màn hình đang mở.
- Không giữ rồi phát lại các input điều khiển cũ sau outage.
- Tank gửi input heartbeat 100 ms. Server dừng input người thật nếu 750 ms không nhận cập nhật, tránh xe chạy/bắn mãi khi rớt mạng.
- Khi mất focus, client nhả các phím Tank.
- Session có 30 giây grace trước khi xóa player khỏi room. Đây là giữ ghế/avatar, **không phải bất tử**: simulation vẫn tiếp tục, rắn vẫn di chuyển và có thể chết.
- UI báo mất kết nối hoặc hơn 1 giây chưa nhận snapshot. Timestamp server giúp bỏ snapshot cũ đến muộn.
- Nội suy Tank có hệ số theo frame delta để cảm giác không lệ thuộc FPS; chưa có prediction + reconciliation, chưa có rollback hoặc lag compensation.

WebSocket chạy trên TCP: mất gói có thể gây chờ các gói trước. Không thể cam kết hết lag trên mạng yếu bằng một thay đổi code. Chọn server gần người chơi, giảm payload, giới hạn buffer và đo p95 là những bước thực tế. Với game bắn súng cạnh tranh hơn, cần đánh giá giao thức/tickrate và netcode chuyên biệt.

## Các giới hạn đang có

| Tài nguyên | Giới hạn mặc định |
|---|---:|
| Room tổng | 128 |
| Trận realtime đang tick | 64 |
| Người thật/room realtime | tối đa 16 |
| Worker tick | 2 |
| Worker output / queue | 2 / 128 |
| Worker sự kiện nền / queue | 1 / 64 |
| Snapshot output đang chờ mỗi room | 1 |
| Worker bot / queue | 1 / 32 |
| Tìm nước cờ HARD | tối đa khoảng 1,5 giây/nước |
| Thân rắn | 512 điểm |
| Thức ăn trong một map | tối đa khoảng 1.200, nền 320 |
| Đạn Tank đang tồn tại | 512 |
| Thử spawn item mỗi tick | tối đa 100 lần |
| Queue inbound/outbound STOMP | 256 mỗi executor |
| Buffer gửi mỗi WebSocket session | 256 KiB |

Các slot là giới hạn tài nguyên, không phải số user đã được benchmark. 64 trận đều đánh bot HARD sẽ chờ worker, thay vì tạo 64 phép tìm kiếm CPU đồng thời. Chấp nhận chờ hoặc từ chối room mới tốt hơn để cả server mất phản hồi.

## Đo tải

Có script `scripts/load/realtime.js`. Chạy k6 trên máy khác nếu muốn đo server thật, đồng bộ giờ máy đo và host để số liệu tuổi snapshot có ý nghĩa:

```bash
k6 run -e BASE_URL=http://localhost:8088 -e GAME=TANK -e USERS=12 -e ROOMS=3 scripts/load/realtime.js
k6 run -e BASE_URL=http://localhost:8088 -e GAME=SNAKE -e USERS=24 -e ROOMS=6 scripts/load/realtime.js
```

Mỗi VU tham gia một phòng, kết nối STOMP, gửi input và đo số byte/tuổi snapshot. Script tự tạo room có tiền tố Load và xóa các room đó sau khi hoàn tất. Chỉ chạy trên môi trường test. Mỗi room tối đa 16 người; tăng ROOMS khi tăng USERS.

Bắt đầu 4 → 12 → 24 → 48 user. Ghi lại throughput, p95 snapshot age, CPU, heap sau GC, số thread và bandwidth. Ngưỡng p95 500 ms trong script là mục tiêu thử ban đầu, không phải SLA đã đo.

```bash
docker stats
# Từ host/container có quyền truy cập backend nội bộ:
curl http://localhost:8080/api/actuator/metrics/jvm.memory.used
curl http://localhost:8080/api/actuator/metrics/process.cpu.usage
```

Thử Chrome DevTools Network: Slow 3G/custom latency, Offline 5–10 giây, reconnect; kiểm tra không nhân đôi subscription, không tiếp tục bắn khi mất input và không reset người chơi trước grace. Test đúng 2 browser context độc lập, vì localStorage trong cùng browser dùng chung playerId.

Khi CPU cao: profile trước khi tăng worker. Khi heap cao: xem số entity/room, lượng snapshot và GC. Không đưa mọi object vào Redis mỗi tick; Redis phù hợp room metadata, bảng điểm và checkpoint thưa.

## Kết quả kiểm tra ban đầu (15/10/2026)

- 42 backend tests thành công, gồm hai integration test sử dụng Redis thật cho dedup/TTL/ngày replay.
- `npm run build` thành công; Phaser vẫn có bundle lớn, cần lưu ý thời gian tải lần đầu trên mạng yếu.
- Hai Docker image build thành công; cả cụm Nginx/backend/Redis/Kafka/ZooKeeper khởi động được trên mạng Docker riêng.
- Tank và Snake: hai client STOMP nhận state riêng, người thứ hai vào khi trận đang chạy, reconnect trong grace vẫn giữ player.
- Kafka thật: phát hai event cùng ID, consumer cập nhật Redis và REST leaderboard chỉ ghi 100 điểm một lần.
- Chrome headless: Tank có canvas, sprite và trạng thái kết nối; ảnh minh họa ở `docs/images/tank-preview.png`.
- k6 Tank: 12 VU / 3 room, cấu hình duration 22s, tổng khoảng 42s do hoàn thành các phiên 20s đang chạy. 24 phiên WebSocket nhận snapshot, không có HTTP lỗi; p95 tuổi snapshot 4ms, tổng snapshot khoảng 11,7MB.

Bài k6 chạy trên **cùng máy qua mạng Docker**, không có latency/loss giả lập; tuổi snapshot đo từ lúc tạo snapshot tới client, không gồm thời gian input → simulation → render. Đây là smoke/load test nhỏ, không phải kết luận sức chứa production hay trải nghiệm trên Internet. Chưa đo CPU/heap dài hạn, soak test hoặc tải bot cờ đồng thời ở quy mô lớn.
