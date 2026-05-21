package com.battlecity.core;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.battlecity.game.LocalMatchController;
import com.battlecity.game.snapshot.GameSnapshot;
import com.battlecity.input.KeyboardInputMapper;
import com.battlecity.net.client.GameClient;
import com.battlecity.net.protocol.ProtocolConstants;
import com.battlecity.render.SnapshotRenderer;
import com.battlecity.ui.ConnectScreen;
import com.battlecity.ui.DebugOverlay;
import com.battlecity.ui.LobbyScreen;
import com.battlecity.ui.MainMenuScreen;
import com.battlecity.ui.MatchEndScreen;
import com.battlecity.ui.SinglePlayerPreStartScreen;
import java.io.IOException;

/**
 * LibGDX application entry-point.
 *
 * <p>Manages the top-level {@link AppPhase} state machine. Shared render resources (batch, font,
 * camera, etc.) are created once in {@link #create()} and disposed in {@link #dispose()}. Phase
 * drivers are created lazily inside {@link #transitionTo(AppPhase)} — simulation and network
 * resources are never started until the player chooses a mode from the main menu.
 *
 * <p>The render loop calls {@link PhaseHandler#update(float)} with the raw (capped) frame delta,
 * then draws the phase result inside a single begin/end pair. A transition requested by
 * {@code update} is applied after rendering, so the current frame always completes cleanly.
 */
public final class CoreGame extends ApplicationAdapter {

    private static final int WORLD_W = 26 * 16;
    private static final int WORLD_H = 26 * 16;

    /**
     * Optional CLI-supplied host/port used as default when entering {@link AppPhase#MP_CONNECT}.
     * If no args were passed, defaults to localhost:DEFAULT_PORT.
     */
    private final String cliHost;
    private final int cliPort;

    // ---- Shared render resources (lifetime: application) ------------------------------------

    private SpriteBatch batch;
    private BitmapFont font;
    private Texture whitePixel;
    private OrthographicCamera camera;
    private Viewport viewport;
    private SnapshotRenderer snapshotRenderer;
    private DebugOverlay debugOverlay;
    private KeyboardInputMapper inputMapper;

    private PhaseContext ctx;

    // ---- Phase state machine ----------------------------------------------------------------

    private AppPhase currentPhase;
    private PhaseHandler currentHandler;

    /**
     * Active network client, shared across MP_CONNECT → MP_LOBBY → MP_MATCH phases.
     * Closed (and set to null) when returning to MAIN_MENU or entering MATCH_END.
     */
    private GameClient pendingNetClient;

    /**
     * Bot seed chosen on the SP_PRESTART panel. Consumed when entering SINGLE_PLAYER to
     * construct the {@link LocalMatchController}. Defaults to the fixed practice seed.
     */
    private long pendingSeed = SinglePlayerPreStartScreen.FIXED_SEED;

    /**
     * Snapshot captured just before transitioning into MATCH_END so the end screen can display
     * outcome details even after the match driver is torn down.
     */
    private GameSnapshot pendingFinalSnapshot;

    // -----------------------------------------------------------------------------------------

    public CoreGame(String[] args) {
        if (args.length >= 2) {
            this.cliHost = args[0];
            this.cliPort = Integer.parseInt(args[1]);
        } else {
            this.cliHost = "127.0.0.1";
            this.cliPort = ProtocolConstants.DEFAULT_PORT;
        }
    }

    @Override
    public void create() {
        camera = new OrthographicCamera();
        viewport = new FitViewport(WORLD_W, WORLD_H, camera);

        batch = new SpriteBatch();
        font = new BitmapFont();
        font.setColor(Color.WHITE);
        debugOverlay = new DebugOverlay();
        inputMapper = new KeyboardInputMapper();

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        whitePixel = new Texture(pm);
        pm.dispose();

        snapshotRenderer = new SnapshotRenderer(batch, whitePixel, font, debugOverlay);
        ctx = new PhaseContext(batch, font, whitePixel, viewport,
                snapshotRenderer, debugOverlay, inputMapper);

        // Always start at main menu; no simulation or network is started yet.
        transitionTo(AppPhase.MAIN_MENU);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void render() {
        Gdx.gl.glClearColor(0.08f, 0.08f, 0.10f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        viewport.apply();
        batch.setProjectionMatrix(camera.combined);

        float dt = Gdx.graphics.getDeltaTime();
        AppPhase next = currentHandler.update(dt);

        batch.begin();
        currentHandler.render();
        batch.end();

        // Apply the transition after the current frame has been drawn so the handler always
        // completes cleanly before being torn down.
        if (next != currentPhase) {
            if (next == AppPhase.MATCH_END) {
                captureMatchEndSnapshot();
            }
            if (next == AppPhase.SINGLE_PLAYER
                    && currentHandler instanceof SinglePlayerPreStartScreen ps) {
                pendingSeed = ps.selectedSeed();
            }
            transitionTo(next);
        }
    }

    @Override
    public void dispose() {
        if (currentHandler != null) {
            currentHandler.onExit();
            currentHandler.dispose();
        }
        closeNetClient();
        if (whitePixel != null) whitePixel.dispose();
        if (font != null) font.dispose();
        if (batch != null) batch.dispose();
    }

    // ---- Phase transition -------------------------------------------------------------------

    /**
     * Tears down the current phase driver and constructs the driver for {@code next}.
     * Simulation and GameClient instances are created lazily here; {@link #create()} stays clean.
     */
    private void transitionTo(AppPhase next) {
        if (currentHandler != null) {
            currentHandler.onExit();
            currentHandler.dispose();
            currentHandler = null;
        }

        currentPhase = next;

        switch (next) {
            case MAIN_MENU -> {
                closeNetClient();
                currentHandler = new MainMenuScreen(ctx);
            }
            case SP_PRESTART -> currentHandler = new SinglePlayerPreStartScreen(ctx);
            case SINGLE_PLAYER -> currentHandler = new SinglePlayerPhaseDriver(
                    ctx, AppPhase.SINGLE_PLAYER, LocalMatchController.withBots(pendingSeed));
            case TUTORIAL -> currentHandler = new SinglePlayerPhaseDriver(
                    ctx, AppPhase.TUTORIAL, LocalMatchController.withoutBots());
            case MP_CONNECT -> {
                closeNetClient();
                try {
                    pendingNetClient = new GameClient(cliHost, cliPort, "Player");
                    pendingNetClient.connect();
                } catch (IOException ex) {
                    Gdx.app.error("CoreGame", "Failed to start network client: " + ex.getMessage());
                    currentPhase = AppPhase.MAIN_MENU;
                    currentHandler = new MainMenuScreen(ctx);
                    return;
                }
                currentHandler = new ConnectScreen(ctx, pendingNetClient, cliHost, cliPort);
            }
            case MP_LOBBY -> currentHandler = new LobbyScreen(ctx, pendingNetClient);
            case MP_MATCH -> currentHandler = new MpMatchPhaseDriver(ctx, pendingNetClient);
            case MATCH_END -> {
                // Net client is no longer needed after the match ends.
                closeNetClient();
                currentHandler = new MatchEndScreen(ctx, pendingFinalSnapshot);
                pendingFinalSnapshot = null;
            }
            default -> {
                // Defensive fallback.
                currentPhase = AppPhase.MAIN_MENU;
                currentHandler = new MainMenuScreen(ctx);
            }
        }
    }

    /**
     * Saves the last snapshot from the active match driver before the driver is disposed.
     * Called only when transitioning to {@link AppPhase#MATCH_END}.
     */
    private void captureMatchEndSnapshot() {
        if (currentHandler instanceof SinglePlayerPhaseDriver sp) {
            pendingFinalSnapshot = sp.currentSnapshot();
        } else if (currentHandler instanceof MpMatchPhaseDriver mp) {
            pendingFinalSnapshot = mp.currentSnapshot();
        }
    }

    private void closeNetClient() {
        if (pendingNetClient != null) {
            pendingNetClient.close();
            pendingNetClient = null;
        }
    }
}
