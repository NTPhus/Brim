package com.brim.game.security;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
@RequiredArgsConstructor
public class SocketHandshake implements HandshakeInterceptor {
    private final HttpSessionCsrfTokenRepository csrfTokens;
    @Override public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servlet)) return false;
        var http = servlet.getServletRequest();
        try {
            var guest = GuestSession.require(http.getSession(false));
            if (http.getUserPrincipal() == null || !guest.id().equals(http.getUserPrincipal().getName())) return false;
            var csrf = csrfTokens.loadToken(http);
            if (csrf == null) return false;
            attributes.put(SocketSessions.HTTP_SESSION, http.getSession(false));
            attributes.put(SocketSessions.CSRF_TOKEN, csrf.getToken());
            return true;
        } catch (RuntimeException invalid) { response.setStatusCode(HttpStatus.UNAUTHORIZED); return false; }
    }
    @Override public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Exception exception) { }
}
