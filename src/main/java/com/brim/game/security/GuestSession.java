package com.brim.game.security;

import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Identity is issued by the server, never reconstructed from localStorage or request IDs. */
public record GuestSession(String id, String username, long expiresAt) implements Serializable {
    public static final String ATTRIBUTE = GuestSession.class.getName();
    public static GuestSession require(HttpSession session) {
        try {
            if (session != null && session.getAttribute(ATTRIBUTE) instanceof GuestSession guest
                    && guest.expiresAt > System.currentTimeMillis()) return guest;
        } catch (IllegalStateException expired) { /* invalidated servlet session */ }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired");
    }
}
