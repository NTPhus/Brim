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
import java.util.concurrent.ConcurrentHashMap;

@Controller
@RequiredArgsConstructor
@Slf4j
public class SnakeGameController {

    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final GameEventProducer eventProducer;

    private final Map<String, SnakeGame> activeGames = new ConcurrentHashMap<>();

    @MessageMapping("/snake/{roomId}/join")
    public void joinSnakeGame(@DestinationVariable String roomId, GameMessage<Player> message) {
        Player player = message.getPayload();
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
            if (player.getUsername() != null && !player.getUsername().isBlank()) {
                p.setUsername(player.getUsername());
            }
        } else {
            if (!room.canJoin()) {
                sendError(player.getSessionId(), "Room not found or full");
                return;
            }
            room.getPlayers().add(player);
        }

        // If room was finished and player joins again, reset status to WAITING
        if (room.getStatus() == com.brim.game.model.dto.RoomStatus.FINISHED) {
            room.setStatus(com.brim.game.model.dto.RoomStatus.WAITING);
            activeGames.remove(roomId);
        }

        roomService.saveRoom(room);

        SnakeGame game = activeGames.computeIfAbsent(roomId, k -> new SnakeGame(
                roomId, 4000, 2400, 3, 50
        ));
        if (!game.getSnakes().containsKey(player.getId())) {
            game.addPlayer(player);
        }

        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/state", room);
        messagingTemplate.convertAndSend("/topic/game/" + roomId + "/state", game.getGameState());
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
        if (game != null && game.respawn(message.getPlayerId())) broadcastGameState(roomId, game.getGameState());
    }

    private void startGame(String roomId) {
        var room = roomService.getRoom(roomId);
        if (room == null) return;
        room.setStatus(com.brim.game.model.dto.RoomStatus.IN_PROGRESS);
        room.setStartedAt(java.time.LocalDateTime.now());
        roomService.saveRoom(room);

        // Always initialize a fresh game
        SnakeGame previous = activeGames.get(roomId);
        if (previous != null) previous.setState(SnakeGame.GameState.FINISHED);
        SnakeGame game = new SnakeGame(roomId, 4000, 2400, 3, 50);
        for (Player p : room.getPlayers()) {
            game.addPlayer(p);
        }
        addAutomaticBots(game, 8);
        game.setState(SnakeGame.GameState.IN_PROGRESS);
        activeGames.put(roomId, game);

        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/state", room);
        messagingTemplate.convertAndSend("/topic/game/" + roomId + "/started", GameMessage.of(
                MessageType.GAME_STARTED, roomId, null, null
        ));
        broadcastGameState(roomId, game.getGameState());

        // Start game loop
        new Thread(() -> gameLoop(roomId)).start();
    }

    private void gameLoop(String roomId) {
        SnakeGame game = activeGames.get(roomId);
        Map<String, Integer> publishedScores = new ConcurrentHashMap<>();
        int snapshotTick = 0;
        while (game != null && game.getState() == SnakeGame.GameState.IN_PROGRESS) {
            game.update();
            publishScoreDeltas(roomId, game, publishedScores);
            // Simulate at 20 Hz, send snapshots at 10 Hz; clients interpolate at render speed.
            if (++snapshotTick % 2 == 0) broadcastGameState(roomId, game.getGameState());

            try {
                Thread.sleep(game.getTickRateMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (activeGames.get(roomId) != game) return;
        // Game over
        var room = roomService.getRoom(roomId);
        if (room != null) {
            room.setStatus(com.brim.game.model.dto.RoomStatus.FINISHED);
            room.setEndedAt(java.time.LocalDateTime.now());
            messagingTemplate.convertAndSend("/topic/room/" + roomId + "/state", room);
            messagingTemplate.convertAndSend("/topic/game/" + roomId + "/over", GameMessage.of(
                    MessageType.GAME_OVER, roomId, null, game.getGameState()
            ));
        }
        activeGames.remove(roomId);
    }

    private void broadcastGameState(String roomId, SnakeGame.SnakeGameState state) {
        messagingTemplate.convertAndSend("/topic/game/" + roomId + "/state", state);
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
        room.getPlayers().stream().filter(player -> !player.getId().startsWith("bot-")).forEach(player -> {
            SnakeGame.Snake snake = game.getSnakes().get(player.getId());
            if (snake == null) return;
            int previous = published.getOrDefault(player.getId(), 0);
            if (snake.getScore() < previous) previous = 0;
            int delta = snake.getScore() - previous;
            if (delta > 0) eventProducer.sendLeaderboardScore(roomId,
                    new GameScoreEvent(player.getId(), player.getUsername(), GameType.SNAKE, delta));
            published.put(player.getId(), snake.getScore());
        });
    }

    public record MovePayload(double angle, boolean boosting) {}
}
