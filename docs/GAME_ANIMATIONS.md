# Animation trong game

- Caro: đặt dấu và làm nổi hàng thắng; Othello: lật quân; Connect Four: thả quân theo độ sâu của ô.
- Cờ vua/cờ tướng: trượt quân và làm mờ quân bị bắt. Cờ vua xử lý cả hai quân khi nhập thành, phong cấp và bắt tốt qua đường theo snapshot máy chủ.
- Battleship: hiệu ứng trúng đạn, gợn nước khi bắn trượt và xuất hiện tàu khi xếp đội hình.
- Bomberman: nhân vật di chuyển trong lớp riêng, bom nhấp nhô trong thời gian chờ nổ và lửa bung ngắn khi xuất hiện.
- Match-3 và Sơn Hà: đổi chỗ, thu nhỏ ô được ghép, trượt theo trọng lực và đổi bàn khi hết nước. Match-3 giữ trọng lực riêng cho từng đoạn cột bị chia bởi vật cản.
- Snake: nội suy thân/camera theo thời gian khung hình; hiệu ứng ăn mồi và bị loại tự hết sau 420 ms.
- Tank: nội suy xe/camera theo thời gian khung hình, nội suy đạn giữa hai snapshot và hiệu ứng nhận sát thương/hồi máu. Đạn được ghép giữa hai snapshot theo chủ sở hữu, hướng và tuổi đạn vì giao thức hiện chưa có ID đạn.

`useGameMotion` quản lý Web Animations, hủy khi component rời trang hoặc người chơi bật `prefers-reduced-motion`. `useBoardMotion` so sánh snapshot trước khi Vue cập nhật DOM, sau đó dùng vị trí thực tế để trượt quân. `useChessMotion` bổ sung vị trí xuất phát và nhập thành. Các snapshot trùng hoặc lịch sử bị reset không phát lại nước đi. `puzzleMotion` dùng chung thứ tự SWAP/CLEAR/FALL cho hai game ghép ô.

Animation không thay đổi luật chơi, HP, điểm hay tọa độ trên máy chủ. Các game thời gian thực vẫn nhận thao tác khi animation đang chạy. Hai game ghép ô chờ chuỗi hiệu ứng hoàn tất trước khi nhận nước đổi tiếp theo.

`WorldEffects` giới hạn tối đa 48 hiệu ứng ngắn cho mỗi scene. Cả hai scene canvas bỏ hiệu ứng trang trí khi bật giảm chuyển động; Tank cũng tắt rung camera. Hiệu ứng DOM, đối tượng tạm và bộ lắng nghe được dọn khi rời game.

Kiểm tra build: `cd frontend` rồi `npm run build`. Đã kiểm tra trên Chromium với snapshot STOMP mô phỏng cho từng game, gồm nhận trạng thái trùng, nhập thành/bắt quân, trọng lực có vật cản, hết thời gian hiệu ứng và bật giảm chuyển động. Các kiểm tra này xác nhận phần hiển thị; không thay thế kiểm thử luật chơi backend. Sơn Hà được kiểm tra thêm ở 1365×768, 390×844, 360×640 và 844×390.
