package com.brim.game.game.warlords;

import java.util.List;

public final class WarlordsCampaign {
    private WarlordsCampaign() {}
    public record Chapter(int id,String title,String warlord,String location,String story,String victory,
                          String encounter,int enemyHp,int enemyAttack,int gold,int experience,int x,int y) {
        @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY) public int botLevel() {return (id-1)/3+1;}
        @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY) public String botStyle() {
            return switch(botLevel()) {
                case 1 -> "Tập sự · chọn các bộ ghép cơ bản";
                case 2 -> "Chiến thuật · cân bằng kiếm, hồi máu và kỹ năng";
                case 3 -> "Lão luyện · đọc liên hoàn và đề phòng phản công";
                default -> "Bậc thầy · dự tính nước đáp trả, chớp thời cơ kết liễu";
            };
        }
    }
    // The route and battles are fictional. The names/strongholds are documented in docs/WARLORDS.md.
    public static final List<Chapter> CHAPTERS=WarlordsTemplates.chapters();
    public static Chapter chapter(int id) {
        if(id<1||id>CHAPTERS.size()) throw new IllegalArgumentException("Invalid chapter");
        return CHAPTERS.get(id-1);
    }
}
