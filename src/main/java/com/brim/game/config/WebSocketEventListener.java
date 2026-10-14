package com.brim.game.config;

import com.brim.game.model.dto.GameRoom;
import com.brim.game.model.dto.Player;
import com.brim.game.model.dto.RoomStatus;
import com.brim.game.service.RoomService;
import com.brim.game.service.TurnBasedGameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for WebSocket session disconnect events and cleans up
 * the player from any rooms they were in.
 *
 * Maintains a mapping of WebSocket sessionId -> (roomId, playerId)
 * so we can clean up when a user disconnects (closes tab, loses connection).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final RoomService roomService;
    private final TurnBasedGameService turnBasedGameService;
    private final SimpMessagingTemplate messagingTemplate;

    // Track which WebSocket session belongs to which room/player
    // Key: STOMP session ID, Value: list of (roomId, playerId) pairs
    private static final Map<String, PlayerRoomBinding> sessionBindings = new ConcurrentHashMap<>();

    public static void bindSession(String stompSessionId, String roomId, String playerId) {
        sessionBindings.put(stompSessionId, new PlayerRoomBinding(roomId, playerId));
        log.info("Bound session {} to room {} player {}", stompSessionId, roomId, playerId);
    }

    public static void unbindSession(String stompSessionId) {
        sessionBindings.remove(stompSessionId);
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        if (sessionId == null) return;

        PlayerRoomBinding binding = sessionBindings.remove(sessionId);
        if (binding == null) {
            log.debug("No room binding found for disconnected session {}", sessionId);
            return;
        }

        String roomId = binding.roomId;
        String playerId = binding.playerId;

        log.info("Player {} disconnected from room {} (session {})", playerId, roomId, sessionId);

        // Don't remove bots on disconnect
        if (playerId.startsWith("bot-")) return;

        GameRoom room = roomService.getRoom(roomId);
        if (room == null) return;

        boolean removed = roomService.leaveRoom(roomId, playerId);
        if (removed) {
            // Re-fetch the room after removal
            GameRoom updatedRoom = roomService.getRoom(roomId);
            if (updatedRoom != null) {
                // Broadcast updated room state to remaining players
                messagingTemplate.convertAndSend("/topic/room/" + roomId + "/state", updatedRoom);
                log.info("Removed disconnected player {} from room {}, {} players remaining",
                        playerId, roomId, updatedRoom.getPlayers().size());
            } else {
                log.info("Room {} deleted after last player {} disconnected", roomId, playerId);
            }
        }
    }

    private record PlayerRoomBinding(String roomId, String playerId) {}
}
