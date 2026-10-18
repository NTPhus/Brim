package com.brim.game.websocket;

import com.brim.game.config.WebSocketEventListener;
import com.brim.game.kafka.GameEventProducer;
import com.brim.game.model.dto.GameMessage;
import com.brim.game.model.dto.MessageType;
import com.brim.game.model.dto.Player;
import com.brim.game.service.RoomService;
import com.brim.game.service.TurnBasedGameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class TurnBasedGameController {

    @org.springframework.beans.factory.annotation.Value("${game.kafka.route-moves:false}")
    private boolean routeMovesThroughKafka;
    private final RoomService roomService;
    private final TurnBasedGameService turnBasedGameService;
    private final GameEventProducer eventProducer;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/turn/{roomId}/join")
    public void joinTurnGame(@DestinationVariable String roomId, GameMessage<Player> message, SimpMessageHeaderAccessor headerAccessor) {
        Player player = message.getPayload();
        var room = roomService.getRoom(roomId);
        if (room == null) {
            sendError(player.getId(), "Room not found");
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
                sendError(player.getId(), "Room not found or full");
                return;
            }
            room.getPlayers().add(player);
        }

        roomService.saveRoom(room);

        // Bind WebSocket session for disconnect tracking
        String stompSessionId = headerAccessor.getSessionId();
        if (stompSessionId != null) {
            WebSocketEventListener.bindSession(stompSessionId, roomId, player.getId());
        }

        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/state", room);

        TurnBasedGameService.TurnBasedGame game = turnBasedGameService.getGame(roomId);
        if (game != null || room.getStatus() == com.brim.game.model.dto.RoomStatus.FINISHED) {
            turnBasedGameService.sendState(roomId);
        } else if (room.getPlayers().size() >= room.getGameType().minimumPlayers() && room.getStatus() == com.brim.game.model.dto.RoomStatus.WAITING) {
            boolean allReady = room.getPlayers().stream().allMatch(Player::isReady);
            if (allReady) {
                startTurnGame(roomId);
            }
        }
    }

    @MessageMapping("/turn/{roomId}/move")
    public void makeMove(@DestinationVariable String roomId, GameMessage<Object> message) {
        var room = roomService.getRoom(roomId);
        boolean realtime = room != null && room.getGameType().isRealTime();
        if (routeMovesThroughKafka && !realtime) eventProducer.sendGameMove(roomId, message.getPlayerId(), message.getPayload());
        else turnBasedGameService.processMove(roomId, message.getPlayerId(), message.getPayload());
    }

    @MessageMapping("/turn/{roomId}/ready")
    public void playerReady(@DestinationVariable String roomId, GameMessage<Void> message) {
        var room = roomService.getRoom(roomId);
        if (room == null) return;

        room.getPlayers().stream()
                .filter(p -> p.getId().equals(message.getPlayerId()))
                .findFirst()
                .ifPresent(p -> p.setReady(true));

        roomService.saveRoom(room);

        boolean allReady = room.getPlayers().size() >= room.getGameType().minimumPlayers() && room.getPlayers().stream().allMatch(Player::isReady);
        if (allReady && (room.getStatus() == com.brim.game.model.dto.RoomStatus.WAITING || room.getStatus() == com.brim.game.model.dto.RoomStatus.FINISHED)) {
            startTurnGame(roomId);
        }
    }

    @MessageMapping("/turn/{roomId}/restart")
    public void restartGame(@DestinationVariable String roomId) {
        var room = roomService.getRoom(roomId);
        if (room == null) return;
        synchronized (room) {
            for (Player p : room.getPlayers()) {
                p.setReady(true);
            }
            room.setStatus(com.brim.game.model.dto.RoomStatus.WAITING);
            roomService.saveRoom(room);
            startTurnGame(roomId);
        }
    }

    private void startTurnGame(String roomId) {
        var room = roomService.getRoom(roomId);
        if (room == null) return;

        synchronized (room) {
            // Concurrent ready/join frames must not replace a game that just started.
            if (room.getStatus() == com.brim.game.model.dto.RoomStatus.IN_PROGRESS) return;
            turnBasedGameService.initializeGame(room);
            if (room.getStatus() != com.brim.game.model.dto.RoomStatus.IN_PROGRESS) return;

            messagingTemplate.convertAndSend("/topic/room/" + roomId + "/state", room);
            messagingTemplate.convertAndSend("/topic/game/" + roomId + "/started",
                    GameMessage.of(MessageType.GAME_STARTED, roomId, null, null));
        }
    }

    private void sendError(String sessionId, String error) {
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/errors",
                GameMessage.of(MessageType.ERROR, null, sessionId, error));
    }
}
