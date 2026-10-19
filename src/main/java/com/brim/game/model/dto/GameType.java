package com.brim.game.model.dto;

public enum GameType {
    VIETNAM_CAMPAIGN("vietnam_campaign", "Dấu chân Việt — Chiến dịch", true),
    SNAKE("snake", "Rắn săn mồi", true),
    TANK("tank", "Tank Battle", true),
    CHESS("chess", "Cờ vua", false),
    CHINESE_CHESS("chinese_chess", "Cờ tướng", false),
    CONNECT_FOUR("connect_four", "Connect Four", false),
    CARO("caro", "Caro", false),
    OTHELLO("othello", "Othello", false),
    BATTLESHIP("battleship", "Battleship", false),
    MATCH_THREE("match_three", "Match-3", false),
    BOMBERMAN("bomberman", "Bomberman", true),
    WARLORDS_CAMPAIGN("warlords_campaign", "Sơn Hà — Chiến dịch", false),
    WARLORDS_DUEL("warlords_duel", "Sơn Hà — Đấu tướng", false);

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
    public boolean isSolo() { return this == VIETNAM_CAMPAIGN || this == MATCH_THREE || this == WARLORDS_CAMPAIGN; }
    public int minimumPlayers() { return isSolo() || this == SNAKE || this == TANK ? 1 : 2; }
    public int maximumPlayers() { return isSolo() ? 1 : this == BOMBERMAN ? 4 : realTime ? 16 : 2; }

    public static GameType fromCode(String code) {
        for (GameType type : values()) {
            if (type.code.equalsIgnoreCase(code)) return type;
        }
        throw new IllegalArgumentException("Unknown game type: " + code);
    }
}
