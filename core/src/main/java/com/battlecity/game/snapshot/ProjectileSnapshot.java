package com.battlecity.game.snapshot;

import com.battlecity.game.Direction;

public record ProjectileSnapshot(
        int entityId,
        float x,
        float y,
        float prevX,
        float prevY,
        Direction dir,
        boolean active,
        int ownerPlayerId
) {}
