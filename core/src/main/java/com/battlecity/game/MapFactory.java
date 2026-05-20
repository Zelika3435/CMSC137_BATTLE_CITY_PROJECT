package com.battlecity.game;

public final class MapFactory {
    public static final int DEFAULT_WIDTH = 26;
    public static final int DEFAULT_HEIGHT = 26;
    public static final float DEFAULT_TILE_SIZE = 16f;

    private MapFactory() {}

    public static TileMap createDefaultMap() {
        TileMap map = new TileMap(DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_TILE_SIZE);

        for (int x = 0; x < map.widthTiles(); x++) {
            map.setTile(x, 0, Tile.STEEL);
            map.setTile(x, map.heightTiles() - 1, Tile.STEEL);
        }
        for (int y = 0; y < map.heightTiles(); y++) {
            map.setTile(0, y, Tile.STEEL);
            map.setTile(map.widthTiles() - 1, y, Tile.STEEL);
        }

        for (int y = 6; y <= 8; y++) {
            for (int x = 6; x <= 9; x++) {
                map.setTile(x, y, Tile.BRICK);
            }
        }
        for (int y = 14; y <= 16; y++) {
            for (int x = 15; x <= 18; x++) {
                map.setTile(x, y, Tile.BRICK);
            }
        }

        map.setTile(12, 12, Tile.STEEL);
        map.setTile(13, 12, Tile.STEEL);
        map.setTile(12, 13, Tile.STEEL);
        map.setTile(13, 13, Tile.STEEL);

        int baseX = map.widthTiles() / 2 - 1;
        int baseY = 2;
        map.setTile(baseX, baseY, Tile.BASE);
        map.setTile(baseX + 1, baseY, Tile.BASE);

        return map;
    }

    public static void spawnDefaultTanks(World world) {
        TileMap map = world.map;
        float mapW = map.worldW();
        float bottomY = 48f;
        float topY = map.worldH() - 48f;

        world.tanks[0] = new Tank(1, 0, mapW * 0.25f, bottomY);
        world.tanks[1] = new Tank(2, 1, mapW * 0.75f, bottomY);
        world.tanks[2] = new Tank(3, 2, mapW * 0.25f, topY);
        world.tanks[3] = new Tank(4, 3, mapW * 0.75f, topY);

        for (Tank tank : world.tanks) {
            if (tank == null) {
                continue;
            }
            tank.dir = tank.playerId < 2 ? Direction.UP : Direction.DOWN;
        }
    }
}
