package com.llamination.backend.lobby;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LobbyService {

    public static final Duration COUNTDOWN_DURATION = Duration.ofSeconds(10);
    public static final int MAX_DESCRIPTION_LENGTH = 160;

    private final Object mutex = new Object();
    private final LobbyRepository repository;
    private final LobbyConstraintsProvider constraintsProvider;
    private final LobbyEventPublisher events;
    private final LobbyGameStarter gameStarter;
    private final TaskScheduler taskScheduler;
    private final Clock clock;
    private final SecureRandom random;
    private final Duration disconnectGrace;
    private final Map<String, Long> presenceVersions = new HashMap<>();

    @Autowired
    public LobbyService(
            LobbyRepository repository,
            LobbyConstraintsProvider constraintsProvider,
            LobbyEventPublisher events,
            LobbyGameStarter gameStarter,
            TaskScheduler taskScheduler,
            @Value("${llamination.lobby.disconnect-grace:5s}") Duration disconnectGrace) {
        this(
                repository,
                constraintsProvider,
                events,
                gameStarter,
                taskScheduler,
                Clock.systemUTC(),
                new SecureRandom(),
                disconnectGrace);
    }

    LobbyService(
            LobbyRepository repository,
            LobbyConstraintsProvider constraintsProvider,
            LobbyEventPublisher events,
            LobbyGameStarter gameStarter,
            TaskScheduler taskScheduler,
            Clock clock,
            SecureRandom random,
            Duration disconnectGrace) {
        this.repository = repository;
        this.constraintsProvider = constraintsProvider;
        this.events = events;
        this.gameStarter = gameStarter;
        this.taskScheduler = taskScheduler;
        this.clock = clock;
        this.random = random;
        this.disconnectGrace = disconnectGrace;
    }

    public LobbySnapshot create(String username, LobbyVisibility visibility, String rawDescription) {
        LobbySnapshot snapshot;
        synchronized (mutex) {
            ensureNotInLobby(username);
            String description = normalizeDescription(rawDescription);
            String inviteToken = visibility == LobbyVisibility.PRIVATE ? newInviteToken() : null;
            Lobby lobby = new Lobby(
                    UUID.randomUUID(),
                    username,
                    visibility,
                    description,
                    constraintsProvider.currentConstraints(),
                    inviteToken,
                    clock.instant());
            repository.save(lobby);
            snapshot = snapshot(lobby);
        }
        publishUpdate(snapshot, true);
        return snapshot;
    }

    public List<PublicLobbySummary> browsePublic() {
        synchronized (mutex) {
            return repository.findAll().stream()
                    .filter(this::isPubliclyJoinable)
                    .sorted(Comparator.comparing(lobby -> lobby.createdAt))
                    .map(this::summary)
                    .toList();
        }
    }

    public Optional<LobbySnapshot> currentLobby(String username) {
        synchronized (mutex) {
            return repository.findByPlayer(username).map(this::snapshot);
        }
    }

    public LobbySnapshot getForMember(UUID lobbyId, String username) {
        synchronized (mutex) {
            Lobby lobby = requireLobby(lobbyId);
            requireMember(lobby, username);
            return snapshot(lobby);
        }
    }

    public LobbySnapshot joinPublic(UUID lobbyId, String username) {
        return join(username, findPublicLobby(lobbyId));
    }

    public LobbySnapshot joinPrivate(String inviteToken, String username) {
        Lobby lobby;
        synchronized (mutex) {
            lobby = repository.findByInviteToken(inviteToken)
                    .orElseThrow(() -> error(LobbyError.LOBBY_NOT_FOUND, "Invite is invalid or expired"));
        }
        return join(username, lobby);
    }

    public LobbySnapshot autoJoin(String username) {
        Lobby lobby;
        synchronized (mutex) {
            ensureNotInLobby(username);
            List<Lobby> candidates = repository.findAll().stream()
                    .filter(this::isPubliclyJoinable)
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            if (candidates.isEmpty()) {
                throw error(LobbyError.LOBBY_NOT_FOUND, "No public lobby is currently available");
            }
            Collections.shuffle(candidates, random);
            lobby = candidates.getFirst();
        }
        return join(username, lobby);
    }

    public LobbySnapshot chooseTeam(UUID lobbyId, String username, TeamChoice choice) {
        LobbySnapshot snapshot;
        synchronized (mutex) {
            Lobby lobby = requireLobby(lobbyId);
            LobbyMember member = requireMember(lobby, username);
            requireWaiting(lobby);
            if (member.teamChoice() != choice
                    && choice != TeamChoice.RANDOM
                    && countChosen(lobby, choice) >= lobby.constraints.maxPlayersPerTeam()) {
                throw error(LobbyError.TEAM_FULL, "That team is already full");
            }
            member.setTeamChoice(choice);
            lobby.changed();
            snapshot = snapshot(lobby);
        }
        events.lobbyUpdated(snapshot);
        return snapshot;
    }

    public LobbySnapshot start(UUID lobbyId, String username) {
        CountdownStart countdown;
        synchronized (mutex) {
            Lobby lobby = requireLobby(lobbyId);
            requireMember(lobby, username);
            if (!lobby.creatorUsername.equals(username)) {
                throw error(LobbyError.NOT_CREATOR, "Only the lobby creator can start the game");
            }
            requireWaiting(lobby);
            if (lobby.members.size() < lobby.constraints.minPlayers()) {
                throw error(LobbyError.MINIMUM_NOT_REACHED, "Not enough players to start");
            }
            countdown = beginCountdown(lobby);
        }
        publishUpdate(countdown.snapshot(), true);
        scheduleCountdown(countdown);
        return countdown.snapshot();
    }

    public void leave(UUID lobbyId, String username) {
        LeaveResult result;
        synchronized (mutex) {
            Lobby lobby = requireLobby(lobbyId);
            requireMember(lobby, username);
            result = leaveLocked(lobby, username);
        }
        publishLeave(result);
    }

    public void leaveIfPresent(String username) {
        LeaveResult result;
        synchronized (mutex) {
            Optional<Lobby> lobby = repository.findByPlayer(username);
            if (lobby.isEmpty()) {
                return;
            }
            result = leaveLocked(lobby.get(), username);
        }
        publishLeave(result);
    }

    public void playerConnected(String username) {
        synchronized (mutex) {
            presenceVersions.merge(username, 1L, Long::sum);
        }
    }

    public void playerDisconnected(String username) {
        long expectedPresenceVersion;
        synchronized (mutex) {
            if (repository.findByPlayer(username).isEmpty()) {
                return;
            }
            expectedPresenceVersion = presenceVersions.merge(username, 1L, Long::sum);
        }
        var scheduledTask = taskScheduler.schedule(
                () -> expireDisconnectedPlayer(username, expectedPresenceVersion),
                clock.instant().plus(disconnectGrace));
        if (scheduledTask == null) {
            throw new IllegalStateException("Unable to schedule disconnected player cleanup");
        }
    }

    void expireDisconnectedPlayer(String username, long expectedPresenceVersion) {
        synchronized (mutex) {
            if (!presenceVersions.getOrDefault(username, 0L).equals(expectedPresenceVersion)) {
                return;
            }
        }
        leaveIfPresent(username);
    }

    private LobbySnapshot join(String username, Lobby expectedLobby) {
        LobbySnapshot snapshot;
        CountdownStart countdown = null;
        synchronized (mutex) {
            ensureNotInLobby(username);
            Lobby lobby = requireLobby(expectedLobby.id);
            if (lobby != expectedLobby || lobby.state != LobbyState.WAITING) {
                throw error(LobbyError.LOBBY_NOT_JOINABLE, "Lobby is no longer joinable");
            }
            if (lobby.members.size() >= lobby.constraints.maxPlayers()) {
                throw error(LobbyError.LOBBY_FULL, "Lobby is full");
            }
            lobby.members.put(username, new LobbyMember(username, false));
            lobby.changed();
            repository.save(lobby);
            if (lobby.members.size() == lobby.constraints.maxPlayers()) {
                countdown = beginCountdown(lobby);
                snapshot = countdown.snapshot();
            } else {
                snapshot = snapshot(lobby);
            }
        }
        publishUpdate(snapshot, true);
        if (countdown != null) {
            scheduleCountdown(countdown);
        }
        return snapshot;
    }

    private Lobby findPublicLobby(UUID lobbyId) {
        synchronized (mutex) {
            Lobby lobby = requireLobby(lobbyId);
            if (lobby.visibility != LobbyVisibility.PUBLIC) {
                throw error(LobbyError.LOBBY_NOT_FOUND, "Lobby not found");
            }
            return lobby;
        }
    }

    private CountdownStart beginCountdown(Lobby lobby) {
        resolveTeams(lobby);
        lobby.state = LobbyState.COUNTDOWN;
        lobby.countdownEndsAt = clock.instant().plus(COUNTDOWN_DURATION);
        lobby.changed();
        return new CountdownStart(snapshot(lobby), lobby.version);
    }

    private void scheduleCountdown(CountdownStart countdown) {
        var scheduledTask = taskScheduler.schedule(
                () -> finishCountdown(countdown.snapshot().id(), countdown.version()),
                countdown.snapshot().countdownEndsAt());
        if (scheduledTask == null) {
            throw new IllegalStateException("Unable to schedule lobby countdown");
        }
    }

    void finishCountdown(UUID lobbyId, long expectedVersion) {
        LobbySnapshot startingSnapshot;
        synchronized (mutex) {
            Optional<Lobby> found = repository.findById(lobbyId);
            if (found.isEmpty()) {
                return;
            }
            Lobby lobby = found.get();
            if (lobby.state != LobbyState.COUNTDOWN || lobby.version != expectedVersion) {
                return;
            }
            lobby.state = LobbyState.STARTING;
            lobby.countdownEndsAt = null;
            lobby.changed();
            startingSnapshot = snapshot(lobby);
        }
        publishUpdate(startingSnapshot, true);

        UUID gameId;
        try {
            gameId = gameStarter.startGame(startingSnapshot);
        } catch (RuntimeException exception) {
            restoreAfterStartFailure(lobbyId);
            return;
        }

        LobbySnapshot startedSnapshot;
        synchronized (mutex) {
            Lobby lobby = requireLobby(lobbyId);
            if (lobby.state != LobbyState.STARTING) {
                return;
            }
            lobby.state = LobbyState.STARTED;
            lobby.gameId = gameId;
            lobby.changed();
            startedSnapshot = snapshot(lobby);
        }
        events.lobbyUpdated(startedSnapshot);
        events.gameStarted(startedSnapshot);
    }

    private void restoreAfterStartFailure(UUID lobbyId) {
        LobbySnapshot snapshot;
        synchronized (mutex) {
            Lobby lobby = requireLobby(lobbyId);
            if (lobby.state != LobbyState.STARTING) {
                return;
            }
            lobby.state = LobbyState.WAITING;
            lobby.members.values().forEach(LobbyMember::clearAssignedTeam);
            lobby.changed();
            snapshot = snapshot(lobby);
        }
        publishUpdate(snapshot, true);
    }

    private LeaveResult leaveLocked(Lobby lobby, String username) {
        List<String> affectedMembers = List.copyOf(lobby.members.keySet());
        if (lobby.creatorUsername.equals(username)) {
            lobby.state = LobbyState.CLOSED;
            lobby.changed();
            repository.delete(lobby);
            affectedMembers.forEach(presenceVersions::remove);
            return new LeaveResult(null, lobby.id, affectedMembers, true);
        }

        lobby.members.remove(username);
        repository.removePlayer(username);
        presenceVersions.remove(username);
        if (lobby.state == LobbyState.COUNTDOWN || lobby.state == LobbyState.STARTING) {
            lobby.state = LobbyState.WAITING;
            lobby.countdownEndsAt = null;
            lobby.members.values().forEach(LobbyMember::clearAssignedTeam);
        }
        lobby.changed();
        repository.save(lobby);
        return new LeaveResult(snapshot(lobby), lobby.id, List.of(username), false);
    }

    private void publishLeave(LeaveResult result) {
        if (result.closed()) {
            events.directoryChanged();
            events.lobbyClosed(result.lobbyId(), result.affectedMembers(), "CREATOR_LEFT");
        } else if (result.snapshot() != null) {
            publishUpdate(result.snapshot(), true);
        }
    }

    private void resolveTeams(Lobby lobby) {
        int teamOne = 0;
        int teamTwo = 0;
        List<LobbyMember> randomMembers = new ArrayList<>();
        for (LobbyMember member : lobby.members.values()) {
            switch (member.teamChoice()) {
                case TEAM_ONE -> {
                    member.setAssignedTeam(Team.TEAM_ONE);
                    teamOne++;
                }
                case TEAM_TWO -> {
                    member.setAssignedTeam(Team.TEAM_TWO);
                    teamTwo++;
                }
                case RANDOM -> randomMembers.add(member);
            }
        }
        if (teamOne > lobby.constraints.maxPlayersPerTeam()
                || teamTwo > lobby.constraints.maxPlayersPerTeam()) {
            throw error(LobbyError.INVALID_TEAM_ASSIGNMENT, "A team has too many players");
        }
        Collections.shuffle(randomMembers, random);
        for (LobbyMember member : randomMembers) {
            Team assigned;
            if (teamOne >= lobby.constraints.maxPlayersPerTeam()) {
                assigned = Team.TEAM_TWO;
            } else if (teamTwo >= lobby.constraints.maxPlayersPerTeam()) {
                assigned = Team.TEAM_ONE;
            } else {
                assigned = teamOne <= teamTwo ? Team.TEAM_ONE : Team.TEAM_TWO;
            }
            member.setAssignedTeam(assigned);
            if (assigned == Team.TEAM_ONE) {
                teamOne++;
            } else {
                teamTwo++;
            }
        }
        if (teamOne == 0 || teamTwo == 0) {
            lobby.members.values().forEach(LobbyMember::clearAssignedTeam);
            throw error(LobbyError.INVALID_TEAM_ASSIGNMENT, "Both teams need at least one player");
        }
    }

    private int countChosen(Lobby lobby, TeamChoice choice) {
        return (int) lobby.members.values().stream()
                .filter(member -> member.teamChoice() == choice)
                .count();
    }

    private boolean isPubliclyJoinable(Lobby lobby) {
        return lobby.visibility == LobbyVisibility.PUBLIC
                && lobby.state == LobbyState.WAITING
                && lobby.members.size() < lobby.constraints.maxPlayers();
    }

    private void requireWaiting(Lobby lobby) {
        if (lobby.state != LobbyState.WAITING) {
            throw error(LobbyError.LOBBY_NOT_JOINABLE, "Lobby is no longer accepting changes");
        }
    }

    private void ensureNotInLobby(String username) {
        if (repository.findByPlayer(username).isPresent()) {
            throw error(LobbyError.ALREADY_IN_LOBBY, "Player is already in a lobby");
        }
    }

    private Lobby requireLobby(UUID lobbyId) {
        return repository.findById(lobbyId)
                .orElseThrow(() -> error(LobbyError.LOBBY_NOT_FOUND, "Lobby not found"));
    }

    private LobbyMember requireMember(Lobby lobby, String username) {
        LobbyMember member = lobby.members.get(username);
        if (member == null) {
            throw error(LobbyError.NOT_A_MEMBER, "Player is not a member of this lobby");
        }
        return member;
    }

    private String normalizeDescription(String rawDescription) {
        String description = rawDescription == null ? "" : rawDescription.strip();
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw error(
                    LobbyError.INVALID_DESCRIPTION,
                    "Description cannot exceed " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        return description;
    }

    private String newInviteToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private PublicLobbySummary summary(Lobby lobby) {
        return new PublicLobbySummary(
                lobby.id,
                lobby.creatorUsername,
                lobby.description,
                lobby.constraints.mapId(),
                lobby.members.size(),
                lobby.constraints.minPlayers(),
                lobby.constraints.maxPlayers(),
                lobby.createdAt);
    }

    private LobbySnapshot snapshot(Lobby lobby) {
        List<LobbySnapshot.Member> members = lobby.members.values().stream()
                .map(member -> new LobbySnapshot.Member(
                        member.username(), member.creator(), member.teamChoice(), member.assignedTeam()))
                .toList();
        return new LobbySnapshot(
                lobby.id,
                lobby.creatorUsername,
                lobby.visibility,
                lobby.description,
                lobby.constraints.mapId(),
                lobby.constraints.minPlayers(),
                lobby.constraints.maxPlayers(),
                lobby.constraints.maxPlayersPerTeam(),
                lobby.state,
                members,
                lobby.countdownEndsAt,
                lobby.inviteToken,
                lobby.gameId,
                lobby.createdAt,
                lobby.version);
    }

    private void publishUpdate(LobbySnapshot snapshot, boolean directoryChanged) {
        if (directoryChanged) {
            events.directoryChanged();
        }
        events.lobbyUpdated(snapshot);
    }

    private static LobbyException error(LobbyError error, String message) {
        return new LobbyException(error, message);
    }

    private record CountdownStart(LobbySnapshot snapshot, long version) {
    }

    private record LeaveResult(
            LobbySnapshot snapshot,
            UUID lobbyId,
            Collection<String> affectedMembers,
            boolean closed) {
    }
}
