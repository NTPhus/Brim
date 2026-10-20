# Dấu chân Việt — đặc tả hành trình chiến tranh, phiên bản 2

**Cập nhật:** 20/10/2026, theo góp ý sau bộ ảnh review. **Trạng thái:** thiết kế cần triển khai; chưa phải tính năng đã hoàn thành. Đợt này chỉ sửa docs và bản xuất HTML/PDF.

Định hướng mới: một hành trình dài, có nhân vật, hội thoại, hậu cần, thương tích, trinh sát và nhiều màn riêng; không kết thúc sau vài waypoint. [Ảnh review bản hiện tại](VIETNAM_CAMPAIGN_REVIEW.md) là baseline, không phải ảnh minh họa tính năng tương lai. [Spec P0 lưu trữ](archive/VIETNAM_CAMPAIGN_P0_2026-10-19.md) giữ thông số/contract/test chi tiết trước lần sửa này; khi có khác biệt về trải nghiệm, phiên bản 2 được ưu tiên.

## 1. Những quyết định chính

| Trụ cột | Yêu cầu đã chốt theo góp ý |
| --- | --- |
| Hành trình | Nhiều phân đoạn có quyết định, gặp người, hội thoại, nguy hiểm và hậu quả; checkpoint an toàn; không kéo dài bằng đi bộ trong map trống |
| Quy mô | Map nhiều khu, nhiều encounter và địch hơn; tuần tra, chốt và nhóm phản ứng; không dồn toàn bộ quân vào một khung hình |
| Nhân vật | Tỷ lệ người dễ nhìn, trang phục và động tác nghiêm túc; giảm hình dáng đồ chơi/ngộ nghĩnh |
| Camera | Mặt đất nhìn chéo từ phía sau/trên vai, thấy chiều sâu; bỏ camera trực diện từ đỉnh đầu. Ngắm phòng không chuyển sang ngôi thứ nhất |
| Sinh tồn | Shift chạy, stamina, cover, bleeding, băng gạc và cứu thương; đạn hạn chế, lấy thêm từ địch và thùng |
| Đồng đội | 1–2 người đi theo; các cảnh có lực lượng giữ chốt/hỗ trợ ở xa, không tất cả cùng chạy theo |
| Thông tin | Phải đọc/nghe hội thoại để hiểu cách vượt màn; nhật ký cho đọc lại, không chỉ đi theo chấm vàng |
| Mỹ thuật | Map bớt sáng, đất/bùn/khói/công sự/nội thất, asset có chiều sâu; tối nhưng vẫn đọc được gameplay |
| Streaming | Đi tới đâu tải/vẽ tới đó với vành đệm; không render hoặc gửi toàn map/địch bí mật |
| Màn riêng | Giao liên Lượm, nhà dân, rừng/bẫy/phục kích, phòng không, chỉ huy và kíp xe có vòng chơi riêng |
| Ngắm | Nhân vật quay theo chuột, đạn bắn theo hướng; không đường kẻ nhân vật–chuột, không quỹ đạo/tâm nổ dự đoán |
| Lịch sử | Sau màn/chiến dịch có hồ sơ và trích dẫn lịch sử có nguồn, phân biệt rõ gameplay sáng tác |

Mục tiêu cảm xúc: căng thẳng nhưng hiểu tình huống; có đi, chờ, trò chuyện, ẩn náu và chăm sóc đồng đội xen giao tranh. Không phải mọi giây đều có địch xuất hiện.

## 2. Hiện trạng và các yêu cầu cũ tiếp tục giữ

Code hiện có ba chapter: 1 Điện Biên, 2 Củ Chi, 3 Tiến vào Dinh; map khoảng 1600 × 1000 unit, canvas từ trên xuống, mục tiêu cũ và AI đi theo. Nền P0 có xe nặng, pháo nạp 6 giây, đạn/nhiên liệu/tiếp tế hữu hạn, sửa xe, vật che, private snapshot và sequence/ACK. AI mặt đất Củ Chi còn dừng khi người chơi xuống hầm.

**Chưa có:** bleeding/băng gạc, loot từ địch, Shift chạy, hội thoại dẫn đường, streaming theo chunk, camera mới, nội thất nhà dân, bẫy, báo động theo tổ, Lượm, chỉ huy hoặc phòng không. Không dùng ảnh bản cũ để nói những phần này đã xong.

Giữ authoritative simulation, loaded/reserve accounting, swept collision, giáp theo hướng thân xe, tự sát thương, vật che chặn nổ, inventory không reset khi reconnect/đổi vai. Classic vẫn riêng, Assault là môi trường thử combat xe; không tự áp mọi luật chiến dịch vào Classic. Spec P0 và ma trận W/A/M/C/F tiếp tục là đầu vào hồi quy, trừ giá trị/định hướng được thay rõ trong bản này.

## 3. Độ dài, map và mật độ địch

Các con số dưới đây là **mục tiêu prototype để playtest**, không phải kết quả đã đạt hoặc quy mô hệ thống đã hỗ trợ.

| Loại màn | Thời lượng lần đầu | Phân đoạn | Phân bố lực lượng |
| --- | --- | --- | --- |
| Bộ binh/làng/rừng | 20–35 phút | 5–7 | 4–6 encounter, khoảng 30–50 địch trên toàn tuyến; không bắt diệt hết |
| Giao liên | 15–25 phút | 5–6 | 4–5 vùng tuần tra/chốt, thắng bằng tránh phát hiện và giao đúng thư |
| Địa đạo | 20–30 phút | 5–7 | Nhánh hầm, điểm mặt đất và tổ giữ cửa |
| Kíp xe | 20–30 phút | 5–7 | Hậu cần, chướng ngại, đội hình và tiếp cận; không thêm địch ở sân Dinh |
| Phòng không | 15–25 phút | Chuẩn bị, 3–4 đợt, cao trào | Nhiều pass/hướng bay, nghỉ tiếp tế và hiệp đồng cuối |
| Vận tải Trường Sơn | 25–40 phút | 6–8 | Đoàn xe, trạm nghỉ, đoạn đường khó và cảnh báo; không bắt diệt địch |
| Chỉ huy | 20–35 phút | 3 mục tiêu lớn có nhánh | 2–3 tổ của ta, chốt/tuần tra/tiếp viện hữu hạn |

Một encounter thử nghiệm có khoảng 6–10 địch, khởi đầu giới hạn 12–16 AI cần quyết định thường xuyên trong vùng giao tranh. Quân xa dùng lịch/simulation cấp khu vực, không biến mất vì camera không nhìn tới. Tăng số encounter trước khi tăng số AI trong cùng tick; đo CPU, path và snapshot.

Map ngoài trời prototype gồm 4–6 khu, mỗi khu khoảng 1200–1800 unit một cạnh; kích thước tổng chốt theo route và checkpoint. Mỗi khu có mốc nhận diện, vật che, đường chính/đường vòng và lý do để tới. Không phóng to map cũ thành đất trống. Mỗi màn cần hai tuyến hoàn thành và một phương án rút/tiếp tế khi hụt đạn. Thời lượng đo loại trừ AFK; không lấy chờ thoại dài làm cách tăng độ dài.

## 4. Camera, nhân vật, chạy và bắn

### 4.1. Góc chéo và tỷ lệ nhân vật

Đích là góc nhìn thứ ba chéo từ phía sau/trên vai, thấy mặt bên và chiều sâu. Thử góc nhìn xuống khoảng 35–50°, phương hướng ổn định; chỉ thêm xoay camera khi không gây mất định hướng. Nhân vật thử cao 64–96 px ở zoom desktop chuẩn, tay/chân/súng/túi dễ đọc, không đầu quá to. Thử trên mobile trước khi chốt pixel/scale.

Animation cần: idle, đi, chạy, khom, bò, áp cover, ngắm/bắn, nạp, băng bó, mang hàng, trúng đòn và hỗ trợ đồng đội. Props chắn camera được làm mờ/cắt lớp nhưng không mất collider hoặc lộ địch sau tường. Nhà có mái/tường occlusion riêng; nhìn thấy nhân vật không đồng nghĩa được nhìn xuyên toàn nội thất.

**Hệ quả kỹ thuật:** canvas hiện tại không thành ngôi ba/FPS chỉ bằng xoay tọa độ. R0 phải thử renderer có chiều sâu/camera/asset tương ứng. 2.5D prerender có thể dùng thử art; nếu cần orbit, che khuất và chuyển FPS liên tục cần đánh giá renderer 3D. Chưa chốt library/đổi engine, chưa mô tả camera mới là đã có. World coordinates, collision và renderer phải tách biệt.

### 4.2. Bắn nhanh hơn, không aim line

Bộ binh xoay theo hướng chuột và bắn theo hướng đó. Với xe, tháp tiếp tục có tốc độ quay, đạn rời nòng theo hướng nòng thực. Được có crosshair nhỏ; **không tia nối người–chuột, không đường ném dự đoán, không vòng tâm nổ trước impact**.

Vận tốc thử: bộ binh 1600–2400 unit/s; pháo 800–1200 unit/s thay vì 500 hiện tại, cân bằng theo map lớn. Đơn vị game, không là thông số khí tài thật. Swept collision bắt buộc để đạn nhanh không xuyên tường/xe. Dùng muzzle flash, âm và impact để phản hồi; nếu có tracer thì chỉ là đoạn ngắn theo viên đạn đã bắn, không là hướng dẫn ngắm hoặc một đường kéo dài tới chuột. Cover/LOS áp dụng cho cả ta và địch.

### 4.3. Input và stamina

| Input | Hành động dự kiến | Giới hạn |
| --- | --- | --- |
| WASD | Bộ binh di chuyển; xe tiến/lùi, quay thân | Server xác nhận tốc độ/collider |
| Giữ Shift | Chạy nhanh | Tốn stamina, tăng tiếng động, không dùng lúc băng bó/đoạn hầm quá hẹp |
| C / Z | Khom / bò | Khác tốc độ/silhouette; không miễn thương |
| Chuột / chuột trái / R | Xoay-ngắm / bắn / nạp | Đạn hữu hạn, không aim line |
| E | Trò chuyện, tương tác, nhặt đồ, vào nhà/hầm/khí tài | Resolver và lựa chọn khi nhiều action hợp lệ |
| F | Băng bó/hỗ trợ; xe sửa theo ngữ cảnh | Icon và action rõ, không nhầm kit/medkit |
| G | Ném lựu đạn | Inventory, cooldown, animation; không preview đường ném |
| B | Dụng cụ bẫy trong màn cho phép | Item và điểm gameplay hợp lệ |
| Tab / 1–5 | Command mode / FOLLOW, HOLD, ADVANCE, AMBUSH, RETREAT | Click đặt lệnh không bắn |
| Esc | Menu/nhật ký | Không tự pause simulation thời gian thực |

Stamina thử: 100, chạy tiêu 18/s, hồi 12/s sau nghỉ 1,5 s; chạy khoảng 1,5 lần đi. Thương tích/mang hàng ảnh hưởng chạy. Đo theo thời gian server, không FPS/input count. Mobile có nút chạy/ngắm/băng bó, thiết kế ít tổ hợp ngón; blur xóa held input nhưng không xóa thương tích hoặc lệnh tổ.

## 5. Bleeding, băng gạc, hồi máu và loot

### 5.1. Vòng thương tích

**Trúng đòn → bị thương/chảy máu → tìm cover → băng bó → hết bleeding nhưng chưa đầy HP → hồi phục giới hạn/đến quân y.** Các số là cân bằng game, không hướng dẫn y tế.

- Đòn hợp lệ có thể gây bleeding theo profile. Một trạng thái severity hữu hạn, có cooldown chống cộng dồn mọi viên thành vô hạn stack.
- Bleeding thử mất 0,5 HP/s, server quản lý. Có icon, máu nhẹ trên trang phục/dấu gần nhân vật, phản hồi thở; giảm FX/flash được nhưng vẫn hiểu trạng thái.
- Băng gạc mất khoảng 3 giây khi đứng an toàn, dừng bleeding nhưng không hồi đầy. Reserve một món lúc bắt đầu; hoàn lại nếu chưa xong mà bị ngắt; consume đúng một lần lúc xong. Chạy/trúng đòn có thể ngắt theo policy.
- Medkit khác băng: sau cầm máu, hồi thử 35 HP/5 giây khi an toàn. Không dùng thuốc để bỏ qua bleeding. Có thể tới gần hỗ trợ đồng đội bằng vật tư hữu hạn.
- Có alive/downed/dead và khoảng thời gian cứu ở màn chiến đấu. Reconnect/đổi actor/đổi camera/checkpoint không hồi sinh, reset thương tích hoặc sinh thêm đồ.
- Với giao liên nhỏ tuổi: thương tích thể hiện tiết chế, trọng tâm né tránh/nguy hiểm; không hiệu ứng đồ họa phóng đại.

### 5.2. Vật phẩm từ địch và map

| Vật phẩm | Nguồn | Quy tắc |
| --- | --- | --- |
| Băng gạc | Trang bị ban đầu, trạm quân y, thùng và một số inventory địch | Có điểm bảo đảm trên tuyến, không phụ thuộc RNG để tránh chết chắc |
| Medkit | Quân y/NPC hỗ trợ, thùng; một số địch có xác suất thấp | Không hồi đầy tức thì hoặc sinh lại qua reload |
| Đạn | Thùng, kho, đồng đội chuyển và địch có loại phù hợp | Kiểm tra tương thích/sức chứa, phần dư giữ lại |
| Lựu đạn | Loadout/kho hoặc địch phù hợp theo mission | Số ít, không spam vô hạn |
| Dụng cụ bẫy | Màn rừng/tiếp tế định trước | Chỉ trừ khi đặt thành công |

E lục/nhặt ở gần, có thời gian và có thể gây tiếng động. Loot gắn container ID và inventory server. Địch chết tạo loot theo inventory/quy tắc seed một lần, không reroll khi mở UI, quay camera hoặc reconnect. Snapshot chỉ gửi đồ được phép thấy; cùng một xác/hòm không farm qua checkpoint.

Đông địch không đồng nghĩa phải diệt tất cả. Mỗi tuyến có budget đạn/băng/kho bảo đảm đủ giao tranh bắt buộc và fallback né/rút. Loot RNG là phần thưởng thêm; không là điều kiện duy nhất để khỏi softlock. Giữ loaded/reserve, phần dư và inventory từng đồng đội; không mọi loại súng dùng cùng đạn.

## 6. Ẩn nấp, báo động, lựu đạn, bẫy và đồng đội

### 6.1. Một địch thấy có thể kéo cả tổ

State machine: tuần tra → nghi ngờ → kiểm tra dấu/tiếng động → phát hiện → gọi tổ/chạm trán → tìm vị trí cuối → giảm cảnh giới. Hô/radio có độ trễ, phạm vi và nhóm nhận tin; không toàn map biết tọa độ thật ngay lập tức. Cắt LOS bằng nhà/rừng/cover khiến địch điều tra vị trí cuối, chia hướng tìm, không bám vị trí server bí mật.

Có cơ hội ngắt/cắt cảnh báo trước khi lan; tiếp viện hữu hạn có tuyến vào đọc được, không spawn ngay lưng người chơi. Địch dùng cover, nạp và đổi vị trí; không bắn/nhìn xuyên tường. Sound event có layer, bán kính, vật liệu chắn và ưu tiên. Cửa mở, đồ đổ, vật treo hoặc vật gây chú ý đánh dấu được hút tuần tra; không mỗi props đều có tác dụng và không click vô hạn để đóng băng AI.

### 6.2. Lựu đạn và bẫy rừng

Lựu đạn server-owned có action ID, animation, đường bay/thời gian nổ và collider. Cover/friendly fire theo mission, không preview quỹ đạo/tâm nổ. Địch có thể phản ứng với cue nhưng không biết chính xác impact tương lai.

Bẫy chỉ là cơ chế game trừu tượng: dùng item, chọn điểm thiết kế, chờ thao tác, chuyển armed/triggered/disarmed. Không mô tả cách chế tạo, vật liệu hay bố trí bẫy ngoài đời. Địch có khả năng phát hiện/vô hiệu hóa theo luật; đồng đội tránh điểm bẫy đã biết. Trap ID/state lưu checkpoint, retry không tạo bản sao.

### 6.3. Hai lớp đồng đội

1. **1–2 đồng đội đi theo:** có tên/cá tính, HP, ammo, wounds, lệnh, thoại; giữ khoảng cách, tránh chắn cửa/hầm, biết tìm cover và nhắc thiếu đạn.
2. **Lực lượng theo cảnh:** quân giữ chốt, quân y, dẫn đường, tổ ở xa, tiếp ứng. Giữ vùng/vai riêng, không tất cả dồn theo người chơi; có hỗ trợ hỏa lực/thoại hoặc nhiệm vụ theo kịch bản.

Quân xa có ảnh hưởng kết quả phải có state và simulation/event LOD; không vì ngoài camera mà bất tử hoặc ngừng bắn. Nhóm chỉ làm cảnh được ghi rõ là background. Màn phù hợp kiểm tra minimumSurvivors; không bắt mọi màn cùng một điều kiện.

## 7. Hội thoại giúp biết cách vượt màn

Đối thoại phải cung cấp đường đi, dấu mốc, người cần gặp, cửa đúng, thời điểm an toàn hoặc điều kiện nhiệm vụ; không chỉ nói vài câu trước khi hiện tọa độ. Người chơi dùng thông tin đó để chọn đường, thời cơ và hành động.

| Ngữ cảnh | Câu thoại gameplay mẫu do thiết kế sáng tác | Ý nghĩa |
| --- | --- | --- |
| Giao liên | “Đừng qua cầu. Tìm người gác cạnh mái nhà có đèn.” | Đổi tuyến, nhận diện đúng NPC |
| Nhà dân | “Chờ tuần tra qua rồi hãy mở cửa sau.” | Quan sát/đợi, dùng lối thoát |
| Địa đạo | “Qua bếp thì rẽ trái, kho ở cuối đoạn thấp.” | Ghi nhớ dấu hiệu và nhánh hầm |
| Phục kích | “Giữ yên, đợi hiệu lệnh của tổ.” | HOLD/AMBUSH thay vì tự bắn ngay |
| Kíp xe | “Dừng chỗ khuất, kiểm tra nhiên liệu trước khi đi.” | Hậu cần và đội hình |
| Phòng không | “Có báo động. Đưa người vào chỗ trú rồi về vị trí.” | Ưu tiên bảo vệ, không chỉ bắn |

Đây không là trích dẫn nhân vật thật. Dialogue node có ID/speaker/prerequisite/choices/journal/outcomes. Lựa chọn có tác dụng như đổi tuyến/người dẫn/thời điểm. NPC chết hoặc bỏ lỡ câu nói có fallback qua thư/đồng đội/nhật ký; không softlock. Bỏ animation/voice được nhưng thông tin thiết yếu lưu đọc lại; không bắt nghe âm thanh mới chơi được. Không thoại toàn tri làm lộ địch chưa quan sát.

HUD chỉ hiện mục tiêu và tình báo đã nhận, không tự đánh dấu mọi bí mật. Có chế độ trợ giúp dựa trên thông tin đã biết. Subtitle đủ lớn, tốc độ đọc hợp lý; không đẩy đoạn thoại dài lúc giao tranh dồn dập. Thoại không lặp để farm vật tư/tiến trình.

## 8. Đi tới đâu tải và render tới đó

Tách **streaming asset**, **camera culling** và **server AOI/LOS**. Giấu sprite không ngăn lộ dữ liệu; unload chunk không xóa quân/mục tiêu trên server.

- Chunk prototype 512 × 512 unit, ID ổn định cho props/tile/trigger. Vẽ vùng camera + buffer; preload 1–2 vòng theo hướng đi, unload LRU với ngân sách RAM/GPU. Shift/xe không được chạy nhanh hơn tốc độ tải dự phòng.
- Manifest/atlas theo biome/chapter, không tải toàn bộ texture chất lượng cao của chiến dịch trước trận. Chỉ loading ở điểm vào/khu lớn, không khựng ở mọi ranh chunk.
- Backend giữ world, gửi entity/delta theo AOI + LOS/layer; preload không kèm vị trí địch bí mật, loot chưa biết hoặc minimap toàn tri.
- Quân/sự kiện xa dùng simulation LOD có lịch/state. Bleed, reload, cảnh báo, đoàn tuần tra, mục tiêu bảo vệ vẫn tiến triển. Củ Chi không freeze surface khi xuống hầm.
- Nhà/hầm có portal/layer/occluder. Qua khu giữ đồ đang mang, wound, loot, thoại, companion và alert. Tải asset lỗi không thay collision, có fallback/retry giới hạn.
- Navgraph/pathfinding có budget; không tạo job mỗi packet chuột. Không đọc DB/tải file chậm trong tick combat.

Mục tiêu đo đầu: 60 FPS desktop, 30 FPS mobile trung bình; p95 tick dưới khoảng 30 ms ở tick 100 ms; không đứng hình >100 ms khi qua chunk trên tuyến thử. Ghi cấu hình máy và build; đây chưa là cam kết hiệu năng đã đạt.

## 9. Không khí thời chiến và asset cần bổ sung

### 9.1. Art direction

Đất/bùn/xanh rêu/xám khói, ánh sáng theo giờ/thời tiết/bối cảnh. Điện Biên có đất đào, hào, bao cát và dấu pháo; rừng có tán nhiều tầng, sương/ánh lọt lá; làng có nội thất/bếp/hàng rào/cửa sau; hầm có trần thấp/đèn nhỏ/âm vang. Dinh giữ nhận diện kiến trúc, không mặc định mọi màn đều đêm tối.

Giảm sáng bản cũ nhưng giữ silhouette, vật tương tác và mép cover đọc được. Bóng, bụi, khói vừa phải, dấu chân, đồ dùng và âm xa tạo không khí; không phủ lớp đen/khói tới mức không chơi được.

### 9.2. Backlog asset có đầu ra

| Nhóm | Asset cần chuẩn bị | Nghiệm thu |
| --- | --- | --- |
| Nhân vật | Bộ binh, đồng đội có nhận diện, Lượm, dân, quân y, địch đúng hồ sơ | Tỷ lệ người, pose/hướng/animation dễ nhìn |
| Địa hình | Đất khô/ướt, bùn, cỏ, đường, ruộng, đá, lá | Texture lặp kín, scale thống nhất, nav/collision tách hình |
| Công sự | Hào góc/ngã ba, bao cát, gỗ, công sự, cửa hầm | Độ cao/cover đúng cả hai phía |
| Làng | Nhà mái ngói/tranh theo nguồn, cửa/nội thất/hàng rào/đồ dùng | Vào/ra thật, occlusion, đồ gây chú ý có cue |
| Rừng | Cây/tán/bụi nhiều lớp, thân đổ, đường mòn, điểm bẫy game | Ẩn nấp có luật, không đi xuyên collider |
| Hầm | Trần/tường đất hẹp, nhánh/dốc/phòng/cửa/đèn | Ngột ngạt nhưng định hướng/di chuyển được |
| Xe | 390/843, thân/xích/tháp hoặc rig, hậu cần/chướng ngại | Hướng thân/nòng rõ, không dùng model sai niên đại |
| Vận tải Trường Sơn | Xe tải không kính, cabin, bánh/nhíp, thùng/bạt, bao lương thực, trạm giao nhận, đường rừng/dốc/ngầm và props sửa xe | Hàng có vị trí/ID, xe khác tank về lái/trọng lượng; model/biên chế cần hồ sơ nguồn |
| Phòng không | Khí tài đúng vai, buồng ngắm FPS, máy bay/bom/shelter/quân xa | Camera switch cùng ngôn ngữ hình ảnh |
| Đồ/FX | Thư, đạn, băng, medkit, hòm, bleeding nhẹ, flash/impact/khói | Trạng thái phân biệt, không aim line |
| Âm thanh | Bước chân/bề mặt, thở, nạp, thoại/radio, hầm, cảnh giới | Có subtitle/cue hình ảnh tương đương |

Nguồn tuyển chọn: [Poly Haven](https://polyhaven.com/license) cho vật liệu/model nền, [ambientCG](https://docs.ambientcg.com/license/) cho texture; hai nguồn công bố asset CC0. [Kenney](https://kenney.nl/support) có asset CC0 cho greybox/prototype nếu phù hợp, không mặc định đáp ứng đích tả thực. **Chưa tải/cài hoặc chọn xong pack** trong lần viết docs. Asset có quyền dùng chưa chắc đúng bối cảnh Việt Nam; props đặc thù cần tự làm/đặt làm, không ghép nguyên làng châu Âu.

Manifest mỗi asset: ID, nguồn trực tiếp, tác giả, license/file license, ngày lấy, biome, scale/pivot, collider proxy, animation/hướng, texture size/LOD, kiểm tra bối cảnh. Ưu tiên 1K/2K theo khoảng nhìn, atlas/LOD mobile; chốt ngân sách sau camera prototype. Không lấy ảnh bảo tàng/web làm texture khi chưa rõ quyền.

## 10. Các ý tưởng được tách thành màn riêng

Design ID dưới đây là **đề xuất**, chưa thêm runtime. Giữ ID cũ 1/2/3; displayOrder tách khỏi unlock/niên biểu. Không để một nhân vật sống qua tất cả thời kỳ.

| Design ID | Màn | Vai riêng | Chuỗi hành trình |
| --- | --- | --- | --- |
| VN-01, runtime 1 | Đường hào Điện Biên làm lại | Bộ binh + tổ | Nhận lệnh/thoại → tiếp cận → bị thương/tiếp tế → chiếm giữ → tập kết đủ quân |
| VN-02, runtime 2 | Mạch ngầm Củ Chi làm lại | Giao liên + tổ giữ cửa | Tránh tuần tra → hầm nhiều nhánh → mang cứu trợ → bệnh xá → đổi cửa ra → báo cáo |
| VN-03, runtime 3 | Tiến vào Dinh, hành trình kíp xe | Xe 390, đồng hành 843/đoàn xe | Kiểm tra/thoại → tiếp liệu → chướng ngại/tuyến vòng mô phỏng → hội quân → cổng/sân |
| VN-04, ID mới | Lượm — Chuyến thư qua vùng nguy hiểm | Cậu bé liên lạc, né tránh | Nhận thư → hỏi đường → tránh chốt → ẩn náu → giao đúng người → kết chặng |
| VN-05, ID mới | Sau cánh cửa làng | Bộ binh/giao liên, nhà dân | Gặp người dẫn → vào nhà → đợi tuần tra → gây chú ý → cửa sau → đưa tổ ra |
| VN-06, ID mới | Dưới tán rừng | Tổ phục kích | Trinh sát/thoại → bố trí tổ/bẫy game → chờ thời cơ → giao tranh → lấy đồ → rút |
| VN-07, ID mới | Giữ bầu trời | Dân quân/khẩu đội, hiệp đồng | Báo động/sơ tán → tiếp đạn/trú ẩn → FPS ngắm → nhiều đợt → cao trào B-52 đúng vai lực lượng |
| VN-08, ID mới | Giữ chốt — Đánh chiếm | Chỉ huy mini | Đọc tình báo → bố trí tổ → nghi binh/yểm trợ → chiếm → giữ quân → tiếp ứng/rút |
| VN-09, ID mới | Xe không kính — Dọc Trường Sơn | Chiến sĩ lái xe chở lương thực | Nhận hàng/kiểm xe → nghe chỉ dẫn → theo đoàn → vượt đường khó → trú/nghỉ/sửa → đổi tuyến → giao hàng/kiểm đếm (§20) |

Các màn làng/rừng/chỉ huy có thể nằm trong một chương lớn nhưng phải là nhiệm vụ riêng với briefing/checkpoint/debrief. Ngày/đơn vị/trận mới cần hồ sơ nguồn trước khi chốt, không gán ngẫu nhiên để có thêm “sự kiện lịch sử”.

## 11. Làm lại ba màn đang có

### 11.1. Điện Biên

Nhận quân/lệnh từ NPC, nghe chỉ dẫn tuyến hào/điểm gặp. 1–2 đồng đội theo, lực lượng khác giữ chốt xa. Hai tuyến: theo hào và vòng khu che; địch có cover, báo động và tiếp viện hữu hạn. Có tình huống băng bó/rút quân y; kho ở phía sau tạo quyết định ai giữ chốt/ai tiếp tế. Chiếm vùng khoảng 8 giây khi áp lực/quân số cho phép; cuối màn tập kết kiểm tra minimumSurvivors, không chỉ nhân vật chạm điểm vàng. Tuyến cụ thể là mô phỏng gameplay trong bối cảnh hồ sơ nguồn.

### 11.2. Củ Chi

Hầm hẹp hơn rõ, có đoạn một người, đoạn khom/bò, ngã ba, nhánh vòng, ngách cụt có dấu hiệu và nhiều cửa ra. Prototype 8–12 đoạn/nhánh, 3–4 điểm chức năng; không mê cung ngẫu nhiên thiếu chỉ dẫn. Thử nhiều tầng sau khi portal/path/asset đạt, không cho đổi layer tùy ý.

Cảm giác ngột ngạt từ trần thấp/ánh sáng/âm/bố cục/nhịp, không bó collider gây kẹt. Có điểm rộng để đổi vị trí đồng đội, đội đi theo xếp hàng; tổ khác giữ cửa mặt đất. Surface tuần tra/báo động vẫn chạy khi người chơi xuống hầm; không damage xuyên layer.

Hội thoại chỉ bếp/phòng/ngã rẽ; thư/cứu trợ có item ID, lượng và tốc độ mang, giao đúng nơi. E gần hòm/cửa/bệnh xá resolve đúng hoặc mở lựa chọn. Checkpoint giữ hàng, hòm cạn, AI và flags. Chưa thêm thiếu oxy như chỉ số bắt buộc khi chưa có thiết kế riêng.

### 11.3. Kíp xe 390

Thêm kiểm tra xe, liên lạc với đơn vị dẫn đường, tiếp nhiên liệu, sửa và chướng ngại/tuyến vòng có ý nghĩa. Độ khó từ phối hợp, đội hình và hậu cần; giao tranh giả định phải ghi rõ sáng tác. Không đặt tank địch ở sân Dinh để đáp ứng “địch ít”. Cổng có diễn hoạt tiếp cận/húc, không chỉ E rồi công trình biến mất. Giữ phân biệt xe 390 húc cổng chính và vai xe 843/Bùi Quang Thận trong hồ sơ/cảnh kết.

## 12. Lượm và ẩn náu trong nhà dân

### 12.1. Lượm — Chuyến thư

Nhập vai Lượm theo cảm hứng văn học từ bài thơ của Tố Hữu; tuyến đi/thoại gameplay sáng tác. [Bảo tàng Tố Hữu](https://tohuu.org/) liệt kê tác phẩm Lượm. Chưa coi tuyến chơi là tiểu sử/phục dựng sự kiện thật; cần hồ sơ riêng khi chốt nguyên mẫu/địa danh/thời gian. Không gán nhân vật vào mọi chiến dịch 1954/1972/1975.

Thư có ID/người nhận/tình trạng. Người chơi quan sát, chạy đúng lúc, khom/bò, chờ tuần tra, nghe người dẫn và tìm nhà trú; tương tác vật gây chú ý để lách đường. Không biến màn giao liên thành màn trang bị hỏa lực diệt hàng loạt địch. Thắng khi giao đúng người và hoàn tất chặng; bị bắt/mất thư/fail theo điều kiện đã thông báo. Không tự thêm kết cục lịch sử chưa kiểm chứng; nếu chuyển thể đoạn kết văn học phải ghi rõ và tách khỏi tuyến vượt màn. Không chép dài bài thơ vào thoại trong bản spec này.

### 12.2. Sau cánh cửa làng

Nhà có nội thất/cửa trước-sau có tác dụng; vào nhà không tự xóa truy đuổi. Cửa, bóng tối, tiếng động và vật che ảnh hưởng phát hiện. Địch tìm vị trí cuối/có thể kiểm tra nhà theo alert, không nhìn xuyên tường. Người dân có thoại và phản ứng, không chỉ là điểm hồi HP. Có lựa chọn chờ, nhờ chỉ đường, đổi lối hoặc gây chú ý; không bắt gây hại dân để vượt màn.

## 13. Phòng không: ngôi ba khi di chuyển, ngôi nhất khi ngắm

### 13.1. Hai camera cùng một state

Di chuyển giữa nơi trú, kho và khẩu đội dùng góc thứ ba chéo. E lên khí tài hợp lệ chuyển ngôi thứ nhất, có buồng ngắm/khí tài và bầu trời. E xuống trở về vị trí operator thật, không teleport. HP/ammo/heat/reload/thời gian đợt/mục tiêu bảo vệ không reset khi đổi góc. Một vị trí có một operator; thế giới mặt đất tiếp tục chạy khi đang ngắm.

### 13.2. Cảm giác bắn vịt với máy bay nhanh

Máy bay bay thấp vào từ nhiều phía, lượn vài vòng giữa các lượt công kích theo kịch bản, thay vận tốc/độ cong theo đợt; không chỉ sprite chạy ngang một lần. Người chơi đón đường bằng crosshair/tốc độ quay, đạn có thời gian bay; không click sprite là chắc trúng. Không aim line hoặc marker “bắn vào đây”.

Đợt có pass/máy bay hữu hạn và cue cảnh giới trước công kích. Ammo, burst, heat và khoảng nghỉ/tiếp đạn tạo nhịp. Bay vòng chỉ dành nhóm mục tiêu bay thấp được thiết kế cho minigame, không áp cho mọi loại hoặc B-52. Không spawn tấn công tức thì ngay khi chuyển camera.

### 13.3. Bom trúng thì nhiệm vụ thất bại

Tới cửa sổ công kích, máy bay thả bom có thời gian rơi/impact authoritative và cue công bằng; không vòng nổ tương lai. **Bom trúng và phá hủy mục tiêu bắt buộc bảo vệ thì fail**, dù số máy bay hạ cao. Bom trực tiếp hạ nhân vật trong lượt không có cứu cũng fail theo mission policy. Bom rơi trượt/vào khu an toàn không tự fail cả màn.

Briefing chỉ rõ shelter/người/khẩu đội cần giữ. Có thời gian trú/sơ tán/chặn pass. Nếu mục tiêu mất và máy bay bị hạ cùng tick, xét fail trước reward. Retry tại checkpoint trước đợt, không ép chơi lại toàn bộ hành trình. Nếu thiết kế chọn “một hit phá hủy” cho mục tiêu cụ thể phải báo từ đầu, không mọi vật trong map đều là điểm fail bí mật.

### 13.4. Finale nhiều đồng đội cùng tham chiến, B-52

Cao trào có nhiều đơn vị, radio, lực lượng ở xa và phản hồi hiệp đồng. [Quân đội nhân dân — Bước ngoặt lịch sử](https://sknc.qdnd.vn/theo-dong-su-kien/buoc-ngoat-lich-su-500814) phân biệt vai bộ đội tên lửa đánh B-52 với lực lượng địa phương/dân quân đánh máy bay bay thấp.

Giữ ý tưởng B-52 ở cuối màn bằng kíp tên lửa/phòng không phù hợp cùng hỗ trợ cảnh giới/tiếp tế/bảo vệ. Có thể chuyển người chơi sang vai trắc thủ trong phân đoạn riêng, hoặc chứng kiến kết quả hiệp đồng sau khi hoàn tất nhiệm vụ bảo vệ. Không mô tả súng trường hay góc ngắm bắn vịt của dân quân tự hạ B-52 tầm cao; B-52 không dùng quỹ đạo lượn cận cảnh của nhóm bay thấp. Cảnh kết là gameplay có giải thích, không gán chiến công cụ thể cho người/đơn vị khi thiếu nguồn.

## 14. Chỉ huy mini — bày binh và đánh chiếm

Điều khiển 2–3 tổ, chọn vùng giữ/tiếp cận, yểm trợ, nghi binh/phục kích rồi chiếm mục tiêu. Đổi về direct mode chỉ actor được phép, không reset tổ khác. Chỉ huy click đặt lệnh không khai hỏa và không pause world.

FOLLOW/HOLD/ADVANCE/AMBUSH/RETREAT có command ID/ACK, waypoint/phase và lý do từ chối. HOLD không tự đuổi địch khỏi vùng; AMBUSH đợi điều kiện/hiệu lệnh; RETREAT ưu tiên đường rút. Lệnh thay thế có kiểm soát, không sinh vô hạn path job. Fog áp dụng bản đồ chỉ huy; tổ có đạn/wounds/suppression và cứu/chia đồ hữu hạn. Thắng theo giữ/chiếm và minimumSurvivors, không chỉ kill count. Đây là cơ chế game, không hướng dẫn chiến thuật thực địa.

## 15. Sau chiến dịch: lịch sử và trích dẫn

Mỗi màn có debrief gameplay và thẻ lịch sử: thời gian/địa điểm, sự kiện xác minh, vai lực lượng, nguồn trực tiếp, ảnh đủ quyền dùng và phần game sáng tác. Cuối chiến dịch lớn có tổng kết nối các chặng. Có thể đọc lại tư liệu/nhật ký ở hub; fail không được receipt thưởng hoàn thành.

Trích đoạn phải ngắn, kiểm chứng nguyên văn, ghi tác giả/người nói và nguồn. Chưa có câu xác thực thì dùng **tóm lược biên tập**, không đặt ngoặc kép/gán danh nhân. Các câu thoại mẫu không là trích dẫn thật. Lượm ghi tác phẩm/tác giả và cảm hứng văn học; mỗi thời kỳ có nhân vật/trang phục/khí tài đúng hồ sơ.

Thêm sự kiện lịch sử theo niên biểu khi có nguồn và vòng chơi có ý nghĩa, không chỉ thêm ngày/tên trận. Hồ sơ Điện Biên, Củ Chi, 30/4/1975 là nền; 12/1972 cần catalog riêng. Các màn mới chưa chốt ngày/đơn vị khi chưa thẩm định.

Debrief ghi quân sống, đạn/băng/medkit/fuel dùng/còn, bị phát hiện, tuyến, mục tiêu và quyết định thoại; receipt thưởng đúng một lần. Người chơi hiểu mình vừa trải qua gì, không chỉ thấy chữ chiến thắng.

## 16. Kiến trúc, contract và checkpoint

Giữ modular backend hiện tại, không đồng thời tách microservices. Trích dần Mission, Dialogue, Wound, Inventory/Loot, Perception/Alert, SquadCommand, WorldChunk/AOI, Interaction, Aircraft và ViewerSnapshot; tái dùng mechanics P0, không nhét tất cả vào VietnamGame.

Snapshot versioned cần mission/segment/checkpoint, chunk references, actor transform/pose/weapon, bleeding/stamina, companion/order, visible contacts, prompt, inventory/loot được phép thấy, journal/dialogue, protected objectives và aircraft/bomb theo capability, lastCommandSeq. Không public dữ liệu địch/loot/plot chưa được biết.

Command BANDAGE, LOOT, THROW_GRENADE, PLACE_TRAP, TALK/CHOICE, MOUNT, ORDER, INTERACT có seq/ID/validation/ACK/reason. Held fire/run/aim là input thay thế được, không replay loạt cũ khi reconnect. Session quyết định người/actor được điều khiển; không tin itemCount/playerId client khai.

Checkpoint giữ RNG/seed/profile/version, HP/wounds/stamina/ammo/chamber/reload/fuel, loot/trap, nhóm/alert, win/fail, dialogue flags, hàng mang, shelter HP và receipts. Save giữa action/retry không trừ hai lần. Restart backend phải resume hoặc từ chối rõ, không tự tạo đầy đồ. Đây là công việc mới, hiện simulation vẫn ở RAM.

Giữ runtime ID 1/2/3, thêm ID ổn định cho màn mới; displayOrder tách riêng. Tiến trình mới dùng tập completed/unlocked/prerequisites có version; migration idempotent giữ entitlement cũ. unlocked=3 không suy ra hoàn thành màn 3. Room giữ rules version được cấp, không đổi nghĩa chapter giữa trận.

## 17. Thứ tự triển khai mới

| Đợt | Đầu ra review được | Điều kiện kết thúc |
| --- | --- | --- |
| R0 | Một đoạn làng/hào có người/camera chéo/asset/ánh sáng, chunk và bắn không aim line | Duyệt ảnh/video và chơi thử desktop/mobile; đo renderer/preload, chưa nhân ra mọi map |
| R1 | Shift, cover, bleeding/băng/medkit, loot/kho, báo động tổ, đồng đội theo/giữ chỗ | 10–15 phút có hai tuyến và fallback, không farm/softlock |
| R2 | Một hành trình hoàn chỉnh Điện Biên hoặc làng, thoại dẫn, 5–7 phân đoạn, checkpoint/debrief | Thời lượng/cảm giác đạt khi người thật chơi, không map trống |
| R3 | Lượm, làng, phục kích rừng, Củ Chi phức tạp làm lần lượt | Từng màn riêng đủ route/resource/nguồn, không chỉ tạo tên/nút |
| R4 | Mini chỉ huy, hành trình kíp xe và màn vận tải Trường Sơn riêng | Lệnh/giữ quân/đội hình có ý nghĩa; Dinh đúng vai; giao lương thực và checkpoint bảo toàn hàng |
| R5 | Phòng không third-person/FPS, pass/bom/tiếp tế, finale B-52 hiệp đồng | Hai camera cùng state, fail rõ/công bằng, đúng vai lực lượng |
| R6 | Art/audio/lịch sử/cân bằng/mobile/performance | Nghiệm thu mỗi màn và hồi quy game cũ |

R0–R6 thay thứ tự sản phẩm P1–P3 sau góp ý, không xóa việc chưa đóng P0. Chưa ước lượng ngày hoàn tất trước prototype renderer/asset.

## 18. Đối chiếu đầy đủ từng góp ý mới

| Mã | Góp ý | Nghiệm thu / mục spec |
| --- | --- | --- |
| FB-01 | Địch ít | Nhiều encounter/tổ, mật độ/budget/tiếp viện hữu hạn (§3/6) |
| FB-02 | Trải nghiệm ngắn | Đạt thời lượng có quyết định, trừ AFK/chờ giả (§3/10) |
| FB-03 | Map nhỏ | Nhiều khu, hai tuyến và đường rút; không đất trống (§3/8) |
| FB-04 | Nhân vật ngộ nghĩnh/khó nhìn | Tỷ lệ/pose/animation mới, duyệt zoom chuẩn/mobile (§4/9) |
| FB-05 | Bleeding | Trúng đòn tạo state/FX, HP server, không reset (§5) |
| FB-06 | Băng gạc/hồi máu | Cầm máu khác hồi HP, consume/reserve đúng (§5) |
| FB-07 | Đồ rớt địch/map | Container ID, loot/kho hữu hạn, không reroll (§5) |
| FB-08 | 1–2 đi theo, quân khác xa | Hai loại nhóm có vai/vùng/state (§6.3) |
| FB-09 | Hội thoại nhân vật | Named speaker, phản ứng trạng thái, nhật ký (§7) |
| FB-10 | Thoại để biết vượt màn | Suy ra tuyến/NPC/thời điểm, fallback (§7) |
| FB-11 | Map sáng/thiếu thời chiến | Palette/props/audio theo bối cảnh vẫn dễ đọc (§9) |
| FB-12 | Asset tả thực | Backlog, manifest/license/scale/LOD, chưa tải (§9) |
| FB-13 | Đi tới đâu render tới đó | Chunk/culling/AOI, RAM ổn, không lộ địch (§8) |
| FB-14 | Đạn chậm | Vận tốc thử mới và swept collision (§4.2) |
| FB-15 | Ta/địch ẩn nấp | Cover/LOS hai phía, không nhìn xuyên (§6) |
| FB-16 | Hành trình thực tế | Thương tích/vật tư/thông tin xuyên phân đoạn (§3/16) |
| FB-17 | Đạn hạn chế, lấy địch/thùng | Budget bắt buộc, kho/loot/fallback (§5) |
| FB-18 | Phòng không ngôi ba/ngôi nhất | Switch mount đúng chỗ, không reset/pause (§13.1) |
| FB-19 | Bay nhanh/lượn/bắn vịt | Nhiều pass, aim có thời gian bay, không auto-hit (§13.2) |
| FB-20 | Bom trúng thì fail | Mục tiêu bắt buộc, policy rõ, fail trước reward (§13.3) |
| FB-21 | Đồng đội/B-52 cao trào | Hiệp đồng và vai tên lửa phù hợp (§13.4) |
| FB-22 | Thêm lịch sử | Nguồn/niên biểu/khí tài, không gán bừa (§10/15) |
| FB-23 | Giữ Shift chạy | Stamina/tốc độ/tiếng động server/mobile (§4.3) |
| FB-24 | Góc quay chéo | Thấy chiều sâu, không overhead cũ (§4.1) |
| FB-25 | Lựu đạn/ẩn nấp | Action/cover/impact, không preview (§6.2) |
| FB-26 | Một địch kéo nhiều địch | Alert có độ trễ/phạm vi/vị trí cuối (§6.1) |
| FB-27 | Vào nhà dân | Nội thất/cửa sau, không reset truy đuổi (§12.2) |
| FB-28 | Hóa thân Lượm | Màn giao thư riêng, chuyển thể ghi rõ (§12.1) |
| FB-29 | Đồ vật gây chú ý | Sound event/AI investigation/layer, không spam (§6.1) |
| FB-30 | Bẫy rừng/phục kích | Màn riêng, item/state/trigger/checkpoint (§6.2/10) |
| FB-31 | Ý tưởng là màn riêng | 9 design ID, briefing/mission/debrief riêng (§10) |
| FB-32 | Không kẻ đường tới chuột | Không aim line rifle/grenade/FPS/map (§4.2) |
| FB-33 | Lịch sử sau chiến dịch | Trích đúng hoặc tóm lược, nguồn/đọc lại (§15) |
| FB-34 | Chỉ huy bày binh/chiếm | Tổ/lệnh ACK/fog/giữ quân (§14) |
| FB-35 | Hầm chật/phức tạp/nhiều đường | Nhánh/tư thế/lối thoát/ánh âm, không kẹt (§11.2) |
| FB-36 | Xe không kính dọc Trường Sơn, lái chở lương thực | Màn vận tải VN-09 riêng: đoàn xe/đường khó/thoại, bảo toàn xe-người-hàng, giao nhận có receipt (§20) |

### Hồi quy và playtest

Giữ W01–W12, A01–A08, M01–M11, C01–C03, F01–F06 ở spec P0. Bổ sung test: bandage/loot ngắt-retry bảo toàn đồ; bleed qua reconnect/checkpoint; stamina độc lập FPS; đạn nhanh không xuyên; grenade/trap ID không damage lặp; alert không toàn tri; dialogue không softlock; camera mount không reset; culling không xóa AI/sự kiện; nhà/hầm không lộ dữ liệu; bom/fail/reward có precedence; migration giữ chapter/entitlement; cargo manifest/transfer/loss/delivery không nhân bản hoặc trả thưởng lại; convoy không teleport khi đổi chunk.

Playtest desktop/mobile mỗi màn, ghi build/profile, route, thời lượng, encounter, bị phát hiện, đạn/băng/medkit/fuel, quân sống, retry, thoại khó hiểu và mất phương hướng. Không dùng test xanh hoặc ảnh bản cũ để tuyên bố cảm giác/renderer mới đã đạt.

## 19. Nguồn và quyết định cần làm tiếp

- [Bảo tàng Tố Hữu](https://tohuu.org/): tác phẩm/cảm hứng Lượm; chưa đủ để khẳng định tiểu sử nguyên mẫu/tuyến game.
- [Quân đội nhân dân — Bước ngoặt lịch sử](https://sknc.qdnd.vn/theo-dong-su-kien/buoc-ngoat-lich-su-500814): vai tên lửa và lực lượng chống bay thấp.
- [Quân đội nhân dân — Tiết kiệm tên lửa SAM-2 để diệt B-52](https://www.qdnd.vn/quoc-phong-an-ninh/nghe-thuat-quan-su-vn/khach-den-con-nhieu-chu-y-tiet-kiem-gao-680795): nguồn bối cảnh, không sao chép quy trình tác chiến thành hướng dẫn.
- [Poly Haven](https://polyhaven.com/license), [ambientCG](https://docs.ambientcg.com/license/), [Kenney](https://kenney.nl/support): nguồn tuyển asset; quyền API có thể khác quyền dùng file.
- Nguồn catalog Điện Biên/Củ Chi/Dinh tiếp tục là đầu vào thẩm định trước khi phát hành cảnh/quote mới. Chưa chốt lời trích danh nhân trong đợt này.

Tiếp theo cần thử renderer và asset pipeline, chọn pack cụ thể đúng bối cảnh, đo thời lượng/mật độ, biên tập màn Lượm/finale B-52, định nghĩa mission và migration/save. Đây là công việc triển khai sau docs, không phải tính năng đã có.


## 20. Xe không kính — Dọc Trường Sơn

**Design ID:** VN-09, runtime ID mới chưa cấp. **Vai:** chiến sĩ lái xe vận tải, nhiệm vụ chính chở lương thực tới điểm tiếp nhận. **Trạng thái:** thiết kế mới, chưa có gameplay/asset/ảnh capture. Đây là màn riêng, không đổi xe tăng thành xe tải bằng skin.

### 20.1. Bối cảnh và tinh thần

Lấy cảm hứng hình tượng người lính lái xe trong *Bài thơ về tiểu đội xe không kính* của Phạm Tiến Duật, kết hợp bối cảnh vận tải Trường Sơn. Theo [Bảo tàng Lịch sử quốc gia](https://baotanglichsu.vn/VI/Articles/3096/18043/djuong-truong-son-con-djuong-noi-hau-phuong-va-tien-tuyen.html), tuyến đường có vai trò vận chuyển người và nhu yếu phẩm giữa hậu phương và tiền tuyến. [Báo Quân đội nhân dân](https://www.qdnd.vn/van-hoa/doi-song/bai-tho-ve-tieu-doi-xe-khong-kinh-410246) giới thiệu tác phẩm và tinh thần người lính vận tải.

Tuyến, người lái, đối thoại và sự cố là sáng tác gameplay; chưa gắn một chuyến đi, binh trạm, model xe hoặc nguyên mẫu cụ thể khi chưa thẩm định. Không chép dài bài thơ thành thoại. Xe không kính phải thấy rõ cabin hở, gió/bụi/mưa và tương tác đồng đội, không chỉ thiếu một texture cửa sổ. Trọng tâm là đưa được hàng tới nơi, sự bền bỉ và tình đồng đội, không phải thi tốc độ hay đua bắn trên đường.

### 20.2. Chuỗi hành trình 6–8 phân đoạn

| Chặng | Việc người chơi làm | Quyết định / thông tin cần dùng |
| --- | --- | --- |
| 1. Nhận chuyến | Gặp cán bộ giao hàng, kiểm đếm bao lương thực, nhận manifest/điểm giao | Nghe điều kiện bảo toàn hàng, điểm tập kết và nguồn tiếp nhiên liệu |
| 2. Chuẩn bị xe | Kiểm tra nhiên liệu, tình trạng xe, vật tư sửa, bạt/ràng hàng theo action game | Chọn phần đồ mang theo trong giới hạn; không tự đầy mọi thứ khi xuất bến |
| 3. Vào tuyến | Lái theo xe dẫn, nghe đồng đội chỉ mốc đường, qua vùng rừng/dốc | Giữ đoàn, đọc biển/dấu/thoại; không có đường sáng dẫn thẳng trên mặt đất |
| 4. Đường khó | Qua đoạn lầy, khúc cua, mặt đường hỏng hoặc ngầm mô phỏng | Chậm mà giữ hàng, đổi tuyến hoặc chờ hỗ trợ theo mission; không mô phỏng kỹ thuật lái thực địa |
| 5. Dừng/nghỉ | Tới điểm trú/nghỉ, sửa xe, tiếp liệu, gặp tổ giữ tuyến/công binh | Hỏi tin chặng tới, kiểm tra kiện hỏng; có hội thoại đời thường xen căng thẳng |
| 6. Biến cố | Có cảnh báo bom hoặc đường bị chặn, một xe bạn gặp sự cố | Dừng ở điểm kịch bản cho phép, hỗ trợ/chuyển hàng nếu được, chọn tuyến dự phòng |
| 7. Chặng cuối | Tập hợp đoàn, xử lý mưa/bụi/giảm tầm nhìn, tới điểm tiếp nhận | Tài nguyên và hàng đã mất vẫn được giữ xuyên chặng; không reset cuối đoạn |
| 8. Bàn giao | Dừng đúng vị trí, xuống xe, giao/kiểm đếm với NPC | Xác nhận đủ hàng, xe/người theo mission rồi mới nhận kết quả và hồ sơ lịch sử |

Độ dài thử nghiệm 25–40 phút có đoạn yên, thoại và sự kiện có ý nghĩa; không tăng độ dài bằng giữ ga trong rừng trống. Có đường chính và đường vòng đạt được với budget nhiên liệu/hàng, checkpoint sau chặng an toàn.

### 20.3. Lái xe, camera và không khí

Camera thứ ba chéo phía sau xe, cho nhìn chiều sâu mặt đường/đoàn xe; tự điều chỉnh vừa phải khi cua. Cabin không kính, hàng và tình trạng thùng đọc được. W/S ga-phanh/lùi, A/D lái, Space phanh tay khi cấu hình cho phép; hiển thị input theo mode. Khi xuống xe dùng input bộ binh, Shift chạy; không mặc định Shift biến xe tải thành xe tăng tốc vô hạn.

Xe tải có gia tốc/phanh/bán kính cua, quán tính và tải hàng; va chạm/tốc độ xấu có thể làm giảm tình trạng xe và độ nguyên vẹn hàng. Mức phạt có cue, không mất hàng vô cớ ở mọi ổ gà. Lái khác tank quay tại chỗ, không mặc định gắn pháo hoặc yêu cầu bắn địch khi đang chở hàng.

Bụi, gió/mưa vào cabin, đường rừng tối theo giờ, vệt bánh và ánh đèn hạn chế tạo không khí; có tùy chọn giảm rung/FX, không che toàn màn hoặc chỉ cho nghe cue mà không có subtitle. Giữ yêu cầu streaming: preload xa hơn theo tốc độ xe và đường cong; không lộ địch/biến cố bí mật qua chunk metadata.

### 20.4. Hàng hóa, đoàn xe và vật tư hữu hạn

Manifest server gồm cargo ID, loại, số lượng, tình trạng, xe đang giữ và điểm nhận. Prototype có thể dùng 12 bao lương thực, yêu cầu giao tối thiểu 8 bao nguyên vẹn; đây chỉ là số cân bằng game cần thử, không trọng tải xe thật. HUD ghi hàng còn/giao tối thiểu, độ nguyên vẹn xe, nhiên liệu và trạng thái đoàn. Xếp/giao/chuyển/thu hồi hàng đều có command ID/ACK, không tự giao chỉ vì xe đi ngang marker.

Có 1 người đồng hành trong cabin hoặc hỗ trợ ở các điểm dừng; 1–2 xe bạn đi theo hoặc dẫn đường; lực lượng giữ tuyến/tiếp ứng ở xa thuộc các nhóm riêng. Xe AI giữ khoảng cách, chờ ở nút hẹp/điểm tập kết và có cách xử lý mắc đường; không đâm xuyên nhau hoặc teleport theo người chơi. Giao tiếp qua thoại/radio cung cấp mốc đường và thông tin chuyến đi. Không cho quân/hàng biến mất khi ra ngoài camera.

Xăng, kit sửa và vật tư cứu thương hữu hạn ở trạm định trước. Nếu xe bạn hỏng, game có thể cho chuyển hàng sang xe còn chỗ tại điểm an toàn, không sao chép hàng hoặc reset hư hại. Mọi hàng chuyển đi giảm đúng ở nguồn và tăng đúng ở đích. Tài nguyên không tự đầy khi đổi xe, reconnect hoặc qua checkpoint.

### 20.5. Thắng, thất bại và lưu tiến trình

Thắng khi bàn giao đủ lượng/tình trạng hàng tối thiểu cho đúng NPC và đáp ứng yêu cầu xe/người của mission. Thất bại nếu người lái chết không còn cứu theo policy, xe/hàng bị phá tới mức không còn phương án giao tối thiểu, hoặc mất mục tiêu bắt buộc đã báo trước. Một bao rơi hoặc xe bạn tạm dừng không mặc định fail ngay. Bom trượt/đường hỏng là biến cố có cue và phương án, không fail ngẫu nhiên không thể phản ứng.

Checkpoint giữ đoàn/xe/vị trí, manifest/loss/transfer/delivery, fuel/hư hại/kit, wounds, dialogue, trạng thái đường và biến cố, RNG và receipt bàn giao. Load lại không hồi kiện mất, nhận cùng hàng hai lần hoặc thưởng lại. Nếu không thể giao tối thiểu, UI giải thích lý do và cho retry checkpoint phù hợp thay vì softlock hết nhiên liệu giữa tuyến.

Sau màn: thống kê lương thực giao được/mất, người và xe về đích, nhiên liệu/sửa đã dùng, quyết định hỗ trợ đoàn; thẻ lịch sử Trường Sơn và thông tin tác phẩm/tác giả có nguồn. Không gán lời thoại sáng tác thành câu thơ hoặc lời người thật.

### 20.6. Nghiệm thu riêng VN-09

- Lái xe tải trực tiếp được trên ít nhất hai tuyến; camera/đường đọc được ở desktop/mobile; chunk preload không khựng khi xe đạt tốc độ thiết kế.
- Giao thiếu/đúng/sai nơi cho kết quả rõ; va chạm/tổn thất có cue; không bắt tiêu diệt địch để thắng chuyến lương thực.
- Manifest bảo toàn qua chuyển xe, cargo rơi/thu hồi, retry/reconnect/checkpoint; receipt giao chỉ áp dụng một lần.
- Đoàn xe không đâm xuyên/teleport, xử lý đường hẹp/tắc và chờ tập kết có thể tiếp tục nhiệm vụ.
- Thử nhánh thiếu nhiên liệu, xe bạn hỏng, đường chính bị chặn, còn sát ngưỡng hàng và retry sau bàn giao.
- Chốt hồ sơ nguồn/model xe và art không kính trước khi công bố là phục dựng lịch sử; ảnh gameplay chỉ chụp sau khi màn tồn tại.
