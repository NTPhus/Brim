# Học Kafka ngay trong BRIM

## 1. Hiểu bằng luồng điểm

Khi người chơi ăn thức ăn/hạ xe, server tổng hợp thay đổi điểm khoảng mỗi giây:

```text
Game controller → GameEventProducer → game.leaderboard
                → GameEventConsumer → LeaderboardService → Redis → UI
```

- **Producer:** ghi event vào topic.
- **Topic:** nhóm sự kiện cùng mục đích.
- **Partition:** một chuỗi log có thứ tự. Thứ tự chỉ có trong từng partition.
- **Key:** hiện leaderboard dùng playerId, nước cờ dùng roomId. Với số partition cố định, cùng key được phân phối vào cùng partition.
- **Consumer group:** các consumer cùng group chia nhau partition. Group khác nhau có thể đọc cùng dữ liệu độc lập.
- **Offset:** vị trí đã đọc trong từng partition; không phải ID nghiệp vụ toàn cục.
- **Lag:** số bản ghi consumer chưa theo kịp, khác với ping của người chơi.

Nguồn chính thức: [Kafka introduction](https://kafka.apache.org/documentation/), [delivery semantics](https://kafka.apache.org/32/design/design/).

## 2. Quan sát topic

Khởi động hạ tầng dev với `docker compose up -d`, backend `./run-backend.sh`, frontend `cd frontend && npm run dev`. Nếu trước đó chạy Compose cũ, recreate Kafka/Kafka UI để áp dụng INTERNAL/EXTERNAL listener mới. Chơi một ván để producer phát điểm.

Các lệnh dưới dùng Compose **dev** và listener nội bộ. Với stack deploy, thay `docker compose` bằng `docker compose -f docker-compose.deploy.yml`.

```bash
docker compose exec kafka kafka-topics --bootstrap-server kafka:29092 --list
docker compose exec kafka kafka-topics --bootstrap-server kafka:29092 --describe --topic game.leaderboard
docker compose exec kafka kafka-console-consumer --bootstrap-server kafka:29092 --topic game.leaderboard --from-beginning --property print.key=true --property print.partition=true --property print.offset=true
```

Tìm `eventId`, `playerId`, `points`, `occurredAt` trong payload. Đọc cùng topic với một console consumer mới không làm consumer của app mất event: chúng là các consumer/group riêng.

```bash
docker compose exec kafka kafka-consumer-groups --bootstrap-server kafka:29092 --describe --group game-platform-leaderboard
```

Quan sát CURRENT-OFFSET, LOG-END-OFFSET và LAG. Khi app đang chạy bình thường, lag nên được tiêu thụ dần về gần 0.

## 3. Vì sao cần eventId?

Producer đã bật idempotence và `acks=all`. Consumer dùng record ack và tắt auto commit. Tuy vậy, nếu xử lý Redis xong mà process dừng trước khi offset được commit, event có thể được đọc lại.

`GameScoreEvent` tạo UUID một lần khi event được tạo. `LeaderboardService` dùng Lua trong Redis để kiểm tra eventId và cộng điểm trong cùng một thao tác nguyên tử. Bản ghi cũ chưa có eventId được consumer gán ID từ topic/partition/offset. Dữ liệu dedup và điểm có TTL 3 ngày, phù hợp bảng xếp hạng theo ngày; event quá cũ không được tính lại. Ngày tính điểm lấy từ occurredAt theo Asia/Ho_Chi_Minh, không lấy ngày replay.

Idempotence producer không đồng nghĩa thao tác Redis tự exactly-once. Redis script cũng không phải distributed transaction với Kafka. Ngoài ra, nếu process chết **trước khi phát event**, điểm chưa gửi vẫn có thể mất; cần transactional outbox/persistent event journal khi muốn bảo đảm bền vững hơn.

Bài thử: gửi cùng một JSON event hai lần (cùng eventId), rồi đọc bảng điểm; điểm chỉ tăng một lần. Đổi eventId thì được xem là sự kiện khác.

## 4. Tự gửi event

Dùng roomId tùy ý, eventId cố định để thử dedup. `occurredAt` và timestamp phải là epoch milliseconds của ngày hiện tại; không copy timestamp đã hết hạn. Lấy giá trị bằng `Date.now()` trong console browser.

```bash
docker compose exec kafka kafka-console-producer --bootstrap-server kafka:29092 --topic game.leaderboard --property parse.key=true --property key.separator='|'
```

Nhập một dòng, thay hai giá trị `EPOCH_MS` bằng số thực tế:

```text
lab-player|{"type":"SCORE_UPDATE","roomId":"lab","playerId":"lab-player","payload":{"eventId":"lab-event-001","playerId":"lab-player","username":"Kafka Student","gameType":"TANK","points":100,"occurredAt":EPOCH_MS},"timestamp":EPOCH_MS}
```

Gửi lại đúng dòng này, kiểm tra `/api/leaderboard?gameType=TANK`. Sau đó đổi eventId thành `lab-event-002` để tăng thêm 100. Thực hành trên môi trường riêng, tránh làm thay đổi bảng điểm thật.

## 5. Retry và dead-letter topic

`KafkaConfig.kafkaErrorHandler` retry hai lần, cách nhau 1 giây. Sau đó chuyển event lỗi sang `<topic>.DLT`, cùng partition. Các DLT cần có số partition tương ứng; config đã khai báo 3 partition cho các topic đang consume.

```bash
docker compose exec kafka kafka-console-consumer --bootstrap-server kafka:29092 --topic game.leaderboard.DLT --from-beginning
```

Thử payload có `points: "khong-phai-so"` để gây lỗi mapping sau deserialize. Dữ liệu không hợp lệ không nên được retry vô hạn và khóa các bản ghi phía sau. Lỗi không retryable có thể được đưa thẳng sang DLT, tùy loại exception.

DLT chưa tự được phát lại vào topic gốc. Đọc lỗi, sửa nguyên nhân/payload rồi replay có kiểm soát, giữ nguyên eventId nếu vẫn là cùng sự kiện. DLT chỉ có replicas=1 trong demo nên không có HA.

## 6. Thử group và partition

Mở hai console consumer cùng `--group lab-shared`: mỗi partition chỉ được giao cho một consumer của group tại một thời điểm. Thêm consumer thứ tư vào topic 3 partition thì có consumer không được giao partition.

Đổi sang `--group lab-independent`: group này có thể tự đọc lại cùng sự kiện. Đừng reset offset group của app trên dữ liệu thật để thử nghiệm.

## 7. So sánh đường đi nước cờ

Mặc định nước cờ đi thẳng WebSocket → TurnBasedGameService để giảm một chặng. Để học luồng Kafka cũ:

```bash
GAME_KAFKA_ROUTE_MOVES=true ./run-backend.sh
```

Theo dõi `game.move` và đặt breakpoint ở `GameEventConsumer.handleGameMove`. Chỉ dùng một backend vì game state vẫn ở RAM cục bộ. Kafka không thay thế room ownership hay phục hồi ván cờ.

Bài nâng cao: thêm event `MatchFinished` cho analytics; consumer thống kê riêng; đo consumer lag; thử broker ngừng hoạt động; thiết kế outbox và retry sau lỗi. Không đưa mọi frame của Tank/Snake vào Kafka.
