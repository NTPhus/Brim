package com.brim.game.game.vietnam;

import java.util.List;

/** Historical context is sourced; playable routes and squad assignments are dramatized. */
public final class VietnamCampaign {
    private VietnamCampaign() {}
    public record Chapter(int id, String title, String date, String location, String role, String character,
                          String history, String mission, String source, String sourceTitle, String theme) {}
    public static final List<Chapter> CHAPTERS = List.of(
        new Chapter(1,"Đường hào Điện Biên","1954","Điện Biên Phủ","SOLDIER","Chiến sĩ bộ binh (hư cấu)",
            "Chiến dịch diễn ra từ 13/3 đến 7/5/1954, do Đại tướng Võ Nguyên Giáp chỉ huy. Chiến hào và công sự là đặc trưng của cách đánh chắc, tiến chắc.",
            "Cùng tổ chiến đấu men theo chiến hào, chiếm công sự và đưa quân tới điểm tập kết. Tuyến tiến quân trong màn là mô phỏng.",
            "https://baotanglichsu.vn/vi/Articles/2001/66681/mot-quyet-djinh-mang-tinh-lich-su-cua-djai-tuong-vo-nguyen-giap.html","Bảo tàng Lịch sử quốc gia","HILLS"),
        new Chapter(2,"Mạch ngầm Củ Chi","Bối cảnh 1961–1967","Củ Chi","SOLDIER","Giao liên (hư cấu)",
            "Hệ thống địa đạo được nối và mở rộng trong giai đoạn 1961–1967, có bệnh xá, bếp, kho và giếng ngầm. Đây là bối cảnh địa đạo, không phải tên một trận đánh cụ thể.",
            "Vào địa đạo, lấy túi cứu trợ trong kho rồi chuyển tới bệnh xá. Hệ thống hầm hai lớp được giản lược để chơi.",
            "https://vietnamtourism.vn/index.php/tourism/items/274/10","Cục Du lịch Quốc gia Việt Nam","JUNGLE"),
        new Chapter(3,"Tiến vào Dinh","30/4/1975","Sài Gòn · Dinh Độc Lập","TANK","Kíp xe 390 · chỉ huy Vũ Đăng Toàn",
            "Xe tăng 390 húc đổ cổng chính Dinh Độc Lập. Bùi Quang Thận, chỉ huy xe 843, là người kéo cờ trên nóc Dinh. Màn chơi giữ riêng vai trò của hai xe.",
            "Điều khiển xe 390 tiến theo đại lộ, tiếp cận cổng và vào sân Dinh. Không yêu cầu bắn vào Dinh; địa hình tuyến đường là mô phỏng.",
            "https://dinhdoclap.gov.vn/lich-su-dinh-doc-lap/","Di tích lịch sử Dinh Độc Lập","CITY")
    );
    public static Chapter chapter(int id) { return CHAPTERS.get(Math.max(1, Math.min(CHAPTERS.size(), id)) - 1); }
}
