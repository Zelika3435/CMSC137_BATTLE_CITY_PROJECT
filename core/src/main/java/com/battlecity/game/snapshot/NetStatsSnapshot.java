package com.battlecity.game.snapshot;

public record NetStatsSnapshot(
        long serverTick,
        float fixedDtSeconds,
        int pingMs,
        float packetLossPercent,
        int tankCount,
        int projectileCount,
        int totalEntities
) {}
