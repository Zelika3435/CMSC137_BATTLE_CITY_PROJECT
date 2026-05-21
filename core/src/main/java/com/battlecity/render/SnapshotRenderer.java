package com.battlecity.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.battlecity.assets.Assets;
import com.battlecity.game.Direction;
import com.battlecity.game.Tile;
import com.battlecity.game.snapshot.GameSnapshot;
import com.battlecity.game.snapshot.ProjectileSnapshot;
import com.battlecity.game.snapshot.TankSnapshot;
import com.battlecity.ui.DebugOverlay;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Renders a {@link GameSnapshot} using pre-loaded sprite textures when available,
 * falling back to coloured rectangles until the {@link Assets} finish loading.
 *
 * <p>All drawing is read-only from immutable snapshots; no simulation state is mutated here.
 * Presentational animation state (smoke timers) is kept here and does not feed back into
 * the simulation.
 */
public final class SnapshotRenderer {

    /** Physics half-size of a tank in world units (matches Tank.halfW / halfH). */
    private static final float TANK_HALF = 6f;

    /**
     * Visual half-size used for drawing sprites only.
     * Set to half a tile (8 world units) so the sprite fills one grid cell (16×16 units = 48×48 px
     * at 3× scale) and the Kenney art detail is clearly visible. The physics hitbox stays at 6.
     */
    private static final float DRAW_HALF = 8f;

    /** Size of a projectile rectangle (world units). */
    private static final float PROJ_SIZE = 4f;

    /** Smoke cloud is drawn 2.5× draw half-size so it billows beyond the hull. */
    private static final float SMOKE_HALF = DRAW_HALF * 2.5f;

    /** Seconds each smoke frame is displayed (6 frames × 0.1 s = 0.6 s total). */
    private static final float SMOKE_FRAME_DURATION = 0.1f;

    /** Fallback per-player colours (RGBA components, 3 per player) used before textures load. */
    private static final float[] FALLBACK_COLORS = {
            0.15f, 0.75f, 0.25f,   // player 0 – green
            0.20f, 0.55f, 0.95f,   // player 1 – blue
            0.95f, 0.55f, 0.20f,   // player 2 – orange
            0.75f, 0.25f, 0.85f    // player 3 – purple
    };

    // ---- Shared render resources (owned externally) ----------------------------------------

    private final SpriteBatch batch;
    private final Texture whitePixel;
    private final BitmapFont font;
    private final DebugOverlay debugOverlay;

    /**
     * May be {@code null} until {@link com.battlecity.core.CoreGame} finishes loading.
     * Renderer falls back to coloured rectangles while null or not yet finished.
     */
    private Assets assets;

    // ---- Presentational animation state (render-only, no simulation side-effects) -----------

    /**
     * Player IDs that are currently known to be dead.
     * Used to detect the first frame of death so we start the smoke animation exactly once.
     * Entries are removed if a player returns to alive (new match).
     */
    private final Set<Integer> knownDeadIds = new HashSet<>();

    /**
     * Active smoke animations keyed by player ID.
     * {@link LinkedHashMap} gives deterministic draw order (oldest first).
     * elapsed: seconds since this animation started.
     */
    private final Map<Integer, float[]> smokeAnims = new LinkedHashMap<>();

    // -----------------------------------------------------------------------------------------

    public SnapshotRenderer(SpriteBatch batch, Texture whitePixel, BitmapFont font, DebugOverlay debugOverlay) {
        this.batch = batch;
        this.whitePixel = whitePixel;
        this.font = font;
        this.debugOverlay = debugOverlay;
    }

    /** Called by {@code CoreGame} once the {@link Assets} are fully loaded. */
    public void setAssets(Assets assets) {
        this.assets = assets;
    }

    // ---- Main render entry ------------------------------------------------------------------

    public void render(GameSnapshot previous, GameSnapshot current, float alpha, int localPlayerId) {
        if (current == null) {
            return;
        }
        final GameSnapshot prev = previous == null ? current : previous;
        final boolean useSprites = assets != null && assets.isFinished();
        final float dt = Gdx.graphics.getDeltaTime();

        detectDeaths(current);

        drawTiles(current, useSprites);
        drawTanks(prev, current, alpha, localPlayerId, useSprites);
        drawProjectiles(prev, current, alpha);
        drawSmoke(dt, useSprites);
        debugOverlay.render(batch, font);
    }

    // ---- Death detection --------------------------------------------------------------------

    /**
     * Compares the current snapshot against {@link #knownDeadIds} to find newly dead tanks
     * and start their smoke animations. Also clears stale dead entries when a player revives
     * (new match started).
     */
    private void detectDeaths(GameSnapshot current) {
        for (TankSnapshot tank : current.tanks()) {
            if (tank.alive()) {
                // Tank is alive: if it was previously marked dead (new match), clean up.
                if (knownDeadIds.remove(tank.playerId())) {
                    smokeAnims.remove(tank.playerId());
                }
            } else {
                // Tank is dead: start animation on first detection.
                if (knownDeadIds.add(tank.playerId())) {
                    // float[0] = elapsed seconds, float[1] = x, float[2] = y
                    smokeAnims.put(tank.playerId(), new float[]{0f, tank.x(), tank.y()});
                }
            }
        }
    }

    // ---- Tile drawing -----------------------------------------------------------------------

    private void drawTiles(GameSnapshot current, boolean useSprites) {
        final float tile = current.tileSize();
        final int width = current.mapWidthTiles();
        final int height = current.mapHeightTiles();

        for (int ty = 0; ty < height; ty++) {
            for (int tx = 0; tx < width; tx++) {
                final Tile t = current.tiles()[ty * width + tx];
                final float px = tx * tile;
                final float py = ty * tile;
                final float sz = tile - 1f;

                if (useSprites) {
                    drawTileSprite(t, px, py, sz);
                } else {
                    drawTileFallback(t, px, py, sz);
                }
            }
        }
    }

    private void drawTileSprite(Tile tile, float x, float y, float size) {
        batch.setColor(Color.WHITE);
        switch (tile) {
            case EMPTY -> batch.draw(assets.tileEmpty(), x, y, size, size);
            case BRICK -> batch.draw(assets.tileBrick(), x, y, size, size);
            case STEEL -> batch.draw(assets.tileSteel(), x, y, size, size);
            case BASE  -> {
                // No dedicated base texture; tint the sandbag sprite gold.
                batch.setColor(0.95f, 0.85f, 0.15f, 1f);
                batch.draw(assets.tileBrick(), x, y, size, size);
                batch.setColor(Color.WHITE);
            }
        }
    }

    private void drawTileFallback(Tile tile, float x, float y, float size) {
        switch (tile) {
            case EMPTY -> batch.setColor(0.16f, 0.16f, 0.18f, 1f);
            case BRICK -> batch.setColor(0.55f, 0.25f, 0.18f, 1f);
            case STEEL -> batch.setColor(0.45f, 0.45f, 0.50f, 1f);
            case BASE  -> batch.setColor(0.75f, 0.75f, 0.20f, 1f);
        }
        batch.draw(whitePixel, x, y, size, size);
    }

    // ---- Tank drawing -----------------------------------------------------------------------

    private void drawTanks(GameSnapshot prev, GameSnapshot current,
                           float alpha, int localPlayerId, boolean useSprites) {
        for (TankSnapshot tank : current.tanks()) {
            if (!tank.alive()) {
                continue;
            }
            final TankSnapshot prevTank = findTank(prev, tank.playerId());
            final float x = prevTank == null ? tank.x() : MathUtils.lerp(prevTank.x(), tank.x(), alpha);
            final float y = prevTank == null ? tank.y() : MathUtils.lerp(prevTank.y(), tank.y(), alpha);

            if (useSprites) {
                drawTankSprite(tank, x, y, localPlayerId);
            } else {
                drawTankFallback(tank, x, y, localPlayerId);
            }
        }
    }

    private void drawTankSprite(TankSnapshot tank, float x, float y, int localPlayerId) {
        final float rotation = directionToDegrees(tank.dir());
        final float size = DRAW_HALF * 2;
        batch.setColor(Color.WHITE);

        // Hull – outline variant already has a clear black border showing the tank shape.
        final Texture hull = assets.tankHullFor(tank.playerId(), localPlayerId);
        batch.draw(hull,
                x - DRAW_HALF, y - DRAW_HALF,
                DRAW_HALF, DRAW_HALF,
                size, size,
                1f, 1f,
                rotation,
                0, 0, hull.getWidth(), hull.getHeight(),
                false, false);

        // Nozzle indicator drawn on top of the hull.
        drawNozzle(x, y, tank.dir());
    }

    private void drawTankFallback(TankSnapshot tank, float x, float y, int localPlayerId) {
        final int ci = Math.floorMod(tank.playerId(), 4);
        if (tank.playerId() == localPlayerId) {
            batch.setColor(0.95f, 0.95f, 0.95f, 1f);
        } else {
            batch.setColor(FALLBACK_COLORS[ci * 3], FALLBACK_COLORS[ci * 3 + 1], FALLBACK_COLORS[ci * 3 + 2], 1f);
        }
        batch.draw(whitePixel, x - DRAW_HALF, y - DRAW_HALF, DRAW_HALF * 2, DRAW_HALF * 2);
        drawNozzle(x, y, tank.dir());
    }

    /**
     * Draws a small dark rectangle extending from the front half of the hull in the tank's
     * facing direction. Consistent across all players regardless of which barrel sprites exist.
     *
     * <pre>
     *   nozzle starts: centre + 2 world units  (inside the hull)
     *   nozzle ends:   centre + DRAW_HALF + 2  (2 units past the hull edge)
     *   nozzle width:  3 world units
     * </pre>
     */
    private void drawNozzle(float cx, float cy, Direction dir) {
        final float len  = DRAW_HALF;   // length of nozzle from start to tip
        final float wide = 3f;          // nozzle width in world units
        final float start = 2f;         // offset from center where nozzle begins
        float rx, ry, rw, rh;
        switch (dir) {
            case UP    -> { rx = cx - wide / 2; ry = cy + start;       rw = wide; rh = len; }
            case DOWN  -> { rx = cx - wide / 2; ry = cy - start - len; rw = wide; rh = len; }
            case LEFT  -> { rx = cx - start - len; ry = cy - wide / 2; rw = len;  rh = wide; }
            case RIGHT -> { rx = cx + start;       ry = cy - wide / 2; rw = len;  rh = wide; }
            default    -> { return; }
        }
        batch.setColor(0.15f, 0.15f, 0.15f, 1f);
        batch.draw(whitePixel, rx, ry, rw, rh);
        batch.setColor(Color.WHITE);
    }

    // ---- Projectile drawing ----------------------------------------------------------------

    private void drawProjectiles(GameSnapshot prev, GameSnapshot current, float alpha) {
        batch.setColor(0.95f, 0.90f, 0.20f, 1f);
        for (ProjectileSnapshot projectile : current.projectiles()) {
            final ProjectileSnapshot prevProj = findProjectile(prev, projectile.entityId());
            final float x = prevProj == null
                    ? projectile.x()
                    : MathUtils.lerp(prevProj.x(), projectile.x(), alpha);
            final float y = prevProj == null
                    ? projectile.y()
                    : MathUtils.lerp(prevProj.y(), projectile.y(), alpha);
            batch.draw(whitePixel, x - PROJ_SIZE / 2, y - PROJ_SIZE / 2, PROJ_SIZE, PROJ_SIZE);
        }
        batch.setColor(Color.WHITE);
    }

    // ---- Smoke animation -------------------------------------------------------------------

    /**
     * Advances all active smoke animations by {@code dt} seconds and draws the current frame.
     * Animations that have played all 6 frames are removed.
     *
     * <p>Smoke is drawn even when sprites are not yet loaded (the frames ARE sprites), so this
     * method returns early if assets are not ready.
     */
    private void drawSmoke(float dt, boolean useSprites) {
        if (!useSprites || smokeAnims.isEmpty()) {
            return;
        }

        final float totalDuration = SMOKE_FRAME_DURATION * Assets.SMOKE_FRAME_COUNT;

        final Iterator<Map.Entry<Integer, float[]>> it = smokeAnims.entrySet().iterator();
        while (it.hasNext()) {
            final float[] state = it.next().getValue();
            state[0] += dt;

            if (state[0] >= totalDuration) {
                it.remove();
                continue;
            }

            final int frame = (int) (state[0] / SMOKE_FRAME_DURATION);
            final Texture tex = assets.smokeFrame(frame);
            final float cx = state[1];
            final float cy = state[2];

            batch.setColor(Color.WHITE);
            batch.draw(tex, cx - SMOKE_HALF, cy - SMOKE_HALF, SMOKE_HALF * 2, SMOKE_HALF * 2);
        }
    }

    // ---- Helpers ---------------------------------------------------------------------------

    /**
     * Maps game direction to counter-clockwise degrees for LibGDX's SpriteBatch.
     * Sprite sheet faces UP (north) = 0°.
     */
    private static float directionToDegrees(Direction dir) {
        return switch (dir) {
            case UP    ->   0f;
            case LEFT  ->  90f;
            case DOWN  -> 180f;
            case RIGHT -> 270f;
        };
    }

    private static TankSnapshot findTank(GameSnapshot snapshot, int playerId) {
        for (TankSnapshot tank : snapshot.tanks()) {
            if (tank.playerId() == playerId) {
                return tank;
            }
        }
        return null;
    }

    private static ProjectileSnapshot findProjectile(GameSnapshot snapshot, int entityId) {
        for (ProjectileSnapshot projectile : snapshot.projectiles()) {
            if (projectile.entityId() == entityId) {
                return projectile;
            }
        }
        return null;
    }
}
