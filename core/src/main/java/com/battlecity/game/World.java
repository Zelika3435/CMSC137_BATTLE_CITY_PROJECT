package com.battlecity.game;

import com.battlecity.game.event.EventQueue;

public final class World {
    public static final int MAX_PLAYERS = 4;
    public static final int PROJECTILE_POOL = 64;
    public static final int PROJECTILE_ID_BASE = 100;

    public final TileMap map;
    public final Tank[] tanks = new Tank[MAX_PLAYERS];
    public final Projectile[] projectiles = new Projectile[PROJECTILE_POOL];
    public final EventQueue events = new EventQueue();

    public long tickCount = 0L;
    public boolean baseDestroyed = false;
    public boolean matchOver = false;

    public World(TileMap map) {
        this.map = map;
        for (int i = 0; i < projectiles.length; i++) {
            projectiles[i] = new Projectile(PROJECTILE_ID_BASE + i);
        }
    }

    public static World createDefault() {
        World world = new World(MapFactory.createDefaultMap());
        MapFactory.spawnDefaultTanks(world);
        return world;
    }

    public Tank tankByPlayerId(int playerId) {
        if (playerId < 0 || playerId >= MAX_PLAYERS) {
            return null;
        }
        return tanks[playerId];
    }

    public Tank tankByEntityId(int entityId) {
        for (Tank tank : tanks) {
            if (tank != null && tank.entityId == entityId) {
                return tank;
            }
        }
        return null;
    }
}
