package com.brim.game.model.dto;
import java.util.UUID;

/** Stable event identity is retained across Kafka retries and replays. */
public record GameScoreEvent(String eventId, String playerId, String username, GameType gameType,
                             int points, long occurredAt) {
    public GameScoreEvent(String playerId, String username, GameType gameType, int points) {
        this(UUID.randomUUID().toString(), playerId, username, gameType, points, System.currentTimeMillis());
    }
}
