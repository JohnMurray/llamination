# Llamination RTS Implementation Plan

## 1. Product Direction

Build a browser-based real-time-strategy game inspired by *Age of Empires* and *Command & Conquer*, with:

- A Canvas-based browser client for rendering, input, UI, audio, and presentation.
- A Java/Spring Boot server that owns the authoritative game simulation.
- WebSockets for low-latency commands, events, and state synchronization.
- Two opposing factions, each cooperatively controlled by a team of up to 4 players who share resources, units, buildings, and victory or defeat.
- An initial focus on small team matches, with the architecture kept suitable for AI opponents later.

The first release should prove the core RTS loop rather than attempt the full scope of either inspiration:

1. Join or create a match and choose a team.
2. Select and command units.
3. Gather two resources.
4. Construct a small set of buildings.
5. Produce military units.
6. Fight an opponent and destroy their headquarters.

### Initial constraints

- Two teams with 1-4 players each (2-8 players per match); uneven team sizes are allowed for private/testing matches.
- One handcrafted map, approximately 128 x 128 tiles.
- 20-50 units per team, with a stretch goal of 100; teammates command the same unit pool.
- One mechanically identical faction ruleset for both teams, with 3 worker/military unit types and 4-6 building types; team color distinguishes sides.
- No per-player resource or unit ownership within a team. The MVP uses fully shared control, with visible teammate selections/orders and server-defined conflict resolution for simultaneous commands.
- Desktop browsers first; mobile and touch controls are out of scope for the MVP.
- No fog of war, ranked matchmaking, replays, campaigns, or persistent progression in the first playable version.

## 2. Technical Approach

### Client

Use TypeScript and the HTML Canvas 2D API. Keep game code independent from the DOM and divide it into:

- `network`: WebSocket connection, protocol encoding/decoding, reconnect handling.
- `state`: client-side view of the latest authoritative world state.
- `prediction`: immediate visual feedback and interpolation; never authoritative game rules.
- `render`: terrain, sprites, selection indicators, effects, camera, and minimap.
- `input`: mouse/keyboard gestures translated into semantic player commands.
- `ui`: HUD, build menus, resource totals, lobby, and match results.
- `assets`: spritesheets, map data, audio, and asset loading.

Start with Canvas 2D because it is simple and adequate for the target scale. Keep rendering behind a small interface so WebGL or a rendering library can replace it if profiling shows that Canvas 2D is the bottleneck.

Use a conventional frontend toolchain (Vite, TypeScript, Vitest, and ESLint). The existing `frontend/index.html` becomes the entry page; source files should move under `frontend/src`.

### Server

Use the existing Java 26 and Spring Boot project. Organize the backend into clear boundaries:

- `transport`: WebSocket sessions, authentication/identity, message validation, and protocol mapping.
- `lobby`: match creation, team assignment, joining, readiness, and lifecycle.
- `simulation`: fixed-tick game loop and deterministic game rules.
- `world`: maps, entities, components, spatial queries, team state, and player-to-team membership.
- `systems`: movement, gathering, construction, production, combat, visibility, and victory.
- `content`: data definitions for units, buildings, weapons, costs, and balance.
- `persistence`: initially optional; later stores accounts, match history, and replays.

The server is authoritative: clients submit intent, and only the server changes canonical state. This limits cheating, prevents client divergence, and makes AI and replay support easier.

### Simulation model

- Run matches on a fixed tick, initially 10-20 ticks per second.
- Run rendering independently at the browser's animation-frame rate.
- Represent positions in world coordinates, not screen pixels.
- Use stable numeric entity IDs and data-oriented components or compact domain objects.
- Process commands at an assigned simulation tick in a stable order.
- Avoid wall-clock time and uncontrolled randomness inside game rules.
- Seed all simulation randomness per match.
- Keep simulation code free of Spring and networking dependencies so it can run in tests, bots, replay tools, and benchmarks.

Exact cross-machine determinism is not required for the MVP because the server is authoritative, but deterministic execution on the server is still a design goal for reproducible tests and eventual replays.

### Networking model

Use a single versioned protocol over WebSockets. JSON is appropriate during early development because it is inspectable and easy to evolve. Move high-frequency state updates to a binary format only if measurements justify it.

Client-to-server messages include:

- `hello` / protocol negotiation
- `create_match`, `join_match`, and `set_ready`
- `move_units`, `attack_target`, and `attack_move`
- `gather_resource`, `build_structure`, and `train_unit`
- `cancel_order`, `ping`, and `leave_match`

Server-to-client messages include:

- `welcome` and lobby state
- map/content manifest
- initial full snapshot
- periodic world delta
- command accepted/rejected result
- game event batch (damage, death, completion, resource delivery)
- match ended and error messages

Match/lobby messages must carry team assignment, teammate presence/readiness, and shared-control identity. Every message should carry a protocol version and relevant sequence/tick identifiers. Commands must be validated for schema, team authority, shared-team affordability, legal placement, and per-player/team rate limits. Each command records its issuing player for acknowledgements, diagnostics, and teammate UI, even though its target entities belong to the team. Commands accepted for the same simulation tick are resolved in a documented stable order. The server should periodically send full or checksum-bearing snapshots so clients can recover from missed or inconsistent deltas.

### State synchronization

- Send an initial snapshot after loading the match.
- Broadcast delta snapshots at roughly 10-20 Hz.
- Buffer two or more snapshots on the client and interpolate remote entities between them.
- Apply immediate local selection and order-marker feedback when a command is issued.
- Initially do not predict combat, economy, or pathfinding results.
- Add limited local movement prediction only after the non-predicted implementation is measured and feels insufficient.

## 3. Core Game Systems

### Map and terrain

- Define a tile map format with terrain type, movement cost, buildability, elevation placeholder, resource nodes, spawn points, and decorative layers.
- Load and validate maps on the server; send the required map data or a versioned map identifier to clients.
- Render terrain in cached chunks to avoid redrawing every tile individually every frame.
- Start with a handcrafted JSON map. Add a map editor or import pipeline after the gameplay loop is stable.

### Camera and interaction

- Pan with edge scrolling, middle/right drag, and keyboard controls.
- Zoom around the cursor.
- Click to select; drag a box for multi-selection; use modifiers to add/remove units.
- Right click for context-sensitive commands.
- Show selection rings, health bars, destinations, invalid placement, and command acknowledgements.
- Distinguish local selection and orders from teammate selection and orders without obscuring team ownership.
- Add control groups and double-click selection after basic input is reliable.

### Entities and content

Define gameplay content in validated data files rather than scattering balance constants through code. Each unit/building definition should include identifiers, display information, costs, build time, footprint, health, movement, sight, weapon, and allowed actions.

Initial content:

- Headquarters: worker production and primary defeat condition.
- Resource drop-off/storage building.
- Barracks: military production.
- Defensive tower or equivalent static defense.
- Worker: gathering and construction.
- Melee unit: inexpensive front-line unit.
- Ranged unit: higher-value ranged attacker.
- Two resource types, such as food and ore/wood.

### Movement and pathfinding

- Use a tile navigation grid with A* for individual or group destination planning.
- Recalculate only when an order, obstruction, or meaningful deviation requires it.
- Use a spatial hash or uniform grid for nearby-entity queries.
- Add simple local separation and collision resolution to prevent complete unit overlap.
- For group movement, assign nearby formation slots or shared waypoints instead of independently pathing every unit to the identical point.
- Profile worst-case path requests early. Add hierarchical pathfinding, flow fields, or a pathfinding job budget only when target-scale tests require them.

### Economy and construction

- Workers travel to a resource, gather a limited carried amount, return it to a valid drop-off, and repeat.
- Building placement validates terrain, footprint, collision, team authority, and shared-team resource cost on the server.
- Workers construct buildings over time; unfinished structures have incomplete health and no production capability.
- Production buildings maintain a queue, consume resources when an item is accepted, and spawn into a legal nearby tile.

### Combat

- Units acquire or receive targets, move into range, face the target if needed, attack on cooldown, and apply authoritative damage.
- Start with direct-hit melee and ranged attacks; visible ranged projectiles can be presentation-only or server-timed events.
- Define armor/damage categories only if the initial unit roster needs them; avoid a large counter matrix in the MVP.
- Specify behavior for lost targets, blocked paths, target priority, friendly fire, and simultaneous death.

### Match rules

- Assign one spawn position and shared starting resources to each team.
- Start after both teams have at least one player and every connected player is ready and assets are loaded.
- All teammates can command all of their team's entities and spend the shared resource pool. The server serializes conflicting orders deterministically and attributes each accepted order to its issuer.
- A team loses when its headquarters is destroyed or every teammate has surrendered/disconnected beyond a grace period; one player's departure does not forfeit the team while a teammate remains.
- Produce an authoritative result and a final statistics summary.

## 4. Delivery Phases

Each phase ends in a runnable, demonstrable increment. Do not begin large amounts of content work before the underlying loop is playable and measured.

### Phase 0: Foundation and decisions

- Document development prerequisites and one-command local startup.
- Add the TypeScript/Vite frontend build and a test setup.
- Make Spring Boot serve production frontend assets or document separate dev servers with a proxy.
- Replace the hello WebSocket with a versioned connection handshake.
- Establish shared protocol documentation and JSON schema validation/test fixtures.
- Add continuous integration for backend tests, frontend tests, linting, and builds.
- Record short architecture decisions for tick rate, protocol format, map format, and simulation ownership.

**Exit criterion:** A browser connects to the server, negotiates the protocol, and displays connection/tick diagnostics in a canvas app.

### Phase 1: Offline simulation and renderer slice

- Implement the fixed-tick simulation runner independent of WebSockets.
- Add world/entity storage, positions, team ownership, player-team membership, map loading, and content definitions.
- Render the map and placeholder units from a local snapshot.
- Implement camera movement, zoom, selection, and order indicators.
- Add basic unit movement and A* pathfinding in simulation tests.
- Create deterministic scenario tests that run a known number of ticks.

**Exit criterion:** A local test scenario renders several selectable units that move around obstacles, with the same server simulation result on repeated runs.

### Phase 2: Authoritative multiplayer movement

- Add lobby creation, team selection/assignment, 1-4 player team capacity, readiness, and match lifecycle management.
- Route validated player commands from WebSocket sessions to the correct match.
- Send initial snapshots and state deltas.
- Add client snapshot buffering and interpolation.
- Implement team-authority checks, teammate presence, shared-order attribution/conflict handling, disconnect handling, and command acknowledgement/rejection.
- Add a developer diagnostics overlay for FPS, server tick, latency, entity count, and bytes per second.

**Exit criterion:** At least three browser windows can join one match, including two on the same team; teammates can both command the same units, opponents cannot, and all clients reliably see movement and command attribution under simulated latency.

### Phase 3: Economy and production

- Add resource nodes, worker gathering, carrying, drop-off, and shared team resource accounting.
- Add building placement mode and authoritative placement validation.
- Add construction progress, production queues, unit spawning, and cancellation rules.
- Implement the HUD and contextual command/build panels.
- Add content validation and tune the initial costs/timings enough to exercise the loop.

**Exit criterion:** Teammates can jointly grow one economy, construct a barracks, and produce military units from shared resources without double-spending or server/client state disagreement.

### Phase 4: Combat and victory

- Add attack, attack-move, target acquisition, range, cooldowns, health, death, and cleanup.
- Add combat feedback: health bars, hit effects, projectiles, sound hooks, and notifications.
- Add team headquarters defeat, individual surrender/leave behavior, match timer, team end screen, and player/team statistics.
- Add basic command spam limits and malformed-message resilience.
- Run full-match soak tests and balance the first faction for a 10-20 minute target match.

**Exit criterion:** A complete match with at least two players on each team can be played from lobby to team victory with the intended cooperative gather-build-produce-fight loop.

### Phase 5: Quality, scale, and deployment

- Profile rendering, serialization, garbage collection, pathfinding, and tick duration at target and overload entity counts.
- Add server backpressure, per-match tick metrics, structured logs, and health endpoints.
- Test reconnect/resynchronization and network degradation.
- Add asset loading screens, settings, accessibility options, and browser compatibility checks.
- Containerize the service and deploy a staging environment with TLS (`wss`).
- Add end-to-end smoke tests and automated load clients.
- Define match capacity per server instance and enforce limits before public testing.

**Exit criterion:** The game runs stable public playtests, with observed tick time and network usage staying inside explicit budgets.

### Phase 6: Post-MVP options

Prioritize these based on playtest evidence rather than implementing all at once:

- Fog of war and line of sight.
- AI players and skirmish mode.
- More factions, units, upgrades, maps, and neutral objectives.
- Replays and spectator mode using the command/tick architecture.
- Matchmaking, accounts, parties, and persistent match history.
- Map editor and community maps.
- Ranked play, anti-cheat hardening, moderation, and analytics.

## 5. Testing Strategy

### Simulation tests

- Unit-test command validation and each game system.
- Use deterministic scenario tests: start from a fixture, enqueue commands, advance N ticks, and compare the resulting state or checksum.
- Test edge cases such as simultaneous attacks, blocked spawns, destroyed targets, depleted resources, invalid placements, and disconnects.
- Test simultaneous teammate orders, shared-resource contention, teammate disconnect/rejoin, and the final teammate leaving or surrendering.
- Add property-based tests for invariants such as non-negative team resources, unique entity IDs, valid team ownership, valid player-team membership, and legal map occupancy.

### Protocol tests

- Maintain representative message fixtures for every protocol version.
- Test malformed, oversized, stale, duplicate, opposing-team, unauthorized, and out-of-order commands.
- Verify snapshot/delta reconstruction against authoritative state.
- Add compatibility tests before changing message fields.

### Client tests

- Unit-test coordinate transforms, selection geometry, interpolation, input-to-command translation, and state reduction.
- Keep renderer logic separable enough to test world-to-screen behavior without pixel snapshots.
- Use multi-browser end-to-end tests for connect, team assignment, readiness, shared selection/order visibility, concurrent spending, build, train, attack, disconnect, and team match completion.

### Performance and resilience tests

- Benchmark simulation ticks with realistic maps and entity counts.
- Load-test concurrent matches using headless bot clients.
- Test latency, jitter, packet loss, reconnects, slow clients, and server overload.
- Establish budgets after early measurements; initial goals should include maintaining the selected tick rate at the target unit count and smooth 60 FPS rendering on an agreed baseline machine.

## 6. Development Practices

- Keep gameplay rules server-side and UI/presentation client-side.
- Prefer vertical slices that end in a playable improvement.
- Put balance and content data in versioned, validated definitions.
- Gate unfinished features behind development flags.
- Add metrics before optimization and preserve benchmark scenarios.
- Treat protocol and saved map/content formats as versioned public contracts.
- Use placeholder art until scale, readability, animation requirements, and faction silhouettes are proven.
- Maintain a short decision log for choices that would be expensive to reverse.

Suggested top-level documentation and source additions:

```text
backend/
  src/main/java/com/llamination/backend/
    transport/
    lobby/
    simulation/
    world/
    systems/
    content/
  src/test/...
frontend/
  src/
    network/
    state/
    render/
    input/
    ui/
    assets/
protocol/
  README.md
  schemas/
maps/
docs/
  decisions/
```

## 7. First Implementation Backlog

Complete these tasks in order to begin Phase 0 and Phase 1:

1. Add Vite, TypeScript, Vitest, and a minimal canvas application.
2. Add a frontend development proxy for the Spring WebSocket endpoint.
3. Define protocol envelope types, handshake messages, error responses, and fixtures.
4. Replace `HelloWebSocketHandler` with a connection/session handler.
5. Implement a standalone fixed-tick `GameSimulation` and deterministic simulation test harness.
6. Define the first map schema and content schemas; create one test map.
7. Add teams and player membership, then entities with position, team owner, selectable, movement, and collision data.
8. Render terrain and placeholder units with camera transforms.
9. Implement click/box selection and issue a semantic move command.
10. Implement grid navigation, A* pathfinding, and movement integration.
11. Connect two browsers as teammates to one authoritative match, synchronize movement, and verify shared unit authority.
12. Add the diagnostics overlay and baseline performance scenario.

## 8. Major Risks and Mitigations

- **RTS scope growth:** Hold the MVP to one faction, one map, and one victory condition; require a playable reason before adding a system.
- **Pathfinding cost:** Benchmark at target scale early, budget path work per tick, cache/reuse paths, and add hierarchical approaches only when needed.
- **Network bandwidth:** Start with observable JSON, measure real messages, use deltas and interest filtering, and adopt binary encoding only for demonstrated hot paths.
- **Client/server disagreement:** Make the server authoritative, sequence commands, periodically resynchronize, and test delta reconstruction.
- **Simulation tick overruns:** Track per-system timing, avoid blocking work on match threads, cap expensive work, and load-test multiple matches.
- **Poor unit readability:** Prototype silhouettes, team colors, selection markers, health bars, and zoom levels before commissioning polished art.
- **Late multiplayer surprises:** Make the first meaningful gameplay slice networked in Phase 2 rather than finishing an offline game first.
- **Shared-control conflict and griefing:** Attribute orders, expose teammate intent, resolve same-tick conflicts deterministically, rate-limit per player and team, and use playtests to decide whether optional role/permission controls are needed after MVP.
- **Protocol churn:** Version envelopes and schemas, keep compatibility fixtures, and separate transport DTOs from simulation objects.
- **Cheating and abuse:** Never trust client state, validate all commands and team authority, rate-limit inputs per player and team, cap message sizes, and add authentication only when accounts are introduced.

## 9. Definition of MVP Complete

The MVP is complete when two teams of up to four players can open the deployed game in supported desktop browsers, create/join a match, choose teams, and cooperatively command their team's shared units and resources to gather, construct buildings, train an army, fight, and reach a server-authoritative team victory result. The core cooperative case must be demonstrated with at least two players per team. The match must remain synchronized through normal latency and a reconnect, handle concurrent teammate commands and spending deterministically, meet the agreed tick/render performance budgets at the target team unit count, and pass automated simulation, protocol, client, multi-browser end-to-end, and load smoke tests.
