# Lộ trình game và gameplay

Các game dưới đây là backlog học tập, **chưa được triển khai**. Tank map/item là phần đã có trong bản nâng cấp này.

| Thứ tự | Game | Mục tiêu học | Điều kiện hoàn thành đầu tiên |
|---|---|---|---|
| 1 | Connect Four | Luật theo lượt, DTO, test, module registry | Hai browser cùng phòng, thắng/hòa đúng |
| 2 | Caro | Bàn cờ lớn hơn, luật thắng, bot | Chọn biến thể luật rõ ràng, chặn nước sai |
| 3 | Othello | Lật quân, đánh giá thế cờ, alpha–beta | Sinh đủ nước hợp lệ, xử lý bỏ lượt |
| 4 | Battleship | Thông tin bí mật và snapshot theo viewer | Đối thủ không nhận vị trí tàu qua API/WebSocket |
| 5 | Match-3 | Puzzle theo màn, seed, cascade, replay | Hoàn thành 10 màn với mục tiêu/số lượt |
| 6 | Bomberman | Tilemap, bomb timer, phá tường, item | Hai người cùng map, bomb/va chạm do server xử lý |

## Match-3 lấy cảm hứng từ Candy Crush Saga

Dùng tên, hình ảnh, âm thanh và level do BRIM tự làm. Mục tiêu là thể loại đổi hai ô để ghép ít nhất ba ô giống nhau; không sao chép bộ asset hoặc thiết kế màn độc quyền.

### Giai đoạn 1: board engine Java thuần

- Board 8×8, sáu loại biểu tượng.
- Đổi hai ô kề ngang/dọc. Nếu không tạo match thì hoàn tác, không trừ lượt.
- Tìm match ngang/dọc; hợp nhất ô giao nhau để không tính điểm hai lần.
- Xóa match đồng thời, rơi ô theo trọng lực, sinh ô mới và tiếp tục cascade.
- Sinh board ban đầu không có match sẵn và có ít nhất một nước hợp lệ.
- RNG có seed để tái hiện lỗi; giới hạn số cascade và tính lại seed/board khi không có nước.

Test bằng các board cố định: match ở biên, hình T/L, hai match đồng thời, cascade, board không có nước và input ngoài bàn.

### Giai đoạn 2: chơi được 10 màn

Mỗi level khai báo JSON: seed, kích thước, số lượt, mục tiêu điểm/thu thập và vật cản. Bắt đầu bằng chướng ngại đơn giản, sau đó thêm ô bị khóa, lớp phủ cần phá và mục tiêu thu thập. Có màn thắng/thua, chơi lại và lưu tiến độ.

Không cần server tick 20 Hz cho puzzle một người. Frontend gửi lệnh đổi ô; server kiểm tra nước đi, trả kết quả board + chuỗi sự kiện animation. Tách thời gian animation khỏi luật. Nếu có leaderboard cạnh tranh, server phải xác minh điểm từ chuỗi lệnh/seed thay vì tin con số client gửi.

### Giai đoạn 3: special pieces và progression

Ghép 4 tạo quân xóa hàng/cột; ghép 5 tạo quân xóa theo màu; ghép T/L tạo bom vùng. Quy định thứ tự kích hoạt, combo và cascade bằng test. Bổ sung bản đồ tiến trình nhiều màn, tutorial và accessibility không chỉ phân biệt quân bằng màu.

### Giai đoạn 4: multiplayer phù hợp

Đấu điểm trong cùng thời gian, hai người cùng seed; hoặc room hợp tác theo lượt với mục tiêu chung. Không cần ép game puzzle thành game điều khiển realtime. Kafka nhận `LevelCompleted`/`MatchFinished` cho thống kê, không xử lý từng frame rơi kẹo.

## Tank đang có và bước tiếp theo

Đã có Arena, Warehouse, Crossroads; sprite Kenney CC0; hồi máu, khiên chặn một phát trong tối đa 10s, tăng tốc 6s, bắn nhanh 6s. Nhặt lại cùng loại đặt lại thời hạn, không cộng dồn sức mạnh. Chết/hồi sinh xóa hiệu ứng.

Tiếp theo: minimap có đánh dấu vùng nhìn; bot tìm đường A* qua ô; tường phá được; map editor JSON; lựa chọn tài nguyên khiến người chơi tranh chấp trung tâm. Cần kiểm tra đường đi liên thông và spawn an toàn trước khi cho tạo map ngẫu nhiên phức tạp.

## UI cho toàn bộ game

1. Chuẩn hóa button/panel/font/spacing và trạng thái kết nối.
2. Chuyển động và phản hồi rõ: di chuyển quân, nhận sát thương, ăn item, hồi sinh.
3. Bộ asset có phong cách thống nhất; lưu nguồn và license trong repo.
4. Điều khiển mobile, focus bàn phím, tương phản và chế độ giảm chuyển động.
5. Đo FPS và payload trước khi thêm particle/shadow dày đặc.
