package com.brim.game.game.warlords;

import java.util.List;

/** Persistent resources; combat takes a snapshot at the start of each encounter. */
public record WarlordsProfile(int gold, int experience, int weapon, int armor, int skill,
                              int unlocked, List<Integer> cleared, int wins, int losses, String heroId) {
    public WarlordsProfile { cleared = List.copyOf(cleared); heroId = WarlordsHeroes.saved(heroId).id(); }
    public WarlordsProfile(int gold,int experience,int weapon,int armor,int skill,int unlocked,List<Integer> cleared,int wins,int losses) {
        this(gold,experience,weapon,armor,skill,unlocked,cleared,wins,losses,WarlordsHeroes.DEFAULT);
    }
    public static WarlordsProfile initial() { return new WarlordsProfile(100,0,0,0,0,1,List.of(),0,0); }
    public int level() { return Math.min(20,1+experience/100); }
    public int maxHp() { return 140+(level()-1)*8+armor*15; }
    public int attack() { return 5+weapon; }
    public static int upgradeCost(String type,int rank) {
        return switch(type) { case "WEAPON" -> 60*(rank+1); case "ARMOR" -> 50*(rank+1); case "SKILL" -> 80*(rank+1); default -> 0; };
    }
    public record Claim(String id,long createdAt,int stage,boolean duel,boolean won,int gold,int experience) {}
    public interface Store {
        WarlordsProfile load(String playerId);
        WarlordsProfile upgrade(String playerId,String type);
        WarlordsProfile claim(String playerId,Claim claim);
        WarlordsProfile selectHero(String playerId,String heroId);
    }
}
