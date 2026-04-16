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
import com.badlogic.gdx.math.MathUtils;

public final class CoreGame extends ApplicationAdapter {
  private static final float FIXED_DT_SECONDS = 1f / 60f;
  private static final float MAX_FRAME_TIME_SECONDS = 0.25f;

  private SpriteBatch batch;
  private BitmapFont font;
  private Texture whitePixel;

  private final GameState state = new GameState();
  private float accumulatorSeconds = 0f;

  @Override
  public void create() {
    batch = new SpriteBatch();
    font = new BitmapFont();
    font.setColor(Color.WHITE);

    Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
    pm.setColor(Color.WHITE);
    pm.fill();
    whitePixel = new Texture(pm);
    pm.dispose();
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

  /**
   * The only method allowed to mutate world state.
   * Runs at a fixed 60 ticks/sec.
   */
  void updateTick() {
    boolean f3Down = Gdx.input.isKeyPressed(Input.Keys.F3);
    if (f3Down && !state.prevF3Down) {
      state.showDebug = !state.showDebug;
    }
    state.prevF3Down = f3Down;

    Direction inputDir = null;
    if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) inputDir = Direction.LEFT;
    else if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) inputDir = Direction.RIGHT;
    else if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) inputDir = Direction.UP;
    else if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) inputDir = Direction.DOWN;

    boolean spaceDown = Gdx.input.isKeyPressed(Input.Keys.SPACE);
    boolean firePressed = spaceDown && !state.prevSpaceDown;
    state.prevSpaceDown = spaceDown;

    Sim.step(state, inputDir, firePressed, FIXED_DT_SECONDS);
  }

  private void renderFrame(float alpha) {
    float x = MathUtils.lerp(state.player.prevX, state.player.x, alpha);
    float y = MathUtils.lerp(state.player.prevY, state.player.y, alpha);

    Gdx.gl.glClearColor(0.08f, 0.08f, 0.10f, 1f);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

    batch.begin();

    // Tile grid — render-only
    float tile = state.map.tileSize();
    for (int ty = 0; ty < state.map.heightTiles(); ty++) {
      for (int tx = 0; tx < state.map.widthTiles(); tx++) {
        Tile t = state.map.tileAt(tx, ty);
        switch (t) {
          case EMPTY -> batch.setColor(0.16f, 0.16f, 0.18f, 1f);
          case BRICK -> batch.setColor(0.55f, 0.25f, 0.18f, 1f);
          case STEEL -> batch.setColor(0.45f, 0.45f, 0.50f, 1f);
          case BASE -> batch.setColor(0.75f, 0.75f, 0.20f, 1f);
        }
        float px = tx * tile;
        float py = ty * tile;
        batch.draw(whitePixel, px, py, tile - 1f, tile - 1f);
      }
    }

    // Player tank placeholder — render-only
    batch.setColor(0.15f, 0.75f, 0.25f, 1f);
    batch.draw(whitePixel, x - 6f, y - 6f, 12f, 12f);

    // Projectiles — render-only
    batch.setColor(0.95f, 0.90f, 0.20f, 1f);
    for (Projectile p : state.projectiles) {
      if (!p.active) continue;
      float px = MathUtils.lerp(p.prevX, p.x, alpha);
      float py = MathUtils.lerp(p.prevY, p.y, alpha);
      batch.draw(whitePixel, px - p.halfW, py - p.halfH, p.halfW * 2f, p.halfH * 2f);
    }

    // Debug text (tick + player position) — render-only
    if (state.showDebug) {
      batch.setColor(Color.WHITE);
      font.draw(batch,
        "tick=" + state.tickCount
          + "  player=(" + (int) state.player.x + "," + (int) state.player.y + ")"
          + " dir=" + state.player.dir
          + " fire=" + state.fireCount,
        8f,
        Gdx.graphics.getHeight() - 8f
      );
    }

    batch.end();
  }

  @Override
  public void dispose() {
    if (whitePixel != null) whitePixel.dispose();
    if (font != null) font.dispose();
    if (batch != null) batch.dispose();
  }
}

