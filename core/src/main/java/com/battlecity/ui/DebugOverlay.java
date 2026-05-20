package com.battlecity.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.battlecity.game.snapshot.NetStatsSnapshot;

public final class DebugOverlay {
    private boolean visible;
    private NetStatsSnapshot stats = new NetStatsSnapshot(0L, 1f / 60f, 0, 0f, 0, 0, 0);

    public void toggle() {
        visible = !visible;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void update(NetStatsSnapshot stats) {
        this.stats = stats;
    }

    public void render(SpriteBatch batch, BitmapFont font) {
        if (!visible) {
            return;
        }
        batch.setColor(Color.WHITE);
        font.draw(
                batch,
                "tick=" + stats.serverTick()
                        + "  dt=" + String.format("%.4f", stats.fixedDtSeconds())
                        + "  ping=" + stats.pingMs() + "ms"
                        + "  loss=" + String.format("%.1f", stats.packetLossPercent()) + "%"
                        + "  tanks=" + stats.tankCount()
                        + "  projectiles=" + stats.projectileCount()
                        + "  entities=" + stats.totalEntities(),
                8f,
                Gdx.graphics.getHeight() - 8f
        );
    }
}
