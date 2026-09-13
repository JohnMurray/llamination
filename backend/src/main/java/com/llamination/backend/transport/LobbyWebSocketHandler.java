package com.llamination.backend.transport;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.llamination.backend.auth.SessionIdentity;
import com.llamination.backend.lobby.LobbyEventPublisher;
import com.llamination.backend.lobby.LobbyService;
import com.llamination.backend.lobby.LobbySnapshot;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

@Component
public class LobbyWebSocketHandler extends TextWebSocketHandler implements LobbyEventPublisher {

    private static final int SEND_TIMEOUT_MILLIS = 5_000;
    private static final int BUFFER_SIZE_BYTES = 64 * 1024;

    private final ObjectMapper objectMapper;
    private final ObjectProvider<LobbyService> lobbyService;
    private final Map<String, Set<WebSocketSession>> sessionsByUsername = new ConcurrentHashMap<>();
    private final Map<String, String> usernamesBySessionId = new ConcurrentHashMap<>();

    public LobbyWebSocketHandler(ObjectMapper objectMapper, ObjectProvider<LobbyService> lobbyService) {
        this.objectMapper = objectMapper;
        this.lobbyService = lobbyService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String username = (String) session.getAttributes().get(SessionIdentity.USERNAME_ATTRIBUTE);
        if (username == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Authentication required"));
            return;
        }
        WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIMEOUT_MILLIS, BUFFER_SIZE_BYTES);
        sessionsByUsername.computeIfAbsent(username, ignored -> ConcurrentHashMap.newKeySet())
                .add(safeSession);
        usernamesBySessionId.put(safeSession.getId(), username);
        lobbyService.getObject().playerConnected(username);
        send(safeSession, "connected", Map.of("username", username));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        recordDisconnect(removeSession(session));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        recordDisconnect(removeSession(session));
    }

    @Override
    public void directoryChanged() {
        sendToAll("lobby_directory_changed", Map.of());
    }

    @Override
    public void lobbyUpdated(LobbySnapshot lobby) {
        sendTo(
                lobby.members().stream().map(LobbySnapshot.Member::username).toList(),
                "lobby_updated",
                lobby);
    }

    @Override
    public void lobbyClosed(UUID lobbyId, Collection<String> usernames, String reason) {
        sendTo(usernames, "lobby_closed", Map.of("lobbyId", lobbyId, "reason", reason));
    }

    @Override
    public void gameStarted(LobbySnapshot lobby) {
        sendTo(
                lobby.members().stream().map(LobbySnapshot.Member::username).toList(),
                "game_started",
                Map.of("lobbyId", lobby.id(), "gameId", lobby.gameId()));
    }

    private void sendToAll(String type, Object payload) {
        sessionsByUsername.values().stream()
                .flatMap(Collection::stream)
                .forEach(session -> send(session, type, payload));
    }

    private void sendTo(Collection<String> usernames, String type, Object payload) {
        usernames.stream()
                .map(sessionsByUsername::get)
                .filter(java.util.Objects::nonNull)
                .flatMap(Collection::stream)
                .forEach(session -> send(session, type, payload));
    }

    private void send(WebSocketSession session, String type, Object payload) {
        try {
            session.sendMessage(new TextMessage(
                    objectMapper.writeValueAsString(new EventEnvelope(type, payload))));
        } catch (IOException | RuntimeException exception) {
            recordDisconnect(removeSession(session));
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (IOException ignored) {
                // Session is already unusable.
            }
        }
    }

    private RemovedSession removeSession(WebSocketSession session) {
        String username = usernamesBySessionId.remove(session.getId());
        if (username == null) {
            return null;
        }
        Set<WebSocketSession> sessions = sessionsByUsername.get(username);
        boolean lastSession = true;
        if (sessions != null) {
            sessions.removeIf(candidate -> candidate.getId().equals(session.getId()));
            if (sessions.isEmpty()) {
                sessionsByUsername.remove(username, sessions);
            } else {
                lastSession = false;
            }
        }
        return new RemovedSession(username, lastSession);
    }

    private void recordDisconnect(RemovedSession removed) {
        if (removed != null && removed.lastSession()) {
            lobbyService.getObject().playerDisconnected(removed.username());
        }
    }

    private record EventEnvelope(String type, Object payload) {
    }

    private record RemovedSession(String username, boolean lastSession) {
    }
}
