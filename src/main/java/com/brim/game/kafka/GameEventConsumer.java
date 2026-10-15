package com.brim.game.kafka;

import com.brim.game.config.KafkaConfig;
import com.brim.game.model.dto.GameMessage;
import com.brim.game.model.dto.MessageType;
import com.brim.game.service.TurnBasedGameService;
import com.brim.game.service.LeaderboardService;
import com.brim.game.model.dto.GameScoreEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.kafka.support.KafkaHeaders;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameEventConsumer {

    private final TurnBasedGameService turnBasedGameService;
    private final LeaderboardService leaderboardService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaConfig.TOPIC_GAME_MOVE, groupId = "game-platform")
    public void handleGameMove(GameMessage<?> message) {
        log.debug("Received move for room {}: {}", message.getRoomId(), message.getPayload());
        turnBasedGameService.processMove(message.getRoomId(), message.getPlayerId(), message.getPayload());
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_TURN_BASED, groupId = "game-platform")
    public void handleTurnEvent(GameMessage<?> message) {
        log.debug("Received turn event for room {}: {}", message.getRoomId(), message.getType());
        switch (message.getType()) {
            case TURN_START -> turnBasedGameService.handleTurnStart(message.getRoomId(), message.getPlayerId());
            case MAKE_MOVE -> turnBasedGameService.processMove(message.getRoomId(), message.getPlayerId(), message.getPayload());
            default -> log.warn("Unknown turn event type: {}", message.getType());
        }
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_GAME_EVENT, groupId = "game-platform")
    public void handleGameEvent(GameMessage<?> message) {
        log.debug("Received game event for room {}: {}", message.getRoomId(), message.getType());
        turnBasedGameService.handleGameEvent(message.getRoomId(), message.getType(), message.getPayload());
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_LEADERBOARD, groupId = "game-platform-leaderboard")
    public void handleLeaderboardScore(GameMessage<?> message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        GameScoreEvent event = objectMapper.convertValue(message.getPayload(), GameScoreEvent.class);
        // Existing records written before eventId was added still get a stable identity.
        if (event.eventId() == null) event = new GameScoreEvent(topic + ":" + partition + ":" + offset,
                event.playerId(), event.username(), event.gameType(), event.points(), message.getTimestamp());
        leaderboardService.record(event);
    }
}
