package com.brim.game.config;

import com.brim.game.security.GameMessageGuard;
import com.brim.game.security.SocketHandshake;
import com.brim.game.security.SocketSessions;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final GameMessageGuard guard;
    private final SocketHandshake handshake;
    private final SocketSessions sockets;
    @Value("${game.security.allowed-origins:}")
    private String[] allowedOrigins;

    @Bean
    public ThreadPoolTaskScheduler heartbeatScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("stomp-heartbeat-");
        return scheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setPreservePublishOrder(true);
        registry.enableSimpleBroker("/topic", "/queue").setTaskScheduler(heartbeatScheduler())
                .setHeartbeatValue(new long[]{10000, 10000});
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
            @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                if (!sockets.register(session)) { session.close(CloseStatus.POLICY_VIOLATION); return; }
                try { super.afterConnectionEstablished(session); }
                catch (Exception error) { sockets.remove(session.getId()); throw error; }
            }
            @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                sockets.remove(session.getId());
                super.afterConnectionClosed(session, status);
            }
        });
        registration.setMessageSizeLimit(16 * 1024)
                .setSendBufferSizeLimit(256 * 1024).setSendTimeLimit(5000).setTimeToFirstMessage(15000);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(guard);
        registration.taskExecutor().corePoolSize(2).maxPoolSize(4).queueCapacity(256);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override public Message<?> preSend(Message<?> message, MessageChannel channel) { return guard.outbound(message); }
        });
        registration.taskExecutor().corePoolSize(2).maxPoolSize(4).queueCapacity(256);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/game")
                .addInterceptors(handshake)
                .setAllowedOrigins(java.util.Arrays.stream(allowedOrigins).filter(origin -> !origin.isBlank()).toArray(String[]::new))
                .withSockJS();
    }
}
