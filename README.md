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
- The player with the **lowest ID** is the **host** and can **force-start** the match from the lobby (no need to wait for everyone to ready up). During the countdown the UI shows **"Match starting…"** instead of the force-start hint; the match begins automatically when the timer reaches zero.
- Countdown ticks are taken directly from the server's `LOBBY_STATE` (not wall-clock).

| Action | Key |
|--------|-----|
| Toggle ready | **R** |
| Force-start (host only; hint shown in LOBBY; keys still work during COUNTDOWN) | **ENTER**, **SPACE**, or numpad **ENTER** |
| Leave lobby | **ESC** — sends `DISCONNECT`, frees server slot, returns to main menu |

**Auto-start:** when all connected players (minimum 2) are ready the server automatically begins the 3-second countdown. The host can skip the countdown (or start solo) with force-start. Any player disconnecting during the countdown drops the count; if it falls below 2 the countdown cancels and the lobby resets.

#### In-match

- A **"GO!"** overlay fades out over ~1.5 s when the first server snapshot arrives.
- **WASD / arrow keys** — move / face; **SPACE** — fire.
- The client only sends input commands; the server is authoritative for all positions, collisions, and tile destruction.

#### Why the host feels faster (loopback vs LAN)

The in-process **HOST** path is not the same network path as a **JOIN** client, even when everyone is in the same room:

| | Host (in-process) | Joiner (LAN) |
|---|-------------------|--------------|
| Transport | Client → `127.0.0.1` loopback | Client → host's LAN IP over UDP |
| Typical RTT | ≈ 0–2 ms | Wired ≈ 1–10 ms; Wi‑Fi often 15–80 ms+ |
| Snapshot path | Same process; no radio/switch jitter | One hop per direction; loss/jitter possible |
| Render delay | Still uses the interpolation buffer, but fresh snapshots arrive every tick | Same buffer, but snapshots age while in flight |

So the host's tank tends to feel snappier: inputs reach the authoritative sim almost instantly, server snapshots come back on loopback, and **`LocalPrediction`** reconciles with very low `pred_err`. Joiners pay real one-way latency before the server sees input, then again before the next snapshot arrives; prediction hides most of that for **your own** tank, but remote tanks are always drawn from delayed, interpolated server state.

This is expected — not a simulation bug — as long as joiners are not systematically one tick (or more) behind the host after mitigation (input lead + late-input drain + prediction).

#### Verifying fair play on LAN (F3 checklist)

Use the **joiner's** machine as the probe (the host on loopback will always look “too good” to be meaningful).

1. **Setup:** one machine **HOST** (`Mode = HOST`), 1–3 others **JOIN** with the Share IP from the lobby. Prefer **wired Ethernet** on the first pass.
2. **Start a match** (auto-start when all ready, or host force-start). Press **F3** on each joiner once `MP_MATCH` is running.
3. **Healthy joiner (wired LAN)** — expect roughly:
   - `rtt` — low tens of ms or less on a quiet LAN
   - `snap/s` — ~60 (one authoritative snapshot per server tick)
   - `snap_age` — usually 0–2 ticks; sustained 5+ means snapshots are stalling
   - `in_unacked` — 0 or briefly small; stuck high means INPUTs are not being acked
   - `pred_err` — small; `[SNAP]` only after big corrections (e.g. wall block)
4. **Wi‑Fi vs wired:** repeat on the same joiner over Wi‑Fi. `rtt` and `snap_age` should rise versus wired; gameplay may feel slightly heavier but should remain playable.
5. **2–4 player stress:** all players move and fire at once for ~30 s. Host and joiners should stay in sync visually; joiner `pred_err` should not climb without `[SNAP]` spam. If only the host feels instant while every joiner shows high `snap_age` or `in_unacked`, check firewall UDP on the host port, mixed subnets, or Wi‑Fi isolation — not the fixed-timestep sim.

Press **F3** again to hide the overlay; it does not affect simulation or network traffic.

#### Protocol notes (input tick policy & snapshots)

**Input tick policy (client → server)**

- Clients send **`INPUT`** only (move / fire); never positions or hits.
- Each input carries `tickStamp = lastServerTick + INPUT_LEAD_TICKS` (**+2 ticks**, ~33 ms at 60 Hz) so a typical LAN one-way delay still lands in the server's near future.
- Server **`ClientConnection.drainInputsForTick`**: applies every queued command with `tickStamp ≤ currentTick`. Late arrivals (network jitter) are **normalized to the current tick** instead of dropped.
- **`MAX_INPUT_FUTURE_TICKS = 4`**: stamps farther ahead are rejected (clock skew / abuse guard).
- Duplicate `(playerId, seq)` pairs are ignored. SNAPSHOT header **`ack`** echoes the highest processed client INPUT seq (F3 `in_unacked`).

**Snapshots (server → client)**

- First tick after **RUNNING**: **`FULL_MAP`** — entire 26×26 tile grid plus entities and `stateHash`.
- Every later tick: **`DELTA`** — entity pose/state plus **sorted** `(tileIndex, tileOrdinal)` changes only; clients merge into a local tile buffer.
- If an encoded delta would exceed **`MAX_PACKET_BYTES` (4096)**, the server sends **`FULL_MAP`** for that tick instead.
- Clients render from immutable merged snapshots; tile destruction on joiners matches the host because deltas (or full resync) carry every mutation.

See **Protocol overview → `SNAPSHOT` body** below for the on-wire layout.

#### Input-lag handling (joiner clients)

See **Protocol notes** above for the full input tick policy. In short: joiners stamp **`+2 ticks` ahead**; the server drains **`tickStamp ≤ currentTick`** and normalises late inputs to the current tick. Constants:

| Constant | Value | Effect |
|----------|-------|--------|
| `INPUT_LEAD_TICKS` | 2 | Ticks ahead the client stamps each input |
| `MAX_INPUT_FUTURE_TICKS` | 4 | Maximum future ticks the server accepts |

The host (loopback, RTT ≈ 0) still queues inputs for two ticks before drain — within the normal 120-tick expiry window — which is why loopback feels tighter than Wi‑Fi joiners even with identical sim rules.

#### Client-side prediction (local player, MP_MATCH only)

To remove the visible RTT lag for the player controlling their own tank, `MP_MATCH` runs a lightweight local preview of the local player's tank in parallel with the authoritative server stream:

- **`LocalPrediction`** (`com.battlecity.game`) runs `MovementSystem` on a private `Tank` copy at 60 Hz, applying the same input sent to the server each tick.
- On every received **server SNAPSHOT** the predicted pose is **reconciled** against the server-authoritative position:
  - **Smooth correction** (error ≤ 16 world units): 25 % of the gap is blended away each snapshot — imperceptible to the player.
  - **Hard snap** (error > 16 world units, or tank died): position jumps to server truth immediately.
  - Direction and alive flag are always corrected to the server value.
- **Remote tanks** are never predicted — they use server-snapshot interpolation only.
- **Snapshot interpolation buffer** (`SnapshotInterpolationBuffer`, default **75 ms** delay on LAN): incoming snapshots are queued by `serverTick` and rendered at `estimatedServerTime − bufferMs`. Out-of-order packets replace the same tick; gaps hold the last good bracket. Tune delay via `SnapshotInterpolationBuffer.DEFAULT_BUFFER_MS` (typical LAN: 50–100 ms). F3 overlay shows `buf=<ms>` during `MP_MATCH`.
- **Server authority is never compromised**: `LocalPrediction` is render-only state; the server continues to run the deterministic simulation from actual client inputs.
- **Not active** in LOBBY, SINGLE_PLAYER, or TUTORIAL.

The F3 debug overlay shows `pred_err=<N>` (world units) and `[SNAP]` when a hard reconcile occurs.

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
| `dt` | Fixed simulation timestep (1/60 s) |
| `rtt` | Round-trip time from the last PING/PONG exchange (ms) |
| `snap/s` | Authoritative snapshots received per second (~1 s rolling window) |
| `snap_age` | Ticks since the last snapshot arrived (grows when snapshots stall) |
| `tanks` / `proj` | Entity counts from the latest `GameSnapshot` |
| `in_unacked` | INPUT messages not yet acked by the server (MP_MATCH only; hidden when 0) |
| `pred_err` | Client-side prediction positional error in world units (MP_MATCH only) |
| `[SNAP]` | Shown beside `pred_err` when the last reconcile was a hard snap |
| `buf` | MP_MATCH snapshot interpolation buffer delay (ms); default 75 |

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
| Lobby | ENTER / SPACE / numpad ENTER | Force-start (host only; hint in LOBBY, auto-start after countdown) |
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
- **F3 debug overlay:** `tick  dt  rtt  snap/s  snap_age  tanks  proj` in top-left corner.

---

### Protocol overview (v1, little-endian binary)

All messages share a **23-byte header**: `protocolVersion` (1 B) · `messageType` (1 B) · `sessionId` (4 B) · `playerId` (1 B) · `seq` (4 B) · `ack` (4 B) · `serverTick` (8 B).

| ID | Type | Dir | Purpose |
|----|------|-----|---------|
| 1  | `JOIN`        | C→S | Request slot; UTF-8 player name (max 32 chars) |
| 2  | `JOIN_ACK`    | S→C | `assignedPlayerId`, `sessionId`, `mapSeed`, `serverTick`, **`currentPhase`** |
| 3  | `INPUT`       | C→S | `tickStamp`, `MOVE_DIR` or `FIRE` (RUNNING phase only) |
| 4  | `SNAPSHOT`    | S→C | Authoritative state + `stateHash` (RUNNING only); see tile modes below |
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

#### `SNAPSHOT` body (tile-optimized)

The first snapshot after a match enters **RUNNING** uses **`FULL_MAP`** (entire 26×26 tile grid). Every subsequent tick uses **`DELTA`** (only changed tiles — sorted by tile index) plus tanks, projectiles, match flags, and `stateHash`. Clients merge deltas into a local tile buffer for rendering.

```
byte   formatOrdinal     0 = FULL_MAP, 1 = DELTA
long   stateHash
short  mapWidthTiles
short  mapHeightTiles
float  tileSize
if FULL_MAP:
  (width × height) tile ordinals
else DELTA:
  short  tileChangeCount
  for each change (sorted by tileIndex):
    short  tileIndex
    byte   tileOrdinal
byte   baseDestroyed
byte   matchOver
byte   tankCount + tank records (unchanged)
byte   projectileCount + projectile records (unchanged)
```

Per-tick traffic drops from ~676 tile bytes to a few bytes when the map is static. Packets stay within **4096 B** (`MAX_PACKET_BYTES`); if a delta would exceed the limit, the server falls back to `FULL_MAP` for that tick.

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
- **Snapshots:** First RUNNING tick is **`FULL_MAP`**; later ticks use **`DELTA`** tile changes (sorted indices) with **`FULL_MAP` fallback** when a packet would exceed 4096 B. All carry `serverTick`, `stateHash`, tanks, and projectiles.
- **Reliability:** Per-packet `seq` / `ack`; duplicate inputs ignored; ping/pong RTT; snapshot SNAPSHOT `ack` echoes highest processed client INPUT seq; F3 overlay shows snap/s, snap_age, and optional `in_unacked`.
- **Input-lag mitigation:** Clients stamp inputs `lastServerTick + INPUT_LEAD_TICKS` (2 ticks ahead).  Server drains all commands with `tickStamp ≤ currentTick` and normalises late tickStamps to the current tick so `Simulation.applyCommands` accepts them.  Future-stamp guard capped at `MAX_INPUT_FUTURE_TICKS = 4`.
- **Rendering:** Clients draw from immutable snapshots with linear interpolation (`prev → cur`, alpha); no simulation mutation in render path.
- **Tank-vs-tank collision:** solid AABB; lower `playerId` wins when both moved into overlap.
