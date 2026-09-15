package com.llamination.backend.lobby;

import java.time.Clock;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Rebuilds process-local timers and reconciles deadlines missed during backend downtime. */
@Component
public class LobbyCountdownRecovery {

    private final LobbyRepository repository;
    private final LobbyService lobbyService;
    private final TaskScheduler taskScheduler;
    private final Clock clock;

    public LobbyCountdownRecovery(
            LobbyRepository repository, LobbyService lobbyService, TaskScheduler taskScheduler) {
        this(repository, lobbyService, taskScheduler, Clock.systemUTC());
    }

    LobbyCountdownRecovery(
            LobbyRepository repository,
            LobbyService lobbyService,
            TaskScheduler taskScheduler,
            Clock clock) {
        this.repository = repository;
        this.lobbyService = lobbyService;
        this.taskScheduler = taskScheduler;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    void recoverPendingCountdowns() {
        repository.findPendingCountdowns().forEach(this::schedule);
    }

    @Scheduled(fixedDelayString = "${llamination.lobby.countdown-recovery-interval:5s}")
    void reconcileOverdueCountdowns() {
        repository.findDueCountdowns(clock.instant()).forEach(this::finish);
    }

    private void schedule(PendingLobbyCountdown countdown) {
        var task = taskScheduler.schedule(() -> finish(countdown), countdown.endsAt());
        if (task == null) {
            throw new IllegalStateException("Unable to recover lobby countdown " + countdown.lobbyId());
        }
    }

    private void finish(PendingLobbyCountdown countdown) {
        lobbyService.finishCountdown(countdown.lobbyId(), countdown.version());
    }
}
