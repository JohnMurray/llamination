package com.llamination.backend.lobby;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Transactional
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
    private final ObjectProvider<LobbyService> transactionalSelf;
    private final Map<UUID, Long> presenceVersions = new HashMap<>();

    @Autowired
    public LobbyService(
            LobbyRepository repository,
            LobbyConstraintsProvider constraintsProvider,
            LobbyEventPublisher events,
            LobbyGameStarter gameStarter,
            TaskScheduler taskScheduler,
            ObjectProvider<LobbyService> transactionalSelf,
            @Value("${llamination.lobby.disconnect-grace:5s}") Duration disconnectGrace) {
        this(
                repository,
                constraintsProvider,
                events,
                gameStarter,
                taskScheduler,
                Clock.systemUTC(),
                new SecureRandom(),
                disconnectGrace,
                transactionalSelf);
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
        this(
                repository,
                constraintsProvider,
                events,
                gameStarter,
                taskScheduler,
                clock,
                random,
                disconnectGrace,
                null);
    }

    private LobbyService(
            LobbyRepository repository,
            LobbyConstraintsProvider constraintsProvider,
            LobbyEventPublisher events,
            LobbyGameStarter gameStarter,
            TaskScheduler taskScheduler,
            Clock clock,
            SecureRandom random,
            Duration disconnectGrace,
            ObjectProvider<LobbyService> transactionalSelf) {
        this.repository = repository;
        this.constraintsProvider = constraintsProvider;
        this.events = events;
        this.gameStarter = gameStarter;
        this.taskScheduler = taskScheduler;
        this.clock = clock;
        this.random = random;
        this.disconnectGrace = disconnectGrace;
        this.transactionalSelf = transactionalSelf;
    }

    public LobbySnapshot create(LobbyPlayer player, LobbyVisibility visibility, String rawDescription) {
        LobbySnapshot snapshot;
        synchronized (mutex) {
            ensureNotInLobby(player.userId());
            String description = normalizeDescription(rawDescription);
            String inviteToken = visibility == LobbyVisibility.PRIVATE ? newInviteToken() : null;
            Lobby lobby = new Lobby(
                    UUID.randomUUID(),
                    player,
                    visibility,
                    description,
                    constraintsProvider.currentConstraints(),
                    inviteToken,
                    hashInviteToken(inviteToken),
                    clock.instant());
            saveWithNewMembership(lobby);
            snapshot = snapshot(lobby);
        }
        publishUpdate(snapshot, true);
        return snapshot;
    }

    @Transactional(readOnly = true)
    public List<PublicLobbySummary> browsePublic() {
        synchronized (mutex) {
            return repository.findAll().stream()
                    .filter(this::isPubliclyJoinable)
                    .sorted(Comparator.comparing(lobby -> lobby.createdAt))
                    .map(this::summary)
                    .toList();
        }
    }

    @Transactional(readOnly = true)
    public Optional<LobbySnapshot> currentLobby(UUID userId) {
        synchronized (mutex) {
            return repository.findByPlayer(userId).map(this::snapshot);
        }
    }

    @Transactional(readOnly = true)
    public LobbySnapshot getForMember(UUID lobbyId, UUID userId) {
        synchronized (mutex) {
            Lobby lobby = requireLobby(lobbyId);
            requireMember(lobby, userId);
            return snapshot(lobby);
        }
    }

    public LobbySnapshot joinPublic(UUID lobbyId, LobbyPlayer player) {
        synchronized (mutex) {
            Lobby lobby = requireLobbyForUpdate(lobbyId);
            if (lobby.visibility != LobbyVisibility.PUBLIC) {
                throw error(LobbyError.LOBBY_NOT_FOUND, "Lobby not found");
            }
        }
        return join(player, lobbyId);
    }

    public LobbySnapshot joinPrivate(String inviteToken, LobbyPlayer player) {
        Lobby lobby;
        synchronized (mutex) {
            lobby = repository.findByInviteTokenHash(hashInviteToken(inviteToken))
                    .orElseThrow(() -> error(LobbyError.LOBBY_NOT_FOUND, "Invite is invalid or expired"));
        }
        return join(player, lobby.id);
    }

    public LobbySnapshot autoJoin(LobbyPlayer player) {
        Lobby lobby;
        synchronized (mutex) {
            ensureNotInLobby(player.userId());
            List<Lobby> candidates = repository.findAll().stream()
                    .filter(this::isPubliclyJoinable)
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            if (candidates.isEmpty()) {
                throw error(LobbyError.LOBBY_NOT_FOUND, "No public lobby is currently available");
            }
            Collections.shuffle(candidates, random);
            lobby = candidates.getFirst();
        }
        return join(player, lobby.id);
    }

    public LobbySnapshot chooseTeam(UUID lobbyId, LobbyPlayer player, TeamChoice choice) {
        LobbySnapshot snapshot;
        synchronized (mutex) {
            Lobby lobby = requireLobbyForUpdate(lobbyId);
            LobbyMember member = requireMember(lobby, player.userId());
            requireWaiting(lobby);
            if (member.teamChoice() != choice
                    && choice != TeamChoice.RANDOM
                    && countChosen(lobby, choice) >= lobby.constraints.maxPlayersPerTeam()) {
                throw error(LobbyError.TEAM_FULL, "That team is already full");
            }
            member.setTeamChoice(choice);
            lobby.changed();
            repository.save(lobby);
            snapshot = snapshot(lobby);
        }
        afterCommit(() -> events.lobbyUpdated(snapshot));
        return snapshot;
    }

    public LobbySnapshot start(UUID lobbyId, LobbyPlayer player) {
        CountdownStart countdown;
        synchronized (mutex) {
            Lobby lobby = requireLobbyForUpdate(lobbyId);
            requireMember(lobby, player.userId());
            if (!lobby.creatorUserId.equals(player.userId())) {
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

    public void leave(UUID lobbyId, LobbyPlayer player) {
        LeaveResult result;
        synchronized (mutex) {
            Lobby lobby = requireLobbyForUpdate(lobbyId);
            requireMember(lobby, player.userId());
            result = leaveLocked(lobby, player);
        }
        publishLeave(result);
    }

    public void leaveIfPresent(LobbyPlayer player) {
        LeaveResult result;
        synchronized (mutex) {
            Optional<Lobby> lobby = repository.findByPlayer(player.userId());
            if (lobby.isEmpty()) {
                return;
            }
            result = leaveLocked(requireLobbyForUpdate(lobby.get().id), player);
        }
        publishLeave(result);
    }

    public void playerConnected(UUID userId) {
        synchronized (mutex) {
            presenceVersions.merge(userId, 1L, Long::sum);
        }
    }

    public void playerDisconnected(LobbyPlayer player) {
        long expectedPresenceVersion;
        synchronized (mutex) {
            if (repository.findByPlayer(player.userId()).isEmpty()) {
                return;
            }
            expectedPresenceVersion = presenceVersions.merge(player.userId(), 1L, Long::sum);
        }
        var scheduledTask = taskScheduler.schedule(
                () -> transactionalService().expireDisconnectedPlayer(player, expectedPresenceVersion),
                clock.instant().plus(disconnectGrace));
        if (scheduledTask == null) {
            throw new IllegalStateException("Unable to schedule disconnected player cleanup");
        }
    }

    public void expireDisconnectedPlayer(LobbyPlayer player, long expectedPresenceVersion) {
        synchronized (mutex) {
            if (!presenceVersions.getOrDefault(player.userId(), 0L).equals(expectedPresenceVersion)) {
                return;
            }
        }
        leaveIfPresent(player);
    }

    private LobbySnapshot join(LobbyPlayer player, UUID lobbyId) {
        LobbySnapshot snapshot;
        CountdownStart countdown = null;
        synchronized (mutex) {
            ensureNotInLobby(player.userId());
            Lobby lobby = requireLobbyForUpdate(lobbyId);
            if (lobby.state != LobbyState.WAITING) {
                throw error(LobbyError.LOBBY_NOT_JOINABLE, "Lobby is no longer joinable");
            }
            if (lobby.members.size() >= lobby.constraints.maxPlayers()) {
                throw error(LobbyError.LOBBY_FULL, "Lobby is full");
            }
            lobby.members.put(player.userId(), new LobbyMember(player.userId(), player.username(), false));
            lobby.changed();
            saveWithNewMembership(lobby);
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

    private CountdownStart beginCountdown(Lobby lobby) {
        resolveTeams(lobby);
        lobby.state = LobbyState.COUNTDOWN;
        lobby.countdownEndsAt = clock.instant().plus(COUNTDOWN_DURATION);
        lobby.changed();
        repository.save(lobby);
        return new CountdownStart(snapshot(lobby), lobby.version);
    }

    private void scheduleCountdown(CountdownStart countdown) {
        afterCommit(() -> scheduleCountdownTask(countdown));
    }

    private void scheduleCountdownTask(CountdownStart countdown) {
        var scheduledTask = taskScheduler.schedule(
                () -> transactionalService().finishCountdown(countdown.snapshot().id(), countdown.version()),
                countdown.snapshot().countdownEndsAt());
        if (scheduledTask == null) {
            throw new IllegalStateException("Unable to schedule lobby countdown");
        }
    }

    public void finishCountdown(UUID lobbyId, long expectedVersion) {
        LobbySnapshot startingSnapshot;
        synchronized (mutex) {
            Optional<Lobby> found = repository.findByIdForUpdate(lobbyId);
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
            repository.save(lobby);
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
            Lobby lobby = requireLobbyForUpdate(lobbyId);
            if (lobby.state != LobbyState.STARTING) {
                return;
            }
            lobby.state = LobbyState.STARTED;
            lobby.gameId = gameId;
            lobby.changed();
            repository.save(lobby);
            startedSnapshot = snapshot(lobby);
        }
        afterCommit(() -> {
            events.lobbyUpdated(startedSnapshot);
            events.gameStarted(startedSnapshot);
        });
    }

    private void restoreAfterStartFailure(UUID lobbyId) {
        LobbySnapshot snapshot;
        synchronized (mutex) {
            Lobby lobby = requireLobbyForUpdate(lobbyId);
            if (lobby.state != LobbyState.STARTING) {
                return;
            }
            lobby.state = LobbyState.WAITING;
            lobby.members.values().forEach(LobbyMember::clearAssignedTeam);
            lobby.changed();
            repository.save(lobby);
            snapshot = snapshot(lobby);
        }
        publishUpdate(snapshot, true);
    }

    private LeaveResult leaveLocked(Lobby lobby, LobbyPlayer player) {
        List<String> affectedMembers = lobby.members.values().stream().map(LobbyMember::username).toList();
        if (lobby.creatorUserId.equals(player.userId())) {
            lobby.state = LobbyState.CLOSED;
            lobby.changed();
            repository.delete(lobby);
            lobby.members.keySet().forEach(presenceVersions::remove);
            return new LeaveResult(null, lobby.id, affectedMembers, true);
        }

        lobby.members.remove(player.userId());
        repository.removePlayer(player.userId());
        presenceVersions.remove(player.userId());
        if (lobby.state == LobbyState.COUNTDOWN || lobby.state == LobbyState.STARTING) {
            lobby.state = LobbyState.WAITING;
            lobby.countdownEndsAt = null;
            lobby.members.values().forEach(LobbyMember::clearAssignedTeam);
        }
        lobby.changed();
        repository.save(lobby);
        return new LeaveResult(snapshot(lobby), lobby.id, List.of(player.username()), false);
    }

    private void publishLeave(LeaveResult result) {
        afterCommit(() -> {
            if (result.closed()) {
                events.directoryChanged();
                events.lobbyClosed(result.lobbyId(), result.affectedMembers(), "CREATOR_LEFT");
            } else if (result.snapshot() != null) {
                publishUpdate(result.snapshot(), true);
            }
        });
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

    private void ensureNotInLobby(UUID userId) {
        if (repository.findByPlayer(userId).isPresent()) {
            throw error(LobbyError.ALREADY_IN_LOBBY, "Player is already in a lobby");
        }
    }

    private void saveWithNewMembership(Lobby lobby) {
        try {
            repository.save(lobby);
        } catch (DataIntegrityViolationException exception) {
            throw error(LobbyError.ALREADY_IN_LOBBY, "Player is already in a lobby");
        }
    }

    private Lobby requireLobby(UUID lobbyId) {
        return repository.findById(lobbyId)
                .orElseThrow(() -> error(LobbyError.LOBBY_NOT_FOUND, "Lobby not found"));
    }

    private Lobby requireLobbyForUpdate(UUID lobbyId) {
        return repository.findByIdForUpdate(lobbyId)
                .orElseThrow(() -> error(LobbyError.LOBBY_NOT_FOUND, "Lobby not found"));
    }

    private LobbyMember requireMember(Lobby lobby, UUID userId) {
        LobbyMember member = lobby.members.get(userId);
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

    private static String hashInviteToken(String inviteToken) {
        if (inviteToken == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(inviteToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
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
        afterCommit(() -> {
            if (directoryChanged) {
                events.directoryChanged();
            }
            events.lobbyUpdated(snapshot);
        });
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private LobbyService transactionalService() {
        return transactionalSelf == null ? this : transactionalSelf.getObject();
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
