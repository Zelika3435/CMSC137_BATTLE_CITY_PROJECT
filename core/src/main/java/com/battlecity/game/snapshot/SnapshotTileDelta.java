package com.battlecity.game.snapshot;

import com.battlecity.game.Tile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Collects and applies tile deltas for network snapshots (deterministic sorted indices). */
public final class SnapshotTileDelta {
    private SnapshotTileDelta() {}

    public static List<TileChange> collectChanges(Tile[] previous, Tile[] current) {
        if (previous.length != current.length) {
            throw new IllegalArgumentException("tile array size mismatch");
        }
        List<TileChange> changes = new ArrayList<>();
        for (int i = 0; i < current.length; i++) {
            if (previous[i] != current[i]) {
                changes.add(new TileChange(i, current[i]));
            }
        }
        changes.sort(Comparator.comparingInt(TileChange::index));
        return List.copyOf(changes);
    }

    public static void applyChanges(Tile[] tiles, List<TileChange> changes) {
        for (TileChange change : changes) {
            tiles[change.index()] = change.tile();
        }
    }

    public static boolean isSortedByIndex(List<TileChange> changes) {
        int last = -1;
        for (TileChange change : changes) {
            if (change.index() <= last) {
                return false;
            }
            last = change.index();
        }
        return true;
    }
}
