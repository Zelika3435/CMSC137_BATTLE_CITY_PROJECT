package com.battlecity.game;

/**
 * Offline skirmish AI only. Uses seeded {@link Rng} for determinism.
 */
public final class BotAI {
    private static final int DECISION_INTERVAL_TICKS = 45;
    private static final int FIRE_COOLDOWN_TICKS = 90;
    private static final float CHASE_LATERAL_THRESHOLD = 32f;

    private final Rng rng;
    private final Direction[] dirs = Direction.values();

    public BotAI(Rng rng) {
        this.rng = rng;
    }

    public void update(Tank enemy, World world, float fixedDtSeconds, Tank target) {
        if (!enemy.alive || world.matchOver) {
            return;
        }

        if (enemy.fireCooldownTicks > 0) {
            enemy.fireCooldownTicks--;
        }

        boolean chasing = false;
        if (target != null && target.alive) {
            float dx = target.x - enemy.x;
            float dy = target.y - enemy.y;

            if (Math.abs(dx) < CHASE_LATERAL_THRESHOLD && Math.abs(dy) > 16f) {
                enemy.dir = dy > 0 ? Direction.UP : Direction.DOWN;
                chasing = true;
            } else if (Math.abs(dy) < CHASE_LATERAL_THRESHOLD && Math.abs(dx) > 16f) {
                enemy.dir = dx > 0 ? Direction.RIGHT : Direction.LEFT;
                chasing = true;
            }
        }

        if (!chasing && world.tickCount % DECISION_INTERVAL_TICKS == 0) {
            enemy.dir = dirs[rng.nextInt(dirs.length)];
        }

        MovementSystem.moveTank(enemy, enemy.dir, fixedDtSeconds, world.map);

        if (!chasing) {
            boolean stuck = Math.abs(enemy.x - enemy.prevX) < 0.001f
                    && Math.abs(enemy.y - enemy.prevY) < 0.001f;
            if (stuck) {
                enemy.dir = dirs[rng.nextInt(dirs.length)];
            }
        }

        if (enemy.fireCooldownTicks <= 0) {
            boolean shouldFire = chasing || rng.nextInt(120) == 0;
            if (shouldFire) {
                ProjectileSystem.trySpawn(world, enemy);
                enemy.fireCooldownTicks = FIRE_COOLDOWN_TICKS;
            }
        }
    }
}
