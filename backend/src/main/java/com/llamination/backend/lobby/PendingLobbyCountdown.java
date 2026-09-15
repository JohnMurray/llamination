package com.llamination.backend.lobby;

import java.time.Instant;
import java.util.UUID;

/** Minimal persisted countdown projection used to rebuild lost in-process timers. */
record PendingLobbyCountdown(UUID lobbyId, Instant endsAt, long version) {
}

