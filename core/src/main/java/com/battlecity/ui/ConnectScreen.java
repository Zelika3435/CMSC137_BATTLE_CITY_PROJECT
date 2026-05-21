package com.battlecity.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.battlecity.core.AppPhase;
import com.battlecity.core.PhaseContext;
import com.battlecity.core.PhaseHandler;
import com.battlecity.net.client.GameClient;

/**
 * Phase driver for {@link AppPhase#MP_CONNECT}.
 *
 * <p>Variable-dt. The {@link GameClient} has already been constructed and {@code connect()} called
 * by {@code CoreGame} before this screen is entered. This screen polls the client each frame until
 * {@link GameClient#isConnected()} is {@code true}, then transitions to {@link AppPhase#MP_LOBBY}.
 * ESC cancels and returns to {@link AppPhase#MAIN_MENU} (CoreGame closes the client on that
 * transition).
 */
public final class ConnectScreen implements PhaseHandler {

    private final PhaseContext ctx;
    private final GameClient netClient;
    private final String host;
    private final int port;

    private float elapsed;
    private boolean prevEscape;

    public ConnectScreen(PhaseContext ctx, GameClient netClient, String host, int port) {
        this.ctx = ctx;
        this.netClient = netClient;
        this.host = host;
        this.port = port;
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
        // endTick() retries the JOIN packet if not yet connected (see GameClient.endTick).
        netClient.endTick();

        if (netClient.isConnected()) {
            return AppPhase.MP_LOBBY;
        }
        return AppPhase.MP_CONNECT;
    }

    @Override
    public void render() {
        int dots = (int) (elapsed * 2f) % 4;
        String anim = ".".repeat(dots);

        float cx = ctx.viewport().getWorldWidth() / 2f;
        float cy = ctx.viewport().getWorldHeight() / 2f;

        ctx.batch().setColor(1f, 1f, 1f, 1f);
        ctx.font().draw(ctx.batch(),
                "Connecting to " + host + ":" + port + anim,
                cx - 110f, cy + 20f);

        ctx.batch().setColor(0.5f, 0.5f, 0.5f, 1f);
        ctx.font().draw(ctx.batch(), "Press ESC to cancel", cx - 70f, cy - 20f);
    }

    @Override
    public void onExit() {}

    @Override
    public void dispose() {}
}
