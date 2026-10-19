package com.brim.game.config;

import com.brim.game.model.dto.GameRoom;
import com.brim.game.service.RoomDeletedEvent;
import com.brim.game.service.RoomService;
import com.brim.game.service.TurnBasedGameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for WebSocket session disconnect events and cleans up
 * the player from any rooms they were in.
 *
 * Maintains a mapping of WebSocket sessionId -> set of (roomId, playerId) pairs
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
    // Key: STOMP session ID, Value: set of (roomId, playerId) pairs
    private static final Map<String, Set<PlayerRoomBinding>> sessionBindings = new ConcurrentHashMap<>();

    private record PendingLeave(String roomId, String playerId, long expiresAt) {}
    private static final Map<String, PendingLeave> pendingLeaves = new ConcurrentHashMap<>();

    public static synchronized void bindSession(String stompSessionId, String roomId, String playerId) {
        pendingLeaves.remove(roomId + ":" + playerId);
        sessionBindings.computeIfAbsent(stompSessionId, id -> ConcurrentHashMap.newKeySet())
                .add(new PlayerRoomBinding(roomId, playerId));
        log.info("Bound session {} to room {} player {}", stompSessionId, roomId, playerId);
    }

    public static synchronized void unbindSession(String stompSessionId) {
        sessionBindings.remove(stompSessionId);
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        if (sessionId == null) return;

        synchronized (WebSocketEventListener.class) {
            Set<PlayerRoomBinding> bindings = sessionBindings.remove(sessionId);
            if (bindings == null) return;
            for (PlayerRoomBinding binding : bindings) {
                String roomId = binding.roomId;
                String playerId = binding.playerId;
                log.info("Player {} disconnected from room {} (session {})", playerId, roomId, sessionId);
                // Keep seats for temporary outages, and for players connected through another socket.
                if (!playerId.startsWith("bot-") && !hasBinding(binding)) {
                    pendingLeaves.put(roomId + ":" + playerId,
                            new PendingLeave(roomId, playerId, System.currentTimeMillis() + 30_000));
                }
            }
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 5000)
    public void expireDisconnectedPlayers() {
        expireDisconnectedPlayers(System.currentTimeMillis());
    }

    private void expireDisconnectedPlayers(long now) {
        pendingLeaves.forEach((key, leave) -> {
            if (leave.expiresAt > now || !pendingLeaves.remove(key, leave)) return;
            if (hasBinding(new PlayerRoomBinding(leave.roomId, leave.playerId))) return;
            roomService.leaveRoom(leave.roomId, leave.playerId);
            GameRoom room = roomService.getRoom(leave.roomId);
            if (room != null) messagingTemplate.convertAndSend("/topic/room/" + leave.roomId + "/state", room);
        });
    }

    @EventListener
    public void onRoomDeleted(RoomDeletedEvent event) {
        synchronized (WebSocketEventListener.class) {
            sessionBindings.values().forEach(bindings -> bindings.removeIf(b -> b.roomId.equals(event.roomId())));
            sessionBindings.entrySet().removeIf(entry -> entry.getValue().isEmpty());
            pendingLeaves.values().removeIf(leave -> leave.roomId.equals(event.roomId()));
        }
    }

    private static boolean hasBinding(PlayerRoomBinding binding) {
        return sessionBindings.values().stream().anyMatch(bindings -> bindings.contains(binding));
    }

    private record PlayerRoomBinding(String roomId, String playerId) {}
}
