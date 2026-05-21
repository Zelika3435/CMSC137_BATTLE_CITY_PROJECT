package com.battlecity.net.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.battlecity.game.Simulation;
import com.battlecity.game.World;
import com.battlecity.game.snapshot.GameSnapshot;
import org.junit.jupiter.api.Test;

final class SnapshotInterpolationBufferTest {

    @Test
    void outOfOrder_insertsSortedAndReplacesDuplicateTick() {
        SnapshotInterpolationBuffer buffer = new SnapshotInterpolationBuffer(0);
        GameSnapshot s5 = snapAt(5);
        GameSnapshot s7 = snapAt(7);
        GameSnapshot s7b = snapAt(7);

        buffer.push(s7);
        buffer.push(s5);
        buffer.push(s7b);

        SnapshotInterpolationBuffer.InterpolationSample sample = buffer.sample();
        assertNotNull(sample);
        assertSame(s7b, sample.cur());
    }

    @Test
    void gap_holdsLastBracketWithoutCrash() {
        // ~5 ticks of delay so display time sits between ticks 10 and 20 right after the second push.
        SnapshotInterpolationBuffer buffer = new SnapshotInterpolationBuffer(83);
        buffer.push(snapAt(10));
        buffer.push(snapAt(20));

        SnapshotInterpolationBuffer.InterpolationSample sample = buffer.sample();
        assertNotNull(sample);
        assertEquals(10L, sample.prev().serverTick());
        assertEquals(20L, sample.cur().serverTick());
        assertEquals(0.5f, sample.alpha(), 0.01f);
    }

    private static GameSnapshot snapAt(long tick) {
        Simulation sim = new Simulation(World.createDefault(), false, 0L);
        for (long t = 0; t < tick; t++) {
            sim.updateTick();
        }
        GameSnapshot snap = sim.snapshot();
        return new GameSnapshot(
                tick,
                snap.stateHash(),
                snap.mapWidthTiles(),
                snap.mapHeightTiles(),
                snap.tileSize(),
                snap.tiles(),
                snap.tanks(),
                snap.projectiles(),
                snap.baseDestroyed(),
                snap.matchOver()
        );
    }
}
