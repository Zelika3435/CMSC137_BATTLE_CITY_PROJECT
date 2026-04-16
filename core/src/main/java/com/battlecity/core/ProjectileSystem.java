package com.battlecity.core;

final class ProjectileSystem {
  private ProjectileSystem() {}

  static void trySpawn(GameState state) {
    for (Projectile p : state.projectiles) {
      if (!p.active) {
        float muzzle = state.player.halfW + p.halfW + 1f;
        float x = state.player.x;
        float y = state.player.y;
        switch (state.player.dir) {
          case LEFT -> x -= muzzle;
          case RIGHT -> x += muzzle;
          case UP -> y += muzzle;
          case DOWN -> y -= muzzle;
        }
        p.spawn(x, y, state.player.dir);
        return;
      }
    }
  }

  static void tick(GameState state, float fixedDtSeconds) {
    TileMap map = state.map;
    float tile = map.tileSize();

    for (Projectile p : state.projectiles) {
      if (!p.active) continue;

      p.prevX = p.x;
      p.prevY = p.y;

      float move = p.speed * fixedDtSeconds;
      switch (p.dir) {
        case LEFT -> p.x -= move;
        case RIGHT -> p.x += move;
        case UP -> p.y += move;
        case DOWN -> p.y -= move;
      }

      // Simple axis-aligned collision: sample the leading edge tile.
      int tx;
      int ty;
      switch (p.dir) {
        case RIGHT -> {
          tx = (int) Math.floor((p.x + p.halfW) / tile);
          ty = (int) Math.floor(p.y / tile);
        }
        case LEFT -> {
          tx = (int) Math.floor((p.x - p.halfW) / tile);
          ty = (int) Math.floor(p.y / tile);
        }
        case UP -> {
          tx = (int) Math.floor(p.x / tile);
          ty = (int) Math.floor((p.y + p.halfH) / tile);
        }
        case DOWN -> {
          tx = (int) Math.floor(p.x / tile);
          ty = (int) Math.floor((p.y - p.halfH) / tile);
        }
        default -> throw new IllegalStateException("unexpected dir " + p.dir);
      }

      Tile hit = map.tileAt(tx, ty);
      if (hit == Tile.BRICK) {
        map.setTile(tx, ty, Tile.EMPTY);
        p.despawn();
        continue;
      }
      if (hit == Tile.STEEL || hit == Tile.BASE) {
        p.despawn();
        continue;
      }

      if (p.x < 0f || p.x > map.worldW() || p.y < 0f || p.y > map.worldH()) {
        p.despawn();
      }
    }
  }
}

