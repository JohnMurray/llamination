-- PostgreSQL owns durable identity and active lobby state; Redis remains disposable.
CREATE TABLE users (
    user_id UUID PRIMARY KEY,
    username VARCHAR(32) NOT NULL,
    normalized_username VARCHAR(32) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE lobbies (
    lobby_id UUID PRIMARY KEY,
    creator_user_id UUID NOT NULL REFERENCES users(user_id),
    visibility VARCHAR(16) NOT NULL CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    description VARCHAR(160) NOT NULL,
    map_id VARCHAR(100) NOT NULL,
    min_players INTEGER NOT NULL CHECK (min_players > 0),
    max_players INTEGER NOT NULL CHECK (max_players >= min_players),
    max_players_per_team INTEGER NOT NULL CHECK (max_players_per_team > 0),
    state VARCHAR(16) NOT NULL CHECK (state IN ('WAITING', 'COUNTDOWN', 'STARTING', 'STARTED', 'CLOSED')),
    countdown_ends_at TIMESTAMPTZ,
    game_id UUID,
    invite_token_hash CHAR(64) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL CHECK (version > 0)
);

CREATE TABLE lobby_members (
    lobby_id UUID NOT NULL REFERENCES lobbies(lobby_id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(user_id),
    team_choice VARCHAR(16) NOT NULL CHECK (team_choice IN ('TEAM_ONE', 'TEAM_TWO', 'RANDOM')),
    assigned_team VARCHAR(16) CHECK (assigned_team IN ('TEAM_ONE', 'TEAM_TWO')),
    joined_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (lobby_id, user_id),
    UNIQUE (user_id)
);

CREATE INDEX lobbies_public_directory_idx
    ON lobbies (created_at, lobby_id)
    WHERE visibility = 'PUBLIC' AND state = 'WAITING';

CREATE INDEX lobby_members_lobby_idx ON lobby_members (lobby_id);

