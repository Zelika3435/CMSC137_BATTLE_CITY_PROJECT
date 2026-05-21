package com.battlecity.game;

public final class MapFactory {
    public static final int DEFAULT_WIDTH = 26;
    public static final int DEFAULT_HEIGHT = 26;
    public static final float DEFAULT_TILE_SIZE = 16f;

    // ---- Tutorial map constants (13×13, tile y=0 is the BOTTOM row) -----------------------

    public static final int TUTORIAL_WIDTH  = 13;
    public static final int TUTORIAL_HEIGHT = 13;

    /** Tile column of the STEEL obstacle (demonstrates indestructible steel). */
    public static final int TUTORIAL_STEEL_TX = 4;

    /** Tile row shared by the brick cluster and the steel obstacle. */
    public static final int TUTORIAL_BRICK_TY = 8;

    /** First (westmost) column of the three BRICK target tiles. */
    public static final int TUTORIAL_BRICK_TX_START = 5;

    /** Last (eastmost) column of the three BRICK target tiles. */
    public static final int TUTORIAL_BRICK_TX_END = 7;

    /** Tile column of the western BASE tile. */
    public static final int TUTORIAL_BASE_TX = 6;

    /** Tile row of the BASE tiles (near the north end of the map). */
    public static final int TUTORIAL_BASE_TY = 11;

    /**
     * Row of the STEEL guard wall placed immediately south of the BASE tiles.
     * This wall prevents northward projectiles from accidentally destroying the BASE,
     * keeping the simulation from freezing during the "reach base" tutorial step.
     * The tank can still get within proximity of the BASE by approaching from the sides.
     */
    public static final int TUTORIAL_BASE_GUARD_TY = TUTORIAL_BASE_TY - 1; // = 10

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

    /**
     * 13×13 tutorial map.
     *
     * <p>Layout (tile y=0 is the BOTTOM row, ty increases northward):
     * <pre>
     *   ty=12  S S S S S S S S S S S S S   ← top steel border
     *   ty=11  S . . . . . B B . . . . S   ← BASE at tx=6,7
     *   ty=10  S . . . . . G G . . . . S   ← STEEL guard wall (protects base)
     *   ty= 9  S . . . . . . . . . . . S
     *   ty= 8  S . . . O B B B . . . . S   ← O=STEEL obstacle, B=BRICK targets
     *   ty= 7  S . . . . . . . . . . . S
     *   ty= 6  S . . . . . . . . . . . S
     *   ty= 5  S . . . . . . . . . . . S
     *   ty= 4  S . . . . . . . . . . . S
     *   ty= 3  S . . . . . P . . . . . S   ← player 0 spawn at tx=6
     *   ty= 2  S . . . . . . . . . . . S
     *   ty= 1  S . . . . . . . . . . . S
     *   ty= 0  S S S S S S S S S S S S S   ← bottom steel border
     * </pre>
     *
     * <p>The STEEL guard wall at {@link #TUTORIAL_BASE_GUARD_TY} (ty=10) prevents northward
     * projectiles from accidentally destroying the BASE and freezing the simulation, while
     * still allowing the tank to satisfy the "reach base" proximity condition from the sides.
     */
    public static TileMap createTutorialMap() {
        TileMap map = new TileMap(TUTORIAL_WIDTH, TUTORIAL_HEIGHT, DEFAULT_TILE_SIZE);

        // Steel border
        for (int x = 0; x < TUTORIAL_WIDTH; x++) {
            map.setTile(x, 0, Tile.STEEL);
            map.setTile(x, TUTORIAL_HEIGHT - 1, Tile.STEEL);
        }
        for (int y = 0; y < TUTORIAL_HEIGHT; y++) {
            map.setTile(0, y, Tile.STEEL);
            map.setTile(TUTORIAL_WIDTH - 1, y, Tile.STEEL);
        }

        // Brick targets: 3 bricks in a row — fire to destroy them
        for (int tx = TUTORIAL_BRICK_TX_START; tx <= TUTORIAL_BRICK_TX_END; tx++) {
            map.setTile(tx, TUTORIAL_BRICK_TY, Tile.BRICK);
        }

        // Steel obstacle: to the left of the bricks — demonstrates steel stops bullets
        map.setTile(TUTORIAL_STEEL_TX, TUTORIAL_BRICK_TY, Tile.STEEL);

        // Base tiles: two-wide, near the north end
        map.setTile(TUTORIAL_BASE_TX,     TUTORIAL_BASE_TY, Tile.BASE);
        map.setTile(TUTORIAL_BASE_TX + 1, TUTORIAL_BASE_TY, Tile.BASE);

        // Steel guard wall: spans the south face of both BASE tiles to stop projectiles
        map.setTile(TUTORIAL_BASE_TX,     TUTORIAL_BASE_GUARD_TY, Tile.STEEL);
        map.setTile(TUTORIAL_BASE_TX + 1, TUTORIAL_BASE_GUARD_TY, Tile.STEEL);

        return map;
    }

    /**
     * Spawns player 0 at the centre-south of the tutorial map facing UP.
     * Slots 1–3 are left {@code null} (no bots in tutorial mode).
     */
    public static void spawnTutorialTank(World world) {
        TileMap map = world.map;
        float spawnX = map.worldW() / 2f;                           // centre column
        float spawnY = 3 * DEFAULT_TILE_SIZE + DEFAULT_TILE_SIZE / 2f; // tile row 3 centre
        world.tanks[0] = new Tank(1, 0, spawnX, spawnY);
        world.tanks[0].dir = Direction.UP;
        // slots 1–3 remain null
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
