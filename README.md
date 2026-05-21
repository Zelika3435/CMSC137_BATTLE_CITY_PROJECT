## Battle City (LibGDX) — 4-player authoritative multiplayer

### Design (v1)
- **Transport:** UDP (`DatagramSocket`), background receiver thread, poll on game thread.
- **Authority:** Headless server runs the same fixed **60 Hz** simulation as single-player; clients send **input commands only** (`MOVE_DIR`, `FIRE`).
- **Snapshots:** Full `GameSnapshot` per tick (no deltas in v1) with `serverTick`, deterministic `stateHash`, tanks (4 players), projectiles, tile grid.
- **Reliability:** Per-packet `seq` / `ack`; duplicate inputs ignored; ping/pong for RTT; packet-loss estimated from sent vs acked counters.
- **Rendering:** Clients draw from immutable snapshots with interpolation (`prev` → `cur`, `alpha`); no simulation mutation in render.

### How to run

**Requirements:** JDK **17** with `javac` (full JDK, not JRE-only). On Ubuntu/Debian: `sudo apt install openjdk-17-jdk`. If the build fails with “does not provide JAVA_COMPILER”, install that package or uncomment `org.gradle.java.home` in `gradle.properties` to point at your JDK 17 install.

**1. Start the authoritative server (headless, no OpenGL):**

```bash
./gradlew :server:run
# custom port:
./gradlew :server:run --args="9000"
```

**2. Start up to four desktop clients** (separate terminals):

```bash
./gradlew :lwjgl3:run --args="127.0.0.1 9000"
```

### Linux and WSL (recommended)

The **server** is headless and works well on WSL/Linux. The **client** needs a display (WSLg on Windows 11, or native Linux desktop).

**One-time setup (WSL/Ubuntu):**

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk libgl1-mesa-dri libgl1
# WSLg (Windows 11): ensure GUI apps work — from PowerShell: wsl --update
```

**Terminal 1 — server:**

```bash
./scripts/run-server.sh
# or: ./gradlew :server:run
```

**Terminal 2 — client on WSL** (use this if you see MESA/EGL errors or a blank window):

```bash
./scripts/run-client-wsl.sh
# or: ./gradlew :lwjgl3:run
```

If the window opens but stays **blank/gray**, do **not** use software OpenGL on WSLg — that is what caused the blank window. Instead:

1. Close the client (Ctrl+C in its terminal).
2. Update WSL from **PowerShell (Admin)**: `wsl --update`, then restart WSL.
3. Retry: `./scripts/run-client-wsl.sh` (uses WSLg GPU / D3D12).
4. Last resort software fallback: `./scripts/run-client-wsl-software.sh`
5. **Most reliable:** run the client on **Windows** (not WSL): open PowerShell, `cd` to a copy of the project on `C:\`, then `.\gradlew.bat :lwjgl3:run` (requires JDK 17+ on Windows).

**Native Linux** (real GPU):

```bash
./scripts/run-client.sh 127.0.0.1 9000
```

| Issue | Fix |
|-------|-----|
| `-XstartOnFirstThread` on Linux | Run `./gradlew --stop`, pull latest `lwjgl3/build.gradle` (Mac-only flag) |
| PipeWire / ALSA errors | Harmless on WSL; launcher disables audio under WSL automatically |
| `libEGL` / MESA / black window | Use `./scripts/run-client-wsl.sh` (WSLg GPU). Avoid `runWsl`/software GL unless GPU path fails |
| Blank gray window (title may show "Battle City") | You likely used software OpenGL (`runWsl` / old `run-client-wsl.sh`). Use `./scripts/run-client-wsl.sh` or run `.\gradlew.bat :lwjgl3:run` on Windows |
| No window at all | `export DISPLAY=:0` (WSLg); install `libgl1-mesa-dri` |

`-XstartOnFirstThread` is **macOS only** (never used on Linux/WSL).

**Offline single player / tutorial** (no server needed):

```bash
./gradlew :lwjgl3:run
# → starts at the main menu; choose Single Player or Tutorial
```

**Tests:**

```bash
./gradlew :core:test
```

### App phases
The client always starts at the **main menu**; no simulation or network is started until the player
picks a mode:

| Phase | Tick loop | Description |
|-------|-----------|-------------|
| `MAIN_MENU` | variable dt | Keyboard menu: Single Player · Multiplayer · Tutorial |
| `SP_PRESTART` | variable dt | Seed picker before single-player match (Fixed 42 or Random) |
| `SINGLE_PLAYER` | 60 Hz fixed | 1 human vs 3 bot enemies; deterministic for a given seed |
| `TUTORIAL` | 60 Hz fixed | 1 human, no bots — learn controls |
| `MP_CONNECT` | variable dt | Sending JOIN, waiting for ack |
| `MP_LOBBY` | variable dt | Connected, waiting for first server snapshot |
| `MP_MATCH` | 60 Hz fixed | Multiplayer match; renders server snapshots |
| `MATCH_END` | variable dt | Outcome screen; press ENTER to return to menu |

### Controls
- **Navigate menu / seed panel:** W/S or UP/DOWN; **confirm:** ENTER or SPACE; **back:** ESC
- **Move / facing (in-game):** Arrow keys or WASD (axis-aligned)
- **Fire:** SPACE
- **Debug overlay** (tick, ping, packet loss, entity counts): **F3** (default **off**)
- **Return to menu (in-game):** ESC

### What you should see
- Main menu with Single Player / Tutorial / Multiplayer / Quit options
- 26×26 tile arena (steel border, brick clusters, **BASE** at top-center)
- Four player tanks (distinct colors; your tank highlighted white)
- Yellow bullets; brick destruction synced by server
- Match-end screen with duration and tanks-remaining count
- With F3: `tick`, `dt`, `ping`, `loss%`, tank/projectile counts

### Protocol overview (v1, little-endian binary)
All messages share a header: `protocolVersion` (1), `messageType`, `sessionId`, `playerId`, `seq`, `ack`, `serverTick`.

| Type | Direction | Purpose |
|------|-----------|---------|
| `JOIN` | C→S | Request slot; UTF-8 player name |
| `JOIN_ACK` | S→C | `playerId`, `sessionId`, `mapSeed`, `serverTick` |
| `INPUT` | C→S | `tickStamp`, `MOVE_DIR` or `FIRE` |
| `SNAPSHOT` | S→C | Full authoritative state + `stateHash` |
| `PING` / `PONG` | C↔S | RTT measurement |
| `DISCONNECT` / `ERROR` | C↔S | Teardown / validation errors |

Invalid packets are rejected (never applied). Clients **never** send positions.

### Project modules
| Module | Role |
|--------|------|
| `core/` | Simulation, snapshots, protocol, UDP client/server, rendering, tests |
| `lwjgl3/` | Desktop launcher (window + `CoreGame`) |
| `server/` | Headless server entry (`HeadlessServer`) |

### Core packages
- `com.battlecity.core` — `CoreGame` (phase state machine), `AppPhase`, `PhaseHandler`, phase drivers
- `com.battlecity.game` — `Simulation`, `World`, `LocalMatchController`, systems, events, `StateHasher`
- `com.battlecity.game.snapshot` — immutable `GameSnapshot`, `NetStatsSnapshot`
- `com.battlecity.net.protocol` — `MessageCodec`, `PacketValidator`
- `com.battlecity.net.server` / `net.client` — `GameServer`, `GameClient`
- `com.battlecity.render` — `SnapshotRenderer` (read-only draw from snapshots)
- `com.battlecity.input` — keyboard → commands
- `com.battlecity.ui` — `MainMenuScreen`, `SinglePlayerPreStartScreen`, `ConnectScreen`, `LobbyScreen`, `MatchEndScreen`, `DebugOverlay`

### Notes
- Tank-vs-tank: solid AABB; lower `playerId` wins when both moved into overlap.
- Placeholder rectangles until sprites are added under `core/src/main/resources/assets/`.
