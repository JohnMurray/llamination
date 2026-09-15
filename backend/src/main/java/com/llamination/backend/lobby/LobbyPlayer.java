package com.llamination.backend.lobby;

import java.util.Objects;
import java.util.UUID;

/** Stable identity supplied by authentication when a player issues a lobby command. */
public record LobbyPlayer(UUID userId, String username) {

    public LobbyPlayer {
        Objects.requireNonNull(userId, "userId");
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
    }
}

