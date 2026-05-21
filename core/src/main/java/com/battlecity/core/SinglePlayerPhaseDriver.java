package com.battlecity.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.battlecity.game.LocalMatchController;
import com.battlecity.game.TutorialScript;
import com.battlecity.game.TutorialStepComplete;
import com.battlecity.game.snapshot.GameSnapshot;
import com.battlecity.game.snapshot.NetStatsSnapshot;
import com.battlecity.game.snapshot.TankSnapshot;
import com.battlecity.input.KeyboardInputMapper;
import com.battlecity.ui.MatchScreenOverlay;
import com.battlecity.ui.PhaseInputGate;
import com.battlecity.ui.TutorialOverlay;
import java.util.List;

/**
 * Phase driver for {@link AppPhase#SINGLE_PLAYER} and {@link AppPhase#TUTORIAL}.
 *
 * <p>Owns only the fixed-timestep accumulator and the {@link PhaseContext}. All simulation
 * logic — command application, bot ticks, state advancement — is delegated to the injected
 * {@link LocalMatchController}. ESC returns to {@link AppPhase#MAIN_MENU} and discards the
 * session. On defeat, a grey overlay blocks play until the player quits to the main menu.
 */
public final class SinglePlayerPhaseDriver implements PhaseHandler {

    private static final float FIXED_DT = LocalMatchController.FIXED_DT_SECONDS;
    private static final float MAX_FRAME_TIME = 0.25f;
    private static final int LOCAL_PLAYER_ID = 0;

    private final PhaseContext ctx;
    private final AppPhase ownPhase;
    private final LocalMatchController match;

    /**
     * Non-null only in {@link AppPhase#TUTORIAL} mode. Drives step advancement and
     * exposes UI events; has no effect on simulation state.
     */
    private final TutorialScript tutorialScript;

    /**
     * Non-null iff {@link #tutorialScript} is non-null. Renders the HUD panel above the
     * tutorial map; reads from the script every frame without mutating it.
     */
    private final TutorialOverlay tutorialOverlay;

    private final PhaseInputGate inputGate = new PhaseInputGate();

    private float accumulator;
    private boolean prevEscape;
    /** When true, match is over and the player must confirm quit (no auto menu hop). */
    private boolean defeatOverlayActive;

    /**
     * Single-player or tutorial driver.
     *
     * @param ctx            shared render resources
     * @param ownPhase       {@link AppPhase#SINGLE_PLAYER} or {@link AppPhase#TUTORIAL}
     * @param match          pre-constructed controller; this driver does not own World or Simulation
     * @param tutorialScript optional step machine; {@code null} for non-tutorial modes
     */
    public SinglePlayerPhaseDriver(PhaseContext ctx, AppPhase ownPhase,
            LocalMatchController match, TutorialScript tutorialScript) {
        this.ctx             = ctx;
        this.ownPhase        = ownPhase;
        this.match           = match;
        this.tutorialScript  = tutorialScript;
        this.tutorialOverlay = (tutorialScript != null) ? new TutorialOverlay(ctx) : null;
        if (tutorialScript != null) {
            tutorialScript.init(match.snapshot());
        }
        // Drain edge-detection state so a SPACE/ENTER held in the menu doesn't fire on tick 1.
        ctx.inputMapper().reset();
    }

    /**
     * Convenience overload for non-tutorial single-player mode (no step script).
     *
     * @param ctx      shared render resources
     * @param ownPhase {@link AppPhase#SINGLE_PLAYER}
     * @param match    pre-constructed controller
     */
    public SinglePlayerPhaseDriver(PhaseContext ctx, AppPhase ownPhase, LocalMatchController match) {
        this(ctx, ownPhase, match, null);
    }

    // ---- PhaseHandler -----------------------------------------------------------------------

    @Override
    public AppPhase update(float dt) {
        inputGate.tick(dt);

        boolean escNow = Gdx.input.isKeyPressed(Input.Keys.ESCAPE);
        if (escNow && !prevEscape) {
            prevEscape = true;
            return AppPhase.MAIN_MENU;
        }
        prevEscape = escNow;

        if (defeatOverlayActive) {
            if (inputGate.confirmJustPressed()) {
                return AppPhase.MAIN_MENU;
            }
            return ownPhase;
        }

        accumulator += Math.min(dt, MAX_FRAME_TIME);
        while (accumulator >= FIXED_DT) {
            runTick();
            accumulator -= FIXED_DT;
            if (match.isMatchOver() && tutorialScript == null) {
                defeatOverlayActive = true;
                accumulator = 0f;
                return ownPhase;
            }
        }
        return ownPhase;
    }

    @Override
    public void render() {
        float alpha = accumulator / FIXED_DT;
        GameSnapshot snap = match.snapshot();
        ctx.snapshotRenderer().render(match.previousSnapshot(), snap, alpha, LOCAL_PLAYER_ID);
        if (tutorialOverlay != null) {
            tutorialOverlay.render(tutorialScript);
        }

        MatchScreenOverlay.LocalStatus status =
                MatchScreenOverlay.localStatus(snap, LOCAL_PLAYER_ID);
        if (status == MatchScreenOverlay.LocalStatus.WAITING_RESPAWN) {
            TankSnapshot local = localTank(snap);
            int ticks = local != null ? local.respawnCooldownTicks() : 0;
            MatchScreenOverlay.renderDeath(ctx, ticks);
        } else if (defeatOverlayActive) {
            MatchScreenOverlay.renderSinglePlayerEnd(ctx, snap, LOCAL_PLAYER_ID);
        }
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

        GameSnapshot snap = match.snapshot();
        if (MatchScreenOverlay.localStatus(snap, LOCAL_PLAYER_ID)
                != MatchScreenOverlay.LocalStatus.PLAYING) {
            match.tick(null, false);
        } else {
            match.tick(input.moveDir(), input.firePressed());
        }

        if (tutorialScript != null) {
            tutorialScript.evaluate(match.snapshot(), match.lastEvents());
            List<TutorialStepComplete> advanced = tutorialScript.drainUiEvents();
            if (!advanced.isEmpty()) {
                for (TutorialStepComplete ev : advanced) {
                    System.out.println("[Tutorial] Step complete: " + ev.completed()
                            + " → " + ev.next());
                }
            }
        }

        snap = match.snapshot();
        ctx.debugOverlay().update(new NetStatsSnapshot(
                snap.serverTick(), FIXED_DT, 0, 0f, 0,
                snap.tanks().size(), snap.projectiles().size(),
                snap.tanks().size() + snap.projectiles().size(),
                0f, false, 0, 0));
    }

    private KeyboardInputMapper.LocalInput pollInput() {
        boolean left  = Gdx.input.isKeyPressed(Input.Keys.A)     || Gdx.input.isKeyPressed(Input.Keys.LEFT);
        boolean right = Gdx.input.isKeyPressed(Input.Keys.D)     || Gdx.input.isKeyPressed(Input.Keys.RIGHT);
        boolean up    = Gdx.input.isKeyPressed(Input.Keys.W)     || Gdx.input.isKeyPressed(Input.Keys.UP);
        boolean down  = Gdx.input.isKeyPressed(Input.Keys.S)     || Gdx.input.isKeyPressed(Input.Keys.DOWN);
        boolean fire  = Gdx.input.isKeyPressed(Input.Keys.SPACE);
        boolean f3    = Gdx.input.isKeyPressed(Input.Keys.F3);

        if (defeatOverlayActive
                || MatchScreenOverlay.localStatus(match.snapshot(), LOCAL_PLAYER_ID)
                        == MatchScreenOverlay.LocalStatus.WAITING_RESPAWN) {
            left = right = up = down = fire = false;
        }

        // In tutorial mode, mask inputs to only what the current step teaches.
        if (tutorialScript != null) {
            switch (tutorialScript.currentStep()) {
                case MOVE_SOUTH -> {
                    left  = false;
                    right = false;
                    up    = false;
                    fire  = false;
                }
                case FACE_NORTH -> {
                    left  = false;
                    right = false;
                    down  = false;
                    fire  = false;
                }
                case DESTROY_BRICK -> {
                    left  = false;
                    right = false;
                    up    = false;
                    down  = false;
                }
                case DESTROY_BASE -> {
                }
                case DONE -> {
                    left  = false;
                    right = false;
                    up    = false;
                    down  = false;
                    fire  = false;
                }
            }
        }

        return ctx.inputMapper().poll(left, right, up, down, fire, f3);
    }

    private static TankSnapshot localTank(GameSnapshot snap) {
        for (TankSnapshot tank : snap.tanks()) {
            if (tank.playerId() == LOCAL_PLAYER_ID) {
                return tank;
            }
        }
        return null;
    }
}
