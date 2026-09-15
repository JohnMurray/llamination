# Llamination

RTS game inspired by Ages of Empires and Command & Conquer with a twist that each faction
is cooperatively controlled, turning a 1v1 to a (max) 4v4. Each team of (up to) 4 must
coordinate amongst each other, sharing resources and units.

## Running locally

Start the application with the launch script:

```sh
./launch.sh
```

The launcher starts at port `8080`, selects the next available port if it is in
use, and opens the application in your browser once the server is ready. Set
`PORT` (or Spring's `SERVER_PORT`) to choose a different base port.

Six development accounts are included for
multi-browser lobby testing: `commander`, `scout`, `builder`, `rider`,
`shepherd`, and `herder`. Their development password is `llama`.

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

Accounts are read from `backend/config/users.txt` in `username:password` format.
Blank lines and lines beginning with `#` are ignored. Set `LLAMINATION_USERS_FILE`
to point at another credentials file. The file is re-read on each login, so account
changes do not require restarting the server.

This deliberately simple credential store uses plain-text passwords and is intended
for local development only. A deployed service should use salted password hashes and
a managed account store.
