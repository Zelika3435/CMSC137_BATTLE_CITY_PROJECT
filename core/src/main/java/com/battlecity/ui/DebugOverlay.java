package com.battlecity.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.battlecity.game.snapshot.NetStatsSnapshot;

public final class DebugOverlay {
    private static final long FORMAT_INTERVAL_MS = 250L;

    private boolean visible;
    private NetStatsSnapshot stats = new NetStatsSnapshot(
            0L, 1f / 60f, 0, 0f, 0, 0, 0, 0, 0f, false, 0, 0);

    private final StringBuilder formatBuffer = new StringBuilder(160);
    private String cachedLine = "";
    private long lastFormatMs;

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
        long now = System.currentTimeMillis();
        if (now - lastFormatMs >= FORMAT_INTERVAL_MS) {
            lastFormatMs = now;
            cachedLine = formatLine(formatBuffer, stats);
        }

        batch.setColor(Color.WHITE);
        font.draw(batch, cachedLine, 8f, Gdx.graphics.getHeight() - 8f);
    }

    private static String formatLine(StringBuilder sb, NetStatsSnapshot stats) {
        sb.setLength(0);
        sb.append("tick=").append(stats.serverTick())
          .append("  dt=").append(String.format("%.4f", stats.fixedDtSeconds()))
          .append("  rtt=").append(stats.rttMs()).append("ms")
          .append("  snap/s=").append(String.format("%.1f", stats.snapshotsPerSecond()))
          .append("  snap_age=").append(stats.snapshotAgeTicks()).append("t")
          .append("  tanks=").append(stats.tankCount())
          .append("  proj=").append(stats.projectileCount())
          .append("  ent=").append(stats.totalEntities());

        if (stats.predErrorPx() > 0f || stats.predReconcileSnap()) {
            sb.append("  pred_err=").append(String.format("%.1f", stats.predErrorPx()));
            if (stats.predReconcileSnap()) {
                sb.append(" [SNAP]");
            }
        }
        if (stats.interpBufferMs() > 0) {
            sb.append("  buf=").append(stats.interpBufferMs()).append("ms");
        }
        if (stats.unackedInputs() > 0) {
            sb.append("  in_unacked=").append(stats.unackedInputs());
        }
        return sb.toString();
    }
}
