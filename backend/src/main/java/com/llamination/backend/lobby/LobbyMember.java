package com.llamination.backend.lobby;

import java.util.UUID;

/** Mutable team selection belonging to one stable authenticated player. */
final class LobbyMember {

    private final UUID userId;
    private final String username;
    private final boolean creator;
    private TeamChoice teamChoice;
    private Team assignedTeam;

    LobbyMember(UUID userId, String username, boolean creator) {
        this(userId, username, creator, TeamChoice.RANDOM, null);
    }

    LobbyMember(UUID userId, String username, boolean creator, TeamChoice teamChoice, Team assignedTeam) {
        this.userId = userId;
        this.username = username;
        this.creator = creator;
        this.teamChoice = teamChoice;
        this.assignedTeam = assignedTeam;
    }

    UUID userId() {
        return userId;
    }

    String username() {
        return username;
    }

    boolean creator() {
        return creator;
    }

    TeamChoice teamChoice() {
        return teamChoice;
    }

    void setTeamChoice(TeamChoice teamChoice) {
        this.teamChoice = teamChoice;
        this.assignedTeam = null;
    }

    Team assignedTeam() {
        return assignedTeam;
    }

    void setAssignedTeam(Team assignedTeam) {
        this.assignedTeam = assignedTeam;
    }

    void clearAssignedTeam() {
        assignedTeam = null;
    }
}
