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
    private final PhaseInputGate inputGate = new PhaseInputGate();

    private boolean prevConfirm;

    public MatchEndScreen(PhaseContext ctx, GameSnapshot finalSnapshot) {
        this.ctx = ctx;
        this.finalSnapshot = finalSnapshot;
    }

    @Override
    public AppPhase update(float dt) {
        inputGate.tick(dt);

        boolean confirmNow = Gdx.input.isKeyPressed(Input.Keys.ENTER)
                || Gdx.input.isKeyPressed(Input.Keys.NUMPAD_ENTER)
                || Gdx.input.isKeyPressed(Input.Keys.SPACE)
                || Gdx.input.isKeyPressed(Input.Keys.ESCAPE);
        if (!inputGate.isBlocking() && confirmNow && !prevConfirm) {
            prevConfirm = true;
            return AppPhase.MAIN_MENU;
        }
        prevConfirm = confirmNow;
        return AppPhase.MATCH_END;
    }

    @Override
    public void render() {
        float w = ctx.viewport().getWorldWidth();
        float h = ctx.viewport().getWorldHeight();
        float cx = w / 2f;
        float cy = h / 2f;

        ctx.batch().setColor(0.10f, 0.10f, 0.12f, 0.85f);
        ctx.batch().draw(ctx.whitePixel(), 0f, 0f, w, h);

        if (finalSnapshot != null) {
            MatchScreenOverlay.renderSinglePlayerEnd(ctx, finalSnapshot, 0);
        } else {
            ctx.batch().setColor(0.9f, 0.9f, 0.9f, 1f);
            ctx.font().draw(ctx.batch(), "MATCH OVER", cx - 44f, cy + 48f);
        }

        ctx.batch().setColor(1f, 1f, 1f, 1f);
        if (finalSnapshot != null) {
            long ticks = finalSnapshot.serverTick();
            long seconds = ticks / 60L;
            ctx.font().draw(ctx.batch(),
                    "Duration: " + seconds + "s  (tick " + ticks + ")",
                    cx - 104f, cy - 52f);
            int aliveTanks = (int) finalSnapshot.tanks().stream().filter(t -> t.alive()).count();
            ctx.font().draw(ctx.batch(),
                    "Tanks remaining: " + aliveTanks,
                    cx - 76f, cy - 76f);
        }
    }

    @Override
    public void onExit() {}

    @Override
    public void dispose() {}
}
