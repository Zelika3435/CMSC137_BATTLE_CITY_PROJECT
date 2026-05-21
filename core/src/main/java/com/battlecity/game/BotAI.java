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

    public void update(Tank enemy, World world, float fixedDtSeconds, boolean[] isBot) {
        if (!enemy.alive || world.matchOver) {
            return;
        }

        if (enemy.fireCooldownTicks > 0) {
            enemy.fireCooldownTicks--;
        }

        Tank target = getClosestTarget(enemy, world, isBot);

        boolean chasing = false;
        if (target != null) {
            float dx = target.x - enemy.x;
            float dy = target.y - enemy.y;

            if (Math.abs(dx) < CHASE_LATERAL_THRESHOLD && Math.abs(dy) > 16f && hasVerticalLineOfSight(enemy, target, world.map)) {
                enemy.dir = dy > 0 ? Direction.UP : Direction.DOWN;
                chasing = true;
            } else if (Math.abs(dy) < CHASE_LATERAL_THRESHOLD && Math.abs(dx) > 16f && hasHorizontalLineOfSight(enemy, target, world.map)) {
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

    private boolean hasVerticalLineOfSight(Tank enemy, Tank target, TileMap map) {
        float tileSize = map.tileSize();
        int tx = (int) (enemy.x / tileSize);
        int startY = (int) (Math.min(enemy.y, target.y) / tileSize);
        int endY = (int) (Math.max(enemy.y, target.y) / tileSize);
        for (int y = startY; y <= endY; y++) {
            if (map.isSolid(tx, y)) return false;
        }
        return true;
    }

    private boolean hasHorizontalLineOfSight(Tank enemy, Tank target, TileMap map) {
        float tileSize = map.tileSize();
        int ty = (int) (enemy.y / tileSize);
        int startX = (int) (Math.min(enemy.x, target.x) / tileSize);
        int endX = (int) (Math.max(enemy.x, target.x) / tileSize);
        for (int x = startX; x <= endX; x++) {
            if (map.isSolid(x, ty)) return false;
        }
        return true;
    }

    private Tank getClosestTarget(Tank enemy, World world, boolean[] isBot) {
        Tank closest = null;
        float minDistSq = Float.MAX_VALUE;
        for (int i = 0; i < World.MAX_PLAYERS; i++) {
            if (isBot[i]) continue;
            Tank human = world.tanks[i];
            if (human != null && human.alive) {
                float dx = human.x - enemy.x;
                float dy = human.y - enemy.y;
                float distSq = dx * dx + dy * dy;
                if (distSq < minDistSq) {
                    minDistSq = distSq;
                    closest = human;
                }
            }
        }
        return closest;
    }
}
