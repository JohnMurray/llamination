package com.llamination.backend.config;

import com.llamination.backend.transport.LobbyWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {

    private final LobbyWebSocketHandler lobbyWebSocketHandler;

    public WebSocketConfiguration(LobbyWebSocketHandler lobbyWebSocketHandler) {
        this.lobbyWebSocketHandler = lobbyWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(lobbyWebSocketHandler, "/ws/events")
                .addInterceptors(new HttpSessionHandshakeInterceptor());
    }
}
