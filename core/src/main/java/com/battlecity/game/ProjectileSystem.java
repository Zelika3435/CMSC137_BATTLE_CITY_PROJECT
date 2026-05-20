package com.battlecity.game;

import com.battlecity.game.event.BaseHit;
import com.battlecity.game.event.ProjectileHitTank;
import com.battlecity.game.event.TileDestroyed;

public final class ProjectileSystem {
    private ProjectileSystem() {}

    public static void trySpawn(World world, Tank source) {
        for (Projectile projectile : world.projectiles) {
            if (!projectile.active) {
                float muzzle = source.halfW + projectile.halfW + 1f;
                float x = source.x;
                float y = source.y;
                switch (source.dir) {
                    case LEFT -> x -= muzzle;
                    case RIGHT -> x += muzzle;
                    case UP -> y += muzzle;
                    case DOWN -> y -= muzzle;
                }
                projectile.spawn(x, y, source.dir, source.playerId);
                return;
            }
        }
    }

    public static void tick(World world, float fixedDtSeconds) {
        TileMap map = world.map;
        float tile = map.tileSize();

        for (Projectile projectile : world.projectiles) {
            if (!projectile.active) {
                continue;
            }

            projectile.prevX = projectile.x;
            projectile.prevY = projectile.y;

            float move = projectile.speed * fixedDtSeconds;
            switch (projectile.dir) {
                case LEFT -> projectile.x -= move;
                case RIGHT -> projectile.x += move;
                case UP -> projectile.y += move;
                case DOWN -> projectile.y -= move;
            }

            int tx;
            int ty;
            switch (projectile.dir) {
                case RIGHT -> {
                    tx = (int) Math.floor((projectile.x + projectile.halfW) / tile);
                    ty = (int) Math.floor(projectile.y / tile);
                }
                case LEFT -> {
                    tx = (int) Math.floor((projectile.x - projectile.halfW) / tile);
                    ty = (int) Math.floor(projectile.y / tile);
                }
                case UP -> {
                    tx = (int) Math.floor(projectile.x / tile);
                    ty = (int) Math.floor((projectile.y + projectile.halfH) / tile);
                }
                case DOWN -> {
                    tx = (int) Math.floor(projectile.x / tile);
                    ty = (int) Math.floor((projectile.y - projectile.halfH) / tile);
                }
                default -> throw new IllegalStateException("unexpected dir " + projectile.dir);
            }

            Tile hit = map.tileAt(tx, ty);
            if (hit == Tile.BRICK) {
                world.events.emit(new TileDestroyed(tx, ty, hit));
                projectile.despawn();
                continue;
            }
            if (hit == Tile.BASE) {
                world.events.emit(new BaseHit(tx, ty, projectile.ownerPlayerId));
                projectile.despawn();
                continue;
            }
            if (hit == Tile.STEEL) {
                projectile.despawn();
                continue;
            }

            if (projectile.x < 0f || projectile.x > map.worldW()
                    || projectile.y < 0f || projectile.y > map.worldH()) {
                projectile.despawn();
                continue;
            }

            for (Tank tank : world.tanks) {
                if (tank == null || !tank.alive) {
                    continue;
                }
                if (tank.playerId == projectile.ownerPlayerId) {
                    continue;
                }
                if (aabbOverlap(projectile, tank)) {
                    world.events.emit(new ProjectileHitTank(
                            projectile.entityId,
                            tank.entityId,
                            projectile.ownerPlayerId
                    ));
                    projectile.despawn();
                    break;
                }
            }
        }
    }

    private static boolean aabbOverlap(Projectile projectile, Tank tank) {
        return Math.abs(projectile.x - tank.x) < (projectile.halfW + tank.halfW)
                && Math.abs(projectile.y - tank.y) < (projectile.halfH + tank.halfH);
    }
}
