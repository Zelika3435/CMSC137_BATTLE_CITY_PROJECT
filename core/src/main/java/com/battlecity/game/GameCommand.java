package com.battlecity.game;

public enum GameCommand {
    MOVE_DIR,
    FIRE;

    public static GameCommand fromOrdinal(int ordinal) {
        GameCommand[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("invalid command ordinal: " + ordinal);
        }
        return values[ordinal];
    }
}
