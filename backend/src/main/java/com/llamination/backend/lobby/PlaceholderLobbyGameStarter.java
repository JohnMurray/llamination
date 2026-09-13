package com.llamination.backend.lobby;

import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class PlaceholderLobbyGameStarter implements LobbyGameStarter {

    @Override
    public UUID startGame(LobbySnapshot lobby) {
        return UUID.randomUUID();
    }
}
