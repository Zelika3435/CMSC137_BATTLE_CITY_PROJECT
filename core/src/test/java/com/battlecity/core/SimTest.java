package com.battlecity.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}

