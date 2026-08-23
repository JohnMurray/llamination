package com.llamination.backend.config;

import com.llamination.backend.hello.HelloWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {

    private final HelloWebSocketHandler helloWebSocketHandler;

    public WebSocketConfiguration(HelloWebSocketHandler helloWebSocketHandler) {
        this.helloWebSocketHandler = helloWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(helloWebSocketHandler, "/ws/hello");
    }
}
