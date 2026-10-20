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
    private final com.brim.game.runtime.GameRuntime runtime;
    private final java.util.Set<String> pendingBots = ConcurrentHashMap.newKeySet();

    private final Map<String, TurnBasedGame> activeGames = new ConcurrentHashMap<>();
    private final Map<String, TurnBasedGame> finishedGames = new ConcurrentHashMap<>();
    private volatile boolean stopping;

    public void processMove(String roomId, String playerId, Object move) {
        var room = roomService.getRoom(roomId);
        if (room == null) return;
        // A restart, timeout and command for one room form one ordered transaction.
        synchronized (room) { processRoomMove(roomId, playerId, move); }
    }

    private void processRoomMove(String roomId, String playerId, Object move) {
        if(stopping){sendMoveError(roomId,playerId,"Server đang bảo trì. Tiến trình đã lưu có thể tiếp tục khi server mở lại.");return;}
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
            sendMoveError(roomId, playerId, "Invalid move payload");
            return;
        }

        boolean valid;
        try { valid = game.makeMove(playerId, typedMove); }
        catch (IllegalArgumentException invalid) {
            sendMoveError(roomId, playerId, invalid.getMessage());
            return;
        }
        catch (RuntimeException unavailable) {
            log.warn("Move could not be committed for room {}",roomId,unavailable);
            sendMoveError(roomId,playerId,"Chưa lưu được lượt chơi. Nước đi chưa được xác nhận; hãy thử lại khi kết nối ổn định.");
            broadcastGameState(roomId,game);
            return;
        }
        if (valid) {
            if (game.realtime()) return; // The shared tick publishes authoritative snapshots.
            var room = roomService.getRoom(roomId);
            if (room != null && !game.checkpoint().isEmpty()) {
                room.getSettings().getCustomSettings().putAll(game.checkpoint());
                roomService.savePuzzleProgress(playerId, game.checkpoint());
                roomService.saveRoom(room);
            }
            // Broadcast state directly via WebSocket so frontend receives it immediately
            broadcastGameState(roomId, game);

            if (game.isGameOver()) {
                endGame(roomId, game);
            } else {
                triggerBotTurnIfNeeded(roomId, game);
            }
        } else {
            sendMoveError(roomId, playerId, "Invalid move");
            if(game instanceof com.brim.game.game.warlords.WarlordsGame)broadcastGameState(roomId,game);
        }
    }

    private void sendMoveError(String roomId, String playerId, String text) {
        messagingTemplate.convertAndSendToUser(playerId, "/queue/errors", GameMessage.of(MessageType.ERROR, roomId, playerId, text));
    }

    public TurnBasedGame getGame(String roomId) {
        return activeGames.get(roomId);
    }

    public void sendState(String roomId) {
        TurnBasedGame game = activeGames.get(roomId);
        if (game == null) game = finishedGames.get(roomId);
        if (game != null) broadcastGameState(roomId, game);
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
        synchronized (room) { initializeRoom(room); }
    }

    private void initializeRoom(GameRoom room) {
        if(stopping)throw new IllegalStateException("Server đang bảo trì.");
        if(room.getGameType()==GameType.WARLORDS_CAMPAIGN) {
            activeGames.forEach((otherId,other)->{
                var otherRoom=roomService.getRoom(otherId);
                if(!otherId.equals(room.getId())&&otherRoom!=null&&otherRoom.getGameType()==GameType.WARLORDS_CAMPAIGN
                        &&room.getHostId().equals(otherRoom.getHostId())&&activeGames.remove(otherId,other)) {
                    otherRoom.setStatus(RoomStatus.CANCELLED);roomService.saveRoom(otherRoom);
                    messagingTemplate.convertAndSend("/topic/room/"+otherId+"/state",otherRoom);
                }
            });
        }
        if (!room.hasHumanPlayers()) {
            roomService.deleteRoomIfNoHumans(room.getId());
            return;
        }
        String difficulty = "MEDIUM";
        if (room.getSettings() != null && room.getSettings().getCustomSettings() != null) {
            difficulty = String.valueOf(room.getSettings().getCustomSettings().getOrDefault("difficulty", "MEDIUM"));
        }
        TurnBasedGame game = gameRegistry.create(room.getGameType(), room.getId(), difficulty);

        if (room.getPlayers().size() < room.getGameType().minimumPlayers()) return;
        game.setPlayers(room.getPlayers());
        if (room.getGameType() == GameType.MATCH_THREE) {
            room.getSettings().getCustomSettings().putAll(roomService.puzzleProgress(room.getPlayers().get(0).getId()));
        }
        game.configure(room.getSettings().getCustomSettings());

        finishedGames.remove(room.getId());
        activeGames.put(room.getId(), game);
        room.setStatus(RoomStatus.IN_PROGRESS);
        roomService.saveRoom(room);

        if (game.realtime() && !runtime.start(room.getId(), 100, () -> tickRoom(room.getId(), game))) {
            activeGames.remove(room.getId(), game);
            room.setStatus(RoomStatus.WAITING);
            roomService.saveRoom(room);
            messagingTemplate.convertAndSend("/topic/game/" + room.getId() + "/error", Map.of("message", "Máy chủ đang đầy"));
            return;
        }

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
        if (game.privateState()) {
            var room = roomService.getRoom(roomId);
            if (room != null) for (var player : room.getPlayers()) {
                if (!player.getId().startsWith("bot-")) messagingTemplate.convertAndSend(
                        "/topic/game/" + roomId + "/state/" + player.getId(), game.getState(player.getId()));
            }
        } else messagingTemplate.convertAndSend("/topic/game/" + roomId + "/state", game.getState());
        log.debug("Broadcast game state for room {}", roomId);
    }

    private boolean tickRoom(String roomId, TurnBasedGame game) {
        if (activeGames.get(roomId) != game) return false;
        var room = roomService.getRoom(roomId);
        if (room == null || !room.hasHumanPlayers()) {
            activeGames.remove(roomId, game);
            if (room != null) roomService.deleteRoomIfNoHumans(roomId);
            return false;
        }
        game.retainPlayers(room.getPlayers().stream().map(com.brim.game.model.dto.Player::getId).collect(java.util.stream.Collectors.toSet()));
        game.tick();
        if (game.isGameOver()) {
            // Final output must not be dropped behind an intermediate snapshot.
            endGame(roomId, game);
            return false;
        }
        runtime.publish(roomId, () -> { if (activeGames.get(roomId) == game) broadcastGameState(roomId, game); });
        return true;
    }

    private void triggerBotTurnIfNeeded(String roomId, TurnBasedGame game) {
        if(stopping)return;
        var room = roomService.getRoom(roomId);
        if (room == null || !room.hasHumanPlayers()) return;
        String current = game.getCurrentPlayerId();
        if (current == null || !current.startsWith("bot-") || game.isGameOver() || !game.botReady() || !pendingBots.add(roomId)) return;
        boolean accepted = botExecutor.submit(() -> {
            try {
                var currentRoom = roomService.getRoom(roomId);
                if (currentRoom == null || !currentRoom.hasHumanPlayers()) return;
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
        if(stopping)return;
        activeGames.forEach((roomId, game) -> {
            var room = roomService.getRoom(roomId);
            if (room == null) activeGames.remove(roomId, game);
            else {
                synchronized (room) {
                    if (activeGames.get(roomId) != game) return;
                    if (!room.hasHumanPlayers()) { roomService.deleteRoomIfNoHumans(roomId); return; }
                    boolean changed;
                    try {changed = game.advanceClock(room.getPlayers().stream().map(com.brim.game.model.dto.Player::getId)
                            .collect(java.util.stream.Collectors.toSet()));}
                    catch(RuntimeException unavailable){log.warn("Unable to save clock update for room {}",roomId);return;}
                    if (changed) {
                        broadcastGameState(roomId, game);
                        if (game.isGameOver()) { endGame(roomId, game); return; }
                    }
                    triggerBotTurnIfNeeded(roomId, game);
                }
            }
        });
        finishedGames.keySet().removeIf(roomId -> roomService.getRoom(roomId) == null);
    }
    @Scheduled(fixedDelay=30000,initialDelay=30000)
    public void saveCampaigns() {
        activeGames.forEach((roomId,game)->{
            if(game instanceof com.brim.game.game.warlords.WarlordsGame warlords)
                try{warlords.flush();}catch(RuntimeException unavailable){log.warn("Campaign autosave failed for room {}",roomId);}
        });
    }
    @jakarta.annotation.PreDestroy public void prepareShutdown(){stopping=true;saveCampaigns();}

    private void endGame(String roomId, TurnBasedGame game) {
        var room = roomService.getRoom(roomId);
        if (room == null) { activeGames.remove(roomId, game); return; }
        synchronized (room) { finishRoom(roomId, game, room); }
    }

    private void finishRoom(String roomId, TurnBasedGame game, GameRoom room) {
        if (!activeGames.remove(roomId, game)) return;
        String winnerId = game.getWinner();
        finishedGames.put(roomId, game);

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
        if (!game.privateState()) {
            Map<String, Object> gameOverPayload = Map.of("winner", winnerId != null ? winnerId : "", "finalState", game.getState());
            messagingTemplate.convertAndSend("/topic/game/" + roomId + "/over",
                    GameMessage.of(MessageType.GAME_OVER, roomId, winnerId, gameOverPayload));
        }

        // Also send final game state
        broadcastGameState(roomId, game);

        log.info("Game over in room {}. Winner: {}", roomId, winnerId);
    }

    /**
     * Remove the active game for a room (used during cleanup).
     */
    public void removeGame(String roomId) {
        TurnBasedGame game = activeGames.remove(roomId);
        finishedGames.remove(roomId);
        if (game != null && game.realtime()) runtime.stop(roomId);
    }

    @org.springframework.context.event.EventListener
    public void onRoomDeleted(RoomDeletedEvent event) {
        removeGame(event.roomId());
    }

    public interface TurnBasedGame {
        default void setPlayers(java.util.List<com.brim.game.model.dto.Player> players) {
            if (!players.isEmpty()) setPlayer1(players.get(0).getId());
            if (players.size() >= 2) setPlayer2(players.get(1).getId());
        }
        default void configure(Map<String, Object> settings) {}
        default Map<String, Object> checkpoint() { return Map.of(); }
        default boolean privateState() { return false; }
        default Object getState(String viewerId) { return getState(); }
        default boolean realtime() { return false; }
        default void tick() {}
        default void retainPlayers(java.util.Set<String> members) {}
        default boolean advanceClock(java.util.Set<String> members) { return false; }
        default boolean botReady() { return true; }
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
