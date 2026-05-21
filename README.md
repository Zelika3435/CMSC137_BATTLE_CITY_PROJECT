## Battle City (LibGDX) — 4-player authoritative multiplayer

---

### Quick start

| Goal | Command |
|------|---------|
| Start the headless server | `./gradlew :server:run` |
| Start a desktop client    | `./gradlew :lwjgl3:run` |
| Run all headless tests    | `./gradlew :core:test`  |
| Custom server port        | `./gradlew :server:run --args="9000"` |

On **Windows (PowerShell)** use `.\gradlew.bat` instead of `./gradlew`.

**Requirements:** JDK 17 (`javac` on PATH).  On Ubuntu/Debian: `sudo apt install openjdk-17-jdk`.

---

### Main menu flow

```
 ┌──────────────┐
 │  Main Menu   │  W/S or ↑/↓ to move, ENTER/SPACE to confirm, ESC from any screen
 └──────┬───────┘
        │
   ┌────┴──────┬────────────┬─────────────┐
   ▼           ▼            ▼             ▼
Single      Tutorial    Multiplayer    Quit
Player
   │           │            │
   ▼           │         ┌──┴──────────────────┐
SP_PRESTART    │         │   MP_CONNECT         │  fill host / port / name
(seed picker)  │         │   HOST or JOIN       │
   │           │         └──┬──────────────────┘
   ▼           ▼            ▼
SINGLE_PLAYER  TUTORIAL   MP_LOBBY (roster + countdown)
   │                        │
   ▼                        ▼
MATCH_END               MP_MATCH (live game)
                            │
                            ▼
                        MP_LOBBY (server resets; ready flags cleared)
```

- All screens accept **ESC** to return to the previous level (or main menu).
- No network connection is opened until the player reaches **MP_CONNECT** and confirms.

---

### Single player (vs bots)

1. From the main menu select **Single Player**.
2. The **seed picker** appears — choose a **Fixed** seed (42) for reproducible layouts, or **Random** for a new map.
3. Press **ENTER** to start the match.
4. You control player 0 (highlighted white); players 1–3 are driven by `BotAI` using the same deterministic seed.
5. The match ends when the **BASE** is destroyed or all enemy tanks are eliminated.
6. Press **ENTER** on the result screen to return to the main menu.

**Map:** 26×26 tile arena — STEEL border, steel L-shaped corner bases protecting each spawn, symmetric brick corridor walls and steel pillars creating lanes and chokepoints, central BASE at (12,12)–(13,12) equidistant from all four spawns.

---

### Tutorial

1. From the main menu select **Tutorial** — no server needed.
2. A **TutorialOverlay** panel anchors at the top of the screen:
   - `Step N / 4` counter · gold step title · grey hint text
   - Four progress dots (green = done, gold = current, grey = upcoming)
   - `ESC: back to menu` reminder (right side)
3. **Step 1 — Move south:** only **S / ↓** is active — other movement keys and FIRE are locked. Completes when the tank moves ≥ 1 tile south of spawn.
4. **Step 2 — Face north:** only **W / ↑** is active — other movement and FIRE are locked. Completes when facing UP.
5. **Step 3 — Destroy brick:** only **SPACE** is active — all movement is locked. Completes on the first `TileDestroyed(BRICK)` event.
6. **Step 4 — Destroy the base:** all **movement keys** and **FIRE** are active. Drive north through the cleared brick row and shoot the gold BASE tiles — completes when a `BaseHit` event is detected.
7. The panel turns green and shows **"Tutorial complete!"** when all four steps are done; all controls are then locked until ESC.
8. Press **ESC** at any point to return to the main menu. **F3** (debug overlay) is always available.

**Notes:**
- Controls outside the current step are silently ignored — the game only responds to keys taught by the active step.
- Steps advance from deterministic simulation conditions (events / position / direction) — never from wall-clock time; the tutorial is headless-testable.
- The BASE is protected by a STEEL guard wall on its south face; bullets cannot destroy it accidentally.
- No bots; no networking.
- **Map:** 13×13 with a STEEL obstacle, a 3-brick cluster, a STEEL guard wall, and a 2-tile BASE.

---

### Multiplayer — step by step

#### Hosting (in-process — no separate server needed)

1. Run a desktop client: `./gradlew :lwjgl3:run`
2. From the main menu choose **Multiplayer**.
3. On the **Mode** row press **RIGHT / D** to switch to **HOST**.
   - The Host field is replaced by **"Your IP: &lt;address&gt;"** — your detected LAN IP.
   - Fill in a **Port** (default `9000`) and a **Name**, then press **ENTER** on `[ HOST GAME ]`.
4. An in-process `GameServer` starts on that port and the client connects to it over loopback.
5. The **LobbyScreen** shows **"Share IP: &lt;address&gt;"** — give that address to other players
   so they can join via the JOIN flow below.

The server goes through: `LOBBY → COUNTDOWN (3 s) → RUNNING → END (5 s) → LOBBY → …`

> **Headless / dedicated server** — if you prefer a standalone server process (e.g. on a remote
> machine or CI), the old `./gradlew :server:run` entry-point is still available.
> Custom port: `./gradlew :server:run --args="9000"`

#### Joining (up to 4 players)

```bash
# Each player on a separate machine/terminal:
./gradlew :lwjgl3:run
```

In the **MP_CONNECT** form (Mode = **JOIN**):

| Field | Default | Notes |
|-------|---------|-------|
| Host  | detected LAN IP | IP shown in the host's LobbyScreen ("Share IP: …") — pre-filled with your own LAN address; edit to match the host's IP |
| Port  | `9000` (from `ProtocolConstants`) | Must match the host's chosen port |
| Name  | `Player` | Display name shown in lobby (max 32 chars) |
| Mode  | `JOIN` | LEFT/RIGHT (or A/D) on the Mode row to toggle JOIN ↔ HOST |

*Dev shortcut* — CLI args **pre-fill** the Host and Port fields (the form still appears; you can still edit):

```bash
./gradlew :lwjgl3:run --args="192.168.1.10 9000"
```

Press **ENTER** on the Confirm row (or navigate there) to connect.

#### Lobby (MP_LOBBY)

Once connected, the **LobbyScreen** shows:

- Player roster (slot · name · ready badge) sorted by player ID.
- The player with the **lowest ID** is the **host** and sees the **Start Match** button.
- Countdown ticks are taken directly from the server's `LOBBY_STATE` (not wall-clock).

| Action | Key |
|--------|-----|
| Toggle ready | **R** |
| Force-start (host only) | **ENTER** |
| Leave lobby | **ESC** — sends `DISCONNECT`, frees server slot, returns to main menu |

**Auto-start:** when all connected players (minimum 2) are ready the server automatically begins the 3-second countdown. Any player disconnecting during the countdown drops the count; if it falls below 2 the countdown cancels and the lobby resets.

#### In-match

- A **"GO!"** overlay fades out over ~1.5 s when the first server snapshot arrives.
- **WASD / arrow keys** — move / face; **SPACE** — fire.
- The client only sends input commands; the server is authoritative for all positions, collisions, and tile destruction.

#### Match end

- When `matchOver` is set by the server, an **end panel** shows (winner / survivors / duration).
- **ENTER** skips the 5-second wait.
- The server auto-resets to LOBBY (ready flags cleared); the client returns to **MP_LOBBY** (connection stays alive — no re-join needed).

If the join is **rejected** (server full, match in progress, etc.) the client auto-redirects to **MP_CONNECT** with the error text pre-filled and your previous host/port/name restored.

---

### Debug overlay (F3)

Press **F3** to toggle the overlay (default **off**; no file I/O, resets each session).

| Field | Source |
|-------|--------|
| `tick` | `serverTick` from the latest snapshot (or local tick in offline mode) |
| `dt`   | Render-frame delta (variable; simulation is fixed at 1/60 s) |
| `ping` | Estimated RTT from last ping/pong exchange |
| `loss%` | Recent packet-loss estimate (sent vs acked counters) |
| `tanks` / `projectiles` | Entity counts from the latest `GameSnapshot` |

The overlay reads only from immutable snapshots and `NetStatsSnapshot` — it has no effect on the simulation.

---

### Controls summary

| Context | Key | Action |
|---------|-----|--------|
| Any menu / form | W / S  ↑ / ↓ | Navigate rows |
| Any menu / form | ENTER / SPACE | Confirm / select |
| Any screen | ESC | Back / return to menu |
| Connect form | A / D  ←/→ on Mode row | Toggle JOIN ↔ HOST |
| Connect form | Type | Edit Host / Port / Name fields |
| Connect form | BACKSPACE | Delete last character |
| Lobby | R | Toggle ready |
| Lobby | ENTER | Force-start (host only) |
| In-game | WASD / arrows | Move + face direction |
| In-game | SPACE | Fire |
| In-game | F3 | Toggle debug overlay |
| In-game | ESC | Return to menu / lobby |
| Match-end | ENTER | Skip wait, return |
| SP seed picker | W/S | Select seed type |
| SP seed picker | ENTER | Confirm + start match |

---

### What you should see

- **Main menu:** four options — Single Player · Tutorial · Multiplayer · Quit.
- **Single-player / bots:** 26×26 tile arena (steel border, corner base pockets, brick/steel corridors, central BASE); your tank highlighted white, bots moving autonomously.
- **Tutorial:** 13×13 map; TutorialOverlay panel at top with step counter, progress dots, and hints; BASE shielded by steel guard wall.
- **Multiplayer lobby:** player roster, ready badges, countdown timer.
- **Match:** four-colour tanks, yellow bullets; brick tiles destroyed by matching-coloured shots; tile destruction synced authoritatively by server.
- **End screen:** duration and surviving-tank count.
- **F3 debug overlay:** `tick  dt  ping  loss%  tanks  projectiles` in top-left corner.

---

### Protocol overview (v1, little-endian binary)

All messages share a **23-byte header**: `protocolVersion` (1 B) · `messageType` (1 B) · `sessionId` (4 B) · `playerId` (1 B) · `seq` (4 B) · `ack` (4 B) · `serverTick` (8 B).

| ID | Type | Dir | Purpose |
|----|------|-----|---------|
| 1  | `JOIN`        | C→S | Request slot; UTF-8 player name (max 32 chars) |
| 2  | `JOIN_ACK`    | S→C | `assignedPlayerId`, `sessionId`, `mapSeed`, `serverTick`, **`currentPhase`** |
| 3  | `INPUT`       | C→S | `tickStamp`, `MOVE_DIR` or `FIRE` (RUNNING phase only) |
| 4  | `SNAPSHOT`    | S→C | Full authoritative state + `stateHash` (RUNNING only) |
| 5  | `PING`        | C→S | RTT probe — `clientTimeMs` |
| 6  | `PONG`        | S→C | RTT reply — echoes `clientTimeMs` + `serverTimeMs` |
| 7  | `DISCONNECT`  | C↔S | Graceful teardown with UTF-8 reason |
| 8  | `ERROR`       | S→C | Rejection — `code` + UTF-8 message |
| 9  | `LOBBY_STATE` | S→C | Lobby roster: `phase`, players (id/name/ready/connected), `hostPlayerId`, `countdownTicksLeft` |
| 10 | `SET_READY`   | C→S | Toggle client ready flag (`byte` 0/1) |
| 11 | `START_MATCH` | C→S | **Host-only** — skip remaining countdown and start immediately |

Invalid packets are rejected before processing; clients **never** send positions or velocities.

#### `JOIN_ACK` body (extends header)
```
byte  assignedPlayerId
int   sessionId
long  mapSeed
long  serverTick
byte  currentPhase     ← LobbyPhase ordinal: 0=LOBBY 1=COUNTDOWN 2=RUNNING 3=END
```

#### `LOBBY_STATE` body
```
byte  phaseOrdinal
byte  hostPlayerId
int   countdownTicksLeft
byte  playerCount
for each player:
  byte  playerId
  byte  nameLen
  bytes name (UTF-8)
  byte  flags          ← bit 0 = ready, bit 1 = connected
```

---

### Project modules

| Module | Role |
|--------|------|
| `core/` | Simulation, snapshots, protocol, UDP client/server, rendering, UI logic, tests |
| `lwjgl3/` | Desktop launcher (window config + `CoreGame`) |
| `server/` | Headless server entry-point (`HeadlessServer`) |

### Core packages

| Package | Key classes |
|---------|-------------|
| `com.battlecity.core` | `CoreGame` (phase FSM), `AppPhase`, phase drivers |
| `com.battlecity.game` | `Simulation`, `World`, `LocalMatchController`, `TutorialScript`, systems, `StateHasher` |
| `com.battlecity.game.snapshot` | Immutable `GameSnapshot`, `NetStatsSnapshot` |
| `com.battlecity.net.protocol` | `MessageCodec`, `PacketValidator`, `LobbyPhase` |
| `com.battlecity.net.server` | `GameServer`, `ServerLobby` (headless FSM), `ClientConnection` |
| `com.battlecity.net.client` | `GameClient`, `LobbySnapshot` |
| `com.battlecity.render` | `SnapshotRenderer` (read-only draw from snapshots) |
| `com.battlecity.input` | Keyboard → game commands |
| `com.battlecity.ui` | `MainMenuScreen`, `LobbyScreen`, `TutorialOverlay`, `DebugOverlay`, `MatchEndScreen` |

---

### Linux / WSL setup

The **server** is headless — runs fine on WSL or a remote Linux machine.  
The **client** needs a display (WSLg on Windows 11, or native Linux desktop).

```bash
# One-time packages (Ubuntu/Debian):
sudo apt install -y openjdk-17-jdk libgl1-mesa-dri libgl1
# WSLg (Windows 11) — ensure GUI support is current:
# from PowerShell (Admin): wsl --update
```

| Issue | Fix |
|-------|-----|
| Blank/gray window | Avoid software OpenGL — use WSLg GPU path (`./scripts/run-client-wsl.sh`) or run the client on Windows (`.\gradlew.bat :lwjgl3:run`) |
| `libEGL` / MESA errors | Install `libgl1-mesa-dri`; update WSL (`wsl --update`) |
| No window at all | `export DISPLAY=:0` (WSLg); check `libgl1` is installed |
| PipeWire / ALSA errors on WSL | Harmless; audio is disabled automatically under WSL |
| `-XstartOnFirstThread` error | macOS-only flag — should not appear on Linux; pull latest `lwjgl3/build.gradle` |

---

### Design notes

- **Transport:** UDP (`DatagramSocket`), background receiver thread, polled on game thread.
- **Authority:** The server runs the authoritative 60 Hz simulation; clients send input commands only.
- **Determinism:** Fixed tick order (consume commands → movement → collision → projectiles → damage → events); `TreeMap` for sorted player iteration; seeded RNG; no wall-clock reads inside simulation.
- **Snapshots:** Full `GameSnapshot` per tick (no deltas in v1) — `serverTick`, deterministic `stateHash`, tanks, projectiles, tile grid.
- **Reliability:** Per-packet `seq` / `ack`; duplicate inputs ignored; ping/pong RTT; packet-loss from sent vs acked counters.
- **Rendering:** Clients draw from immutable snapshots with linear interpolation (`prev → cur`, alpha); no simulation mutation in render path.
- **Tank-vs-tank collision:** solid AABB; lower `playerId` wins when both moved into overlap.
