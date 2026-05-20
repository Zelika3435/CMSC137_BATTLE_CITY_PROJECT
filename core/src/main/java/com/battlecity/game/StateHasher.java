package com.battlecity.game;

import com.battlecity.game.snapshot.GameSnapshot;
import com.battlecity.game.snapshot.ProjectileSnapshot;
import com.battlecity.game.snapshot.TankSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class StateHasher {
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private StateHasher() {}

    public static long hash(World world) {
        long hash = FNV_OFFSET;
        hash = mix(hash, world.tickCount);
        hash = mix(hash, world.baseDestroyed ? 1 : 0);
        hash = mix(hash, world.matchOver ? 1 : 0);

        for (Tile tile : world.map.copyTiles()) {
            hash = mix(hash, tile.ordinal());
        }

        List<Tank> tanks = new ArrayList<>();
        for (Tank tank : world.tanks) {
            if (tank != null) {
                tanks.add(tank);
            }
        }
        tanks.sort(Comparator.comparingInt(t -> t.entityId));
        for (Tank tank : tanks) {
            hash = mix(hash, tank.entityId);
            hash = mix(hash, tank.playerId);
            hash = mix(hash, Float.floatToIntBits(tank.x));
            hash = mix(hash, Float.floatToIntBits(tank.y));
            hash = mix(hash, tank.dir.ordinal());
            hash = mix(hash, tank.alive ? 1 : 0);
        }

        List<Projectile> projectiles = new ArrayList<>();
        for (Projectile projectile : world.projectiles) {
            if (projectile.active) {
                projectiles.add(projectile);
            }
        }
        projectiles.sort(Comparator.comparingInt(p -> p.entityId));
        for (Projectile projectile : projectiles) {
            hash = mix(hash, projectile.entityId);
            hash = mix(hash, Float.floatToIntBits(projectile.x));
            hash = mix(hash, Float.floatToIntBits(projectile.y));
            hash = mix(hash, projectile.dir.ordinal());
            hash = mix(hash, projectile.ownerPlayerId);
        }

        return hash;
    }

    public static long hashSnapshot(GameSnapshot snapshot) {
        long hash = FNV_OFFSET;
        hash = mix(hash, snapshot.serverTick());
        hash = mix(hash, snapshot.baseDestroyed() ? 1 : 0);
        hash = mix(hash, snapshot.matchOver() ? 1 : 0);

        for (Tile tile : snapshot.tiles()) {
            hash = mix(hash, tile.ordinal());
        }

        List<TankSnapshot> tanks = snapshot.tanks().stream()
                .sorted(Comparator.comparingInt(TankSnapshot::entityId))
                .toList();
        for (TankSnapshot tank : tanks) {
            hash = mix(hash, tank.entityId());
            hash = mix(hash, tank.playerId());
            hash = mix(hash, Float.floatToIntBits(tank.x()));
            hash = mix(hash, Float.floatToIntBits(tank.y()));
            hash = mix(hash, tank.dir().ordinal());
            hash = mix(hash, tank.alive() ? 1 : 0);
        }

        List<ProjectileSnapshot> projectiles = snapshot.projectiles().stream()
                .sorted(Comparator.comparingInt(ProjectileSnapshot::entityId))
                .toList();
        for (ProjectileSnapshot projectile : projectiles) {
            hash = mix(hash, projectile.entityId());
            hash = mix(hash, Float.floatToIntBits(projectile.x()));
            hash = mix(hash, Float.floatToIntBits(projectile.y()));
            hash = mix(hash, projectile.dir().ordinal());
            hash = mix(hash, projectile.ownerPlayerId());
        }

        return hash;
    }

    private static long mix(long hash, long value) {
        long mixed = hash ^ value;
        return mixed * FNV_PRIME;
    }
}
