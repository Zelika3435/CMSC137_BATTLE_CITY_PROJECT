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
        TankSnapshot local = tankForPlayer(snap, localPlayerId);
        if (local != null && local.eliminated()) {
            return LocalStatus.DEFEATED;
        }
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

    /**
     * Shown while the local player is eliminated but the round is still in progress.
     */
    public static void renderEliminatedSpectating(PhaseContext ctx, GameSnapshot snap,
            int localPlayerId) {
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

        ctx.batch().setColor(0.65f, 0.65f, 0.65f, 1f);
        ctx.font().draw(ctx.batch(), "Match continues for other players", cx - 120f, cy + 16f);
        ctx.font().draw(ctx.batch(), "Wait for round end to rejoin the lobby", cx - 132f, cy - 8f);

        ctx.batch().setColor(0.55f, 0.55f, 0.55f, 1f);
        ctx.font().draw(ctx.batch(), "ESC: Leave party", cx - 56f, cy - 40f);
        ctx.batch().setColor(1f, 1f, 1f, 1f);
    }

    /**
     * Shown when the server has ended the round ({@code matchOver} or lobby phase END).
     * ENTER returns to the lobby; ESC leaves the party (host disbands for everyone).
     */
    public static void renderMatchEndMenu(PhaseContext ctx, GameSnapshot snap, int localPlayerId,
            boolean hostSession) {
        float w = ctx.viewport().getWorldWidth();
        float h = ctx.viewport().getWorldHeight();
        float cx = w / 2f;
        float cy = h / 2f;

        ctx.batch().setColor(0.10f, 0.10f, 0.12f, 0.85f);
        ctx.batch().draw(ctx.whitePixel(), 0f, 0f, w, h);

        ctx.batch().setColor(1f, 0.85f, 0.2f, 1f);
        ctx.font().draw(ctx.batch(), "MATCH ENDED", cx - 52f, cy + 52f);

        boolean lost = localPlayerLost(snap, localPlayerId);
        ctx.batch().setColor(lost ? 1f : 0.4f, lost ? 0.35f : 1f, lost ? 0.35f : 0.4f, 1f);
        ctx.font().draw(ctx.batch(), lost ? "YOU LOSE" : "YOU WIN", cx - (lost ? 44f : 40f), cy + 22f);

        ctx.batch().setColor(0.9f, 0.9f, 0.9f, 1f);
        ctx.font().draw(ctx.batch(), "ENTER: Rejoin lobby", cx - 72f, cy - 12f);

        ctx.batch().setColor(0.55f, 0.55f, 0.55f, 1f);
        if (hostSession) {
            ctx.font().draw(ctx.batch(), "ESC: Leave party (disbands for all)", cx - 120f, cy - 40f);
        } else {
            ctx.font().draw(ctx.batch(), "ESC: Leave party", cx - 56f, cy - 40f);
        }
        ctx.batch().setColor(1f, 1f, 1f, 1f);
    }

    /** Single-player defeat / match-end screen (no lobby rejoin). */
    public static void renderSinglePlayerEnd(PhaseContext ctx, GameSnapshot snap, int localPlayerId) {
        float w = ctx.viewport().getWorldWidth();
        float h = ctx.viewport().getWorldHeight();
        float cx = w / 2f;
        float cy = h / 2f;

        ctx.batch().setColor(0.10f, 0.10f, 0.12f, 0.85f);
        ctx.batch().draw(ctx.whitePixel(), 0f, 0f, w, h);

        boolean baseLost = snap != null && snap.baseDestroyed() && localPlayerId == LOCAL_PLAYER_ID;
        boolean lost = snap == null || localPlayerLost(snap, localPlayerId);
        ctx.batch().setColor(1f, 0.28f, 0.28f, 1f);
        ctx.font().draw(ctx.batch(),
                baseLost ? "BASE DESTROYED" : (lost ? "YOU LOSE" : "MATCH OVER"),
                cx - (baseLost ? 76f : 52f), cy + 48f);

        ctx.batch().setColor(0.55f, 0.55f, 0.55f, 1f);
        ctx.font().draw(ctx.batch(), "ENTER / ESC: Main menu", cx - 88f, cy - 18f);
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
