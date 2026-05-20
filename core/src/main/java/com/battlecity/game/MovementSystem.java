package com.battlecity.game;

public final class MovementSystem {
    private MovementSystem() {}

    public static void moveTank(Tank tank, Direction inputDir, float fixedDtSeconds, TileMap map) {
        if (!tank.alive || inputDir == null) {
            return;
        }

        tank.prevX = tank.x;
        tank.prevY = tank.y;
        tank.dir = inputDir;

        float movePerTick = tank.speed * fixedDtSeconds;
        switch (tank.dir) {
            case LEFT -> tank.x -= movePerTick;
            case RIGHT -> tank.x += movePerTick;
            case UP -> tank.y += movePerTick;
            case DOWN -> tank.y -= movePerTick;
        }

        CollisionSystem.resolveTankVsMap(tank, map);
        tank.x = MathUtil.clamp(tank.x, tank.halfW, map.worldW() - tank.halfW);
        tank.y = MathUtil.clamp(tank.y, tank.halfH, map.worldH() - tank.halfH);
    }
}
