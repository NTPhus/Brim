package com.brim.game.service;

import com.brim.game.game.chess.ChessGame;
import com.brim.game.game.chinesechess.ChineseChessGame;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.brim.game.kafka.GameEventProducer;
import com.brim.game.model.dto.GameMessage;
import com.brim.game.model.dto.GameRoom;
import com.brim.game.model.dto.GameType;
import com.brim.game.model.dto.MessageType;
import com.brim.game.model.dto.RoomStatus;
import com.brim.game.model.dto.GameScoreEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import com.brim.game.runtime.BotExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class TurnBasedGameService {

    private final RoomService roomService;
    private final GameEventProducer eventProducer;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final BotExecutor botExecutor;
    private final com.brim.game.runtime.TurnBasedGameRegistry gameRegistry;
    private final java.util.Set<String> pendingBots = ConcurrentHashMap.newKeySet();

    private final Map<String, TurnBasedGame> activeGames = new ConcurrentHashMap<>();

    public void processMove(String roomId, String playerId, Object move) {
        TurnBasedGame game = activeGames.get(roomId);
        if (game == null) {
            log.warn("Game not found for room: {}", roomId);
            return;
        }

        Object typedMove = move;
        try {
            typedMove = objectMapper.convertValue(move, game.moveType());
        } catch (IllegalArgumentException conversionError) {
            log.warn("Invalid move payload for room {}: {}", roomId, move);
            eventProducer.sendGameEvent(roomId, playerId, MessageType.ERROR, "Invalid move payload");
            return;
        }

        boolean valid = game.makeMove(playerId, typedMove);
        if (valid) {
            // Broadcast state directly via WebSocket so frontend receives it immediately
            broadcastGameState(roomId, game);

            if (game.isGameOver()) {
                endGame(roomId, game.getWinner());
            } else {
                triggerBotTurnIfNeeded(roomId, game);
            }
        } else {
            eventProducer.sendGameEvent(roomId, playerId, MessageType.ERROR, "Invalid move");
        }
    }

    public TurnBasedGame getGame(String roomId) {
        return activeGames.get(roomId);
    }

    public void handleTurnStart(String roomId, String playerId) {
        TurnBasedGame game = activeGames.get(roomId);
        if (game != null) {
            broadcastGameState(roomId, game);
        }
    }

    public void handleGameEvent(String roomId, MessageType type, Object payload) {
        TurnBasedGame game = activeGames.get(roomId);
        if (game != null) {
            game.handleEvent(type, payload);
        }
    }

    public void initializeGame(GameRoom room) {
        String difficulty = "MEDIUM";
        if (room.getSettings() != null && room.getSettings().getCustomSettings() != null) {
            difficulty = String.valueOf(room.getSettings().getCustomSettings().getOrDefault("difficulty", "MEDIUM"));
        }
        TurnBasedGame game = gameRegistry.create(room.getGameType(), room.getId(), difficulty);

        // Assign players
        var players = room.getPlayers();
        if (players.size() >= 2) {
            game.setPlayer1(players.get(0).getId());
            game.setPlayer2(players.get(1).getId());
        }

        activeGames.put(room.getId(), game);
        room.setStatus(RoomStatus.IN_PROGRESS);
        roomService.saveRoom(room);

        // Broadcast initial game state directly via WebSocket
        broadcastGameState(room.getId(), game);

        triggerBotTurnIfNeeded(room.getId(), game);
    }

    /**
     * Broadcast game state directly via WebSocket to all subscribers.
     * This ensures frontend receives state updates immediately (previously
     * state was sent to Kafka topic game.state with no consumer).
     */
    private void broadcastGameState(String roomId, TurnBasedGame game) {
        Object state = game.getState();
        messagingTemplate.convertAndSend("/topic/game/" + roomId + "/state", state);
        log.debug("Broadcast game state for room {}", roomId);
    }

    private void triggerBotTurnIfNeeded(String roomId, TurnBasedGame game) {
        String current = game.getCurrentPlayerId();
        if (current == null || !current.startsWith("bot-") || game.isGameOver() || !pendingBots.add(roomId)) return;
        boolean accepted = botExecutor.submit(() -> {
            try {
                if (activeGames.get(roomId) != game || !current.equals(game.getCurrentPlayerId()) || game.isGameOver()) return;
                Object move = game.generateBotMove();
                if (move != null && activeGames.get(roomId) == game
                        && current.equals(game.getCurrentPlayerId()) && !game.isGameOver()) processMove(roomId, current, move);
            } catch (Exception error) {
                log.error("Bot failed in room {}", roomId, error);
            } finally { pendingBots.remove(roomId); }
        });
        if (!accepted) pendingBots.remove(roomId);
    }

    // Bounded worker queue: retry busy rooms rather than enqueue unlimited searches.
    @Scheduled(fixedDelay = 500)
    public void resumeWaitingBots() {
        activeGames.forEach((roomId, game) -> {
            if (roomService.getRoom(roomId) == null) activeGames.remove(roomId, game);
            else triggerBotTurnIfNeeded(roomId, game);
        });
    }

    private void endGame(String roomId, String winnerId) {
        TurnBasedGame game = activeGames.remove(roomId);
        if (game == null) return;

        var room = roomService.getRoom(roomId);
        if (room != null) {
            room.getPlayers().stream().filter(player -> !player.getId().startsWith("bot-")).forEach(player -> {
                int points = player.getId().equals(winnerId) ? 100 : 10;
                eventProducer.sendLeaderboardScore(roomId,
                        new GameScoreEvent(player.getId(), player.getUsername(), room.getGameType(), points));
            });
            room.setStatus(RoomStatus.FINISHED);
            roomService.saveRoom(room);

            // Broadcast updated room state
            messagingTemplate.convertAndSend("/topic/room/" + roomId + "/state", room);
        }

        // Broadcast GAME_OVER via WebSocket directly (frontend subscribes to /topic/game/{roomId}/over)
        Map<String, Object> gameOverPayload = Map.of("winner", winnerId != null ? winnerId : "", "finalState", game.getState());
        messagingTemplate.convertAndSend("/topic/game/" + roomId + "/over",
                GameMessage.of(MessageType.GAME_OVER, roomId, winnerId, gameOverPayload));

        // Also send final game state
        messagingTemplate.convertAndSend("/topic/game/" + roomId + "/state", game.getState());

        log.info("Game over in room {}. Winner: {}", roomId, winnerId);
    }

    /**
     * Remove the active game for a room (used during cleanup).
     */
    public void removeGame(String roomId) {
        activeGames.remove(roomId);
    }

    public interface TurnBasedGame {
        Class<?> moveType();
        boolean makeMove(String playerId, Object move);
        Object getState();
        String getCurrentPlayerId();
        boolean isGameOver();
        String getWinner();
        void setPlayer1(String playerId);
        void setPlayer2(String playerId);
        void handleEvent(MessageType type, Object payload);
        Object generateBotMove();
    }
}
