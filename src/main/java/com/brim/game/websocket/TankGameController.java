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
import java.util.concurrent.ConcurrentHashMap;

@Controller
@RequiredArgsConstructor
@Slf4j
public class TankGameController {

    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final GameEventProducer eventProducer;

    private final Map<String, TankGame> activeGames = new ConcurrentHashMap<>();

    @MessageMapping("/tank/{roomId}/join")
    public void joinTankGame(@DestinationVariable String roomId, GameMessage<Player> message) {
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
            if (player.getColor() != null) p.setColor(player.getColor());
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

        TankGame game = activeGames.computeIfAbsent(roomId, k -> new TankGame(
                roomId, 3600, 2400, 5, 15, 50
        ));
        if (!game.getTanks().containsKey(player.getId())) {
            game.addPlayer(player);
        }

        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/state", room);
        messagingTemplate.convertAndSend("/topic/game/" + roomId + "/state", game.getGameState());
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
            broadcastGameState(roomId, game.getGameState());
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
        if (game != null && game.respawn(message.getPlayerId())) broadcastGameState(roomId, game.getGameState());
    }

    private void startGame(String roomId) {
        var room = roomService.getRoom(roomId);
        if (room == null) return;
        room.setStatus(com.brim.game.model.dto.RoomStatus.IN_PROGRESS);
        room.setStartedAt(java.time.LocalDateTime.now());
        roomService.saveRoom(room);

        // Always initialize fresh game
        TankGame previous = activeGames.get(roomId);
        if (previous != null) previous.setState(TankGame.GameState.FINISHED);
        TankGame game = new TankGame(roomId, 3600, 2400, 5, 15, 50);
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
        broadcastGameState(roomId, game.getGameState());

        new Thread(() -> gameLoop(roomId)).start();
    }

    private void gameLoop(String roomId) {
        TankGame game = activeGames.get(roomId);
        Map<String, Integer> publishedScores = new ConcurrentHashMap<>();
        int snapshotTick = 0;
        while (game != null && game.getState() == TankGame.GameState.IN_PROGRESS) {
            game.update();
            publishScoreDeltas(roomId, game, publishedScores);
            if (++snapshotTick % 2 == 0) broadcastGameState(roomId, game.getGameState());

            try {
                Thread.sleep(game.getTickRateMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (activeGames.get(roomId) != game) return;
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

    private void broadcastGameState(String roomId, TankGame.TankGameState state) {
        messagingTemplate.convertAndSend("/topic/game/" + roomId + "/state", state);
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
