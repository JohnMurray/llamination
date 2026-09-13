package com.llamination.backend.lobby;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class Lobby {

    final UUID id;
    final String creatorUsername;
    final LobbyVisibility visibility;
    final String description;
    final LobbyConstraints constraints;
    final String inviteToken;
    final Instant createdAt;
    final Map<String, LobbyMember> members = new LinkedHashMap<>();

    LobbyState state = LobbyState.WAITING;
    Instant countdownEndsAt;
    UUID gameId;
    long version = 1;

    Lobby(
            UUID id,
            String creatorUsername,
            LobbyVisibility visibility,
            String description,
            LobbyConstraints constraints,
            String inviteToken,
            Instant createdAt) {
        this.id = id;
        this.creatorUsername = creatorUsername;
        this.visibility = visibility;
        this.description = description;
        this.constraints = constraints;
        this.inviteToken = inviteToken;
        this.createdAt = createdAt;
        members.put(creatorUsername, new LobbyMember(creatorUsername, true));
    }

    void changed() {
        version++;
    }
}
