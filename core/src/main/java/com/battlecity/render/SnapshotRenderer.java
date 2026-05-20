package com.battlecity.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.MathUtils;
import com.battlecity.game.Tile;
import com.battlecity.game.snapshot.GameSnapshot;
import com.battlecity.game.snapshot.ProjectileSnapshot;
import com.battlecity.game.snapshot.TankSnapshot;
import com.battlecity.ui.DebugOverlay;

public final class SnapshotRenderer {
    private static final float[] PLAYER_COLORS = {
            0.15f, 0.75f, 0.25f,
            0.20f, 0.55f, 0.95f,
            0.95f, 0.55f, 0.20f,
            0.75f, 0.25f, 0.85f
    };

    private final SpriteBatch batch;
    private final Texture whitePixel;
    private final BitmapFont font;
    private final DebugOverlay debugOverlay;

    public SnapshotRenderer(SpriteBatch batch, Texture whitePixel, BitmapFont font, DebugOverlay debugOverlay) {
        this.batch = batch;
        this.whitePixel = whitePixel;
        this.font = font;
        this.debugOverlay = debugOverlay;
    }

    public void render(GameSnapshot previous, GameSnapshot current, float alpha, int localPlayerId) {
        if (current == null) {
            return;
        }
        GameSnapshot prev = previous == null ? current : previous;
        float tile = current.tileSize();
        int width = current.mapWidthTiles();
        int height = current.mapHeightTiles();

        for (int ty = 0; ty < height; ty++) {
            for (int tx = 0; tx < width; tx++) {
                Tile t = current.tiles()[ty * width + tx];
                switch (t) {
                    case EMPTY -> batch.setColor(0.16f, 0.16f, 0.18f, 1f);
                    case BRICK -> batch.setColor(0.55f, 0.25f, 0.18f, 1f);
                    case STEEL -> batch.setColor(0.45f, 0.45f, 0.50f, 1f);
                    case BASE -> batch.setColor(0.75f, 0.75f, 0.20f, 1f);
                }
                batch.draw(whitePixel, tx * tile, ty * tile, tile - 1f, tile - 1f);
            }
        }

        for (TankSnapshot tank : current.tanks()) {
            if (!tank.alive()) {
                continue;
            }
            TankSnapshot prevTank = findTank(prev, tank.playerId());
            float x = prevTank == null ? tank.x() : MathUtils.lerp(prevTank.x(), tank.x(), alpha);
            float y = prevTank == null ? tank.y() : MathUtils.lerp(prevTank.y(), tank.y(), alpha);
            int colorIndex = Math.floorMod(tank.playerId(), 4);
            batch.setColor(
                    PLAYER_COLORS[colorIndex * 3],
                    PLAYER_COLORS[colorIndex * 3 + 1],
                    PLAYER_COLORS[colorIndex * 3 + 2],
                    1f
            );
            if (tank.playerId() == localPlayerId) {
                batch.setColor(0.95f, 0.95f, 0.95f, 1f);
            }
            batch.draw(whitePixel, x - 6f, y - 6f, 12f, 12f);
        }

        batch.setColor(0.95f, 0.90f, 0.20f, 1f);
        for (ProjectileSnapshot projectile : current.projectiles()) {
            ProjectileSnapshot prevProjectile = findProjectile(prev, projectile.entityId());
            float x = prevProjectile == null
                    ? projectile.x()
                    : MathUtils.lerp(prevProjectile.x(), projectile.x(), alpha);
            float y = prevProjectile == null
                    ? projectile.y()
                    : MathUtils.lerp(prevProjectile.y(), projectile.y(), alpha);
            batch.draw(whitePixel, x - 2f, y - 2f, 4f, 4f);
        }

        debugOverlay.render(batch, font);
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
