package com.battlecity.game.snapshot;

public record NetStatsSnapshot(
        long serverTick,
        float fixedDtSeconds,
        /** Round-trip time from the most recent PING/PONG exchange (ms). */
        int rttMs,
        /** Authoritative snapshots received over the last ~1 s window. */
        float snapshotsPerSecond,
        /** Ticks elapsed since the last snapshot arrived (stale-data indicator). */
        int snapshotAgeTicks,
        int tankCount,
        int projectileCount,
        int totalEntities,
        /** Last client-side prediction positional error in world units (0 when not in MP_MATCH). */
        float predErrorPx,
        /** {@code true} if the most recent prediction reconcile was a hard snap. */
        boolean predReconcileSnap,
        /** MP_MATCH snapshot interpolation delay (ms); 0 in offline modes. */
        int interpBufferMs,
        /** INPUT messages sent but not yet acked by the server (0 when fully caught up). */
        int unackedInputs
) {}
