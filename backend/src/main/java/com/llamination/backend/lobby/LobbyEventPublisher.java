package com.llamination.backend.lobby;

import java.util.Collection;
import java.util.UUID;

public interface LobbyEventPublisher {

    void directoryChanged();

    void lobbyUpdated(LobbySnapshot lobby);

    void lobbyClosed(UUID lobbyId, Collection<String> usernames, String reason);

    void gameStarted(LobbySnapshot lobby);
}
