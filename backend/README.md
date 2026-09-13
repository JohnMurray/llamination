# Backend

Spring Boot 4.1.1 application built with Gradle and Java 26. It serves the
production frontend, the session-authenticated lobby API, and realtime lobby
events.

## Run

```shell
./gradlew bootRun
```

Open <http://localhost:8080>. See the root README for development accounts and
the separate Vite development workflow.

## Lobby API

All lobby endpoints require an authenticated HTTP session.

- `GET /api/lobbies`: list open public lobbies.
- `POST /api/lobbies`: create a public or private lobby.
- `GET /api/lobbies/{id}`: get a lobby for one of its members.
- `POST /api/lobbies/{id}/join`: join a public lobby.
- `POST /api/lobby-invites/{token}/join`: join through a private invitation.
- `POST /api/lobbies/auto-join`: join a random open public lobby.
- `PUT /api/lobbies/{id}/team`: choose Team One, Team Two, or Random.
- `POST /api/lobbies/{id}/start`: creator-only countdown start.
- `POST /api/lobbies/{id}/leave`: leave or, for the creator, close the lobby.
- `GET /api/me/lobby`: recover the authenticated player's current lobby.

`/ws/events` uses the same session and publishes authoritative lobby snapshots,
directory invalidations, closures, and game-start events. A five-second
disconnect grace period allows ordinary page refreshes without losing lobby
membership; it is configured with `llamination.lobby.disconnect-grace`.

Lobby storage and placeholder game creation are deliberately in-memory. The
repository, constraint provider, and game starter are interfaces so durable
storage, map metadata, and the authoritative simulation can replace them.

## Test

```shell
./gradlew test
```

The Gradle suite compiles the production frontend and covers authentication,
WebSocket connection, lobby lifecycle, team limits, automatic joining,
disconnect recovery, countdown behavior, and concurrent capacity enforcement.
