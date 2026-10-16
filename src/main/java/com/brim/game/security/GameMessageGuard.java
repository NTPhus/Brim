package com.brim.game.security;

import com.brim.game.model.dto.GameType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;

/** Authorizes before both annotated handlers and the simple broker see a client frame. */
@Component
@RequiredArgsConstructor
public class GameMessageGuard implements ChannelInterceptor {
    private static final Pattern ACTION = Pattern.compile("^/app/(snake|tank|turn)/([a-zA-Z0-9-]{1,64})/(join|ready|move|input|color|restart|respawn)$");
    private static final Pattern TOPIC = Pattern.compile("^/topic/(room|game)/([a-zA-Z0-9-]{1,64})/(state(?:/[a-zA-Z0-9-]{1,64})?|started|over|error|deleted)$");
    private final RoomAccess access;
    private final RequestLimits limits;
    private final ObjectMapper mapper;
    private final Map<String, SubscriptionSession> sessions = new ConcurrentHashMap<>();
    private record SubscriptionSession(String playerId, HttpSession http, Set<String> subscriptions) { }

    @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null) return message;
        var command = headers.getCommand();
        if (command == StompCommand.DISCONNECT) { sessions.remove(headers.getSessionId()); return message; }
        var http = headers.getSessionAttributes() == null ? null
                : (HttpSession) headers.getSessionAttributes().get(SocketSessions.HTTP_SESSION);
        var guest = GuestSession.require(http);
        if (headers.getUser() == null || !guest.id().equals(headers.getUser().getName())) RoomAccess.deny();
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            String expected = (String) headers.getSessionAttributes().get(SocketSessions.CSRF_TOKEN);
            String actual = headers.getFirstNativeHeader("X-CSRF-TOKEN");
            if (expected == null || actual == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    actual.getBytes(StandardCharsets.UTF_8))) RoomAccess.deny();
            sessions.put(headers.getSessionId(), new SubscriptionSession(guest.id(), http, ConcurrentHashMap.newKeySet()));
            return message;
        }
        var session = sessions.get(headers.getSessionId());
        if (session == null) RoomAccess.deny();
        if (command == StompCommand.SUBSCRIBE) {
            if (!limits.allow("subscribe:" + guest.id(), 20, 5)) RoomAccess.deny();
            authorizeSubscription(headers.getDestination(), guest.id());
            String id = headers.getSubscriptionId();
            if (id == null || id.length() > 128 || session.subscriptions().size() >= 32 || !session.subscriptions().add(id)) RoomAccess.deny();
            return message;
        }
        if (command == StompCommand.UNSUBSCRIBE) {
            if (headers.getSubscriptionId() != null) session.subscriptions().remove(headers.getSubscriptionId());
            return message;
        }
        if (command == null && headers.getMessageType() == SimpMessageType.HEARTBEAT) return message;
        if (command != StompCommand.SEND) { RoomAccess.deny(); return null; }
        var match = ACTION.matcher(headers.getDestination() == null ? "" : headers.getDestination());
        if (!match.matches()) { RoomAccess.deny(); return null; }
        String kind = match.group(1), roomId = match.group(2), action = match.group(3);
        var room = access.member(roomId, guest.id());
        access.gameKind(room, kind);
        boolean validAction = kind.equals("turn") ? Set.of("join", "ready", "move", "restart").contains(action)
                : Set.of("join", "ready", "color", "restart", "respawn", kind.equals("tank") ? "input" : "move").contains(action);
        if (!validAction) RoomAccess.deny();
        if (action.equals("restart")) access.host(roomId, guest.id());
        boolean input = action.equals("input") || action.equals("move");
        if (!limits.allow((input && !kind.equals("turn") ? "input:" : "control:") + guest.id(),
                input && !kind.equals("turn") ? 60 : 12, input && !kind.equals("turn") ? 30 : 4)) RoomAccess.deny();
        try {
            if (!(message.getPayload() instanceof byte[] bytes) || bytes.length > 4096) RoomAccess.deny();
            var tree = mapper.readTree((byte[]) message.getPayload());
            if (!(tree instanceof ObjectNode body)) { RoomAccess.deny(); return null; }
            if (body.has("playerId")) access.claimedIdentity(body.path("playerId").asText(), guest.id());
            if (body.has("roomId")) access.claimedIdentity(body.path("roomId").asText(), roomId);
            body.put("playerId", guest.id()); body.put("roomId", roomId);
            if (action.equals("join")) {
                if (!(body.get("payload") instanceof ObjectNode payload)) { RoomAccess.deny(); return null; }
                if (payload.has("id")) access.claimedIdentity(payload.path("id").asText(), guest.id());
                if (payload.has("color") && !payload.path("color").asText().matches("#[0-9a-fA-F]{6}")) RoomAccess.deny();
                // Only ready/color are client-controlled; strip score, state, session IDs and extra fields.
                var safe = mapper.createObjectNode();
                safe.put("id", guest.id()); safe.put("username", guest.username());
                safe.put("sessionId", headers.getSessionId()); safe.put("ready", payload.path("ready").asBoolean(false));
                safe.put("score", 0); safe.put("state", "WAITING");
                if (payload.has("color")) safe.set("color", payload.get("color"));
                body.set("payload", safe);
            } else if (action.equals("color")) {
                if (!body.path("payload").path("color").asText().matches("#[0-9a-fA-F]{6}")) RoomAccess.deny();
            } else if (action.equals("move") || action.equals("input")) {
                if (!body.path("payload").isObject()) RoomAccess.deny();
                if (kind.equals("snake")) {
                    var angle = body.path("payload").path("angle");
                    if (!angle.isNumber() || !Double.isFinite(angle.asDouble()) || Math.abs(angle.asDouble()) > 1e6) RoomAccess.deny();
                }
            }
            return MessageBuilder.createMessage(mapper.writeValueAsBytes(body), message.getHeaders());
        } catch (java.io.IOException malformed) { RoomAccess.deny(); return null; }
    }

    public void authorizeSubscription(String destination, String playerId) {
        if ("/user/queue/errors".equals(destination) || "/topic/leaderboard".equals(destination)) return;
        if (destination != null && destination.startsWith("/topic/leaderboard/")) {
            try { GameType.valueOf(destination.substring("/topic/leaderboard/".length())); return; }
            catch (IllegalArgumentException invalid) { RoomAccess.deny(); }
        }
        var match = TOPIC.matcher(destination == null ? "" : destination);
        if (!match.matches()) { RoomAccess.deny(); return; }
        var room = access.member(match.group(2), playerId);
        String suffix = match.group(3);
        if (suffix.startsWith("state/")) {
            if (!suffix.equals("state/" + playerId) || match.group(1).equals("room")) RoomAccess.deny();
        } else if (suffix.equals("state") && match.group(1).equals("game")
                && (room.getGameType() == GameType.TANK || room.getGameType() == GameType.SNAKE)) RoomAccess.deny();
    }

    /** Re-check after leave/logout, including subscriptions that were allowed earlier. */
    public Message<?> outbound(Message<?> message) {
        var headers = SimpMessageHeaderAccessor.wrap(message);
        if (headers.getMessageType() != SimpMessageType.MESSAGE) return message;
        var session = headers.getSessionId() == null ? null : sessions.get(headers.getSessionId());
        if (session == null) return null;
        try {
            GuestSession.require(session.http());
            // Deletion notification contains no private game state; deliver it to former members.
            String destination = headers.getDestination();
            if (destination != null && !destination.endsWith("/deleted") && destination.startsWith("/topic/"))
                authorizeSubscription(destination, session.playerId());
            return message;
        } catch (RuntimeException denied) { return null; }
    }

    @org.springframework.context.event.EventListener
    public void disconnected(org.springframework.web.socket.messaging.SessionDisconnectEvent event) {
        sessions.remove(event.getSessionId());
    }
}
