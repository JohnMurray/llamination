package com.llamination.backend.lobby;

import java.time.Instant;
import java.util.UUID;

public record PublicLobbySummary(
        UUID id,
        String creatorUsername,
        String description,
        String mapId,
        int currentPlayers,
        int minPlayers,
        int maxPlayers,
        Instant createdAt) {
}
