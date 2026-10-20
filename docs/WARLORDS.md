# Sơn Hà — Hợp cờ Hoa Lư

Game match-3 chiến đấu do BRIM tự xây dựng. Bản đầu có chiến dịch 12 chương, phòng PvP hai người, kỹ năng, vàng, kinh nghiệm và nâng cấp. Không sử dụng tên thương mại, hình ảnh, âm thanh hay thiết kế màn từ game điện thoại khác.

## Vào chơi

Trong lobby chọn **Vào Sơn Hà** hoặc mở `/warlords`.

- **Chiến dịch:** chọn chương đã mở trên bản đồ → vào chương → xuất quân. Đỏ/kiếm là sát thương; tích năng lượng để dùng kỹ năng. Khi thắng, quay về bản đồ để chọn chương tiếp theo hoặc nâng cấp.
- **Đấu tướng:** tạo bàn PvP, sao chép link phòng và gửi cho bạn. Người thứ hai mở link bằng phiên guest riêng, vào phòng và sẵn sàng. Có thể thêm bot ở phòng chờ để luyện tập. Hai tab cùng browser thường dùng chung phiên; dùng hai browser hoặc hai profile độc lập khi muốn hai người chơi.
- Có thể chọn hai loại game **Sơn Hà · Chiến dịch** và **Sơn Hà · Đấu tướng** trong form tạo phòng chung.

## Luật chiến đấu

Hai bên luân phiên dùng chung một bàn 8×8. Đổi hai ô kề ngang/dọc để ghép ít nhất ba tài nguyên cùng loại, hoặc đổi một quân đặc biệt để kích hoạt. Nước không tạo bộ ba và không kích hoạt quân đặc biệt được hoàn tác, không tốn lượt. Một lần đổi hợp lệ hoặc dùng kỹ năng tiêu tốn một lượt; ghép bốn/năm không cho thêm lượt.

| Tài nguyên | Hiệu ứng mỗi ô được ghép |
|---|---|
| Kiếm | Sát thương bằng chỉ số công của người đang đi; khiên đối thủ hấp thụ trước HP |
| Năng lượng | +6, tối đa 100 |
| Tim | Hồi 3 HP, không vượt HP tối đa |
| Khiên | +4, tối đa 80 |
| Vàng | +2 chiến lợi phẩm; mỗi bên tối đa 100 vàng nhặt trong một trận chiến dịch |
| Quân thư / kinh nghiệm | +3 XP chiến lợi phẩm; tối đa 180 XP nhặt trong trận chiến dịch |

### Quân đặc biệt và nổ dây chuyền

| Quân | Cách tạo | Khi kích hoạt |
|---|---|---|
| Hoành trận ↔ | Ghép 4 ô ngang | Xóa cả hàng ngang |
| Tung trận ↕ | Ghép 4 ô dọc | Xóa cả cột dọc |
| Hỏa trận ✹ | Hai bộ ghép giao nhau thành chữ T hoặc L | Nổ 9 ô trong vùng 3×3, cắt theo mép bàn |
| Trận chữ thập ✣ | Ghép từ 5 ô thẳng hàng | Xóa cả hàng ngang và cột dọc |

Quân đặc biệt giữ loại tài nguyên gốc; vẫn ghép được với các ô thường cùng loại. Mỗi nhóm ghép liền nhau tạo tối đa một quân, ưu tiên chữ thập rồi bom rồi hàng/cột. Vị trí tạo ưu tiên ô đích của nước đổi, sau đó ô nguồn, rồi ô thường đầu tiên trong nhóm. Ô được giữ để tạo quân chưa cho tài nguyên cho tới khi bị xóa ở đợt sau.

Đổi quân đặc biệt với một ô kề luôn hợp lệ, kể cả không có bộ ba; cả hai ô đổi bị tiêu thụ và vụ nổ xuất phát tại vị trí sau đổi. Ghép trúng quân đặc biệt hoặc để vụ nổ chạm nó cũng kích hoạt được. Mỗi quân kích hoạt một lần và mỗi ô chỉ cho tài nguyên một lần trong một đợt, dù các vùng nổ giao nhau. Quân vừa tạo được bảo vệ trong đợt tạo ra nó, nhưng có thể kích hoạt từ đợt liên hoàn tiếp theo của cùng nước đi. Bàn là dùng chung: đối thủ cũng có thể tận dụng quân bạn vừa tạo.

Hoành trận, Tung trận, Hỏa trận và Trận chữ thập là cơ chế hư cấu cho game; tên gọi và hiệu ứng nổ không mô tả vũ khí thế kỷ X.

Các bộ ba đồng thời và ô giao nhau được hợp nhất. Xóa, rơi và sinh mới có thể tạo liên hoàn. Sát thương kiếm ở các đợt tiếp theo tăng 25% mỗi đợt, tối đa hệ số 1,75. Tối đa 30 đợt liên hoàn; nếu còn match hoặc hết nước đi, server tạo lại bàn không có match sẵn và có nước hợp lệ. Hiệu ứng hồi máu/năng lượng/khiên được áp dụng trước sát thương trong cùng một đợt. Khi có bên hết HP, kết quả trận được xác nhận và không áp dụng phần thưởng các đợt sau đó.

| Kỹ năng | Năng lượng | Hiệu ứng ở cấp binh pháp 0 |
|---|---|---|
| Phá trận | 30 | 28 sát thương |
| Hồi quân | 25 | Hồi 30 HP |
| Thiết bích | 20 | Thêm 40 khiên |

Không thể dùng Hồi quân khi đầy HP hoặc Thiết bích khi đủ 80 khiên. Chiến dịch: mỗi cấp binh pháp thêm 6 sát thương / 5 HP hồi / 4 khiên tương ứng. Mỗi lượt có 45 giây. Quá hạn mất 12 HP và đổi lượt; bỏ ba lượt của mình liên tiếp sẽ thua. Từ lượt 40, người vừa hành động mất HP do hao quân, bắt đầu 4 HP và tăng thêm 1 mỗi bốn lượt, xuyên khiên. Hết HP là thua; rời phòng PvP khi đối thủ còn trong phòng cũng xử thua.

## Chiến dịch và lịch sử

Người chơi dẫn một quân đoàn từ Hoa Lư qua 12 chương. Mỗi chương có đối thủ, địa danh, lời dẫn và lời kết riêng. Trần Lãm và Phạm Bạch Hổ xuất hiện trong các chương thử tài/thao luyện; chiến thắng trên bàn không có nghĩa họ bị tiêu diệt.

Nhân vật và địa danh lấy cảm hứng từ bối cảnh lịch sử. Thứ tự chương, hội thoại, chỉ số, nâng cấp và các trận đấu là sáng tác cho game, **không phải niên biểu hoặc mô phỏng sự kiện lịch sử**. Bản đồ là lộ trình chương minh họa, không thể hiện ranh giới lãnh thổ chính xác.

Nguồn đối chiếu tên/bối cảnh:

- [Bảo tàng Lịch sử quốc gia: Tầm nhìn chiến lược của Đinh Bộ Lĩnh](https://baotanglichsuquocgia.vn/vi/Articles/3096/14529/tam-nhin-chien-luoc-cua-djinh-bo-linh.html).
- [Khâm định Việt sử thông giám cương mục — bản số hóa](https://vietnamvanhien.org/kdvstgcm.pdf), đoạn chép các sứ quân và căn cứ: Bình Kiều, Phong Châu, Hồi Hồ, Tam Đái, Đường Lâm, Đỗ Động, Siêu Loại, Tiên Du, Tế Giang, Tây Phù Liệt, Đằng Châu và Bố Hải Khẩu. Cách viết Lã/Lữ Đường có dị bản; giao diện dùng Lã Đường.

Hub và hướng dẫn trong trận có bảng dấu mốc 965–967, việc lập quốc hiệu Đại Cồ Việt và chọn Hoa Lư làm kinh đô năm 968, cùng địa thế núi/sông của Hoa Lư. Hình ảnh cờ lau được giới thiệu là truyền thuyết. Nội dung tham khảo [Bảo tàng Lịch sử quốc gia](https://baotanglichsu.vn/vi/Articles/3096/14529/tam-nhin-chien-luoc-cua-djinh-bo-linh.html) và [Sở Văn hóa Ninh Bình](https://vhtt.ninhbinh.gov.vn/vi/di-san-van-hoa/dong-hoa-lu-174.html).

## Hồ sơ và phần thưởng

### Chọn tướng

Chọn một trong sáu tướng miễn phí ở hub hoặc bản đồ trong phòng chiến dịch. Lựa chọn lưu cùng hồ sơ Redis, giữ nguyên tài nguyên và tiến độ. Tướng mặc định của hồ sơ cũ là Đinh Bộ Lĩnh. Đổi tướng chỉ áp dụng ở trận tiếp theo; không đổi tướng giữa trận. Vai trò và chỉ số dưới đây là thiết kế hư cấu của game:

| Tướng | Phong cách chiến dịch |
|---|---|
| Đinh Bộ Lĩnh | Chỉ số cân bằng ban đầu |
| Đinh Liễn | −12 HP, +1 công/kiếm |
| Nguyễn Bặc | +24 HP, −1 công/kiếm, 12 khiên đầu trận |
| Đinh Điền | −8 HP, +1 binh pháp |
| Lê Hoàn | 25 năng lượng đầu trận |
| Dương Vân Nga | +12 HP, 16 khiên đầu trận |

Các chỉ số cộng vào nâng cấp và cấp nhân vật trong chiến dịch. PvP dùng chân dung tướng đã chọn, luôn giữ 140 HP, 5 công/kiếm, binh pháp 0, 10 năng lượng và 0 khiên đầu trận.

### Theo dõi lượt đối thủ

Cả người chơi và đối thủ có bước làm nổi bật hai ô chọn trước khi đổi chỗ; lượt đối thủ dùng viền xanh. Tên người đang thao tác hiện trong thanh trạng thái, HP/năng lượng/khiên cập nhật theo từng đợt xóa quân hoặc kỹ năng. Các snapshot liên tiếp được xếp hàng để phát đủ lượt người và bot, thao tác bị khóa tới khi phát xong. Chế độ giảm chuyển động chuyển thẳng tới trạng thái mới nhất và vẫn hiện mô tả nước đi gần nhất.

Hồ sơ khởi đầu có 100 vàng, cấp 1. Cứ 100 XP tăng một cấp, tối đa cấp 20. Mỗi cấp thêm 8 HP trong chiến dịch. Vũ khí, giáp và binh pháp nâng tối đa 5 lần:

- Vũ khí: thêm 1 công/ô kiếm; giá `60 × (cấp nâng hiện tại + 1)`.
- Giáp: thêm 15 HP tối đa; giá `50 × (cấp nâng hiện tại + 1)`.
- Binh pháp: tăng cả ba kỹ năng; giá `80 × (cấp nâng hiện tại + 1)`.

Nâng cấp không hồi máu hay đổi chỉ số giữa trận, chỉ áp dụng từ trận chiến dịch tiếp theo. Thắng chương nhận thưởng cơ bản của chương và chiến lợi phẩm; lần đầu hoàn thành chương thêm 50 vàng/30 XP và mở chương kế tiếp. Thua nhận nửa vàng nhặt, 10 XP cộng nửa XP nhặt. Rút quân khỏi chiến dịch không nhận thưởng trận bỏ dở.

PvP luôn có 140 HP, 5 công/kiếm, binh pháp 0 cho cả hai, không dùng nâng cấp chiến dịch. Người thắng nhận 25 vàng/30 XP cộng chiến lợi phẩm, giới hạn phần chiến lợi phẩm ở 40 vàng/60 XP. Người thua nhận 5 vàng/10 XP cộng một nửa phần chiến lợi phẩm trong cùng giới hạn đó. Bàn PvP chứa bot là luyện tập, không nhận vàng/XP. Thưởng leaderboard của nền tảng chỉ phát khi trận PvP kết thúc; phần thưởng kinh tế Sơn Hà được lưu trực tiếp trong Redis, không phụ thuộc Kafka.

Hồ sơ lưu ở `profile:warlords:{playerId}` trong Redis, theo danh tính do server cấp. Tải lại hoặc tạo phòng khác trong cùng phiên giữ vàng/XP/nâng cấp/chương đã mở. BRIM hiện dùng guest session, chưa có tài khoản lâu dài: logout, phiên hết hạn hoặc tạo danh tính guest mới sẽ không tự gắn lại hồ sơ cũ. Redis cần giữ volume để không mất dữ liệu khi xóa container.

## Server, reconnect và giới hạn bản đầu

- Server sở hữu toàn bộ bàn cờ, RNG, HP, năng lượng, kỹ năng và phần thưởng. Client chỉ gửi tọa độ/hành động. Không có API nhận điểm, vàng hay XP do client khai.
- Mỗi lệnh mang `encounterId` và `revision`. Lệnh trùng, trễ, sai lượt hoặc thuộc trận trước bị từ chối.
- Nâng cấp và cộng thưởng được tuần tự hóa theo người chơi. Biên nhận chống nhận thưởng trùng; giữ 256 biên nhận gần nhất và watermark từ chối yêu cầu đã cũ. Cơ chế khóa hiện dành cho một backend, như kiến trúc BRIM hiện tại.
- Nếu lưu thưởng thất bại, màn kết quả hiện nút **Lưu lại phần thưởng**; cần giữ phòng để thử lại. Đã lưu một bên thì thử lại không cộng bên đó lần nữa. Pending reward và trận đang đánh ở RAM; chưa có outbox/checkpoint để phục hồi các phần này sau khi backend bị restart.
- Bot NPC và bot PvP dùng `BotExecutor`. Chiến dịch tăng độ khó mỗi ba chương: chương 1–3 tập sự chọn trong các bộ ghép tốt; 4–6 cân nhắc sát thương, hồi phục, khiên và kỹ năng; 7–9 đọc liên hoàn nhìn thấy và dè chừng phản công; 10–12 đánh giá nước đáp trả với trọng số đầy đủ. Bot PvP dùng mức 3. Tìm kiếm giới hạn tám nước ứng viên ở bước xét phản công, không đọc RNG hoặc quân chưa sinh; ô chưa biết giữ trống trong mô phỏng. Bot chờ tối thiểu 900 ms trước lượt, được scheduler thử lại, không chiếm worker bằng sleep.
- WebSocket đồng bộ cả hai người. Snapshot tách khỏi bàn đang thay đổi. Trạng thái cuối PvP được giữ theo lifecycle phòng để tải lại vẫn xem kết quả.
- Màn chiến đấu tự co bàn cờ theo chiều cao viewport, giữ HP và kỹ năng cùng màn hình ở cả chế độ dọc/ngang. Hướng dẫn, nhật ký và rút quân nằm trong hộp thoại “Hướng dẫn / Nhật ký”. Hoạt ảnh trượt đổi chỗ, xóa và rơi ô chạy tuần tự theo sự kiện SWAP/CLEAR/FALL từ máy chủ; khóa thao tác trong lúc xử lý và tôn trọng chế độ giảm chuyển động. Khi kết nối lại, giao diện lấy ngay bàn mới nhất. Có đạn/hiệu ứng sát thương và nút gợi ý một nước. Các SVG ô tài nguyên, tướng và bản đồ là mã tự tạo trong repo.
- Bàn cờ hỗ trợ phím mũi tên để di chuyển, Enter/Space chọn hai ô kề nhau, Esc bỏ chọn; Tab chuyển sang điều khiển tiếp theo. Thanh lượt có thời gian còn lại trên 45 giây; mỗi kỹ năng hiện lý do bị khóa hoặc trạng thái sẵn sàng. Bảng tài nguyên trên desktop và hướng dẫn trên mobile giải thích hiệu ứng mỗi ô. Rời trận hoặc rút quân cần xác nhận trong giao diện; lúc nối lại mạng, bàn khóa thao tác đến khi nhận snapshot mới.

Chưa có bang hội, giao dịch, cửa hàng tiền thật, gacha, xếp hạng riêng hoặc âm thanh. Chưa có di chuyển tự do trên bản đồ; người chơi chọn các chương đã mở trên lộ trình.

## Mã và kiểm tra

Engine: `game/warlords/CombatBoard`, `WarlordsGame`, `WarlordsCampaign`. Module: `config/WarlordsModules`. Lưu hồ sơ: `service/WarlordsProfileService`. REST: `GET /api/warlords`, `POST /api/warlords/upgrade` (chỉ nhận loại nâng cấp), `POST /api/warlords/hero` (chỉ nhận heroId trong danh sách server). REST và STOMP dùng chung danh tính phiên đã xác thực.

Frontend: `/warlords`, `/game/warlords-campaign/:roomId`, `/game/warlords-duel/:roomId`. Tạo phòng bằng enum `WARLORDS_CAMPAIGN` (một người) hoặc `WARLORDS_DUEL` (hai người).

```bash
bash ./mvnw test
cd frontend
npm run build
```

`WarlordsGameTest` kiểm tra sáu tài nguyên, HP/khiên, kỹ năng, lượt, lệnh cũ, kết quả, timeout/rời phòng và retry thưởng. `WarlordsProfileTest` kiểm tra giá nâng cấp, mở chương, nhận thưởng trùng, mất phản hồi sau commit và cập nhật đồng thời. `WarlordsSocketIntegrationTest` mở hai HTTP session và hai kết nối STOMP thật trên server test; Redis/Kafka được thay bằng test double để không chạm hạ tầng hoặc dữ liệu đang chạy.

`CombatBoardSpecialTest` kiểm tra tạo bốn loại quân, kích hoạt không cần bộ ba, nổ dây chuyền, cắt biên, không cộng tài nguyên trùng, bảo vệ quân mới tạo, hoàn tác nước sai, bot chỉ đọc ô nhìn thấy và khôi phục đúng RNG từ cả bàn cũ lẫn bàn có quân đặc biệt. Snapshot bàn dùng giá trị `loại tài nguyên + 10 × loại quân đặc biệt` (loại đặc biệt 0–4); số hàng/cột vẫn là 8. Sự kiện `SPECIAL_ACTIVATED` giữ ảnh bàn trước khi xóa; `SPECIAL_CREATED` giữ ảnh bàn sau khi tạo để client phát hiệu ứng đúng thứ tự.
