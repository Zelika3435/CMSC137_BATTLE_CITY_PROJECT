package com.battlecity.game.snapshot;

import com.battlecity.game.Direction;

public record TankSnapshot(
        int entityId,
        int playerId,
        float x,
        float y,
        float prevX,
        float prevY,
        Direction dir,
        boolean alive,
        /** Ticks until respawn; {@code 0} means respawn this tick; {@code -1} if not waiting. */
        int respawnCooldownTicks,
        boolean eliminated
) {}
