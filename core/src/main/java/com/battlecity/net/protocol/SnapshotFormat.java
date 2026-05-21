package com.battlecity.net.protocol;

/**
 * Tile payload mode for {@link MessageType#SNAPSHOT}.
 *
 * <p>{@link #FULL_MAP} is sent once when a match enters RUNNING (and on oversized deltas).
 * {@link #DELTA} carries only changed tile indices for per-tick updates.
 */
public enum SnapshotFormat {
    FULL_MAP(0),
    DELTA(1);

    private final int id;

    SnapshotFormat(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static SnapshotFormat fromId(int id) {
        for (SnapshotFormat format : values()) {
            if (format.id == id) {
                return format;
            }
        }
        throw new IllegalArgumentException("unknown snapshot format: " + id);
    }
}
