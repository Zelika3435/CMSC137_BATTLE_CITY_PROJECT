package com.battlecity.core;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.battlecity.input.KeyboardInputMapper;
import com.battlecity.render.SnapshotRenderer;
import com.battlecity.ui.DebugOverlay;

/**
 * Immutable bag of shared render resources created once in {@code CoreGame.create()} and passed to
 * every phase driver. Drivers must NOT dispose any of these fields; CoreGame owns their lifecycle.
 */
public record PhaseContext(
        SpriteBatch batch,
        BitmapFont font,
        Texture whitePixel,
        Viewport viewport,
        SnapshotRenderer snapshotRenderer,
        DebugOverlay debugOverlay,
        KeyboardInputMapper inputMapper) {}
