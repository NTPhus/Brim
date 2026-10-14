package com.brim.game.model.dto;

public enum GameType {
    SNAKE("snake", "Rắn săn mồi", true),
    TANK("tank", "Tank Battle", true),
    CHESS("chess", "Cờ vua", false),
    CHINESE_CHESS("chinese_chess", "Cờ tướng", false);

    private final String code;
    private final String name;
    private final boolean realTime;

    GameType(String code, String name, boolean realTime) {
        this.code = code;
        this.name = name;
        this.realTime = realTime;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isRealTime() { return realTime; }

    public static GameType fromCode(String code) {
        for (GameType type : values()) {
            if (type.code.equalsIgnoreCase(code)) return type;
        }
        throw new IllegalArgumentException("Unknown game type: " + code);
    }
}