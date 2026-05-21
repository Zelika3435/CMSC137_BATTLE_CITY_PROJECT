package com.battlecity.game.snapshot;

import com.battlecity.game.Projectile;
import com.battlecity.game.Tank;
import com.battlecity.game.Tile;
import com.battlecity.game.World;
import com.battlecity.game.StateHasher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SnapshotBuilder {
    private SnapshotBuilder() {}

    private static final Tile[] NO_TILES = new Tile[0];

    public static GameSnapshot build(World world) {
        return build(world, true);
    }

    /**
     * @param includeTiles when {@code false}, {@link GameSnapshot#tiles()} is empty (for delta
     *                     wire payloads; entity fields are still populated).
     */
    public static GameSnapshot build(World world, boolean includeTiles) {
        List<TankSnapshot> tankSnapshots = new ArrayList<>();
        for (Tank tank : world.tanks) {
            if (tank == null) {
                continue;
            }
            tankSnapshots.add(new TankSnapshot(
                    tank.entityId,
                    tank.playerId,
                    tank.x,
                    tank.y,
                    tank.prevX,
                    tank.prevY,
                    tank.dir,
                    tank.alive
            ));
        }
        tankSnapshots.sort(Comparator.comparingInt(TankSnapshot::entityId));

        List<ProjectileSnapshot> projectileSnapshots = new ArrayList<>();
        for (Projectile projectile : world.projectiles) {
            if (!projectile.active) {
                continue;
            }
            projectileSnapshots.add(new ProjectileSnapshot(
                    projectile.entityId,
                    projectile.x,
                    projectile.y,
                    projectile.prevX,
                    projectile.prevY,
                    projectile.dir,
                    true,
                    projectile.ownerPlayerId
            ));
        }
        projectileSnapshots.sort(Comparator.comparingInt(ProjectileSnapshot::entityId));

        long hash = StateHasher.hash(world);
        Tile[] tiles = includeTiles ? world.map.copyTiles() : NO_TILES;
        return new GameSnapshot(
                world.tickCount,
                hash,
                world.map.widthTiles(),
                world.map.heightTiles(),
                world.map.tileSize(),
                tiles,
                List.copyOf(tankSnapshots),
                List.copyOf(projectileSnapshots),
                world.baseDestroyed,
                world.matchOver
        );
    }

    public static void applyToWorld(GameSnapshot snapshot, World world) {
        world.tickCount = snapshot.serverTick();
        world.baseDestroyed = snapshot.baseDestroyed();
        world.matchOver = snapshot.matchOver();
        world.map.copyFrom(snapshot.tiles());

        for (TankSnapshot tankSnapshot : snapshot.tanks()) {
            Tank tank = world.tankByPlayerId(tankSnapshot.playerId());
            if (tank == null) {
                continue;
            }
            tank.x = tankSnapshot.x();
            tank.y = tankSnapshot.y();
            tank.prevX = tankSnapshot.prevX();
            tank.prevY = tankSnapshot.prevY();
            tank.dir = tankSnapshot.dir();
            tank.alive = tankSnapshot.alive();
        }

        for (Projectile projectile : world.projectiles) {
            projectile.despawn();
        }
        for (ProjectileSnapshot projectileSnapshot : snapshot.projectiles()) {
            Projectile projectile = findProjectile(world, projectileSnapshot.entityId());
            if (projectile == null) {
                continue;
            }
            projectile.active = true;
            projectile.x = projectileSnapshot.x();
            projectile.y = projectileSnapshot.y();
            projectile.prevX = projectileSnapshot.prevX();
            projectile.prevY = projectileSnapshot.prevY();
            projectile.dir = projectileSnapshot.dir();
            projectile.ownerPlayerId = projectileSnapshot.ownerPlayerId();
        }
    }

    private static Projectile findProjectile(World world, int entityId) {
        for (Projectile projectile : world.projectiles) {
            if (projectile.entityId == entityId) {
                return projectile;
            }
        }
        return null;
    }
}
