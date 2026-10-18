# Lộ trình game và gameplay

Sáu game dưới đây đã có phiên bản chơi được, tích hợp lobby/phòng/WebSocket và kiểm thử backend. Các phần mở rộng trong những giai đoạn sau vẫn là backlog. Tank map/item là phần đã có. Chưa kiểm tra thủ công đầy đủ bằng hai browser.

| Thứ tự | Game | Mục tiêu học | Điều kiện hoàn thành đầu tiên |
|---|---|---|---|
| 1 | Connect Four (đã có) | Luật theo lượt, DTO, test, module registry | Đã có engine, test luật và tích hợp service; cần kiểm tra thủ công hai browser cùng phòng |
| 2 | Caro (đã có) | Bàn 15×15 freestyle, từ 5 quân là thắng, bot ba mức | Test lượt/nước sai, thắng bốn hướng, overline và bot |
| 3 | Othello (đã có) | Lật quân, alpha–beta, tự bỏ lượt | Test nước hợp lệ, lật quân, bỏ lượt, thắng/hòa |
| 4 | Battleship (đã có) | Xếp tàu thủ công/tự động, snapshot theo viewer | Test không lộ tàu qua snapshot chung, kênh địch bị chặn, giữ riêng dữ liệu cuối ván |
| 5 | Match-3 (đã có giai đoạn 1–2) | 10 màn JSON, seed, cascade, thu thập, chắn/lớp phủ | Test khởi tạo, nước sai, match giao nhau, replay cùng seed, chuyển màn và tiến độ |
| 6 | Bomberman (đã có) | Tilemap, bomb timer, nổ dây chuyền, phá tường, item | Test input, thời gian nổ, va chạm, chain reaction, thắng/hòa và lifecycle runtime |

## Match-3 lấy cảm hứng từ Candy Crush Saga

Dùng tên, hình ảnh, âm thanh và level do BRIM tự làm. Mục tiêu là thể loại đổi hai ô để ghép ít nhất ba ô giống nhau; không sao chép bộ asset hoặc thiết kế màn độc quyền.

### Giai đoạn 1: board engine Java thuần

Đã triển khai tại `game/matchthree/MatchThreeGame.java`.

- Board 8×8, sáu loại biểu tượng.
- Đổi hai ô kề ngang/dọc. Nếu không tạo match thì hoàn tác, không trừ lượt.
- Tìm match ngang/dọc; hợp nhất ô giao nhau để không tính điểm hai lần.
- Xóa match đồng thời, rơi ô theo trọng lực, sinh ô mới và tiếp tục cascade.
- Sinh board ban đầu không có match sẵn và có ít nhất một nước hợp lệ.
- RNG có seed để tái hiện lỗi; giới hạn số cascade và tính lại seed/board khi không có nước.

Test bằng các board cố định: match ở biên, hình T/L, hai match đồng thời, cascade, board không có nước và input ngoài bàn.

### Giai đoạn 2: chơi được 10 màn

Đã có 10 màn trong `src/main/resources/games/match-three-levels.json`, chọn màn đã mở, thắng/thua, chơi lại và lưu màn mở/màn chọn trong Redis theo phiên khách. Bàn đang chơi vẫn ở RAM như các game khác. Frontend dùng sự kiện swap/clear/fall/reshuffle để diễn hoạt.

Mỗi level khai báo JSON: seed, kích thước, số lượt, mục tiêu điểm/thu thập và vật cản. Bắt đầu bằng chướng ngại đơn giản, sau đó thêm ô bị khóa, lớp phủ cần phá và mục tiêu thu thập. Có màn thắng/thua, chơi lại và lưu tiến độ.

Không cần server tick 20 Hz cho puzzle một người. Frontend gửi lệnh đổi ô; server kiểm tra nước đi, trả kết quả board + chuỗi sự kiện animation. Tách thời gian animation khỏi luật. Nếu có leaderboard cạnh tranh, server phải xác minh điểm từ chuỗi lệnh/seed thay vì tin con số client gửi.

### Giai đoạn 3: special pieces và progression

**Chưa triển khai.**

Ghép 4 tạo quân xóa hàng/cột; ghép 5 tạo quân xóa theo màu; ghép T/L tạo bom vùng. Quy định thứ tự kích hoạt, combo và cascade bằng test. Bổ sung bản đồ tiến trình nhiều màn, tutorial và accessibility không chỉ phân biệt quân bằng màu.

### Giai đoạn 4: multiplayer phù hợp

**Chưa triển khai cho Match-3.** Bản hiện tại chỉ có một người trong phòng puzzle.

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
