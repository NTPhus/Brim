package com.brim.game.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final HttpSessionSecurityContextRepository contexts;
    private final HttpSessionCsrfTokenRepository csrfTokens;
    private final SocketSessions sockets;
    private final AccountService accounts;
    private final AccountSessions accountSessions;

    @GetMapping("/csrf")
    public CsrfView csrf(CsrfToken token) { return new CsrfView(token.getHeaderName(), token.getToken()); }

    @GetMapping("/session")
    public GuestSession session(HttpServletRequest request) { return GuestSession.require(request.getSession(false)); }

    @PostMapping("/guest")
    public GuestSession guest(@Valid @RequestBody Profile requestBody, HttpServletRequest request, HttpServletResponse response) {
        return establish(UUID.randomUUID().toString(),clean(requestBody.username()),false,request,response);
    }
    @PostMapping("/register")
    public GuestSession register(@Valid @RequestBody Credentials body,HttpServletRequest request,HttpServletResponse response) {
        GuestSession current=null;
        try{current=GuestSession.require(request.getSession(false));}catch(org.springframework.web.server.ResponseStatusException ignored){}
        var account=accounts.register(body.username(),body.password(),current);
        return establish(account.id(),account.username(),true,request,response);
    }
    @PostMapping("/login")
    public GuestSession login(@Valid @RequestBody Credentials body,HttpServletRequest request,HttpServletResponse response) {
        var account=accounts.authenticate(body.username(),body.password());
        return establish(account.id(),account.username(),true,request,response);
    }
    private GuestSession establish(String id,String username,boolean account,HttpServletRequest request,HttpServletResponse response) {
        // Repeated creation revokes the previous guest and every socket belonging to that session.
        var old = request.getSession(false);
        if (old != null) { sockets.revoke(old); old.invalidate(); }

        var session = request.getSession(true);
        var guest = new GuestSession(id,username,System.currentTimeMillis() + 4 * 60 * 60 * 1000L,account);
        session.setAttribute(GuestSession.ATTRIBUTE, guest);
        session.setMaxInactiveInterval(4 * 60 * 60);

        var authentication = new UsernamePasswordAuthenticationToken(guest.id(), null,
                List.of(new SimpleGrantedAuthority("ROLE_PLAYER")));

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);

        SecurityContextHolder.setContext(context);

        contexts.saveContext(context, request, response);
        if(account)accountSessions.replace(id,session);
        return guest;
    }
    @PutMapping("/profile")
    public GuestSession profile(@Valid @RequestBody Profile body, HttpServletRequest request) {
        var session = request.getSession(false);
        var guest = GuestSession.require(session);
        var updated = new GuestSession(guest.id(), clean(body.username()), guest.expiresAt(),guest.account());
        if(guest.account())accounts.rename(guest.id(),updated.username());
        session.setAttribute(GuestSession.ATTRIBUTE, updated);
        return updated;
    }
    @PostMapping("/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        sockets.revoke(request.getSession(false));
        csrfTokens.saveToken(null, request, response);
        new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
    }
    private String clean(String value) {
        String name = value.strip();
        if (name.isEmpty() || name.chars().anyMatch(Character::isISOControl))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid username");
        return name;
    }
    public record Profile(@NotBlank @Size(max = 32) String username) {}
    public record Credentials(@NotBlank @Size(min=3,max=32) String username,@NotBlank @Size(min=8,max=72) String password) {}
    public record CsrfView(String headerName, String token) {}
}
