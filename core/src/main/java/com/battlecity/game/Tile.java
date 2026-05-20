package com.battlecity.game;

public enum Tile {
    EMPTY,
    BRICK,
    STEEL,
    BASE;

    public static Tile fromOrdinal(int ordinal) {
        Tile[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("invalid tile ordinal: " + ordinal);
        }
        return values[ordinal];
    }
}
