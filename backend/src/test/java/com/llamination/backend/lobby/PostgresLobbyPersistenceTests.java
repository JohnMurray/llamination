package com.llamination.backend.lobby;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.llamination.backend.InfrastructureIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Exercises PostgreSQL reconstruction, secret lookup, and cross-service capacity locking. */
@SpringBootTest
class PostgresLobbyPersistenceTests extends InfrastructureIntegrationTest {

    private static final LobbyPlayer COMMANDER =
            new LobbyPlayer(UUID.fromString("10000000-0000-0000-0000-000000000001"), "commander");
    private static final LobbyPlayer SCOUT =
            new LobbyPlayer(UUID.fromString("10000000-0000-0000-0000-000000000002"), "scout");

    @Autowired
    private LobbyService service;

    @Autowired
    private PostgresLobbyRepository repository;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearLobbies() {
        jdbcClient.sql("DELETE FROM lobbies").update();
        jdbcClient.sql("DELETE FROM users WHERE username LIKE 'database-contender-%'").update();
    }

    @Test
    void privateLobbyRoundTripsAndUsesHashedInviteLookup() {
        LobbySnapshot created = service.create(COMMANDER, LobbyVisibility.PRIVATE, "Secret match");

        String storedToken = jdbcClient
                .sql("SELECT invite_token_hash FROM lobbies WHERE lobby_id = :id")
                .param("id", created.id())
                .query(String.class)
                .single();
        assertThat(storedToken).isNotEqualTo(created.inviteToken()).hasSize(64);

        LobbySnapshot joined = service.joinPrivate(created.inviteToken(), SCOUT);
        assertThat(joined.members()).extracting(LobbySnapshot.Member::username)
                .containsExactly("commander", "scout");
        assertThat(service.currentLobby(COMMANDER.userId())).isPresent();
    }

    @Test
    void separateServiceInstancesCannotExceedCapacity() throws Exception {
        LobbySnapshot lobby = service.create(COMMANDER, LobbyVisibility.PUBLIC, "Database locking");
        List<LobbyPlayer> contenders = createContenders(12);
        LobbyService first = independentService();
        LobbyService second = independentService();
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        CountDownLatch ready = new CountDownLatch(contenders.size());
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger joined = new AtomicInteger();

        try (var executor = Executors.newFixedThreadPool(contenders.size())) {
            List<Future<?>> attempts = new ArrayList<>();
            for (int index = 0; index < contenders.size(); index++) {
                LobbyPlayer contender = contenders.get(index);
                LobbyService instance = index % 2 == 0 ? first : second;
                attempts.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        go.await(5, TimeUnit.SECONDS);
                        transactions.executeWithoutResult(ignored -> instance.joinPublic(lobby.id(), contender));
                        joined.incrementAndGet();
                    } catch (LobbyException ignored) {
                        // Capacity losers are expected after five contenders commit.
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            for (Future<?> attempt : attempts) {
                attempt.get(10, TimeUnit.SECONDS);
            }
        }

        assertThat(joined).hasValue(5);
        assertThat(service.getForMember(lobby.id(), COMMANDER.userId()).members()).hasSize(6);
    }

    private List<LobbyPlayer> createContenders(int count) {
        List<LobbyPlayer> contenders = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            UUID id = UUID.randomUUID();
            String username = "database-contender-" + index;
            jdbcClient.sql("""
                            INSERT INTO users (
                                user_id, username, normalized_username, password_hash, enabled, created_at)
                            VALUES (:id, :username, :username, 'unused', TRUE, CURRENT_TIMESTAMP)
                            """)
                    .param("id", id)
                    .param("username", username)
                    .update();
            contenders.add(new LobbyPlayer(id, username));
        }
        return contenders;
    }

    @SuppressWarnings("unchecked")
    private LobbyService independentService() {
        TaskScheduler scheduler = mock(TaskScheduler.class);
        when(scheduler.schedule(any(Runnable.class), any(java.time.Instant.class)))
                .thenReturn(mock(ScheduledFuture.class));
        LobbyEventPublisher noEvents = mock(LobbyEventPublisher.class);
        return new LobbyService(
                repository,
                () -> new LobbyConstraints("placeholder-map", 2, 6, 2, 3),
                noEvents,
                lobby -> UUID.randomUUID(),
                scheduler,
                Clock.systemUTC(),
                new SecureRandom(),
                Duration.ofSeconds(5));
    }
}
