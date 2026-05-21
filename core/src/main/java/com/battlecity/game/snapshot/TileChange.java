package com.battlecity.game.snapshot;

import com.battlecity.game.Tile;

/** One tile mutation in a delta {@link com.battlecity.net.protocol.SnapshotFormat#DELTA} snapshot. */
public record TileChange(int index, Tile tile) {}
