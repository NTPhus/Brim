package com.brim.game.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Player {
    private String id;
    private String username;
    private String sessionId;
    private int score;
    private boolean ready;
    private PlayerState state;

    public enum PlayerState {
        WAITING, PLAYING, SPECTATING, DISCONNECTED, FINISHED
    }
}