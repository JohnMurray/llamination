# Llamination

RTS game inspired by Ages of Empires and Command & Conquer with a twist that each faction
is cooperatively controlled, turning a 1v1 to a (max) 4v4. Each team of (up to) 4 must
coordinate amongst each other, sharing resources and units.

## Running locally

Install Java 26, Node.js, and a Docker Compose provider. PostgreSQL and Redis run
in containers; the backend and frontend build continue running directly on the
host. Start the full development environment with:

```sh
./dev.sh
```

The command waits for both stores to become healthy, starts the backend with the
`dev` Spring profile, and opens the application. The launcher starts at port
`8080`, selects the next available port if it is in use, and honors `PORT` or
Spring's `SERVER_PORT` as the base port.

Infrastructure can also be managed independently:

```sh
./dev.sh up
./dev.sh logs
./dev.sh down
```

Copy `.env.example` to `.env` to override local ports or credentials. PostgreSQL
uses a named volume. `./dev.sh reset-data` deliberately deletes that volume and
recreates an empty, migrated development database.

Six development accounts are included for
multi-browser lobby testing: `commander`, `scout`, `builder`, `rider`,
`shepherd`, and `herder`. Their development password is `llama`. These accounts
are inserted by a dev-only Flyway migration and their passwords are BCrypt
hashes; the application does not currently provide account registration.

For frontend development with hot reload, run the backend as above and start
Vite in a second terminal:

```sh
cd frontend
npm install
npm run dev
```

Vite proxies `/api` and `/ws` to Spring Boot. A production frontend build is
also run automatically as part of the Gradle resource build.

## Lobby foundation

Authenticated players can create public or private lobbies, browse and join
open public lobbies, follow private invite links, select a team or request a
random assignment, and auto-join a random public lobby. The server owns lobby
capacity and lifecycle state, including the ten-second start countdown.

The placeholder map currently fixes lobbies at a minimum of 2 and maximum of 6
players, with up to 3 players per team. These values are exposed through
`LobbyConstraintsProvider` and must be replaced with constraints from the
selected map when the map catalog is implemented.

PostgreSQL is authoritative for accounts, active lobbies, memberships, and
countdown deadlines. Redis stores disposable HTTP sessions. Lobby deadlines are
recovered after backend restarts, while losing Redis requires players to sign in
again without deleting their durable lobby state. See `docs/persistence.md` for
the storage and multi-instance boundaries.
