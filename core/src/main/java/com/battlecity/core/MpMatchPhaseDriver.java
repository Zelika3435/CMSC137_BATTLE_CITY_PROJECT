package com.battlecity.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.battlecity.game.snapshot.GameSnapshot;
import com.battlecity.game.snapshot.TankSnapshot;
import com.battlecity.input.KeyboardInputMapper;
import com.battlecity.net.client.GameClient;
import com.battlecity.net.client.SnapshotInterpolationBuffer;
import com.battlecity.ui.MatchScreenOverlay;
import com.battlecity.ui.PhaseInputGate;

/**
 * Phase driver for {@link AppPhase#MP_MATCH}.
 *
 * <p>Runs a 60 Hz tick loop to send player inputs at a consistent rate. Inbound snapshots are
 * drained every render frame via {@link GameClient#poll()} so display state is not limited to
 * tick boundaries. Rendering is driven by server-authoritative snapshots; no local simulation
 * runs in {@link #render()}.
 *
 * <p>Local death shows a grey respawn countdown overlay. Defeat (eliminated / base lost) shows a
 * grey loss screen until the player quits — joiners leave the lobby; hosts disband the party.
 */
public final class MpMatchPhaseDriver implements PhaseHandler {

    private static final float FIXED_DT           = GameClient.SimulationConstants.FIXED_DT;
    private static final float MAX_FRAME_TIME      = 0.25f;
    private static final float GO_DURATION         = 1.5f;
    private static final float END_OVERLAY_DURATION = 5f;

    private final PhaseContext ctx;
    private final GameClient netClient;
    private final boolean hostSession;

    private final PhaseInputGate inputGate = new PhaseInputGate();

    private float accumulator;
    private boolean prevEscape;

    private float goTimer = GO_DURATION;

    /**
     * Counts down after a non-defeat match end (e.g. local victory) before returning to lobby.
     */
    private float endOverlayTimer = -1f;

    /** Local player must confirm quit after a loss. */
    private boolean defeatOverlayActive;

    /** Set when returning to lobby after a win so {@link #onExit()} keeps the connection. */
    private boolean leavingForLobby;

    public MpMatchPhaseDriver(PhaseContext ctx, GameClient netClient, boolean hostSession) {
        this.ctx = ctx;
        this.netClient = netClient;
        this.hostSession = hostSession;
        ctx.inputMapper().reset();
    }

    @Override
    public AppPhase update(float dt) {
        inputGate.tick(dt);
        netClient.poll();

        if (goTimer > 0f) {
            goTimer -= dt;
        }

        String err = netClient.lastError();
        if (err != null && !netClient.isConnected()) {
            return AppPhase.MAIN_MENU;
        }

        boolean escNow = Gdx.input.isKeyPressed(Input.Keys.ESCAPE);
        if (escNow && !prevEscape) {
            prevEscape = true;
            return quitPhase();
        }
        prevEscape = escNow;

        if (defeatOverlayActive) {
            if (inputGate.confirmJustPressed()) {
                return quitPhase();
            }
            netClient.endTick();
            return AppPhase.MP_MATCH;
        }

        if (endOverlayTimer >= 0f) {
            endOverlayTimer -= dt;
            netClient.endTick();
            if (inputGate.confirmJustPressed() || endOverlayTimer <= 0f) {
                leavingForLobby = true;
                return AppPhase.MP_LOBBY;
            }
            return AppPhase.MP_MATCH;
        }

        accumulator += Math.min(dt, MAX_FRAME_TIME);
        while (accumulator >= FIXED_DT) {
            runTick();
            accumulator -= FIXED_DT;
            GameSnapshot snap = netClient.currentSnapshot();
            if (snap != null && snap.matchOver()) {
                if (MatchScreenOverlay.localPlayerLost(snap, netClient.playerId())) {
                    defeatOverlayActive = true;
                } else {
                    endOverlayTimer = END_OVERLAY_DURATION;
                }
                accumulator = 0f;
                return AppPhase.MP_MATCH;
            }
        }
        return AppPhase.MP_MATCH;
    }

    @Override
    public void render() {
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
        float alpha = (defeatOverlayActive || endOverlayTimer >= 0f) ? 0f
                : (interp != null ? interp.alpha() : 0f);
        ctx.snapshotRenderer().render(
                renderPrev, renderCur, alpha,
                netClient.playerId(), netClient.predictedLocalTank());

        if (goTimer > 0f) {
            final float cx = ctx.viewport().getWorldWidth()  / 2f;
            final float cy = ctx.viewport().getWorldHeight() / 2f;
            ctx.batch().setColor(1f, 0.9f, 0.1f, Math.max(0f, goTimer / GO_DURATION));
            ctx.font().draw(ctx.batch(), "GO!", cx - 14f, cy + 32f);
            ctx.batch().setColor(1f, 1f, 1f, 1f);
        }

        int playerId = netClient.playerId();
        MatchScreenOverlay.LocalStatus status = MatchScreenOverlay.localStatus(snap, playerId);
        if (status == MatchScreenOverlay.LocalStatus.WAITING_RESPAWN) {
            TankSnapshot local = tankForPlayer(snap, playerId);
            int ticks = local != null ? local.respawnCooldownTicks() : 0;
            MatchScreenOverlay.renderDeath(ctx, ticks);
        } else if (defeatOverlayActive) {
            MatchScreenOverlay.renderDefeat(ctx, snap, playerId, hostSession);
        } else if (endOverlayTimer >= 0f) {
            renderVictoryOverlay(snap);
        }
    }

    @Override
    public void onExit() {
        if (leavingForLobby) {
            return;
        }
        netClient.sendDisconnect("left match");
    }

    @Override
    public void dispose() {}

    public GameSnapshot currentSnapshot() {
        return netClient.currentSnapshot();
    }

    private AppPhase quitPhase() {
        netClient.sendDisconnect("quit match");
        return AppPhase.MAIN_MENU;
    }

    private void renderVictoryOverlay(GameSnapshot snap) {
        final float cx = ctx.viewport().getWorldWidth()  / 2f;
        final float cy = ctx.viewport().getWorldHeight() / 2f;

        ctx.batch().setColor(0f, 0f, 0f, 0.55f);
        ctx.batch().draw(ctx.whitePixel(), cx - 156f, cy - 72f, 312f, 148f);

        ctx.batch().setColor(1f, 0.85f, 0.1f, 1f);
        ctx.font().draw(ctx.batch(), "MATCH OVER", cx - 44f, cy + 58f);

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

    private void runTick() {
        GameSnapshot snap = netClient.currentSnapshot();
        boolean blockInput = snap != null
                && MatchScreenOverlay.localStatus(snap, netClient.playerId())
                        != MatchScreenOverlay.LocalStatus.PLAYING;

        KeyboardInputMapper.LocalInput input = blockInput ? blockedInput() : pollInput();

        if (input.debugToggle()) {
            ctx.debugOverlay().toggle();
        }

        if (!blockInput) {
            netClient.sendInput(input.moveDir(), input.firePressed());
            netClient.stepPrediction(input.moveDir());
        }
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

    private static KeyboardInputMapper.LocalInput blockedInput() {
        return new KeyboardInputMapper.LocalInput(null, false, false);
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
