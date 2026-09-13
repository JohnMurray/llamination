package com.llamination.backend.lobby;

public record LobbyConstraints(
        String mapId,
        int minPlayers,
        int maxPlayers,
        int teamCount,
        int maxPlayersPerTeam) {

    public LobbyConstraints {
        if (mapId == null || mapId.isBlank()) {
            throw new IllegalArgumentException("mapId is required");
        }
        if (minPlayers < 2 || maxPlayers < minPlayers) {
            throw new IllegalArgumentException("Invalid player constraints");
        }
        if (teamCount != 2 || maxPlayersPerTeam * teamCount < maxPlayers) {
            throw new IllegalArgumentException("Invalid team constraints");
        }
    }
}
