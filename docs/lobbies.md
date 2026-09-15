# Lobby architecture

The lobby service is the authoritative owner of membership, capacity, team
assignment, visibility, and game-start transitions. HTTP handles commands and
initial reads; the authenticated WebSocket broadcasts resulting snapshots.

## Temporary map constraints

`DefaultLobbyConstraintsProvider` currently supplies the rules for a
`placeholder-map`:

- Minimum players: 2
- Maximum players: 6
- Teams: 2
- Maximum players per team: 3

When maps are introduced, lobby creation should accept a map identifier and the
provider should resolve these values from validated map metadata. Clients must
continue displaying the values returned by the server instead of embedding map
limits locally.

## Lifecycle

1. A lobby begins in `WAITING` and accepts members.
2. Reaching six players or a valid creator start moves it to `COUNTDOWN`, hides
   it from public browsing, locks team choices, and rejects new members.
3. Random team choices are balanced into available team slots when the
   countdown begins.
4. A non-creator departure cancels the countdown and returns the lobby to
   `WAITING`. A creator departure closes it for every member.
5. Countdown expiry moves through `STARTING` exactly once, creates a game, and
   then publishes `STARTED` with its game identifier.

Lobby mutations run in PostgreSQL transactions. Mutating commands lock the
lobby header row before loading members, so capacity checks, team selection,
membership, and lifecycle transitions remain atomic even when another backend
process targets the same lobby. A unique membership constraint prevents one
stable user ID from occupying multiple active lobbies.

## Persistence and restart recovery

PostgreSQL is authoritative for the lobby header, members, map constraints,
version, and countdown deadline. WebSocket events are published only after the
transaction commits. Private invite bearer tokens are returned to the creator
but only their SHA-256 hashes are stored; an already shared link continues to
work after restart even when the raw token is no longer displayed.

Every countdown has a durable `countdown_ends_at` value. Startup reconstructs
future timers, and a periodic reconciler finds overdue countdowns that may have
been missed during downtime. Completion locks the row and checks its state and
version, so duplicate timers cannot create two transitions. The placeholder
game starter derives an idempotent game ID from the lobby ID; the future
simulation store must keep that idempotency contract.

The initial runtime remains single-instance. PostgreSQL coordination is ready
for multiple instances, but presence tracking and WebSocket connections remain
local to one process. Before scaling horizontally, publish committed events
through Redis and store expiring presence generations there so each instance
can deliver to its own connected sockets.
