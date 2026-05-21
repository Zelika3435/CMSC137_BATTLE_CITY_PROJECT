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
    public final boolean hasBases;

    public long tickCount = 0L;
    public boolean baseDestroyed = false;
    public boolean matchOver = false;

    public World(TileMap map) {
        this.map = map;
        for (int i = 0; i < projectiles.length; i++) {
            projectiles[i] = new Projectile(PROJECTILE_ID_BASE + i);
        }
        boolean foundBase = false;
        for (int y = 0; y < map.heightTiles(); y++) {
            for (int x = 0; x < map.widthTiles(); x++) {
                if (map.tileAt(x, y) == Tile.BASE) {
                    foundBase = true;
                    break;
                }
            }
        }
        this.hasBases = foundBase;
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

    public int getPlayerIdForTile(int tx, int ty) {
        if (map.widthTiles() == MapFactory.TUTORIAL_WIDTH && map.heightTiles() == MapFactory.TUTORIAL_HEIGHT) {
            return 0; // In tutorial map, Player 0 owns the tutorial base
        }
        if (tx < 13) {
            return ty < 13 ? 0 : 2;
        } else {
            return ty < 13 ? 1 : 3;
        }
    }

    public boolean hasBaseLeft(int playerId) {
        if (!hasBases) {
            return true;
        }
        if (map.widthTiles() == MapFactory.TUTORIAL_WIDTH && map.heightTiles() == MapFactory.TUTORIAL_HEIGHT) {
            if (playerId != 0) {
                return false;
            }
            for (int y = 0; y < map.heightTiles(); y++) {
                for (int x = 0; x < map.widthTiles(); x++) {
                    if (map.tileAt(x, y) == Tile.BASE) {
                        return true;
                    }
                }
            }
            return false;
        }

        int startX = (playerId == 0 || playerId == 2) ? 0 : 13;
        int endX = (playerId == 0 || playerId == 2) ? 13 : 26;
        int startY = (playerId == 0 || playerId == 1) ? 0 : 13;
        int endY = (playerId == 0 || playerId == 1) ? 13 : 26;

        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                if (map.tileAt(x, y) == Tile.BASE) {
                    return true;
                }
            }
        }
        return false;
    }
}
