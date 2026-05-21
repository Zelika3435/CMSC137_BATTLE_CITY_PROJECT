package com.battlecity.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.battlecity.game.snapshot.GameSnapshot;
import com.battlecity.game.snapshot.TankSnapshot;
import com.battlecity.input.KeyboardInputMapper;
import com.battlecity.net.client.GameClient;
import com.battlecity.net.client.SnapshotInterpolationBuffer;
import com.battlecity.net.protocol.LobbyPhase;
import com.battlecity.ui.MatchScreenOverlay;
import com.battlecity.ui.PhaseInputGate;

/**
 * Phase driver for {@link AppPhase#MP_MATCH}.
 *
 * <p>Local death shows a respawn countdown. Elimination mid-round shows a spectate overlay with
 * leave-only. When the server ends the round, all players choose <b>Rejoin lobby</b> (ENTER) or
 * <b>Leave party</b> (ESC) — joiners leave alone; the host disbands the party for everyone.
 */
public final class MpMatchPhaseDriver implements PhaseHandler {

    private static final float FIXED_DT       = GameClient.SimulationConstants.FIXED_DT;
    private static final float MAX_FRAME_TIME  = 0.25f;
    private static final float GO_DURATION     = 1.5f;

    private final PhaseContext ctx;
    private final GameClient netClient;
    private final boolean hostSession;

    private final PhaseInputGate inputGate = new PhaseInputGate();

    private float accumulator;
    private boolean prevEscape;

    private float goTimer = GO_DURATION;

    /** Round finished — player picks rejoin lobby or leave party. */
    private boolean matchEndOverlayActive;

    /** Eliminated before the round ended — may later transition to {@link #matchEndOverlayActive}. */
    private boolean eliminatedOverlayActive;

    /** Set when returning to lobby so {@link #onExit()} keeps the connection. */
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

        GameSnapshot snap = netClient.currentSnapshot();

        if (matchEndOverlayActive) {
            return updateMatchEndOverlay(snap);
        }

        boolean escNow = Gdx.input.isKeyPressed(Input.Keys.ESCAPE);
        if (escNow && !prevEscape) {
            prevEscape = true;
            return leaveParty();
        }
        prevEscape = escNow;

        if (eliminatedOverlayActive) {
            if (isRoundFinished(snap)) {
                eliminatedOverlayActive = false;
                matchEndOverlayActive = true;
                return updateMatchEndOverlay(snap);
            }
            netClient.endTick();
            return AppPhase.MP_MATCH;
        }

        accumulator += Math.min(dt, MAX_FRAME_TIME);
        while (accumulator >= FIXED_DT) {
            runTick();
            accumulator -= FIXED_DT;
            snap = netClient.currentSnapshot();
            if (snap != null) {
                int playerId = netClient.playerId();
                if (isRoundFinished(snap)) {
                    matchEndOverlayActive = true;
                    eliminatedOverlayActive = false;
                    accumulator = 0f;
                    return updateMatchEndOverlay(snap);
                }
                if (MatchScreenOverlay.localPlayerLost(snap, playerId)) {
                    eliminatedOverlayActive = true;
                    accumulator = 0f;
                    return AppPhase.MP_MATCH;
                }
            }
        }
        return AppPhase.MP_MATCH;
    }

    private AppPhase updateMatchEndOverlay(GameSnapshot snap) {
        if (rejoinJustPressed()) {
            leavingForLobby = true;
            return AppPhase.MP_LOBBY;
        }
        boolean escNow = Gdx.input.isKeyPressed(Input.Keys.ESCAPE);
        if (escNow && !prevEscape) {
            prevEscape = true;
            return leaveParty();
        }
        prevEscape = escNow;
        netClient.endTick();
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
        boolean overlay = matchEndOverlayActive || eliminatedOverlayActive;
        float alpha = overlay ? 0f : (interp != null ? interp.alpha() : 0f);
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
        if (matchEndOverlayActive) {
            MatchScreenOverlay.renderMatchEndMenu(ctx, snap, playerId, hostSession);
        } else if (eliminatedOverlayActive) {
            MatchScreenOverlay.renderEliminatedSpectating(ctx, snap, playerId);
        } else {
            MatchScreenOverlay.LocalStatus status = MatchScreenOverlay.localStatus(snap, playerId);
            if (status == MatchScreenOverlay.LocalStatus.WAITING_RESPAWN) {
                TankSnapshot local = tankForPlayer(snap, playerId);
                int ticks = local != null ? local.respawnCooldownTicks() : 0;
                MatchScreenOverlay.renderDeath(ctx, ticks);
            }
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

    /** Joiner: disconnect and main menu. Host: disconnect and {@code CoreGame} tears down the server. */
    private AppPhase leaveParty() {
        netClient.sendDisconnect("leave party");
        return AppPhase.MAIN_MENU;
    }

    private boolean rejoinJustPressed() {
        if (inputGate.isBlocking()) {
            return false;
        }
        return Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_ENTER);
    }

    private boolean isRoundFinished(GameSnapshot snap) {
        if (snap != null && snap.matchOver()) {
            return true;
        }
        return netClient.clientPhase() == LobbyPhase.END;
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
