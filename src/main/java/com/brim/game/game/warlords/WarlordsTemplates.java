package com.brim.game.game.warlords;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** Load immutable, validated templates once, like an MMO's startup data tables. */
public final class WarlordsTemplates {
    private WarlordsTemplates() {}
    private static <T> List<T> read(String file,Class<T> type) {
        try(var input=WarlordsTemplates.class.getResourceAsStream("/games/warlords/"+file)) {
            if(input==null)throw new IllegalStateException("Missing Warlords template: "+file);
            ObjectMapper json=new ObjectMapper();
            return List.copyOf(json.readValue(input,json.getTypeFactory().constructCollectionType(List.class,type)));
        }catch(java.io.IOException invalid){throw new IllegalStateException("Invalid Warlords template: "+file,invalid);}
    }
    static List<WarlordsCampaign.Chapter> chapters() {
        var chapters=read("chapters.json",WarlordsCampaign.Chapter.class);
        if(chapters.size()!=12)throw new IllegalStateException("Warlords requires 12 campaign chapters.");
        for(int i=0;i<chapters.size();i++) {
            var c=chapters.get(i);
            if(c.id()!=i+1||blank(c.title(),c.warlord(),c.location(),c.story(),c.victory(),c.encounter())
                    ||c.enemyHp()<1||c.enemyHp()>10000||c.enemyAttack()<1||c.enemyAttack()>100
                    ||c.gold()<0||c.gold()>300||c.experience()<0||c.experience()>300||c.x()<0||c.x()>100||c.y()<0||c.y()>100)
                throw new IllegalStateException("Invalid campaign chapter "+(i+1));
        }
        return chapters;
    }
    static List<WarlordsHeroes.Hero> heroes() {
        var heroes=read("heroes.json",WarlordsHeroes.Hero.class);Set<String> ids=new HashSet<>();
        if(heroes.isEmpty()||heroes.size()>32)throw new IllegalStateException("Invalid Warlords roster size.");
        for(var h:heroes) {
            if(blank(h.id(),h.name(),h.title(),h.description(),h.color(),h.emblem())||!ids.add(h.id())||!h.id().matches("[A-Z_]{1,32}")
                    ||!h.color().matches("#[0-9a-fA-F]{6}")||!Set.of("banner","sword","shield","scroll","spear","lotus").contains(h.emblem())
                    ||h.hpBonus()<-100||h.hpBonus()>100||h.attackBonus()<-3||h.attackBonus()>5||h.skillBonus()<0||h.skillBonus()>3
                    ||h.startingEnergy()<0||h.startingEnergy()>100||h.startingShield()<0||h.startingShield()>80)
                throw new IllegalStateException("Invalid hero template: "+h.id());
        }
        if(!ids.contains(WarlordsHeroes.DEFAULT))throw new IllegalStateException("Default hero is missing.");
        return heroes;
    }
    private static boolean blank(String... values){return Arrays.stream(values).anyMatch(value->value==null||value.isBlank());}
}
