package com.brim.game.websocket;

import com.brim.game.game.snake.SnakeGame;
import com.brim.game.model.dto.GameMessage;
import com.brim.game.model.dto.MessageType;
import com.brim.game.model.dto.Player;
import com.brim.game.model.dto.GameScoreEvent;
import com.brim.game.model.dto.GameType;
import com.brim.game.service.RoomService;
import com.brim.game.kafka.GameEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;
import com.brim.game.runtime.GameRuntime;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import com.brim.game.config.WebSocketEventListener;
import java.util.concurrent.ConcurrentHashMap;

@Controller
@RequiredArgsConstructor
@Slf4j
public class SnakeGameController {

    private final RoomService roomService;
    private final GameRuntime runtime;
    @org.springframework.beans.factory.annotation.Value("${game.snake.grid-width:6000}")
    private int worldWidth;
    @org.springframework.beans.factory.annotation.Value("${game.snake.grid-height:3600}")
    private int worldHeight;
    private final SimpMessagingTemplate messagingTemplate;
    private final GameEventProducer eventProducer;

    private final Map<String, SnakeGame> activeGames = new ConcurrentHashMap<>();

    @MessageMapping("/snake/{roomId}/join")
    public void joinSnakeGame(@DestinationVariable String roomId, GameMessage<Player> message, SimpMessageHeaderAccessor headers) {
        Player player = message.getPayload();
        if (player == null || player.getId() == null || player.getId().startsWith("bot-")) return;
        var room = roomService.getRoom(roomId);
        if (room == null) {
            sendError(player.getSessionId(), "Room not found");
            return;
        }

        java.util.Optional<Player> existing = room.getPlayers().stream()
                .filter(p -> p.getId().equals(player.getId()))
                .findFirst();

        if (existing.isPresent()) {
            Player p = existing.get();
            p.setSessionId(player.getSessionId());
            p.setReady(player.isReady());
            if (player.getUsername() != null && !player.getUsername().isBlank()) {
                p.setUsername(player.getUsername());
            }
            if (player.getColor() != null) p.setColor(player.getColor());
        } else {
            if (!roomService.joinRoom(roomId, player)) {
                sendError(player.getSessionId(), "Room not found or full");
                return;
            }
        }

        // If room was finished and player joins again, reset status to WAITING
        if (room.getStatus() == com.brim.game.model.dto.RoomStatus.FINISHED) {
            room.setStatus(com.brim.game.model.dto.RoomStatus.WAITING);
            activeGames.remove(roomId);
        }

        roomService.saveRoom(room);
        if (headers.getSessionId() != null) WebSocketEventListener.bindSession(headers.getSessionId(), roomId, player.getId());

        SnakeGame game = activeGames.computeIfAbsent(roomId, k -> new SnakeGame(
                roomId, Math.max(800, worldWidth), Math.max(600, worldHeight), 3, 50
        ));
        if (!game.getSnakes().containsKey(player.getId())) {
            game.addPlayer(player);
        }

        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/state", room);
        broadcastGameState(roomId, null);
        log.info("Player {} joined snake game {}", player.getId(), roomId);

        // Auto-start if all current players are ready (supports solo/bot mode)
        boolean allReady = !room.getPlayers().isEmpty() &&
                room.getPlayers().stream().allMatch(Player::isReady);
        if (allReady && (room.getStatus() == com.brim.game.model.dto.RoomStatus.WAITING || room.getStatus() == com.brim.game.model.dto.RoomStatus.FINISHED)) {
            log.info("All players ready after join, starting snake game {}", roomId);
            startGame(roomId);
        }
    }

    @MessageMapping("/snake/{roomId}/move")
    public void handleMove(@DestinationVariable String roomId, GameMessage<MovePayload> message) {
        SnakeGame game = activeGames.get(roomId);
        if (game == null) return;

        MovePayload payload = message.getPayload();
        if (payload != null) game.handleInput(message.getPlayerId(), payload.angle(), payload.boosting());
    }

    @MessageMapping("/snake/{roomId}/color")
    public void playerColor(@DestinationVariable String roomId, GameMessage<ColorPayload> message) {
        SnakeGame game = activeGames.get(roomId);
        if (game != null && message.getPayload() != null) {
            String color = message.getPayload().color();
            game.setColor(message.getPlayerId(), color);
            var room = roomService.getRoom(roomId);
            if (room != null) room.getPlayers().stream().filter(p -> p.getId().equals(message.getPlayerId())).findFirst().ifPresent(p -> p.setColor(color));
            broadcastGameState(roomId, null);
        }
    }

    @MessageMapping("/snake/{roomId}/ready")
    public void playerReady(@DestinationVariable String roomId, GameMessage<Void> message) {
        var room = roomService.getRoom(roomId);
        if (room == null) return;

        room.getPlayers().stream()
                .filter(p -> p.getId().equals(message.getPlayerId()))
                .findFirst()
                .ifPresent(p -> p.setReady(true));

        roomService.saveRoom(room);

        boolean allReady = room.getPlayers().stream().allMatch(Player::isReady);
        if (allReady && (room.getStatus() == com.brim.game.model.dto.RoomStatus.WAITING || room.getStatus() == com.brim.game.model.dto.RoomStatus.FINISHED)) {
            startGame(roomId);
        }
    }

    @MessageMapping("/snake/{roomId}/restart")
    public void restartGame(@DestinationVariable String roomId) {
        var room = roomService.getRoom(roomId);
        if (room == null) return;
        for (Player p : room.getPlayers()) {
            p.setReady(true);
        }
        room.setStatus(com.brim.game.model.dto.RoomStatus.WAITING);
        roomService.saveRoom(room);
        startGame(roomId);
    }

    @MessageMapping("/snake/{roomId}/respawn")
    public void respawn(@DestinationVariable String roomId, GameMessage<Void> message) {
        SnakeGame game = activeGames.get(roomId);
        if (game != null && game.respawn(message.getPlayerId())) broadcastGameState(roomId, null);
    }

    private synchronized void startGame(String roomId) {
        var room = roomService.getRoom(roomId);
        if (room == null || room.getStatus() == com.brim.game.model.dto.RoomStatus.IN_PROGRESS) return;
        room.setStatus(com.brim.game.model.dto.RoomStatus.IN_PROGRESS);
        room.setStartedAt(java.time.LocalDateTime.now());
        roomService.saveRoom(room);

        // Always initialize a fresh game
        SnakeGame previous = activeGames.get(roomId);
        if (previous != null) previous.setState(SnakeGame.GameState.FINISHED);
        SnakeGame game = new SnakeGame(roomId, Math.max(800, worldWidth), Math.max(600, worldHeight), 3, 50);
        for (Player p : room.getPlayers()) {
            game.addPlayer(p);
        }
        addAutomaticBots(game, 4);
        game.setState(SnakeGame.GameState.IN_PROGRESS);
        activeGames.put(roomId, game);

        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/state", room);
        messagingTemplate.convertAndSend("/topic/game/" + roomId + "/started", GameMessage.of(
                MessageType.GAME_STARTED, roomId, null, null
        ));
        broadcastGameState(roomId, null);

        Map<String, Integer> publishedScores = new ConcurrentHashMap<>();
        int[] tick = {0};
        if (!runtime.start(roomId, 50, () -> tickGame(roomId, game, publishedScores, tick))) {
            game.setState(SnakeGame.GameState.FINISHED);
            activeGames.remove(roomId, game);
            room.setStatus(com.brim.game.model.dto.RoomStatus.WAITING);
            roomService.saveRoom(room);
            messagingTemplate.convertAndSend("/topic/room/" + roomId + "/state", room);
            messagingTemplate.convertAndSend("/topic/game/" + roomId + "/error", Map.of("message", "Máy chủ đang đầy, vui lòng thử lại sau"));
        }
    }

    private boolean tickGame(String roomId, SnakeGame game, Map<String, Integer> scores, int[] tick) {
        if (activeGames.get(roomId) != game) return false;
        var room = roomService.getRoom(roomId);
        if (room == null || room.getPlayers().stream().noneMatch(p -> !p.getId().startsWith("bot-"))) {
            game.setState(SnakeGame.GameState.FINISHED);
            activeGames.remove(roomId, game);
            if (room != null) {
                room.setStatus(com.brim.game.model.dto.RoomStatus.FINISHED);
                room.setEndedAt(java.time.LocalDateTime.now());
                roomService.saveRoom(room);
            }
            return false;
        }
        synchronized (game) {
            // Explicit leave or expired reconnect grace removes the corresponding avatar.
            var members = room.getPlayers().stream().map(Player::getId).collect(java.util.stream.Collectors.toSet());
            game.getSnakes().keySet().removeIf(id -> !id.startsWith("bot-") && !members.contains(id));
            game.update();
        }
        ++tick[0];
        if (tick[0] % 2 == 0) {
            boolean scoreDue = tick[0] % 20 == 0;
            runtime.publish(roomId, () -> {
                if (activeGames.get(roomId) != game) return;
                broadcastGameState(roomId, null);
            });
            if (scoreDue) runtime.publishEvent(roomId, () -> {
                if (activeGames.get(roomId) == game) publishScoreDeltas(roomId, game, scores);
            });
        }
        return game.getState() == SnakeGame.GameState.IN_PROGRESS;
    }

    private void broadcastGameState(String roomId, SnakeGame.SnakeGameState ignored) {
        SnakeGame game = activeGames.get(roomId);
        var room = roomService.getRoom(roomId);
        if (game == null || room == null) return;
        for (Player player : room.getPlayers()) {
            if (!player.getId().startsWith("bot-")) {
                messagingTemplate.convertAndSend("/topic/game/" + roomId + "/state/" + player.getId(), game.getGameState(player.getId()));
            }
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 30000)
    public void releaseDeletedRooms() {
        // Waiting simulations have no tick callback to release them after REST deletion.
        activeGames.forEach((roomId, game) -> {
            if (roomService.getRoom(roomId) == null && activeGames.remove(roomId, game)) {
                game.setState(SnakeGame.GameState.FINISHED);
                runtime.stop(roomId);
            }
        });
    }

    private void sendError(String sessionId, String error) {
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/errors",
                GameMessage.of(MessageType.ERROR, null, sessionId, error));
    }

    private void addAutomaticBots(SnakeGame game, int targetPlayers) {
        while (game.getSnakes().size() < targetPlayers) {
            Player bot = new Player();
            bot.setId("bot-auto-snake-" + game.getSnakes().size());
            bot.setUsername("Snake AI " + game.getSnakes().size());
            bot.setReady(true);
            game.addPlayer(bot);
        }
    }

    private void publishScoreDeltas(String roomId, SnakeGame game, Map<String, Integer> published) {
        var room = roomService.getRoom(roomId);
        if (room == null) return;
        game.getSnakes().values().forEach(snake -> {
            if (snake == null || snake.getPlayerId().startsWith("bot-")) return;
            int previous = published.getOrDefault(snake.getPlayerId(), 0);
            if (snake.getScore() < previous) {
                // Dash spends score; only positive score deltas belong on the daily board.
                published.put(snake.getPlayerId(), snake.getScore());
                return;
            }
            int delta = snake.getScore() - previous;
            if (delta > 0) eventProducer.sendLeaderboardScore(roomId,
                    new GameScoreEvent(snake.getPlayerId(), snake.getUsername(), GameType.SNAKE, delta));
            published.put(snake.getPlayerId(), snake.getScore());
        });
    }

    public record MovePayload(double angle, boolean boosting) {}
    public record ColorPayload(String color) {}
}
