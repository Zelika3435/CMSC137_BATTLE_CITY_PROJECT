package com.battlecity.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.battlecity.game.LocalMatchController;
import com.battlecity.game.snapshot.GameSnapshot;
import com.battlecity.game.snapshot.NetStatsSnapshot;
import com.battlecity.input.KeyboardInputMapper;

/**
 * Phase driver for {@link AppPhase#SINGLE_PLAYER} and {@link AppPhase#TUTORIAL}.
 *
 * <p>Owns only the fixed-timestep accumulator and the {@link PhaseContext}. All simulation
 * logic — command application, bot ticks, state advancement — is delegated to the injected
 * {@link LocalMatchController}. ESC returns to {@link AppPhase#MAIN_MENU} and discards the
 * session. Transitions to {@link AppPhase#MATCH_END} when the controller reports
 * {@code isMatchOver()}.
 */
public final class SinglePlayerPhaseDriver implements PhaseHandler {

    private static final float FIXED_DT = LocalMatchController.FIXED_DT_SECONDS;
    private static final float MAX_FRAME_TIME = 0.25f;

    private final PhaseContext ctx;
    private final AppPhase ownPhase;
    private final LocalMatchController match;

    private float accumulator;
    private boolean prevEscape;

    /**
     * @param ctx      shared render resources
     * @param ownPhase {@link AppPhase#SINGLE_PLAYER} or {@link AppPhase#TUTORIAL}
     * @param match    pre-constructed controller; this driver does not own World or Simulation
     */
    public SinglePlayerPhaseDriver(PhaseContext ctx, AppPhase ownPhase, LocalMatchController match) {
        this.ctx = ctx;
        this.ownPhase = ownPhase;
        this.match = match;
        // Drain edge-detection state so a SPACE/ENTER held in the menu doesn't fire on tick 1.
        ctx.inputMapper().reset();
    }

    // ---- PhaseHandler -----------------------------------------------------------------------

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
            if (match.isMatchOver()) {
                accumulator = 0f;
                return AppPhase.MATCH_END;
            }
        }
        return ownPhase;
    }

    @Override
    public void render() {
        float alpha = accumulator / FIXED_DT;
        ctx.snapshotRenderer().render(match.previousSnapshot(), match.snapshot(), alpha, 0);
    }

    @Override
    public void onExit() {}

    @Override
    public void dispose() {}

    /** Latest snapshot — read by {@code CoreGame} just before transitioning to MATCH_END. */
    public GameSnapshot currentSnapshot() {
        return match.snapshot();
    }

    // ---- Private ----------------------------------------------------------------------------

    private void runTick() {
        KeyboardInputMapper.LocalInput input = pollInput();

        if (input.debugToggle()) {
            ctx.debugOverlay().toggle();
        }

        match.tick(input.moveDir(), input.firePressed());

        GameSnapshot snap = match.snapshot();
        ctx.debugOverlay().update(new NetStatsSnapshot(
                snap.serverTick(), FIXED_DT, 0, 0f,
                snap.tanks().size(), snap.projectiles().size(),
                snap.tanks().size() + snap.projectiles().size()));
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
