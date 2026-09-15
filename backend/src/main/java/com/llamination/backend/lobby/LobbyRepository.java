package com.llamination.backend.lobby;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface LobbyRepository {

    void save(Lobby lobby);

    Optional<Lobby> findById(UUID id);

    Optional<Lobby> findByIdForUpdate(UUID id);

    Optional<Lobby> findByInviteTokenHash(String inviteTokenHash);

    Optional<Lobby> findByPlayer(UUID userId);

    Collection<Lobby> findAll();

    void removePlayer(UUID userId);

    void delete(Lobby lobby);
}
