package com.battlecity.core;

final class ProjectileSystem {
  private ProjectileSystem() {}

  /**
   * Attempt to spawn a projectile from the given tank.
   */
  static void trySpawn(GameState state, Tank source) {
    for (Projectile p : state.projectiles) {
      if (!p.active) {
        float muzzle = source.halfW + p.halfW + 1f;
        float x = source.x;
        float y = source.y;
        switch (source.dir) {
          case LEFT -> x -= muzzle;
          case RIGHT -> x += muzzle;
          case UP -> y += muzzle;
          case DOWN -> y -= muzzle;
        }
        p.spawn(x, y, source.dir, source.isPlayer);
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

      // Tile collision (unchanged logic)
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

      // Out-of-bounds
      if (p.x < 0f || p.x > map.worldW() || p.y < 0f || p.y > map.worldH()) {
        p.despawn();
        continue;
      }

      // Tank-hit detection
      if (p.ownerIsPlayer) {
        // Player bullet → check enemy tanks
        for (Tank enemy : state.enemies) {
          if (enemy.alive && aabbOverlap(p, enemy)) {
            enemy.alive = false;
            p.despawn();
            break;
          }
        }
      } else {
        // Enemy bullet → check player tank
        Tank player = state.player;
        if (player.alive && aabbOverlap(p, player)) {
          player.alive = false;
          p.despawn();
        }
      }
    }
  }

  /** Simple AABB overlap test between a projectile and a tank. */
  private static boolean aabbOverlap(Projectile p, Tank t) {
    return Math.abs(p.x - t.x) < (p.halfW + t.halfW)
        && Math.abs(p.y - t.y) < (p.halfH + t.halfH);
  }
}
