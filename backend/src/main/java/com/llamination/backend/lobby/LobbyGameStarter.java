package com.llamination.backend.lobby;

import java.util.UUID;

/** Creates or retrieves a game idempotently for a lobby that won its countdown transition. */
public interface LobbyGameStarter {

    UUID startGame(LobbySnapshot lobby);
}
