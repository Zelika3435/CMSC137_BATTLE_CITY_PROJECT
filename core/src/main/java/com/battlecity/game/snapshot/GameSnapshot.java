package com.battlecity.game.snapshot;

import com.battlecity.game.Tile;
import java.util.List;

public record GameSnapshot(
        long serverTick,
        long stateHash,
        int mapWidthTiles,
        int mapHeightTiles,
        float tileSize,
        Tile[] tiles,
        List<TankSnapshot> tanks,
        List<ProjectileSnapshot> projectiles,
        boolean baseDestroyed,
        boolean matchOver
) {}
