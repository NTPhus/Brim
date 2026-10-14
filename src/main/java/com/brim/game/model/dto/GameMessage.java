package com.brim.game.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameMessage<T> {
    private MessageType type;
    private String roomId;
    private String playerId;
    private T payload;
    private long timestamp;

    public static <T> GameMessage<T> of(MessageType type, String roomId, String playerId, T payload) {
        return new GameMessage<>(type, roomId, playerId, payload, System.currentTimeMillis());
    }
}