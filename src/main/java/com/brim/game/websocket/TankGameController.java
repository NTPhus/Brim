package com.brim.game.websocket;

import com.brim.game.game.tank.TankGame;
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
public class TankGameController {

    private final RoomService roomService;
    private final GameRuntime runtime;
    @org.springframework.beans.factory.annotation.Value("${game.tank.map-width:3600}")
    private int worldWidth;
    @org.springframework.beans.factory.annotation.Value("${game.tank.map-height:2400}")
    private int worldHeight;
    private final SimpMessagingTemplate messagingTemplate;
    private final GameEventProducer eventProducer;

    private final Map<String, TankGame> activeGames = new ConcurrentHashMap<>();

    @MessageMapping("/tank/{roomId}/join")
    public void joinTankGame(@DestinationVariable String roomId, GameMessage<Player> message, SimpMessageHeaderAccessor headers) {
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

        TankGame game = activeGames.computeIfAbsent(roomId, k -> new TankGame(
                roomId, Math.max(800, worldWidth), Math.max(600, worldHeight), 5, 15, 50, String.valueOf(room.getSettings().getCustomSettings().getOrDefault("mapId", "ARENA"))
        ));
        if (!game.getTanks().containsKey(player.getId())) {
            game.addPlayer(player);
        }

        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/state", room);
        broadcastGameState(roomId, null);
        log.info("Player {} joined tank game {}", player.getId(), roomId);

        // Auto-start if all current players are ready (supports solo/bot mode)
        boolean allReady = !room.getPlayers().isEmpty() &&
                room.getPlayers().stream().allMatch(Player::isReady);
        if (allReady && (room.getStatus() == com.brim.game.model.dto.RoomStatus.WAITING || room.getStatus() == com.brim.game.model.dto.RoomStatus.FINISHED)) {
            log.info("All players ready after join, starting tank game {}", roomId);
            startGame(roomId);
        }
    }

    @MessageMapping("/tank/{roomId}/input")
    public void handleInput(@DestinationVariable String roomId, GameMessage<TankGame.TankInput> message) {
        TankGame game = activeGames.get(roomId);
        if (game == null) return;

        game.handleInput(message.getPlayerId(), message.getPayload());
    }

    @MessageMapping("/tank/{roomId}/color")
    public void playerColor(@DestinationVariable String roomId, GameMessage<ColorPayload> message) {
        TankGame game = activeGames.get(roomId);
        if (game != null && message.getPayload() != null) {
            String color = message.getPayload().color();
            game.setColor(message.getPlayerId(), color);
            var room = roomService.getRoom(roomId);
            if (room != null) room.getPlayers().stream().filter(p -> p.getId().equals(message.getPlayerId())).findFirst().ifPresent(p -> p.setColor(color));
            broadcastGameState(roomId, null);
        }
    }

    @MessageMapping("/tank/{roomId}/ready")
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

    @MessageMapping("/tank/{roomId}/restart")
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

    @MessageMapping("/tank/{roomId}/respawn")
    public void respawn(@DestinationVariable String roomId, GameMessage<Void> message) {
        TankGame game = activeGames.get(roomId);
        if (game != null && game.respawn(message.getPlayerId())) broadcastGameState(roomId, null);
    }

    private synchronized void startGame(String roomId) {
        var room = roomService.getRoom(roomId);
        if (room == null || room.getStatus() == com.brim.game.model.dto.RoomStatus.IN_PROGRESS) return;
        room.setStatus(com.brim.game.model.dto.RoomStatus.IN_PROGRESS);
        room.setStartedAt(java.time.LocalDateTime.now());
        roomService.saveRoom(room);

        // Always initialize fresh game
        TankGame previous = activeGames.get(roomId);
        if (previous != null) previous.setState(TankGame.GameState.FINISHED);
        TankGame game = new TankGame(roomId, Math.max(800, worldWidth), Math.max(600, worldHeight), 5, 15, 50, String.valueOf(room.getSettings().getCustomSettings().getOrDefault("mapId", "ARENA")));
        for (Player p : room.getPlayers()) {
            game.addPlayer(p);
        }
        addAutomaticBots(game, 4);
        game.setState(TankGame.GameState.IN_PROGRESS);
        activeGames.put(roomId, game);

        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/state", room);
        messagingTemplate.convertAndSend("/topic/game/" + roomId + "/started", GameMessage.of(
                MessageType.GAME_STARTED, roomId, null, null
        ));
        broadcastGameState(roomId, null);

        Map<String, Integer> publishedScores = new ConcurrentHashMap<>();
        int[] tick = {0};
        if (!runtime.start(roomId, 50, () -> tickGame(roomId, game, publishedScores, tick))) {
            game.setState(TankGame.GameState.FINISHED);
            activeGames.remove(roomId, game);
            room.setStatus(com.brim.game.model.dto.RoomStatus.WAITING);
            roomService.saveRoom(room);
            messagingTemplate.convertAndSend("/topic/room/" + roomId + "/state", room);
            messagingTemplate.convertAndSend("/topic/game/" + roomId + "/error", Map.of("message", "Máy chủ đang đầy, vui lòng thử lại sau"));
        }
    }

    private boolean tickGame(String roomId, TankGame game, Map<String, Integer> scores, int[] tick) {
        if (activeGames.get(roomId) != game) return false;
        var room = roomService.getRoom(roomId);
        if (room == null || room.getPlayers().stream().noneMatch(p -> !p.getId().startsWith("bot-"))) {
            game.setState(TankGame.GameState.FINISHED);
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
            game.getTanks().keySet().removeIf(id -> !id.startsWith("bot-") && !members.contains(id));
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
        return game.getState() == TankGame.GameState.IN_PROGRESS;
    }

    private void broadcastGameState(String roomId, TankGame.TankGameState ignored) {
        TankGame game = activeGames.get(roomId);
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
                game.setState(TankGame.GameState.FINISHED);
                runtime.stop(roomId);
            }
        });
    }

    private void sendError(String sessionId, String error) {
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/errors",
                GameMessage.of(MessageType.ERROR, null, sessionId, error));
    }

    private void addAutomaticBots(TankGame game, int targetPlayers) {
        while (game.getTanks().size() < targetPlayers) {
            Player bot = new Player();
            bot.setId("bot-auto-tank-" + game.getTanks().size());
            bot.setUsername("Tank AI " + game.getTanks().size());
            bot.setReady(true);
            game.addPlayer(bot);
        }
    }

    private void publishScoreDeltas(String roomId, TankGame game, Map<String, Integer> published) {
        var room = roomService.getRoom(roomId);
        if (room == null) return;
        var memberIds = room.getPlayers().stream().map(Player::getId).collect(java.util.stream.Collectors.toSet());
        published.keySet().retainAll(memberIds);
        room.getPlayers().stream().filter(player -> !player.getId().startsWith("bot-")).forEach(player -> {
            TankGame.Tank tank = game.getTanks().get(player.getId());
            if (tank == null) return;
            int previous = published.getOrDefault(player.getId(), 0);
            int delta = tank.getScore() - previous;
            if (delta > 0) eventProducer.sendLeaderboardScore(roomId,
                    new GameScoreEvent(player.getId(), player.getUsername(), GameType.TANK, delta));
            published.put(player.getId(), tank.getScore());
        });
    }

    public record ColorPayload(String color) {}
}
