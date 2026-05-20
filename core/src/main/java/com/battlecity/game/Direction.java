package com.battlecity.game;

public enum Direction {
    UP,
    DOWN,
    LEFT,
    RIGHT;

    public static Direction fromOrdinal(int ordinal) {
        Direction[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("invalid direction ordinal: " + ordinal);
        }
        return values[ordinal];
    }
}
