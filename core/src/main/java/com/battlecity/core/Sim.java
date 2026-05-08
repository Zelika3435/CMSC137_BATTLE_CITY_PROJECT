package com.battlecity.core;

import com.badlogic.gdx.math.MathUtils;

final class Sim {
  private Sim() {}

  static void step(GameState state, Direction inputDir, boolean firePressed, float fixedDtSeconds) {
    Tank p = state.player;

    // --- Player movement (only if alive) ---
    if (p.alive) {
      p.prevX = p.x;
      p.prevY = p.y;

      if (inputDir != null) {
        p.dir = inputDir;

        float movePerTick = p.speed * fixedDtSeconds;
        switch (p.dir) {
          case LEFT -> p.x -= movePerTick;
          case RIGHT -> p.x += movePerTick;
          case UP -> p.y += movePerTick;
          case DOWN -> p.y -= movePerTick;
        }
      }

      CollisionSystem.resolveTankVsMap(p, state.map);

      if (firePressed) {
        state.fireCount++;
        ProjectileSystem.trySpawn(state, p);
      }

      p.x = MathUtils.clamp(p.x, p.halfW, state.map.worldW() - p.halfW);
      p.y = MathUtils.clamp(p.y, p.halfH, state.map.worldH() - p.halfH);
    }

    // --- Enemy bot AI ---
    for (Tank enemy : state.enemies) {
      if (enemy.alive) {
        BotAI.update(enemy, state, fixedDtSeconds);
      }
    }

    // --- Projectiles (all bullets, regardless of owner) ---
    ProjectileSystem.tick(state, fixedDtSeconds);

    state.tickCount++;
  }
}
