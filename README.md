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
# or: ./gradlew :lwjgl3:runWsl
```

**Native Linux** (real GPU):

```bash
./scripts/run-client.sh 127.0.0.1 9000
```

| Issue | Fix |
|-------|-----|
| `-XstartOnFirstThread` on Linux | Run `./gradlew --stop`, pull latest `lwjgl3/build.gradle` (Mac-only flag) |
| PipeWire / ALSA errors | Harmless on WSL; launcher disables audio under WSL automatically |
| `libEGL` / MESA / black window | Use `./gradlew :lwjgl3:runWsl` or `./scripts/run-client-wsl.sh` |
| No window at all | `export DISPLAY=:0` (WSLg); install `libgl1-mesa-dri` |

`-XstartOnFirstThread` is **macOS only** (never used on Linux/WSL).

**Offline skirmish** (1 human vs bots, no server):

```bash
./gradlew :lwjgl3:run
```

**Tests:**

```bash
./gradlew :core:test
```

### Controls
- **Move / facing:** Arrow keys or WASD (axis-aligned)
- **Fire:** SPACE
- **Debug overlay** (tick, ping, packet loss, entity counts): **F3** (default **off**)

### What you should see
- 26×26 tile arena (steel border, brick clusters, **BASE** at top-center)
- Four player tanks (distinct colors; your tank highlighted white)
- Yellow bullets; brick destruction synced by server
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
- `com.battlecity.game` — `Simulation`, `World`, systems, events, `StateHasher`
- `com.battlecity.game.snapshot` — immutable `GameSnapshot`, `NetStatsSnapshot`
- `com.battlecity.net.protocol` — `MessageCodec`, `PacketValidator`
- `com.battlecity.net.server` / `net.client` — `GameServer`, `GameClient`
- `com.battlecity.render` — `SnapshotRenderer` (read-only draw)
- `com.battlecity.input` — keyboard → commands
- `com.battlecity.ui` — debug overlay

### Notes
- Tank-vs-tank: solid AABB; lower `playerId` wins when both moved into overlap.
- Placeholder rectangles until sprites are added under `core/src/main/resources/assets/`.
