package com.brim.game.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.HashMap;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameSettings {
    private Map<String, Object> customSettings;
    private int maxPlayers;
    private int timeLimitSeconds;
    private boolean allowSpectators;

    public static GameSettings defaultFor(GameType gameType) {
        GameSettings settings = new GameSettings();
        settings.maxPlayers = switch (gameType) {
            case SNAKE -> 4;
            case TANK, BOMBERMAN -> 4;
            case CHESS, CHINESE_CHESS, CONNECT_FOUR, CARO, OTHELLO, BATTLESHIP, WARLORDS_DUEL -> 2;
            case VIETNAM_CAMPAIGN, MATCH_THREE, WARLORDS_CAMPAIGN -> 1;
        };
        settings.timeLimitSeconds = 300;
        settings.allowSpectators = true;
        settings.customSettings = new HashMap<>();
        settings.customSettings.put("difficulty", "MEDIUM");
        return settings;
    }
}
