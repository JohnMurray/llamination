package com.llamination.backend.lobby;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LobbySnapshot(
        UUID id,
        String creatorUsername,
        LobbyVisibility visibility,
        String description,
        String mapId,
        int minPlayers,
        int maxPlayers,
        int maxPlayersPerTeam,
        LobbyState state,
        List<Member> members,
        Instant countdownEndsAt,
        String inviteToken,
        UUID gameId,
        Instant createdAt,
        long version) {

    public record Member(
            String username, boolean creator, TeamChoice teamChoice, Team assignedTeam) {
    }
}
