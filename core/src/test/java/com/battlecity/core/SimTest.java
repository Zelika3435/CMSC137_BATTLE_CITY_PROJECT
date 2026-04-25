package com.battlecity.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SimTest {
  @Test
  void step_advancesTick_andMovesPlayer() {
    GameState s = new GameState();
    float fixedDt = 1f / 60f;

    long t0 = s.tickCount;
    float x0 = s.player.x;
    float y0 = s.player.y;

    Sim.step(s, Direction.RIGHT, false, fixedDt);

    assertEquals(t0 + 1, s.tickCount);
    assertTrue(s.player.x > x0);
    assertEquals(y0, s.player.y, 0.0001f);
  }

  @Test
  void sixtyStepsAt120UnitsPerSecond_movesAbout120Units() {
    GameState s = new GameState();
    float fixedDt = 1f / 60f;

    // Kill all enemies so their AI doesn't interfere with this movement test.
    for (Tank e : s.enemies) e.alive = false;

    float startX = s.player.x;
    for (int i = 0; i < 60; i++) {
      Sim.step(s, Direction.RIGHT, false, fixedDt);
    }

    assertEquals(startX + 120f, s.player.x, 0.01f);
    assertEquals(60L, s.tickCount);
  }

  @Test
  void movingIntoBrickWall_stopsAtTileBoundary() {
    GameState s = new GameState();
    float fixedDt = 1f / 60f;

    // Kill all enemies so their AI doesn't interfere.
    for (Tank e : s.enemies) e.alive = false;

    int wallTx = 5;
    int wallTy = 4;
    s.map.setTile(wallTx, wallTy, Tile.BRICK);

    float tile = s.map.tileSize();
    float wallLeft = wallTx * tile;

    s.player.y = wallTy * tile + tile / 2f;
    s.player.x = wallLeft - s.player.halfW - 2f;
    s.player.prevX = s.player.x;
    s.player.prevY = s.player.y;

    for (int i = 0; i < 10; i++) {
      Sim.step(s, Direction.RIGHT, false, fixedDt);
    }

    assertEquals(wallLeft - s.player.halfW, s.player.x, 0.0001f);
  }

  @Test
  void projectileIntoBrick_destroysBrick_andDespawnsProjectile() {
    GameState s = new GameState();
    float fixedDt = 1f / 60f;

    // Kill all enemies so their AI doesn't interfere.
    for (Tank e : s.enemies) e.alive = false;

    for (int y = 0; y < s.map.heightTiles(); y++) {
      for (int x = 0; x < s.map.widthTiles(); x++) {
        s.map.setTile(x, y, Tile.EMPTY);
      }
    }

    int wallTx = 8;
    int wallTy = 8;
    s.map.setTile(wallTx, wallTy, Tile.BRICK);

    float tile = s.map.tileSize();
    float wallLeft = wallTx * tile;

    s.player.dir = Direction.RIGHT;
    s.player.x = wallLeft - s.player.halfW - 10f;
    s.player.y = wallTy * tile + tile / 2f;
    s.player.prevX = s.player.x;
    s.player.prevY = s.player.y;

    Sim.step(s, null, true, fixedDt); // fire

    for (int i = 0; i < 120; i++) {
      Sim.step(s, null, false, fixedDt);
      if (s.map.tileAt(wallTx, wallTy) == Tile.EMPTY) break;
    }

    assertEquals(Tile.EMPTY, s.map.tileAt(wallTx, wallTy));
    for (Projectile p : s.projectiles) {
      assertEquals(false, p.active);
    }
  }

  @Test
  void playerBullet_killsEnemy() {
    GameState s = new GameState();
    float fixedDt = 1f / 60f;

    // Clear all walls so bullets travel freely.
    for (int y = 0; y < s.map.heightTiles(); y++) {
      for (int x = 0; x < s.map.widthTiles(); x++) {
        s.map.setTile(x, y, Tile.EMPTY);
      }
    }

    // Kill all default enemies, then set up a controlled target.
    for (Tank e : s.enemies) e.alive = false;

    Tank target = s.enemies[0];
    target.alive = true;
    target.x = s.player.x + 60f;  // 60px to the right of the player
    target.y = s.player.y;
    target.prevX = target.x;
    target.prevY = target.y;
    target.fireCooldownTicks = 9999; // prevent enemy from firing

    s.player.dir = Direction.RIGHT;
    s.player.prevX = s.player.x;
    s.player.prevY = s.player.y;

    // Fire a bullet toward the enemy
    Sim.step(s, null, true, fixedDt);

    // Tick until the bullet reaches the enemy
    for (int i = 0; i < 300; i++) {
      Sim.step(s, null, false, fixedDt);
      if (!target.alive) break;
    }

    assertFalse(target.alive, "Enemy should be killed by player bullet");
  }

  @Test
  void enemyBullet_killsPlayer() {
    GameState s = new GameState();
    float fixedDt = 1f / 60f;

    // Clear all walls so bullets travel freely.
    for (int y = 0; y < s.map.heightTiles(); y++) {
      for (int x = 0; x < s.map.widthTiles(); x++) {
        s.map.setTile(x, y, Tile.EMPTY);
      }
    }

    // Kill all default enemies except one controlled enemy.
    for (Tank e : s.enemies) e.alive = false;

    Tank enemy = s.enemies[0];
    enemy.alive = true;
    enemy.x = s.player.x;
    enemy.y = s.player.y + 60f;  // 60px above the player
    enemy.prevX = enemy.x;
    enemy.prevY = enemy.y;
    enemy.dir = Direction.DOWN;   // facing toward the player
    enemy.fireCooldownTicks = 0;

    // Manually spawn an enemy bullet aimed at the player
    ProjectileSystem.trySpawn(s, enemy);

    // Tick until bullet reaches the player
    for (int i = 0; i < 300; i++) {
      Sim.step(s, null, false, fixedDt);
      if (!s.player.alive) break;
    }

    assertFalse(s.player.alive, "Player should be killed by enemy bullet");
  }
}
