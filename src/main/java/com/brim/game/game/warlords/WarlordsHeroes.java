package com.brim.game.game.warlords;

import java.util.List;

/** Fictional combat styles; campaign bonuses never apply to PvP. */
public final class WarlordsHeroes {
    private WarlordsHeroes() {}
    public static final String DEFAULT = "DINH_BO_LINH";
    public record Hero(String id,String name,String title,String description,String color,String emblem,
                       int hpBonus,int attackBonus,int skillBonus,int startingEnergy,int startingShield) {}
    public static final List<Hero> ALL=WarlordsTemplates.heroes();
    public static Hero require(String id) {
        return ALL.stream().filter(hero -> hero.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tướng không hợp lệ."));
    }
    public static Hero saved(String id) {
        return ALL.stream().filter(hero -> hero.id().equals(id)).findFirst().orElseGet(()->require(DEFAULT));
    }
}
