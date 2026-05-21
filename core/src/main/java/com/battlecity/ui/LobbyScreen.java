package com.battlecity.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.battlecity.core.AppPhase;
import com.battlecity.core.PhaseContext;
import com.battlecity.core.PhaseHandler;
import com.battlecity.net.client.GameClient;

/**
 * Phase driver for {@link AppPhase#MP_LOBBY}.
 *
 * <p>Variable-dt. The client is connected (JOIN_ACK received) but no match snapshot has arrived
 * yet. This screen polls the client and waits for the first {@link GameClient#currentSnapshot()}
 * to become non-null, which signals that the server has started the match. ESC returns to
 * {@link AppPhase#MAIN_MENU}.
 */
public final class LobbyScreen implements PhaseHandler {

    private final PhaseContext ctx;
    private final GameClient netClient;

    private float elapsed;
    private boolean prevEscape;

    public LobbyScreen(PhaseContext ctx, GameClient netClient) {
        this.ctx = ctx;
        this.netClient = netClient;
    }

    @Override
    public AppPhase update(float dt) {
        elapsed += dt;

        boolean escNow = Gdx.input.isKeyPressed(Input.Keys.ESCAPE);
        if (escNow && !prevEscape) {
            prevEscape = true;
            return AppPhase.MAIN_MENU;
        }
        prevEscape = escNow;

        netClient.poll();
        netClient.endTick();

        if (netClient.currentSnapshot() != null) {
            return AppPhase.MP_MATCH;
        }
        return AppPhase.MP_LOBBY;
    }

    @Override
    public void render() {
        int dots = (int) (elapsed * 1.5f) % 4;
        String anim = ".".repeat(dots);

        float cx = ctx.viewport().getWorldWidth() / 2f;
        float cy = ctx.viewport().getWorldHeight() / 2f;

        ctx.batch().setColor(1f, 0.85f, 0.1f, 1f);
        ctx.font().draw(ctx.batch(), "BATTLE CITY", cx - 52f, cy + 64f);

        ctx.batch().setColor(1f, 1f, 1f, 1f);
        ctx.font().draw(ctx.batch(),
                "Waiting for match to start" + anim,
                cx - 112f, cy + 12f);

        ctx.batch().setColor(0.55f, 0.55f, 0.55f, 1f);
        ctx.font().draw(ctx.batch(),
                "Player ID: " + netClient.playerId(),
                cx - 52f, cy - 18f);
        ctx.font().draw(ctx.batch(), "Press ESC to cancel", cx - 72f, cy - 48f);
    }

    @Override
    public void onExit() {}

    @Override
    public void dispose() {}
}
