package com.battlecity.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

/**
 * Short cooldown after a screen becomes active so held confirm keys (ENTER / SPACE from the
 * previous menu) do not immediately trigger actions on the new screen.
 */
public final class PhaseInputGate {

    private static final float DEFAULT_COOLDOWN_SEC = 0.35f;

    private float remainingSec;

    public PhaseInputGate() {
        this(DEFAULT_COOLDOWN_SEC);
    }

    public PhaseInputGate(float cooldownSec) {
        this.remainingSec = Math.max(0f, cooldownSec);
    }

    public void tick(float dt) {
        if (remainingSec > 0f) {
            remainingSec = Math.max(0f, remainingSec - dt);
        }
    }

    public boolean isBlocking() {
        return remainingSec > 0f;
    }

    /**
     * Edge-triggered confirm (ENTER, SPACE, numpad ENTER). Returns {@code false} while the gate
     * is active or if no confirm key was just pressed.
     */
    public boolean confirmJustPressed() {
        if (remainingSec > 0f) {
            return false;
        }
        return Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_ENTER)
                || Gdx.input.isKeyJustPressed(Input.Keys.SPACE);
    }

    /** {@code true} if a confirm key is currently held down. */
    public static boolean confirmHeld() {
        return Gdx.input.isKeyPressed(Input.Keys.ENTER)
                || Gdx.input.isKeyPressed(Input.Keys.NUMPAD_ENTER)
                || Gdx.input.isKeyPressed(Input.Keys.SPACE);
    }
}
