package com.llamination.backend.lobby;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryLobbyRepository implements LobbyRepository {

    private final Map<UUID, Lobby> lobbies = new HashMap<>();
    private final Map<String, UUID> playerLobbies = new HashMap<>();
    private final Map<String, UUID> inviteLobbies = new HashMap<>();

    @Override
    public void save(Lobby lobby) {
        lobbies.put(lobby.id, lobby);
        for (LobbyMember member : lobby.members.values()) {
            playerLobbies.put(member.username(), lobby.id);
        }
        if (lobby.inviteToken != null) {
            inviteLobbies.put(lobby.inviteToken, lobby.id);
        }
    }

    @Override
    public Optional<Lobby> findById(UUID id) {
        return Optional.ofNullable(lobbies.get(id));
    }

    @Override
    public Optional<Lobby> findByInviteToken(String inviteToken) {
        return Optional.ofNullable(inviteLobbies.get(inviteToken)).flatMap(this::findById);
    }

    @Override
    public Optional<Lobby> findByPlayer(String username) {
        return Optional.ofNullable(playerLobbies.get(username)).flatMap(this::findById);
    }

    @Override
    public Collection<Lobby> findAll() {
        return ListCopy.copyOf(lobbies.values());
    }

    @Override
    public void delete(Lobby lobby) {
        lobbies.remove(lobby.id);
        lobby.members.keySet().forEach(playerLobbies::remove);
        if (lobby.inviteToken != null) {
            inviteLobbies.remove(lobby.inviteToken);
        }
    }

    @Override
    public void removePlayer(String username) {
        playerLobbies.remove(username);
    }

    private static final class ListCopy {
        private ListCopy() {
        }

        static <T> Collection<T> copyOf(Collection<T> values) {
            return List.copyOf(values);
        }
    }
}
