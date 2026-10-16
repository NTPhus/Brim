package com.brim.game.security;

import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@Component
public class SocketSessions {
    public static final String HTTP_SESSION = "brim.httpSession";
    public static final String CSRF_TOKEN = "brim.csrf";
    private final Map<String, WebSocketSession> sockets = new ConcurrentHashMap<>();
    public synchronized boolean register(WebSocketSession socket) {
        HttpSession http = (HttpSession) socket.getAttributes().get(HTTP_SESSION);
        GuestSession guest = GuestSession.require(http);
        long count = sockets.values().stream().filter(s -> s.getPrincipal() != null
                && guest.id().equals(s.getPrincipal().getName())).count();
        if (count >= 4 || sockets.size() >= 1024) return false;
        sockets.put(socket.getId(), socket);
        return true;
    }
    public void remove(String id) { sockets.remove(id); }
    public void revoke(HttpSession session) {
        sockets.values().forEach(socket -> {
            if (socket.getAttributes().get(HTTP_SESSION) == session) close(socket);
        });
    }
    @Scheduled(fixedDelay = 5000)
    public void expire() {
        sockets.values().forEach(socket -> {
            try { GuestSession.require((HttpSession) socket.getAttributes().get(HTTP_SESSION)); }
            catch (RuntimeException invalid) { close(socket); }
        });
    }
    private void close(WebSocketSession socket) {
        sockets.remove(socket.getId());
        try { socket.close(CloseStatus.POLICY_VIOLATION); } catch (IOException ignored) { }
    }
}
