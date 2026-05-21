package com.battlecity.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.battlecity.game.snapshot.GameSnapshot;
import com.battlecity.input.KeyboardInputMapper;
import com.battlecity.net.client.GameClient;

/**
 * Phase driver for {@link AppPhase#MP_MATCH}.
 *
 * <p>Runs a 60 Hz tick loop to send player inputs at a consistent rate. Rendering is driven
 * entirely by server-authoritative snapshots received via {@link GameClient}; no local simulation
 * runs here. Transitions to {@link AppPhase#MATCH_END} when the received snapshot reports
 * {@code matchOver}. ESC transitions to {@link AppPhase#MAIN_MENU}.
 */
public final class MpMatchPhaseDriver implements PhaseHandler {

    private static final float FIXED_DT = GameClient.SimulationConstants.FIXED_DT;
    private static final float MAX_FRAME_TIME = 0.25f;

    private final PhaseContext ctx;
    private final GameClient netClient;

    private float accumulator;
    private boolean prevEscape;

    public MpMatchPhaseDriver(PhaseContext ctx, GameClient netClient) {
        this.ctx = ctx;
        this.netClient = netClient;
        ctx.inputMapper().reset();
    }

    @Override
    public AppPhase update(float dt) {
        boolean escNow = Gdx.input.isKeyPressed(Input.Keys.ESCAPE);
        if (escNow && !prevEscape) {
            prevEscape = true;
            return AppPhase.MAIN_MENU;
        }
        prevEscape = escNow;

        accumulator += Math.min(dt, MAX_FRAME_TIME);
        while (accumulator >= FIXED_DT) {
            runTick();
            accumulator -= FIXED_DT;
            GameSnapshot snap = netClient.currentSnapshot();
            if (snap != null && snap.matchOver()) {
                accumulator = 0f;
                return AppPhase.MATCH_END;
            }
        }
        return AppPhase.MP_MATCH;
    }

    @Override
    public void render() {
        GameSnapshot snap = netClient.currentSnapshot();
        if (snap == null) {
            ctx.batch().setColor(1f, 1f, 1f, 1f);
            ctx.font().draw(ctx.batch(), "In match — awaiting first server snapshot...", 60f, 208f);
            return;
        }
        float alpha = accumulator / FIXED_DT;
        ctx.snapshotRenderer().render(netClient.previousSnapshot(), snap, alpha, netClient.playerId());
    }

    @Override
    public void onExit() {}

    @Override
    public void dispose() {}

    /** Exposes the latest received snapshot so {@code CoreGame} can pass it to MATCH_END. */
    public GameSnapshot currentSnapshot() {
        return netClient.currentSnapshot();
    }

    // -----------------------------------------------------------------------------------------

    private void runTick() {
        KeyboardInputMapper.LocalInput input = pollInput();

        if (input.debugToggle()) {
            ctx.debugOverlay().toggle();
        }

        netClient.poll();
        netClient.sendInput(input.moveDir(), input.firePressed());
        netClient.endTick();
        ctx.debugOverlay().update(netClient.netStats());
    }

    private KeyboardInputMapper.LocalInput pollInput() {
        return ctx.inputMapper().poll(
                Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT),
                Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT),
                Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP),
                Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN),
                Gdx.input.isKeyPressed(Input.Keys.SPACE),
                Gdx.input.isKeyPressed(Input.Keys.F3));
    }
}
