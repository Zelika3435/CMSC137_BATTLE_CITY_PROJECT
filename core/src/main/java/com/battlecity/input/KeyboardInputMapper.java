package com.battlecity.input;

import com.battlecity.game.Direction;

public final class KeyboardInputMapper {
    private boolean prevSpaceDown;
    private boolean prevF3Down;

    public LocalInput poll(
            boolean left,
            boolean right,
            boolean up,
            boolean down,
            boolean spaceDown,
            boolean f3Down
    ) {
        boolean firePressed = spaceDown && !prevSpaceDown;
        prevSpaceDown = spaceDown;

        boolean debugToggle = f3Down && !prevF3Down;
        prevF3Down = f3Down;

        Direction moveDir = null;
        if (left) {
            moveDir = Direction.LEFT;
        } else if (right) {
            moveDir = Direction.RIGHT;
        } else if (up) {
            moveDir = Direction.UP;
        } else if (down) {
            moveDir = Direction.DOWN;
        }

        return new LocalInput(moveDir, firePressed, debugToggle);
    }

    /**
     * Clears edge-detection state. Call when entering a simulation phase so key presses made in
     * menu phases (e.g. SPACE to confirm a menu selection) don't carry over as fire/toggle events
     * on the first simulation tick.
     */
    public void reset() {
        prevSpaceDown = false;
        prevF3Down = false;
    }

    public record LocalInput(Direction moveDir, boolean firePressed, boolean debugToggle) {}
}
