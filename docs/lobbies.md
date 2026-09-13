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

Lobby mutations are serialized by `LobbyService`. This makes list visibility,
capacity checks, membership, and countdown transitions atomic for the current
single-server in-memory implementation.

## Persistence boundary

Lobby state intentionally disappears when the server restarts. Before running
multiple server instances, replace `InMemoryLobbyRepository` with durable
storage and introduce cross-instance coordination for capacity and countdown
transitions.
