package com.brim.game.game.warlords;

import java.util.List;

public final class WarlordsCampaign {
    private WarlordsCampaign() {}
    public record Chapter(int id,String title,String warlord,String location,String story,String victory,
                          String encounter,int enemyHp,int enemyAttack,int gold,int experience,int x,int y) {}
    // The route and battles are fictional. The names/strongholds are documented in docs/WARLORDS.md.
    public static final List<Chapter> CHAPTERS=List.of(
        new Chapter(1,"Kết nghĩa nơi cửa biển","Trần Lãm","Bố Hải Khẩu",
            "Từ Hoa Lư, đoàn quân tìm đến Bố Hải Khẩu. Hãy vượt qua một cuộc thao luyện để gây dựng lòng tin với Trần Lãm.",
            "Tình bằng hữu mở ra một con đường mới. Một đội quân mạnh bắt đầu từ sự đồng lòng.","Thao luyện",116,3,35,42,72,86),
        new Chapter(2,"Cờ nghĩa Đằng Châu","Phạm Bạch Hổ","Đằng Châu",
            "Đoàn quân tiến đến Đằng Châu. Trong cuộc giao đấu giả định, hãy kết hợp khiên và kiếm để chứng tỏ bản lĩnh.",
            "Một lời hẹn giữ yên dân trở thành nhịp cầu nối những ngọn cờ.","Thử tài",132,3,45,54,58,72),
        new Chapter(3,"Dấu ấn nhà Ngô","Ngô Xương Xí","Bình Kiều",
            "Ở Bình Kiều, tiếng vọng nhà Ngô vẫn còn. Cuộc thử sức này đặt lòng kiên nhẫn lên trên thế công vội vàng.",
            "Những khác biệt được đặt xuống để cùng nhìn về một cõi yên bình.","Thử tài",148,3,55,66,30,92),
        new Chapter(4,"Bóng thành Đường Lâm","Ngô Nhật Khánh","Đường Lâm",
            "Đường Lâm hiện ra giữa những con đường đồi. Tích năng lượng trước khi tung đòn quyết định.",
            "Đường phía trước đã mở. Đoàn quân tiếp tục hành trình dưới ngọn cờ Hoa Lư.","Giao đấu",164,4,65,78,22,52),
        new Chapter(5,"Đường lên Hồi Hồ","Kiều Thuận","Hồi Hồ",
            "Những lối quanh co dẫn tới Hồi Hồ. Đừng quên hồi quân khi lượng máu xuống thấp.",
            "Sau cuộc thử thách, đoàn quân lại có thêm kinh nghiệm giữ vững đội hình.","Giao đấu",180,4,75,90,12,18),
        new Chapter(6,"Ngọn cờ Phong Châu","Kiều Công Hãn","Phong Châu",
            "Trên vùng Phong Châu, mỗi nước đi đều có thể đổi thế trận. Một chuỗi kiếm liên hoàn sẽ tạo bước ngoặt.",
            "Những ngọn cờ xa dần. Lòng quyết tâm thống nhất vẫn hướng đoàn quân tiến bước.","Giao đấu",196,4,85,102,30,30),
        new Chapter(7,"Qua miền Tam Đái","Nguyễn Khoan","Tam Đái",
            "Tam Đái là thử thách của sức bền. Khiên và năng lượng giúp bạn chuẩn bị cho những đợt tiến công dài.",
            "Kinh nghiệm từ những cuộc chạm trán đã tôi luyện đội quân Hoa Lư.","Giao đấu",212,5,95,114,43,43),
        new Chapter(8,"Tiếng trống Tiên Du","Nguyễn Thủ Tiệp","Tiên Du",
            "Tiếng trống vang trên đường Tiên Du. Hãy giữ thế chủ động bằng những kỹ năng đúng thời điểm.",
            "Đoàn quân vượt thêm một cửa ải và sửa soạn cho những thử thách cuối.","Giao đấu",228,5,105,126,73,24),
        new Chapter(9,"Bến nước Siêu Loại","Lý Khuê","Siêu Loại",
            "Bên bến Siêu Loại, cuộc giao đấu đòi hỏi cân bằng giữa tiến công và giữ quân.",
            "Những bến sông dần nối lại đường đi. Hành trình trở về Hoa Lư đang gần hơn.","Giao đấu",244,5,115,138,85,43),
        new Chapter(10,"Ngược dòng Tế Giang","Lã Đường","Tế Giang",
            "Đường qua Tế Giang không dễ vượt. Dùng tài nguyên đã tích lũy để nâng cấp trước trận.",
            "Một cửa ải nữa ở lại phía sau. Đội quân vẫn giữ mục tiêu chấm dứt phân tranh.","Giao đấu",260,6,125,150,75,59),
        new Chapter(11,"Thế trận Phù Liệt","Nguyễn Siêu","Tây Phù Liệt",
            "Thế trận Phù Liệt là một thử thách lớn. Hãy dự trữ năng lượng và tận dụng các chuỗi liên hoàn.",
            "Qua thế trận khó, ngọn cờ vẫn vững. Chỉ còn một chương nữa trên lộ trình này.","Giao đấu",276,6,135,162,48,59),
        new Chapter(12,"Hợp cờ sơn hà","Đỗ Cảnh Thạc","Đỗ Động Giang",
            "Chương cuối của chiến dịch giả định đưa bạn tới Đỗ Động Giang. Vận dụng tất cả kinh nghiệm đã có.",
            "Chiến dịch hoàn tất. Năm 968, Đinh Bộ Lĩnh lên ngôi, đặt quốc hiệu Đại Cồ Việt. Một kỷ nguyên mới mở ra từ Hoa Lư.","Quyết chiến",292,6,145,174,35,75)
    );
    public static Chapter chapter(int id) {
        if(id<1||id>CHAPTERS.size()) throw new IllegalArgumentException("Invalid chapter");
        return CHAPTERS.get(id-1);
    }
}
