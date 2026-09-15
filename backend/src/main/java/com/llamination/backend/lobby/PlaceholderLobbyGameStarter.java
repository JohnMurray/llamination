package com.llamination.backend.lobby;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class PlaceholderLobbyGameStarter implements LobbyGameStarter {

    @Override
    public UUID startGame(LobbySnapshot lobby) {
        // The future simulation store must preserve this retry-safe contract across process crashes.
        return UUID.nameUUIDFromBytes(("llamination-game:" + lobby.id()).getBytes(StandardCharsets.UTF_8));
    }
}
