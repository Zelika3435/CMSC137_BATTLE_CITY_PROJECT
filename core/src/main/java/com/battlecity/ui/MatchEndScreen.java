package com.battlecity.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.battlecity.core.AppPhase;
import com.battlecity.core.PhaseContext;
import com.battlecity.core.PhaseHandler;
import com.battlecity.game.snapshot.GameSnapshot;

/**
 * Phase driver for {@link AppPhase#MATCH_END}.
 *
 * <p>Variable-dt. Displays the match outcome from the final {@link GameSnapshot}. Press
 * ENTER, SPACE, or ESC to return to {@link AppPhase#MAIN_MENU}.
 */
public final class MatchEndScreen implements PhaseHandler {

    private final PhaseContext ctx;
    private final GameSnapshot finalSnapshot;

    private boolean prevConfirm;

    public MatchEndScreen(PhaseContext ctx, GameSnapshot finalSnapshot) {
        this.ctx = ctx;
        this.finalSnapshot = finalSnapshot;
    }

    @Override
    public AppPhase update(float dt) {
        boolean confirmNow = Gdx.input.isKeyPressed(Input.Keys.ENTER)
                || Gdx.input.isKeyPressed(Input.Keys.SPACE)
                || Gdx.input.isKeyPressed(Input.Keys.ESCAPE);
        if (confirmNow && !prevConfirm) {
            prevConfirm = true;
            return AppPhase.MAIN_MENU;
        }
        prevConfirm = confirmNow;
        return AppPhase.MATCH_END;
    }

    @Override
    public void render() {
        float cx = ctx.viewport().getWorldWidth() / 2f;
        float cy = ctx.viewport().getWorldHeight() / 2f;

        boolean baseDestroyed = finalSnapshot != null && finalSnapshot.baseDestroyed();
        String headline = baseDestroyed ? "BASE DESTROYED" : "MATCH OVER";

        ctx.batch().setColor(1f, 0.25f, 0.25f, 1f);
        ctx.font().draw(ctx.batch(), headline, cx - 68f, cy + 48f);

        ctx.batch().setColor(1f, 1f, 1f, 1f);
        if (finalSnapshot != null) {
            long ticks = finalSnapshot.serverTick();
            long seconds = ticks / 60L;
            ctx.font().draw(ctx.batch(),
                    "Duration: " + seconds + "s  (tick " + ticks + ")",
                    cx - 104f, cy + 14f);
            int aliveTanks = (int) finalSnapshot.tanks().stream().filter(t -> t.alive()).count();
            ctx.font().draw(ctx.batch(),
                    "Tanks remaining: " + aliveTanks,
                    cx - 76f, cy - 10f);
        }

        ctx.batch().setColor(0.5f, 0.5f, 0.5f, 1f);
        ctx.font().draw(ctx.batch(),
                "Press ENTER / SPACE / ESC to return to menu",
                cx - 152f, cy - 44f);
    }

    @Override
    public void onExit() {}

    @Override
    public void dispose() {}
}
