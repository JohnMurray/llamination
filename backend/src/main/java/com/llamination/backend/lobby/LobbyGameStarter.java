package com.llamination.backend.lobby;

import java.util.UUID;

public interface LobbyGameStarter {

    UUID startGame(LobbySnapshot lobby);
}
