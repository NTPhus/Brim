# Tự học Spring Boot trong BRIM

## Mỗi bước tạo ra một kết quả kiểm tra được

1. **Java:** viết board Connect Four và test thắng/hòa, chưa dùng Spring. Nắm class/interface/enum/record, List/Map/Set, generics, exception và Lombok.
2. **Bean và DI:** xem BoardGameModules, RoomService và constructor do `@RequiredArgsConstructor` sinh ra. Tự thêm một service, inject qua constructor và mock dependency trong unit test.
3. **REST:** lần theo RoomRestController → RoomService → RedisTemplate. Tự thêm endpoint đọc luật game; phân biệt DTO với entity persistence.
4. **WebSocket:** đặt breakpoint tại TurnBasedGameController.makeMove → TurnBasedGameService.processMove → ChessGame.makeMove. Mặc định không qua Kafka. So sánh JSON frontend với DTO backend.
5. **Module game:** triển khai TurnBasedGame, moveType, makeMove, getState và đăng ký Definition. Thêm enum/settings/router/view cho Connect Four.
6. **Multiplayer:** dùng hai browser context để có playerId khác nhau, kiểm tra sai lượt, join/rejoin, disconnect và game over. Không chỉ thử hai tab dùng chung localStorage.
7. **Kafka:** làm lần lượt bài trong KAFKA_LAB.md; tự phát một event kết thúc ván và consumer đếm số trận.
8. **Realtime:** tìm hiểu GameRuntime và GameSimulation. Tự thêm Pong đơn giản hoặc một prototype Bomberman, không tạo thread vô hạn.
9. **Tài nguyên:** đo bằng script k6, actuator, docker stats; đọc log theo roomId. Tạo bug chậm có chủ đích, đo rồi sửa để thấy tác động.

## Điểm cần hiểu trước khi tối ưu

- `ConcurrentHashMap` không bảo vệ mọi field nằm trong object được lưu bên trong.
- Snapshot phải tách khỏi trạng thái đang bị thay đổi khi serialize bất đồng bộ.
- Thread pool phải có giới hạn và hành vi khi đầy.
- Database/Redis/Kafka call có thể chậm: tránh đặt trong tick simulation.
- Càng nhiều service không tự làm hệ thống chịu tải hơn; room ownership và dữ liệu mới là vấn đề chính khi scale.

Nguồn học: [Spring REST guide](https://spring.io/guides/gs/rest-service/), [Spring WebSocket guide](https://spring.io/guides/gs/messaging-stomp-websocket/), [Kafka official docs](https://kafka.apache.org/documentation/).

Bài tốt nghiệp đầu tiên: từ thư mục trống, tự viết Connect Four, tích hợp vào BRIM, hai browser chơi đúng lượt, viết test luật và đóng gói Docker chạy được.
