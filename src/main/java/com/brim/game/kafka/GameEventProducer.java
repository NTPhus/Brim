package com.brim.game.kafka;

import com.brim.game.config.KafkaConfig;
import com.brim.game.model.dto.GameMessage;
import com.brim.game.model.dto.MessageType;
import com.brim.game.model.dto.GameScoreEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendGameMove(String roomId, String playerId, Object move) {
        GameMessage<Object> message = GameMessage.of(MessageType.MAKE_MOVE, roomId, playerId, move);
        kafkaTemplate.send(KafkaConfig.TOPIC_GAME_MOVE, roomId, message);
        log.debug("Sent move for room {}: {}", roomId, move);
    }

    public void sendGameState(String roomId, Object state) {
        GameMessage<Object> message = GameMessage.of(MessageType.STATE_UPDATE, roomId, "system", state);
        kafkaTemplate.send(KafkaConfig.TOPIC_GAME_STATE, roomId, message);
    }

    public void sendGameEvent(String roomId, String playerId, MessageType type, Object payload) {
        GameMessage<Object> message = GameMessage.of(type, roomId, playerId, payload);
        kafkaTemplate.send(KafkaConfig.TOPIC_GAME_EVENT, roomId, message);
    }

    public void sendTurnEvent(String roomId, String playerId, Object turnData) {
        GameMessage<Object> message = GameMessage.of(MessageType.TURN_START, roomId, playerId, turnData);
        kafkaTemplate.send(KafkaConfig.TOPIC_TURN_BASED, roomId, message);
    }

    public void sendLeaderboardScore(String roomId, GameScoreEvent score) {
        kafkaTemplate.send(KafkaConfig.TOPIC_LEADERBOARD, score.playerId(),
                GameMessage.of(MessageType.SCORE_UPDATE, roomId, score.playerId(), score));
    }
}
