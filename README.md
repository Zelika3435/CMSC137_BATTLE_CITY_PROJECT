## Battle City (LibGDX) — Starter

### How to run
- **Desktop app**:

```bash
.\gradlew.bat :lwjgl3:run
```

- **Run headless tests**:

```bash
.\gradlew.bat :core:test
```

### Controls
- **Move / set facing**: Arrow keys or WASD (axis-aligned; no diagonals)
- **Fire**: SPACE (spawns a bullet)
- **Toggle debug text**: F3

### What you should see
- A 26x26 tile map (16px tiles) with:
  - **STEEL** border walls
  - a few **BRICK** clusters (bullets destroy BRICK)
  - a small **STEEL** block (bullets stop)
- A green tank rectangle (placeholder)
- Yellow bullet rectangles (placeholder)

### Project modules
- **`core/`**: game simulation + rules + headless tests (no desktop/GL backend code)
- **`lwjgl3/`**: desktop launcher only

### Core packages (current)
All code is currently in `com.battlecity.core` (early prototype stage). Key classes:
- **Simulation / state**
  - `CoreGame`: fixed-timestep loop (60 ticks/sec) + render
  - `Sim`: tick-step entry point
  - `GameState`: mutable sim state (player, map, projectile pool)
- **Entities**
  - `Tank`: player tank (position, dir, speed, bounds)
  - `Projectile`: bullets (pooled)
  - `Direction`: `UP/DOWN/LEFT/RIGHT`
- **Map + collision**
  - `TileMap`, `Tile`: tile grid and tile types (`EMPTY/BRICK/STEEL/BASE`)
  - `CollisionSystem`: tank AABB vs tile-grid resolution
  - `ProjectileSystem`: bullet spawn/move + tile impacts

### Notes
- Rendering uses placeholder rectangles until sprites are added under `core/src/main/resources/assets/`.

