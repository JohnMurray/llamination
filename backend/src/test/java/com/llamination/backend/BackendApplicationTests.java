package com.llamination.backend;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BackendApplicationTests {

    @Value("${local.server.port}")
    private int port;

    @Test
    void apiReturnsHelloWorld() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/hello")).GET().build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"message\":\"Hello, World!\"}");
    }

    @Test
    void webSocketReturnsHelloWorldOnConnection() throws Exception {
        CompletableFuture<String> message = new CompletableFuture<>();
        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketHandler handler = new WebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) {
            }

            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> webSocketMessage) {
                message.complete(webSocketMessage.getPayload().toString());
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {
                message.completeExceptionally(exception);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus closeStatus) {
            }

            @Override
            public boolean supportsPartialMessages() {
                return false;
            }
        };

        WebSocketSession session = client.execute(handler, "ws://localhost:" + port + "/ws/hello")
                .get(5, TimeUnit.SECONDS);
        try {
            assertThat(message.get(5, TimeUnit.SECONDS)).isEqualTo("Hello, World!");
        } finally {
            session.close();
        }
    }
}
