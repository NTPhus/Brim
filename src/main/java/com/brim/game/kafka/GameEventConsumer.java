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

@Service
@RequiredArgsConstructor
@Slf4j
public class GameEventConsumer {

    private final TurnBasedGameService turnBasedGameService;
    private final LeaderboardService leaderboardService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaConfig.TOPIC_GAME_MOVE, groupId = "game-platform")
    public void handleGameMove(GameMessage<?> message) {
        log.info("Received move for room {}: {}", message.getRoomId(), message.getPayload());
        turnBasedGameService.processMove(message.getRoomId(), message.getPlayerId(), message.getPayload());
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_TURN_BASED, groupId = "game-platform")
    public void handleTurnEvent(GameMessage<?> message) {
        log.info("Received turn event for room {}: {}", message.getRoomId(), message.getType());
        switch (message.getType()) {
            case TURN_START -> turnBasedGameService.handleTurnStart(message.getRoomId(), message.getPlayerId());
            case MAKE_MOVE -> turnBasedGameService.processMove(message.getRoomId(), message.getPlayerId(), message.getPayload());
            default -> log.warn("Unknown turn event type: {}", message.getType());
        }
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_GAME_EVENT, groupId = "game-platform")
    public void handleGameEvent(GameMessage<?> message) {
        log.info("Received game event for room {}: {}", message.getRoomId(), message.getType());
        turnBasedGameService.handleGameEvent(message.getRoomId(), message.getType(), message.getPayload());
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_LEADERBOARD, groupId = "game-platform-leaderboard")
    public void handleLeaderboardScore(GameMessage<?> message) {
        leaderboardService.record(objectMapper.convertValue(message.getPayload(), GameScoreEvent.class));
    }
}
