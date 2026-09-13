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
    void loginCreatesSessionAndLogoutInvalidatesIt() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest login = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"commander\",\"password\":\"llama\"}"))
                .build();

        HttpResponse<String> loginResponse = client.send(login, HttpResponse.BodyHandlers.ofString());
        String cookie = loginResponse.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0];

        assertThat(loginResponse.statusCode()).isEqualTo(200);
        assertThat(loginResponse.body()).isEqualTo("{\"authenticated\":true,\"username\":\"commander\"}");

        HttpRequest session = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/auth/session"))
                .header("Cookie", cookie)
                .GET()
                .build();
        HttpResponse<String> sessionResponse = client.send(session, HttpResponse.BodyHandlers.ofString());

        assertThat(sessionResponse.body()).isEqualTo("{\"authenticated\":true,\"username\":\"commander\"}");

        HttpRequest logout = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/auth/logout"))
                .header("Cookie", cookie)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        assertThat(client.send(logout, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(204);

        HttpResponse<String> loggedOutSession = client.send(session, HttpResponse.BodyHandlers.ofString());
        assertThat(loggedOutSession.body()).isEqualTo("{\"authenticated\":false,\"username\":null}");
    }

    @Test
    void loginRejectsInvalidCredentials() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"commander\",\"password\":\"wrong\"}"))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("set-cookie")).isEmpty();
    }

    @Test
    void servesLoginPage() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/")).GET().build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("id=\"root\"");
    }

    @Test
    void authenticatedWebSocketAcceptsConnection() throws Exception {
        HttpRequest login = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"commander\",\"password\":\"llama\"}"))
                .build();
        HttpResponse<String> loginResponse = HttpClient.newHttpClient()
                .send(login, HttpResponse.BodyHandlers.ofString());
        String cookie = loginResponse.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0];

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

        org.springframework.web.socket.WebSocketHttpHeaders headers =
                new org.springframework.web.socket.WebSocketHttpHeaders();
        headers.add("Cookie", cookie);
        WebSocketSession session = client.execute(
                        handler, headers, URI.create("ws://localhost:" + port + "/ws/events"))
                .get(5, TimeUnit.SECONDS);
        try {
            assertThat(message.get(5, TimeUnit.SECONDS))
                    .contains("\"type\":\"connected\"")
                    .contains("\"username\":\"commander\"");
        } finally {
            session.close();
        }
    }
}
