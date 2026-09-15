# Persistence architecture

This decision keeps canonical state in one transactional store and uses Redis
only where losing data is acceptable.

## Storage ownership

- PostgreSQL owns users, BCrypt password hashes, active lobby aggregates,
  membership, map constraint snapshots, invite-token hashes, lifecycle versions,
  and countdown deadlines.
- Redis owns Spring HTTP sessions. Redis loss signs users out but does not delete
  accounts or lobbies.
- Public lobby reads go directly to PostgreSQL. A cache should be introduced only
  after measurements demonstrate a need.

Application code uses stable user UUIDs for ownership and membership. Usernames
remain display values in HTTP and WebSocket payloads. Flyway owns schema changes;
the `dev` profile adds a repeatable migration containing local test accounts.
There is intentionally no account-registration workflow yet.

## Transaction and recovery model

Lobby commands lock the affected lobby row with `SELECT FOR UPDATE`, validate
the aggregate, persist the header and members, and publish events after commit.
Database uniqueness is the final defense against a user joining two lobbies.

Countdown deadlines are durable. Process-local timers provide prompt execution,
startup rebuilds timers after downtime, and periodic reconciliation processes
overdue rows. State and version checks make duplicate execution harmless. Game
creation must be idempotent by lobby ID so a crash between an external side
effect and transaction commit can be retried safely.

## Deployment boundary

Development uses Docker Compose for PostgreSQL and Redis while Java and Node run
on the host. Production supplies equivalent services and credentials through
environment variables; it does not need to use the development Compose file.

The first supported topology is one backend instance. Before horizontal scaling,
add Redis-backed presence generations and committed-event fan-out. Redis Pub/Sub
messages are hints rather than canonical data, so clients must continue using
HTTP snapshots to recover missed WebSocket events.
