package com.llamination.backend.lobby;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Active lobby aggregate persisted as one header and a small member collection. */
final class Lobby {

    final UUID id;
    final UUID creatorUserId;
    final String creatorUsername;
    final LobbyVisibility visibility;
    final String description;
    final LobbyConstraints constraints;
    final String inviteToken;
    final String inviteTokenHash;
    final Instant createdAt;
    final Map<UUID, LobbyMember> members;

    LobbyState state = LobbyState.WAITING;
    Instant countdownEndsAt;
    UUID gameId;
    long version = 1;

    Lobby(
            UUID id,
            LobbyPlayer creator,
            LobbyVisibility visibility,
            String description,
            LobbyConstraints constraints,
            String inviteToken,
            String inviteTokenHash,
            Instant createdAt) {
        this(
                id,
                creator.userId(),
                creator.username(),
                visibility,
                description,
                constraints,
                inviteToken,
                inviteTokenHash,
                createdAt,
                LobbyState.WAITING,
                null,
                null,
                1,
                new LinkedHashMap<>());
        members.put(creator.userId(), new LobbyMember(creator.userId(), creator.username(), true));
    }

    Lobby(
            UUID id,
            UUID creatorUserId,
            String creatorUsername,
            LobbyVisibility visibility,
            String description,
            LobbyConstraints constraints,
            String inviteToken,
            String inviteTokenHash,
            Instant createdAt,
            LobbyState state,
            Instant countdownEndsAt,
            UUID gameId,
            long version,
            Map<UUID, LobbyMember> members) {
        this.id = id;
        this.creatorUserId = creatorUserId;
        this.creatorUsername = creatorUsername;
        this.visibility = visibility;
        this.description = description;
        this.constraints = constraints;
        this.inviteToken = inviteToken;
        this.inviteTokenHash = inviteTokenHash;
        this.createdAt = createdAt;
        this.state = state;
        this.countdownEndsAt = countdownEndsAt;
        this.gameId = gameId;
        this.version = version;
        this.members = members;
    }

    void changed() {
        version++;
    }
}
