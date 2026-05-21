package com.battlecity.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.battlecity.game.snapshot.GameSnapshot;
import com.battlecity.game.snapshot.TankSnapshot;
import com.battlecity.input.KeyboardInputMapper;
import com.battlecity.net.client.GameClient;
import com.battlecity.net.client.SnapshotInterpolationBuffer;

/**
 * Phase driver for {@link AppPhase#MP_MATCH}.
 *
 * <p>Runs a 60 Hz tick loop to send player inputs at a consistent rate. Inbound snapshots are
 * drained every render frame via {@link GameClient#poll()} so display state is not limited to
 * tick boundaries. Rendering is driven by server-authoritative snapshots; no local simulation
 * runs in {@link #render()}.
 *
 * <h3>Match lifecycle</h3>
 * <ol>
 *   <li>On entry, a "GO!" overlay fades out over {@link #GO_DURATION} s.
 *   <li>Normal play: inputs sent; render uses a delayed snapshot buffer (prev→cur + alpha).
 *   <li>When {@code snapshot.matchOver()} is detected, {@link #endOverlayTimer} is armed and
 *       the tick loop stops.  An end panel is shown over the last game frame.
 *   <li>After {@link #END_OVERLAY_DURATION} s or an {@code ENTER} keypress the driver returns
 *       {@link AppPhase#MP_LOBBY}.  {@code CoreGame} will call
 *       {@link GameClient#resetForLobby()} before constructing the new {@link com.battlecity.ui.LobbyScreen}.
 *   <li>ESC always returns {@link AppPhase#MAIN_MENU}.
 * </ol>
 */
public final class MpMatchPhaseDriver implements PhaseHandler {

    private static final float FIXED_DT           = GameClient.SimulationConstants.FIXED_DT;
    private static final float MAX_FRAME_TIME      = 0.25f;
    /** Duration of the "GO!" flash overlay at match start. */
    private static final float GO_DURATION         = 1.5f;
    /** How long the match-end overlay stays before auto-returning to lobby. */
    private static final float END_OVERLAY_DURATION = 5f;

    private final PhaseContext ctx;
    private final GameClient netClient;

    private float accumulator;
    private boolean prevEscape;
    private boolean prevEnter;

    /** Counts down from {@link #GO_DURATION} to 0; negative means the flash is done. */
    private float goTimer = GO_DURATION;

    /**
     * -1 while the match is active.  Set to {@link #END_OVERLAY_DURATION} the moment
     * {@code snapshot.matchOver()} is first detected; counts down to 0 then triggers lobby
     * return.
     */
    private float endOverlayTimer = -1f;

    public MpMatchPhaseDriver(PhaseContext ctx, GameClient netClient) {
        this.ctx = ctx;
        this.netClient = netClient;
        ctx.inputMapper().reset();
    }

    @Override
    public AppPhase update(float dt) {
        if (goTimer > 0f) {
            goTimer -= dt;
        }

        // ESC always quits to main menu, even during the end overlay.
        boolean escNow = Gdx.input.isKeyPressed(Input.Keys.ESCAPE);
        if (escNow && !prevEscape) {
            prevEscape = true;
            return AppPhase.MAIN_MENU;
        }
        prevEscape = escNow;

        // ---- End overlay mode ---------------------------------------------------------------
        if (endOverlayTimer >= 0f) {
            endOverlayTimer -= dt;
            netClient.endTick();

            boolean enterNow = Gdx.input.isKeyPressed(Input.Keys.ENTER);
            boolean skip = (enterNow && !prevEnter) || endOverlayTimer <= 0f;
            prevEnter = enterNow;
            if (skip) {
                return AppPhase.MP_LOBBY;
            }
            return AppPhase.MP_MATCH;
        }

        // ---- Normal match mode --------------------------------------------------------------
        accumulator += Math.min(dt, MAX_FRAME_TIME);
        while (accumulator >= FIXED_DT) {
            runTick();
            accumulator -= FIXED_DT;
            GameSnapshot snap = netClient.currentSnapshot();
            if (snap != null && snap.matchOver()) {
                // Arm the end overlay; stop the tick loop.
                endOverlayTimer = END_OVERLAY_DURATION;
                accumulator = 0f;
                return AppPhase.MP_MATCH;
            }
        }
        return AppPhase.MP_MATCH;
    }

    @Override
    public void render() {
        netClient.poll();
        ctx.debugOverlay().update(netClient.netStats());

        GameSnapshot snap = netClient.currentSnapshot();
        if (snap == null) {
            ctx.batch().setColor(1f, 1f, 1f, 1f);
            ctx.font().draw(ctx.batch(), "Awaiting first snapshot...", 80f, 208f);
            return;
        }

        SnapshotInterpolationBuffer.InterpolationSample interp = netClient.interpolationSample();
        GameSnapshot renderPrev = interp != null ? interp.prev() : snap;
        GameSnapshot renderCur  = interp != null ? interp.cur() : snap;
        float alpha = endOverlayTimer >= 0f ? 0f : (interp != null ? interp.alpha() : 0f);
        ctx.snapshotRenderer().render(
                renderPrev, renderCur, alpha,
                netClient.playerId(), netClient.predictedLocalTank());

        // "GO!" flash — purely visual; fades linearly.
        if (goTimer > 0f) {
            final float cx = ctx.viewport().getWorldWidth()  / 2f;
            final float cy = ctx.viewport().getWorldHeight() / 2f;
            ctx.batch().setColor(1f, 0.9f, 0.1f, Math.max(0f, goTimer / GO_DURATION));
            ctx.font().draw(ctx.batch(), "GO!", cx - 14f, cy + 32f);
            ctx.batch().setColor(1f, 1f, 1f, 1f);
        }

        // Match-end overlay panel.
        if (endOverlayTimer >= 0f) {
            renderEndOverlay(snap);
        }
    }

    @Override
    public void onExit() {}

    @Override
    public void dispose() {}

    /** Exposes the latest received snapshot so {@code CoreGame} can capture it for MATCH_END. */
    public GameSnapshot currentSnapshot() {
        return netClient.currentSnapshot();
    }

    // ---- End overlay ------------------------------------------------------------------------

    private void renderEndOverlay(GameSnapshot snap) {
        final float cx = ctx.viewport().getWorldWidth()  / 2f;
        final float cy = ctx.viewport().getWorldHeight() / 2f;

        // Semi-transparent dark panel background.
        ctx.batch().setColor(0f, 0f, 0f, 0.70f);
        ctx.batch().draw(ctx.whitePixel(), cx - 156f, cy - 72f, 312f, 148f);

        // Result header.
        if (snap.baseDestroyed()) {
            ctx.batch().setColor(1f, 0.3f, 0.3f, 1f);
            ctx.font().draw(ctx.batch(), "BASE DESTROYED — GAME OVER", cx - 116f, cy + 58f);
        } else {
            ctx.batch().setColor(1f, 0.85f, 0.1f, 1f);
            ctx.font().draw(ctx.batch(), "MATCH OVER", cx - 44f, cy + 58f);
        }

        // Survivor count.
        int aliveTanks = countAliveTanks(snap);
        ctx.batch().setColor(1f, 1f, 1f, 1f);
        ctx.font().draw(ctx.batch(),
                "Tanks alive: " + aliveTanks + " / " + snap.tanks().size(),
                cx - 72f, cy + 30f);

        // Countdown / hint row.
        ctx.batch().setColor(0.7f, 0.7f, 0.7f, 1f);
        if (endOverlayTimer > 0f) {
            ctx.font().draw(ctx.batch(),
                    String.format("Returning to lobby in %.0fs", endOverlayTimer),
                    cx - 96f, cy);
            ctx.font().draw(ctx.batch(), "ENTER: skip", cx - 48f, cy - 24f);
        } else {
            ctx.font().draw(ctx.batch(), "Returning to lobby...", cx - 80f, cy);
        }
        ctx.font().draw(ctx.batch(), "ESC: main menu", cx - 56f, cy - 48f);
        ctx.batch().setColor(1f, 1f, 1f, 1f);
    }

    private static int countAliveTanks(GameSnapshot snap) {
        int n = 0;
        for (TankSnapshot t : snap.tanks()) {
            if (t.alive()) n++;
        }
        return n;
    }

    // ---- Tick helpers -----------------------------------------------------------------------

    private void runTick() {
        KeyboardInputMapper.LocalInput input = pollInput();

        if (input.debugToggle()) {
            ctx.debugOverlay().toggle();
        }

        netClient.sendInput(input.moveDir(), input.firePressed());
        // Step the local prediction forward with the same input sent to the server.
        netClient.stepPrediction(input.moveDir());
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
