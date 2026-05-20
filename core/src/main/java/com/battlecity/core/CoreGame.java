package com.battlecity.core;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.battlecity.game.Simulation;
import com.battlecity.game.World;
import com.battlecity.game.snapshot.GameSnapshot;
import com.battlecity.game.snapshot.NetStatsSnapshot;
import com.battlecity.input.KeyboardInputMapper;
import com.battlecity.net.client.GameClient;
import com.battlecity.net.protocol.ProtocolConstants;
import com.battlecity.render.SnapshotRenderer;
import com.battlecity.ui.DebugOverlay;
import java.io.IOException;

public final class CoreGame extends ApplicationAdapter {
    private static final float FIXED_DT_SECONDS = Simulation.FIXED_DT_SECONDS;
    private static final float MAX_FRAME_TIME_SECONDS = 0.25f;

    private final String serverHost;
    private final int serverPort;
    private final boolean offlineMode;

    private SpriteBatch batch;
    private BitmapFont font;
    private Texture whitePixel;
    private SnapshotRenderer snapshotRenderer;
    private DebugOverlay debugOverlay;
    private KeyboardInputMapper inputMapper;

    private GameClient netClient;
    private Simulation offlineSimulation;
    private GameSnapshot offlinePrev;
    private GameSnapshot offlineCur;

    private float accumulatorSeconds = 0f;

    public CoreGame(String[] args) {
        if (args.length >= 2) {
            this.serverHost = args[0];
            this.serverPort = Integer.parseInt(args[1]);
            this.offlineMode = false;
        } else {
            this.serverHost = "127.0.0.1";
            this.serverPort = ProtocolConstants.DEFAULT_PORT;
            this.offlineMode = true;
        }
    }

    @Override
    public void create() {
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

        try {
            if (offlineMode) {
                offlineSimulation = new Simulation(World.createDefault(), true, 42L);
                offlineCur = offlineSimulation.snapshot();
                offlinePrev = offlineCur;
            } else {
                netClient = new GameClient(serverHost, serverPort, "Player");
                netClient.connect();
            }
        } catch (IOException ex) {
            throw new RuntimeException("failed to start networking", ex);
        }
    }

    @Override
    public void render() {
        float frameDt = Math.min(Gdx.graphics.getDeltaTime(), MAX_FRAME_TIME_SECONDS);
        accumulatorSeconds += frameDt;

        while (accumulatorSeconds >= FIXED_DT_SECONDS) {
            updateTick();
            accumulatorSeconds -= FIXED_DT_SECONDS;
        }

        float alpha = accumulatorSeconds / FIXED_DT_SECONDS;
        renderFrame(alpha);
    }

    void updateTick() {
        KeyboardInputMapper.LocalInput input = inputMapper.poll(
                key(Input.Keys.A) || key(Input.Keys.LEFT),
                key(Input.Keys.D) || key(Input.Keys.RIGHT),
                key(Input.Keys.W) || key(Input.Keys.UP),
                key(Input.Keys.S) || key(Input.Keys.DOWN),
                key(Input.Keys.SPACE),
                key(Input.Keys.F3)
        );

        if (input.debugToggle()) {
            debugOverlay.toggle();
        }

        if (offlineMode) {
            java.util.ArrayList<com.battlecity.game.QueuedCommand> commands = new java.util.ArrayList<>();
            long tick = offlineSimulation.tickCount();
            int seq = 0;
            if (input.moveDir() != null) {
                commands.add(new com.battlecity.game.QueuedCommand(
                        0, tick, seq++, com.battlecity.game.GameCommand.MOVE_DIR, input.moveDir()));
            }
            if (input.firePressed()) {
                commands.add(new com.battlecity.game.QueuedCommand(
                        0, tick, seq, com.battlecity.game.GameCommand.FIRE, null));
            }
            offlineSimulation.applyCommands(commands);
            offlinePrev = offlineCur;
            offlineSimulation.updateTick();
            offlineCur = offlineSimulation.snapshot();
            debugOverlay.update(new NetStatsSnapshot(
                    offlineCur.serverTick(),
                    FIXED_DT_SECONDS,
                    0,
                    0f,
                    offlineCur.tanks().size(),
                    offlineCur.projectiles().size(),
                    offlineCur.tanks().size() + offlineCur.projectiles().size()
            ));
            return;
        }

        if (netClient != null) {
            netClient.poll();
            netClient.sendInput(input.moveDir(), input.firePressed());
            netClient.endTick();
            debugOverlay.update(netClient.netStats());
        }
    }

    private void renderFrame(float alpha) {
        Gdx.gl.glClearColor(0.08f, 0.08f, 0.10f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.begin();

        if (offlineMode) {
            snapshotRenderer.render(offlinePrev, offlineCur, alpha, 0);
        } else if (netClient != null) {
            snapshotRenderer.render(
                    netClient.previousSnapshot(),
                    netClient.currentSnapshot(),
                    alpha,
                    netClient.playerId()
            );
        }

        batch.end();
    }

    private static boolean key(int keycode) {
        return Gdx.input.isKeyPressed(keycode);
    }

    @Override
    public void dispose() {
        if (netClient != null) {
            netClient.close();
        }
        if (whitePixel != null) {
            whitePixel.dispose();
        }
        if (font != null) {
            font.dispose();
        }
        if (batch != null) {
            batch.dispose();
        }
    }
}
