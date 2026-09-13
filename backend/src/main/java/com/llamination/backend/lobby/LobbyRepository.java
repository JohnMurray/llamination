package com.llamination.backend.lobby;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface LobbyRepository {

    void save(Lobby lobby);

    Optional<Lobby> findById(UUID id);

    Optional<Lobby> findByInviteToken(String inviteToken);

    Optional<Lobby> findByPlayer(String username);

    Collection<Lobby> findAll();

    void removePlayer(String username);

    void delete(Lobby lobby);
}
