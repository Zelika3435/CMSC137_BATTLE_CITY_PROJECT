package com.battlecity.ui;

import com.battlecity.core.PhaseContext;
import com.battlecity.game.snapshot.GameSnapshot;
import com.battlecity.game.snapshot.TankSnapshot;

/**
 * Full-screen grey overlays for local death (respawn countdown) and match defeat (quit).
 * Render-only; does not affect simulation.
 */
public final class MatchScreenOverlay {

    private static final int LOCAL_PLAYER_ID = 0;

    private MatchScreenOverlay() {}

    public enum LocalStatus {
        PLAYING,
        WAITING_RESPAWN,
        DEFEATED
    }

    public static LocalStatus localStatus(GameSnapshot snap, int localPlayerId) {
        if (snap == null) {
            return LocalStatus.PLAYING;
        }
        if (snap.matchOver() && localPlayerLost(snap, localPlayerId)) {
            return LocalStatus.DEFEATED;
        }
        TankSnapshot local = tankForPlayer(snap, localPlayerId);
        if (local != null
                && !local.alive()
                && !local.eliminated()
                && local.respawnCooldownTicks() > 0) {
            return LocalStatus.WAITING_RESPAWN;
        }
        return LocalStatus.PLAYING;
    }

    public static boolean localPlayerLost(GameSnapshot snap, int localPlayerId) {
        TankSnapshot local = tankForPlayer(snap, localPlayerId);
        if (local == null) {
            return snap.matchOver();
        }
        return local.eliminated();
    }

    public static void renderDeath(PhaseContext ctx, int respawnCooldownTicks) {
        float w = ctx.viewport().getWorldWidth();
        float h = ctx.viewport().getWorldHeight();
        float cx = w / 2f;
        float cy = h / 2f;

        ctx.batch().setColor(0.12f, 0.12f, 0.14f, 0.78f);
        ctx.batch().draw(ctx.whitePixel(), 0f, 0f, w, h);

        float seconds = Math.max(0f, respawnCooldownTicks / 60f);
        ctx.batch().setColor(0.85f, 0.85f, 0.85f, 1f);
        ctx.font().draw(ctx.batch(), "TANK DESTROYED", cx - 72f, cy + 40f);
        ctx.batch().setColor(0.65f, 0.65f, 0.65f, 1f);
        ctx.font().draw(ctx.batch(),
                String.format("Respawn in %.1fs", seconds),
                cx - 64f, cy + 8f);
        ctx.batch().setColor(1f, 1f, 1f, 1f);
    }

    public static void renderDefeat(PhaseContext ctx, GameSnapshot snap, int localPlayerId,
                                    boolean hostSession) {
        float w = ctx.viewport().getWorldWidth();
        float h = ctx.viewport().getWorldHeight();
        float cx = w / 2f;
        float cy = h / 2f;

        ctx.batch().setColor(0.10f, 0.10f, 0.12f, 0.82f);
        ctx.batch().draw(ctx.whitePixel(), 0f, 0f, w, h);

        boolean baseLost = snap != null && snap.baseDestroyed() && localPlayerId == LOCAL_PLAYER_ID;
        ctx.batch().setColor(1f, 0.28f, 0.28f, 1f);
        ctx.font().draw(ctx.batch(),
                baseLost ? "BASE DESTROYED" : "YOU LOSE",
                cx - (baseLost ? 76f : 44f), cy + 48f);

        ctx.batch().setColor(0.9f, 0.9f, 0.9f, 1f);
        ctx.font().draw(ctx.batch(), "Match over", cx - 44f, cy + 18f);

        ctx.batch().setColor(0.55f, 0.55f, 0.55f, 1f);
        if (hostSession) {
            ctx.font().draw(ctx.batch(),
                    "ENTER / ESC: Quit (ends party for all players)",
                    cx - 168f, cy - 18f);
        } else {
            ctx.font().draw(ctx.batch(),
                    "ENTER / ESC: Quit (leave lobby)",
                    cx - 132f, cy - 18f);
        }
        ctx.batch().setColor(1f, 1f, 1f, 1f);
    }

    private static TankSnapshot tankForPlayer(GameSnapshot snap, int playerId) {
        for (TankSnapshot tank : snap.tanks()) {
            if (tank.playerId() == playerId) {
                return tank;
            }
        }
        return null;
    }
}
