package com.brim.game.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
    @Bean public HttpSessionCsrfTokenRepository csrfTokens() { return new HttpSessionCsrfTokenRepository(); }
    @Bean public HttpSessionSecurityContextRepository securityContexts() { return new HttpSessionSecurityContextRepository(); }
    @Bean
    public SecurityFilterChain httpSecurity(HttpSecurity http, RequestLimits limits,
            HttpSessionCsrfTokenRepository csrfTokens, HttpSessionSecurityContextRepository contexts) throws Exception {
        http.csrf(csrf -> csrf.csrfTokenRepository(csrfTokens)
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                // SockJS uses POST; its authenticated STOMP CONNECT validates the same CSRF token.
                .ignoringRequestMatchers("/ws/**"))
            .securityContext(context -> context.securityContextRepository(contexts))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/csrf", "/auth/guest", "/actuator/health", "/error").permitAll()
                .requestMatchers("/auth/**", "/rooms/**", "/leaderboard", "/ws/**", "/warlords", "/warlords/**").authenticated()
                .anyRequest().denyAll())
            .requestCache(cache -> cache.disable())
            .formLogin(form -> form.disable()).httpBasic(basic -> basic.disable()).logout(logout -> logout.disable())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, error) -> response.sendError(401))
                .accessDeniedHandler((request, response, error) -> response.sendError(403)))
            .addFilterBefore(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                        FilterChain chain) throws ServletException, IOException {
                    String path = request.getServletPath();
                    boolean safe = request.getMethod().equals("GET") || request.getMethod().equals("HEAD");
                    String key = request.getRemoteAddr();
                    if (!limits.allow("http:" + key, 120, 60)
                            || (!safe && !path.startsWith("/ws/") && !limits.allow("write:" + key, 30, 10))
                            || (path.equals("/auth/guest") && request.getMethod().equals("POST")
                                && !limits.allow("guest:" + key, 5, 1.0 / 30))) {
                        response.setHeader("Retry-After", "3"); response.sendError(429); return;
                    }
                    if (!path.equals("/auth/csrf") && !path.equals("/auth/guest")
                            && request.getUserPrincipal() != null) {
                        try { GuestSession.require(request.getSession(false)); }
                        catch (RuntimeException expired) { response.sendError(401); return; }
                    }
                    chain.doFilter(request, response);
                }
            }, AuthorizationFilter.class);
        return http.build();
    }
}
