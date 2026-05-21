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
import com.battlecity.assets.Assets;
import com.battlecity.game.LocalMatchController;
import com.battlecity.game.TutorialScript;
import com.battlecity.game.snapshot.GameSnapshot;
import com.battlecity.input.KeyboardInputMapper;
import com.battlecity.net.LocalAddress;
import com.battlecity.net.client.GameClient;
import com.battlecity.net.protocol.ProtocolConstants;
import com.battlecity.net.server.GameServer;
import com.battlecity.render.SnapshotRenderer;
import com.battlecity.ui.DebugOverlay;
import com.battlecity.ui.LobbyScreen;
import com.battlecity.ui.MainMenuScreen;
import com.battlecity.ui.MatchEndScreen;
import com.battlecity.ui.MultiplayerConnectScreen;
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
    private Assets assets;
    private boolean assetsReady;

    private PhaseContext ctx;

    // ---- Phase state machine ----------------------------------------------------------------

    private AppPhase currentPhase;
    private PhaseHandler currentHandler;

    /**
     * Active network client, shared across MP_CONNECT → MP_LOBBY → MP_MATCH phases.
     * Closed (and set to null) when returning to MAIN_MENU or entering MATCH_END.
     *
     * <p>In HOST mode this client connects to {@code 127.0.0.1} on the same port as
     * {@link #hostedServer}, so it communicates with the in-process server over loopback
     * rather than a remote machine.
     */
    private GameClient pendingNetClient;

    /**
     * In-process server started when the player chooses HOST mode.
     * Null in JOIN mode or when no multiplayer session is active.
     * Torn down by {@link #closeMultiplayerResources()}.
     */
    private GameServer hostedServer;

    /**
     * Daemon thread that drives {@link #hostedServer} at 60 Hz.
     * Null when no hosted server is running.
     */
    private Thread serverThread;

    /**
     * Values from the last successful connection attempt (host, port, display name).
     * Re-used when returning to {@link AppPhase#MP_CONNECT} after a lobby error so the
     * player does not have to retype the server address.
     */
    private String lastConnectHost;
    private int    lastConnectPort;
    private String lastConnectName;

    /**
     * Error message captured from {@link GameClient#lastError()} just before the network client
     * is closed on a lobby → MP_CONNECT redirect.  Passed as the initial status banner of the
     * next {@link com.battlecity.ui.MultiplayerConnectScreen} instance, then cleared.
     */
    private String pendingConnectError;

    /**
     * LAN IP detected when the player chose HOST mode.  Forwarded to {@link LobbyScreen} so it
     * can show "Share IP: …" without any network calls inside the render path.
     * Cleared by {@link #closeMultiplayerResources()} when the session ends.
     */
    private String pendingHostIp;

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
    private boolean startupLogged;
    private long frameCounter;

    // -----------------------------------------------------------------------------------------

    public CoreGame(String[] args) {
        if (args.length >= 2) {
            this.cliHost = args[0];
            this.cliPort = Integer.parseInt(args[1]);
        } else {
            this.cliHost = LocalAddress.detect();
            this.cliPort = ProtocolConstants.DEFAULT_PORT;
        }
    }

    @Override
    public void create() {
        System.out.println("[Battle City] CoreGame.create() start");
        camera = new OrthographicCamera();
        viewport = new FitViewport(WORLD_W, WORLD_H, camera);
        // Initialize camera/viewport once up front; some platforms may delay the first resize.
        viewport.update(WORLD_W, WORLD_H, true);

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

        assets = new Assets();
        assets.loadAll();

        // Always start at main menu; no simulation or network is started yet.
        transitionTo(AppPhase.MAIN_MENU);
        System.out.println("[Battle City] CoreGame.create() complete");
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void render() {
        if (!startupLogged) {
            startupLogged = true;
            String glVendor = Gdx.gl.glGetString(GL20.GL_VENDOR);
            String glRenderer = Gdx.gl.glGetString(GL20.GL_RENDERER);
            String glVersion = Gdx.gl.glGetString(GL20.GL_VERSION);
            System.out.println("[Battle City] First render tick reached");
            System.out.println("[Battle City] GL_VENDOR=" + glVendor);
            System.out.println("[Battle City] GL_RENDERER=" + glRenderer);
            System.out.println("[Battle City] GL_VERSION=" + glVersion);
            System.out.println("[Battle City] Framebuffer="
                    + Gdx.graphics.getBackBufferWidth() + "x" + Gdx.graphics.getBackBufferHeight());
        }

        frameCounter++;
        if (frameCounter % 300 == 0) {
            System.out.println("[Battle City] render heartbeat frame=" + frameCounter
                    + " fps=" + Gdx.graphics.getFramesPerSecond()
                    + " phase=" + currentPhase);
        }

        Gdx.gl.glClearColor(0.08f, 0.08f, 0.10f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        viewport.apply();
        camera.update();
        batch.setProjectionMatrix(camera.combined);

        if (!assetsReady) {
            assetsReady = assets.update();
            if (assetsReady) {
                snapshotRenderer.setAssets(assets);
                System.out.println("[Battle City] Assets loaded");
            }
        }

        float dt = Gdx.graphics.getDeltaTime();
        AppPhase next = currentHandler.update(dt);

        batch.begin();
        currentHandler.render();
        batch.end();

        // Apply the transition after the current frame has been drawn so the handler always
        // completes cleanly before being torn down.
        if (next == AppPhase.QUIT) {
            Gdx.app.exit();
            return;
        }
        if (next != currentPhase) {
            if (next == AppPhase.MATCH_END) {
                captureMatchEndSnapshot();
            }
            if (next == AppPhase.SINGLE_PLAYER
                    && currentHandler instanceof SinglePlayerPreStartScreen ps) {
                pendingSeed = ps.selectedSeed();
            }
            // When the multiplayer form confirms, create the server (HOST) or client (JOIN) here
            // so that CoreGame owns the lifecycle and MultiplayerConnectScreen stays network-free.
            if (next == AppPhase.MP_LOBBY
                    && currentHandler instanceof MultiplayerConnectScreen mcs) {
                closeMultiplayerResources();
                final int    port = mcs.selectedPort();
                final String name = mcs.selectedDisplayName();

                if (mcs.selectedMode() == MultiplayerConnectScreen.Mode.HOST) {
                    try {
                        hostedServer = new GameServer(port);
                        // Capture into a local so the lambda sees an effectively-final reference.
                        final GameServer srv = hostedServer;
                        serverThread = new Thread(() -> {
                            final long TICK_NS = 1_000_000_000L / 60;
                            long lastNs  = System.nanoTime();
                            long accumNs = 0;
                            while (!Thread.currentThread().isInterrupted() && srv.isRunning()) {
                                long now = System.nanoTime();
                                accumNs += now - lastNs;
                                lastNs   = now;
                                while (accumNs >= TICK_NS) {
                                    srv.pollNetwork();
                                    srv.updateTick();
                                    accumNs -= TICK_NS;
                                }
                                // Sleep for the idle remainder to avoid busy-spinning.
                                long sleepNs = TICK_NS - accumNs;
                                if (sleepNs > 1_000_000L) {
                                    try {
                                        Thread.sleep(sleepNs / 1_000_000L);
                                    } catch (InterruptedException ie) {
                                        Thread.currentThread().interrupt();
                                        break;
                                    }
                                }
                            }
                        }, "battle-city-server");
                        serverThread.setDaemon(true);
                        serverThread.start();
                        pendingNetClient = new GameClient("127.0.0.1", port, name);
                        pendingNetClient.connect();
                        lastConnectHost = "127.0.0.1";
                        lastConnectPort = port;
                        lastConnectName = name;
                        pendingHostIp   = mcs.selectedIp();
                    } catch (IOException ex) {
                        Gdx.app.error("CoreGame", "Failed to start hosted server: " + ex.getMessage());
                        pendingConnectError = "Cannot host: " + ex.getMessage();
                        closeMultiplayerResources();
                        transitionTo(AppPhase.MP_CONNECT);
                        return;
                    }
                } else {
                    // JOIN — existing path unchanged.
                    final String host = mcs.selectedHost();
                    try {
                        pendingNetClient = new GameClient(host, port, name);
                        pendingNetClient.connect();
                        // Persist the values so MP_CONNECT can pre-fill them if the join fails.
                        lastConnectHost = host;
                        lastConnectPort = port;
                        lastConnectName = name;
                    } catch (IOException ex) {
                        Gdx.app.error("CoreGame", "Failed to start network client: " + ex.getMessage());
                        pendingConnectError = "Cannot connect: " + ex.getMessage();
                        transitionTo(AppPhase.MP_CONNECT);
                        return;
                    }
                }
            }

            // When the lobby redirects back to MP_CONNECT (server rejected the join), capture
            // the error text before closeMultiplayerResources() nulls the client.
            if (next == AppPhase.MP_CONNECT && pendingNetClient != null) {
                String lobbyErr = pendingNetClient.lastError();
                if (lobbyErr != null) {
                    pendingConnectError = lobbyErr;
                }
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
        closeMultiplayerResources();
        if (assets != null) assets.dispose();
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
        final AppPhase previousPhase = currentPhase;

        if (currentHandler != null) {
            currentHandler.onExit();
            currentHandler.dispose();
            currentHandler = null;
        }

        currentPhase = next;

        switch (next) {
            case MAIN_MENU -> {
                closeMultiplayerResources();
                currentHandler = new MainMenuScreen(ctx);
            }
            case SP_PRESTART -> currentHandler = new SinglePlayerPreStartScreen(ctx);
            case SINGLE_PLAYER -> currentHandler = new SinglePlayerPhaseDriver(
                    ctx, AppPhase.SINGLE_PLAYER, LocalMatchController.withBots(pendingSeed));
            case TUTORIAL -> {
                LocalMatchController tutorialCtrl = LocalMatchController.forTutorial();
                currentHandler = new SinglePlayerPhaseDriver(
                        ctx, AppPhase.TUTORIAL, tutorialCtrl, new TutorialScript());
            }
            case MP_CONNECT -> {
                closeMultiplayerResources();
                // Pre-fill with the last-attempted values (or CLI defaults on first visit).
                // pendingConnectError is non-null only when returning from a failed lobby join.
                String h = lastConnectHost != null ? lastConnectHost : cliHost;
                int    p = lastConnectPort  > 0    ? lastConnectPort  : cliPort;
                String n = lastConnectName  != null ? lastConnectName : "Player";
                currentHandler = new MultiplayerConnectScreen(ctx, h, p, n, pendingConnectError);
                pendingConnectError = null;
            }
            case MP_LOBBY -> {
                // After a completed match the player returns here without re-joining.
                // Reset the client's snapshot state so LobbyScreen does not immediately
                // re-enter MP_MATCH on the stale currentSnapshot reference.
                if (previousPhase == AppPhase.MP_MATCH && pendingNetClient != null) {
                    pendingNetClient.resetForLobby();
                }
                currentHandler = new LobbyScreen(ctx, pendingNetClient, pendingHostIp);
            }
            case MP_MATCH -> currentHandler = new MpMatchPhaseDriver(
                    ctx, pendingNetClient, hostedServer != null);
            case MATCH_END -> {
                // Net client is no longer needed after the match ends.
                closeMultiplayerResources();
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

    /**
     * Tears down all multiplayer resources in safe order:
     * client first (sends DISCONNECT), then server (closes socket), then server thread.
     * Idempotent — safe to call when any or all resources are already null.
     */
    private void closeMultiplayerResources() {
        if (pendingNetClient != null) {
            pendingNetClient.sendDisconnect("session ended");
            pendingNetClient.close();
            pendingNetClient = null;
        }
        if (hostedServer != null) {
            hostedServer.close();
            hostedServer = null;
        }
        if (serverThread != null) {
            serverThread.interrupt();
            try {
                serverThread.join(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            serverThread = null;
        }
        pendingHostIp = null;
    }
}
