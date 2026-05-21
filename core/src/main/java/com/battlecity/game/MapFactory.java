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
     * Row immediately south of the BASE tiles — kept as a named constant for map documentation.
     * The guard wall that previously occupied this row has been removed: step 4 of the tutorial
     * now requires the player to fire at and destroy the BASE, so the path must be clear.
     */
    public static final int TUTORIAL_BASE_GUARD_TY = TUTORIAL_BASE_TY - 1; // = 10

    private MapFactory() {}

    /**
     * Creates the default 26×26 arena with full left-right and top-bottom
     * mirror symmetry so every player spawns in an equally fair position.
     *
     * <p>Layout highlights (tile y=0 is BOTTOM, y=25 is TOP):
     * <ul>
     *   <li>STEEL border on all four edges.</li>
     *   <li>Corner bases — steel L-shaped pockets protect each spawn.</li>
     *   <li>Brick corridor walls create lanes and chokepoints.</li>
     *   <li>Steel pillars provide permanent cover at key positions.</li>
     *   <li>Central BASE (2 tiles) at (12,12)–(13,12), equidistant from all spawns.</li>
     * </ul>
     *
     * <p>Spawns (set by {@link #spawnDefaultTanks}):
     * <pre>
     *   P0  bottom-left   (~tile  6, 3)
     *   P1  bottom-right  (~tile 19, 3)
     *   P2  top-left      (~tile  6,22)
     *   P3  top-right     (~tile 19,22)
     * </pre>
     */
    public static TileMap createDefaultMap() {
        TileMap map = new TileMap(DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_TILE_SIZE);
        final int W = DEFAULT_WIDTH;   // 26
        final int H = DEFAULT_HEIGHT;  // 26

        // ── 1. STEEL border ──────────────────────────────────────────────
        for (int x = 0; x < W; x++) {
            map.setTile(x, 0, Tile.STEEL);
            map.setTile(x, H - 1, Tile.STEEL);
        }
        for (int y = 0; y < H; y++) {
            map.setTile(0, y, Tile.STEEL);
            map.setTile(W - 1, y, Tile.STEEL);
        }

        // ── 2. Corner base structures (6 base blocks per corner) ────────
        // Each corner gets 6 base blocks (3x2 cluster), shielded by a mix of brick and steel walls.
        // Bottom-left (P0): bases at (1,1) to (3,2)
        for (int x = 1; x <= 3; x++) {
            for (int y = 1; y <= 2; y++) {
                placeSymBase(map, x, y);
            }
        }
        
        // Shield around the base: Steel anchors to make it harder to destroy
        placeSymSteel(map, 4, 3);
        placeSymSteel(map, 3, 3);
        placeSymSteel(map, 4, 1);
        
        // Brick walls for the rest of the shield
        placeSymBrick(map, 1, 3);
        placeSymBrick(map, 2, 3);
        placeSymBrick(map, 4, 2);

        // ── 3. Approach corridors — brick walls from corners toward center ─
        // These create diagonal "lanes" players must navigate to leave base.
        // Horizontal runs near each corner (row 5/20)
        for (int x = 5; x <= 8; x++) {
            placeSymBrick(map, x, 5);
        }
        // Vertical runs near each corner (col 5/20)
        for (int y = 5; y <= 8; y++) {
            placeSymBrick(map, 5, y);
        }

        // ── 4. Mid-field bunkers — brick cover with steel anchors ────────
        // 2×3 brick bunkers at the midpoint of each edge, with 1 steel anchor.
        // South/North bunkers (centred on x=12,13 at y=5 and y=20)
        placeSymBrick(map, 11, 5);
        placeSymBrick(map, 12, 5);
        placeSymSteel(map, 12, 4);  // steel anchor behind bunker
        // Wall extending down to block horizontal spawn kills
        placeSymBrick(map, 12, 3);
        placeSymBrick(map, 12, 2);

        // West/East bunkers (centred on y=12,13 at x=5 and x=20)
        placeSymBrick(map, 5, 11);
        placeSymBrick(map, 5, 12);
        placeSymSteel(map, 4, 12);  // steel anchor behind bunker

        // ── 5. Inner ring — brick walls forming lanes around center ──────
        // Horizontal inner walls (row 9/16)
        for (int x = 7; x <= 10; x++) {
            placeSymBrick(map, x, 9);
        }
        // Vertical inner walls (col 9/16)
        for (int y = 7; y <= 10; y++) {
            placeSymBrick(map, 9, y);
        }

        // ── 6. Steel pillars — permanent cover at key intersections ──────
        // Single steel tiles at the 4 "crossroads" points
        placeSymSteel(map, 9, 5);
        placeSymSteel(map, 5, 9);
        // Centre steel pillar (4 tiles forming a 2×2 block)
        map.setTile(12, 12, Tile.STEEL);
        map.setTile(13, 12, Tile.STEEL);
        map.setTile(12, 13, Tile.STEEL);
        map.setTile(13, 13, Tile.STEEL);

        // ── 7. Central approach brick walls — guard the center ───────────
        // Brick walls on all 4 sides of the centre pillar
        placeSymBrick(map, 11, 12);
        placeSymBrick(map, 12, 11);
        placeSymBrick(map, 11, 11);

        // ── 8. Scattered cover — small brick patches in open lanes ───────
        // Break up long sight lines so combat is closer-range
        placeSymBrick(map, 3, 7);
        placeSymBrick(map, 7, 3);
        placeSymBrick(map, 7, 7);   // intersection cover
        placeSymBrick(map, 11, 7);
        placeSymBrick(map, 7, 11);
        placeSymBrick(map, 11, 9);
        placeSymBrick(map, 9, 11);

        return map;
    }

    // ── Symmetry helpers ─────────────────────────────────────────────────
    // Place a tile at (x,y) and its 3 mirror positions (LR + TB symmetry).

    private static void placeSym4(TileMap map, int x, int y, Tile tile) {
        final int mx = DEFAULT_WIDTH - 1 - x;
        final int my = DEFAULT_HEIGHT - 1 - y;
        map.setTile(x,  y,  tile);
        map.setTile(mx, y,  tile);
        map.setTile(x,  my, tile);
        map.setTile(mx, my, tile);
    }

    private static void placeSymBrick(TileMap map, int x, int y) {
        placeSym4(map, x, y, Tile.BRICK);
    }

    private static void placeSymSteel(TileMap map, int x, int y) {
        placeSym4(map, x, y, Tile.STEEL);
    }

    private static void placeSymBase(TileMap map, int x, int y) {
        placeSym4(map, x, y, Tile.BASE);
    }

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

        // Brick targets: 3 bricks in a row — fire to destroy them (step 3)
        for (int tx = TUTORIAL_BRICK_TX_START; tx <= TUTORIAL_BRICK_TX_END; tx++) {
            map.setTile(tx, TUTORIAL_BRICK_TY, Tile.BRICK);
        }

        // Steel obstacle: to the left of the bricks — demonstrates steel stops bullets
        map.setTile(TUTORIAL_STEEL_TX, TUTORIAL_BRICK_TY, Tile.STEEL);

        // Base tiles: two-wide, near the north end — destroy these in step 4
        map.setTile(TUTORIAL_BASE_TX,     TUTORIAL_BASE_TY, Tile.BASE);
        map.setTile(TUTORIAL_BASE_TX + 1, TUTORIAL_BASE_TY, Tile.BASE);

        // No guard wall: the path from the brick row to the BASE (ty=9,10) is intentionally
        // clear so the player can shoot the BASE after completing step 3.

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
