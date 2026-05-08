package com.battlecity.core;

import java.util.Random;

/**
 * Simple AI controller for enemy tanks.
 * <p>
 * Behaviour:
 * <ul>
 *   <li><b>Patrol</b>: move in the current direction; when hitting a wall or
 *       obstacle, pick a new random direction.</li>
 *   <li><b>Chase</b>: if the player is roughly axis-aligned (within 2 tiles
 *       laterally), face the player and fire.</li>
 * </ul>
 */
final class BotAI {
  private static final int DECISION_INTERVAL_TICKS = 45; // re-evaluate ~every 0.75s
  private static final int FIRE_COOLDOWN_TICKS = 90;     // ~1.5 seconds between shots
  private static final float CHASE_LATERAL_THRESHOLD = 32f; // px (~2 tiles)

  private static final Direction[] DIRS = Direction.values();
  private static final Random RNG = new Random();

  private BotAI() {}

  /**
   * Called once per tick for each alive enemy.
   */
  static void update(Tank enemy, GameState state, float fixedDtSeconds) {
    if (!enemy.alive) return;

    // Decrease fire cooldown
    if (enemy.fireCooldownTicks > 0) {
      enemy.fireCooldownTicks--;
    }

    Tank player = state.player;

    // --- Chase: can we see the player along an axis? ---
    boolean chasing = false;
    if (player.alive) {
      float dx = player.x - enemy.x;
      float dy = player.y - enemy.y;

      if (Math.abs(dx) < CHASE_LATERAL_THRESHOLD && Math.abs(dy) > 16f) {
        // Player is roughly on the same column
        enemy.dir = (dy > 0) ? Direction.UP : Direction.DOWN;
        chasing = true;
      } else if (Math.abs(dy) < CHASE_LATERAL_THRESHOLD && Math.abs(dx) > 16f) {
        // Player is roughly on the same row
        enemy.dir = (dx > 0) ? Direction.RIGHT : Direction.LEFT;
        chasing = true;
      }
    }

    // --- Patrol (when not chasing): pick random direction periodically ---
    if (!chasing && state.tickCount % DECISION_INTERVAL_TICKS == 0) {
      enemy.dir = DIRS[RNG.nextInt(DIRS.length)];
    }

    // --- Move ---
    enemy.prevX = enemy.x;
    enemy.prevY = enemy.y;

    float movePerTick = enemy.speed * fixedDtSeconds;
    switch (enemy.dir) {
      case LEFT -> enemy.x -= movePerTick;
      case RIGHT -> enemy.x += movePerTick;
      case UP -> enemy.y += movePerTick;
      case DOWN -> enemy.y -= movePerTick;
    }

    // Collision
    CollisionSystem.resolveTankVsMap(enemy, state.map);

    // If patrol and we didn't actually move (wall), pick a new direction
    if (!chasing) {
      boolean stuck = (Math.abs(enemy.x - enemy.prevX) < 0.001f)
                   && (Math.abs(enemy.y - enemy.prevY) < 0.001f);
      if (stuck) {
        enemy.dir = DIRS[RNG.nextInt(DIRS.length)];
      }
    }

    // Clamp
    enemy.x = com.badlogic.gdx.math.MathUtils.clamp(
        enemy.x, enemy.halfW, state.map.worldW() - enemy.halfW);
    enemy.y = com.badlogic.gdx.math.MathUtils.clamp(
        enemy.y, enemy.halfH, state.map.worldH() - enemy.halfH);

    // --- Fire ---
    if (enemy.fireCooldownTicks <= 0) {
      boolean shouldFire = chasing || (RNG.nextInt(120) == 0); // occasional random shot while patrolling
      if (shouldFire) {
        ProjectileSystem.trySpawn(state, enemy);
        enemy.fireCooldownTicks = FIRE_COOLDOWN_TICKS;
      }
    }
  }
}
