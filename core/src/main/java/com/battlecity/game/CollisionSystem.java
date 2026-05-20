package com.battlecity.game;

public final class CollisionSystem {
    private static final float EPS = 0.0001f;

    private CollisionSystem() {}

    public static void resolveTankVsMap(Tank tank, TileMap map) {
        float dx = tank.x - tank.prevX;
        float dy = tank.y - tank.prevY;

        if (dx != 0f) {
            resolveHorizontal(tank, map, dx);
        }
        if (dy != 0f) {
            resolveVertical(tank, map, dy);
        }
    }

    /**
     * Tanks block each other (solid AABB). Lower {@code playerId} wins displacement when both moved.
     */
    public static void resolveTankVsTanks(World world) {
        for (int i = 0; i < World.MAX_PLAYERS; i++) {
            Tank a = world.tanks[i];
            if (a == null || !a.alive) {
                continue;
            }
            for (int j = i + 1; j < World.MAX_PLAYERS; j++) {
                Tank b = world.tanks[j];
                if (b == null || !b.alive) {
                    continue;
                }
                if (!aabbOverlap(a, b)) {
                    continue;
                }
                boolean aMoved = moved(a);
                boolean bMoved = moved(b);
                if (aMoved && !bMoved) {
                    revertToPrevious(a);
                } else if (bMoved && !aMoved) {
                    revertToPrevious(b);
                } else {
                    revertToPrevious(b);
                }
            }
        }
    }

    private static boolean moved(Tank tank) {
        return Math.abs(tank.x - tank.prevX) > EPS || Math.abs(tank.y - tank.prevY) > EPS;
    }

    private static void revertToPrevious(Tank tank) {
        tank.x = tank.prevX;
        tank.y = tank.prevY;
    }

    private static boolean aabbOverlap(Tank a, Tank b) {
        return Math.abs(a.x - b.x) < (a.halfW + b.halfW)
                && Math.abs(a.y - b.y) < (a.halfH + b.halfH);
    }

    private static void resolveHorizontal(Tank tank, TileMap map, float dx) {
        float tile = map.tileSize();
        float minY = tank.y - tank.halfH;
        float maxY = tank.y + tank.halfH;
        int ty0 = (int) Math.floor((minY + EPS) / tile);
        int ty1 = (int) Math.floor((maxY - EPS) / tile);

        if (dx > 0f) {
            float rightEdge = tank.x + tank.halfW;
            int tx = (int) Math.floor((rightEdge - EPS) / tile);
            for (int ty = ty0; ty <= ty1; ty++) {
                if (map.isSolid(tx, ty)) {
                    float tileLeft = tx * tile;
                    tank.x = tileLeft - tank.halfW;
                    return;
                }
            }
        } else {
            float leftEdge = tank.x - tank.halfW;
            int tx = (int) Math.floor((leftEdge + EPS) / tile);
            for (int ty = ty0; ty <= ty1; ty++) {
                if (map.isSolid(tx, ty)) {
                    float tileRight = (tx + 1) * tile;
                    tank.x = tileRight + tank.halfW;
                    return;
                }
            }
        }
    }

    private static void resolveVertical(Tank tank, TileMap map, float dy) {
        float tile = map.tileSize();
        float minX = tank.x - tank.halfW;
        float maxX = tank.x + tank.halfW;
        int tx0 = (int) Math.floor((minX + EPS) / tile);
        int tx1 = (int) Math.floor((maxX - EPS) / tile);

        if (dy > 0f) {
            float topEdge = tank.y + tank.halfH;
            int ty = (int) Math.floor((topEdge - EPS) / tile);
            for (int tx = tx0; tx <= tx1; tx++) {
                if (map.isSolid(tx, ty)) {
                    float tileBottom = ty * tile;
                    tank.y = tileBottom - tank.halfH;
                    return;
                }
            }
        } else {
            float bottomEdge = tank.y - tank.halfH;
            int ty = (int) Math.floor((bottomEdge + EPS) / tile);
            for (int tx = tx0; tx <= tx1; tx++) {
                if (map.isSolid(tx, ty)) {
                    float tileTop = (ty + 1) * tile;
                    tank.y = tileTop + tank.halfH;
                    return;
                }
            }
        }
    }
}
