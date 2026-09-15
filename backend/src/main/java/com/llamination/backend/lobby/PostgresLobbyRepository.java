package com.llamination.backend.lobby;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Maps the lobby aggregate to PostgreSQL while leaving lifecycle policy in {@link LobbyService}. */
@Repository
public class PostgresLobbyRepository implements LobbyRepository {

    private static final String SELECT_LOBBY = """
            SELECT l.lobby_id, l.creator_user_id, creator.username AS creator_username,
                   l.visibility, l.description, l.map_id, l.min_players, l.max_players,
                   l.max_players_per_team, l.state, l.countdown_ends_at, l.game_id,
                   l.invite_token_hash, l.created_at, l.version
            FROM lobbies l
            JOIN users creator ON creator.user_id = l.creator_user_id
            """;

    private final JdbcClient jdbcClient;

    public PostgresLobbyRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void save(Lobby lobby) {
        jdbcClient.sql("""
                        INSERT INTO lobbies (
                            lobby_id, creator_user_id, visibility, description, map_id,
                            min_players, max_players, max_players_per_team, state,
                            countdown_ends_at, game_id, invite_token_hash, created_at, updated_at, version)
                        VALUES (
                            :id, :creatorUserId, :visibility, :description, :mapId,
                            :minPlayers, :maxPlayers, :maxPlayersPerTeam, :state,
                            :countdownEndsAt, :gameId, :inviteTokenHash, :createdAt, CURRENT_TIMESTAMP, :version)
                        ON CONFLICT (lobby_id) DO UPDATE SET
                            state = EXCLUDED.state,
                            countdown_ends_at = EXCLUDED.countdown_ends_at,
                            game_id = EXCLUDED.game_id,
                            updated_at = CURRENT_TIMESTAMP,
                            version = EXCLUDED.version
                        """)
                .param("id", lobby.id)
                .param("creatorUserId", lobby.creatorUserId)
                .param("visibility", lobby.visibility.name())
                .param("description", lobby.description)
                .param("mapId", lobby.constraints.mapId())
                .param("minPlayers", lobby.constraints.minPlayers())
                .param("maxPlayers", lobby.constraints.maxPlayers())
                .param("maxPlayersPerTeam", lobby.constraints.maxPlayersPerTeam())
                .param("state", lobby.state.name())
                .param("countdownEndsAt", lobby.countdownEndsAt, Types.TIMESTAMP_WITH_TIMEZONE)
                .param("gameId", lobby.gameId, Types.OTHER)
                .param("inviteTokenHash", lobby.inviteTokenHash, Types.VARCHAR)
                .param("createdAt", lobby.createdAt)
                .param("version", lobby.version)
                .update();

        jdbcClient.sql("DELETE FROM lobby_members WHERE lobby_id = :lobbyId")
                .param("lobbyId", lobby.id)
                .update();
        for (LobbyMember member : lobby.members.values()) {
            jdbcClient.sql("""
                            INSERT INTO lobby_members (
                                lobby_id, user_id, team_choice, assigned_team, joined_at)
                            VALUES (:lobbyId, :userId, :teamChoice, :assignedTeam, CURRENT_TIMESTAMP)
                            """)
                    .param("lobbyId", lobby.id)
                    .param("userId", member.userId())
                    .param("teamChoice", member.teamChoice().name())
                    .param(
                            "assignedTeam",
                            member.assignedTeam() == null ? null : member.assignedTeam().name(),
                            Types.VARCHAR)
                    .update();
        }
    }

    @Override
    public Optional<Lobby> findById(UUID id) {
        return findOne(SELECT_LOBBY + " WHERE l.lobby_id = :id", Map.of("id", id));
    }

    @Override
    public Optional<Lobby> findByIdForUpdate(UUID id) {
        return findOne(SELECT_LOBBY + " WHERE l.lobby_id = :id FOR UPDATE OF l", Map.of("id", id));
    }

    @Override
    public Optional<Lobby> findByInviteTokenHash(String inviteTokenHash) {
        return findOne(
                SELECT_LOBBY + " WHERE l.invite_token_hash = :inviteTokenHash",
                Map.of("inviteTokenHash", inviteTokenHash));
    }

    @Override
    public Optional<Lobby> findByPlayer(UUID userId) {
        return findOne(
                SELECT_LOBBY + " JOIN lobby_members membership ON membership.lobby_id = l.lobby_id"
                        + " WHERE membership.user_id = :userId",
                Map.of("userId", userId));
    }

    @Override
    public Collection<Lobby> findAll() {
        return jdbcClient.sql(SELECT_LOBBY + " ORDER BY l.created_at, l.lobby_id")
                .query(this::mapLobbyRow)
                .list()
                .stream()
                .map(this::hydrate)
                .toList();
    }

    @Override
    public Collection<PendingLobbyCountdown> findPendingCountdowns() {
        return findCountdowns("state = 'COUNTDOWN' AND countdown_ends_at IS NOT NULL", Map.of());
    }

    @Override
    public Collection<PendingLobbyCountdown> findDueCountdowns(Instant now) {
        return findCountdowns(
                "state = 'COUNTDOWN' AND countdown_ends_at IS NOT NULL AND countdown_ends_at <= :now",
                Map.of("now", now));
    }

    @Override
    public void removePlayer(UUID userId) {
        jdbcClient.sql("DELETE FROM lobby_members WHERE user_id = :userId")
                .param("userId", userId)
                .update();
    }

    @Override
    public void delete(Lobby lobby) {
        jdbcClient.sql("DELETE FROM lobbies WHERE lobby_id = :lobbyId")
                .param("lobbyId", lobby.id)
                .update();
    }

    private Optional<Lobby> findOne(String sql, Map<String, ?> parameters) {
        return jdbcClient.sql(sql)
                .params(parameters)
                .query(this::mapLobbyRow)
                .optional()
                .map(this::hydrate);
    }

    private Collection<PendingLobbyCountdown> findCountdowns(String condition, Map<String, ?> parameters) {
        return jdbcClient.sql("""
                        SELECT lobby_id, countdown_ends_at, version
                        FROM lobbies
                        WHERE """ + condition + " ORDER BY countdown_ends_at, lobby_id")
                .params(parameters)
                .query((resultSet, rowNumber) -> new PendingLobbyCountdown(
                        resultSet.getObject("lobby_id", UUID.class),
                        resultSet.getObject("countdown_ends_at", OffsetDateTime.class).toInstant(),
                        resultSet.getLong("version")))
                .list();
    }

    private LobbyRow mapLobbyRow(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
        OffsetDateTime countdown = resultSet.getObject("countdown_ends_at", OffsetDateTime.class);
        return new LobbyRow(
                resultSet.getObject("lobby_id", UUID.class),
                resultSet.getObject("creator_user_id", UUID.class),
                resultSet.getString("creator_username"),
                LobbyVisibility.valueOf(resultSet.getString("visibility")),
                resultSet.getString("description"),
                new LobbyConstraints(
                        resultSet.getString("map_id"),
                        resultSet.getInt("min_players"),
                        resultSet.getInt("max_players"),
                        2,
                        resultSet.getInt("max_players_per_team")),
                LobbyState.valueOf(resultSet.getString("state")),
                countdown == null ? null : countdown.toInstant(),
                resultSet.getObject("game_id", UUID.class),
                resultSet.getString("invite_token_hash"),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                resultSet.getLong("version"));
    }

    private Lobby hydrate(LobbyRow row) {
        Map<UUID, LobbyMember> members = new LinkedHashMap<>();
        jdbcClient.sql("""
                        SELECT membership.user_id, users.username, membership.team_choice,
                               membership.assigned_team
                        FROM lobby_members membership
                        JOIN users ON users.user_id = membership.user_id
                        WHERE membership.lobby_id = :lobbyId
                        ORDER BY membership.joined_at, membership.user_id
                        """)
                .param("lobbyId", row.id())
                .query((resultSet, rowNumber) -> {
                    UUID userId = resultSet.getObject("user_id", UUID.class);
                    String assignedTeam = resultSet.getString("assigned_team");
                    return new LobbyMember(
                            userId,
                            resultSet.getString("username"),
                            userId.equals(row.creatorUserId()),
                            TeamChoice.valueOf(resultSet.getString("team_choice")),
                            assignedTeam == null ? null : Team.valueOf(assignedTeam));
                })
                .list()
                .forEach(member -> members.put(member.userId(), member));

        return new Lobby(
                row.id(),
                row.creatorUserId(),
                row.creatorUsername(),
                row.visibility(),
                row.description(),
                row.constraints(),
                null,
                row.inviteTokenHash(),
                row.createdAt(),
                row.state(),
                row.countdownEndsAt(),
                row.gameId(),
                row.version(),
                members);
    }

    private record LobbyRow(
            UUID id,
            UUID creatorUserId,
            String creatorUsername,
            LobbyVisibility visibility,
            String description,
            LobbyConstraints constraints,
            LobbyState state,
            java.time.Instant countdownEndsAt,
            UUID gameId,
            String inviteTokenHash,
            java.time.Instant createdAt,
            long version) {
    }
}
