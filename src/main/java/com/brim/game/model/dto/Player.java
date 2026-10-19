package com.brim.game.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Player {
    private String id;
    private String username;
    // Accept legacy/incoming values, but never expose or persist a transport session ID.
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String sessionId;
    private int score;
    private boolean ready;
    private PlayerState state;
    /** Preferred player color, encoded as #RRGGBB for the realtime game renderers. */
    private String color;

    public enum PlayerState {
        WAITING, PLAYING, SPECTATING, DISCONNECTED, FINISHED
    }
}
