package com.llamination.backend.lobby;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;

class LobbyServiceTests {

    private static final Instant NOW = Instant.parse("2026-09-06T12:00:00Z");

    private InMemoryLobbyRepository repository;
    private RecordingEvents events;
    private RecordingGameStarter gameStarter;
    private LobbyService service;
    private List<Runnable> scheduledTasks;

    @BeforeEach
    void setUp() {
        repository = new InMemoryLobbyRepository();
        events = new RecordingEvents();
        gameStarter = new RecordingGameStarter();
        scheduledTasks = new ArrayList<>();
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
        doAnswer(invocation -> {
                    scheduledTasks.add(invocation.getArgument(0));
                    return scheduledFuture;
                })
                .when(scheduler)
                .schedule(any(Runnable.class), any(Instant.class));
        service = new LobbyService(
                repository,
                () -> new LobbyConstraints("placeholder-map", 2, 6, 2, 3),
                events,
                gameStarter,
                scheduler,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SecureRandom(new byte[] {1, 2, 3, 4}),
                java.time.Duration.ofSeconds(5));
    }

    @Test
    void publicLobbyCanBeBrowsedWhilePrivateLobbyCannot() {
        LobbySnapshot publicLobby = service.create("public-owner", LobbyVisibility.PUBLIC, "  Friendly match  ");

        assertThat(service.browsePublic()).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo(publicLobby.id());
            assertThat(summary.description()).isEqualTo("Friendly match");
            assertThat(summary.minPlayers()).isEqualTo(2);
            assertThat(summary.maxPlayers()).isEqualTo(6);
        });

        LobbySnapshot privateLobby = service.create("private-owner", LobbyVisibility.PRIVATE, "Invite only");
        assertThat(privateLobby.inviteToken()).isNotBlank();
        assertThat(service.browsePublic()).extracting(PublicLobbySummary::id)
                .containsExactly(publicLobby.id());
    }

    @Test
    void fullLobbyStartsTenSecondCountdownAndRejectsMorePlayers() {
        LobbySnapshot lobby = service.create("owner", LobbyVisibility.PUBLIC, "Six players");
        for (int index = 1; index <= 5; index++) {
            lobby = service.joinPublic(lobby.id(), "player-" + index);
        }

        assertThat(lobby.state()).isEqualTo(LobbyState.COUNTDOWN);
        assertThat(lobby.countdownEndsAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(lobby.members()).hasSize(6);
        assertThat(service.browsePublic()).isEmpty();
        UUID lobbyId = lobby.id();
        assertThatThrownBy(() -> service.joinPublic(lobbyId, "too-late"))
                .isInstanceOfSatisfying(LobbyException.class,
                        exception -> assertThat(exception.error()).isEqualTo(LobbyError.LOBBY_NOT_JOINABLE));
    }

    @Test
    void creatorStartUsesCountdownAndResolvedTeamsAreBalanced() {
        LobbySnapshot lobby = service.create("owner", LobbyVisibility.PUBLIC, "Start early");
        service.joinPublic(lobby.id(), "guest");

        LobbySnapshot countdown = service.start(lobby.id(), "owner");

        assertThat(countdown.state()).isEqualTo(LobbyState.COUNTDOWN);
        assertThat(countdown.countdownEndsAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(countdown.members()).extracting(LobbySnapshot.Member::assignedTeam)
                .containsExactlyInAnyOrder(Team.TEAM_ONE, Team.TEAM_TWO);
    }

    @Test
    void nonCreatorLeavingCancelsCountdownButCreatorLeavingClosesLobby() {
        LobbySnapshot lobby = service.create("owner", LobbyVisibility.PUBLIC, "Lifecycle");
        service.joinPublic(lobby.id(), "guest");
        LobbySnapshot countdown = service.start(lobby.id(), "owner");

        service.leave(lobby.id(), "guest");

        LobbySnapshot waiting = service.getForMember(lobby.id(), "owner");
        assertThat(waiting.state()).isEqualTo(LobbyState.WAITING);
        assertThat(waiting.countdownEndsAt()).isNull();
        assertThat(waiting.members()).singleElement().satisfies(member -> assertThat(member.assignedTeam()).isNull());
        service.leave(lobby.id(), "owner");
        assertThat(service.currentLobby("owner")).isEmpty();
        assertThat(events.closedLobbyIds).containsExactly(countdown.id());
    }

    @Test
    void countdownCreatesOnlyOneGame() {
        LobbySnapshot lobby = service.create("owner", LobbyVisibility.PUBLIC, "Exactly once");
        service.joinPublic(lobby.id(), "guest");
        LobbySnapshot countdown = service.start(lobby.id(), "owner");

        service.finishCountdown(lobby.id(), countdown.version());
        service.finishCountdown(lobby.id(), countdown.version());

        LobbySnapshot started = service.getForMember(lobby.id(), "owner");
        assertThat(started.state()).isEqualTo(LobbyState.STARTED);
        assertThat(started.gameId()).isEqualTo(gameStarter.gameId);
        assertThat(gameStarter.starts).hasValue(1);
    }

    @Test
    void teamChoiceHonorsPerTeamCapacity() {
        LobbySnapshot lobby = service.create("owner", LobbyVisibility.PUBLIC, "Team limits");
        service.chooseTeam(lobby.id(), "owner", TeamChoice.TEAM_ONE);
        for (int index = 1; index <= 3; index++) {
            service.joinPublic(lobby.id(), "player-" + index);
        }
        service.chooseTeam(lobby.id(), "player-1", TeamChoice.TEAM_ONE);
        service.chooseTeam(lobby.id(), "player-2", TeamChoice.TEAM_ONE);

        UUID lobbyId = lobby.id();
        assertThatThrownBy(() -> service.chooseTeam(lobbyId, "player-3", TeamChoice.TEAM_ONE))
                .isInstanceOfSatisfying(LobbyException.class,
                        exception -> assertThat(exception.error()).isEqualTo(LobbyError.TEAM_FULL));
    }

    @Test
    void concurrentJoinsNeverExceedCapacity() throws Exception {
        LobbySnapshot lobby = service.create("owner", LobbyVisibility.PUBLIC, "Race safe");
        int contenders = 12;
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger joined = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(contenders)) {
            List<Future<?>> attempts = new ArrayList<>();
            for (int index = 0; index < contenders; index++) {
                String username = "contender-" + index;
                attempts.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        go.await(5, TimeUnit.SECONDS);
                        service.joinPublic(lobby.id(), username);
                        joined.incrementAndGet();
                    } catch (LobbyException ignored) {
                        // Expected once capacity is reached.
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            for (Future<?> attempt : attempts) {
                attempt.get(5, TimeUnit.SECONDS);
            }
        }

        assertThat(joined).hasValue(5);
        assertThat(service.getForMember(lobby.id(), "owner").members()).hasSize(6);
    }

    @Test
    void autoJoinUsesOnlyPublicLobbies() {
        service.create("private-owner", LobbyVisibility.PRIVATE, "Hidden");
        LobbySnapshot expected = service.create("public-owner", LobbyVisibility.PUBLIC, "Visible");

        LobbySnapshot joined = service.autoJoin("guest");

        assertThat(joined.id()).isEqualTo(expected.id());
        assertThat(joined.members()).extracting(LobbySnapshot.Member::username).contains("guest");
    }

    @Test
    void reconnectDuringGracePeriodPreservesCreatorLobby() {
        LobbySnapshot lobby = service.create("owner", LobbyVisibility.PUBLIC, "Reconnect");
        service.playerConnected("owner");
        service.playerDisconnected("owner");
        Runnable expiry = scheduledTasks.getLast();

        service.playerConnected("owner");
        expiry.run();

        assertThat(service.getForMember(lobby.id(), "owner").state()).isEqualTo(LobbyState.WAITING);
    }

    @Test
    void creatorLobbyClosesAfterDisconnectGraceExpires() {
        LobbySnapshot lobby = service.create("owner", LobbyVisibility.PUBLIC, "Disconnect");
        service.playerConnected("owner");
        service.playerDisconnected("owner");

        scheduledTasks.getLast().run();

        assertThat(service.currentLobby("owner")).isEmpty();
        assertThat(events.closedLobbyIds).containsExactly(lobby.id());
    }

    private static final class RecordingEvents implements LobbyEventPublisher {
        private final List<UUID> closedLobbyIds = new ArrayList<>();

        @Override
        public void directoryChanged() {
        }

        @Override
        public void lobbyUpdated(LobbySnapshot lobby) {
        }

        @Override
        public void lobbyClosed(UUID lobbyId, Collection<String> usernames, String reason) {
            closedLobbyIds.add(lobbyId);
        }

        @Override
        public void gameStarted(LobbySnapshot lobby) {
        }
    }

    private static final class RecordingGameStarter implements LobbyGameStarter {
        private final UUID gameId = UUID.fromString("16c087b3-85b4-4514-8c87-e4fd2ed063ab");
        private final AtomicInteger starts = new AtomicInteger();

        @Override
        public UUID startGame(LobbySnapshot lobby) {
            starts.incrementAndGet();
            return gameId;
        }
    }
}
