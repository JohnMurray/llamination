package com.llamination.backend.lobby;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Fast repository used by domain tests; production state is owned by PostgreSQL. */
public class InMemoryLobbyRepository implements LobbyRepository {

    private final Map<UUID, Lobby> lobbies = new HashMap<>();
    private final Map<UUID, UUID> playerLobbies = new HashMap<>();
    private final Map<String, UUID> inviteLobbies = new HashMap<>();

    @Override
    public void save(Lobby lobby) {
        lobbies.put(lobby.id, lobby);
        for (LobbyMember member : lobby.members.values()) {
            playerLobbies.put(member.userId(), lobby.id);
        }
        if (lobby.inviteTokenHash != null) {
            inviteLobbies.put(lobby.inviteTokenHash, lobby.id);
        }
    }

    @Override
    public Optional<Lobby> findById(UUID id) {
        return Optional.ofNullable(lobbies.get(id));
    }

    @Override
    public Optional<Lobby> findByIdForUpdate(UUID id) {
        return findById(id);
    }

    @Override
    public Optional<Lobby> findByInviteTokenHash(String inviteTokenHash) {
        return Optional.ofNullable(inviteLobbies.get(inviteTokenHash)).flatMap(this::findById);
    }

    @Override
    public Optional<Lobby> findByPlayer(UUID userId) {
        return Optional.ofNullable(playerLobbies.get(userId)).flatMap(this::findById);
    }

    @Override
    public Collection<Lobby> findAll() {
        return ListCopy.copyOf(lobbies.values());
    }

    @Override
    public void delete(Lobby lobby) {
        lobbies.remove(lobby.id);
        lobby.members.keySet().forEach(playerLobbies::remove);
        if (lobby.inviteTokenHash != null) {
            inviteLobbies.remove(lobby.inviteTokenHash);
        }
    }

    @Override
    public void removePlayer(UUID userId) {
        playerLobbies.remove(userId);
    }

    private static final class ListCopy {
        private ListCopy() {
        }

        static <T> Collection<T> copyOf(Collection<T> values) {
            return java.util.List.copyOf(values);
        }
    }
}
