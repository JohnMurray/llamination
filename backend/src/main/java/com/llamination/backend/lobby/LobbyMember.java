package com.llamination.backend.lobby;

final class LobbyMember {

    private final String username;
    private final boolean creator;
    private TeamChoice teamChoice;
    private Team assignedTeam;

    LobbyMember(String username, boolean creator) {
        this.username = username;
        this.creator = creator;
        this.teamChoice = TeamChoice.RANDOM;
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
