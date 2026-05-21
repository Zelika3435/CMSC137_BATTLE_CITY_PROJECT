package com.battlecity.assets;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.TextureLoader;
import com.badlogic.gdx.graphics.Texture;

/**
 * Central asset registry. Owns a single {@link AssetManager} and exposes typed accessors.
 *
 * <p>Usage lifecycle:
 * <pre>
 *   assets.loadAll();          // queue loads during create()
 *   assets.update();           // poll each frame; returns true when done
 *   assets.getTankGreen();     // safe to call once isFinished() == true
 *   assets.dispose();          // in ApplicationAdapter.dispose()
 * </pre>
 */
public final class Assets {

    // ---- Classpath paths (relative to resources root) ----------------------------------------

    // Outline variants used for hulls — clear black border makes the tank shape visible
    public static final String TANK_GREEN        = "assets/tankGreen_outline.png";
    public static final String TANK_BLACK        = "assets/tankBlack_outline.png";
    public static final String TANK_BLUE         = "assets/tankBlue_outline.png";
    public static final String TANK_RED          = "assets/tankRed_outline.png";
    public static final String TANK_BEIGE        = "assets/tankBeige_outline.png";
    // _up variants are larger and show the barrel tube length from above
    public static final String BARREL_GREEN      = "assets/barrelGreen_up.png";
    public static final String BARREL_BLACK      = "assets/barrelBlack.png";
    public static final String BARREL_BLUE       = "assets/barrelBlue.png";
    public static final String BARREL_RED        = "assets/barrelRed_up.png";
    public static final String BARREL_BEIGE      = "assets/barrelBeige.png";
    public static final String TRACKS            = "assets/tracksLarge.png";
    public static final int    SMOKE_FRAME_COUNT = 6;
    private static final String[] SMOKE_FRAMES;
    static {
        SMOKE_FRAMES = new String[SMOKE_FRAME_COUNT];
        for (int i = 0; i < SMOKE_FRAME_COUNT; i++) {
            SMOKE_FRAMES[i] = "assets/smokeOrange" + i + ".png";
        }
    }
    public static final String TILE_GRASS        = "assets/grass.png";
    public static final String TILE_DIRT         = "assets/dirt.png";
    public static final String TILE_SAND         = "assets/sand.png";
    public static final String TILE_SANDBAG      = "assets/sandbagBeige.png";
    public static final String TILE_SANDBAG_DARK = "assets/sandbagBrown.png";

    private final AssetManager manager = new AssetManager();

    /**
     * Nearest-neighbour filtering keeps pixel-art sprites crisp when upscaled.
     * Without this, bilinear interpolation blurs the sprite into an unrecognisable smear.
     */
    private static final TextureLoader.TextureParameter NEAREST;
    static {
        NEAREST = new TextureLoader.TextureParameter();
        NEAREST.minFilter = Texture.TextureFilter.Nearest;
        NEAREST.magFilter = Texture.TextureFilter.Nearest;
    }

    // ---- Loading lifecycle -------------------------------------------------------------------

    /** Queues all game assets for async loading. Call once during {@code create()}. */
    public void loadAll() {
        manager.load(TANK_GREEN,        Texture.class, NEAREST);
        manager.load(TANK_BLACK,        Texture.class, NEAREST);
        manager.load(TANK_BLUE,         Texture.class, NEAREST);
        manager.load(TANK_RED,          Texture.class, NEAREST);
        manager.load(TANK_BEIGE,        Texture.class, NEAREST);
        manager.load(BARREL_GREEN,      Texture.class, NEAREST);
        manager.load(BARREL_BLACK,      Texture.class, NEAREST);
        manager.load(BARREL_BLUE,       Texture.class, NEAREST);
        manager.load(BARREL_RED,        Texture.class, NEAREST);
        manager.load(BARREL_BEIGE,      Texture.class, NEAREST);
        manager.load(TRACKS,            Texture.class, NEAREST);
        for (String path : SMOKE_FRAMES) manager.load(path, Texture.class, NEAREST);
        manager.load(TILE_GRASS,        Texture.class, NEAREST);
        manager.load(TILE_DIRT,         Texture.class, NEAREST);
        manager.load(TILE_SAND,         Texture.class, NEAREST);
        manager.load(TILE_SANDBAG,      Texture.class, NEAREST);
        manager.load(TILE_SANDBAG_DARK, Texture.class, NEAREST);
    }

    /**
     * Advances async loading. Call once per frame before drawing.
     *
     * @return {@code true} when all queued assets are ready
     */
    public boolean update() {
        return manager.update();
    }

    public boolean isFinished() {
        return manager.isFinished();
    }

    public float progress() {
        return manager.getProgress();
    }

    public void dispose() {
        manager.dispose();
    }

    // ---- Typed accessors ---------------------------------------------------------------------

    /**
     * Returns the tank hull sprite for a given player.
     *
     * <ul>
     *   <li>Local player (playerId == localPlayerId) always gets the green tank.</li>
     *   <li>Other players cycle through blue → red → beige → black by playerId.</li>
     * </ul>
     */
    public Texture tankHullFor(int playerId, int localPlayerId) {
        if (playerId == localPlayerId) {
            return manager.get(TANK_GREEN, Texture.class);
        }
        return switch (Math.floorMod(playerId, 4)) {
            case 1  -> manager.get(TANK_BLUE,  Texture.class);
            case 2  -> manager.get(TANK_RED,   Texture.class);
            case 3  -> manager.get(TANK_BEIGE, Texture.class);
            default -> manager.get(TANK_BLACK, Texture.class);
        };
    }

    /**
     * Returns the barrel sprite that matches the hull returned by {@link #tankHullFor}.
     * Draw this centered on top of the hull, rotated to the tank's direction.
     */
    public Texture tankBarrelFor(int playerId, int localPlayerId) {
        if (playerId == localPlayerId) {
            return manager.get(BARREL_GREEN, Texture.class);
        }
        return switch (Math.floorMod(playerId, 4)) {
            case 1  -> manager.get(BARREL_BLUE,  Texture.class);
            case 2  -> manager.get(BARREL_RED,   Texture.class);
            case 3  -> manager.get(BARREL_BEIGE, Texture.class);
            default -> manager.get(BARREL_BLACK, Texture.class);
        };
    }

    /** One frame of the tank-destruction smoke animation (index 0–5). */
    public Texture smokeFrame(int index) {
        return manager.get(SMOKE_FRAMES[Math.floorMod(index, SMOKE_FRAME_COUNT)], Texture.class);
    }

    /** Shared track sprite drawn beneath every tank hull. */
    public Texture tracks() {
        return manager.get(TRACKS, Texture.class);
    }

    /** Floor texture for EMPTY tiles. */
    public Texture tileEmpty() {
        return manager.get(TILE_GRASS, Texture.class);
    }

    /** Texture for destructible BRICK tiles (brown sandbag). */
    public Texture tileBrick() {
        return manager.get(TILE_SANDBAG_DARK, Texture.class);
    }

    /** Texture for indestructible STEEL tiles (white/beige sandbag). */
    public Texture tileSteel() {
        return manager.get(TILE_SANDBAG, Texture.class);
    }
}
